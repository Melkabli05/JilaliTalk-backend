package com.jilali.roomcontext.application.port.out;

import com.jilali.roomcontext.infrastructure.dto.vip.ReceiveFriendSentCardRequest;
import com.jilali.roomcontext.infrastructure.dto.vip.UseVipExperienceCardRequest;
import com.jilali.roomcontext.infrastructure.dto.vip.VipExperienceCardRecordsRequest;
import com.jilali.roomcontext.infrastructure.dto.vip.VipExperienceCardRecordsResponse;
import com.jilali.roomcontext.infrastructure.dto.vip.VipFeatureRightRequest;
import com.jilali.roomcontext.infrastructure.dto.vip.VipFeatureRightResponse;

/** Mirrors the real HelloTalk upstream action set exactly (query feature-right, query records,
 *  use card, receive friend-sent card) - deliberately action-shaped rather than a generic
 *  CRUD-style repository, because that's what the actual upstream API is. */
public interface VipUpstreamPort {
    VipFeatureRightResponse queryFeatureRight(VipFeatureRightRequest request);
    VipExperienceCardRecordsResponse queryRecords(VipExperienceCardRecordsRequest request);
    void useCard(UseVipExperienceCardRequest request);
    void receiveFriendSentCard(ReceiveFriendSentCardRequest request);
    boolean claimTrial();
}
