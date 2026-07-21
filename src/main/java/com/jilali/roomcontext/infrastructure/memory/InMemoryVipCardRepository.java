package com.jilali.roomcontext.infrastructure.memory;

import com.jilali.roomcontext.application.port.out.VipCardRepositoryPort;
import com.jilali.roomcontext.domain.model.VipExperienceCard;
import com.jilali.roomcontext.domain.valueobject.RoomUserId;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Singleton
public class InMemoryVipCardRepository implements VipCardRepositoryPort {

    private final Map<String, VipExperienceCard> store = new ConcurrentHashMap<>();

    @Override
    public Optional<VipExperienceCard> findById(String cardId) {
        return Optional.ofNullable(store.get(cardId));
    }

    @Override
    public List<VipExperienceCard> findByOwner(RoomUserId owner) {
        return store.values().stream()
            .filter(card -> card.owner().equals(owner))
            .collect(Collectors.toList());
    }

    @Override
    public void save(VipExperienceCard card) {
        store.put(card.id(), card);
    }
}
