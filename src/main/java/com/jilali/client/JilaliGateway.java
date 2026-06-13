package com.jilali.client;

import com.jilali.core.JilaliEnvelope;
import com.jilali.core.JilaliException;
import com.jilali.core.JilaliProperties;
import com.jilali.room.dto.ChannelListResponse;
import com.jilali.room.dto.CreateVoiceChannelRequest;
import com.jilali.room.dto.CreateVoiceChannelResponse;
import com.jilali.room.dto.EndChannelRequest;
import com.jilali.room.dto.UpdateVoiceChannelRequest;
import com.jilali.room.dto.VoiceRoomInfoResponse;
import com.jilali.signin.dto.ClaimRewardRequest;
import com.jilali.signin.dto.RoomLevelRewardResponse;
import com.jilali.signin.dto.VoiceSignPanelResponse;
import com.jilali.user.dto.UserInfo;
import com.jilali.user.dto.UserInfoRequest;
import com.jilali.user.dto.UserInfoResponse;
import com.jilali.crypto.Curve25519SessionGenerator;
import com.jilali.crypto.EncbinUtil;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * The seam between our application and Jilali. It owns envelope unwrapping so that everything
 * above it (services, controllers) deals only in plain payloads and never sees Jilali's
 * {@code {code,msg,data}} shape or its error codes. Non-zero codes become {@code JilaliException}
 * here, at the boundary, exactly once.
 * <p>
 * Bare-payload endpoints (listings, info) are exposed as-is; enveloped endpoints are unwrapped.
 */
@Singleton
public class JilaliGateway {

    private static final Logger log = LoggerFactory.getLogger(JilaliGateway.class);
    private final JilaliClient client;
    private final HttpClient httpClient;
    private final JilaliProperties properties;

    public JilaliGateway(JilaliClient client, @Client("jlhub") HttpClient jlhubClient, JilaliProperties properties) {
        this.client = client;
        this.httpClient = jlhubClient;
        this.properties = properties;
    }

    // Bare payloads pass straight through.
    public ChannelListResponse listVoiceRooms(int langId, int limit, int offset, int refresh) {
        log.info("listVoiceRooms called -> langId={}, limit={}, offset={}, refresh={}", langId, limit, offset, refresh);
        JilaliEnvelope<ChannelListResponse> resp = client.listVoiceRooms(langId, limit, offset, refresh);
        ChannelListResponse payload = JilaliResponses.unwrap(resp);
        log.info("listVoiceRooms response: items count={}", payload.items() != null ? payload.items().size() : "NULL");
        return payload;
    }

    public ChannelListResponse listLiveRooms(int langId, int limit, int offset, int refresh) {
        return JilaliResponses.unwrap(client.listLiveRooms(langId, limit, offset, refresh));
    }

    public ChannelListResponse recommendVoiceRooms(String excludeCname, String scene) {
        return JilaliResponses.unwrap(client.recommendVoiceRooms(excludeCname, scene));
    }

    public ChannelListResponse recommendLiveRooms(String scene) {
        return JilaliResponses.unwrap(client.recommendLiveRooms(scene));
    }

    public com.jilali.room.dto.ChannelListItem recommendSingleVoiceRoom(int langId) {
        return JilaliResponses.unwrap(client.recommendSingleVoiceRoom(langId));
    }

    public java.util.List<com.jilali.room.dto.LanguageGroup> languageGroupVoice(String scene) {
        return JilaliResponses.unwrap(client.languageGroupVoice(scene));
    }

    public java.util.List<com.jilali.room.dto.LanguageGroup> languageGroupLive() {
        return JilaliResponses.unwrap(client.languageGroupLive());
    }

    public com.jilali.room.dto.CategoryTopicListResponse categoryTopicList(int busiType) {
        return JilaliResponses.unwrap(client.categoryTopicList(busiType));
    }

    public VoiceRoomInfoResponse voiceRoomInfo(String cname) {
        return JilaliResponses.unwrap(client.voiceRoomInfo(cname));
    }


    public VoiceRoomInfoResponse liveRoomInfo(String cname) {
        return JilaliResponses.unwrap(client.liveRoomInfo(cname));
    }

    public Map<String, Object> channelBasicInfo(String cname) {
        return JilaliResponses.unwrap(client.channelBasicInfo(cname));
    }

    public com.jilali.room.dto.BatchQueryResponse batchQueryChannel(com.jilali.room.dto.BatchQueryRequest body) {
        return JilaliResponses.unwrap(client.batchQueryChannel(body));
    }

    public Map<String, Object> liveVoiceConfig() {
        return JilaliResponses.unwrap(client.liveVoiceConfig());
    }

    // Enveloped payloads are unwrapped at this boundary.
    public CreateVoiceChannelResponse createVoiceChannel(CreateVoiceChannelRequest body) {
        return JilaliResponses.unwrap(client.createVoiceChannel(body));
    }

    public void updateVoiceChannel(UpdateVoiceChannelRequest body) {
        JilaliResponses.unwrap(client.updateVoiceChannel(body));
    }

    public Map<String, Object> endChannel(EndChannelRequest body) {
        return JilaliResponses.unwrap(client.endChannel(body));
    }

    /** Upstream returns a literal null body when there is no active channel; we pass that through. */
    @io.micronaut.core.annotation.Nullable
    public Map<String, Object> userStartedChannel(int busiType) {
        JilaliEnvelope<Map<String, Object>> resp = client.userStartedChannel(busiType);
        return resp == null ? null : resp.data();
    }

    public Map<String, Object> userLatestChannel(int busiType) {
        return JilaliResponses.unwrap(client.userLatestChannel(busiType));
    }

    public void joinRoom(String cname, int busiType) {
        JilaliResponses.unwrap(client.joinRoom(new JilaliClient.JoinQuitRequest(cname, busiType)));
    }

    public void quitRoom(String cname, int busiType) {
        JilaliResponses.unwrap(client.quitRoom(new JilaliClient.JoinQuitRequest(cname, busiType)));
    }

    public void heartbeat(com.jilali.user.dto.HeartbeatRequest body) {
        JilaliResponses.unwrap(client.heartbeat(body));
    }

    public com.jilali.user.dto.RoomUserListResponse roomUserList(com.jilali.user.dto.RoomUserListRequest body) {
        return JilaliResponses.unwrap(client.roomUserList(body));
    }

    public com.jilali.user.dto.BatchStatusResponse batchUserStatus(com.jilali.user.dto.BatchStatusRequest body) {
        return JilaliResponses.unwrap(client.batchUserStatus(body));
    }

    public Map<String, Object> userEndPageHost(int busiType, String cname, String contributeListType) {
        return JilaliResponses.unwrap(client.userEndPageHost(busiType, cname, contributeListType));
    }

    public Map<String, Object> userEndPageAudience(int busiType, String cname) {
        return JilaliResponses.unwrap(client.userEndPageAudience(busiType, cname));
    }

    public Map<String, Object> userRecordLive(int limit, int offset) {
        return JilaliResponses.unwrap(client.userRecordLive(limit, offset));
    }

    // ---- Stage ----

    public com.jilali.stage.dto.StageListResponse stageList(int busiType, String cname) {
        return JilaliResponses.unwrap(client.stageList(busiType, cname));
    }

    public void stageJoin(com.jilali.stage.dto.StageActionRequest body) {
        JilaliResponses.unwrap(client.stageJoin(body));
    }

    public void stageQuit(com.jilali.stage.dto.StageActionRequest body) {
        JilaliResponses.unwrap(client.stageQuit(body));
    }

    public void raiseHand(com.jilali.stage.dto.RaiseHandRequest body) {
        JilaliResponses.unwrap(client.raiseHand(body));
    }

    public void stageKick(com.jilali.stage.dto.KickRequest body) {
        JilaliResponses.unwrap(client.stageKick(body));
    }

    public void raiseHandApproval(com.jilali.stage.dto.RaiseHandApprovalRequest body) {
        JilaliResponses.unwrap(client.raiseHandApproval(body));
    }

    public void stageInvite(com.jilali.stage.dto.StageInviteRequest body) {
        JilaliResponses.unwrap(client.stageInvite(body));
    }

    public void stageInviteApproval(com.jilali.stage.dto.StageInviteApprovalRequest body) {
        JilaliResponses.unwrap(client.stageInviteApproval(body));
    }

    public void deviceControl(com.jilali.stage.dto.DeviceControlRequest body) {
        JilaliResponses.unwrap(client.deviceControl(body));
    }

    public com.jilali.stage.dto.PublisherTokenResponse publisherRtcToken(String cname) {
        return JilaliResponses.unwrap(client.publisherRtcToken(cname));
    }

    // ---- Manager ----

    public com.jilali.manager.dto.ManagerListResponse managerList(String cname, long hostId) {
        return JilaliResponses.unwrap(client.managerList(cname, hostId));
    }

    public void setManagers(com.jilali.manager.dto.SetManagerRequest body) {
        JilaliResponses.unwrap(client.setManagers(body));
    }

    public void approveManager(com.jilali.manager.dto.ApproveManagerRequest body) {
        JilaliResponses.unwrap(client.approveManager(body));
    }

    public com.jilali.manager.dto.ManagerJudgeResponse managerJudge(String cname, long hostId) {
        return JilaliResponses.unwrap(client.managerJudge(cname, hostId));
    }

    // ---- Comments& Captions ----

    public com.jilali.comment.dto.CommentListResponse comments(int busiType, String cname) {
        return JilaliResponses.unwrap(client.comments(busiType, cname));
    }

    public com.jilali.comment.dto.CommentNotifyResponse commentNotify(int busiType) {
        return JilaliResponses.unwrap(client.commentNotify(busiType));
    }

    public com.jilali.comment.dto.CaptionHistoryResponse captionHistory(int busiType, String cname, int pageSize) {
        return JilaliResponses.unwrap(client.captionHistory(busiType, cname, pageSize));
    }

    public void captionSwitch(com.jilali.comment.dto.CaptionSwitchRequest body) {
        JilaliResponses.unwrap(client.captionSwitch(body));
    }

    // ---- Sign-in & Tasks ----

    public VoiceSignPanelResponse voiceSignPanel(String cname) {
        return JilaliResponses.unwrap(client.voiceSignPanel(cname));
    }

    public Object voiceTasks() {
        return JilaliResponses.unwrap(client.voiceTasks());
    }

    public RoomLevelRewardResponse roomLevelReward(String cname, long hostId, int level) {
        return JilaliResponses.unwrap(client.roomLevelReward(cname, hostId, level));
    }

    public void claimRoomLevelReward(ClaimRewardRequest body) {
        JilaliResponses.unwrap(client.claimRoomLevelReward(body));
    }

    // ---- User status & profile ----

    public com.jilali.user.dto.UserStatus userStatus(long userId) {
        return JilaliResponses.unwrap(client.userStatus(userId));
    }

    public com.jilali.user.dto.HostStatus hostStatus() {
        return JilaliResponses.unwrap(client.hostStatus());
    }

    public byte[] userProfile(int busiType, String cname, long userId) {
        return client.userProfile(busiType, cname, userId);
    }

    /**
     * Fetches HelloTalk user info via the encrypted ht/encbin endpoint.
     * Uses a direct HTTP call to set per-request ht/encbin headers correctly.
     *
     * @param userId the HelloTalk user ID to look up
     * @return clean UserInfo record
     */
    public UserInfo userInfo(long userId) {
        var session = Curve25519SessionGenerator.generate();
        var request = UserInfoRequest.forUser(userId);
        byte[] encryptedPayload = EncbinUtil.encrypt(request, session.sharedSecret());

        String token = properties.defaultAuthToken();
        String fixDid = "4050e6f3ca1a3029ba21a01f4e3f13ba";

        HttpRequest<byte[]> httpRequest = HttpRequest.POST(
                "/profile/v2/userinfo", encryptedPayload)
            .header("ht-content-type", "ht/encbin")
            .header("Content-Type", "application/octet-stream")
            .header("Accept", "*/*")
            .header("Accept-Charset", "UTF-8,*;q=0.5")
            .header("Accept-Encoding", "gzip")
            .header("Accept-Language", "en-MA;q=1.0, fr-MA;q=0.9, ar-MA;q=0.8")
            .header("Cache-Control", "no-cache")
            .header("User-Agent", "android;6.1.0;SM-A908N;11;" + userId)
            .header("x-ht-version", "6.1.0")
            .header("x-ht-timezone", ".00")
            .header("x-ht-tzid", "Africa/Casablanca")
            .header("x-ht-ui-mode", "1")
            .header("x-ht-channel", "google")
            .header("x-ht-device", "SM-A908N#720X1280#360#360#320#20.4")
            .header("x-ht-os-version", "11")
            .header("x-ht-os", "android")
            .header("x-ht-lang", "English")
            .header("x-ht-uid", String.valueOf(userId))
            .header("x-ht-did", fixDid)
            .header("x-ht-build", "135")
            .header("x-ht-token", "Bearer " + token)
            .header("Authorization", "Bearer " + token)
            .header("x-ht-pub", session.headerValue());

        BlockingHttpClient blockingClient = httpClient.toBlocking();
        byte[] responseBytes;
        try {
            responseBytes = blockingClient.retrieve(httpRequest, byte[].class);
        } catch (HttpClientResponseException e) {
            log.error("userInfo upstream error: status={}", e.getStatus());
            throw new JilaliException(1, "Upstream userinfo failed: " + e.getStatus(), HttpStatus.BAD_GATEWAY);
        }
        if (responseBytes == null || responseBytes.length == 0) {
            throw new JilaliException(1, "Empty userinfo response", HttpStatus.BAD_GATEWAY);
        }
        UserInfoResponse raw = EncbinUtil.decrypt(responseBytes, session.sharedSecret(), UserInfoResponse.class);
        return raw.toUserInfo();
    }
}
