# Task 1: Shared WebSocket infrastructure (`com.jilali.core.ws`)

## Context
This task creates three small shared utilities for the two upstream WebSocket connectors in `com.jilali.im` (IM channel) and `com.jilali.realtime` (LiveHub room channel). No previous tasks exist — start from scratch.

## Files to create
- `src/main/java/com/jilali/core/ws/ExponentialBackoff.java`
- `src/main/java/com/jilali/core/ws/SequentialSender.java`
- `src/main/java/com/jilali/core/ws/HeartbeatPump.java`
- `src/test/java/com/jilali/core/ws/ExponentialBackoffTest.java`

## Exact code to implement

### ExponentialBackoff.java
```java
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
```

### SequentialSender.java
```java
package com.jilali.core.ws;

import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Serializes async sends over a single WebSocket so concurrent callers never interleave
 * partial writes. WebSocket.sendText/sendBinary each return CompletableFuture[WebSocket];
 * chaining with handle+thenCompose ensures each send waits for the previous to finish.
 */
public final class SequentialSender {

    private volatile CompletableFuture<WebSocket> chain = CompletableFuture.completedFuture(null);

    /** Queue a send; runs only after every previously queued send has completed. */
    public synchronized void enqueue(Supplier<CompletableFuture<WebSocket>> sendOp, Consumer<Throwable> onError) {
        chain = chain
            .handle((r, t) -> null)
            .thenCompose(r -> sendOp.get())
            .exceptionally(e -> { onError.accept(e); return null; });
    }

    /** Reset the chain after a reconnect where in-flight sends are now moot. */
    public void reset() {
        chain = CompletableFuture.completedFuture(null);
    }
}
```

### HeartbeatPump.java
```java
package com.jilali.core.ws;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Owns a single virtual-thread scheduler for one periodic heartbeat.
 * {@link #start} cancels any previously-scheduled ping before scheduling the new one,
 * so a server-driven interval change never leaks the old schedule.
 */
public final class HeartbeatPump implements AutoCloseable {

    private final ScheduledExecutorService scheduler;
    private volatile ScheduledFuture<?> future;

    public HeartbeatPump(String threadName) {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().name(threadName).factory());
    }

    /** Start a fixed-rate ping, first firing after initialDelay, then every period. */
    public synchronized void start(Duration initialDelay, Duration period, Runnable pingAction) {
        cancelCurrent();
        long initSec = Math.max(0, initialDelay.toSeconds());
        long periodSec = Math.max(1, period.toSeconds());
        future = scheduler.scheduleAtFixedRate(pingAction, initSec, periodSec, TimeUnit.SECONDS);
    }

    /** Convenience: first ping fires after one full period. */
    public void start(Duration period, Runnable pingAction) {
        start(period, period, pingAction);
    }

    public synchronized void stop() {
        cancelCurrent();
    }

    private void cancelCurrent() {
        ScheduledFuture<?> f = future;
        if (f != null) { f.cancel(false); future = null; }
    }

    @Override
    public void close() {
        stop();
        scheduler.shutdownNow();
    }
}
```

### ExponentialBackoffTest.java (write this first, verify it fails, then write the class)
```java
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
```

## Test contract
Run `./gradlew test --tests "com.jilali.core.ws.ExponentialBackoffTest"` — all 3 tests must pass. Then run `./gradlew test` to confirm nothing else broke.

## Report contract
Write your full report to `/home/mohammed/Desktop/JilaliTalk/jilalibff/.superpowers/sdd/task-1-report.md` with:
- Status: DONE or DONE_WITH_CONCERNS or BLOCKED
- What you implemented
- Test results (command + output)
- Any concerns
- Commit SHA

Then return only: status, commit SHA, test summary (e.g. "3/3 tests passing"), and any concerns.
