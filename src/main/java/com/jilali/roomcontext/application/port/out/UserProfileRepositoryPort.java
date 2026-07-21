package com.jilali.roomcontext.application.port.out;

import com.jilali.roomcontext.domain.model.UserProfile;
import com.jilali.roomcontext.domain.valueobject.RoomUserId;

import java.util.Optional;

public interface UserProfileRepositoryPort {
    Optional<UserProfile> findById(RoomUserId userId);
    void save(UserProfile profile);
}
