package com.jilali.realtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RoomSubscriberTrackerTest {

    private final RoomSubscriberTracker tracker = new RoomSubscriberTracker();

    @Test
    void firstSubscribeReturnsTrue() {
        assertTrue(tracker.subscribe("room-a"));
    }

    @Test
    void secondSubscribeToTheSameRoomReturnsFalse() {
        tracker.subscribe("room-a");
        assertFalse(tracker.subscribe("room-a"));
        assertEquals(2, tracker.subscriberCount("room-a"));
    }

    @Test
    void unsubscribeBeforeTheLastSubscriberReturnsFalse() {
        tracker.subscribe("room-a");
        tracker.subscribe("room-a");
        assertFalse(tracker.unsubscribe("room-a"));
        assertEquals(1, tracker.subscriberCount("room-a"));
    }

    @Test
    void lastUnsubscribeReturnsTrueAndResetsCount() {
        tracker.subscribe("room-a");
        assertTrue(tracker.unsubscribe("room-a"));
        assertEquals(0, tracker.subscriberCount("room-a"));
    }

    @Test
    void unsubscribingAnUnknownRoomReturnsFalse() {
        assertFalse(tracker.unsubscribe("never-subscribed"));
    }

    @Test
    void roomsAreTrackedIndependently() {
        tracker.subscribe("room-a");
        tracker.subscribe("room-b");
        tracker.subscribe("room-b");
        assertEquals(1, tracker.subscriberCount("room-a"));
        assertEquals(2, tracker.subscriberCount("room-b"));
    }

    @Test
    void resubscribingAfterFullyEmptyingARoomStartsFresh() {
        tracker.subscribe("room-a");
        tracker.unsubscribe("room-a");
        assertTrue(tracker.subscribe("room-a"), "a new subscriber after the room emptied out should be 'first' again");
    }
}