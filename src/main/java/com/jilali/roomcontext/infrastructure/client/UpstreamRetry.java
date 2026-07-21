package com.jilali.roomcontext.infrastructure.client;

import io.micronaut.http.client.exceptions.HttpClientResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;

/** Retries a call up to 4 times when upstream returns a 5xx (a just-created room's own read
 *  endpoints can lag briefly behind creation) - never retries a 4xx, which is a genuine error
 *  more attempts cannot fix. Shared by RoomUpstreamAdapter (single-call retries) and
 *  RoomJoinService (the same retry wrapping each fan-out subtask) - previously duplicated in
 *  both places. */
public final class UpstreamRetry {

    private static final Logger log = LoggerFactory.getLogger(UpstreamRetry.class);
    private static final int MAX_ATTEMPTS = 4;
    private static final long DELAY_MS = 700;

    private UpstreamRetry() {}

    public static <T> T withRetry(Callable<T> call) {
        for (int attempt = 1; ; attempt++) {
            try {
                return call.call();
            } catch (HttpClientResponseException e) {
                boolean serverError = e.getStatus().getCode() >= 500;
                String upstreamBody = e.getResponse() != null
                        ? e.getResponse().getBody(String.class).orElse("<empty>")
                        : "<no response>";
                if (!serverError || attempt >= MAX_ATTEMPTS) {
                    log.warn("Upstream call failed permanently (status={}): {}", e.getStatus(), upstreamBody);
                    throw e;
                }
                log.warn("Upstream call failed (status={}), retrying attempt {}/{}: {}",
                        e.getStatus(), attempt, MAX_ATTEMPTS - 1, upstreamBody);
                try {
                    Thread.sleep(DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during upstream retry", ie);
                }
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException("Upstream call failed", e);
            }
        }
    }
}
