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
        AuthSession session = new AuthSession(randomId(), helloTalkUid, email, jwt, deviceId, now, now.plus(SESSION_TTL));
        String sql = """
            INSERT INTO auth_session (id, hellotalk_uid, email, jwt, device_id, created_at, expires_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, session.id());
            ps.setLong(2, session.helloTalkUid());
            ps.setString(3, session.email());
            ps.setString(4, session.jwt());
            ps.setString(5, session.deviceId());
            ps.setTimestamp(6, Timestamp.from(session.createdAt()));
            ps.setTimestamp(7, Timestamp.from(session.expiresAt()));
            ps.executeUpdate();
            return session;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create auth session", e);
        }
    }

    @Override
    public Optional<AuthSession> find(String sessionId) {
        String sql = """
            SELECT id, hellotalk_uid, email, jwt, device_id, created_at, expires_at
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
            rs.getTimestamp("expires_at").toInstant());
    }

    private static String randomId() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
