package com.jilali.realtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.nio.charset.StandardCharsets;

class RoomRealtimeRegistryTest {

    private final ObjectMapper om = new ObjectMapper();

    private static String fakeJwt(String payloadJson) {
        String header = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("{\"alg\":\"HS256\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".signature";
    }

    @Test
    void resolveUserIdExtractsUidFromAValidJwt() {
        String token = fakeJwt("{\"exp\":1753214399,\"src\":2,\"uid\":131331894}");
        assertEquals("131331894", RoomRealtimeRegistry.resolveUserId(token, om));
    }

    @Test
    void resolveUserIdRejectsATokenWithoutTwoSegments() {
        assertThrows(IllegalStateException.class, () -> RoomRealtimeRegistry.resolveUserId("not-a-jwt", om));
    }

    @Test
    void resolveUserIdRejectsAPayloadMissingTheUidClaim() {
        String token = fakeJwt("{\"exp\":1753214399}");
        assertThrows(IllegalStateException.class, () -> RoomRealtimeRegistry.resolveUserId(token, om));
    }
}