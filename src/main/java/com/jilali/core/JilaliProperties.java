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
        @Nullable String deviceId,
        @Nullable String htServerUrl,
        @Nullable String htImOperator,
        @Nullable String htImCountry,
        @Nullable String deviceModel) {

    /** Stable fallback device ID generated once per process start. */
    private static final String FALLBACK_DEVICE_ID = UUID.randomUUID().toString().replace("-", "");

    public JilaliProperties {
        forwardedHeaders = forwardedHeaders == null ? List.of() : List.copyOf(forwardedHeaders);
        defaultAuthToken = defaultAuthToken != null ? defaultAuthToken : "";
        agoraCipherKey   = agoraCipherKey   != null ? agoraCipherKey   : "";
        serverPubKeyHex  = serverPubKeyHex  != null ? serverPubKeyHex  : "";
        deviceId         = deviceId         != null && !deviceId.isBlank()
            ? deviceId : FALLBACK_DEVICE_ID;
        htServerUrl      = htServerUrl != null && !htServerUrl.isBlank()
            ? htServerUrl : "wss://uploadprocn.hellotalk8.com/livehub/ws/conn";
        htImOperator     = htImOperator     != null ? htImOperator     : "unknown";
        htImCountry      = htImCountry      != null ? htImCountry      : "US";
        deviceModel      = deviceModel      != null ? deviceModel      : "samsung SM-G991B";
    }

    /** 16-byte AES key for decrypting LiveHub's Agora token payloads. */
    public String agoraCipherKey() { return agoraCipherKey; }

    /** LiveHub WebSocket base URL. */
    public String htServerUrl() { return htServerUrl; }

    /** Mobile operator string included in the HT IM login packet. */
    public String htImOperator() { return htImOperator; }

    /** Operator country code included in the HT IM login packet. */
    public String htImCountry() { return htImCountry; }

    /** Device model string included in the HT IM login packet ({@code device_detail} field). */
    public String deviceModel() { return deviceModel; }
}
