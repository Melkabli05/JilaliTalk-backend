package com.jilali.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jilali.comment.dto.CaptionHistoryResponse;
import com.jilali.comment.dto.CaptionSwitchRequest;
import com.jilali.comment.dto.CommentListResponse;
import com.jilali.comment.dto.SendCommentRequest;
import com.jilali.core.JilaliEnvelope;
import com.jilali.manager.dto.ApproveManagerRequest;
import com.jilali.manager.dto.ManagerJudgeResponse;
import com.jilali.manager.dto.ManagerListResponse;
import com.jilali.manager.dto.SetManagerRequest;
import com.jilali.room.dto.BatchQueryRequest;
import com.jilali.room.dto.BatchQueryResponse;
import com.jilali.room.dto.CategoryTopicListResponse;
import com.jilali.room.dto.ChannelListItem;
import com.jilali.room.dto.ChannelListResponse;
import com.jilali.room.dto.CreateVoiceChannelRequest;
import com.jilali.room.dto.CreateVoiceChannelResponse;
import com.jilali.room.dto.EndChannelRequest;
import com.jilali.room.dto.LanguageGroup;
import com.jilali.room.dto.RoomLevelConfigResponse;
import com.jilali.room.dto.UpdateVoiceChannelRequest;
import com.jilali.room.dto.VoiceRoomInfoResponse;
import com.jilali.signin.dto.ClaimRewardRequest;
import com.jilali.signin.dto.ClaimTaskRewardRequest;
import com.jilali.signin.dto.RoomLevelRewardResponse;
import com.jilali.signin.dto.VoiceSignPanelResponse;
import com.jilali.stage.dto.DeviceControlRequest;
import com.jilali.stage.dto.KickRequest;
import com.jilali.stage.dto.PublisherTokenResponse;
import com.jilali.stage.dto.RaiseHandApprovalRequest;
import com.jilali.stage.dto.RaiseHandRequest;
import com.jilali.stage.dto.StageActionRequest;
import com.jilali.stage.dto.StageInviteApprovalRequest;
import com.jilali.stage.dto.StageInviteRequest;
import com.jilali.stage.dto.StageListResponse;
import com.jilali.user.dto.BatchStatusRequest;
import com.jilali.user.dto.BatchStatusResponse;
import com.jilali.user.dto.HeartbeatRequest;
import com.jilali.user.dto.HostStatus;
import com.jilali.user.dto.RoomUserListRequest;
import com.jilali.user.dto.RoomUserListResponse;
import com.jilali.user.dto.UserStatus;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
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
            @QueryValue("exclude_cname") @Nullable String excludeCname,
            @QueryValue String scene);

    @Get("/channel_list_recommend/live")
    JilaliEnvelope<ChannelListResponse> recommendLiveRooms(@QueryValue String scene);

    @Get("/channel_recommend/voice")
    JilaliEnvelope<ChannelListItem> recommendSingleVoiceRoom(@QueryValue("lang_id") int langId);

    @Get("/language_group/voice")
    JilaliEnvelope<List<LanguageGroup>> languageGroupVoice(@QueryValue String scene);

    @Get("/language_group/live")
    JilaliEnvelope<List<LanguageGroup>> languageGroupLive();

    @Get("/category_topic_list")
    JilaliEnvelope<CategoryTopicListResponse> categoryTopicList(@QueryValue("busi_type") int busiType);

    // ---- Info (enveloped) ----

    @Get("/voice_room_info")
    JilaliEnvelope<VoiceRoomInfoResponse> voiceRoomInfo(@QueryValue String cname);

    @Get("/live_room_info")
    JilaliEnvelope<VoiceRoomInfoResponse> liveRoomInfo(@QueryValue String cname);

    @Get("/channel_basic_info")
    JilaliEnvelope<Map<String, Object>> channelBasicInfo(@QueryValue String cname);

    @Post("/batch_query_channel")
    JilaliEnvelope<BatchQueryResponse> batchQueryChannel(@Body BatchQueryRequest body);

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
    @Nullable
    JilaliEnvelope<Map<String, Object>> userStartedChannel(@QueryValue("busi_type") int busiType);

    @Get("/user_latest_channel")
    JilaliEnvelope<Map<String, Object>> userLatestChannel(@QueryValue("busi_type") int busiType);

    // ---- User room actions (enveloped, null data on success) ----

    @Post("/user/join")
    JilaliEnvelope<Object> joinRoom(@Body JoinQuitRequest body);

    @Post("/user/quit")
    JilaliEnvelope<Object> quitRoom(@Body JoinQuitRequest body);

    @Post("/user/heartbeat")
    JilaliEnvelope<Object> heartbeat(@Body HeartbeatRequest body);

    @Post("/user/list")
    JilaliEnvelope<RoomUserListResponse> roomUserList(@Body RoomUserListRequest body);

    // ---- User status& profile (extended) ----

    @Post("/user/status_list")
    JilaliEnvelope<BatchStatusResponse> batchUserStatus(@Body BatchStatusRequest body);

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
    JilaliEnvelope<StageListResponse> stageList(@QueryValue("busi_type") int busiType,
                                                              @QueryValue String cname);

    @Post("/stage/join")
    JilaliEnvelope<Object> stageJoin(@Body StageActionRequest body);

    @Post("/stage/quit")
    JilaliEnvelope<Object> stageQuit(@Body StageActionRequest body);

    @Post("/stage/raisehand")
    JilaliEnvelope<Object> raiseHand(@Body RaiseHandRequest body);

    @Post("/stage/kick")
    JilaliEnvelope<Object> stageKick(@Body KickRequest body);

    @Post("/stage/raisehand_approval")
    JilaliEnvelope<Object> raiseHandApproval(@Body RaiseHandApprovalRequest body);

    @Post("/stage/invite")
    JilaliEnvelope<Object> stageInvite(@Body StageInviteRequest body);

    @Post("/stage/invite_approval")
    JilaliEnvelope<Object> stageInviteApproval(@Body StageInviteApprovalRequest body);

    @Post("/stage/device_control")
    JilaliEnvelope<Object> deviceControl(@Body DeviceControlRequest body);

    @Get("/publisher_rtc_token")
    JilaliEnvelope<PublisherTokenResponse> publisherRtcToken(@QueryValue String cname);

    // ---- Manager ----

    @Get("/user/manager_list")
    JilaliEnvelope<ManagerListResponse> managerList(@QueryValue String cname,
                                                                    @QueryValue("host_id") long hostId);

    @Post("/user/set_managers")
    JilaliEnvelope<Object> setManagers(@Body SetManagerRequest body);

    @Post("/user/approve_manager")
    JilaliEnvelope<Object> approveManager(@Body ApproveManagerRequest body);

    @Get("/user/manager_judge")
    JilaliEnvelope<ManagerJudgeResponse> managerJudge(@QueryValue String cname,
                                                                      @QueryValue("host_id") long hostId);

    // ---- Comments & Captions ----

    @Get("/comment")
    JilaliEnvelope<CommentListResponse> comments(@QueryValue("busi_type") int busiType,
                                                                 @QueryValue String cname);

    @Post("/comment")
    JilaliEnvelope<Object> sendComment(@Body SendCommentRequest body);

    @Get("/caption/history")
    JilaliEnvelope<CaptionHistoryResponse> captionHistory(@QueryValue("busi_type") int busiType,
                                                                          @QueryValue String cname,
                                                                          @QueryValue("page_size") int pageSize);

    @Post("/caption/switch")
    JilaliEnvelope<Object> captionSwitch(@Body CaptionSwitchRequest body);

    // ---- Sign-in& Tasks ----

    @Get("/voice_sign_panel")
    JilaliEnvelope<VoiceSignPanelResponse> voiceSignPanel(@QueryValue String cname);

    @Get("/voice/tasks")
    JilaliEnvelope<Object> voiceTasks();

    @Post("/voice/task/reward")
    JilaliEnvelope<Object> voiceTaskReward(@Body ClaimTaskRewardRequest body);

    @Get("/voice/room_level/reward")
    JilaliEnvelope<RoomLevelRewardResponse> roomLevelReward(
        @QueryValue String cname,
        @QueryValue("host_id") long hostId,
        @QueryValue int level
    );

    @Post("/voice/room_level/reward")
    JilaliEnvelope<Object> claimRoomLevelReward(@Body ClaimRewardRequest body);

    @Get("/voice/room_level/config")
    JilaliEnvelope<RoomLevelConfigResponse> roomLevelConfig(
        @QueryValue String cname,
        @QueryValue("host_id") long hostId
    );

    // ---- User status & profile ----

    @Get("/user/status")
    JilaliEnvelope<UserStatus> userStatus(@QueryValue("user_id") long userId);

    @Get("/host_status")
    JilaliEnvelope<HostStatus> hostStatus();

    /**
     * Profile is served as a binary {@code bin/cc2018} payload, NOT JSON. We return the raw bytes
     * and let the controller forward them with the upstream content type rather than attempting a
     * deserialization that would always fail.
     */
    @Get(value = "/user/profile", processes = MediaType.APPLICATION_OCTET_STREAM)
    byte[] userProfile(@QueryValue("busi_type") int busiType,
                       @QueryValue String cname,
                       @QueryValue("user_id") long userId);

    @Serdeable
    record JoinQuitRequest(String cname, @JsonProperty("busi_type") int busiType) {
    }
}
