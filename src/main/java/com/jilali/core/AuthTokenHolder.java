package com.jilali.core;

import jakarta.inject.Singleton;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Mutable, live-refreshable source of the single HelloTalk JWT this backend authenticates
 * every upstream call with. Seeded from {@link JilaliProperties#defaultAuthToken()} at
 * startup; refreshed in place by {@code HtImUpstreamConnector} when the upstream reports a
 * status-105 ("logged in on another device") session mismatch and {@code jilali.hellotalk-email}
 * / {@code jilali.hellotalk-password} are configured for auto-relogin.
 *
 * <p>Every caller that needs the current auth token — REST client filters, per-request uid
 * derivation, the IM WebSocket connector — reads {@link #get()} live rather than capturing
 * {@code JilaliProperties.defaultAuthToken()} once in a constructor field, which is what this
 * class replaces. Without this, a relogin would mint a fresh JWT that nothing in the app ever
 * picks up, since every consumer would still be holding the stale value from process start.
 */
@Singleton
public final class AuthTokenHolder {

    private final AtomicReference<String> token;

    public AuthTokenHolder(JilaliProperties properties) {
        this.token = new AtomicReference<>(properties.defaultAuthToken());
    }

    public String get() {
        return token.get();
    }

    public void set(String newToken) {
        token.set(newToken);
    }
}
