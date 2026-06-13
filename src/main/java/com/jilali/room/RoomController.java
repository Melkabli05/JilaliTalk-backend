package com.jilali.room;

import com.jilali.client.JilaliGateway;
import com.jilali.room.dto.ChannelListResponse;
import com.jilali.room.dto.CreateVoiceChannelRequest;
import com.jilali.room.dto.CreateVoiceChannelResponse;
import com.jilali.room.dto.EndChannelRequest;
import com.jilali.room.dto.UpdateVoiceChannelRequest;
import com.jilali.room.dto.VoiceRoomInfoResponse;
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
    public VoiceRoomInfoResponse voiceRoomInfo(String cname) {
        return decryptRtcToken(liveHub.voiceRoomInfo(cname));
    }

    @Get("/live/{cname}")
    public VoiceRoomInfoResponse liveRoomInfo(String cname) {
        return decryptRtcToken(liveHub.liveRoomInfo(cname));
    }

    /**
     * Shared by the voice and live info endpoints. LiveHub hands back an AES-encrypted
     * {@code rtc_info.token}; the browser Agora SDK needs the plain token (it carries the App ID)
     * or the gateway reports {@code CAN_NOT_GET_GATEWAY_SERVER: invalid vendor key, can not find
     * appid}. See {@link AgoraTokenCipher}.
     */
    private static VoiceRoomInfoResponse decryptRtcToken(VoiceRoomInfoResponse resp) {
        if (resp == null || resp.channelInfo() == null || resp.channelInfo().rtcInfo() == null) {
            return resp;
        }
        var encrypted = resp.channelInfo().rtcInfo().token();
        return resp.withRtcToken(AgoraTokenCipher.decrypt(encrypted));
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
