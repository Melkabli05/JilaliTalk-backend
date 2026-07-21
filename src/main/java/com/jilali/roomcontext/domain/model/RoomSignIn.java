package com.jilali.roomcontext.domain.model;

import com.jilali.platform.models.RewardItem;
import com.jilali.roomcontext.domain.exception.DomainRuleViolation;
import com.jilali.roomcontext.domain.valueobject.Cname;
import com.jilali.roomcontext.domain.valueobject.RoomUserId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RoomSignIn {

    public record SignInDay(int day, boolean claimed, RewardItem reward) {}
    public record PendingReward(String rewardId, RewardItem reward) {}
    public record ClaimedReward(String rewardId, RewardItem reward) {}

    private final RoomUserId userId;
    private final Cname cname;
    private final List<SignInDay> calendar;
    private final Map<String, PendingReward> claimable = new LinkedHashMap<>();
    private final Map<String, ClaimedReward> claimed = new LinkedHashMap<>();

    public RoomSignIn(RoomUserId userId, Cname cname, List<SignInDay> calendar, List<PendingReward> initiallyClaimable) {
        this.userId = userId;
        this.cname = cname;
        this.calendar = List.copyOf(calendar);
        for (PendingReward reward : initiallyClaimable) {
            claimable.put(reward.rewardId(), reward);
        }
    }

    public RoomUserId userId() { return userId; }
    public Cname cname() { return cname; }
    public List<SignInDay> calendar() { return calendar; }
    public Map<String, PendingReward> claimable() { return Map.copyOf(claimable); }

    public ClaimedReward claim(String rewardId) {
        PendingReward pending = claimable.remove(rewardId);
        if (pending == null) {
            throw new DomainRuleViolation("Reward " + rewardId + " is not claimable for user " + userId);
        }
        ClaimedReward result = new ClaimedReward(pending.rewardId(), pending.reward());
        claimed.put(rewardId, result);
        return result;
    }

    public ClaimedReward claimTask(String taskId) {
        return claim(taskId);
    }
}
