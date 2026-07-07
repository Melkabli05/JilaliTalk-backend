package com.jilali.im;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.core.JilaliProperties;
import com.jilali.core.UidExtractor;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.validation.constraints.NotBlank;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP send-side of the IM channel: thin glue over {@link ImEventSource#sendOutbound} that lets
 * the Angular frontend fire the binary-WS primitives the legacy HelloTalk client used to build
 * directly in JS ({@code sendReadReceipt}, {@code sendTypingIndicator}, {@code sendTextMessage}
 * in {@code prvgmsgpacket.js}).
 *
 * <p>Why each call goes {@code BFF -> upstream WS} rather than {@code Frontend -> upstream WS}:
 * the browser no longer speaks the upstream binary protocol at all — it only sees our own JSON
 * {@code /ws/im} relay. The BFF's {@code ImEventSource} already holds an authenticated upstream
 * socket per user, so it just hands bytes to that socket when the HTTP caller asks.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /api/im/messages/{userId}/read}    — packetPRIVATEMSG-flavored read receipt
 *   <li>{@code POST /api/im/messages/{userId}/typing}  — typing indicator (on/off)
 *   <li>{@code POST /api/im/messages/{userId}/send}    — 1:1 DM (text/image/gift/introduction/voice_room/live_link)
 * </ul>
 * The {@code {userId}} in all three is the *peer* (recipient) uid; the caller (sender) uid is
 * the JWT subject — see {@link JilaliProperties#defaultAuthToken()} + {@link UidExtractor#uidAsLong}.
 */
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/api/im/messages")
public class ImSendController {

    private final long callerUserId;
    private final ObjectMapper om;
    private final ImEventSource imEventSource;

    public ImSendController(JilaliProperties properties, ObjectMapper om, ImEventSource imEventSource) {
        this.om = om;
        this.imEventSource = imEventSource;
        // Single-user BFF today: the caller uid is whoever's JWT is configured for this process.
        // Multiple-caller setups would read the JWT from the request's Authorization header instead.
        this.callerUserId = UidExtractor.uidAsLong(properties.defaultAuthToken(), om);
    }

    /** Send a read-receipt for one upstream {@code msgId} to a peer ({@code userId} is the peer). */
    @Post("/{userId}/read")
    public HttpResponse<Void> read(@PathVariable("userId") long userId, @Body ReadReceiptRequest body) {
        if (body.msgId() == null || body.msgId().isBlank()) {
            return HttpResponse.badRequest();
        }
        imEventSource.sendOutbound(
            HtImPacketFramer.buildReadReceipt(callerUserId, userId, body.msgId())
        );
        return HttpResponse.noContent();
    }

    /**
     * Send a typing-indicator on/off to a peer. Caller ({@code Angular composer}) debounces
     * this to a 1Hz cadence while input is non-empty and a one-shot "off" on input empty or blur.
     */
    @Post(value = "/{userId}/typing", consumes = MediaType.APPLICATION_JSON)
    public HttpResponse<Void> typing(@PathVariable("userId") long userId, @Body TypingRequest body) {
        imEventSource.sendOutbound(
            HtImPacketFramer.buildTypingIndicator(callerUserId, userId, body.typing())
        );
        return HttpResponse.noContent();
    }

    /**
     * Send a 1:1 private message in one of six shapes. The {@link SendMessageRequest#kind()}
     * selector mirrors the legacy {@code sendTextMessage} dispatch. The body is zlib-deflated
     * JSON (same shape HelloTalk's mobile client sends), so the upstream treats this identically
     * to the iOS app's packets.
     */
    @Post(value = "/{userId}/send", consumes = MediaType.APPLICATION_JSON)
    public HttpResponse<Void> send(@PathVariable("userId") long userId, @Body SendMessageRequest body) {
        try {
            byte[] json = buildSendMessageJson(body).getBytes();
            imEventSource.sendOutbound(
                HtImPacketFramer.buildPrivateMessagePacket(callerUserId, userId, json, true)
            );
            return HttpResponse.noContent();
        } catch (IOException e) {
            return HttpResponse.serverError();
        }
    }

    /**
     * Mirrors the legacy {@code sendTextMessage} body-shaping. Every shape inherits the same
     * envelope (text/image/etc. flag fields, language tag, version), only its inner payload
     * diverges — see {@code prvgmsgpacket.js} lines 184-310 for the canonical layout.
     */
    private static String buildSendMessageJson(SendMessageRequest body) throws IOException {
        String msgId = body.msgId() != null && !body.msgId().isBlank()
            ? body.msgId()
            : java.util.UUID.randomUUID().toString();
        long fromId = body.fromId();
        String fromNickname = body.fromNickname();
        Long fromProfileTs = body.fromProfileTs();

        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("msg_id", msgId);
        msg.put("send_ts", System.currentTimeMillis() / 1000L);
        msg.put("msg_type", body.kind());
        switch (body.kind()) {
            case "text" -> msg.put("text", Map.of(
                "reportIndex", 0,
                "reportText", "",
                "text", body.text() != null ? body.text() : ""));
            case "image" -> {
                Map<String, Object> image = new LinkedHashMap<>();
                image.put("compressed_url", "");
                image.put("height", body.height() != null ? body.height() : 0);
                image.put("name", body.localPath() != null ? body.localPath() : "");
                image.put("size", body.size() != null ? body.size() : 0);
                image.put("type", body.mimeType() != null ? body.mimeType() : "image/png");
                image.put("url", body.url() != null ? body.url() : "");
                image.put("width", body.width() != null ? body.width() : 0);
                msg.put("image", image);
            }
            case "voice_room" -> msg.put("voice_room", body.roomData());
            case "live_link"  -> msg.put("live_link", body.roomData());
            case "introduction" -> msg.put("introduction", body.roomData());
            case "send_gift"  -> msg.put("send_gift", body.gift());
            default           -> throw new IOException("unsupported message kind: " + body.kind());
        }
        msg.put("chat_follow_notify", 0);
        msg.put("correction_gift_notify", 0);
        msg.put("cost_diamonds", 0);
        msg.put("pay_chat_cost_virtual_val", 0);
        msg.put("pay_chat_switch", 0);
        msg.put("recv_diamonds", 0);
        msg.put("source", "Chat List");
        msg.put("to_payer", false);
        msg.put("valid_time", 0);
        if (fromNickname != null) msg.put("from_nickname", fromNickname);
        if (fromProfileTs != null) msg.put("from_profile_ts", fromProfileTs);

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("msg", msg);
        envelope.put("version", 394024);
        envelope.put("client_lang", "English");
        return new ObjectMapper().writeValueAsString(envelope);
    }

    public record ReadReceiptRequest(@NotBlank String msgId) {}
    public record TypingRequest(boolean typing) {}
    public record SendMessageRequest(
        String kind,                   // text | image | voice_room | live_link | introduction | send_gift
        String msgId,
        Long fromId,
        String fromNickname,
        Long fromProfileTs,
        // text
        String text,
        // image
        String url, String localPath, Long size, Long width, Long height, String mimeType,
        // voice_room / live_link / introduction
        Object roomData,
        // send_gift
        Object gift
    ) {}
}
