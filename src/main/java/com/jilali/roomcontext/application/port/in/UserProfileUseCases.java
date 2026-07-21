package com.jilali.roomcontext.application.port.in;

import com.jilali.roomcontext.application.command.user.UserCommands.FollowUserCommand;
import com.jilali.roomcontext.application.command.user.UserCommands.UnfollowUserCommand;
import com.jilali.roomcontext.domain.model.UserProfile;
import com.jilali.roomcontext.domain.valueobject.RoomUserId;

public interface UserProfileUseCases {
    UserProfile getProfile(RoomUserId userId);
    void follow(FollowUserCommand command);
    void unfollow(UnfollowUserCommand command);
}
