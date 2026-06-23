package com.jilali.auth;

import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

@Singleton
public class UserRepository {

    private final DataSource dataSource;

    public UserRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Optional<AppUser> findByEmail(String email) {
        String sql = "SELECT id, email, password_hash, nickname, created_at FROM app_user WHERE email = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to look up user by email", e);
        }
    }

    public Optional<AppUser> findById(long id) {
        String sql = "SELECT id, email, password_hash, nickname, created_at FROM app_user WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to look up user by id", e);
        }
    }

    public AppUser create(String email, String passwordHash, String nickname) {
        String sql = "INSERT INTO app_user (email, password_hash, nickname) VALUES (?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, email);
            ps.setString(2, passwordHash);
            ps.setString(3, nickname);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return findById(keys.getLong(1)).orElseThrow();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create user", e);
        }
    }

    private AppUser map(ResultSet rs) throws SQLException {
        return new AppUser(
            rs.getLong("id"),
            rs.getString("email"),
            rs.getString("password_hash"),
            rs.getString("nickname"),
            rs.getTimestamp("created_at").toInstant());
    }
}
