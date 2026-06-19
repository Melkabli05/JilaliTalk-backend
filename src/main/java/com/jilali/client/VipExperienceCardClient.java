package com.jilali.client;

import com.jilali.core.JilaliEnvelope;
import com.jilali.vip.dto.ReceiveFriendSentCardRequest;
import com.jilali.vip.dto.UseVipExperienceCardRequest;
import com.jilali.vip.dto.VipExperienceCardRecordsRequest;
import com.jilali.vip.dto.VipExperienceCardRecordsResponse;
import com.jilali.vip.dto.VipFeatureRightRequest;
import com.jilali.vip.dto.VipFeatureRightResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.client.annotation.Client;

/**
 * The VIP experience-card family ({@code member_privilege_center/v3/vip_experience_card/*}) lives
 * under a different upstream path than the rest of Jilali's surface, which {@link JilaliClient}
 * fixes at {@code /livehub}. A {@code @Client} interface's {@code path} applies to every method in
 * it, so this can't be folded into {@link JilaliClient} without escaping that prefix per call.
 * <p>
 * It still shares the {@code jlhub} service id, so it reuses the same configured base URL,
 * timeouts and security settings, and the same header-propagation/default-header filters —
 * nothing about upstream configuration is duplicated, only the path namespace differs.
 */
@Client(id = "jlhub", path = "/member_privilege_center/v3/vip_experience_card")
public interface VipExperienceCardClient {

    @Post("/query_user_feature_right")
    JilaliEnvelope<VipFeatureRightResponse> queryUserFeatureRight(@Body VipFeatureRightRequest body);

    @Post("/query_user_record")
    JilaliEnvelope<VipExperienceCardRecordsResponse> queryUserRecord(@Body VipExperienceCardRecordsRequest body);

    @Post("/user_use_card")
    JilaliEnvelope<Object> useCard(@Body UseVipExperienceCardRequest body);

    @Post("/receive_friend_sent_card")
    JilaliEnvelope<Object> receiveFriendSentCard(@Body ReceiveFriendSentCardRequest body);
}
