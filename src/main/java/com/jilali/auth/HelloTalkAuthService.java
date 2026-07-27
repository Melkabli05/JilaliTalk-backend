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
        if (client.signupCheck(email, password, emailVerifyCode, iriskToken).isEmpty()) {
            return new SignupOutcome.Rejected(
                "HelloTalk rejected the signup request (invalid code, email already registered, "
                    + "or an anti-cheat check this BFF cannot satisfy)");
        }
        return switch (login(email, password)) {
            case LoginOutcome.Authenticated(var session, var user) -> new SignupOutcome.Created(session, user);
            case LoginOutcome.InvalidCredentials ignored -> new SignupOutcome.Rejected(
                "Account was created but the immediate follow-up login failed; try logging in manually");
        };
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
