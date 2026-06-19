package com.jilali.realtime;

/** Pure exponential backoff calculator: 1s, 2s, 4s, 8s, 16s, then capped at 30s. */
public final class ReconnectBackoff {

    private static final long BASE_DELAY_MS = 1_000L;
    private static final long MAX_DELAY_MS = 30_000L;

    private ReconnectBackoff() {}

    /**
     * @param attempt the reconnect attempt number, starting at 0 for the first retry
     * @return delay in milliseconds before this attempt, capped at {@value #MAX_DELAY_MS}
     */
    public static long delayMillis(int attempt) {
        if (attempt < 0) throw new IllegalArgumentException("attempt must be >= 0, got " + attempt);
        long delay = BASE_DELAY_MS << Math.min(attempt, 20); // shift cap avoids overflow for huge attempt counts
        return Math.min(delay, MAX_DELAY_MS);
    }
}
