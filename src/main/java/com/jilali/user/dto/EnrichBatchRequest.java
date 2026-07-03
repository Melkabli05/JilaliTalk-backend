package com.jilali.user.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Request payload for batch enrichment of user profiles — used by the frontend to resolve
 * partial user data (nickname, headUrl, nationality) received over the LiveHub WebSocket without
 * burning one HTTP call per user.
 *
 * @param userIds list of HelloTalk user IDs to enrich
 */
@Serdeable
public record EnrichBatchRequest(@NotEmpty List<Long> userIds) {}
