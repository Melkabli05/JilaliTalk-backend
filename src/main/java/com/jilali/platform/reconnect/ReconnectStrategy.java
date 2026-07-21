package com.jilali.platform.reconnect;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Capped exponential backoff with full jitter: {@code delay = random(0, min(cap, base * 2^attempt))}.
 * Full jitter is the standard defense against reconnect storms against a recovering upstream.
 * Stateful only in the attempt counter; call {@link #reset()} after a successful reconnect.
 *
 * <p>Promoted from {@code com.jilali.core.ws.ExponentialBackoff} as part of the refactor into
 * the target {@code com.jilali.platform} structure. This is the first record-shaped value
 * in the new platform sub-package — it stays mutable (the {@code AtomicInteger} attempt
 * counter) because backoff is inherently stateful per connection, but the surface is a
 * compact 3-method value class.
 */
public final class ReconnectStrategy {

    private final Duration base;
    private final Duration cap;
    private final AtomicInteger attempt = new AtomicInteger(0);

    public ReconnectStrategy(Duration base, Duration cap) {
        this.base = base;
        this.cap = cap;
    }

    /**
     * Standard 1s→30s capped exponential with full jitter — the exact shape both
     * upstream WebSocket connectors have used since the inlining of the helper. Single
     * source of truth for the default policy.
     */
    public static ReconnectStrategy defaults() {
        return new ReconnectStrategy(Duration.ofSeconds(1), Duration.ofSeconds(30));
    }

    /** Upper bound of the jitter window for a given attempt count, before randomization. */
    static Duration boundFor(int attempt, Duration base, Duration cap) {
        long baseMillis = base.toMillis();
        long capMillis = cap.toMillis();
        long shifted = baseMillis;
        for (int i = 0; i < attempt && shifted < capMillis; i++) {
            shifted = Math.min(capMillis, shifted * 2);
        }
        return Duration.ofMillis(Math.min(capMillis, shifted));
    }

    /** Next delay, advancing the attempt counter — full jitter: {@code random(0, boundFor(attempt))}. */
    public Duration nextDelay() {
        Duration bound = boundFor(attempt.getAndIncrement(), base, cap);
        long jittered = ThreadLocalRandom.current().nextLong(0, bound.toMillis() + 1);
        return Duration.ofMillis(jittered);
    }

    /** Call after a successful reconnect so the next unrelated failure starts from {@code base}. */
    public void reset() {
        attempt.set(0);
    }
}
