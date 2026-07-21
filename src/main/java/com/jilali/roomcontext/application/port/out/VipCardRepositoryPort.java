package com.jilali.roomcontext.application.port.out;

import com.jilali.roomcontext.domain.model.VipExperienceCard;
import com.jilali.roomcontext.domain.valueobject.RoomUserId;

import java.util.List;
import java.util.Optional;

public interface VipCardRepositoryPort {
    Optional<VipExperienceCard> findById(String cardId);
    List<VipExperienceCard> findByOwner(RoomUserId owner);
    void save(VipExperienceCard card);
}
