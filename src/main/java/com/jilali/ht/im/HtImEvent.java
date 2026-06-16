package com.jilali.ht.im;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Typed event union produced by the HT IM bridge.
 *
 * <p>Maps the inbound packet types from {@code connectwebsock.js}:
 * <ul>
 *   <li>0xF1 login response → {@link SessionReady} + optionally {@link OfflineMessages} / {@link GroupMessages}</li>
 *   <li>0xF2 push (msg_type text/image/gift/voice_room/live_link) → {@link DirectMessage}</li>
 *   <li>0xF2 + cmdId 16386 (MSG ACK) → {@link MsgAck}</li>
 *   <li>0xF2 first-byte 0x25 (read receipt) → {@link ReadReceipt}</li>
 *   <li>0xF5 + cmdId 16407 (typing) → {@link Typing}</li>
 *   <li>0xF1 + cmdId 0x9002 (pong) → {@link Pong}</li>
 * </ul>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = HtImEvent.SessionReady.class,    name = "session-ready"),
    @JsonSubTypes.Type(value = HtImEvent.DirectMessage.class,   name = "direct-message"),
    @JsonSubTypes.Type(value = HtImEvent.MsgAck.class,          name = "msg-ack"),
    @JsonSubTypes.Type(value = HtImEvent.ReadReceipt.class,     name = "read-receipt"),
    @JsonSubTypes.Type(value = HtImEvent.Typing.class,          name = "typing"),
    @JsonSubTypes.Type(value = HtImEvent.OfflineMessages.class, name = "offline-messages"),
    @JsonSubTypes.Type(value = HtImEvent.GroupMessages.class,   name = "group-messages"),
    @JsonSubTypes.Type(value = HtImEvent.Pong.class,            name = "pong"),
    @JsonSubTypes.Type(value = HtImEvent.Error.class,           name = "error"),
    @JsonSubTypes.Type(value = HtImEvent.Raw.class,             name = "raw"),
})
public sealed interface HtImEvent permits
    HtImEvent.SessionReady,
    HtImEvent.DirectMessage,
    HtImEvent.MsgAck,
    HtImEvent.ReadReceipt,
    HtImEvent.Typing,
    HtImEvent.OfflineMessages,
    HtImEvent.GroupMessages,
    HtImEvent.Pong,
    HtImEvent.Error,
    HtImEvent.Raw {

    /**
     * Emitted after a successful login and session key exchange.
     *
     * <p>{@code sessionKey} is the raw string from {@code data.session_key}; the bridge
     * converts it to UTF-8 bytes for use as the QQTEA key. It is excluded from the JSON
     * sent to the frontend (it is a server-side secret).
     */
    record SessionReady(
        String sessionId,
        String userId,
        String refreshedJwt,
        String areaCode,
        @JsonIgnore String sessionKey
    ) implements HtImEvent {}

    /**
     * A decoded direct message (text, image, gift, voice_room share, live_link, etc.).
     * {@code msgType} mirrors the HT {@code msg_type} or {@code notify_type} field.
     * {@code payload} is the full decoded JSON object.
     */
    record DirectMessage(
        String msgType,
        String fromId,
        String toId,
        String msgId,
        Object payload
    ) implements HtImEvent {}

    /** ACK for a message we sent (cmdId 16386 inbound). */
    record MsgAck(
        String msgId,
        Object decoded
    ) implements HtImEvent {}

    /** Server confirmed the recipient read our message (first byte 0x25). */
    record ReadReceipt(
        String msgId,
        long fromId
    ) implements HtImEvent {}

    /** Typing indicator from another user (0xF5, cmdId 16407). */
    record Typing(
        long fromId,
        boolean isTyping
    ) implements HtImEvent {}

    /**
     * Offline message batch (cmdId 16454). Contains the raw decoded JSON with
     * {@code data.packet_list} already parsed via {@link QqTea} and zlib.
     *
     * <p>{@code lastId} is pre-extracted from {@code data.data.last_id}; when non-zero,
     * the bridge sends a continuation request (cmdId 16453) to fetch the next page.
     */
    record OfflineMessages(
        Object data,
        long lastId
    ) implements HtImEvent {}

    /** Group message sync response (cmdId 29968). */
    record GroupMessages(
        Object data
    ) implements HtImEvent {}

    /** Heartbeat PONG from server (0xF1, cmdId 0x9002). */
    record Pong() implements HtImEvent {}

    /** Bridge-level error or decode failure. */
    record Error(
        String message
    ) implements HtImEvent {}

    /** Passthrough for unknown packet types. */
    record Raw(
        int flag,
        int cmdId,
        Object data
    ) implements HtImEvent {}
}
