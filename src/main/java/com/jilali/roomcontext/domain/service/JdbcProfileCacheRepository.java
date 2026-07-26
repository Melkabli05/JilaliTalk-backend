package com.jilali.roomcontext.domain.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.roomcontext.infrastructure.dto.user.UserInfo;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

/**
 * SQLite-backed profile cache. Replaces the previous in-process Caffeine {@code user-info}
 * cache so multi-instance BFFs behind a load balancer share one profile cache. The shape
 * mirrors the existing 24h expire-after-access policy (see {@code application.yml}) — the
 * scheduled cleanup job deletes any row whose {@code last_used_at} is older than 24h.
 *
 * <p>Storage format is the full {@link UserInfo} record serialized as JSON in the
 * {@code payload_json} column. SQLite 3.38+ has built-in JSON1 functions, so future
 * enhancements (e.g. "find all users with VIP > 50") can index into the blob without
 * a separate parse — for now we treat the blob as opaque and round-trip through Jackson.
 *
 * <p>Read path is two-step: {@code SELECT payload_json} (O(1) by primary key), then
 * Jackson parse. On miss, the caller falls back to upstream and calls
 * {@link #upsert(long, UserInfo)} — the write path uses SQLite's
 * {@code INSERT … ON CONFLICT(user_id) DO UPDATE} for atomic read-or-write without the
 * SELECT-then-INSERT race.
 */
@Singleton
public final class JdbcProfileCacheRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcProfileCacheRepository.class);

    private final DataSource dataSource;
    private final ObjectMapper om;

    public JdbcProfileCacheRepository(DataSource dataSource, @Named("io") ObjectMapper om) {
        this.dataSource = dataSource;
        this.om = om;
    }

    /** Returns the cached profile for the user, refreshing last_used_at as a side-effect.
     *  Empty if the user isn't cached, the row is past the TTL, or the JSON fails to
     *  parse (corruption — the row will be reaped on the next cleanup pass). */
    public Optional<UserInfo> find(long userId) {
        // The ON DELETE / ON UPDATE for last_used_at happens in two steps: SELECT first to
        // deserialize, then UPDATE if the row exists. A single statement with a RETURNING
        // clause would be more elegant, but SQLite 3.39+ RETURNING is supported and this
        // version ships it — keeping the two-statement form for now for clarity.
        String sql = "SELECT payload_json, last_used_at FROM profile_cache WHERE user_id = ?";
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                Timestamp lastUsed = rs.getTimestamp("last_used_at");
                if (lastUsed != null && lastUsed.toInstant().isBefore(Instant.now().minusSeconds(24 * 3600))) {
                    // Stale — drop the row so a fresh upstream fetch repopulates it.
                    deleteStale(conn, userId);
                    return Optional.empty();
                }
                UserInfo profile = parse(rs.getString("payload_json"));
                if (profile == null) return Optional.empty();
                // Touch last_used_at on every successful read.
                touchLastUsed(conn, userId);
                return Optional.of(profile);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read profile_cache for userId=" + userId, e);
        }
    }

    /** Atomic upsert: insert on conflict, replace payload_json + cached_at, keep last_used_at.
     *  SQLite's INSERT … ON CONFLICT replaces the SELECT-then-INSERT-or-UPDATE race
     *  with a single statement. Returns true if a new row was inserted, false if an
     *  existing row was updated (informational, mostly for tests). */
    public boolean upsert(long userId, UserInfo profile) {
        String json;
        try {
            json = om.writeValueAsString(profile);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize UserInfo for userId=" + userId, e);
        }
        String sql = """
            INSERT INTO profile_cache (user_id, payload_json, cached_at, last_used_at)
            VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT(user_id) DO UPDATE SET
                payload_json = excluded.payload_json,
                cached_at = CURRENT_TIMESTAMP
            """;
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, json);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to upsert profile_cache for userId=" + userId, e);
        }
    }

    /** Purges rows with {@code last_used_at} older than the cutoff. Returns the row count
     *  deleted. Called by the scheduled cleanup job on the instance that holds the
     *  scheduler_lock. */
    public long purgeOlderThan(Instant cutoff) {
        String sql = "DELETE FROM profile_cache WHERE last_used_at < ?";
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(cutoff));
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to purge stale profile_cache rows", e);
        }
    }

    private void touchLastUsed(Connection conn, long userId) throws SQLException {
        // Best-effort — don't propagate a touch failure to the read path. The next
        // successful read or the next periodic write will fix last_used_at.
        try (PreparedStatement ps = conn.prepareStatement(
            "UPDATE profile_cache SET last_used_at = CURRENT_TIMESTAMP WHERE user_id = ?")) {
            ps.setLong(1, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.debug("Profile cache touch failed for userId={}: {}", userId, e.getMessage());
        }
    }

    private void deleteStale(Connection conn, long userId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            "DELETE FROM profile_cache WHERE user_id = ?")) {
            ps.setLong(1, userId);
            ps.executeUpdate();
        }
    }

    private UserInfo parse(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return om.readValue(json, UserInfo.class);
        } catch (JsonProcessingException e) {
            log.warn("Corrupt profile_cache entry: {}", e.getMessage());
            return null;
        }
    }
}
