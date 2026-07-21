package com.jilali.roomcontext.domain.model;

import com.jilali.roomcontext.domain.event.RoomEvent;
import com.jilali.roomcontext.domain.exception.DomainRuleViolation;
import com.jilali.roomcontext.domain.policy.ManagerAuthorizationPolicy;
import com.jilali.roomcontext.domain.valueobject.BusiType;
import com.jilali.roomcontext.domain.valueobject.Cname;
import com.jilali.roomcontext.domain.valueobject.HostId;
import com.jilali.roomcontext.domain.valueobject.ManagerId;
import com.jilali.roomcontext.domain.valueobject.MicState;
import com.jilali.roomcontext.domain.valueobject.RoomLevel;
import com.jilali.roomcontext.domain.valueobject.RoomLifecycleState;
import com.jilali.roomcontext.domain.valueobject.RoomUserId;

import java.util.ArrayList;
import java.util.List;

public final class Room {

    private final Cname cname;
    private final HostId hostId;
    private final BusiType busiType;
    private RoomLifecycleState lifecycleState;
    private RoomLevel level;
    private final Stage stage;
    private final RoomRoster roster;
    private final ManagerRoster managers;
    private final ManagerAuthorizationPolicy authorizationPolicy;
    private final List<RoomEvent> pendingEvents = new ArrayList<>();

    public Room(Cname cname, HostId hostId, BusiType busiType, int stageCapacity, RoomLevel level) {
        this(cname, hostId, busiType, stageCapacity, level, ManagerAuthorizationPolicy.PERMISSIVE);
    }

    public Room(Cname cname, HostId hostId, BusiType busiType, int stageCapacity, RoomLevel level,
                ManagerAuthorizationPolicy authorizationPolicy) {
        this.cname = cname;
        this.hostId = hostId;
        this.busiType = busiType;
        this.lifecycleState = RoomLifecycleState.CREATED;
        this.level = level;
        this.stage = new Stage(stageCapacity);
        this.roster = new RoomRoster();
        this.managers = new ManagerRoster();
        this.authorizationPolicy = authorizationPolicy;
        this.roster.join(hostId.asRoomUserId());
        this.managers.grant(hostId.asRoomUserId());
    }

    public Cname cname() { return cname; }
    public HostId hostId() { return hostId; }
    public BusiType busiType() { return busiType; }
    public RoomLifecycleState lifecycleState() { return lifecycleState; }
    public RoomLevel level() { return level; }
    public Stage stage() { return stage; }
    public RoomRoster roster() { return roster; }
    public ManagerRoster managers() { return managers; }

    public List<RoomEvent> pullPendingEvents() {
        List<RoomEvent> events = List.copyOf(pendingEvents);
        pendingEvents.clear();
        return events;
    }

    public void join(RoomUserId userId) {
        requireLive();
        roster.join(userId);
        pendingEvents.add(new RoomEvent.MemberJoined(cname, userId));
    }

    public void leave(RoomUserId userId) {
        roster.leave(userId);
        stage.vacateSeat(userId);
        pendingEvents.add(new RoomEvent.MemberLeft(cname, userId));
    }

    public int assignStageSeat(RoomUserId userId) {
        requireLive();
        roster.require(userId);
        int seat = stage.assignSeat(userId);
        roster.require(userId).setMicState(new MicState.Listening());
        pendingEvents.add(new RoomEvent.StageSeatTaken(cname, userId, seat));
        return seat;
    }

    public void vacateStageSeat(RoomUserId userId) {
        stage.vacateSeat(userId);
        RoomMember member = roster.require(userId);
        member.setMicState(new MicState.Off());
        pendingEvents.add(new RoomEvent.StageSeatVacated(cname, userId));
    }

    public void queueRaiseHand(RoomUserId userId) {
        requireLive();
        roster.require(userId);
        stage.queueRaiseHand(userId);
        roster.require(userId).setMicState(new MicState.PendingApproval());
        pendingEvents.add(new RoomEvent.RaiseHandQueued(cname, userId));
    }

    public void approveRaiseHand(RoomUserId userId, ManagerId approver) {
        requireManager(approver);
        int seat = stage.approveRaiseHand(userId);
        roster.require(userId).setMicState(new MicState.Listening());
        pendingEvents.add(new RoomEvent.RaiseHandApproved(cname, userId, approver));
        pendingEvents.add(new RoomEvent.StageSeatTaken(cname, userId, seat));
    }

    public void kick(RoomUserId target, ManagerId actor) {
        requireManager(actor);
        stage.kick(target);
        roster.require(target).setMicState(new MicState.Off());
        pendingEvents.add(new RoomEvent.MemberKicked(cname, target, actor));
    }

    public void inviteToStage(RoomUserId target, ManagerId invitedBy) {
        requireManager(invitedBy);
        roster.require(target);
        stage.invite(target);
    }

    public void approveStageInvite(RoomUserId userId) {
        roster.require(userId);
        int seat = stage.approveInvite(userId);
        roster.require(userId).setMicState(new MicState.Listening());
        pendingEvents.add(new RoomEvent.StageSeatTaken(cname, userId, seat));
    }

    public void controlStageDevice(RoomUserId target, ManagerId actor, boolean camOn) {
        requireManager(actor);
        RoomMember member = roster.require(target);
        if (member.micState() instanceof MicState.Speaking) {
            member.setMicState(new MicState.Speaking(camOn));
        }
    }

    public void grantManager(RoomUserId target, HostId grantedBy) {
        requireHost(grantedBy);
        roster.require(target);
        managers.grant(target);
        pendingEvents.add(new RoomEvent.ManagerGranted(cname, target, grantedBy));
    }

    public void revokeManager(RoomUserId target, HostId revokedBy) {
        requireHost(revokedBy);
        managers.revoke(target);
        pendingEvents.add(new RoomEvent.ManagerRevoked(cname, target, revokedBy));
    }

    public void end(HostId endedBy) {
        requireHost(endedBy);
        if (lifecycleState == RoomLifecycleState.ENDED) {
            throw new DomainRuleViolation("Room " + cname + " has already ended");
        }
        lifecycleState = RoomLifecycleState.ENDED;
        pendingEvents.add(new RoomEvent.RoomEnded(cname, endedBy));
    }

    public void goLive() {
        if (lifecycleState != RoomLifecycleState.CREATED) {
            throw new DomainRuleViolation("Room " + cname + " is not in CREATED state");
        }
        lifecycleState = RoomLifecycleState.LIVE;
    }

    private void requireLive() {
        if (lifecycleState == RoomLifecycleState.ENDED) {
            throw new DomainRuleViolation("Room " + cname + " has ended");
        }
    }

    private void requireHost(HostId candidate) {
        if (!candidate.equals(hostId)) {
            throw new DomainRuleViolation("Only the host may perform this action on room " + cname);
        }
    }

    private void requireManager(ManagerId candidate) {
        if (!managers.isManager(candidate.asRoomUserId()) || !authorizationPolicy.canManage(candidate.asRoomUserId(), cname)) {
            throw new DomainRuleViolation("User " + candidate + " is not a manager of room " + cname);
        }
    }
}
