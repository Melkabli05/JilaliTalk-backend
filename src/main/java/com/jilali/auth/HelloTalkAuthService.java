package com.jilali.auth;

import com.jilali.auth.dto.AuthUserResponse;
import com.jilali.auth.dto.upstream.LoginResponse;
import com.jilali.core.AuthTokenHolder;
import com.jilali.core.JilaliProperties;
import com.jilali.roomcontext.application.port.out.UserUpstreamPort;
import com.jilali.roomcontext.infrastructure.dto.user.UserInfo;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
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
    private final AuthTokenHolder authTokenHolder;
    private final DataSource dataSource;

    public HelloTalkAuthService(HelloTalkAuthClient client, AuthSessionRepository sessions,
                                 JilaliProperties properties, UserUpstreamPort userUpstream,
                                 AuthTokenHolder authTokenHolder, DataSource dataSource) {
        this.client = client;
        this.sessions = sessions;
        this.properties = properties;
        this.userUpstream = userUpstream;
        this.authTokenHolder = authTokenHolder;
        this.dataSource = dataSource;
    }

    /**
     * Verifies real HelloTalk credentials and, on success, opens a local session for them.
     * <p>
     * Also republishes the freshly-minted JWT into {@link AuthTokenHolder} — the same
     * shared fallback token {@link com.jilali.core.DefaultHeadersClientFilter} uses for any
     * upstream call that has no session (anonymous browsing, background jobs). Without
     * {@code jilali.hellotalk-email}/{@code hellotalk-password} configured, {@link
     * com.jilali.platform.reconnect.ImReloginRunner} never runs, so that shared token would
     * otherwise just sit dead until someone hand-rotates {@code LIVEHUB_AUTH_TOKEN}. Every
     * real login is a free, already-authenticated JWT for the same account this BFF was
     * seeded with — piggybacking off it keeps the fallback token self-healing with zero
     * extra upstream calls. Persisted to {@code service_token} (best-effort, same as {@link
     * AuthTokenHolder#persistIfHolder}) so a restart doesn't lose it either.
     */
    public LoginOutcome login(String email, String password) {
        return client.login(email, password)
            .map(LoginResponse::userInfo)
            .<LoginOutcome>map(userInfo -> {
                var session = sessions.create(userInfo.userId(), email, userInfo.jwt(), properties.deviceId());
                authTokenHolder.set(userInfo.jwt());
                authTokenHolder.persistIfHolder(dataSource);
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

    /**
     * Enriches a session with the profile fields the frontend's {@code AuthUser} needs
     * (nickname, avatar) via the existing profile-lookup gateway — best-effort: login
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