package com.jilali.ht.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Normalized event union produced by the HT bridge.
 * Every event that flows from HelloTalk's binary protocol to the frontend arrives
 * here as a typed, validated POJO — the frontend never parses HT's proprietary format.
 *
 * <p>Subtypes are matched by {@code type} field. Add a new variant here whenever a new
 * HT message kind is decoded.
 *
 * <p>All fields are {@code final} — events are immutable once constructed.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = HtEvent.Comment.class,         name = "new-comment"),
    @JsonSubTypes.Type(value = HtEvent.UserJoin.class,        name = "user-join"),
    @JsonSubTypes.Type(value = HtEvent.UserLeave.class,       name = "user-leave"),
    @JsonSubTypes.Type(value = HtEvent.StageUpdate.class,     name = "stage-update"),
    @JsonSubTypes.Type(value = HtEvent.CommentReaction.class, name = "comment-reaction"),
    @JsonSubTypes.Type(value = HtEvent.HandRaise.class,       name = "hand-raise"),
    @JsonSubTypes.Type(value = HtEvent.HandLower.class,       name = "hand-lower"),
    @JsonSubTypes.Type(value = HtEvent.StageInvite.class,     name = "stage-invite"),
    @JsonSubTypes.Type(value = HtEvent.ModInvite.class,       name = "mod-invite"),
    @JsonSubTypes.Type(value = HtEvent.MicMuted.class,        name = "mic-muted"),
    @JsonSubTypes.Type(value = HtEvent.Connected.class,       name = "connected"),
    @JsonSubTypes.Type(value = HtEvent.Error.class,           name = "error"),
    @JsonSubTypes.Type(value = HtEvent.Raw.class,             name = "raw"),
})
public sealed interface HtEvent permits
    HtEvent.Comment,
    HtEvent.UserJoin,
    HtEvent.UserLeave,
    HtEvent.StageUpdate,
    HtEvent.CommentReaction,
    HtEvent.HandRaise,
    HtEvent.HandLower,
    HtEvent.StageInvite,
    HtEvent.ModInvite,
    HtEvent.MicMuted,
    HtEvent.Connected,
    HtEvent.Error,
    HtEvent.Raw {

    record Comment(
        String id,
        String uid,
        String nickname,
        String avatar,
        String text,
        long ts
    ) implements HtEvent {}

    record UserJoin(
        String uid,
        String nickname
    ) implements HtEvent {}

    record UserLeave(
        String uid
    ) implements HtEvent {}

    /**
     * {@code users} is the raw decoded payload from the upstream stage-update frame.
     * The shape depends on what HelloTalk sends — currently passed through as Object
     * so the frontend can render from it without a schema change every time upstream
     * adds a field.
     */
    record StageUpdate(
        Object users
    ) implements HtEvent {}

    record CommentReaction(
        String id,
        Object reaction,
        int delta
    ) implements HtEvent {}

    /** notify_type 10 — user raised hand to join the stage. */
    record HandRaise(String uid) implements HtEvent {}

    /** notify_type 11 — user lowered hand (cancelled raise). */
    record HandLower(String uid) implements HtEvent {}

    /** notify_type 18 — host invited this user to join the stage. */
    record StageInvite(String uid) implements HtEvent {}

    /** notify_type 48 — host invited this user to become a moderator. */
    record ModInvite(String uid) implements HtEvent {}

    /** notify_type 30 — a moderator muted a user on stage. */
    record MicMuted(String uid) implements HtEvent {}

    /** Emitted when the HT binary connection has been established and authenticated. */
    record Connected(
        String cname
    ) implements HtEvent {}

    /** Bridge-level error (connection dropped, decode failure, upstream timeout). */
    record Error(
        String message
    ) implements HtEvent {}

    /**
     * Fallback for any HT event kind this BFF doesn't (yet) understand.
     * Carries the raw decoded JSON so the frontend can handle it without a BFF deploy.
     */
    record Raw(
        String originalType,
        Object payload
    ) implements HtEvent {}
}