package com.jilali.core.ws;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Capped exponential backoff with full jitter: {@code delay = random(0, min(cap, base * 2^attempt))}.
 * Full jitter is the standard defense against reconnect storms against a recovering upstream.
 * Stateful only in the attempt counter; call {@link #reset()} after a successful reconnect.
 */
public final class ExponentialBackoff {

    private final Duration base;
    private final Duration cap;
    private final AtomicInteger attempt = new AtomicInteger(0);

    public ExponentialBackoff(Duration base, Duration cap) {
        this.base = base;
        this.cap = cap;
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
