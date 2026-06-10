package com.jilali.room;

import com.jilali.client.JilaliGateway;
import com.jilali.room.dto.ChannelListResponse;
import com.jilali.room.dto.CreateVoiceChannelRequest;
import com.jilali.room.dto.CreateVoiceChannelResponse;
import com.jilali.room.dto.EndChannelRequest;
import com.jilali.room.dto.UpdateVoiceChannelRequest;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.validation.Valid;

import java.util.Map;

/**
 * Our public room API. Discovery and info are pure read pass-throughs and go straight to the
 * gateway — no service layer, because a delegating-only service would be pure ceremony. Lifecycle
 * mutations also go through the gateway, which unwraps envelopes and raises typed exceptions that
 * the global handler turns into problem+json. Validation is enforced via {@code @Valid} at this
 * boundary by Micronaut's compile-time validation.
 */
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/api/rooms")
public class RoomController {

    private final JilaliGateway liveHub;

    public RoomController(JilaliGateway liveHub) {
        this.liveHub = liveHub;
    }

    // ---- Discovery ----

    @Get("/voice")
    public ChannelListResponse listVoiceRooms(
            @QueryValue(defaultValue = "0") int langId,
            @QueryValue(defaultValue = "20") int limit,
            @QueryValue(defaultValue = "0") int offset,
            @QueryValue(defaultValue = "1") int refresh) {
        return liveHub.listVoiceRooms(langId, limit, offset, refresh);
    }

    @Get("/live")
    public ChannelListResponse listLiveRooms(
            @QueryValue(defaultValue = "0") int langId,
            @QueryValue(defaultValue = "20") int limit,
            @QueryValue(defaultValue = "0") int offset,
            @QueryValue(defaultValue = "1") int refresh) {
        return liveHub.listLiveRooms(langId, limit, offset, refresh);
    }

    @Get("/voice/recommend")
    public ChannelListResponse recommendVoiceRooms(
            @QueryValue(defaultValue = "") String excludeCname,
            @QueryValue(defaultValue = "in_room") String scene) {
        return liveHub.recommendVoiceRooms(excludeCname.isBlank() ? null : excludeCname, scene);
    }

    @Get("/live/recommend")
    public ChannelListResponse recommendLiveRooms(
            @QueryValue(defaultValue = "moment_tab") String scene) {
        return liveHub.recommendLiveRooms(scene);
    }

    @Get("/voice/recommend-single")
    public com.jilali.room.dto.ChannelListItem recommendSingleVoiceRoom(
            @QueryValue(defaultValue = "0") int langId) {
        return liveHub.recommendSingleVoiceRoom(langId);
    }

    @Get("/language-groups/voice")
    public java.util.List<com.jilali.room.dto.LanguageGroup> languageGroupsVoice(
            @QueryValue(defaultValue = "create") String scene) {
        return liveHub.languageGroupVoice(scene);
    }

    @Get("/language-groups/live")
    public java.util.List<com.jilali.room.dto.LanguageGroup> languageGroupsLive() {
        return liveHub.languageGroupLive();
    }

    @Get("/categories")
    public com.jilali.room.dto.CategoryTopicListResponse categories(
            @QueryValue(defaultValue = "2") int busiType) {
        return liveHub.categoryTopicList(busiType);
    }

    // ---- Info ----

    @Get("/voice/{cname}")
    public com.jilali.room.dto.VoiceRoomInfoResponse voiceRoomInfo(String cname) {
        return decryptRtcToken(liveHub.voiceRoomInfo(cname));
    }

    /**
     * LiveHub hands back an AES-encrypted {@code rtc_info.token}; the browser Agora SDK needs the
     * plain token (it carries the App ID). Rebuild the immutable response with the decrypted value
     * so callers never see ciphertext. See {@link AgoraTokenCipher}.
     */
    private static com.jilali.room.dto.VoiceRoomInfoResponse decryptRtcToken(
            com.jilali.room.dto.VoiceRoomInfoResponse resp) {
        if (resp == null || resp.channelInfo() == null || resp.channelInfo().rtcInfo() == null) {
            return resp;
        }
        var ch = resp.channelInfo();
        var rtc = ch.rtcInfo();
        var decrypted = new com.jilali.room.dto.VoiceRoomInfoResponse.ChannelInfo.RtcInfo(
                rtc.appId(), AgoraTokenCipher.decrypt(rtc.token()), rtc.engine());
        var newChannel = new com.jilali.room.dto.VoiceRoomInfoResponse.ChannelInfo(
                ch.name(), ch.langId(), ch.langs(), ch.topic(), ch.notice(), ch.noticePinType(),
                ch.hourRank(), ch.topLastHourRanking(), decrypted);
        return new com.jilali.room.dto.VoiceRoomInfoResponse(
                resp.hostInfo(), resp.reqUserInfo(), newChannel);
    }

    @Get("/live/{cname}")
    public Map<String, Object> liveRoomInfo(String cname) {
        return liveHub.liveRoomInfo(cname);
    }

    @Get("/{cname}/basic")
    public Map<String, Object> channelBasicInfo(String cname) {
        return liveHub.channelBasicInfo(cname);
    }

    @Post("/batch-query")
    public com.jilali.room.dto.BatchQueryResponse batchQuery(
            @Valid @Body com.jilali.room.dto.BatchQueryRequest request) {
        return liveHub.batchQueryChannel(request);
    }

    @Get("/config")
    public Map<String, Object> liveVoiceConfig() {
        return liveHub.liveVoiceConfig();
    }

    // ---- Lifecycle ----

    @Post("/voice")
    public HttpResponse<CreateVoiceChannelResponse> createVoiceChannel(
            @Valid @Body CreateVoiceChannelRequest request) {
        return HttpResponse.created(liveHub.createVoiceChannel(request));
    }

    @Post("/voice/update")
    public HttpResponse<Void> updateVoiceChannel(@Valid @Body UpdateVoiceChannelRequest request) {
        liveHub.updateVoiceChannel(request);
        return HttpResponse.noContent();
    }

    @Post("/end")
    public Map<String, Object> endChannel(@Valid @Body EndChannelRequest request) {
        return liveHub.endChannel(request);
    }

    @Get("/active")
    @io.micronaut.core.annotation.Nullable
    public Map<String, Object> userStartedChannel(@QueryValue(defaultValue = "2") int busiType) {
        return liveHub.userStartedChannel(busiType);
    }

    @Get("/latest-settings")
    public Map<String, Object> userLatestChannel(@QueryValue(defaultValue = "2") int busiType) {
        return liveHub.userLatestChannel(busiType);
    }
}
