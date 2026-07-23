package com.jilali.roomcontext.api;

import com.jilali.core.AuthTokenHolder;
import com.jilali.realtime.RoomEventSource;
import com.jilali.realtime.dto.RoomRealtimeEvent;
import com.jilali.roomcontext.application.port.out.UserUpstreamPort;
import com.jilali.roomcontext.domain.service.GhostPublisherRegistry;
import com.jilali.roomcontext.infrastructure.client.CallerIdentity;
import com.jilali.roomcontext.infrastructure.dto.user.BatchStatusRequest;
import com.jilali.roomcontext.infrastructure.dto.user.BatchStatusResponse;
import com.jilali.roomcontext.infrastructure.dto.user.EnrichBatchRequest;
import com.jilali.roomcontext.infrastructure.dto.user.EnrichBatchResponse;
import com.jilali.roomcontext.infrastructure.dto.user.HeartbeatRequest;
import com.jilali.roomcontext.infrastructure.dto.user.HostStatus;
import com.jilali.roomcontext.infrastructure.dto.user.RoomUserListRequest;
import com.jilali.roomcontext.infrastructure.dto.user.RoomUserListResponse;
import com.jilali.roomcontext.infrastructure.dto.user.RoomUserProfileResponse;
import com.jilali.roomcontext.infrastructure.dto.user.UserInfo;
import com.jilali.roomcontext.infrastructure.dto.user.UserStatus;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/** New-architecture controller, temporarily mounted under {@code /api/v2} - see
 *  TranslateController's Javadoc for the coexistence rationale.
 *
 * <p>The room-scoped profile endpoint ({@code /{userId}/profile}) is decoded server-side via
 * {@code Cc2018Cipher} (see {@link com.jilali.roomcontext.infrastructure.client.UserProfileEncryptedClient#fetchRoomUserProfile})
 * rather than forwarded as raw bin/cc2018 bytes - it's the only upstream call that exposes the
 * viewer's follow relation to an arbitrary user. */
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/api/v2/users")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserUpstreamPort upstream;
    private final RoomEventSource roomEventSource;
    private final GhostPublisherRegistry ghostPublishers;
    private final AuthTokenHolder authToken;

    public UserController(
            UserUpstreamPort upstream,
            RoomEventSource roomEventSource,
            GhostPublisherRegistry ghostPublishers,
            AuthTokenHolder authToken) {
        this.upstream = upstream;
        this.roomEventSource = roomEventSource;
        this.ghostPublishers = ghostPublishers;
        this.authToken = authToken;
    }

    @Post("/rooms/{cname}/join")
    public HttpResponse<Void> join(@NotBlank String cname, @QueryValue(defaultValue = "2") int busiType) {
        upstream.joinRoom(cname, busiType);
        return HttpResponse.noContent();
    }

    @Post("/rooms/{cname}/quit")
    public HttpResponse<Void> quit(@NotBlank String cname, @QueryValue(defaultValue = "2") int busiType) {
        upstream.quitRoom(cname, busiType);
        return HttpResponse.noContent();
    }

    /**
     * Start "ghost publishing" — the caller is about to (or already has) start publishing
     * audio via Agora while remaining invisible in the audience roster. This deliberately
     * does NOT call {@code upstream.joinRoom} (that would add the caller to the real
     * audience roster, defeating the whole point). Instead it synthesizes a stage_join WS
     * event tagged {@code isGhost: true} and injects it directly into this room's live
     * event stream — see {@link RoomEventSource#emitSynthetic}. Receivers subscribe to the
     * caller's Agora audio (their client renders a ghost-badged stage entry rather than a
     * normal one) without the caller ever appearing in anyone's audience list.
     *
     * <p>Known limitation (v1, BFF-mediated only — see GhostPublisherRegistry's class doc):
     * a client that opens the room's WebSocket AFTER this event fired won't see the ghost
     * publisher until the next start/stop toggle re-emits the event. There is no persisted
     * "current ghost publishers" list included in the join-bundle response yet.
     */
    @Post("/rooms/{cname}/ghost-publish")
    public HttpResponse<Void> startGhostPublish(@NotBlank String cname, @QueryValue(defaultValue = "2") int busiType) {
        Long userId = CallerIdentity.currentUserId(authToken);
        if (userId == null) {
            log.warn("UserController: startGhostPublish called with no resolvable caller identity, cname='{}'", cname);
            return HttpResponse.badRequest();
        }
        if (!ghostPublishers.start(cname, userId)) {
            log.debug("UserController: startGhostPublish no-op, userId={} already ghost-publishing in cname='{}'", userId, cname);
            return HttpResponse.noContent();
        }
        roomEventSource.emitSynthetic(cname, new RoomRealtimeEvent.StageJoin(ghostStageUser(userId)));
        log.info("UserController: userId={} started ghost-publishing in cname='{}' busiType={}", userId, cname, busiType);
        return HttpResponse.noContent();
    }

    /** Mirror of {@link #startGhostPublish} — removes the caller from the ghost registry and
     *  synthesizes a stage_quit so receivers stop subscribing to their audio. */
    @Post("/rooms/{cname}/ghost-publish/stop")
    public HttpResponse<Void> stopGhostPublish(@NotBlank String cname, @QueryValue(defaultValue = "2") int busiType) {
        Long userId = CallerIdentity.currentUserId(authToken);
        if (userId == null) {
            log.warn("UserController: stopGhostPublish called with no resolvable caller identity, cname='{}'", cname);
            return HttpResponse.badRequest();
        }
        if (!ghostPublishers.stop(cname, userId)) {
            log.debug("UserController: stopGhostPublish no-op, userId={} was not ghost-publishing in cname='{}'", userId, cname);
            return HttpResponse.noContent();
        }
        roomEventSource.emitSynthetic(cname, new RoomRealtimeEvent.StageQuit(String.valueOf(userId)));
        log.info("UserController: userId={} stopped ghost-publishing in cname='{}' busiType={}", userId, cname, busiType);
        return HttpResponse.noContent();
    }

    /** Minimal synthetic StageUserEvent for a ghost publisher — most fields default to
     *  null/0/false since the frontend already falls back to its own ghost enrichment
     *  (UserInfoService.ensureFresh) for uid-only pushes, matching how the existing
     *  ghost-audience rendering already treats unenriched ghost uids. */
    private RoomRealtimeEvent.StageUserEvent ghostStageUser(long userId) {
        return new RoomRealtimeEvent.StageUserEvent(
            String.valueOf(userId), null, null, null,
            3, 0, null, null, 0, 0, 0, 0, 0L, 0, 0, null, 0, 0, null, null,
            true, false, true, true, false,
            null, null, null, null,
            -1, null, "#ffffff", 0, null,
            0, null, false, 0L, 0L, 0, 0, null, 0L, 0L, 0, null, 0,
            0, 0, null, null, 0, null, null, null, null, null,
            true);
    }

    @Post("/heartbeat")
    public HttpResponse<Void> heartbeat(@Valid @Body HeartbeatRequest request) {
        upstream.heartbeat(request);
        return HttpResponse.noContent();
    }

    @Post("/rooms/list")
    public RoomUserListResponse roomUsers(@Valid @Body RoomUserListRequest request) {
        return upstream.roomUsers(request);
    }

    @Post("/status/batch")
    public BatchStatusResponse batchStatus(@Valid @Body BatchStatusRequest request) {
        return upstream.batchStatus(request);
    }

    @Post("/enrich-batch")
    public EnrichBatchResponse enrichBatch(@Valid @Body EnrichBatchRequest request) {
        return upstream.enrichBatch(request);
    }

    @Get("/end-page/host")
    public Map<String, Object> endPageHost(
            @QueryValue(defaultValue = "2") int busiType, @QueryValue String cname,
            @QueryValue(value = "contributeListType", defaultValue = "topn") String contributeListType) {
        return upstream.endPageHost(busiType, cname, contributeListType);
    }

    @Get("/end-page/audience")
    public Map<String, Object> endPageAudience(@QueryValue(defaultValue = "2") int busiType, @QueryValue String cname) {
        return upstream.endPageAudience(busiType, cname);
    }

    @Get("/record/live")
    public Map<String, Object> recordLive(@QueryValue(defaultValue = "8") int limit, @QueryValue(defaultValue = "0") int offset) {
        return upstream.recordLive(limit, offset);
    }

    @Get("/{userId}/status")
    public UserStatus status(long userId) {
        return upstream.status(userId);
    }

    @Get("/host-status")
    public HostStatus hostStatus() {
        return upstream.hostStatus();
    }

    @Get("/{userId}/profile")
    public RoomUserProfileResponse profile(long userId, @QueryValue String cname, @QueryValue(defaultValue = "2") int busiType) {
        return upstream.profile(userId, cname, busiType);
    }

    @Get("/info")
    public UserInfo userInfo(@QueryValue long userId) {
        return upstream.userInfo(userId);
    }
}
