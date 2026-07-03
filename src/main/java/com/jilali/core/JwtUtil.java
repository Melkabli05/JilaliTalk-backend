package com.jilali.core;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * Tiny utility for extracting non-sensitive claims from our auth JWTs without verifying the
 * signature — we trust the gateway has already validated the token before it reaches us, and we
 * only read it to add per-request context (caller uid) to outbound upstream headers.
 * <p>
 * Signature verification is intentionally NOT done here: it would add a dependency on a JWK set,
 * and an upstream that already accepted an authenticated request doesn't gain anything from a
 * second check at this layer.
 */
public final class JwtUtil {

    /** Matches the {@code "uid": 12345} field in a JWT payload. */
    private static final Pattern UID_CLAIM_PATTERN = Pattern.compile("\"uid\"\\s*:\\s*(\\d+)");

    private JwtUtil() {
    }

    /**
     * Returns the {@code uid} claim encoded in the given {@code Bearer <jwt>} string, or
     * {@code null} if it can't be parsed. Returns the first match — the only uid claim in our
     * tokens is the caller.
     */
    public static Long uidFromBearer(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank()) {
            return null;
        }
        String jwt = bearerToken.replaceFirst("(?i)^Bearer\\s+", "");
        String[] parts = jwt.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        try {
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            var matcher = UID_CLAIM_PATTERN.matcher(payload);
            return matcher.find() ? Long.parseLong(matcher.group(1)) : null;
        } catch (IllegalArgumentException _) {
            return null;
        }
    }
}
