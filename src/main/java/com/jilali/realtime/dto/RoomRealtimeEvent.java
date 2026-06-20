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
    @JsonSubTypes.Type(value = RoomRealtimeEvent.RoomKick.class,           name = "room_kick"),
    @JsonSubTypes.Type(value = RoomRealtimeEvent.StageKick.class,           name = "stage_kick"),
    @JsonSubTypes.Type(value = RoomRealtimeEvent.ModAccepted.class,         name = "mod_accepted"),
    @JsonSubTypes.Type(value = RoomRealtimeEvent.ModRemoved.class,          name = "mod_removed"),
    @JsonSubTypes.Type(value = RoomRealtimeEvent.Follow.class,             name = "follow"),
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
    RoomRealtimeEvent.RoomKick,
    RoomRealtimeEvent.StageKick,
    RoomRealtimeEvent.ModAccepted,
    RoomRealtimeEvent.ModRemoved,
    RoomRealtimeEvent.Follow,
    RoomRealtimeEvent.Raw,
    RoomRealtimeEvent.Error {

    record ConnectionState(String state) implements RoomRealtimeEvent {}

    record UserJoin(String userId, String nickname) implements RoomRealtimeEvent {}

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

    record CommentEvent(
        String id,
        String userId,
        String nickname,
        String headUrl,
        String text,
        long ts,
        ReplyInfoEvent replyInfo) {}

    record Comment(CommentEvent comment) implements RoomRealtimeEvent {}

    record GiftEvent(
        String sendUid,
        String sendNickname,
        String receiverUid,
        String receiverNickname,
        String smallPic) {}

    record Gift(List<GiftEvent> gifts) implements RoomRealtimeEvent {}

    record StageDeviceControl(String userId, int deviceType, int switchType) implements RoomRealtimeEvent {}

    record ModInvite(String userId) implements RoomRealtimeEvent {}

    // Whiteboard
    record WhiteboardActivated(String cname) implements RoomRealtimeEvent {}
    record WhiteboardDeactivated(String cname) implements RoomRealtimeEvent {}

    // Mic on/off (user-initiated, not mod-initiated)
    record MicOpened(String userId) implements RoomRealtimeEvent {}
    record MicClosed(String userId) implements RoomRealtimeEvent {}

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
    record Follow(String nickname, int status) implements RoomRealtimeEvent {} // 1 = followed you, 2 = followed back

    record Raw(String originalType, Object payload) implements RoomRealtimeEvent {}

    record Error(String message) implements RoomRealtimeEvent {}
}
