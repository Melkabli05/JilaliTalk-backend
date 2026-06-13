package com.jilali.client;

import com.jilali.core.JilaliEnvelope;
import com.jilali.room.dto.ChannelListItem;
import com.jilali.room.dto.ChannelListResponse;
import com.jilali.room.dto.CreateVoiceChannelRequest;
import com.jilali.room.dto.CreateVoiceChannelResponse;
import com.jilali.room.dto.EndChannelRequest;
import com.jilali.room.dto.UpdateVoiceChannelRequest;
import com.jilali.room.dto.VoiceRoomInfoResponse;
import com.jilali.signin.dto.ClaimRewardRequest;
import com.jilali.signin.dto.RoomLevelRewardResponse;
import com.jilali.signin.dto.VoiceSignPanelResponse;
import com.jilali.stage.dto.KickRequest;
import com.jilali.stage.dto.RaiseHandRequest;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.serde.annotation.Serdeable;

import java.util.Map;

/**
 * The one and only downstream client. A BFF talks to a single upstream with one base URL and one
 * auth scheme, so splitting this per feature would just duplicate configuration. Auth and tracing
 * headers are injected by {@code HeaderPropagationFilter}, not declared here.
 * <p>
 * Two response shapes exist in Jilali and we model both faithfully:
 * <ul>
 *   <li>Listing endpoints return a bare {@code {"items":[...]}} object (no envelope).</li>
 *   <li>Action/info endpoints return the {@code {"code","msg","data"}} envelope.</li>
 * </ul>
 * Callers (services/controllers) are responsible for unwrapping envelopes via
 * {@code JilaliResponses.unwrap(...)}.
 */
@Client(id = "jlhub", path = "/livehub")
public interface JilaliClient {

    // ---- Discovery (bare {items} payloads) ----

    @Get("/channel_list/voice")
    JilaliEnvelope<ChannelListResponse> listVoiceRooms(@QueryValue("lang_id") int langId,
                                       @QueryValue int limit,
                                       @QueryValue int offset,
                                       @QueryValue int refresh);

    @Get("/channel_list/live")
    JilaliEnvelope<ChannelListResponse> listLiveRooms(@QueryValue("lang_id") int langId,
                                      @QueryValue int limit,
                                      @QueryValue int offset,
                                      @QueryValue int refresh);

    @Get("/channel_list_recommend/voice")
    JilaliEnvelope<ChannelListResponse> recommendVoiceRooms(
            @QueryValue("exclude_cname") @io.micronaut.core.annotation.Nullable String excludeCname,
            @QueryValue String scene);

    @Get("/channel_list_recommend/live")
    JilaliEnvelope<ChannelListResponse> recommendLiveRooms(@QueryValue String scene);

    @Get("/channel_recommend/voice")
    JilaliEnvelope<ChannelListItem> recommendSingleVoiceRoom(@QueryValue("lang_id") int langId);

    @Get("/language_group/voice")
    JilaliEnvelope<java.util.List<com.jilali.room.dto.LanguageGroup>> languageGroupVoice(@QueryValue String scene);

    @Get("/language_group/live")
    JilaliEnvelope<java.util.List<com.jilali.room.dto.LanguageGroup>> languageGroupLive();

    @Get("/category_topic_list")
    JilaliEnvelope<com.jilali.room.dto.CategoryTopicListResponse> categoryTopicList(@QueryValue("busi_type") int busiType);

    // ---- Info (enveloped) ----

    @Get("/voice_room_info")
    JilaliEnvelope<VoiceRoomInfoResponse> voiceRoomInfo(@QueryValue String cname);

    @Get("/live_room_info")
    JilaliEnvelope<VoiceRoomInfoResponse> liveRoomInfo(@QueryValue String cname);

    @Get("/channel_basic_info")
    JilaliEnvelope<Map<String, Object>> channelBasicInfo(@QueryValue String cname);

    @Post("/batch_query_channel")
    JilaliEnvelope<com.jilali.room.dto.BatchQueryResponse> batchQueryChannel(@Body com.jilali.room.dto.BatchQueryRequest body);

    @Get("/live_voice/cfg")
    JilaliEnvelope<Map<String, Object>> liveVoiceConfig();

    // ---- Lifecycle (enveloped) ----

    @Post("/create_voice_channel")
    JilaliEnvelope<CreateVoiceChannelResponse> createVoiceChannel(@Body CreateVoiceChannelRequest body);

    @Post("/update_voice_channel")
    JilaliEnvelope<Object> updateVoiceChannel(@Body UpdateVoiceChannelRequest body);

    @Post("/end_channel")
    JilaliEnvelope<Map<String, Object>> endChannel(@Body EndChannelRequest body);

    /** Returns a literal {@code null} body when the user has no active channel. */
    @Get("/user_started_channel")
    @io.micronaut.core.annotation.Nullable
    JilaliEnvelope<Map<String, Object>> userStartedChannel(@QueryValue("busi_type") int busiType);

    @Get("/user_latest_channel")
    JilaliEnvelope<Map<String, Object>> userLatestChannel(@QueryValue("busi_type") int busiType);

    // ---- User room actions (enveloped, null data on success) ----

    @Post("/user/join")
    JilaliEnvelope<Object> joinRoom(@Body JoinQuitRequest body);

    @Post("/user/quit")
    JilaliEnvelope<Object> quitRoom(@Body JoinQuitRequest body);

    @Post("/user/heartbeat")
    JilaliEnvelope<Object> heartbeat(@Body com.jilali.user.dto.HeartbeatRequest body);

    @Post("/user/list")
    JilaliEnvelope<com.jilali.user.dto.RoomUserListResponse> roomUserList(@Body com.jilali.user.dto.RoomUserListRequest body);

    // ---- User status& profile (extended) ----

    @Post("/user/status_list")
    JilaliEnvelope<com.jilali.user.dto.BatchStatusResponse> batchUserStatus(@Body com.jilali.user.dto.BatchStatusRequest body);

    @Get("/user_end_page/host")
    JilaliEnvelope<Map<String, Object>> userEndPageHost(@QueryValue("busi_type") int busiType,
                                        @QueryValue String cname,
                                        @QueryValue("contribute_list_type") String contributeListType);

    @Get("/user_end_page/audience")
    JilaliEnvelope<Map<String, Object>> userEndPageAudience(@QueryValue("busi_type") int busiType,
                                            @QueryValue String cname);

    @Get("/user_record_live")
    JilaliEnvelope<Map<String, Object>> userRecordLive(@QueryValue int limit, @QueryValue int offset);

    // ---- Stage ----

    @Get("/stage/list")
    JilaliEnvelope<com.jilali.stage.dto.StageListResponse> stageList(@QueryValue("busi_type") int busiType,
                                                              @QueryValue String cname);

    @Post("/stage/join")
    JilaliEnvelope<Object> stageJoin(@Body com.jilali.stage.dto.StageActionRequest body);

    @Post("/stage/quit")
    JilaliEnvelope<Object> stageQuit(@Body com.jilali.stage.dto.StageActionRequest body);

    @Post("/stage/raisehand")
    JilaliEnvelope<Object> raiseHand(@Body RaiseHandRequest body);

    @Post("/stage/kick")
    JilaliEnvelope<Object> stageKick(@Body KickRequest body);

    @Post("/stage/raisehand_approval")
    JilaliEnvelope<Object> raiseHandApproval(@Body com.jilali.stage.dto.RaiseHandApprovalRequest body);

    @Post("/stage/invite")
    JilaliEnvelope<Object> stageInvite(@Body com.jilali.stage.dto.StageInviteRequest body);

    @Post("/stage/invite_approval")
    JilaliEnvelope<Object> stageInviteApproval(@Body com.jilali.stage.dto.StageInviteApprovalRequest body);

    @Post("/stage/device_control")
    JilaliEnvelope<Object> deviceControl(@Body com.jilali.stage.dto.DeviceControlRequest body);

    @Get("/publisher_rtc_token")
    JilaliEnvelope<com.jilali.stage.dto.PublisherTokenResponse> publisherRtcToken(@QueryValue String cname);

    // ---- Manager ----

    @Get("/user/manager_list")
    JilaliEnvelope<com.jilali.manager.dto.ManagerListResponse> managerList(@QueryValue String cname,
                                                                    @QueryValue("host_id") long hostId);

    @Post("/user/set_managers")
    JilaliEnvelope<Object> setManagers(@Body com.jilali.manager.dto.SetManagerRequest body);

    @Post("/user/approve_manager")
    JilaliEnvelope<Object> approveManager(@Body com.jilali.manager.dto.ApproveManagerRequest body);

    @Get("/user/manager_judge")
    JilaliEnvelope<com.jilali.manager.dto.ManagerJudgeResponse> managerJudge(@QueryValue String cname,
                                                                      @QueryValue("host_id") long hostId);

    // ---- Comments & Captions ----

    @Get("/comment")
    JilaliEnvelope<com.jilali.comment.dto.CommentListResponse> comments(@QueryValue("busi_type") int busiType,
                                                                 @QueryValue String cname);

    @Get("/comment_notify")
    JilaliEnvelope<com.jilali.comment.dto.CommentNotifyResponse> commentNotify(@QueryValue("busi_type") int busiType);

    @Get("/caption/history")
    JilaliEnvelope<com.jilali.comment.dto.CaptionHistoryResponse> captionHistory(@QueryValue("busi_type") int busiType,
                                                                          @QueryValue String cname,
                                                                          @QueryValue("page_size") int pageSize);

    @Post("/caption/switch")
    JilaliEnvelope<Object> captionSwitch(@Body com.jilali.comment.dto.CaptionSwitchRequest body);

    // ---- Sign-in& Tasks ----

    @Get("/voice_sign_panel")
    JilaliEnvelope<VoiceSignPanelResponse> voiceSignPanel(@QueryValue String cname);

    @Get("/voice/tasks")
    JilaliEnvelope<Object> voiceTasks();

    @Get("/voice/room_level/reward")
    JilaliEnvelope<RoomLevelRewardResponse> roomLevelReward(
 @QueryValue String cname,
        @QueryValue("host_id") long hostId,
        @QueryValue int level
    );

    @Post("/voice/room_level/reward")
    JilaliEnvelope<Object> claimRoomLevelReward(@Body ClaimRewardRequest body);

    // ---- User status & profile ----

    @Get("/user/status")
    JilaliEnvelope<com.jilali.user.dto.UserStatus> userStatus(@QueryValue("user_id") long userId);

    @Get("/host_status")
    JilaliEnvelope<com.jilali.user.dto.HostStatus> hostStatus();

    /**
     * Profile is served as a binary {@code bin/cc2018} payload, NOT JSON. We return the raw bytes
     * and let the controller forward them with the upstream content type rather than attempting a
     * deserialization that would always fail.
     */
    @Get(value = "/user/profile", processes = io.micronaut.http.MediaType.APPLICATION_OCTET_STREAM)
    byte[] userProfile(@QueryValue("busi_type") int busiType,
                       @QueryValue String cname,
                       @QueryValue("user_id") long userId);

    @Serdeable
    record JoinQuitRequest(String cname, int busi_type) {
    }

}
