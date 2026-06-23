package com.jilali.auth;

import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Opaque session ids only — never a JWT. The browser holds this (as an HttpOnly cookie set by
 * AuthController) and nothing else; the real HelloTalk credential it resolves to stays
 * server-side for the lifetime of the request (see SessionAuthClientFilter).
 */
@Singleton
public class SessionRepository {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long SESSION_TTL_DAYS = 30;

    private final DataSource dataSource;

    public SessionRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public String create(long userId) {
        String sessionId = randomId();
        Instant expiresAt = Instant.now().plus(SESSION_TTL_DAYS, ChronoUnit.DAYS);
        String sql = "INSERT INTO app_session (id, user_id, expires_at) VALUES (?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ps.setLong(2, userId);
            ps.setTimestamp(3, Timestamp.from(expiresAt));
            ps.executeUpdate();
            return sessionId;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create session", e);
        }
    }

    public Optional<Long> resolveUserId(String sessionId) {
        String sql = "SELECT user_id FROM app_session WHERE id = ? AND expires_at > CURRENT_TIMESTAMP";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getLong("user_id")) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to resolve session", e);
        }
    }

    public void delete(String sessionId) {
        String sql = "DELETE FROM app_session WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete session", e);
        }
    }

    private static String randomId() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
