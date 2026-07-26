package com.jilali.auth;

import java.util.Optional;

/**
 * Port to session persistence (Dependency Inversion — {@link HelloTalkAuthService} depends on
 * this abstraction, never on {@link JdbcAuthSessionRepository} directly).
 */
public interface AuthSessionRepository {

    /** Persists a freshly-verified identity and returns the new session's opaque id. */
    AuthSession create(long helloTalkUid, String email, String jwt, String deviceId);

    /** Empty if the id is unknown, or the session has expired. */
    Optional<AuthSession> find(String sessionId);

    /**
     * Replaces the stored JWT in place when upstream returns a fresh token (status 105 re-login
     * or any other server-mandated rotation). Bumps the row's `refreshed_at` column. Idempotent —
     * a no-op if the id is unknown.
     */
    void updateJwt(String sessionId, String newJwt);

    /**
     * Touches the row's `last_seen` column without changing anything else. Called on every
     * outbound upstream call so the cleanup job can detect truly abandoned sessions.
     */
    void touchLastSeen(String sessionId);

    void delete(String sessionId);
}
