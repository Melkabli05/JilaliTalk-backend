package com.jilali.auth;

import java.time.Instant;

/**
 * A verified HelloTalk identity, persisted server-side. The browser only ever holds
 * {@link #id()} (as an HttpOnly cookie) — {@link #jwt()} never leaves the backend; see
 * {@link SessionAuthClientFilter}, the only place it's read back out.
 *
 * <p>{@code refreshedAt} and {@code lastSeen} are nullable because the {@code refreshed_at}
 * and {@code last_seen} columns are added at runtime by {@code AuthSchemaInitializer}
 * (SQLite has no {@code ADD COLUMN IF NOT EXISTS}) — rows written before the column
 * existed will load with {@code null} for both. The caller treats them as informational
 * only; the cleanup job (see {@code CleanupJobs}) uses {@code lastSeen} to detect
 * abandoned sessions.
 */
public record AuthSession(
    String id,
    long helloTalkUid,
    String email,
    String jwt,
    String deviceId,
    Instant createdAt,
    Instant expiresAt,
    Instant refreshedAt,
    Instant lastSeen
) {
    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
