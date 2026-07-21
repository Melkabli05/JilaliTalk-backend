package com.jilali.roomcontext.application.command.user;

import com.jilali.roomcontext.domain.valueobject.RoomUserId;

public final class UserCommands {
    private UserCommands() {}

    public record FollowUserCommand(RoomUserId actor, RoomUserId target) {}
    public record UnfollowUserCommand(RoomUserId actor, RoomUserId target) {}
    public record RecordProfileVisitCommand(RoomUserId visitor, RoomUserId visited) {}
    public record EditProfileCommand(RoomUserId userId, String nickname, String avatarUrl) {}
}
