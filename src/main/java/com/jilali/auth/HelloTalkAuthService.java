package com.jilali.auth;

import com.jilali.auth.dto.AuthUserResponse;
import com.jilali.auth.dto.upstream.LoginResponse;
import com.jilali.auth.dto.upstream.SignCheckResponse;
import com.jilali.client.JilaliGateway;
import com.jilali.core.JilaliProperties;
import com.jilali.user.dto.UserInfo;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Orchestrates the auth business flows. Depends on the {@link HelloTalkAuthClient} and
 * {@link AuthSessionRepository} ports (Dependency Inversion) for everything auth-specific — no
 * HTTP, no JDBC, no crypto details leak in here. The one exception is {@link JilaliGateway},
 * injected directly (not behind a narrower port) because the `user` bounded context hasn't been
 * split out of it yet (see {@code docs/superpowers/specs/2026-07-09-ddd-migration-design.md}
 * phase 3, not yet landed) — this is inherited coupling from that in-progress migration, not a
 * new design choice made here.
 */
@Singleton
public final class HelloTalkAuthService {

    private static final Logger log = LoggerFactory.getLogger(HelloTalkAuthService.class);

    private final HelloTalkAuthClient client;
    private final AuthSessionRepository sessions;
    private final JilaliProperties properties;
    private final JilaliGateway gateway;

    public HelloTalkAuthService(HelloTalkAuthClient client, AuthSessionRepository sessions,
                                 JilaliProperties properties, JilaliGateway gateway) {
        this.client = client;
        this.sessions = sessions;
        this.properties = properties;
        this.gateway = gateway;
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
     */
    public SignupOutcome signup(String email, String password, String emailVerifyCode) {
        if (client.signupCheck(email, password, emailVerifyCode).isEmpty()) {
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
            UserInfo profile = gateway.userInfo(session.helloTalkUid());
            String headUrl = profile.details() != null && profile.details().base() != null
                ? profile.details().base().headUrl() : null;
            return AuthUserResponse.of(session, profile.nickname(), headUrl, properties.deviceModel());
        } catch (RuntimeException e) {
            log.warn("Profile enrichment failed for uid {}: {}", session.helloTalkUid(), e.getMessage());
            return AuthUserResponse.withoutProfile(session);
        }
    }
}
