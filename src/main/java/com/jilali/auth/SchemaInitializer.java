package com.jilali.auth;

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

/** Runs schema.sql's idempotent CREATE TABLE IF NOT EXISTS statements once on startup —
 *  no migration framework needed for three small tables at this stage. */
@Singleton
public class SchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(SchemaInitializer.class);

    private final DataSource dataSource;

    public SchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @EventListener
    void onStartup(StartupEvent event) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("schema.sql")) {
            if (in == null) throw new IOException("schema.sql not found on classpath");
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                for (String statement : withoutComments(sql).split(";")) {
                    if (!statement.isBlank()) stmt.execute(statement);
                }
            }
            log.info("Platform-auth schema ready");
        } catch (SQLException | IOException e) {
            throw new RuntimeException("Failed to initialize platform-auth schema", e);
        }
    }

    /** Strips {@code --} line comments before splitting on {@code ;} — otherwise a semicolon
     *  in ordinary comment prose (English punctuation, not SQL) gets misread as a statement
     *  boundary. */
    private static String withoutComments(String sql) {
        StringBuilder out = new StringBuilder();
        for (String line : sql.split("\n")) {
            int commentStart = line.indexOf("--");
            out.append(commentStart >= 0 ? line.substring(0, commentStart) : line).append('\n');
        }
        return out.toString();
    }
}
