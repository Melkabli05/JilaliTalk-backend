package com.jilali.core;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link JwtUtil}. The tokens we care about are opaque to us — we only read the
 * {@code uid} claim to attach per-request context to outbound upstream calls, so the test inputs
 * are constructed manually rather than signed.
 */
class JwtUtilTest {

    private static String fakeJwt(String payloadJson) {
        String header = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".signature";
    }

    @Test
    void extractsUidFromBearerString() {
        String jwt = fakeJwt("{\"uid\":169335562,\"src\":2}");

        assertEquals(169335562L, JwtUtil.uidFromBearer("Bearer " + jwt));
    }

    @Test
    void acceptsTokenWithoutBearerPrefix() {
        String jwt = fakeJwt("{\"uid\":42}");

        assertEquals(42L, JwtUtil.uidFromBearer(jwt));
    }

    @Test
    void isCaseInsensitiveAboutBearerPrefix() {
        String jwt = fakeJwt("{\"uid\":99}");

        assertEquals(99L, JwtUtil.uidFromBearer("bearer " + jwt));
        assertEquals(99L, JwtUtil.uidFromBearer("BEARER " + jwt));
    }

    @Test
    void returnsNullWhenPayloadHasNoUidClaim() {
        String jwt = fakeJwt("{\"src\":2,\"exp\":1234}");

        assertNull(JwtUtil.uidFromBearer("Bearer " + jwt));
    }

    @Test
    void returnsNullForMalformedJwt() {
        assertNull(JwtUtil.uidFromBearer(null));
        assertNull(JwtUtil.uidFromBearer(""));
        assertNull(JwtUtil.uidFromBearer("   "));
        assertNull(JwtUtil.uidFromBearer("not-a-jwt"));
        assertNull(JwtUtil.uidFromBearer("only.one"));
        // Payload is not valid Base64URL — must not throw, must return null.
        assertNull(JwtUtil.uidFromBearer("header.!!!not-base64!!!.signature"));
    }
}