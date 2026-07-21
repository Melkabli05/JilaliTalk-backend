package com.jilali.roomcontext.infrastructure.client;

import com.jilali.roomcontext.application.port.out.UserUpstreamPort;
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
import jakarta.inject.Singleton;

import java.util.Map;

/** Dedicated User room-action/status upstream adapter - zero dependency on the legacy
 *  client.JilaliGateway/client.JilaliClient god interface. */
@Singleton
public class UserUpstreamAdapter implements UserUpstreamPort {

    private final UserJilaliClient client;
    private final UserProfileEncryptedClient encryptedClient;

    public UserUpstreamAdapter(UserJilaliClient client, UserProfileEncryptedClient encryptedClient) {
        this.client = client;
        this.encryptedClient = encryptedClient;
    }

    @Override
    public void joinRoom(String cname, int busiType) {
        JilaliResponses.unwrap(client.joinRoom(new UserJilaliClient.JoinQuitRequest(cname, busiType)));
    }

    @Override
    public void quitRoom(String cname, int busiType) {
        JilaliResponses.unwrap(client.quitRoom(new UserJilaliClient.JoinQuitRequest(cname, busiType)));
    }

    @Override
    public void heartbeat(HeartbeatRequest request) {
        JilaliResponses.unwrap(client.heartbeat(request));
    }

    @Override
    public RoomUserListResponse roomUsers(RoomUserListRequest request) {
        return JilaliResponses.unwrap(client.roomUserList(request));
    }

    @Override
    public BatchStatusResponse batchStatus(BatchStatusRequest request) {
        return JilaliResponses.unwrap(client.batchUserStatus(request));
    }

    @Override
    public EnrichBatchResponse enrichBatch(EnrichBatchRequest request) {
        return new EnrichBatchResponse(
            request.userIds().stream()
                .map(encryptedClient::fetchUserInfo)
                .filter(u -> u != null)
                .toList());
    }

    @Override
    public Map<String, Object> endPageHost(int busiType, String cname, String contributeListType) {
        return JilaliResponses.unwrap(client.userEndPageHost(busiType, cname, contributeListType));
    }

    @Override
    public Map<String, Object> endPageAudience(int busiType, String cname) {
        return JilaliResponses.unwrap(client.userEndPageAudience(busiType, cname));
    }

    @Override
    public Map<String, Object> recordLive(int limit, int offset) {
        return JilaliResponses.unwrap(client.userRecordLive(limit, offset));
    }

    @Override
    public UserStatus status(long userId) {
        return JilaliResponses.unwrap(client.userStatus(userId));
    }

    @Override
    public HostStatus hostStatus() {
        return JilaliResponses.unwrap(client.hostStatus());
    }

    @Override
    public RoomUserProfileResponse profile(long userId, String cname, int busiType) {
        return encryptedClient.fetchRoomUserProfile(userId, cname, busiType);
    }

    @Override
    public UserInfo userInfo(long userId) {
        return encryptedClient.fetchUserInfo(userId);
    }
}
