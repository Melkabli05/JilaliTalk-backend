package com.jilali.room;

import com.jilali.client.JilaliClient;
import com.jilali.client.JilaliResponses;
import com.jilali.core.JilaliException;
import com.jilali.core.JilaliProperties;
import com.jilali.room.dto.BatchQueryRequest;
import com.jilali.room.dto.BatchQueryResponse;
import com.jilali.room.dto.CategoryTopicListResponse;
import com.jilali.room.dto.ChannelListItem;
import com.jilali.room.dto.ChannelListResponse;
import com.jilali.room.dto.CreateVoiceChannelRequest;
import com.jilali.room.dto.CreateVoiceChannelResponse;
import com.jilali.room.dto.EndChannelRequest;
import com.jilali.room.dto.LanguageGroup;
import com.jilali.room.dto.UpdateVoiceChannelRequest;
import com.jilali.room.dto.VoiceRoomInfoResponse;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.validation.Valid;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Our public room API. Discovery and info are pure read pass-throughs — no service layer,
 * because a delegating-only service would be pure ceremony. Lifecycle mutations also go through
 * the gateway, which unwraps envelopes and raises typed exceptions that the global handler turns
 * into problem+json. Validation is enforced via {@code @Valid} at this boundary by Micronaut's
 * compile-time validation.
 */
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/api/rooms")
public class RoomController {

    private final JilaliClient client;
    private final JilaliProperties properties;

    public RoomController(JilaliClient client, JilaliProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    // ---- Discovery ----

    @Get("/voice")
    public ChannelListResponse listVoiceRooms(
            @QueryValue(defaultValue = "0") int langId,
            @QueryValue(defaultValue = "20") int limit,
            @QueryValue(defaultValue = "0") int offset,
            @QueryValue(defaultValue = "1") int refresh) {
        return JilaliResponses.unwrap(client.listVoiceRooms(langId, limit, offset, refresh));
    }

    @Get("/live")
    public ChannelListResponse listLiveRooms(
            @QueryValue(defaultValue = "0") int langId,
            @QueryValue(defaultValue = "20") int limit,
            @QueryValue(defaultValue = "0") int offset,
            @QueryValue(defaultValue = "1") int refresh) {
        return JilaliResponses.unwrap(client.listLiveRooms(langId, limit, offset, refresh));
    }

    @Get("/voice/recommend")
    public ChannelListResponse recommendVoiceRooms(
            @QueryValue(defaultValue = "") String excludeCname,
            @QueryValue(defaultValue = "in_room") String scene) {
        return JilaliResponses.unwrap(client.recommendVoiceRooms(excludeCname.isBlank() ? null : excludeCname, scene));
    }

    @Get("/live/recommend")
    public ChannelListResponse recommendLiveRooms(
            @QueryValue(defaultValue = "moment_tab") String scene) {
        return JilaliResponses.unwrap(client.recommendLiveRooms(scene));
    }

    @Get("/voice/recommend-single")
    public ChannelListItem recommendSingleVoiceRoom(
            @QueryValue(defaultValue = "0") int langId) {
        return JilaliResponses.unwrap(client.recommendSingleVoiceRoom(langId));
    }

    @Get("/language-groups/voice")
    public List<LanguageGroup> languageGroupsVoice(
            @QueryValue(defaultValue = "create") String scene) {
        return JilaliResponses.unwrap(client.languageGroupVoice(scene));
    }

    @Get("/language-groups/live")
    public List<LanguageGroup> languageGroupsLive() {
        return JilaliResponses.unwrap(client.languageGroupLive());
    }

    @Get("/categories")
    public CategoryTopicListResponse categories(
            @QueryValue(defaultValue = "2") int busiType) {
        return JilaliResponses.unwrap(client.categoryTopicList(busiType));
    }

    // ---- Info ----

    @Get("/voice/{cname}")
    public VoiceRoomInfoResponse voiceRoomInfo(String cname) {
        return decryptRtcToken(JilaliResponses.unwrap(client.voiceRoomInfo(cname)));
    }

    @Get("/live/{cname}")
    public VoiceRoomInfoResponse liveRoomInfo(String cname) {
        return decryptRtcToken(JilaliResponses.unwrap(client.liveRoomInfo(cname)));
    }

    /**
     * Shared by the voice and live info endpoints. LiveHub hands back an AES-encrypted
     * {@code rtc_info.token}; the browser Agora SDK needs the plain token (it carries the App ID)
     * or the gateway reports {@code CAN_NOT_GET_GATEWAY_SERVER: invalid vendor key, can not find
     * appid}. See {@link AgoraTokenCipher}.
     */
    private VoiceRoomInfoResponse decryptRtcToken(VoiceRoomInfoResponse resp) {
        if (resp == null || resp.channelInfo() == null) {
            return resp;
        }
        if (resp.channelInfo().rtcInfo() == null) {
            throw new JilaliException(
                    "Upstream returned no RTC info for channel",
                    HttpStatus.BAD_GATEWAY);
        }
        var encrypted = resp.channelInfo().rtcInfo().token();
        byte[] key = properties.agoraCipherKey().getBytes(StandardCharsets.US_ASCII);
        return resp.withRtcToken(AgoraTokenCipher.decrypt(encrypted, key));
    }

    @Get("/{cname}/basic")
    public Map<String, Object> channelBasicInfo(String cname) {
        return JilaliResponses.unwrap(client.channelBasicInfo(cname));
    }

    @Post("/batch-query")
    public BatchQueryResponse batchQuery(
            @Valid @Body BatchQueryRequest request) {
        return JilaliResponses.unwrap(client.batchQueryChannel(request));
    }

    @Get("/config")
    public Map<String, Object> liveVoiceConfig() {
        return JilaliResponses.unwrap(client.liveVoiceConfig());
    }

    // ---- Lifecycle ----

    @Post("/voice")
    public HttpResponse<CreateVoiceChannelResponse> createVoiceChannel(
            @Valid @Body CreateVoiceChannelRequest request) {
        return HttpResponse.created(JilaliResponses.unwrap(client.createVoiceChannel(request)));
    }

    @Post("/voice/update")
    public HttpResponse<Void> updateVoiceChannel(@Valid @Body UpdateVoiceChannelRequest request) {
        JilaliResponses.unwrap(client.updateVoiceChannel(request));
        return HttpResponse.noContent();
    }

    @Post("/end")
    public Map<String, Object> endChannel(@Valid @Body EndChannelRequest request) {
        return JilaliResponses.unwrap(client.endChannel(request));
    }

    @Get("/active")
    @Nullable
    public Map<String, Object> userStartedChannel(@QueryValue(defaultValue = "2") int busiType) {
        // null body when user has no active channel — pass through
        var resp = client.userStartedChannel(busiType);
        return resp == null ? null : resp.data();
    }

    @Get("/latest-settings")
    public Map<String, Object> userLatestChannel(@QueryValue(defaultValue = "2") int busiType) {
        return JilaliResponses.unwrap(client.userLatestChannel(busiType));
    }
}
