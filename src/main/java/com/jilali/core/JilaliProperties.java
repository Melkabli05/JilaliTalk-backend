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
        @Nullable String defaultAuthToken,
        @Nullable String agoraCipherKey,
        @Nullable String serverPubKeyHex,
        @Nullable String translateServerPubKeyHex,
        @Nullable String deviceId,
        @Nullable String deviceModel,
        @Nullable List<String> allowedWebSocketOrigins) {

    public JilaliProperties {
        forwardedHeaders = forwardedHeaders == null ? List.of() : List.copyOf(forwardedHeaders);
        defaultAuthToken = defaultAuthToken != null ? defaultAuthToken : "";
        agoraCipherKey   = agoraCipherKey   != null ? agoraCipherKey   : "";
        serverPubKeyHex  = serverPubKeyHex  != null ? serverPubKeyHex  : "";
        translateServerPubKeyHex = translateServerPubKeyHex != null ? translateServerPubKeyHex : "";
        // Falls back to a persisted device id (see DeviceIdStore) rather than a fresh random one
        // per process start — a stable device id across restarts, matching the real app's own
        // MMKV-persisted DeviceVQHelper.generateDVId() behavior. Only computed/read from disk
        // when no explicit override is configured (LIVEHUB_DEVICE_ID).
        deviceId         = deviceId         != null && !deviceId.isBlank()
            ? deviceId : DeviceIdStore.loadOrCreate();
        deviceModel      = deviceModel      != null && !deviceModel.isBlank()
            ? deviceModel : "Samsung Galaxy S21";
        allowedWebSocketOrigins = allowedWebSocketOrigins != null
            ? List.copyOf(allowedWebSocketOrigins)
            : List.of("http://localhost:4200", "http://localhost:4201");
    }

    /** 16-byte AES key for decrypting LiveHub's Agora token payloads. */
    public String agoraCipherKey() { return agoraCipherKey; }
}
