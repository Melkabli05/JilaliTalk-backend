package com.jilali.roomcontext.infrastructure.client;

import com.jilali.core.JilaliEnvelope;
import com.jilali.roomcontext.infrastructure.dto.stage.DeviceControlRequest;
import com.jilali.roomcontext.infrastructure.dto.stage.KickRequest;
import com.jilali.roomcontext.infrastructure.dto.stage.PublisherTokenResponse;
import com.jilali.roomcontext.infrastructure.dto.stage.RaiseHandApprovalRequest;
import com.jilali.roomcontext.infrastructure.dto.stage.RaiseHandRequest;
import com.jilali.roomcontext.infrastructure.dto.stage.StageActionRequest;
import com.jilali.roomcontext.infrastructure.dto.stage.StageInviteApprovalRequest;
import com.jilali.roomcontext.infrastructure.dto.stage.StageInviteRequest;
import com.jilali.roomcontext.infrastructure.dto.stage.StageListResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.client.annotation.Client;

/** Dedicated Stage upstream client - calls HelloTalk's {@code /livehub/stage/*} and
 *  {@code /livehub/publisher_rtc_token} endpoints directly. Zero dependency on the legacy
 *  client.JilaliClient god interface. */
@Client(id = "jlhub", path = "/livehub")
public interface StageJilaliClient {

    @Get("/stage/list")
    JilaliEnvelope<StageListResponse> stageList(@QueryValue("busi_type") int busiType, @QueryValue String cname);

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
}
