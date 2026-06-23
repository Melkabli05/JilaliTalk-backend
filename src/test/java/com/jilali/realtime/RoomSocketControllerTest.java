package com.jilali.realtime;

import com.jilali.realtime.dto.RoomRealtimeEvent;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.websocket.WebSocketClient;
import io.micronaut.websocket.annotation.ClientWebSocket;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Proves {@link RoomSocketController} parses the {@code hostId}/{@code busiType}/
 * {@code heartbeatSeconds} query params off the WS connect URL and forwards them to
 * {@link RoomEventSource#subscribe} — the plumbing {@link RoomRealtimeRegistry} relies on to
 * drive the room-level presence heartbeat. Substitutes a fake {@link RoomEventSource} (the
 * seam {@code RoomEventSource}'s own javadoc calls out) so this never touches a real LiveHub
 * socket or JilaliClient call.
 */
@MicronautTest
class RoomSocketControllerTest {

    @Inject
    WebSocketClient wsClient;

    @Inject
    FakeRoomEventSource source;

    @Inject
    EmbeddedServer embeddedServer;

    private TestClientSocket socket;

    @BeforeEach
    void clearRecordedCalls() {
        source.calls.clear();
    }

    @AfterEach
    void close() throws Exception {
        if (socket != null) {
            socket.close();
        }
    }

    @Test
    void forwardsHostIdBusiTypeAndHeartbeatSecondsFromQueryParams() throws InterruptedException {
        URI uri = embeddedServer.getURI().resolve(UriBuilder.of("/ws/ht/{cname}")
            .queryParam("hostId", 555)
            .queryParam("busiType", 1)
            .queryParam("heartbeatSeconds", 30)
            .expand(java.util.Map.of("cname", "ROOM_1")));

        socket = Mono.from(wsClient.connect(TestClientSocket.class, HttpRequest.GET(uri).header("Origin", "http://localhost:4200"))).block();

        Call call = awaitOneCall();
        assertEquals("ROOM_1", call.cname);
        assertEquals(555L, call.hostId);
        assertEquals(1, call.busiType);
        assertEquals(30L, call.heartbeatSeconds);
    }

    @Test
    void defaultsHostIdToZeroWhenAbsent() throws InterruptedException {
        URI uri = embeddedServer.getURI().resolve(
            UriBuilder.of("/ws/ht/{cname}").expand(java.util.Map.of("cname", "ROOM_2")));

        socket = Mono.from(wsClient.connect(TestClientSocket.class, HttpRequest.GET(uri).header("Origin", "http://localhost:4200"))).block();

        Call call = awaitOneCall();
        assertEquals("ROOM_2", call.cname);
        assertEquals(0L, call.hostId);
        assertEquals(2, call.busiType);
        assertEquals(0L, call.heartbeatSeconds);
    }

    /** The server's onOpen runs asynchronously relative to the client's connect() completing. */
    private Call awaitOneCall() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            if (!source.calls.isEmpty()) {
                return source.calls.get(0);
            }
            Thread.sleep(20);
        }
        fail("RoomEventSource.subscribe was never called within 2s");
        throw new AssertionError("unreachable");
    }

    record Call(String cname, long hostId, int busiType, long heartbeatSeconds, Optional<Long> jilaliUserId) {
    }

    @Singleton
    @Requires(env = "test")
    @Replaces(RoomRealtimeRegistry.class)
    static class FakeRoomEventSource implements RoomEventSource {
        final List<Call> calls = new CopyOnWriteArrayList<>();

        @Override
        public Flux<RoomRealtimeEvent> subscribe(String cname, long hostId, int busiType, long heartbeatSeconds,
                                                   Optional<Long> jilaliUserId) {
            calls.add(new Call(cname, hostId, busiType, heartbeatSeconds, jilaliUserId));
            return Flux.never();
        }

        @Override
        public void unsubscribe(String cname, Optional<Long> jilaliUserId) {
            // no-op
        }
    }

    @ClientWebSocket
    abstract static class TestClientSocket implements AutoCloseable {
        @OnOpen
        void onOpen() {
            // no-op — connecting is all this test needs
        }

        /** Required by Micronaut even though this push-only endpoint never sends frames. */
        @OnMessage
        void onMessage(String message) {
            // no-op
        }
    }
}
