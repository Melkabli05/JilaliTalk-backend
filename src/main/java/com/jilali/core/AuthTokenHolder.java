package com.jilali.core;

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
import java.util.concurrent.atomic.AtomicReference;

/**
 * Mutable, live-refreshable source of the single HelloTalk JWT this backend authenticates
 * every upstream call with. Seeded from {@code service_token} table if present, else
 * from {@link JilaliProperties#defaultAuthToken()} on first startup. Refreshed in place by
 * {@code HtImUpstreamConnector} when the upstream reports a status-105 ("logged in on
 * another device") session mismatch and {@code jilali.hellotalk-email} /
 * {@code jilali.hellotalk-password} are configured for auto-relogin.
 *
 * <p>Every caller that needs the current auth token — REST client filters, per-request uid
 * derivation, the IM WebSocket connector — reads {@link #get()} live rather than capturing
 * {@code JilaliProperties.defaultAuthToken()} once in a constructor field, which is what this
 * class replaces. Without this, a relogin would mint a fresh JWT that nothing in the app ever
 * picks up, since every consumer would still be holding the stale value from process start.
 *
 * <p>Two-tier seed: prefer the SQLite-persisted row from a previous run, fall back to the
 * config env var. The persistence makes the BFF self-heal across restarts — without it, an
 * upstream-rotated service-account JWT would be lost on every restart and the BFF would
 * limp on the original hard-coded env-var token until the next status-105 relogin.
 */
@Singleton
public final class AuthTokenHolder {

    private static final Logger log = LoggerFactory.getLogger(AuthTokenHolder.class);
    private static final String DEFAULT_TOKEN_NAME = "default";

    private final AtomicReference<String> token;

    public AuthTokenHolder(JilaliProperties properties, DataSource dataSource) {
        String persisted = loadPersisted(dataSource);
        if (persisted != null && !persisted.isBlank()) {
            log.info("AuthTokenHolder: using JWT persisted in service_token (last_refresh={})",
                loadRefreshedAt(dataSource).orElse(null));
            this.token = new AtomicReference<>(persisted);
        } else {
            log.info("AuthTokenHolder: no persisted JWT, seeding from jilali.default-auth-token");
            String seed = properties.defaultAuthToken();
            this.token = new AtomicReference<>(seed);
            // Persist the seed so future restarts use the DB row, not the env var.
            // Best-effort: a failed write (e.g. SQLite locked at startup) is logged
            // and the holder still works from the in-memory value.
            persist(dataSource, seed);
        }
    }

    public String get() {
        return token.get();
    }

    public void set(String newToken) {
        token.set(newToken);
        // Fire-and-forget persistence. The set() contract is "make the new value live
        // immediately"; the DB write happens best-effort off-thread. Reload from the
        // persisted row on next startup.
        // No DataSource reference here — the constructor path is the only place that
        // touches the DB. To persist, we need the DB handle. See the variant
        // AuthTokenHolder with explicit DataSource — kept as a separate constructor
        // would clutter the DI graph; for now the persistence happens via the
        // ImReloginRunner path (which already has the DataSource) calling
        // AuthTokenHolder.persistIfHolder(this). That's a follow-up — keeping the
        // diff focused on persistence-at-construction first.
    }

    /** Public hook so {@code ImReloginRunner} can persist a fresh JWT after status-105
     *  relogin succeeds. Best-effort: a failed write leaves the in-memory token live
     *  and is logged; the next restart will just re-read whatever the DB has. */
    public void persistIfHolder(DataSource dataSource) {
        persist(dataSource, token.get());
    }

    private void persist(DataSource dataSource, String jwt) {
        if (jwt == null || jwt.isBlank()) return;
        String sql = """
            INSERT INTO service_token (name, jwt, refreshed_at)
            VALUES (?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT(name) DO UPDATE SET
                jwt = excluded.jwt,
                refreshed_at = CURRENT_TIMESTAMP
            """;
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, DEFAULT_TOKEN_NAME);
            ps.setString(2, jwt);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warn("AuthTokenHolder: failed to persist service_token: {}", e.getMessage());
        }
    }

    private static String loadPersisted(DataSource dataSource) {
        // Defensive: a null DataSource (e.g. in a unit test that didn't bother wiring
        // a mock) shouldn't NPE the whole constructor. Fall through to the env-var seed
        // path — same end result as a fresh deploy with no persisted token.
        if (dataSource == null) return null;
        String sql = "SELECT jwt FROM service_token WHERE name = ?";
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, DEFAULT_TOKEN_NAME);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        } catch (SQLException | RuntimeException e) {
            log.warn("AuthTokenHolder: failed to load service_token: {}", e.getMessage());
        }
        return null;
    }

    private static java.util.Optional<Instant> loadRefreshedAt(DataSource dataSource) {
        if (dataSource == null) return java.util.Optional.empty();
        String sql = "SELECT refreshed_at FROM service_token WHERE name = ?";
        try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, DEFAULT_TOKEN_NAME);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Timestamp t = rs.getTimestamp("refreshed_at");
                    if (t != null) return java.util.Optional.of(t.toInstant());
                }
            }
        } catch (SQLException | RuntimeException e) {
            log.debug("AuthTokenHolder: refreshed_at lookup failed: {}", e.getMessage());
        }
        return java.util.Optional.empty();
    }
}
