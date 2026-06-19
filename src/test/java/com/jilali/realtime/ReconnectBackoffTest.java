package com.jilali.realtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ReconnectBackoffTest {

    @Test
    void delayDoublesEachAttemptUntilTheCap() {
        assertEquals(1_000L, ReconnectBackoff.delayMillis(0));
        assertEquals(2_000L, ReconnectBackoff.delayMillis(1));
        assertEquals(4_000L, ReconnectBackoff.delayMillis(2));
        assertEquals(8_000L, ReconnectBackoff.delayMillis(3));
        assertEquals(16_000L, ReconnectBackoff.delayMillis(4));
    }

    @Test
    void delayIsCappedAt30Seconds() {
        assertEquals(30_000L, ReconnectBackoff.delayMillis(5));
        assertEquals(30_000L, ReconnectBackoff.delayMillis(100));
    }

    @Test
    void negativeAttemptIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ReconnectBackoff.delayMillis(-1));
    }
}
