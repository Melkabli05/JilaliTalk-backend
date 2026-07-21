package com.jilali.roomcontext.domain.model;

import com.jilali.roomcontext.domain.exception.DomainRuleViolation;
import com.jilali.roomcontext.domain.valueobject.RoomUserId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class VipExperienceCard {

    public enum CardState { UNCLAIMED, CLAIMED, USED }

    public record CardFeature(String featureId, String cardType, String name) {}
    public record UsedFeature(String featureId, long usedAtMillis) {}

    private final String id;
    private RoomUserId owner;
    private CardState state;
    private final List<CardFeature> features;
    private final Map<String, UsedFeature> usedFeatures = new LinkedHashMap<>();

    public VipExperienceCard(String id, RoomUserId owner, List<CardFeature> features) {
        this.id = id;
        this.owner = owner;
        this.state = CardState.UNCLAIMED;
        this.features = List.copyOf(features);
    }

    public String id() { return id; }
    public RoomUserId owner() { return owner; }
    public CardState state() { return state; }
    public List<CardFeature> features() { return features; }
    public Map<String, UsedFeature> usedFeatures() { return Map.copyOf(usedFeatures); }

    public void claim() {
        if (state != CardState.UNCLAIMED) {
            throw new DomainRuleViolation("Card " + id + " is not claimable (state=" + state + ")");
        }
        state = CardState.CLAIMED;
    }

    public UsedFeature use(String featureId) {
        if (state != CardState.CLAIMED) {
            throw new DomainRuleViolation("Card " + id + " cannot be used (state=" + state + ")");
        }
        boolean hasFeature = features.stream().anyMatch(f -> f.featureId().equals(featureId));
        if (!hasFeature) {
            throw new DomainRuleViolation("Card " + id + " has no feature " + featureId);
        }
        UsedFeature used = new UsedFeature(featureId, System.currentTimeMillis());
        usedFeatures.put(featureId, used);
        state = CardState.USED;
        return used;
    }

    public void receiveFromFriend(RoomUserId newOwner) {
        if (state != CardState.UNCLAIMED) {
            throw new DomainRuleViolation("Card " + id + " has already been claimed and cannot be re-gifted");
        }
        this.owner = newOwner;
    }
}
