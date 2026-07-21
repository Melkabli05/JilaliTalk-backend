package com.jilali.roomcontext.application.port.in;

import com.jilali.roomcontext.application.command.vip.VipCommands.ClaimVipTrialCommand;
import com.jilali.roomcontext.application.command.vip.VipCommands.ReceiveFriendCardCommand;
import com.jilali.roomcontext.application.command.vip.VipCommands.UseVipCardCommand;
import com.jilali.roomcontext.domain.model.VipExperienceCard;
import com.jilali.roomcontext.domain.model.VipExperienceCard.UsedFeature;
import com.jilali.roomcontext.domain.valueobject.RoomUserId;

import java.util.List;

public interface VipUseCases {
    List<VipExperienceCard> listCards(RoomUserId owner);
    VipExperienceCard claimTrial(ClaimVipTrialCommand command);
    UsedFeature useCard(UseVipCardCommand command);
    void receiveFriendCard(ReceiveFriendCardCommand command);
}
