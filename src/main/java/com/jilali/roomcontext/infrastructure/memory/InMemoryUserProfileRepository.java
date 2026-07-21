package com.jilali.roomcontext.infrastructure.memory;

import com.jilali.roomcontext.application.port.out.UserProfileRepositoryPort;
import com.jilali.roomcontext.domain.model.UserProfile;
import com.jilali.roomcontext.domain.valueobject.RoomUserId;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class InMemoryUserProfileRepository implements UserProfileRepositoryPort {

    private final Map<RoomUserId, UserProfile> store = new ConcurrentHashMap<>();

    @Override
    public Optional<UserProfile> findById(RoomUserId userId) {
        return Optional.ofNullable(store.get(userId));
    }

    @Override
    public void save(UserProfile profile) {
        store.put(profile.userId(), profile);
    }
}
