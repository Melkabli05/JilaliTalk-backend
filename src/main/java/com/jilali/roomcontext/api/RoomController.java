package com.jilali.roomcontext.api;

import com.jilali.realtime.RoomEventSource;
import com.jilali.roomcontext.application.port.out.RoomUpstreamPort;
import com.jilali.roomcontext.application.service.RoomJoinService;
import com.jilali.roomcontext.application.service.RoomsSearchService;
import com.jilali.roomcontext.infrastructure.client.JilaliResponses;
import com.jilali.roomcontext.infrastructure.client.UserJilaliClient;
import com.jilali.roomcontext.infrastructure.dto.room.AudienceReconcileResponse;
import com.jilali.roomcontext.infrastructure.dto.room.BatchQueryRequest;
import com.jilali.roomcontext.infrastructure.dto.room.BatchQueryResponse;
import com.jilali.roomcontext.infrastructure.dto.room.CategoryTopicListResponse;
import com.jilali.roomcontext.infrastructure.dto.room.ChannelListItem;
import com.jilali.roomcontext.infrastructure.dto.room.ChannelListResponse;
import com.jilali.roomcontext.infrastructure.dto.room.CreateVoiceChannelRequest;
import com.jilali.roomcontext.infrastructure.dto.room.CreateVoiceChannelResponse;
import com.jilali.roomcontext.infrastructure.dto.room.EndChannelRequest;
import com.jilali.roomcontext.infrastructure.dto.room.JoinBundleResponse;
import com.jilali.roomcontext.infrastructure.dto.room.LanguageGroup;
import com.jilali.roomcontext.infrastructure.dto.room.UpdateVoiceChannelRequest;
import com.jilali.roomcontext.infrastructure.dto.room.VoiceRoomInfoResponse;
import com.jilali.roomcontext.infrastructure.dto.user.RoomUserListRequest;
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
 * <p>All upstream HTTP calls (discovery/info/lifecycle pass-through, the join-bundle fan-out,
 * and the search fan-out) go through this bounded context's own dedicated clients
 * (RoomJilaliClient/StageJilaliClient/UserJilaliClient/CommentJilaliClient via RoomUpstreamPort/
 * RoomJoinService/RoomsSearchService) - zero dependency on the legacy client.JilaliClient god
 * interface.
 *
 * <p>One deliberate exception: {@code realtime.RoomEventSource.audienceRevision(cname)} for the
 * audience-reconcile drift-check counter. That class is the live WebSocket relay's in-memory
 * revision tracker (bumped by inbound upstream push events, not an HTTP call) - a completely
 * different subsystem from "the HTTP client to HelloTalk" this rebuild targets, and duplicating
 * the entire realtime WebSocket connector/pub-sub layer to avoid one 3-line method call would be
 * a large, unrelated, destabilizing undertaking for a still-live piece of infrastructure. The
 * actual upstream HTTP call in this same endpoint (the roster refetch) already goes through the
 * dedicated UserJilaliClient. */
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/api/v2/rooms")
public class RoomController {

    private final RoomUpstreamPort upstream;
    private final RoomJoinService roomJoinService;
    private final RoomEventSource roomEventSource;
    private final RoomsSearchService roomsSearchService;
    private final UserJilaliClient userClient;

    public RoomController(RoomUpstreamPort upstream, RoomJoinService roomJoinService,
                           RoomEventSource roomEventSource, RoomsSearchService roomsSearchService,
                           UserJilaliClient userClient) {
        this.upstream = upstream;
        this.roomJoinService = roomJoinService;
        this.roomEventSource = roomEventSource;
        this.roomsSearchService = roomsSearchService;
        this.userClient = userClient;
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
            userClient.roomUserList(new RoomUserListRequest(List.of(3), cname, busiType)));
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
