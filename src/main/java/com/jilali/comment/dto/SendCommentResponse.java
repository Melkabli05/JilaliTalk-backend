package com.jilali.comment.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Returned by {@code POST /api/comments} so the frontend can reconcile its optimistic local
 * insert against a server-confirmed timestamp instead of guessing at the realtime WS echo's
 * arrival time. {@code createdAtMs} is always populated (the BFF's own send instant, the same
 * one used to build the upstream request's {@code send_time}) — {@code id} is best-effort: only
 * populated when the upstream response happens to include one, since the comment endpoint's
 * upstream response shape isn't documented and callers must not depend on it being present.
 * <p>
 * Deliberately no {@code @JsonProperty} renames — see {@code Comment}'s field naming, where several
 * such overrides aren't currently honored by the running server (confirmed by curl against the
 * live GET /comments response returning raw Java field names, not the annotated ones). Plain
 * field names sidestep that uncertainty entirely rather than adding one more unverified rename.
 */
@Serdeable
public record SendCommentResponse(
        long createdAtMs,
        @Nullable String id) {
}
