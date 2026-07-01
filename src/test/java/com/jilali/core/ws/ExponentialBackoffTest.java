package com.jilali.core.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.time.Duration;

class ExponentialBackoffTest {

    private final Duration base = Duration.ofSeconds(1);
    private final Duration cap = Duration.ofSeconds(30);

    @Test
    void boundDoublesEachAttemptUntilCapped() {
        assertEquals(Duration.ofSeconds(1),  ExponentialBackoff.boundFor(0, base, cap));
        assertEquals(Duration.ofSeconds(2),  ExponentialBackoff.boundFor(1, base, cap));
        assertEquals(Duration.ofSeconds(4),  ExponentialBackoff.boundFor(2, base, cap));
        assertEquals(Duration.ofSeconds(8),  ExponentialBackoff.boundFor(3, base, cap));
        assertEquals(Duration.ofSeconds(16), ExponentialBackoff.boundFor(4, base, cap));
        assertEquals(Duration.ofSeconds(30), ExponentialBackoff.boundFor(5, base, cap)); // 32 capped to 30
        assertEquals(Duration.ofSeconds(30), ExponentialBackoff.boundFor(100, base, cap)); // stays capped
    }

    @Test
    void nextDelayNeverExceedsTheBoundForItsAttempt() {
        ExponentialBackoff backoff = new ExponentialBackoff(base, cap);
        for (int i = 0; i < 10; i++) {
            Duration expectedBound = ExponentialBackoff.boundFor(i, base, cap);
            Duration delay = backoff.nextDelay();
            assertTrue(delay.toMillis() >= 0, "delay must be non-negative");
            assertTrue(delay.toMillis() <= expectedBound.toMillis(),
                "delay " + delay + " exceeded bound " + expectedBound + " at attempt " + i);
        }
    }

    @Test
    void resetReturnsToBaseAttempt() {
        ExponentialBackoff backoff = new ExponentialBackoff(base, cap);
        backoff.nextDelay();
        backoff.nextDelay();
        backoff.nextDelay(); // attempt counter now at 3

        backoff.reset();

        Duration delay = backoff.nextDelay(); // back to attempt 0 -> bound 1s
        assertTrue(delay.toMillis() <= base.toMillis());
    }
}
