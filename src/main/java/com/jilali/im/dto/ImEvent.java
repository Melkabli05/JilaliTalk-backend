package com.jilali.im.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Events from the per-user {@code ht_im/sock} connection — account-level notifications
 * (profile visits, DMs, room shares, session/ban status), as opposed to
 * {@code com.jilali.realtime.dto.RoomRealtimeEvent}'s per-room LiveHub events. Every record
 * is hand-constructed (never deserialized), so wire fields are camelCase already — see
 * {@code com.jilali.im.HtImMessageMapper}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ImEvent.ConnectionState.class,  name = "connection-state"),
    @JsonSubTypes.Type(value = ImEvent.ProfileVisit.class,     name = "profile_visit"),
    @JsonSubTypes.Type(value = ImEvent.TextMessage.class,      name = "text_message"),
    @JsonSubTypes.Type(value = ImEvent.ImageMessage.class,     name = "image_message"),
    @JsonSubTypes.Type(value = ImEvent.VoiceRoomShared.class,  name = "voice_room_shared"),
    @JsonSubTypes.Type(value = ImEvent.LiveRoomShared.class,   name = "live_room_shared"),
    @JsonSubTypes.Type(value = ImEvent.AccountStatus.class,    name = "account_status"),
    @JsonSubTypes.Type(value = ImEvent.Error.class,            name = "error"),
})
public sealed interface ImEvent permits
    ImEvent.ConnectionState,
    ImEvent.ProfileVisit,
    ImEvent.TextMessage,
    ImEvent.ImageMessage,
    ImEvent.VoiceRoomShared,
    ImEvent.LiveRoomShared,
    ImEvent.AccountStatus,
    ImEvent.Error {

    record ConnectionState(String state) implements ImEvent {}

    /** Someone visited your profile (the reference client's {@code new_voice_visitor}). */
    record ProfileVisit(String visitorUserId) implements ImEvent {}

    record TextMessage(String fromUserId, String text, long ts) implements ImEvent {}

    record ImageMessage(String fromUserId, String imageUrl, long ts) implements ImEvent {}

    /** A voice room shared with you via DM. {@code count} > 1 when several arrived in a
     *  short window — see the reference client's 800ms batching window in scriptv2.js. */
    record VoiceRoomShared(String fromNickname, String cname, String headUrl, int count) implements ImEvent {}

    record LiveRoomShared(String fromNickname, String cname, String headUrl) implements ImEvent {}

    /** {@code status}: "banned" or "session_mismatch" (logged in on another device) —
     *  mirrors the reference client's status 2 / 105 handling. */
    record AccountStatus(String status) implements ImEvent {}

    record Error(String message) implements ImEvent {}
}
