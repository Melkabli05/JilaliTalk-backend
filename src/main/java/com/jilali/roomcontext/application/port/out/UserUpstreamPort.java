package com.jilali.roomcontext.application.port.out;

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
