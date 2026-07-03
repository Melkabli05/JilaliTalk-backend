package com.jilali.user.dto;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

/**
 * Response for batch user profile enrichment. Returns all requested profiles in one round-trip.
 * Warm entries (already in the user-info cache) are free; cold entries pay one Curve25519
 * encrypted upstream call each. Partial failures are silently dropped — only successfully
 * resolved profiles are returned.
 *
 * @param profiles list of successfully enriched UserInfo records
 */
@Serdeable
public record EnrichBatchResponse(List<UserInfo> profiles) {}
