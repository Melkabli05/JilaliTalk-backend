package com.jilali.roomcontext.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jilali.core.JilaliEnvelope;
import com.jilali.roomcontext.infrastructure.dto.user.BatchStatusRequest;
import com.jilali.roomcontext.infrastructure.dto.user.BatchStatusResponse;
import com.jilali.roomcontext.infrastructure.dto.user.HeartbeatRequest;
import com.jilali.roomcontext.infrastructure.dto.user.HostStatus;
import com.jilali.roomcontext.infrastructure.dto.user.RoomUserListRequest;
import com.jilali.roomcontext.infrastructure.dto.user.RoomUserListResponse;
import com.jilali.roomcontext.infrastructure.dto.user.UserStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.serde.annotation.Serdeable;

import java.util.Map;

/** Dedicated User room-action/status upstream client - calls HelloTalk's {@code /livehub/user/*}
 *  endpoints directly. Zero dependency on the legacy client.JilaliClient god interface. */
@Client(id = "jlhub", path = "/livehub")
public interface UserJilaliClient {

    @Post("/user/join")
    JilaliEnvelope<Object> joinRoom(@Body JoinQuitRequest body);

    @Post("/user/quit")
    JilaliEnvelope<Object> quitRoom(@Body JoinQuitRequest body);

    @Post("/user/heartbeat")
    JilaliEnvelope<Object> heartbeat(@Body HeartbeatRequest body);

    @Post("/user/list")
    JilaliEnvelope<RoomUserListResponse> roomUserList(@Body RoomUserListRequest body);

    @Post("/user/status_list")
    JilaliEnvelope<BatchStatusResponse> batchUserStatus(@Body BatchStatusRequest body);

    @Get("/user_end_page/host")
    JilaliEnvelope<Map<String, Object>> userEndPageHost(@QueryValue("busi_type") int busiType,
                                                         @QueryValue String cname,
                                                         @QueryValue("contribute_list_type") String contributeListType);

    @Get("/user_end_page/audience")
    JilaliEnvelope<Map<String, Object>> userEndPageAudience(@QueryValue("busi_type") int busiType, @QueryValue String cname);

    @Get("/user_record_live")
    JilaliEnvelope<Map<String, Object>> userRecordLive(@QueryValue int limit, @QueryValue int offset);

    @Get("/user/status")
    JilaliEnvelope<UserStatus> userStatus(@QueryValue("user_id") long userId);

    @Get("/host_status")
    JilaliEnvelope<HostStatus> hostStatus();

    @Get(value = "/user/profile", processes = MediaType.APPLICATION_OCTET_STREAM)
    byte[] userProfile(@QueryValue("busi_type") int busiType, @QueryValue String cname, @QueryValue("user_id") long userId);

    @Serdeable
    record JoinQuitRequest(String cname, @JsonProperty("busi_type") int busiType) {}
}
