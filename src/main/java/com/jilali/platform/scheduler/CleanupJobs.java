package com.jilali.platform.scheduler;

import com.jilali.roomcontext.domain.service.JdbcGhostPublisherRepository;
import com.jilali.roomcontext.domain.service.JdbcProfileCacheRepository;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;

/**
 * Scheduled cleanup jobs for the BFF's persistent state. Two of them:
 *  - {@link #purgeStaleGhostPublishers()} — every 30 minutes, drops ghost publishers
 *    whose {@code last_seen} is older than 1 hour. Without this, a ghost publisher who
 *    closes their tab without calling /stop would leave a row in the table forever.
 *  - {@link #purgeStaleProfileCache()} — every 6 hours, drops profile-cache rows whose
 *    {@code last_used_at} is older than 24 hours. The 24h expire-after-access is the
 *    same window the previous Caffeine cache used.
 *
 * <p>Both jobs are gated on a per-job SQLite leader lock. With N BFF instances behind a
 * load balancer, exactly one of them holds the lock at any moment — the others see
 * {@code currentHolder != this instance id} and skip the work. The lock TTL is 5 minutes,
 * the renewal tick is 60 seconds. A crashed leader's lock expires after 5 minutes, another
 * instance acquires it on the next tryAcquire, and the work resumes.
 */
@Singleton
public class CleanupJobs {

    private static final Logger log = LoggerFactory.getLogger(CleanupJobs.class);

    private static final String GHOST_PUBLISHER_LOCK = "purge_ghost_publishers";
    private static final String PROFILE_CACHE_LOCK = "purge_profile_cache";

    private static final Duration LOCK_TTL = Duration.ofMinutes(5);
    private static final Duration GHOST_PUBLISHER_TTL = Duration.ofHours(1);
    private static final Duration PROFILE_CACHE_TTL = Duration.ofHours(24);

    private final SchedulerLockRepository locks;
    private final JdbcGhostPublisherRepository ghostPublishers;
    private final JdbcProfileCacheRepository profileCache;

    public CleanupJobs(
        SchedulerLockRepository locks,
        JdbcGhostPublisherRepository ghostPublishers,
        JdbcProfileCacheRepository profileCache
    ) {
        this.locks = locks;
        this.ghostPublishers = ghostPublishers;
        this.profileCache = profileCache;
    }

    /** Every 30 minutes: renew our lock, run purge if we still hold it. */
    @Scheduled(fixedDelay = "30m", initialDelay = "1m")
    void leaderAndPurgeGhostPublishers() {
        if (!ensureLeadership(GHOST_PUBLISHER_LOCK)) return;
        Instant cutoff = Instant.now().minus(GHOST_PUBLISHER_TTL);
        long deleted = ghostPublishers.purgeOlderThan(cutoff);
        if (deleted > 0) {
            log.info("CleanupJobs: purged {} stale ghost publisher rows (cutoff={})", deleted, cutoff);
        }
    }

    /** Every 6 hours: same leader pattern. */
    @Scheduled(fixedDelay = "6h", initialDelay = "5m")
    void leaderAndPurgeProfileCache() {
        if (!ensureLeadership(PROFILE_CACHE_LOCK)) return;
        Instant cutoff = Instant.now().minus(PROFILE_CACHE_TTL);
        long deleted = profileCache.purgeOlderThan(cutoff);
        if (deleted > 0) {
            log.info("CleanupJobs: purged {} stale profile_cache rows (cutoff={})", deleted, cutoff);
        }
    }

    /** Acquire the lock if free; renew if we already hold it. Returns true iff this
     *  instance currently holds the lock. */
    private boolean ensureLeadership(String lockName) {
        if (locks.tryAcquire(lockName, LOCK_TTL)) {
            log.info("CleanupJobs: acquired scheduler lock '{}'", lockName);
            return true;
        }
        if (locks.renew(lockName, LOCK_TTL)) {
            return true;
        }
        // Couldn't acquire or renew — another instance is the leader, or our lock has
        // expired and we're racing. Try once more to recover from a crash-then-restart.
        if (locks.tryAcquire(lockName, LOCK_TTL)) {
            log.info("CleanupJobs: re-acquired scheduler lock '{}' after expiry", lockName);
            return true;
        }
        log.debug("CleanupJobs: skipping {} — another instance is the leader", lockName);
        return false;
    }
}
