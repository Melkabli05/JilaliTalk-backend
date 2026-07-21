package com.jilali.im;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.core.AuthTokenHolder;
import com.jilali.core.UidExtractor;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.serde.annotation.Serdeable;
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
 * the JWT subject — see {@link AuthTokenHolder} + {@link UidExtractor#uidAsLong}.
 */
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/api/im/messages")
public class ImSendController {

    private final AuthTokenHolder authToken;
    private final ObjectMapper om;
    private final ImEventSource imEventSource;

    public ImSendController(AuthTokenHolder authToken, ObjectMapper om, ImEventSource imEventSource) {
        this.authToken = authToken;
        this.om = om;
        this.imEventSource = imEventSource;
    }

    /** Re-derives the caller uid from the live auth token on every call — see {@link AuthTokenHolder}. */
    private long callerUserId() {
        // Single-user BFF today: the caller uid is whoever's JWT is currently held.
        // Multiple-caller setups would read the JWT from the request's Authorization header instead.
        return UidExtractor.uidAsLong(authToken.get(), om);
    }

    /** Send a read-receipt for one upstream {@code msgId} to a peer ({@code userId} is the peer). */
    @Post("/{userId}/read")
    public HttpResponse<Void> read(@PathVariable("userId") long userId, @Body ReadReceiptRequest body) {
        if (body.msgId() == null || body.msgId().isBlank()) {
            return HttpResponse.badRequest();
        }
        int chatType = body.chatType() != null ? body.chatType() : 1;
        imEventSource.sendOutbound(
            HtImPacketFramer.buildReadReceipt(callerUserId(), userId, body.msgId(), chatType)
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
            HtImPacketFramer.buildTypingIndicator(callerUserId(), userId, body.typing())
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
            byte[] json = buildSendMessageJson(body, userId).getBytes();
            imEventSource.sendOutbound(
                HtImPacketFramer.buildPrivateMessagePacket(callerUserId(), userId, json, true)
            );
            return HttpResponse.noContent();
        } catch (IOException e) {
            return HttpResponse.serverError();
        }
    }

    /**
     * Mirrors the legacy {@code sendTextMessage} body-shaping — see {@code prvgmsgpacket.js}
     * lines 184-310. {@code send_gift} is NOT nested under {@code msg}: it's its own top-level
     * envelope with {@code from_id}/{@code to_id}/{@code msg_id} as siblings, no {@code version}/
     * {@code client_lang} wrapper — every other kind shares the common {@code msg{...}} envelope.
     */
    static String buildSendMessageJson(SendMessageRequest body, long toId) throws IOException {
        String msgId = body.msgId() != null && !body.msgId().isBlank()
            ? body.msgId()
            : java.util.UUID.randomUUID().toString();
        String fromNickname = body.fromNickname();

        if ("send_gift".equals(body.kind())) {
            GiftRequest g = body.gift();
            if (g == null) throw new IOException("send_gift requires a gift payload");
            Map<String, Object> gift = new LinkedHashMap<>();
            gift.put("anim_url", g.animUrl());
            gift.put("big_pic", g.bigPic() != null ? g.bigPic() : "");
            gift.put("gift_type", g.giftType());
            gift.put("id", g.id());
            gift.put("multi_name", g.multiName() != null ? g.multiName() : Map.of());
            gift.put("name", g.name());
            gift.put("small_pic", g.smallPic());
            gift.put("users", java.util.List.of(Map.of("user_id", toId, "user_name", "")));
            gift.put("user_size", 1);
            gift.put("is_select_all", 0);
            gift.put("view_size", 1);
            // IMSendGiftBean declares diamondVal as a String field on the wire (APK-confirmed
            // via its @SerializedName annotation), not a number.
            gift.put("diamond_val", String.valueOf(g.diamondVal()));
            gift.put("finish_wish", false);
            gift.put("is_birthday_gift", false);
            gift.put("have_birthday_user", true);

            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("send_gift", gift);
            envelope.put("from_id", String.valueOf(body.fromId()));
            envelope.put("to_id", toId);
            envelope.put("from_nickname", fromNickname);
            envelope.put("msg_id", msgId);
            envelope.put("msg_type", "send_gift");
            envelope.put("source", "Chat List");
            envelope.put("bubble", Map.of());
            return new ObjectMapper().writeValueAsString(envelope);
        }

        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("msg_id", msgId);
        msg.put("send_ts", System.currentTimeMillis());
        msg.put("msg_type", body.kind());
        switch (body.kind()) {
            case "text" -> {
                // IMTextBean's confirmed wire fields (APK @SerializedName) are is_gift_word/
                // message_id/text; reportIndex/reportText are kept alongside since dropping
                // an unconfirmed-but-possibly-load-bearing field is riskier than a harmless
                // extra one a Gson receiver ignores.
                String text = body.text() != null ? body.text() : "";
                msg.put("text", Map.of(
                    "reportIndex", 0,
                    "reportText", "",
                    "is_gift_word", 0,
                    "message_id", msgId,
                    "text", text));
                msg.put("source", "Chat List");
            }
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
                msg.put("source", "Chat List");
            }
            case "voice_room" -> msg.put("voice_room", body.roomData());
            case "live_link"  -> msg.put("live_link", body.roomData());
            case "introduction" -> {
                // Shape confirmed directly from the APK's IMIntroductionBean/UserProfile
                // classes (Gson @SerializedName on the actual fields): introduction =
                // {receiver_nick, sender_nick, user_profile: {user_id, nick_name, head_url,
                // sex, age, country, ...}} — also mirrored flat for older/alternate servers,
                // since unrecognized extra JSON fields are harmless to a Gson-based receiver.
                IntroductionRequest i = body.introduction();
                if (i == null) throw new IOException("introduction requires an introduction payload");
                Map<String, Object> profile = new LinkedHashMap<>();
                profile.put("user_id", i.userId());
                profile.put("nick_name", i.nickname());
                profile.put("head_url", i.headUrl() != null ? i.headUrl() : "");
                profile.put("sex", i.sex() != null ? i.sex() : "");
                profile.put("age", i.age() != null ? i.age() : 0);
                profile.put("country", i.nationality() != null ? i.nationality() : "");

                Map<String, Object> intro = new LinkedHashMap<>(profile);
                intro.put("user_profile", profile);
                intro.put("receiver_nick", "");
                intro.put("sender_nick", fromNickname != null ? fromNickname : "");
                msg.put("introduction", intro);
                msg.put("bubble", Map.of("id", 0));
            }
            default -> throw new IOException("unsupported message kind: " + body.kind());
        }
        msg.put("chat_follow_notify", 0);
        msg.put("correction_gift_notify", 0);
        msg.put("cost_diamonds", 0);
        msg.put("pay_chat_cost_virtual_val", 0);
        msg.put("pay_chat_switch", 0);
        msg.put("recv_diamonds", 0);
        msg.put("to_payer", false);
        msg.put("valid_time", 0);
        if (fromNickname != null) msg.put("from_nickname", fromNickname);
        if (body.fromProfileTs() != null) msg.put("from_profile_ts", body.fromProfileTs());

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("msg", msg);
        envelope.put("version", 394024);
        envelope.put("client_lang", "English");
        return new ObjectMapper().writeValueAsString(envelope);
    }

    /**
     * {@code chatType} matches the field on {@code HasReadRequest.smali} (re_output apktool_out,
     * the real Android client's read-receipt class). 1 = 1:1 DM. The protocol carries it as a
     * single byte after the length-prefixed msgId (see {@link HtImPacketFramer#buildReadReceipt}
     * for the full field-by-field smali citation) — the legacy JS shim
     * ({@code prvgmsgpacket.js:sendReadReceipt}) omits it entirely, which is why we now default
     * it to 1 server-side rather than crash on missing input.
     */
    @Serdeable
    public record ReadReceiptRequest(@NotBlank String msgId, Integer chatType) {}
    @Serdeable
    public record TypingRequest(boolean typing) {}
    @Serdeable
    public record IntroductionRequest(
        long userId, String nickname, String headUrl, String sex, Integer age, String nationality, String bio
    ) {}
    @Serdeable
    public record GiftRequest(
        long id, String name, Map<String, String> multiName, String smallPic, String bigPic,
        String animUrl, long diamondVal, int giftType
    ) {}
    @Serdeable
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
        // voice_room / live_link
        Object roomData,
        // introduction
        IntroductionRequest introduction,
        // send_gift
        GiftRequest gift
    ) {}
}
