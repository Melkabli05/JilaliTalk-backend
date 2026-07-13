package com.jilali.core;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Type-safe binding of the {@code jilali.*} config tree.
 */
@ConfigurationProperties("jilali")
public record JilaliProperties(
        List<String> forwardedHeaders,
        @Nullable String defaultAuthToken,
        @Nullable String agoraCipherKey,
        @Nullable String serverPubKeyHex,
        @Nullable String translateServerPubKeyHex,
        @Nullable String deviceId,
        @Nullable String deviceModel,
        @Nullable List<String> allowedWebSocketOrigins) {

    /** Stable fallback device ID generated once per process start. */
    private static final String FALLBACK_DEVICE_ID = UUID.randomUUID().toString().replace("-", "");

    public JilaliProperties {
        forwardedHeaders = forwardedHeaders == null ? List.of() : List.copyOf(forwardedHeaders);
        defaultAuthToken = defaultAuthToken != null ? defaultAuthToken : "";
        agoraCipherKey   = agoraCipherKey   != null ? agoraCipherKey   : "";
        serverPubKeyHex  = serverPubKeyHex  != null ? serverPubKeyHex  : "";
        translateServerPubKeyHex = translateServerPubKeyHex != null ? translateServerPubKeyHex : "";
        deviceId         = deviceId         != null && !deviceId.isBlank()
            ? deviceId : FALLBACK_DEVICE_ID;
        deviceModel      = deviceModel      != null && !deviceModel.isBlank()
            ? deviceModel : "Samsung Galaxy S21";
        allowedWebSocketOrigins = allowedWebSocketOrigins != null
            ? List.copyOf(allowedWebSocketOrigins)
            : List.of("http://localhost:4200", "http://localhost:4201");
    }

    /** 16-byte AES key for decrypting LiveHub's Agora token payloads. */
    public String agoraCipherKey() { return agoraCipherKey; }
}
