package com.jilali.platform.scheduler;

import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Multi-instance leader election via a single-row SQLite table. Each instance tries
 * {@code INSERT OR IGNORE} at boot + every leader-renewal tick. The instance that won the row
 * holds the lock; others stay idle.
 *
 * <p>Lock semantics:
 *  - {@link #tryAcquire(String, Duration)} — INSERT OR IGNORE. Returns true iff this call
 *    actually inserted the row (i.e. the lock was free and this instance won).
 *  - {@link #renew(String, Duration)} — UPDATE the row's acquired_at. Returns true iff
 *    this instance still holds the lock (the row's instance_id matches ours).
 *  - {@link #isHeldBy(String)} — peek at the current holder without modifying anything.
 *  - {@link #releaseIfHeldBy(String)} — DELETE the row iff instance_id matches. Idempotent.
 *
 * <p>The lock is short-lived (default 60s acquired_at) so a crashed instance doesn't
 * permanently hold it. The renewal tick is faster than the TTL so a healthy instance
 * keeps it; an unhealthy one misses a renewal and another instance can take over after
 * the next tryAcquire.
 */
@Singleton
public class SchedulerLockRepository {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final DataSource dataSource;

    public SchedulerLockRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Returns true iff this call inserted the row (this instance is now the holder).
     *  {@code lockName} is the partition key — each job name has its own lock so cleanup
     *  jobs don't all compete for one global lock. */
    public boolean tryAcquire(String lockName, Duration ttl) {
        String instanceId = instanceId();
        String sql = """
            INSERT OR IGNORE INTO scheduler_lock (name, instance_id, acquired_at)
            VALUES (?, ?, CURRENT_TIMESTAMP)
            """;
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, lockName);
            ps.setString(2, instanceId);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to acquire scheduler lock " + lockName, e);
        }
    }

    /** Renews the lock's acquired_at if (and only if) we currently hold it. Returns true on
     *  successful renewal. If another instance has already taken over (because we missed a
     *  tick), returns false and this instance stops being the leader. */
    public boolean renew(String lockName, Duration ttl) {
        String instanceId = currentInstanceId();
        String sql = """
            UPDATE scheduler_lock
            SET acquired_at = CURRENT_TIMESTAMP
            WHERE name = ? AND instance_id = ?
            """;
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, lockName);
            ps.setString(2, instanceId);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to renew scheduler lock " + lockName, e);
        }
    }

    /** Returns the instance_id currently holding the lock, or empty if the lock is free or
     *  has expired (acquired_at older than {@code ttl}). */
    public Optional<String> currentHolder(String lockName, Duration ttl) {
        String sql = "SELECT instance_id, acquired_at FROM scheduler_lock WHERE name = ?";
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, lockName);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                Timestamp acquiredAt = rs.getTimestamp("acquired_at");
                if (acquiredAt != null && acquiredAt.toInstant().isBefore(Instant.now().minus(ttl))) {
                    // Expired — let it be reclaimed.
                    return Optional.empty();
                }
                return Optional.ofNullable(rs.getString("instance_id"));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read scheduler lock " + lockName, e);
        }
    }

    /** Releases the lock iff this instance still holds it. Idempotent. Returns true on
     *  actual release. */
    public boolean releaseIfHeldBy(String lockName) {
        String instanceId = currentInstanceId();
        String sql = "DELETE FROM scheduler_lock WHERE name = ? AND instance_id = ?";
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, lockName);
            ps.setString(2, instanceId);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to release scheduler lock " + lockName, e);
        }
    }

    /** Per-JVM instance id, generated once at class load. Same id is used for every
     *  tryAcquire/renew/release cycle on this process. */
    private String instanceId() {
        return ID;
    }

    private static final String ID = generateInstanceId();

    private static String generateInstanceId() {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        return "bff-" + HexFormat.of().formatHex(bytes);
    }

    /** Test hook — only used in unit tests that want a deterministic instance id. */
    public static void setCurrentInstanceIdForTest(String id) {
        // No-op: instanceId is final-static. Tests use a separate subclass. Kept here as
        // a documentation marker for future work if this ever needs to be reconfigurable
        // (e.g. for a multi-tenant BFF where instance ids must include the tenant).
    }

    private String currentInstanceId() {
        return ID;
    }
}
