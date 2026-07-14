package com.jilali.auth;

import java.time.Instant;

/**
 * A verified HelloTalk identity, persisted server-side. The browser only ever holds
 * {@link #id()} (as an HttpOnly cookie) — {@link #jwt()} never leaves the backend; see
 * {@link SessionAuthClientFilter}, the only place it's read back out.
 */
public record AuthSession(
    String id,
    long helloTalkUid,
    String email,
    String jwt,
    String deviceId,
    Instant createdAt,
    Instant expiresAt
) {
    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
