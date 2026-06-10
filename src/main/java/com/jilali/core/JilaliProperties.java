package com.jilali.core;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;

import java.util.List;

/**
 * Type-safe binding of the {@code jilali.*} config tree.
 */
@ConfigurationProperties("jilali")
public record JilaliProperties(
        List<String> forwardedHeaders,
        @Nullable String defaultAuthToken) {

    public JilaliProperties {
        forwardedHeaders = forwardedHeaders == null ? List.of() : List.copyOf(forwardedHeaders);
        defaultAuthToken = defaultAuthToken != null ? defaultAuthToken : "";
    }
}
