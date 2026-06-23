package com.jilali.auth;

import jakarta.inject.Singleton;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Real HelloTalk JWTs obtained out-of-band (a real device/app login), assignable to JilaliTalk
 * accounts. There is no self-service way to mint new ones — the upstream login endpoint uses an
 * undeciphered encrypted binary protocol (bin/cc2018, see RoomRealtimeRegistry's neighbors for
 * the protocols that ARE solved: AgoraTokenCipher, EncbinUtil). A JilaliTalk account with no
 * assigned row simply falls back to {@code jilali.default-auth-token}, exactly like every
 * account did before this table existed.
 */
@Singleton
public class HelloTalkTokenPoolRepository {

    private final DataSource dataSource;

    public HelloTalkTokenPoolRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Claims the oldest unassigned pool row for this user. A no-op if the pool is empty. */
    public void assignNextAvailable(long userId) {
        String sql = """
            UPDATE hellotalk_token_pool
            SET assigned_to_user_id = ?
            WHERE id = (
                SELECT id FROM hellotalk_token_pool
                WHERE assigned_to_user_id IS NULL
                ORDER BY id
                LIMIT 1
            )
            """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to assign HelloTalk token", e);
        }
    }

    public Optional<String> findJwtForUser(long userId) {
        String sql = "SELECT jwt FROM hellotalk_token_pool WHERE assigned_to_user_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getString("jwt")) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to look up assigned HelloTalk token", e);
        }
    }

    /**
     * The real HelloTalk uid behind this JilaliTalk user's assigned token, if any. Used by
     * {@link com.jilali.realtime.RoomRealtimeRegistry} to open the LiveHub room WebSocket as
     * the viewer's own identity instead of the shared {@code jilali.default-auth-token} —
     * LiveHub's {@code /livehub/ws/conn} takes a bare {@code user_id} query param with no
     * auth header at all, so this is the only piece of identity that connection needs.
     */
    public Optional<Long> findHelloTalkUidForUser(long userId) {
        String sql = "SELECT hellotalk_uid FROM hellotalk_token_pool WHERE assigned_to_user_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getLong("hellotalk_uid")) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to look up assigned HelloTalk uid", e);
        }
    }

    /** Adds a manually-obtained real HelloTalk JWT to the pool, immediately claimable by the
     *  next registering (or currently unassigned) account. */
    public void addToken(long helloTalkUid, String jwt, String label) {
        String sql = "INSERT INTO hellotalk_token_pool (hellotalk_uid, jwt, label) VALUES (?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, helloTalkUid);
            ps.setString(2, jwt);
            ps.setString(3, label);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to add HelloTalk token to pool", e);
        }
    }
}
