package com.jilali.roomcontext.domain.service;

import com.jilali.roomcontext.infrastructure.client.UserProfileEncryptedClient;
import com.jilali.roomcontext.infrastructure.dto.user.UserInfo;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SQLite-backed read-through cache for the upstream {@code /profile/v2/userinfo} call. The
 * existing in-process Caffeine {@code user-info} cache on {@code UserProfileEncryptedClient}
 * stays in place as a per-process L1; this adds an L2 layer that survives BFF restarts and
 * is shared across multi-instance deployments.
 *
 * <p>Callers opt in by calling {@link #findOrFetch(long)} instead of
 * {@code UserProfileEncryptedClient.fetchUserInfo()}. Existing callers can keep using the
 * Caffeine cache (per-process, instant) until they're migrated.
 *
 * <p>Behavior: cache hit returns the persisted profile without touching upstream. Cache miss
 * (or stale row) calls the upstream encrypted endpoint, persists the result, returns it. On
 * upstream error, an empty Optional is returned — the caller is expected to handle a
 * "no profile" case, same as it would without caching.
 *
 * <p>The Caffeine {@code @Cacheable("user-info")} on {@code UserProfileEncryptedClient} still
 * runs on a cache miss, so once the SQLite row is cold, the next call from THIS instance
 * gets the L1 hit; another instance of the BFF would hit the L2 SQLite row. This is
 * intentional — both layers are useful.
 */
@Singleton
public class CachedUserProfileService {

    private static final Logger log = LoggerFactory.getLogger(CachedUserProfileService.class);

    private final JdbcProfileCacheRepository cache;
    private final UserProfileEncryptedClient upstream;

    public CachedUserProfileService(
        JdbcProfileCacheRepository cache,
        UserProfileEncryptedClient upstream
    ) {
        this.cache = cache;
        this.upstream = upstream;
    }

    /**
     * Returns the cached profile, falling through to upstream on miss. Returns empty on
     * upstream failure (caller is expected to tolerate a "no profile" response, same as the
     * uncached path).
     */
    public java.util.Optional<UserInfo> findOrFetch(long userId) {
        var cached = cache.find(userId);
        if (cached.isPresent()) {
            log.debug("CachedUserProfileService: cache hit for userId={}", userId);
            return cached;
        }
        log.debug("CachedUserProfileService: cache miss for userId={}, fetching upstream", userId);
        try {
            UserInfo profile = upstream.fetchUserInfo(userId);
            if (profile == null || profile.userId() != userId) {
                // Upstream returned a different uid (rare — typically a stale-cache mismatch).
                // Don't poison our cache with a mismatched row.
                log.warn("CachedUserProfileService: upstream returned userId={} for requested {}, skipping cache",
                    profile == null ? "null" : profile.userId(), userId);
                return java.util.Optional.ofNullable(profile);
            }
            cache.upsert(userId, profile);
            return java.util.Optional.of(profile);
        } catch (RuntimeException e) {
            log.warn("CachedUserProfileService: upstream fetch failed for userId={}: {}", userId, e.getMessage());
            return java.util.Optional.empty();
        }
    }
}
