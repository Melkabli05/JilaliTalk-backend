package com.jilali.roomcontext.infrastructure.client;

import com.jilali.core.JilaliEnvelope;
import com.jilali.roomcontext.infrastructure.dto.vip.ReceiveFriendSentCardRequest;
import com.jilali.roomcontext.infrastructure.dto.vip.UseVipExperienceCardRequest;
import com.jilali.roomcontext.infrastructure.dto.vip.VipExperienceCardRecordsRequest;
import com.jilali.roomcontext.infrastructure.dto.vip.VipExperienceCardRecordsResponse;
import com.jilali.roomcontext.infrastructure.dto.vip.VipFeatureRightRequest;
import com.jilali.roomcontext.infrastructure.dto.vip.VipFeatureRightResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.client.annotation.Client;

/** Dedicated VIP-experience-card upstream client for this bounded context - calls HelloTalk's
 *  {@code member_privilege_center/v3/vip_experience_card/*} family directly. Shares the
 *  {@code jlhub} HTTP client id (same base URL/auth/pool config as every other dedicated client
 *  in this package - see docs/room-redesign/06-package-dependency-analysis.md) but is its own
 *  independent @Client interface with zero dependency on the legacy client.JilaliClient god
 *  interface or client.VipExperienceCardClient. */
@Client(id = "jlhub", path = "/member_privilege_center/v3/vip_experience_card")
public interface VipJilaliClient {

    @Post("/query_user_feature_right")
    JilaliEnvelope<VipFeatureRightResponse> queryUserFeatureRight(@Body VipFeatureRightRequest body);

    @Post("/query_user_record")
    JilaliEnvelope<VipExperienceCardRecordsResponse> queryUserRecord(@Body VipExperienceCardRecordsRequest body);

    @Post("/user_use_card")
    JilaliEnvelope<Object> useCard(@Body UseVipExperienceCardRequest body);

    @Post("/receive_friend_sent_card")
    JilaliEnvelope<Object> receiveFriendSentCard(@Body ReceiveFriendSentCardRequest body);
}
