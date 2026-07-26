package com.jilali.roomcontext.domain.service;

import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * SQLite-backed ghost publisher persistence — replaces the previous in-memory
 * {@code ConcurrentHashMap} in {@code GhostPublisherRegistry} so ghost publishers survive
 * BFF restarts and are visible to all instances behind a load balancer. Raw JDBC, same
 * style as {@code JdbcAuthSessionRepository}, to avoid pulling in the Micronaut Data JDBC
 * annotation processor for a single 3-column table.
 *
 * <p>Idempotency matches the previous in-memory contract exactly:
 *  - {@link #start(String, long)} is a no-op (returns {@code false}) if the user is already
 *    registered as a ghost publisher in that room.
 *  - {@link #stop(String, long)} is a no-op (returns {@code false}) if the user is not
 *    registered.
 *
 * <p>Atomicity: the {@code ghost_publisher} table's two-column primary key
 * ({@code cname}, {@code userId}) makes {@code INSERT OR IGNORE} race-safe — two concurrent
 * calls to {@link #start} for the same {@code (cname, userId)} will result in exactly one row.
 */
@Singleton
public final class JdbcGhostPublisherRepository {

    private final DataSource dataSource;

    public JdbcGhostPublisherRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Returns true if this call actually started ghost-publishing for the user. False
     *  means the (cname, userId) pair was already in the table — no-op, the second start
     *  call from a retried client doesn't re-emit a stage_join or update timestamps. */
    public boolean start(String cname, long userId) {
        // INSERT OR IGNORE: race-safe idempotency, no exception on conflict.
        // started_at + last_seen default to CURRENT_TIMESTAMP per the table schema.
        String sql = "INSERT OR IGNORE INTO ghost_publisher (cname, user_id) VALUES (?, ?)";
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cname);
            ps.setLong(2, userId);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to start ghost publisher for cname=" + cname + " userId=" + userId, e);
        }
    }

    /** Returns true if this call actually stopped ghost-publishing. False means the user
     *  wasn't registered. */
    public boolean stop(String cname, long userId) {
        String sql = "DELETE FROM ghost_publisher WHERE cname = ? AND user_id = ?";
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cname);
            ps.setLong(2, userId);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to stop ghost publisher for cname=" + cname + " userId=" + userId, e);
        }
    }

    /** Updates {@code last_seen} to current time without touching any other column. Called
     *  on every synthetic event the BFF emits so the cleanup job can detect abandoned
     *  ghost publishers. */
    public void touchLastSeen(String cname, long userId) {
        String sql = "UPDATE ghost_publisher SET last_seen = CURRENT_TIMESTAMP WHERE cname = ? AND user_id = ?";
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cname);
            ps.setLong(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to touch last_seen for ghost publisher cname=" + cname + " userId=" + userId, e);
        }
    }

    /** Returns true if the user is currently registered as a ghost publisher in this room. */
    public boolean isGhostPublishing(String cname, long userId) {
        String sql = "SELECT 1 FROM ghost_publisher WHERE cname = ? AND user_id = ? LIMIT 1";
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cname);
            ps.setLong(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to check ghost publisher cname=" + cname + " userId=" + userId, e);
        }
    }

    /** All active ghost publishers for one room. Used by the late-joiner re-emit path:
     *  when a new client subscribes via {@code /ws/ht/{cname}}, we walk this list and re-emit
     *  a synthetic stage_join for each so they see the current publishers. Returns an
     *  empty list (never null) when no one is currently ghost-publishing in the room. */
    public List<GhostPublisherEntity> listByRoom(String cname) {
        String sql = "SELECT cname, user_id, started_at, last_seen FROM ghost_publisher WHERE cname = ? ORDER BY started_at";
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cname);
            try (ResultSet rs = ps.executeQuery()) {
                // Simple loop rather than StreamSupport — ResultSet doesn't have a native
                // Spliterator in the JDK (Spliterators.spliteratorUnknownSize takes a
                // Iterator<? extends T>, not a ResultSet directly). The expected cardinality
                // is "a handful" per room, so a flat loop is also clearer.
                List<GhostPublisherEntity> out = new java.util.ArrayList<>(4);
                while (rs.next()) out.add(map(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list ghost publishers for cname=" + cname, e);
        }
    }

    /** Purges rows with {@code last_seen} older than the cutoff. Called by the scheduled
     *  cleanup job — only on the instance holding the scheduler_lock. Returns the count
     *  of rows deleted, useful for log/metric reporting. */
    public long purgeOlderThan(Instant cutoff) {
        String sql = "DELETE FROM ghost_publisher WHERE last_seen < ?";
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, java.sql.Timestamp.from(cutoff));
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to purge stale ghost publishers", e);
        }
    }

    private GhostPublisherEntity map(ResultSet rs) throws SQLException {
        return new GhostPublisherEntity(
            rs.getString("cname"),
            rs.getLong("user_id"),
            rs.getTimestamp("started_at").toInstant(),
            rs.getTimestamp("last_seen").toInstant());
    }
}
