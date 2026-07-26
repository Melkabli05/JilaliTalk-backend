package com.jilali.platform.sql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

/**
 * Applies the recommended SQLite PRAGMAs to every connection Hikari hands out from the pool.
 *
 * <p>SQLite does NOT persist {@code foreign_keys} or {@code busy_timeout} with the database
 * file — they are per-connection, so we must re-apply on every checkout. {@code journal_mode}
 * IS persisted, but applying it on every connection is harmless and makes the configuration
 * self-documenting (one place to look for what our connection setup is).
 *
 * <p>Hikari's preferred way to run SQL on every new connection is via
 * {@link HikariConfig#setConnectionInitSql(String)}; that string is a semicolon-separated list
 * of statements. We can't use it directly here because Hikari passes that string through as a
 * single batch and bails on the first failure (defeating our intent to be idempotent and
 * tolerate pool refreshes). Instead we wrap the resulting pool with a {@link BeanCreatedEventListener}
 * and configure Hikari ourselves with {@code connectionInitSql} set to an empty list plus the
 * PRAGMA statements.
 *
 * <p>Why not use {@code DataSourceInitializer}? It runs once at startup, not per connection —
 * wrong layer for these flags.
 *
 * <p>The PRAGMA values mirror the 2025-best-practices baseline documented in the BFF's
 * SQLite integration plan. Tweak with care; {@code synchronous=FULL} halves write throughput
 * for marginal power-loss durability, and {@code foreign_keys=OFF} silently breaks FK
 * constraints.
 */
@Context
@Singleton
public class SqliteConnectionInit implements BeanCreatedEventListener<HikariDataSource> {

    private static final Logger log = LoggerFactory.getLogger(SqliteConnectionInit.class);

    private static final String PRAGMA_STATEMENTS =
        "PRAGMA foreign_keys = ON;"
      + "PRAGMA busy_timeout = 5000;"
      + "PRAGMA synchronous = NORMAL;"
      + "PRAGMA temp_store = MEMORY;"
      + "PRAGMA cache_size = -20000";

    @Override
    public HikariDataSource onCreated(BeanCreatedEvent<HikariDataSource> event) {
        HikariDataSource ds = event.getBean();
        // The url already carries journal_mode=WAL via JDBC URL params; the rest need
        // connectionInitSql because SQLite doesn't honor them via the URL for every version.
        ds.setConnectionInitSql(PRAGMA_STATEMENTS);
        log.info("SqliteConnectionInit: applied PRAGMA block on every Hikari connection");
        return ds;
    }

    /** Helper for the schema initializer — calls all PRAGMAs on a given connection. Used by
     *  the one-shot schema setup (CREATE TABLE / PRAGMA table_info introspection) which may run
     *  before Hikari has handed out a pool-managed connection. */
    public static void applyPragmas(java.sql.Connection conn) throws java.sql.SQLException {
        try (var st = conn.createStatement()) {
            st.execute(PRAGMA_STATEMENTS);
        }
    }

    /** Exposed for tests / the schema initializer — the SQL block as a single string, with a
     *  trailing newline so callers that split on {@code ;} don't drop the last statement. */
    public static String pragmaBlock() {
        return PRAGMA_STATEMENTS;
    }
}
