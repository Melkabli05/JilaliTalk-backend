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

    void delete(String sessionId);
}
