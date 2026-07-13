package com.jilali.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Decoded body of {@code GET livehub/user/profile?cname=...&user_id=...} — the room-scoped,
 * per-target-user profile lookup. Wire format is {@code bin/cc2018} ({@link com.jilali.crypto.Cc2018Cipher}),
 * not JSON on the upstream side; {@link com.jilali.client.JilaliGateway#roomUserProfile} decodes it
 * before this record is deserialized from the resulting plaintext.
 *
 * <p>Only {@code follow_stat} is mapped — the response also carries the target user's full base
 * profile, gift wall, pay info, etc., none of which any current caller needs. Add fields here if a
 * future caller needs them; {@code @JsonIgnoreProperties(ignoreUnknown = true)} means anything
 * unmapped is silently dropped rather than failing deserialization.
 */
@Serdeable
@JsonIgnoreProperties(ignoreUnknown = true)
public record RoomUserProfileResponse(
    int code,
    @Nullable String msg,
    @Nullable Data data
) {
    @Serdeable
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(
        @JsonProperty("follow_stat") @Nullable FollowStat followStat
    ) {}

    /**
     * {@code status} is the viewer's relation to the target: 0 = not following, 1 = following,
     * 2 = mutual/friend. {@code folowerStatus} (upstream's own spelling — not a typo introduced
     * here) is the reverse direction: whether the target follows the viewer back.
     */
    @Serdeable
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FollowStat(
        int status,
        @JsonProperty("folower_status") int folowerStatus
    ) {}
}
