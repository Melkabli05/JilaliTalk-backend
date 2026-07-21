package com.jilali.roomcontext.domain.policy;

import com.jilali.roomcontext.domain.valueobject.Cname;
import com.jilali.roomcontext.domain.valueobject.RoomUserId;

public interface ManagerAuthorizationPolicy {
    boolean canManage(RoomUserId actor, Cname cname);

    /** Permissive default — matches current legacy (unchecked) behavior. Authorization is out
     *  of scope per explicit user direction; this exists only as a future extension seam. */
    ManagerAuthorizationPolicy PERMISSIVE = (actor, cname) -> true;
}
