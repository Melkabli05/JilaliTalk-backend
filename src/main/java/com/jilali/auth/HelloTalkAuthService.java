package com.jilali.auth;

import com.jilali.auth.dto.AuthUserResponse;
import com.jilali.auth.dto.upstream.LoginResponse;
import com.jilali.auth.dto.upstream.SignCheckResponse;
import com.jilali.core.JilaliProperties;
import com.jilali.roomcontext.application.port.out.UserUpstreamPort;
import com.jilali.roomcontext.infrastructure.dto.user.UserInfo;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Orchestrates the auth business flows. Depends on the {@link HelloTalkAuthClient} and
 * {@link AuthSessionRepository} ports (Dependency Inversion) for everything auth-specific — no
 * HTTP, no JDBC, no crypto details leak in here. Profile enrichment (nickname/avatar for the
 * frontend's AuthUser) goes through {@link UserUpstreamPort}, the roomcontext bounded context's
 * user-profile lookup port — the consolidated owner of HelloTalk user-info calls after the
 * legacy god-client was removed.
 */
@Singleton
public final class HelloTalkAuthService {

    private static final Logger log = LoggerFactory.getLogger(HelloTalkAuthService.class);

    private final HelloTalkAuthClient client;
    private final AuthSessionRepository sessions;
    private final JilaliProperties properties;
    private final UserUpstreamPort userUpstream;

    public HelloTalkAuthService(HelloTalkAuthClient client, AuthSessionRepository sessions,
                                 JilaliProperties properties, UserUpstreamPort userUpstream) {
        this.client = client;
        this.sessions = sessions;
        this.properties = properties;
        this.userUpstream = userUpstream;
    }

    /** Verifies real HelloTalk credentials and, on success, opens a local session for them. */
    public LoginOutcome login(String email, String password) {
        return client.login(email, password)
            .map(LoginResponse::userInfo)
            .<LoginOutcome>map(userInfo -> {
                var session = sessions.create(userInfo.userId(), email, userInfo.jwt(), properties.deviceId());
                return new LoginOutcome.Authenticated(session, buildAuthUser(session));
            })
            .orElseGet(LoginOutcome.InvalidCredentials::new);
    }

    public void logout(String sessionId) {
        sessions.delete(sessionId);
    }

    /** Resolves an opaque session id (from the {@code jilali_session} cookie) back to the
     *  identity it belongs to, or empty if unknown/expired. */
    public Optional<AuthUserResponse> currentUser(String sessionId) {
        return sessions.find(sessionId).map(this::buildAuthUser);
    }

    public void signupPrepare() {
        client.regPrepare();
    }

    public void signupSendEmailCode(String email) {
        client.sendEmailCode(email);
    }

    public void signupCheckNickname(String nickname) {
        client.checkNickname(nickname);
    }

    /**
     * Runs the terminal {@code /v3/check} signup step and, on success, immediately falls back
     * into {@link #login} with the same credentials — {@code /v3/check} never returns a JWT
     * (confirmed from smali, see {@link SignCheckResponse}), so a freshly-created account isn't
     * actually usable until this second round-trip mints one.
     *
     * <p>Before the check, calls {@link HelloTalkAuthClient#regPrepare} to bind the
     * NetEase Yidun anti-cheat token. The irisk_token from that response is required
     * on the /v3/reg/* flow (vs. the login flow's behavior_validate). Without it,
     * upstream returns "code is incorrect, or the account could not be created".
     * regPrepare is best-effort — if the bind fails, the check proceeds with an
     * regPrepare is best-effort — see HelloTalkAuthClient.regPrepare docs.
     *
     * <p>Per FINDINGS.md §7.2 line 193, the upstream's irisk_token field is "empty for
     * login (only set on /v3/reg/* flow)". But live test (commit dc46bde's debug log
     * capture) shows that an empty string still rejects the /v3/check call with a
     * silent upstream-side no-verify_token. The behavior_validate field on /v3/login
     * is "checked for presence only, not cryptographic validity" (FINDINGS line 133) —
     * confirmed live with an arbitrary placeholder. Treating irisk_token the same way:
     * if regPrepare fails (or upstream doesn't return a token), pass a non-empty
     * placeholder string so upstream's "absent vs present" check passes. The token
     * content is never validated — it just has to exist.
     */
    public SignupOutcome signup(String email, String password, String emailVerifyCode) {
        String iriskToken = client.regPrepare().orElse("jilalibff-no-sdk-available");
        return switch (client.signupCheck(email, password, emailVerifyCode, iriskToken)) {
            case SignupCheckOutcome.Accepted accepted -> switch (login(email, password)) {
                case LoginOutcome.Authenticated(var session, var user) ->
                    new SignupOutcome.Created(session, user);
                case LoginOutcome.InvalidCredentials loginFail ->
                    new SignupOutcome.Rejected(0, "Account was created but the follow-up login failed; try logging in manually");
            };
            case SignupCheckOutcome.Rejected(int status, String msg) -> {
                // The status code table mirrors the Android client's h21/e0 smali
                // (re_output/apktool_out/smali_classes22/h21/e0.smali lines 196-1190 —
                // the packed-switch on status). Common ones the frontend will surface:
                //   208 (0xd0) verification_code_error
                //   105 (0x69)  too many signup attempts within 24h
                //   109 (0x6d)  password_format_incorrect
                //   125 (0x7d)  your_account_has_been_hidden
                //   212 (0xd4)  verification_failed
                //   213 (0xd5)  silent re-route
                //   100 (0x64)  generic server_error
                //   101 (0x65)  invalid_email_address
                //   102 (0x66)  facebook_connection_fail
                //   103 (0x67)/104 (0x68) silent
                //   106 (0x6a)  cant_register_new_account (multi-account on this device)
                //   107 (0x6b)  Existing Account fallback
                //   108 (0x6c)  failed
                //   201 (0xc9)  phone_number_is_not_valid
                //   202 (0xca)  phone_number_is_bound
                //   204 (0xcc)  sending_failed
                //   206 (0xce)/207 (0xcf) 24h-blocked
                //   551 (0x227) network-lost
                String reason = mapSignupRejection(status, msg);
                yield new SignupOutcome.Rejected(status, reason);
            }
        };
    }

    /**
     * Translates the upstream envelope {@code status} (from the Android client's
     * {@code h21/e0} smali status code table) to a user-facing message. Falls back to
     * the raw upstream {@code msg} field if present, otherwise to a generic message.
     * {@code status == 0} means envelope-level failure (transport / parse) — not an
     * upstream rejection — and gets a distinct generic message.
     */
    private static String mapSignupRejection(int status, String upstreamMsg) {
        String mapped = switch (status) {
            case 208 -> "That verification code is incorrect. Please try again.";
            case 212 -> "Verification failed. Please request a new code.";
            case 105, 111, 206, 207 -> "Too many attempts. Try again in 24 hours.";
            case 109 -> "Password format is incorrect.";
            case 101 -> "That email address is invalid.";
            case 102 -> "Connection to the third-party login provider failed.";
            case 100, 551 -> "The server is temporarily unavailable. Please try again shortly.";
            case 106, 125 -> "Your account has been hidden. Contact support to recover it.";
            case 107 -> "An account with that email already exists. Try signing in instead.";
            case 108 -> "Signup failed. Please try again.";
            case 202 -> "That phone number is already bound to an account.";
            case 201 -> "That phone number is not valid.";
            case 204 -> "We could not send the verification code. Please try again.";
            case 0 -> "Could not reach the signup service. Please check your connection.";
            default -> upstreamMsg != null && !upstreamMsg.isBlank()
                ? upstreamMsg
                : "Signup failed (upstream status " + status + "). Please try again.";
        };
        return mapped;
    }

    /**
     * Enriches a session with the profile fields the frontend's {@code AuthUser} needs
     * (nickname, avatar) via the existing profile-lookup gateway — best-effort: login/signup
     * already succeeded by this point, so a lookup failure degrades to a nameless identity
     * rather than failing the whole request.
     */
    private AuthUserResponse buildAuthUser(AuthSession session) {
        try {
            UserInfo profile = userUpstream.userInfo(session.helloTalkUid());
            String headUrl = profile.details() != null && profile.details().base() != null
                ? profile.details().base().headUrl() : null;
            return AuthUserResponse.of(session, profile.nickname(), headUrl, properties.deviceModel());
        } catch (RuntimeException e) {
            log.warn("Profile enrichment failed for uid {}: {}", session.helloTalkUid(), e.getMessage());
            return AuthUserResponse.withoutProfile(session);
        }
    }
}
