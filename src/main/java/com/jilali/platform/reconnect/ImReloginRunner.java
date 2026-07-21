package com.jilali.platform.reconnect;

import com.jilali.auth.HelloTalkAuthClient;
import com.jilali.auth.dto.upstream.LoginResponse;
import com.jilali.core.JilaliProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.retry.annotation.Retryable;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Singleton bean that performs the IM-channel auto-relogin (called when the upstream reports
 * status 105 / "logged in on another device", {@code scriptv2.js:4595}'s "logged in on
 * another device" toast). Decoupled from {@code HtImUpstreamConnector} so the retry policy is
 * configurable in one place via Micronaut's {@code @Retryable} rather than a bare
 * {@code CompletableFuture} block tangled into the connector's volatile state machine.
 *
 * <p>Only loaded when {@code jilali.hellotalk-email} and {@code jilali.hellotalk-password}
 * are configured (the {@code @Requires} predicates) — when credentials are absent, this bean
 * is never instantiated and {@code HtImUpstreamConnector} falls back to a clean disconnect on
 * status 105 (previous behavior, unchanged).
 *
 * <p>Replaces the connector's own inlined {@code authClient}/{@code hellotalkEmail}/
 * {@code hellotalkPassword} fields (three constructor params collapsed into this one
 * dependency) — the connector no longer needs to know how relogin is retried, just that it
 * either produces a fresh JWT or doesn't.
 */
@Singleton
@Requires(property = "jilali.hellotalk-email", notEquals = "")
@Requires(property = "jilali.hellotalk-password", notEquals = "")
public class ImReloginRunner {

    private static final Logger log = LoggerFactory.getLogger(ImReloginRunner.class);

    private final HelloTalkAuthClient authClient;
    private final String email;
    private final String password;

    public ImReloginRunner(HelloTalkAuthClient authClient, JilaliProperties properties) {
        this.authClient = authClient;
        this.email = properties.hellotalkEmail();
        this.password = properties.hellotalkPassword();
    }

    /**
     * Attempts to mint a fresh JWT for the configured account, retrying on transient
     * upstream failures with a short capped backoff. Returns the new JWT, or empty if the
     * upstream rejects the credentials outright (no point retrying a genuinely dead account).
     */
    @Retryable(attempts = "3", delay = "500ms", maxDelay = "2s", multiplier = "2.0",
            includes = { java.io.IOException.class, java.util.concurrent.TimeoutException.class,
                    io.micronaut.http.client.exceptions.HttpClientResponseException.class })
    public Optional<String> attemptRelogin(long userId) {
        log.info("IM: attempting relogin after session mismatch uid={}", userId);
        return authClient.login(email, password)
            .map(LoginResponse::userInfo)
            .filter(u -> u != null && u.jwt() != null && !u.jwt().isBlank())
            .map(u -> {
                log.info("IM: relogin succeeded uid={}", userId);
                return u.jwt();
            });
    }
}
