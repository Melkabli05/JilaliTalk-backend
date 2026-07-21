package com.jilali.platform.websocket;

import com.jilali.platform.reconnect.ReconnectStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Single source of truth for the "maintain a long-lived WebSocket with auto-reconnect" state
 * machine. Both upstream-connector classes in this codebase
 * ({@code im.HtImUpstreamConnector} and {@code realtime.HtLiveHubUpstreamConnector})
 * previously each held their own copies of:
 *
 * <ul>
 *   <li>{@code volatile boolean intentionalClose} — "are we shutting down deliberately?"
 *   <li>{@code volatile boolean connected} — "are we currently connected?"
 *   <li>{@code reconnectInBackground()} — the reconnect loop with backoff
 *   <li>{@code close()} — the deliberate-disconnect path
 *   <li>the read-then-compare-then-act race in their WS listener
 * </ul>
 *
 * <p>Extracted in Refactor 6 as a strict behavior-preserving step toward the Phase 3
 * {@code UpstreamWebSocketConnector<TEvent>} base class. Each connector still owns its own
 * wire-specific bits (frame encode/decode, notify mapping, event dispatch) and continues to
 * call this lifecycle from the appropriate hooks. The lifecycle is *only* state + reconnect
 * — it does NOT own the WebSocket instance, the heartbeat, or any of the connect URL
 * negotiation; those stay in the connector.
 */
public final class WebSocketConnectionLifecycle {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConnectionLifecycle.class);

    private final String name;            // for log lines
    private final ReconnectStrategy backoff;

    private volatile boolean intentionalClose;

    public WebSocketConnectionLifecycle(String name, ReconnectStrategy backoff) {
        this.name = name;
        this.backoff = backoff;
    }

    /** Open the connection (clear the close flag, reset backoff). */
    public void markOpening() {
        intentionalClose = false;
        backoff.reset();
    }

    /** Mark the connection as cleanly closed (no further reconnects). */
    public void markClosed() {
        intentionalClose = true;
    }

    /** Whether {@link #reconnectInBackground} should still be active. */
    public boolean shouldReconnect() {
        return !intentionalClose;
    }

    /**
     * Schedule a background reconnect with exponential backoff. Runs the supplied
     * {@code attempt} supplier on a delayed executor; if it completes cleanly, fine. If it
     * throws, reschedules (still respecting {@link #shouldReconnect}).
     *
     * <p>Bytes-identical to the previous inline implementations in both connectors.
     */
    public void reconnectInBackground(String logTag, Supplier<CompletableFuture<Void>> attempt) {
        if (intentionalClose) return;
        long delayMs = backoff.nextDelay().toMillis();
        log.info("{}: reconnecting in {}ms", logTag, delayMs);
        CompletableFuture.runAsync(() -> {
            if (intentionalClose) return;
            attempt.get().exceptionally(ex -> {
                log.warn("{}: reconnect attempt failed: {}", logTag, ex.getMessage());
                reconnectInBackground(logTag, attempt);
                return null;
            });
        }, CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS));
    }

    /** Reset the backoff (call after a successful reconnect). */
    public void resetBackoff() {
        backoff.reset();
    }

    public String name() { return name; }
}
