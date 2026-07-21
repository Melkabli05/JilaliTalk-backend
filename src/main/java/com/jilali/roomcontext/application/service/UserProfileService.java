package com.jilali.roomcontext.application.service;

import com.jilali.roomcontext.application.command.user.UserCommands.FollowUserCommand;
import com.jilali.roomcontext.application.command.user.UserCommands.UnfollowUserCommand;
import com.jilali.roomcontext.application.port.in.UserProfileUseCases;
import com.jilali.roomcontext.application.port.out.UserProfileRepositoryPort;
import com.jilali.roomcontext.domain.exception.DomainRuleViolation;
import com.jilali.roomcontext.domain.model.UserProfile;
import com.jilali.roomcontext.domain.valueobject.RoomUserId;

import jakarta.inject.Singleton;

@Singleton
public class UserProfileService implements UserProfileUseCases {

    private final UserProfileRepositoryPort profiles;

    public UserProfileService(UserProfileRepositoryPort profiles) {
        this.profiles = profiles;
    }

    @Override
    public UserProfile getProfile(RoomUserId userId) {
        return profiles.findById(userId)
            .orElseThrow(() -> new DomainRuleViolation("Profile " + userId + " not found"));
    }

    @Override
    public void follow(FollowUserCommand command) {
        UserProfile profile = getProfile(command.target());
        profile.follow();
        profiles.save(profile);
    }

    @Override
    public void unfollow(UnfollowUserCommand command) {
        UserProfile profile = getProfile(command.target());
        profile.unfollow();
        profiles.save(profile);
    }
}
