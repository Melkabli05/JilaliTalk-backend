package com.jilali.realtime.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

/**
 * Realtime events from the AI captioning / subtitle channel of the LiveHub socket. Same physical
 * WebSocket as {@link RoomRealtimeEvent}, but a different {@code notify_info} shape (the Android
 * side calls this {@code LiveCCNotify}) — and a separate stream of {@code notify_type} integers
 * (1, 2, 3, 4, 6, 12 / 0xc, plus the "kill" set {3, 5, 7, 9, 10, 11} that resets subtitle state).
 * The dispatcher decides which mapper to invoke based on {@code notify_info} shape, not on
 * {@code notify_type} — the two channels reuse the same integer namespace.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    // Stream lifecycle
    @JsonSubTypes.Type(value = RoomCcRealtimeEvent.SubtitleStart.class,   name = "subtitle_start"),
    @JsonSubTypes.Type(value = RoomCcRealtimeEvent.SubtitleEnd.class,     name = "subtitle_end"),
    @JsonSubTypes.Type(value = RoomCcRealtimeEvent.SubtitleDisabled.class, name = "subtitle_disabled"),
    // Per-line content
    @JsonSubTypes.Type(value = RoomCcRealtimeEvent.SubtitleLine.class,    name = "subtitle_line"),
    // User-level toggles
    @JsonSubTypes.Type(value = RoomCcRealtimeEvent.SubtitleExperienceActivated.class, name = "subtitle_experience_activated"),
    // Expiry (no more captions available)
    @JsonSubTypes.Type(value = RoomCcRealtimeEvent.SubtitleExpired.class, name = "subtitle_expired"),
    // Fallbacks
    @JsonSubTypes.Type(value = RoomCcRealtimeEvent.Raw.class,             name = "raw"),
    @JsonSubTypes.Type(value = RoomCcRealtimeEvent.Error.class,           name = "error"),
})
public sealed interface RoomCcRealtimeEvent permits
    RoomCcRealtimeEvent.SubtitleStart,
    RoomCcRealtimeEvent.SubtitleEnd,
    RoomCcRealtimeEvent.SubtitleDisabled,
    RoomCcRealtimeEvent.SubtitleLine,
    RoomCcRealtimeEvent.SubtitleExperienceActivated,
    RoomCcRealtimeEvent.SubtitleExpired,
    RoomCcRealtimeEvent.Raw,
    RoomCcRealtimeEvent.Error {

    /**
     * Notify-type 1 — the upstream is starting to stream subtitles for the given cname. Decoded
     * from the {@code LiveCCNotify} fields: {@code cname} (where the captions are for),
     * {@code nick_name} (who's being captioned — usually the speaker), {@code head_url},
     * {@code nationality}, {@code role_type}, {@code _id}.
     */
    record SubtitleStart(
        String cname,
        String speakerId,
        String speakerNickname,
        String speakerHeadUrl,
        String nationality,
        int roleType,
        String id
    ) implements RoomCcRealtimeEvent {}

    /**
     * Notify-type 2 — the upstream is ending the subtitle stream for the cname carried on the
     * payload. {@code cname} is the room the stream belonged to.
     */
    record SubtitleEnd(String cname) implements RoomCcRealtimeEvent {}

    /**
     * Notify-type 3 — subtitles disabled (mod / host action). {@code cname} identifies the room.
     * Note: type 3 is ALSO in the "kill" set on the Android side, so the subtitle controller
     * unconditionally tears down state on receipt, regardless of any branch match.
     */
    record SubtitleDisabled(String cname) implements RoomCcRealtimeEvent {}

    /**
     * Notify-type 4 — a single line of caption text was pushed. The Android mapper
     * ({@code Luf0/i.a(LiveCCNotify;)}) converts the {@code LiveCCNotify} payload into a
     * subtitle UI model; we surface the raw fields here and let the frontend handle rendering.
     */
    record SubtitleLine(
        String cname,
        String id,
        String text,
        String userId,
        String nickName,
        String headUrl,
        String nationality,
        int roleType,
        long createAt,
        long updateAt,
        long resultId,
        Boolean enabled,
        Long expiredAt
    ) implements RoomCcRealtimeEvent {}

    /**
     * Notify-type 6 — the subtitle experience card activated for the named user. The Android
     * client gates this on the user-id matching the local user; a non-self user_id on this
     * payload is dropped by the client (we forward it unconditionally and let the frontend
     * gate if needed).
     */
    record SubtitleExperienceActivated(
        String cname,
        String userId
    ) implements RoomCcRealtimeEvent {}

    /**
     * Notify-type 12 (0xc) — subtitle entitlement expired. The Android controller checks
     * {@code expired_at} against the current epoch (in seconds) and tears down the subtitle
     * state when it has expired. We forward {@code expired_at} (ms epoch) and the cname; the
     * frontend decides whether to surface a renewal prompt.
     */
    record SubtitleExpired(
        String cname,
        long expiredAt
    ) implements RoomCcRealtimeEvent {}

    /** Catch-all for CC-channel notify_types not yet modeled (e.g. 5, 7, 9, 10, 11). */
    record Raw(String originalType, Object payload) implements RoomCcRealtimeEvent {}

    /** Mapper / pipeline error. */
    record Error(String message) implements RoomCcRealtimeEvent {}
}
