package com.jilali.roomcontext.application.port.out;

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

import java.util.Map;

public interface UserUpstreamPort {
    void joinRoom(String cname, int busiType);
    void quitRoom(String cname, int busiType);
    void heartbeat(HeartbeatRequest request);
    RoomUserListResponse roomUsers(RoomUserListRequest request);
    BatchStatusResponse batchStatus(BatchStatusRequest request);
    EnrichBatchResponse enrichBatch(EnrichBatchRequest request);
    Map<String, Object> endPageHost(int busiType, String cname, String contributeListType);
    Map<String, Object> endPageAudience(int busiType, String cname);
    Map<String, Object> recordLive(int limit, int offset);
    UserStatus status(long userId);
    HostStatus hostStatus();
    RoomUserProfileResponse profile(long userId, String cname, int busiType);
    UserInfo userInfo(long userId);
}
