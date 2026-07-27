package com.jilali.platform.sql;

import io.micronaut.context.annotation.Value;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Creates the SQLite database's parent directory before the DataSource eager-initializes
 * on startup. Solves the production-deploy crash where {@code JILALI_DB_PATH} defaulted
 * to a relative {@code ./data/jilalitalk.db} that didn't exist in the container WORKDIR
 * (Render's WORKDIR is /home/app; relative paths are resolved against it, and there's no
 * data/ subdirectory in the production image).
 *
 * <p>Reads the same {@code JILALI_DB_PATH} env var that application.yml uses, parses out the
 * file portion of the {@code jdbc:sqlite:} URL, and ensures the parent dir exists. Runs
 * early — as a @EventListener on StartupEvent, which fires before the eager DataSource
 * bean init since the JdbcAuthSessionRepository is eagerly-initialized.
 *
 * <p>No-op if the file is in-memory (test path like {@code file::memory:?cache=shared})
 * because the parsed path won't have a parent on the local filesystem.
 */
@Singleton
public class DbDirectoryInitializer {

    private static final Logger log = LoggerFactory.getLogger(DbDirectoryInitializer.class);

    private final String jdbcUrl;

    public DbDirectoryInitializer(@Value("${datasources.default.url}") String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    @EventListener
    void onStartup(StartupEvent event) {
        try {
            ensureParentDir(jdbcUrl);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to prepare SQLite database directory from URL " + jdbcUrl, e);
        }
    }

    /** Visible for tests + the @EventListener above. Parses the file path out of the
     *  JDBC URL and creates its parent dir. Returns the resolved parent path, or null
     *  for an in-memory / shared-cache test URL that has no local file. */
    static Path ensureParentDir(String jdbcUrl) throws IOException {
        // URL format: jdbc:sqlite:[path]?PRAGMAs
        // The path may be absolute (/foo/bar.db), relative (./data/bar.db), or
        // a memory URL (file::memory:?cache=shared) which we skip.
        String prefix = "jdbc:sqlite:";
        if (!jdbcUrl.startsWith(prefix)) {
            log.warn("DbDirectoryInitializer: unexpected URL prefix '{}', skipping", jdbcUrl);
            return null;
        }
        String tail = jdbcUrl.substring(prefix.length());
        int qmark = tail.indexOf('?');
        if (qmark >= 0) tail = tail.substring(0, qmark);
        if (tail.isEmpty() || tail.startsWith("file::memory:") || tail.startsWith("memory:")) {
            log.info("DbDirectoryInitializer: in-memory URL, skipping directory creation");
            return null;
        }
        Path dbFile = Paths.get(tail);
        Path parent = dbFile.getParent();
        if (parent == null) {
            // dbFile is just a filename (no parent) — write into CWD.
            log.info("DbDirectoryInitializer: bare filename '{}', no parent to create", dbFile);
            return null;
        }
        Files.createDirectories(parent);
        log.info("DbDirectoryInitializer: ensured SQLite parent dir '{}'", parent);
        return parent;
    }
}
