package com.jilali.roomcontext.domain.service;

import java.time.Instant;

/**
 * Plain record for one ghost-publisher row. Not annotated with {@code @MappedEntity} — the
 * raw-JDBC {@link JdbcGhostPublisherRepository} does the row mapping by hand, no Micronaut
 * Data annotation processor needed for this single 4-column table. Kept as a record so the
 * JdbcGhostPublisherRepository and any future Micronaut-Data migration can share the same
 * shape.
 *
 * <p>{@code startedAt} is set by the database ({@code DEFAULT CURRENT_TIMESTAMP} in
 * {@code schema.sql}); the application never writes it. {@code lastSeen} is touched on every
 * synthetic event the BFF emits, and the scheduled cleanup job deletes rows whose
 * {@code lastSeen} is older than the configured TTL (1 hour by default).
 */
public record GhostPublisherEntity(
    String cname,
    long userId,
    Instant startedAt,
    Instant lastSeen
) {
    public GhostPublisherEntity {
        if (cname == null || cname.isBlank()) {
            throw new IllegalArgumentException("cname required");
        }
        if (userId <= 0) {
            throw new IllegalArgumentException("userId required");
        }
    }
}
