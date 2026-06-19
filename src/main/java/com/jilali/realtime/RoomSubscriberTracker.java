package com.jilali.realtime;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-{@code cname} subscriber counting. Tells {@link RoomRealtimeRegistry} when a room's
 * upstream LiveHub connection should be created (first subscriber) or disposed (last
 * unsubscribe) — kept separate from the registry so this bookkeeping is unit-testable
 * without touching any network code.
 */
public class RoomSubscriberTracker {

    private final ConcurrentHashMap<String, AtomicInteger> counts = new ConcurrentHashMap<>();

    /** @return true if this is the first subscriber for {@code cname} */
    public boolean subscribe(String cname) {
        AtomicInteger count = counts.computeIfAbsent(cname, k -> new AtomicInteger(0));
        return count.incrementAndGet() == 1;
    }

    /** @return true if this was the last subscriber for {@code cname} */
    public boolean unsubscribe(String cname) {
        AtomicInteger count = counts.get(cname);
        if (count == null) return false;
        int remaining = count.decrementAndGet();
        if (remaining <= 0) {
            counts.remove(cname, count);
            return true;
        }
        return false;
    }

    public int subscriberCount(String cname) {
        AtomicInteger count = counts.get(cname);
        return count != null ? Math.max(count.get(), 0) : 0;
    }
}