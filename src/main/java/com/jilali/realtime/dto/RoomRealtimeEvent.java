package com.jilali.realtime.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

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
    RoomRealtimeEvent.Raw,
    RoomRealtimeEvent.Error {

    record ConnectionState(String state) implements RoomRealtimeEvent {}

    record UserJoin(@JsonProperty("user_id") String userId, String nickname) implements RoomRealtimeEvent {}

    record UserQuit(@JsonProperty("user_id") String userId) implements RoomRealtimeEvent {}

    record StageUserEvent(
        @JsonProperty("user_id") String userId,
        String nickname,
        @JsonProperty("head_url") String headUrl) {}

    record StageJoin(StageUserEvent stageUser) implements RoomRealtimeEvent {}

    record StageQuit(@JsonProperty("user_id") String userId) implements RoomRealtimeEvent {}

    record StageRaiseHand(
        @JsonProperty("user_id") String userId,
        @JsonProperty("raisehand_type") int raisehandType) implements RoomRealtimeEvent {}

    record StageInvite(@JsonProperty("user_id") String userId) implements RoomRealtimeEvent {}

    record ReplyInfoEvent(
        @JsonProperty("msg_id") String msgId,
        @JsonProperty("from_id") long fromId,
        @JsonProperty("from_nickname") String fromNickname,
        String text,
        @JsonProperty("msg_type") String msgType) {}

    record CommentEvent(
        String id,
        @JsonProperty("user_id") String userId,
        String nickname,
        @JsonProperty("head_url") String headUrl,
        String text,
        long ts,
        @JsonProperty("reply_info") ReplyInfoEvent replyInfo) {}

    record Comment(CommentEvent comment) implements RoomRealtimeEvent {}

    record GiftEvent(
        @JsonProperty("send_uid") String sendUid,
        @JsonProperty("send_nickname") String sendNickname,
        @JsonProperty("receiver_uid") String receiverUid,
        @JsonProperty("receiver_nickname") String receiverNickname,
        @JsonProperty("small_pic") String smallPic) {}

    record Gift(List<GiftEvent> gifts) implements RoomRealtimeEvent {}

    record StageDeviceControl(
        @JsonProperty("user_id") String userId,
        @JsonProperty("device_type") int deviceType,
        @JsonProperty("switch_type") int switchType) implements RoomRealtimeEvent {}

    record ModInvite(@JsonProperty("user_id") String userId) implements RoomRealtimeEvent {}

    record Raw(@JsonProperty("original_type") String originalType, Object payload) implements RoomRealtimeEvent {}

    record Error(String message) implements RoomRealtimeEvent {}
}
