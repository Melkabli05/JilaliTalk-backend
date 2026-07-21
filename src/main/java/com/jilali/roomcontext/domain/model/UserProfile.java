package com.jilali.roomcontext.domain.model;

import com.jilali.roomcontext.domain.valueobject.RoomUserId;

public final class UserProfile {

    public enum PresenceStatus { ONLINE, OFFLINE, IN_ROOM }
    public enum FollowState { NONE, FOLLOWING, FOLLOWED_BY, MUTUAL }
    public record ProfileSummary(String nickname, String avatarUrl, String nationality) {}

    private final RoomUserId userId;
    private ProfileSummary summary;
    private PresenceStatus presence;
    private FollowState followState;

    public UserProfile(RoomUserId userId, ProfileSummary summary, PresenceStatus presence, FollowState followState) {
        this.userId = userId;
        this.summary = summary;
        this.presence = presence;
        this.followState = followState;
    }

    public RoomUserId userId() { return userId; }
    public ProfileSummary summary() { return summary; }
    public PresenceStatus presence() { return presence; }
    public FollowState followState() { return followState; }

    public void follow() {
        followState = switch (followState) {
            case NONE -> FollowState.FOLLOWING;
            case FOLLOWED_BY -> FollowState.MUTUAL;
            case FOLLOWING, MUTUAL -> followState;
        };
    }

    public void unfollow() {
        followState = switch (followState) {
            case FOLLOWING -> FollowState.NONE;
            case MUTUAL -> FollowState.FOLLOWED_BY;
            case NONE, FOLLOWED_BY -> followState;
        };
    }
}
