package com.jilali.roomcontext.application.port.out;

import com.jilali.vip.dto.ReceiveFriendSentCardRequest;
import com.jilali.vip.dto.UseVipExperienceCardRequest;
import com.jilali.vip.dto.VipExperienceCardRecordsRequest;
import com.jilali.vip.dto.VipExperienceCardRecordsResponse;
import com.jilali.vip.dto.VipFeatureRightRequest;
import com.jilali.vip.dto.VipFeatureRightResponse;

/** Mirrors the real HelloTalk upstream action set exactly (query feature-right, query records,
 *  use card, receive friend-sent card) - deliberately action-shaped rather than a generic
 *  CRUD-style repository, because that's what the actual upstream API is. Reuses the existing
 *  {@code com.jilali.vip.dto} wire types directly rather than declaring parallel duplicates -
 *  they already are the wire contract. */
public interface VipUpstreamPort {
    VipFeatureRightResponse queryFeatureRight(VipFeatureRightRequest request);
    VipExperienceCardRecordsResponse queryRecords(VipExperienceCardRecordsRequest request);
    void useCard(UseVipExperienceCardRequest request);
    void receiveFriendSentCard(ReceiveFriendSentCardRequest request);
    boolean claimTrial();
}
