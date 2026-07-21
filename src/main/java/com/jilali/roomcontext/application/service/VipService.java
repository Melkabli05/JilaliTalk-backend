package com.jilali.roomcontext.application.service;

import com.jilali.roomcontext.application.command.vip.VipCommands.ClaimVipTrialCommand;
import com.jilali.roomcontext.application.command.vip.VipCommands.ReceiveFriendCardCommand;
import com.jilali.roomcontext.application.command.vip.VipCommands.UseVipCardCommand;
import com.jilali.roomcontext.application.port.in.VipUseCases;
import com.jilali.roomcontext.application.port.out.VipCardRepositoryPort;
import com.jilali.roomcontext.domain.exception.DomainRuleViolation;
import com.jilali.roomcontext.domain.model.VipExperienceCard;
import com.jilali.roomcontext.domain.model.VipExperienceCard.UsedFeature;
import com.jilali.roomcontext.domain.valueobject.RoomUserId;

import jakarta.inject.Singleton;

import java.util.List;
import java.util.UUID;

@Singleton
public class VipService implements VipUseCases {

    private final VipCardRepositoryPort cards;

    public VipService(VipCardRepositoryPort cards) {
        this.cards = cards;
    }

    @Override
    public List<VipExperienceCard> listCards(RoomUserId owner) {
        return cards.findByOwner(owner);
    }

    @Override
    public VipExperienceCard claimTrial(ClaimVipTrialCommand command) {
        VipExperienceCard card = new VipExperienceCard(UUID.randomUUID().toString(), command.userId(), List.of());
        card.claim();
        cards.save(card);
        return card;
    }

    @Override
    public UsedFeature useCard(UseVipCardCommand command) {
        VipExperienceCard card = cards.findById(command.cardId())
            .orElseThrow(() -> new DomainRuleViolation("Card " + command.cardId() + " not found"));
        UsedFeature used = card.use(command.featureId());
        cards.save(card);
        return used;
    }

    @Override
    public void receiveFriendCard(ReceiveFriendCardCommand command) {
        VipExperienceCard card = cards.findById(command.cardId())
            .orElseThrow(() -> new DomainRuleViolation("Card " + command.cardId() + " not found"));
        card.receiveFromFriend(command.receiver());
        cards.save(card);
    }
}
