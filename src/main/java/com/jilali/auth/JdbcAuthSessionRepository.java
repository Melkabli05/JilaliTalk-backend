package com.jilali.auth;

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
 * Raw JDBC against the H2 file store — no JPA (see the README's stated "no persistence
 * framework" position; three columns on one table doesn't earn an ORM).
 */
@Singleton
public final class JdbcAuthSessionRepository implements AuthSessionRepository {

    private static final Duration SESSION_TTL = Duration.ofDays(30);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final DataSource dataSource;

    public JdbcAuthSessionRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public AuthSession create(long helloTalkUid, String email, String jwt, String deviceId) {
        Instant now = Instant.now();
        AuthSession session = new AuthSession(randomId(), helloTalkUid, email, jwt, deviceId, now, now.plus(SESSION_TTL), now, now);
        String sql = """
            INSERT INTO auth_session (id, hellotalk_uid, email, jwt, device_id, created_at, expires_at, refreshed_at, last_seen)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, session.id());
            ps.setLong(2, session.helloTalkUid());
            ps.setString(3, session.email());
            ps.setString(4, session.jwt());
            ps.setString(5, session.deviceId());
            ps.setTimestamp(6, Timestamp.from(session.createdAt()));
            ps.setTimestamp(7, Timestamp.from(session.expiresAt()));
            ps.setTimestamp(8, Timestamp.from(session.refreshedAt()));
            ps.setTimestamp(9, Timestamp.from(session.lastSeen()));
            ps.executeUpdate();
            return session;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create auth session", e);
        }
    }

    @Override
    public Optional<AuthSession> find(String sessionId) {
        String sql = """
            SELECT id, hellotalk_uid, email, jwt, device_id, created_at, expires_at, refreshed_at, last_seen
            FROM auth_session WHERE id = ?
            """;
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                AuthSession session = map(rs);
                return session.isExpired(Instant.now()) ? Optional.empty() : Optional.of(session);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to look up auth session", e);
        }
    }

    @Override
    public void updateJwt(String sessionId, String newJwt) {
        // Two writes in one statement: swap the JWT and bump refreshed_at to current time
        // (CURRENT_TIMESTAMP is UTC per SQLite default). Idempotent on missing id — SQLite
        // returns 0 affected rows for a no-op rather than erroring.
        String sql = "UPDATE auth_session SET jwt = ?, refreshed_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newJwt);
            ps.setString(2, sessionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update auth session JWT", e);
        }
    }

    @Override
    public void touchLastSeen(String sessionId) {
        // Single-column update — used by the cleanup job to identify truly abandoned sessions
        // (sessions that haven't had any outbound upstream call in N days). Refreshed_at
        // would be a misnomer — this column tracks the BFF's last observation of activity
        // for this session, not necessarily the last JWT refresh — so we use a separate column
        // to keep the semantics clear.
        String sql = "UPDATE auth_session SET last_seen = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to touch auth session last_seen", e);
        }
    }

    @Override
    public void delete(String sessionId) {
        String sql = "DELETE FROM auth_session WHERE id = ?";
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete auth session", e);
        }
    }

    private static AuthSession map(ResultSet rs) throws SQLException {
        return new AuthSession(
            rs.getString("id"),
            rs.getLong("hellotalk_uid"),
            rs.getString("email"),
            rs.getString("jwt"),
            rs.getString("device_id"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("expires_at").toInstant(),
            nullableTimestamp(rs, "refreshed_at"),
            nullableTimestamp(rs, "last_seen"));
    }

    /** Reads a possibly-null TIMESTAMP column and returns either the Instant or null. Used for
     *  columns that were added after some rows were written (refreshed_at, last_seen) — older
     *  rows have NULL there, which the default rs.getTimestamp() maps to a NullPointerException
     *  when .toInstant() is called. getObject with a class hint is the only JDBC-portable
     *  null-safe way. */
    private static java.time.Instant nullableTimestamp(ResultSet rs, String col) throws SQLException {
        var t = rs.getTimestamp(col);
        return t == null ? null : t.toInstant();
    }

    private static String randomId() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
