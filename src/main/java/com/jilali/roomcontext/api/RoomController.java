package com.jilali.roomcontext.api;

import com.jilali.client.JilaliResponses;
import com.jilali.client.JilaliClient;
import com.jilali.realtime.RoomEventSource;
import com.jilali.room.RoomJoinService;
import com.jilali.room.RoomsSearchService;
import com.jilali.room.dto.AudienceReconcileResponse;
import com.jilali.room.dto.BatchQueryRequest;
import com.jilali.room.dto.BatchQueryResponse;
import com.jilali.room.dto.CategoryTopicListResponse;
import com.jilali.room.dto.ChannelListItem;
import com.jilali.room.dto.ChannelListResponse;
import com.jilali.room.dto.CreateVoiceChannelRequest;
import com.jilali.room.dto.CreateVoiceChannelResponse;
import com.jilali.room.dto.EndChannelRequest;
import com.jilali.room.dto.JoinBundleResponse;
import com.jilali.room.dto.LanguageGroup;
import com.jilali.room.dto.UpdateVoiceChannelRequest;
import com.jilali.room.dto.VoiceRoomInfoResponse;
import com.jilali.roomcontext.application.port.out.RoomUpstreamPort;
import com.jilali.user.dto.RoomUserListRequest;
import io.micronaut.cache.annotation.Cacheable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.validation.Valid;

import java.util.List;
import java.util.Map;

/** New-architecture controller, temporarily mounted under {@code /api/v2} - see
 *  TranslateController's Javadoc for the coexistence rationale.
 *
 * <p>Deliberately depends on the existing legacy RoomJoinService/RoomEventSource/
 * RoomsSearchService directly (their public methods, joinBundle/audienceRevision/search)
 * rather than re-wrapping them behind a new port - those three already correctly implement
 * fan-out (StructuredTaskScope), drift-correction, and pagination/fuzzy-search respectively,
 * and re-deriving any of that logic here would be pure risk for zero benefit. This is the
 * most concurrency-sensitive capability in the whole migration (per docs/room-redesign/
 * 09-technical-risks.md R3), which is exactly why it reuses proven code instead of rebuilding
 * it from scratch. Everything else (pure pass-through discovery/info/lifecycle calls) goes
 * through the new RoomUpstreamPort, same pattern as every other slice in this migration. */
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/api/v2/rooms")
public class RoomController {

    private final RoomUpstreamPort upstream;
    private final RoomJoinService roomJoinService;
    private final RoomEventSource roomEventSource;
    private final RoomsSearchService roomsSearchService;
    private final JilaliClient client;

    public RoomController(RoomUpstreamPort upstream, RoomJoinService roomJoinService,
                           RoomEventSource roomEventSource, RoomsSearchService roomsSearchService,
                           JilaliClient client) {
        this.upstream = upstream;
        this.roomJoinService = roomJoinService;
        this.roomEventSource = roomEventSource;
        this.roomsSearchService = roomsSearchService;
        this.client = client;
    }

    @Get("/voice")
    public ChannelListResponse listVoiceRooms(
            @QueryValue(defaultValue = "0") int langId, @QueryValue(defaultValue = "20") int limit,
            @QueryValue(defaultValue = "0") int offset, @QueryValue(defaultValue = "1") int refresh) {
        return upstream.listVoiceRooms(langId, limit, offset, refresh);
    }

    @Get("/live")
    public ChannelListResponse listLiveRooms(
            @QueryValue(defaultValue = "0") int langId, @QueryValue(defaultValue = "20") int limit,
            @QueryValue(defaultValue = "0") int offset, @QueryValue(defaultValue = "1") int refresh) {
        return upstream.listLiveRooms(langId, limit, offset, refresh);
    }

    @Get("/voice/recommend")
    public ChannelListResponse recommendVoiceRooms(
            @QueryValue(defaultValue = "") String excludeCname, @QueryValue(defaultValue = "in_room") String scene) {
        return upstream.recommendVoiceRooms(excludeCname, scene);
    }

    @Get("/live/recommend")
    public ChannelListResponse recommendLiveRooms(@QueryValue(defaultValue = "moment_tab") String scene) {
        return upstream.recommendLiveRooms(scene);
    }

    @Get("/voice/recommend-single")
    public ChannelListItem recommendSingleVoiceRoom(@QueryValue(defaultValue = "0") int langId) {
        return upstream.recommendSingleVoiceRoom(langId);
    }

    @Get("/{type}/search")
    public ChannelListResponse searchRooms(String type, @QueryValue String query,
                                            @QueryValue(defaultValue = "0") int langId,
                                            @QueryValue(defaultValue = "5") int maxPages) {
        return roomsSearchService.search(type, query, langId, maxPages);
    }

    @Cacheable("reference-data")
    @Get("/language-groups/voice")
    public List<LanguageGroup> languageGroupsVoice(@QueryValue(defaultValue = "create") String scene) {
        return upstream.languageGroupsVoice(scene);
    }

    @Cacheable("reference-data")
    @Get("/language-groups/live")
    public List<LanguageGroup> languageGroupsLive() {
        return upstream.languageGroupsLive();
    }

    @Cacheable("reference-data")
    @Get("/categories")
    public CategoryTopicListResponse categories(@QueryValue(defaultValue = "2") int busiType) {
        return upstream.categories(busiType);
    }

    @Get("/voice/{cname}")
    public VoiceRoomInfoResponse voiceRoomInfo(String cname) {
        return upstream.decryptRtcToken(upstream.voiceRoomInfoRaw(cname));
    }

    @Get("/live/{cname}")
    public VoiceRoomInfoResponse liveRoomInfo(String cname) {
        return upstream.decryptRtcToken(upstream.liveRoomInfoRaw(cname));
    }

    @Get("/{cname}/join-bundle")
    public JoinBundleResponse joinBundle(String cname, @QueryValue(defaultValue = "2") int busiType) {
        return roomJoinService.joinBundle(cname, busiType);
    }

    @Get("/{cname}/audience-reconcile")
    public AudienceReconcileResponse audienceReconcile(
            String cname, @QueryValue(defaultValue = "2") int busiType,
            @QueryValue(defaultValue = "-1") int sinceRevision) {
        int revision = roomEventSource.audienceRevision(cname);
        if (revision <= sinceRevision) {
            return new AudienceReconcileResponse(revision, false, null);
        }
        var roster = JilaliResponses.unwrap(
            client.roomUserList(new RoomUserListRequest(List.of(3), cname, busiType)));
        return new AudienceReconcileResponse(revision, true, roster.list());
    }

    @Get("/{cname}/basic")
    public Map<String, Object> channelBasicInfo(String cname) {
        return upstream.channelBasicInfo(cname);
    }

    @Post("/batch-query")
    public BatchQueryResponse batchQuery(@Valid @Body BatchQueryRequest request) {
        return upstream.batchQuery(request);
    }

    @Cacheable("reference-data")
    @Get("/config")
    public Map<String, Object> liveVoiceConfig() {
        return upstream.liveVoiceConfig();
    }

    @Post("/voice")
    public HttpResponse<CreateVoiceChannelResponse> createVoiceChannel(@Valid @Body CreateVoiceChannelRequest request) {
        return HttpResponse.created(upstream.createVoiceChannel(request));
    }

    @Post("/voice/update")
    public HttpResponse<Void> updateVoiceChannel(@Valid @Body UpdateVoiceChannelRequest request) {
        upstream.updateVoiceChannel(request);
        return HttpResponse.noContent();
    }

    @Post("/end")
    public Map<String, Object> endChannel(@Valid @Body EndChannelRequest request) {
        return upstream.endChannel(request);
    }

    @Get("/active")
    public HttpResponse<Map<String, Object>> userStartedChannel(@QueryValue(defaultValue = "2") int busiType) {
        return HttpResponse.ok(upstream.activeChannel(busiType));
    }

    @Get("/latest-settings")
    public Map<String, Object> userLatestChannel(@QueryValue(defaultValue = "2") int busiType) {
        return upstream.latestSettings(busiType);
    }
}
