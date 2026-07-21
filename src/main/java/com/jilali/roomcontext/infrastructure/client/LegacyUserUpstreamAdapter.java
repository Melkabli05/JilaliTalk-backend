package com.jilali.roomcontext.infrastructure.client;

import com.jilali.client.JilaliClient;
import com.jilali.client.JilaliGateway;
import com.jilali.client.JilaliResponses;
import com.jilali.roomcontext.application.port.out.UserUpstreamPort;
import com.jilali.user.dto.BatchStatusRequest;
import com.jilali.user.dto.BatchStatusResponse;
import com.jilali.user.dto.EnrichBatchRequest;
import com.jilali.user.dto.EnrichBatchResponse;
import com.jilali.user.dto.HeartbeatRequest;
import com.jilali.user.dto.HostStatus;
import com.jilali.user.dto.RoomUserListRequest;
import com.jilali.user.dto.RoomUserListResponse;
import com.jilali.user.dto.RoomUserProfileResponse;
import com.jilali.user.dto.UserInfo;
import com.jilali.user.dto.UserStatus;
import jakarta.inject.Singleton;

import java.util.Map;

@Singleton
public class LegacyUserUpstreamAdapter implements UserUpstreamPort {

    private final JilaliGateway gateway;

    public LegacyUserUpstreamAdapter(JilaliGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public void joinRoom(String cname, int busiType) {
        JilaliResponses.unwrap(gateway.client().joinRoom(new JilaliClient.JoinQuitRequest(cname, busiType)));
    }

    @Override
    public void quitRoom(String cname, int busiType) {
        JilaliResponses.unwrap(gateway.client().quitRoom(new JilaliClient.JoinQuitRequest(cname, busiType)));
    }

    @Override
    public void heartbeat(HeartbeatRequest request) {
        JilaliResponses.unwrap(gateway.client().heartbeat(request));
    }

    @Override
    public RoomUserListResponse roomUsers(RoomUserListRequest request) {
        return JilaliResponses.unwrap(gateway.client().roomUserList(request));
    }

    @Override
    public BatchStatusResponse batchStatus(BatchStatusRequest request) {
        return JilaliResponses.unwrap(gateway.client().batchUserStatus(request));
    }

    @Override
    public EnrichBatchResponse enrichBatch(EnrichBatchRequest request) {
        return new EnrichBatchResponse(
            request.userIds().stream()
                .map(gateway::userInfo)
                .filter(u -> u != null)
                .toList());
    }

    @Override
    public Map<String, Object> endPageHost(int busiType, String cname, String contributeListType) {
        return JilaliResponses.unwrap(gateway.client().userEndPageHost(busiType, cname, contributeListType));
    }

    @Override
    public Map<String, Object> endPageAudience(int busiType, String cname) {
        return JilaliResponses.unwrap(gateway.client().userEndPageAudience(busiType, cname));
    }

    @Override
    public Map<String, Object> recordLive(int limit, int offset) {
        return JilaliResponses.unwrap(gateway.client().userRecordLive(limit, offset));
    }

    @Override
    public UserStatus status(long userId) {
        return JilaliResponses.unwrap(gateway.client().userStatus(userId));
    }

    @Override
    public HostStatus hostStatus() {
        return JilaliResponses.unwrap(gateway.client().hostStatus());
    }

    @Override
    public RoomUserProfileResponse profile(long userId, String cname, int busiType) {
        return gateway.roomUserProfile(userId, cname, busiType);
    }

    @Override
    public UserInfo userInfo(long userId) {
        return gateway.userInfo(userId);
    }
}
