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
import com.jilali.room.dto.JoinBundleResponse;
import com.jilali.room.dto.AudienceReconcileResponse;
import com.jilali.realtime.RoomEventSource;
import com.jilali.room.dto.LanguageGroup;
import com.jilali.room.dto.UpdateVoiceChannelRequest;
import com.jilali.room.dto.VoiceRoomInfoResponse;
import com.jilali.user.dto.RoomUserListRequest;
import io.micronaut.cache.annotation.Cacheable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.validation.Valid;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

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
    private final RoomJoinService roomJoinService;
    private final RoomEventSource roomEventSource;
    private final RoomsSearchService roomsSearchService;

    public RoomController(JilaliClient client, JilaliProperties properties,
                          RoomJoinService roomJoinService, RoomEventSource roomEventSource,
                          RoomsSearchService roomsSearchService) {
        this.client = client;
        this.properties = properties;
        this.roomJoinService = roomJoinService;
        this.roomEventSource = roomEventSource;
        this.roomsSearchService = roomsSearchService;
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

    @Get("/{type}/search")
    public ChannelListResponse searchRooms(
            String type,
            @QueryValue String query,
            @QueryValue(defaultValue = "0") int langId,
            @QueryValue(defaultValue = "5") int maxPages) {
        return roomsSearchService.search(type, query, langId, maxPages);
    }

    @Cacheable("reference-data")
    @Get("/language-groups/voice")
    public List<LanguageGroup> languageGroupsVoice(
            @QueryValue(defaultValue = "create") String scene) {
        return JilaliResponses.unwrap(client.languageGroupVoice(scene));
    }

    @Cacheable("reference-data")
    @Get("/language-groups/live")
    public List<LanguageGroup> languageGroupsLive() {
        return JilaliResponses.unwrap(client.languageGroupLive());
    }

    @Cacheable("reference-data")
    @Get("/categories")
    public CategoryTopicListResponse categories(
            @QueryValue(defaultValue = "2") int busiType) {
        return JilaliResponses.unwrap(client.categoryTopicList(busiType));
    }

    // ---- Info ----

    @Get("/voice/{cname}")
    public VoiceRoomInfoResponse voiceRoomInfo(String cname) {
        // Same retry the joinBundle path applies — a fresh-room 5xx on upstream's
        // voice_room_info is recoverable with a short wait, not a fatal "Room not found".
        // The frontend's create-room flow hits this single-call endpoint directly via
        // fresh=true (see RoomPageComponent / VideoRoomPageComponent), so without this
        // protection a freshly created room's first viewport load 500s and bounces the
        // user back to home with a wrong "please create a new one" message.
        var info = withUpstreamRetryOrRethrow(() ->
                JilaliResponses.unwrap(client.voiceRoomInfo(cname)));
        return decryptRtcToken(info);
    }

    @Get("/live/{cname}")
    public VoiceRoomInfoResponse liveRoomInfo(String cname) {
        // Same retry note as voiceRoomInfo above (this is the fresh-room counterpart
        // for the live/video room page).
        var info = withUpstreamRetryOrRethrow(() ->
                JilaliResponses.unwrap(client.liveRoomInfo(cname)));
        return decryptRtcToken(info);
    }

    /**
     * Adapts {@link RoomJoinService#withUpstreamRetry} (declared {@code throws Exception} so it
     * can wrap a {@link java.util.concurrent.Callable}) to these two endpoints, which have no
     * checked exception of their own to declare. Mirrors the catch pattern in
     * {@link RoomJoinService#joinBundle}: rethrow {@link HttpClientResponseException} unwrapped
     * so {@code GlobalErrorHandler.UpstreamTransportExceptionHandler} can log upstream's actual
     * response body, wrap anything else in a {@link RuntimeException}.
     */
    private <T> T withUpstreamRetryOrRethrow(Callable<T> call) {
        try {
            return roomJoinService.withUpstreamRetry(call);
        } catch (HttpClientResponseException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Upstream fetch failed", e);
        }
    }

    /**
     * Bundled join payload — fans four LiveHub calls (room info, stage roster, audience roster,
     * comment history) out in parallel server-side ({@link RoomJoinService#joinBundle}) so the
     * browser makes one round-trip instead of four. The Angular frontend's room-join flow
     * (RoomPageComponent/VideoRoomPageComponent {@code doEnterRoom}), its "refresh room" button
     * ({@code doRefreshRoomCore}), and its invisible→visible toggle ({@code makeVisible}) all
     * call this instead of {@code /voice}, {@code /live}, {@code /stage/list},
     * {@code /users/rooms/list}, and {@code /comments} individually.
     *
     * <p>Deliberately <em>not</em> used by the audience-reconciliation poll in the frontend's
     * {@code AudienceStore} — that only needs a roster refresh, so pulling in room info + stage +
     * comments on every drift-corrected poll would waste three upstream calls per check. That
     * poll uses {@link #audienceReconcile} instead.
     */
    @Get("/{cname}/join-bundle")
    public JoinBundleResponse joinBundle(
            String cname,
            @QueryValue(defaultValue = "2") int busiType) {
        return roomJoinService.joinBundle(cname, busiType);
    }

    /**
     * One round trip for the frontend's 30-second audience-drift-correction poll. Every 30s
     * {@code AudienceStore} sends its last-known revision as {@code sinceRevision}; if the
     * server-side revision (see {@link RoomEventSource#audienceRevision}) hasn't moved, this
     * returns immediately with {@code changed=false} and makes no upstream call at all. Only a
     * real roster change costs an upstream {@code roomUserList} round trip. Replaces the
     * previous two-call sequence (a revision check followed by a conditional full refetch).
     */
    @Get("/{cname}/audience-reconcile")
    public AudienceReconcileResponse audienceReconcile(
            String cname,
            @QueryValue(defaultValue = "2") int busiType,
            @QueryValue(defaultValue = "-1") int sinceRevision) {
        int revision = roomEventSource.audienceRevision(cname);
        if (revision <= sinceRevision) {
            return new AudienceReconcileResponse(revision, false, null);
        }
        var roster = JilaliResponses.unwrap(
                client.roomUserList(new RoomUserListRequest(List.of(3), cname, busiType)));
        return new AudienceReconcileResponse(revision, true, roster.list());
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

    @Cacheable("reference-data")
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
    public HttpResponse<Map<String, Object>> userStartedChannel(@QueryValue(defaultValue = "2") int busiType) {
        // A bare `null` return from a Micronaut controller method is treated as "no route
        // matched" and produces a 404, not a 200 with an empty body — even with @Nullable on
        // the return type, which is only a compile-time hint. "User has no active channel" is
        // the common, expected case (anyone about to create their first room hits this), so it
        // must be an explicit 200 with a null body, wrapped via HttpResponse.ok(), not a bare
        // return null.
        var resp = client.userStartedChannel(busiType);
        return HttpResponse.ok(resp == null ? null : resp.data());
    }

    @Get("/latest-settings")
    public Map<String, Object> userLatestChannel(@QueryValue(defaultValue = "2") int busiType) {
        return JilaliResponses.unwrap(client.userLatestChannel(busiType));
    }
}
