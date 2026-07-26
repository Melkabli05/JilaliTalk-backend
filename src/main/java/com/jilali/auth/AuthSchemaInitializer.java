package com.jilali.auth;

import com.jilali.platform.sql.SchemaGuard;
import com.jilali.platform.sql.SqliteConnectionInit;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * Runs {@code schema.sql}'s idempotent {@code CREATE TABLE IF NOT EXISTS} once on startup —
 * no migration framework needed at this scale, but the conditional column-adds require
 * an explicit guard because SQLite does not support {@code ADD COLUMN IF NOT EXISTS}.
 *
 * <p>Three idempotent operations:
 *  1. Run {@code schema.sql} as a single batch of statements (CREATE TABLE IF NOT EXISTS).
 *  2. For each legacy column that the new code needs but the historical schema didn't have
 *     (e.g. {@code refreshed_at}, {@code last_seen}), check via {@code PRAGMA table_info} and
 *     issue the {@code ALTER TABLE ADD COLUMN} only if absent.
 *  3. Apply the SQLite PRAGMAs on the same connection used for DDL, so the introspection
 *     sees them too (foreign_keys in particular affects how {@code PRAGMA table_info} works
 *     on some versions).
 */
@Singleton
public final class AuthSchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(AuthSchemaInitializer.class);

    private final DataSource dataSource;

    public AuthSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @EventListener
    void onStartup(StartupEvent event) {
        try (Connection conn = dataSource.getConnection()) {
            // PRAGMAs first so the CREATE TABLE block sees the same connection settings
            // that the rest of the app will see. (foreign_keys is connection-scoped, so this
            // only affects the very next introspection on this same connection.)
            SqliteConnectionInit.applyPragmas(conn);
            runSchemaFile(conn);
            addMissingColumns(conn);
            log.info("Auth schema ready");
        } catch (SQLException | IOException e) {
            throw new IllegalStateException("Failed to initialize auth schema", e);
        }
    }

    private void runSchemaFile(Connection conn) throws SQLException, IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("schema.sql")) {
            if (in == null) {
                throw new IOException("schema.sql not found on classpath");
            }
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            try (Statement stmt = conn.createStatement()) {
                for (String statement : withoutComments(sql).split(";")) {
                    if (!statement.isBlank()) {
                        stmt.execute(statement);
                    }
                }
            }
        }
    }

    /** Conditional ALTER TABLE for the two new columns added at runtime. Each is a no-op on
     *  databases that already have the column (old deploys, fresh deploys from this version).
     *  Order matters: columns are added in the same order they're listed in the AuthSession
     *  record's positional constructor. */
    private void addMissingColumns(Connection conn) throws SQLException {
        addColumnIfMissing(conn, "auth_session", "refreshed_at", "TIMESTAMP");
        addColumnIfMissing(conn, "auth_session", "last_seen", "TIMESTAMP");
    }

    /** Generic "add a column if it isn't there yet" helper. We don't use a default value
     *  here because nullable TIMESTAMP is exactly what we want — pre-existing rows get NULL,
     *  which the AuthSession.record maps to a Java null Instant. */
    private void addColumnIfMissing(Connection conn, String table, String column, String type) throws SQLException {
        if (SchemaGuard.columnExists(conn, table, column)) {
            log.debug("Schema column {}.{} already present, skipping ALTER", table, column);
            return;
        }
        try (Statement stmt = conn.createStatement()) {
            // table is from a hardcoded internal list, not user input — safe to interpolate.
            // The column/type pair is also hardcoded above (no caller-supplied data).
            stmt.execute(String.format("ALTER TABLE %s ADD COLUMN %s %s", table, column, type));
            log.info("Schema: added column {}.{} ({})", table, column, type);
        }
    }

    /** Strips {@code --} line comments before splitting on {@code ;} — a semicolon in ordinary
     *  comment prose would otherwise be misread as a statement boundary. */
    private static String withoutComments(String sql) {
        StringBuilder out = new StringBuilder();
        for (String line : sql.split("\n")) {
            int commentStart = line.indexOf("--");
            out.append(commentStart >= 0 ? line.substring(0, commentStart) : line).append('\n');
        }
        return out.toString();
    }

    /** Used by tests that need a fresh in-memory database name per case so concurrent runs
     *  don't share the on-disk file. SQLite honors {@code file::memory:?cache=shared} for
     *  the test-process lifetime. */
    public static String inMemoryDbName() {
        return "file:test-" + UUID.randomUUID() + "?mode=memory&cache=shared";
    }
}
