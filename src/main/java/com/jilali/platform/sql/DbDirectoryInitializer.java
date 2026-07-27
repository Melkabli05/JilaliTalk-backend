package com.jilali.platform.sql;

import io.micronaut.context.annotation.Value;
import jakarta.annotation.PostConstruct;
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
 * to a path whose parent didn't exist in the container WORKDIR.
 *
 * <p>Runs as a {@code @PostConstruct} (not an {@code @EventListener<StartupEvent>}) so
 * the dir is created during the singleton's own construction, which is the earliest
 * Java-level point we control. The DataSource bean is constructed on first use, and
 * since the JdbcAuthSessionRepository that depends on it is itself a singleton, the
 * DataSource gets constructed lazily — so our @PostConstruct here runs first.
 *
 * <p>The Dockerfile's runtime layer also pre-creates /home/app/data via {@code RUN
 * mkdir -p}, so the dir exists at JVM start regardless. Both the image build and the
 * JVM @PostConstruct create the same dir, which is idempotent — only adds robustness.
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

    @PostConstruct
    void onInit() {
        try {
            ensureParentDir(jdbcUrl);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to prepare SQLite database directory from URL " + jdbcUrl, e);
        }
    }

    /** Visible for tests + the @PostConstruct above. Parses the file path out of the
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
            log.info("DbDirectoryInitializer: bare filename '{}', no parent to create", dbFile);
            return null;
        }
        Files.createDirectories(parent);
        log.info("DbDirectoryInitializer: ensured SQLite parent dir '{}'", parent);
        return parent;
    }
}
