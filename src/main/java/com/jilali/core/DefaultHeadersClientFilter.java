package com.jilali.core;

import io.micronaut.core.order.Ordered;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.ClientFilter;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.context.ServerRequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Supplies sensible defaults for device-context headers on every outbound Jilali call.
 * Runs after {@link HeaderPropagationFilter} so that:
 * <ul>
 *   <li>Real auth headers (authorization, x-ht-token) take priority — they come from the frontend.</li>
 *   <li>Device-context defaults (x-ht-version, x-ht-os, etc.) are used when the frontend omits them.</li>
 *   <li>Tracing headers are always generated fresh per call to maintain distributed trace hygiene.</li>
 * </ul>
 * <p>
 * {@code x-ht-uid} is special: the upstream rejects requests that omit it ({@code err_param}),
 * but the Angular frontend doesn't know its caller uid without decoding the JWT, and adding JWT
 * decoding to every HttpInterceptor would couple it to the auth scheme. We derive it here from
 * the inbound {@code Authorization} header's JWT instead — the same trick
 * {@link com.jilali.client.JilaliGateway#currentUserId} uses for the encrypted {@code userInfo}
 * path, generalized via {@link JwtUtil#uidFromBearer}.
 */
@ClientFilter(serviceId = "jlhub")
public class DefaultHeadersClientFilter implements Ordered {

    private static final Logger log = LoggerFactory.getLogger(
        DefaultHeadersClientFilter.class
    );

    private final JilaliProperties properties;

    public DefaultHeadersClientFilter(JilaliProperties properties) {
        this.properties = properties;
    }

    @RequestFilter
    public void addDefaults(MutableHttpRequest<?> downstream) {
        var h = downstream.getHeaders();

        // User-Agent is not a jilali-propagated header, set a generic one.
        if (h.get("user-agent") == null) {
            downstream.header("User-Agent", "JilaliBff/1.0");
        }

        // If the frontend didn't send a known header, supply the default.
        for (var header : properties.forwardedHeaders()) {
            if (h.get(header) == null) {
                var defaultValue = defaultFor(header);
                if (defaultValue != null) {
                    downstream.header(header, defaultValue);
                }
            }
        }

        // Always generate fresh tracing — don't reuse inbound trace IDs.
        downstream.header("x-b3-sampled", "1");
        downstream.header("x-b3-traceid", "%032x".formatted(System.nanoTime()));
        downstream.header(
            "x-b3-spanid",
            "%016x".formatted(System.nanoTime() & 0xFFFF_FFFF_FFFFL)
        );
        downstream.header(
            "x-request-start",
            String.valueOf(System.currentTimeMillis())
        );
    }

    private String defaultFor(String header) {
        return switch (header) {
            case "authorization", "x-ht-token" -> "Bearer " +
                properties.defaultAuthToken();
            case "user-agent" -> "%s;%s;%s;%s;".formatted(
                "ios",
                "6.1.0",
                "iPhone",
                "18.5"
            );
            case "x-ht-version" -> "6.1.0";
            case "x-ht-os" -> "ios";
            case "x-ht-channel" -> "AppStore";
            case "x-ht-lang" -> "English";
            case "x-ht-ui-mode" -> "1";
            case "x-ht-timezone" -> "1.00";
            case "x-ht-tzid" -> "Africa/Casablanca";
            case "x-ht-device" -> "iPhone";
            case "x-ht-os-version" -> "18.5";
            case "x-ht-build" -> "135";
            case "x-ht-did" -> "373b9d3f80345ace3ed2679e78de84cafc7cd481";
            case "accept-language" -> "en-MA;q=1.0, fr-MA;q=0.9, ar-MA;q=0.8";
            // Derive the caller uid from the inbound JWT. The frontend never sends x-ht-uid,
            // and upstream rejects requests that omit it (err_param).
            case "x-ht-uid" -> deriveCallerUid();
            default -> null;
        };
    }

    /**
     * Reads the inbound {@code Authorization} header (set by the frontend or, in its absence,
     * the default token from {@link JilaliProperties}) and extracts the {@code uid} claim. Returns
     * {@code null} if no JWT is available or its payload has no uid — the filter then skips the
     * header, which is the same fail-soft behavior as the other defaults.
     */
    private String deriveCallerUid() {
        String inboundAuth = ServerRequestContext.currentRequest()
            .map(req -> req.getHeaders().get("authorization"))
            .orElse(null);
        String token = (inboundAuth != null && !inboundAuth.isBlank())
            ? inboundAuth
            : "Bearer " + properties.defaultAuthToken();
        Long uid = JwtUtil.uidFromBearer(token);
        log.debug("[jlhub] deriveCallerUid: token-source={}, uid={}",
            (inboundAuth != null && !inboundAuth.isBlank()) ? "inbound" : "default",
            uid);
        if (uid == null) {
            log.debug("[jlhub] could not derive x-ht-uid from inbound JWT");
            return null;
        }
        return String.valueOf(uid);
    }

    @Override
    public int getOrder() {
        return Integer.MAX_VALUE; // run after HeaderPropagationFilter
    }
}