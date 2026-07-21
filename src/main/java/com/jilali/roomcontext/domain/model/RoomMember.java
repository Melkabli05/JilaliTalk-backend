package com.jilali.roomcontext.domain.model;

import com.jilali.roomcontext.domain.valueobject.MicState;
import com.jilali.roomcontext.domain.valueobject.RoomUserId;

public final class RoomMember {

    private final RoomUserId userId;
    private MicState micState;
    private boolean bannedFromComment;
    private boolean bannedFromMic;

    public RoomMember(RoomUserId userId) {
        this.userId = userId;
        this.micState = new MicState.Off();
    }

    public RoomUserId userId() {
        return userId;
    }

    public MicState micState() {
        return micState;
    }

    void setMicState(MicState micState) {
        this.micState = micState;
    }

    public boolean bannedFromComment() {
        return bannedFromComment;
    }

    public boolean bannedFromMic() {
        return bannedFromMic;
    }

    public void banFromComment() {
        this.bannedFromComment = true;
    }

    public void banFromMic() {
        this.bannedFromMic = true;
    }
}
