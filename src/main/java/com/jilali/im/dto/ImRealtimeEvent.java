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
    @JsonSubTypes.Type(value = ImRealtimeEvent.TypingIndicator.class,      name = "typing_indicator"),
    @JsonSubTypes.Type(value = ImRealtimeEvent.ReadReceipt.class,          name = "read_receipt"),
    @JsonSubTypes.Type(value = ImRealtimeEvent.MessageAck.class,            name = "message_ack"),
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
    ImRealtimeEvent.TypingIndicator,
    ImRealtimeEvent.ReadReceipt,
    ImRealtimeEvent.MessageAck,
    ImRealtimeEvent.AccountStatus,
    ImRealtimeEvent.Error {

    record ConnectionState(String state) implements ImRealtimeEvent {}

    /**
     * {@code msgId} is the upstream-issued message identifier (the same UUID we generated and
     * sent on the outbound {@code SingleMessagePacketRequest} envelope's {@code msg.msg_id}).
     * The frontend stores the inbound message under this id locally so that the eventual
     * {@code message_ack} for the same logical message can be correlated by msgId and flip the
     * local bubble's send-state to {@code delivered}. Without {@code msgId} on the inbound
     * event, the chat store's dedupe-by-id logic produces two distinct local rows (one for the
     * optimistic send, one for the echoed upstream delivery) and the {@code message_ack} never
     * finds its match. The Jackson serializer emits {@code msgId: null} when absent, which the
     * frontend reads as a missing field and falls back to a fresh local id.
     */
    record TextMessage(String fromUserId, String fromNickname, String fromHeadUrl, String text, long ts, String msgId) implements ImRealtimeEvent {}

    record ImageMessage(String fromUserId, String fromNickname, String fromHeadUrl, String imageUrl, long ts, String msgId) implements ImRealtimeEvent {}

    record GiftMessage(String fromUserId, String fromNickname, String fromHeadUrl, long giftId, int count, String msgId) implements ImRealtimeEvent {}

    record IntroductionMessage(
        String fromUserId, String fromNickname, String fromHeadUrl,
        String targetUserId, String targetNickname, String targetHeadUrl,
        String targetSex, Integer targetAge, String targetNationality, String targetBio,
        String msgId
    ) implements ImRealtimeEvent {}

    /**
     * roomName/topicName mirror the real Android client's share-bubble fields
     * (IMVoiceRoomBean: {@code name}/{@code topic_name}, rendered by ChatVoiceRoomDelegate
     * via {@code LiveCommunicationItemView.setRoomName} — smali_classes8/.../voiceRoom/
     * IMVoiceRoomBean.smali). Previously dropped entirely, so the share card only ever
     * showed a generic "X shared a voice room" — no room title or topic, even though the
     * upstream payload always carries them.
     */
    record VoiceRoomShared(
        String fromUserId, String fromNickname, String cname, String headUrl, Integer count, String msgId,
        String roomName, String topicName
    ) implements ImRealtimeEvent {}

    /** activityName/topicName mirror IMLiveLinkBean's {@code activity_name}/{@code topic_name}
     *  (same delegate pattern as {@link VoiceRoomShared} — see smali_classes23/.../liveLink/
     *  IMLiveLinkBean.smali). */
    record LiveRoomShared(
        String fromUserId, String fromNickname, String cname, String headUrl, String msgId,
        String activityName, String topicName
    ) implements ImRealtimeEvent {}

    record ProfileVisit(String visitorUserId, String nickname, String headUrl) implements ImRealtimeEvent {}

    // Personal notify_type pushes (notify_type 18/48/34/35/40/53) — same wire types the
    // reference client (scriptv2.js startwebsock()) received on this same ht_im/sock channel,
    // filtered client-side there by user_id === myuid. Here the channel is already scoped to
    // one account, so anything mapped below is already "for us" — no further filtering needed.
    record StageInvite(String userId, String cname) implements ImRealtimeEvent {}

    record ModInvite(String userId, String cname) implements ImRealtimeEvent {}

    record ModAccepted(String userId) implements ImRealtimeEvent {}

    record ModRemoved(String userId) implements ImRealtimeEvent {}

    record ModUnmuted(String userId) implements ImRealtimeEvent {}

    record Follow(String userId, String nickname, String headUrl, int status) implements ImRealtimeEvent {} // 1 = followed you, 2 = followed back

    record TypingIndicator(String fromUserId, boolean isTyping) implements ImRealtimeEvent {}

    record ReadReceipt(String msgId) implements ImRealtimeEvent {}

    /**
     * Server echo for a DM we just sent (cmdId 16386 on the upstream socket). Payload
     * shape: short binary body {@code [u16 strLen][strVal UTF-8][u64 LE sequence][prefix byte]}
     * per the legacy {@code connectwebsock.js} {@code decodeCmd16386}, decoded by
     * {@link com.jilali.im.HtImFrameDecoder#decodeMsgAck}. The frontend uses {@code msgId}
     * to flip the matching local bubble's send-state to "delivered", and {@code prefix}
     * to distinguish a successful ACK (any non-zero prefix byte typically means the
     * server accepted) from a failure ACK (≤16-byte empty body).
     */
    record MessageAck(String msgId, long sequence, int prefix) implements ImRealtimeEvent {}

    record AccountStatus(String status) implements ImRealtimeEvent {}

    record Error(String message) implements ImRealtimeEvent {}
}
