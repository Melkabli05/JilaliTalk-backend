# Task 1 Report: Shared WebSocket Infrastructure (`com.jilali.core.ws`)

## Status: DONE

## What was implemented

Created three shared utilities in `com.jilali.core.ws`:

1. **ExponentialBackoff.java** - Capped exponential backoff with full jitter. Uses `ThreadLocalRandom` for jitter, `AtomicInteger` for thread-safe attempt counting. Includes `boundFor()` static method for calculating the upper bound before randomization.

2. **SequentialSender.java** - Serializes async WebSocket sends using a CompletableFuture chain pattern. Ensures concurrent callers never interleave partial writes.

3. **HeartbeatPump.java** - Virtual-thread-based scheduled executor for periodic heartbeat pings. Implements `AutoCloseable`, cancels any previously-scheduled ping before scheduling a new one.

4. **ExponentialBackoffTest.java** - Unit test covering:
   - `boundDoublesEachAttemptUntilCapped()` - verifies doubling behavior up to cap
   - `nextDelayNeverExceedsTheBoundForItsAttempt()` - verifies jitter upper bound
   - `resetReturnsToBaseAttempt()` - verifies reset behavior

## Test Results

### ExponentialBackoffTest (task-specific):
```
./gradlew test --tests "com.jilali.core.ws.ExponentialBackoffTest"

BUILD SUCCESSFUL in 10s
6 actionable tasks: 3 executed, 3 up-to-date
```

### Full test suite:
```
./gradlew test

BUILD SUCCESSFUL in 4s
6 actionable tasks: 1 executed, 5 up-to-date
```

**3/3 ExponentialBackoffTest tests passing**

## Files Created
- `src/main/java/com/jilali/core/ws/ExponentialBackoff.java`
- `src/main/java/com/jilali/core/ws/SequentialSender.java`
- `src/main/java/com/jilali/core/ws/HeartbeatPump.java`
- `src/test/java/com/jilali/core/ws/ExponentialBackoffTest.java`

## Commit SHA
`6b87cf8`

## Concerns
None.
