package com.jilali.platform.time;

import java.time.Instant;

/**
 * Single helper for the seconds-to-milliseconds conversion that's repeated 10+ times across
 * the upstream mappers (HelloTalk's wire shape carries Unix seconds; the BFF and the
 * Angular frontend work in milliseconds). Centralizes the magic number and the zero-fallback
 * behavior.
 */
public final class Seconds {

    private Seconds() {}

    /** Rescales a Unix-seconds value to epoch milliseconds; {@code 0} stays {@code 0}. */
    public static long toMillis(long unixSeconds) {
        return unixSeconds * 1000L;
    }

    /** Returns the epoch milliseconds for "now" in a single call site — useful for fields
     *  the wire shape may or may not carry. */
    public static long nowMillis() {
        return Instant.now().toEpochMilli();
    }
}
