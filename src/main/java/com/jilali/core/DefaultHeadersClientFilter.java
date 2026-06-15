package com.jilali.core;

import io.micronaut.core.order.Ordered;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.ClientFilter;
import io.micronaut.http.annotation.RequestFilter;
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
            default -> null;
        };
    }

    @Override
    public int getOrder() {
        return Integer.MAX_VALUE; // run after HeaderPropagationFilter
    }
}
