package com.jilali.realtime.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

// Wire keys are camelCase: every record below is constructed by hand in HtNotifyMapper —
// never deserialized from JSON — so there is no upstream snake_case shape to mirror here.
// (Contrast with com.jilali.room.dto, where the same record both reads upstream's
// snake_case JSON and is returned to our frontend, so @JsonProperty(snake_case) is load-bearing.)

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = RoomRealtimeEvent.ConnectionState.class,    name = "connection-state"),
    @JsonSubTypes.Type(value = RoomRealtimeEvent.UserJoin.class,           name = "user_join"),
    @JsonSubTypes.Type(value = RoomRealtimeEvent.UserQuit.class,           name = "user_quit"),
    @JsonSubTypes.Type(value = RoomRealtimeEvent.StageJoin.class,          name = "stage_join"),
    @JsonSubTypes.Type(value = RoomRealtimeEvent.StageQuit.class,          name = "stage_quit"),
    @JsonSubTypes.Type(value = RoomRealtimeEvent.StageRaiseHand.class,     name = "stage_raisehand"),
    @JsonSubTypes.Type(value = RoomRealtimeEvent.StageInvite.class,        name = "stage_invite"),
    @JsonSubTypes.Type(value = RoomRealtimeEvent.Comment.class,            name = "comment"),
    @JsonSubTypes.Type(value = RoomRealtimeEvent.Gift.class,               name = "gift"),
    @JsonSubTypes.Type(value = RoomRealtimeEvent.StageDeviceControl.class, name = "stage_device_control"),
    @JsonSubTypes.Type(value = RoomRealtimeEvent.ModInvite.class,          name = "mod_invite"),
    @JsonSubTypes.Type(value = RoomRealtimeEvent.WhiteboardActivated.class, name = "whiteboard_activated"),
    @JsonSubTypes.Type(value = RoomRealtimeEvent.WhiteboardDeactivated.class, name = "whiteboard_deactivated"),
    @JsonSubTypes.Type(value = RoomRealtimeEvent.MicOpened.class,          name = "mic_opened"),
    @JsonSubTypes.Type(value = RoomRealtimeEvent.MicClosed.class,          name = "mic_closed"),
    @JsonSubTypes.Type(value = RoomRealtimeEvent.ModUnmuted.class,         name = "mod_unmuted"),
    @JsonSubTypes.Type(value = RoomRealtimeEvent.RoomKick.class,           name = "room_kick"),
    @JsonSubTypes.Type(value = RoomRealtimeEvent.StageKick.class,           name = "stage_kick"),
    @JsonSubTypes.Type(value = RoomRealtimeEvent.ModAccepted.class,         name = "mod_accepted"),
    @JsonSubTypes.Type(value = RoomRealtimeEvent.ModRemoved.class,          name = "mod_removed"),
    @JsonSubTypes.Type(value = RoomRealtimeEvent.Follow.class,             name = "follow"),
    @JsonSubTypes.Type(value = RoomRealtimeEvent.LuckyBag.class,           name = "lucky_bag"),
    @JsonSubTypes.Type(value = RoomRealtimeEvent.Raw.class,                name = "raw"),
    @JsonSubTypes.Type(value = RoomRealtimeEvent.Error.class,              name = "error"),
})
public sealed interface RoomRealtimeEvent permits
    RoomRealtimeEvent.ConnectionState,
    RoomRealtimeEvent.UserJoin,
    RoomRealtimeEvent.UserQuit,
    RoomRealtimeEvent.StageJoin,
    RoomRealtimeEvent.StageQuit,
    RoomRealtimeEvent.StageRaiseHand,
    RoomRealtimeEvent.StageInvite,
    RoomRealtimeEvent.Comment,
    RoomRealtimeEvent.Gift,
    RoomRealtimeEvent.StageDeviceControl,
    RoomRealtimeEvent.ModInvite,
    RoomRealtimeEvent.WhiteboardActivated,
    RoomRealtimeEvent.WhiteboardDeactivated,
    RoomRealtimeEvent.MicOpened,
    RoomRealtimeEvent.MicClosed,
    RoomRealtimeEvent.ModUnmuted,
    RoomRealtimeEvent.RoomKick,
    RoomRealtimeEvent.StageKick,
    RoomRealtimeEvent.ModAccepted,
    RoomRealtimeEvent.ModRemoved,
    RoomRealtimeEvent.Follow,
    RoomRealtimeEvent.LuckyBag,
    RoomRealtimeEvent.Raw,
    RoomRealtimeEvent.Error {

    record ConnectionState(String state) implements RoomRealtimeEvent {}

    record UserJoin(String userId, String nickname, String headUrl, String nationality, boolean isBannedComment) implements RoomRealtimeEvent {}

    record UserQuit(String userId) implements RoomRealtimeEvent {}

    record StageUserEvent(String userId, String nickname, String headUrl) {}

    record StageJoin(StageUserEvent stageUser) implements RoomRealtimeEvent {}

    record StageQuit(String userId) implements RoomRealtimeEvent {}

    record StageRaiseHand(String userId, int raisehandType) implements RoomRealtimeEvent {}

    record StageInvite(String userId) implements RoomRealtimeEvent {}

    record ReplyInfoEvent(
        String msgId,
        long fromId,
        String fromNickname,
        String text,
        String msgType) {}

    /**
     * Carries every decoration field the live comment frame actually supplies (role, VIP, bubble
     * skin + companion animal, family-group badge, nationality) — earlier this record only had
     * id/userId/nickname/headUrl/text/ts/replyInfo, so a paying user's equipped bubble skin and
     * FG badge silently vanished from their own LIVE messages, only reappearing after a refresh
     * pulled the same comment back through the REST history endpoint, which always mapped these
     * fields correctly.
     */
    record CommentEvent(
        String id,
        String userId,
        String nickname,
        String headUrl,
        String text,
        long ts,
        ReplyInfoEvent replyInfo,
        String nationality,
        int role,
        int vipType,
        int dayRankLevel,
        int giftLevel,
        int fgLevel,
        String fgName,
        boolean fgIsActive,
        int bubbleId,
        String bubbleUrl,
        String bubbleColor,
        int hitBad,
        int bubbleAnimalType,
        String bubbleAnimalUrl) {}

    record Comment(CommentEvent comment) implements RoomRealtimeEvent {}

    record GiftEvent(
        String sendUid,
        String sendNickname,
        String sendHeadUrl,
        String sendNation,
        String receiverUid,
        String receiverNickname,
        String receiverHeadUrl,
        String receiverNation,
        String smallPic,
        long giftId,
        int giftNumber,
        long giftVal,
        int vipType,
        int giftLevel,
        int dayRankLevel) {}

    record Gift(List<GiftEvent> gifts) implements RoomRealtimeEvent {}

    record StageDeviceControl(String userId, int deviceType, int switchType) implements RoomRealtimeEvent {}

    record ModInvite(String userId) implements RoomRealtimeEvent {}

    // Whiteboard
    record WhiteboardActivated(String cname) implements RoomRealtimeEvent {}
    record WhiteboardDeactivated(String cname) implements RoomRealtimeEvent {}

    // Mic on/off (user-initiated, not mod-initiated)
    record MicOpened(String userId) implements RoomRealtimeEvent {}
    record MicClosed(String userId) implements RoomRealtimeEvent {}

    /**
     * Type 40 — a mod lifted a mute (the counterpart to {@code StageDeviceControl} on type 30).
     * Deliberately not folded into {@link MicOpened}: the reference client (scriptv2.js:5262)
     * explicitly does NOT flip the mic-on roster state here (that line is commented out) — being
     * allowed to speak again is not the same as actively speaking, so this only ever drives a
     * "you can speak now" toast for the affected user, never a roster update.
     */
    record ModUnmuted(String userId) implements RoomRealtimeEvent {}

    // Kicked from room
    record RoomKick(
        String userId,
        String nickname,
        String managerName,
        String cname
    ) implements RoomRealtimeEvent {}

    // Kicked from stage (by mod)
    record StageKick(
        String userId,
        String managerName,
        String cname
    ) implements RoomRealtimeEvent {}

    // Role changes
    record ModAccepted(String userId) implements RoomRealtimeEvent {}
    record ModRemoved(String userId) implements RoomRealtimeEvent {}

    // Follow
    record Follow(String userId, String nickname, String headUrl, int status) implements RoomRealtimeEvent {} // 1 = followed you, 2 = followed back

    /**
     * A lucky bag giveaway started in the room. Field names mirror {@code VoiceRoomInfoObjects.LuckBag}
     * (the REST {@code voice_room_info.luck_bag} shape) — same lucky-bag subsystem, confirmed real
     * field names from captured traffic, reused here since the push carries the same {@code lucky_bag_id}
     * marker this mapper already keys off of.
     */
    record LuckyBag(String luckyBagId, int luckyBagNumber, String cname) implements RoomRealtimeEvent {}

    record Raw(String originalType, Object payload) implements RoomRealtimeEvent {}

    record Error(String message) implements RoomRealtimeEvent {}
}
