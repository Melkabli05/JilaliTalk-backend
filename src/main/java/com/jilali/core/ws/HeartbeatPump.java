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
