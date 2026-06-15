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
        @Nullable String deviceId) {

    public JilaliProperties {
        forwardedHeaders = forwardedHeaders == null ? List.of() : List.copyOf(forwardedHeaders);
        defaultAuthToken = defaultAuthToken != null ? defaultAuthToken : "";
        // Left empty when unset; AgoraTokenCipher.decrypt then fails on first use with an empty key.
        agoraCipherKey = agoraCipherKey != null ? agoraCipherKey : "";
        serverPubKeyHex = serverPubKeyHex != null ? serverPubKeyHex : "";
        deviceId = deviceId != null ? deviceId : "";
    }

    /** 16-byte AES key for decrypting LiveHub's Agora token payloads. */
    public String agoraCipherKey() {
        return agoraCipherKey;
    }
}
