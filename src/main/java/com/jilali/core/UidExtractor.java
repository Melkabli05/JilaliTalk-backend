package com.jilali.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;

/** Shared JWT uid extractor — used at startup to derive the HelloTalk user ID from the auth token. */
public final class UidExtractor {

    private static final Logger log = LoggerFactory.getLogger(UidExtractor.class);

    private UidExtractor() {}

    /** Extract uid as String (null-safe). */
    public static String uidAsString(String jwt, ObjectMapper om) {
        return uidAsStringImpl(jwt, om);
    }

    /** Extract uid as long (null-safe, 0 if absent). */
    public static long uidAsLong(String jwt, ObjectMapper om) {
        return uidAsLongImpl(jwt, om);
    }

    private static String uidAsStringImpl(String jwt, ObjectMapper om) {
        if (jwt == null || jwt.isBlank()) {
            log.warn("UidExtractor: null/blank JWT, returning \"0\"");
            return "0";
        }
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) {
                log.warn("UidExtractor: JWT has fewer than 2 parts, returning \"0\"");
                return "0";
            }
            String payload = parts[1];
            payload = switch (payload.length() % 4) {
                case 2 -> payload + "==";
                case 3 -> payload + "=";
                default -> payload;
            };
            return om.readTree(Base64.getUrlDecoder().decode(payload))
                .path("uid").asText("0");
        } catch (Exception e) {
            log.warn("UidExtractor: failed to decode uid from JWT: {}", e.getMessage());
            return "0";
        }
    }

    private static long uidAsLongImpl(String jwt, ObjectMapper om) {
        if (jwt == null || jwt.isBlank()) {
            log.warn("UidExtractor: null/blank JWT, returning 0L");
            return 0L;
        }
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) {
                log.warn("UidExtractor: JWT has fewer than 2 parts, returning 0L");
                return 0L;
            }
            String payload = parts[1];
            payload = switch (payload.length() % 4) {
                case 2 -> payload + "==";
                case 3 -> payload + "=";
                default -> payload;
            };
            return om.readTree(Base64.getUrlDecoder().decode(payload))
                .path("uid").asLong(0L);
        } catch (Exception e) {
            log.warn("UidExtractor: failed to decode uid from JWT: {}", e.getMessage());
            return 0L;
        }
    }
}
