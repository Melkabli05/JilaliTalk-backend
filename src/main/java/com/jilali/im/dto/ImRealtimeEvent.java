package com.jilali.im.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ImRealtimeEvent.ConnectionState.class,      name = "connection-state"),
    @JsonSubTypes.Type(value = ImRealtimeEvent.TextMessage.class,          name = "text_message"),
    @JsonSubTypes.Type(value = ImRealtimeEvent.ImageMessage.class,         name = "image_message"),
    @JsonSubTypes.Type(value = ImRealtimeEvent.GiftMessage.class,          name = "gift_message"),
    @JsonSubTypes.Type(value = ImRealtimeEvent.IntroductionMessage.class,  name = "introduction_message"),
    @JsonSubTypes.Type(value = ImRealtimeEvent.VoiceRoomShared.class,      name = "voice_room_shared"),
    @JsonSubTypes.Type(value = ImRealtimeEvent.LiveRoomShared.class,       name = "live_room_shared"),
    @JsonSubTypes.Type(value = ImRealtimeEvent.ProfileVisit.class,         name = "profile_visit"),
    @JsonSubTypes.Type(value = ImRealtimeEvent.StageInvite.class,          name = "stage_invite"),
    @JsonSubTypes.Type(value = ImRealtimeEvent.ModInvite.class,            name = "mod_invite"),
    @JsonSubTypes.Type(value = ImRealtimeEvent.ModAccepted.class,          name = "mod_accepted"),
    @JsonSubTypes.Type(value = ImRealtimeEvent.ModRemoved.class,           name = "mod_removed"),
    @JsonSubTypes.Type(value = ImRealtimeEvent.ModUnmuted.class,           name = "mod_unmuted"),
    @JsonSubTypes.Type(value = ImRealtimeEvent.Follow.class,               name = "follow"),
    @JsonSubTypes.Type(value = ImRealtimeEvent.GroupMessage.class,         name = "group_message"),
    @JsonSubTypes.Type(value = ImRealtimeEvent.TypingIndicator.class,      name = "typing_indicator"),
    @JsonSubTypes.Type(value = ImRealtimeEvent.ReadReceipt.class,          name = "read_receipt"),
    @JsonSubTypes.Type(value = ImRealtimeEvent.AccountStatus.class,        name = "account_status"),
    @JsonSubTypes.Type(value = ImRealtimeEvent.Error.class,                name = "error"),
})
public sealed interface ImRealtimeEvent permits
    ImRealtimeEvent.ConnectionState,
    ImRealtimeEvent.TextMessage,
    ImRealtimeEvent.ImageMessage,
    ImRealtimeEvent.GiftMessage,
    ImRealtimeEvent.IntroductionMessage,
    ImRealtimeEvent.VoiceRoomShared,
    ImRealtimeEvent.LiveRoomShared,
    ImRealtimeEvent.ProfileVisit,
    ImRealtimeEvent.StageInvite,
    ImRealtimeEvent.ModInvite,
    ImRealtimeEvent.ModAccepted,
    ImRealtimeEvent.ModRemoved,
    ImRealtimeEvent.ModUnmuted,
    ImRealtimeEvent.Follow,
    ImRealtimeEvent.GroupMessage,
    ImRealtimeEvent.TypingIndicator,
    ImRealtimeEvent.ReadReceipt,
    ImRealtimeEvent.AccountStatus,
    ImRealtimeEvent.Error {

    record ConnectionState(String state) implements ImRealtimeEvent {}

    record TextMessage(String fromUserId, String text, long ts) implements ImRealtimeEvent {}

    record ImageMessage(String fromUserId, String imageUrl, long ts) implements ImRealtimeEvent {}

    record GiftMessage(String fromUserId, String fromNickname, long giftId, int count) implements ImRealtimeEvent {}

    record IntroductionMessage(String fromUserId, String fromNickname) implements ImRealtimeEvent {}

    record VoiceRoomShared(String fromNickname, String cname, String headUrl, Integer count) implements ImRealtimeEvent {}

    record LiveRoomShared(String fromNickname, String cname, String headUrl) implements ImRealtimeEvent {}

    record ProfileVisit(String visitorUserId) implements ImRealtimeEvent {}

    // Personal notify_type pushes (notify_type 18/48/34/35/40/53) — same wire types the
    // reference client (scriptv2.js startwebsock()) received on this same ht_im/sock channel,
    // filtered client-side there by user_id === myuid. Here the channel is already scoped to
    // one account, so anything mapped below is already "for us" — no further filtering needed.
    record StageInvite(String userId, String cname) implements ImRealtimeEvent {}

    record ModInvite(String userId, String cname) implements ImRealtimeEvent {}

    record ModAccepted(String userId) implements ImRealtimeEvent {}

    record ModRemoved(String userId) implements ImRealtimeEvent {}

    record ModUnmuted(String userId) implements ImRealtimeEvent {}

    record Follow(String nickname, int status) implements ImRealtimeEvent {} // 1 = followed you, 2 = followed back

    record GroupMessage(String senderId, String senderName, String roomName, String text) implements ImRealtimeEvent {}

    record TypingIndicator(String fromUserId, boolean isTyping) implements ImRealtimeEvent {}

    record ReadReceipt(String msgId) implements ImRealtimeEvent {}

    record AccountStatus(String status) implements ImRealtimeEvent {}

    record Error(String message) implements ImRealtimeEvent {}
}
