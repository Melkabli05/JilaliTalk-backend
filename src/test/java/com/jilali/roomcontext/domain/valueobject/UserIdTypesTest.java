package com.jilali.roomcontext.domain.valueobject;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Covers RoomUserId, HostId, ManagerId together — same validation shape, same rationale. */
class UserIdTypesTest {

    @Test
    void roomUserIdRejectsNonPositive() {
        assertThrows(IllegalArgumentException.class, () -> new RoomUserId(0));
        assertThrows(IllegalArgumentException.class, () -> new RoomUserId(-1));
    }

    @Test
    void hostIdRejectsNonPositive() {
        assertThrows(IllegalArgumentException.class, () -> new HostId(0));
        assertThrows(IllegalArgumentException.class, () -> new HostId(-1));
    }

    @Test
    void managerIdRejectsNonPositive() {
        assertThrows(IllegalArgumentException.class, () -> new ManagerId(0));
        assertThrows(IllegalArgumentException.class, () -> new ManagerId(-1));
    }

    @Test
    void hostIdConvertsToRoomUserId() {
        HostId hostId = new HostId(42);
        assertEquals(new RoomUserId(42), hostId.asRoomUserId());
    }

    @Test
    void managerIdConvertsToRoomUserId() {
        ManagerId managerId = new ManagerId(99);
        assertEquals(new RoomUserId(99), managerId.asRoomUserId());
    }

    @Test
    void distinctTypesAreNeverEqualEvenWithSameValue() {
        RoomUserId roomUserId = new RoomUserId(7);
        HostId hostId = new HostId(7);
        // Compile-time distinct types — this assertion just documents that .equals()
        // across types is false (records only equal same-type instances), reinforcing
        // why the type distinction prevents accidental argument-swap bugs.
        assertEquals(false, roomUserId.equals(hostId));
    }
}
