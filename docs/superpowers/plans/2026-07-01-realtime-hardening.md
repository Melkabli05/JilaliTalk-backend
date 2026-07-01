# Realtime (`com.jilali.im` + `com.jilali.realtime`) Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Decompose `HtImUpstreamConnector`'s 624-line god class into focused, independently-testable units; eliminate duplicated send-queue/heartbeat-scheduling code between the two upstream WebSocket connectors; replace fragile hand-built JSON strings with `ObjectMapper`-built payloads; and add capped-exponential-backoff-with-jitter reconnect for unexpected upstream disconnects.

**Architecture:** New shared infrastructure (`ExponentialBackoff`, `SequentialSender`, `HeartbeatPump`) lives in `com.jilali.core.ws`, composed (not inherited) by both connectors. `HtImUpstreamConnector`'s byte-level decoding moves to a new pure `HtImFrameDecoder`; its JSON-to-event mapping moves to a new pure `HtImNotifyMapper` mirroring the already-solid `HtNotifyMapper` pattern in `com.jilali.realtime`. `HtLiveHubUpstreamConnector` adopts the same shared utilities and switches its string-concatenated JSON frames to `ObjectMapper`-built ones.

**Tech Stack:** Java 25, Micronaut 5, Jackson (`ObjectMapper`/`JsonNode`), JDK `java.net.http.WebSocket`, JUnit 5.

## Global Constraints

- Wire contract is frozen: WebSocket endpoints (`/ws/im`, `/ws/ht/{cname}`) and the JSON shapes of `ImRealtimeEvent`/`RoomRealtimeEvent` do not change. This is a pure internal refactor — zero frontend changes.
- No new max-attempt cutoff on reconnect: retries continue indefinitely (capped exponential backoff, full jitter) as long as a subscriber exists; `RoomEventSource`/`ImEventSource` still own "is anyone listening" via their existing `close()` calls.
- Composition over inheritance for the two connectors — no shared abstract base class.
- All existing tests (`HtNotifyMapperTest`, `RoomRealtimeEventTest`) must stay green throughout.
- Full spec: `docs/superpowers/specs/2026-07-01-realtime-hardening-design.md`.

---

### Task 1: Shared WebSocket infrastructure (`com.jilali.core.ws`)

**Files:**
- Create: `src/main/java/com/jilali/core/ws/ExponentialBackoff.java`
- Create: `src/main/java/com/jilali/core/ws/SequentialSender.java`
- Create: `src/main/java/com/jilali/core/ws/HeartbeatPump.java`
- Test: `src/test/java/com/jilali/core/ws/ExponentialBackoffTest.java`

**Interfaces:**
- Produces: `ExponentialBackoff(Duration base, Duration cap)`, `Duration nextDelay()`, `void reset()`, package-private static `Duration boundFor(int attempt, Duration base, Duration cap)`.
- Produces: `SequentialSender()` (no-arg), `void enqueue(Supplier<CompletableFuture<WebSocket>> sendOp, Consumer<Throwable> onError)`, `void reset()`.
- Produces: `HeartbeatPump(String threadName)`, `void start(Duration initialDelay, Duration period, Runnable pingAction)`, `void start(Duration period, Runnable pingAction)`, `void stop()`, `void close()` (implements `AutoCloseable`).
- Consumes: nothing from other tasks — this task has no dependencies.

- [ ] **Step 1: Write the failing test for `ExponentialBackoff`**

```java
// src/test/java/com/jilali/core/ws/ExponentialBackoffTest.java
package com.jilali.core.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.time.Duration;

class ExponentialBackoffTest {

    private final Duration base = Duration.ofSeconds(1);
    private final Duration cap = Duration.ofSeconds(30);

    @Test
    void boundDoublesEachAttemptUntilCapped() {
        assertEquals(Duration.ofSeconds(1),  ExponentialBackoff.boundFor(0, base, cap));
        assertEquals(Duration.ofSeconds(2),  ExponentialBackoff.boundFor(1, base, cap));
        assertEquals(Duration.ofSeconds(4),  ExponentialBackoff.boundFor(2, base, cap));
        assertEquals(Duration.ofSeconds(8),  ExponentialBackoff.boundFor(3, base, cap));
        assertEquals(Duration.ofSeconds(16), ExponentialBackoff.boundFor(4, base, cap));
        assertEquals(Duration.ofSeconds(30), ExponentialBackoff.boundFor(5, base, cap)); // 32 capped to 30
        assertEquals(Duration.ofSeconds(30), ExponentialBackoff.boundFor(100, base, cap)); // stays capped
    }

    @Test
    void nextDelayNeverExceedsTheBoundForItsAttempt() {
        ExponentialBackoff backoff = new ExponentialBackoff(base, cap);
        for (int i = 0; i < 10; i++) {
            Duration expectedBound = ExponentialBackoff.boundFor(i, base, cap);
            Duration delay = backoff.nextDelay();
            assertTrue(delay.toMillis() >= 0, "delay must be non-negative");
            assertTrue(delay.toMillis() <= expectedBound.toMillis(),
                "delay " + delay + " exceeded bound " + expectedBound + " at attempt " + i);
        }
    }

    @Test
    void resetReturnsToBaseAttempt() {
        ExponentialBackoff backoff = new ExponentialBackoff(base, cap);
        backoff.nextDelay();
        backoff.nextDelay();
        backoff.nextDelay(); // attempt counter now at 3

        backoff.reset();

        Duration delay = backoff.nextDelay(); // back to attempt 0 -> bound 1s
        assertTrue(delay.toMillis() <= base.toMillis());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails (class doesn't exist yet)**

Run: `./gradlew test --tests "com.jilali.core.ws.ExponentialBackoffTest"`
Expected: FAIL — compilation error, `ExponentialBackoff` cannot be resolved.

- [ ] **Step 3: Implement `ExponentialBackoff`**

```java
// src/main/java/com/jilali/core/ws/ExponentialBackoff.java
package com.jilali.core.ws;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Capped exponential backoff with full jitter: {@code delay = random(0, min(cap, base * 2^attempt))}.
 * Full jitter is the standard defense against reconnect storms against a recovering upstream —
 * every retrying client picks an independent point in the window instead of firing in sync.
 * Stateful only in the attempt counter; call {@link #reset()} after a successful reconnect so
 * the next unrelated failure starts from {@code base} again.
 */
public final class ExponentialBackoff {

    private final Duration base;
    private final Duration cap;
    private final AtomicInteger attempt = new AtomicInteger(0);

    public ExponentialBackoff(Duration base, Duration cap) {
        this.base = base;
        this.cap = cap;
    }

    /** Upper bound of the jitter window for a given attempt count, before randomization. */
    static Duration boundFor(int attempt, Duration base, Duration cap) {
        long baseMillis = base.toMillis();
        long capMillis = cap.toMillis();
        long shifted = baseMillis;
        for (int i = 0; i < attempt && shifted < capMillis; i++) {
            shifted = Math.min(capMillis, shifted * 2);
        }
        return Duration.ofMillis(Math.min(capMillis, shifted));
    }

    /** Next delay, advancing the attempt counter — full jitter: {@code random(0, boundFor(attempt))}. */
    public Duration nextDelay() {
        Duration bound = boundFor(attempt.getAndIncrement(), base, cap);
        long jittered = ThreadLocalRandom.current().nextLong(0, bound.toMillis() + 1);
        return Duration.ofMillis(jittered);
    }

    /** Call after a successful reconnect so the next unrelated failure starts from {@code base}. */
    public void reset() {
        attempt.set(0);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests "com.jilali.core.ws.ExponentialBackoffTest"`
Expected: PASS — 3 tests, 0 failures.

- [ ] **Step 5: Implement `SequentialSender` (no dedicated test — see rationale below)**

`SequentialSender` and `HeartbeatPump` are thin wrappers around JDK concurrency primitives
(`CompletableFuture` chaining, `ScheduledExecutorService`) with no branching logic of their
own to unit-test in isolation — exercising them meaningfully requires a real `WebSocket`,
which neither connector's existing test suite attempts (this codebase doesn't unit-test the
raw `HttpClient` WebSocket plumbing anywhere today). They're validated indirectly: the full
test suite staying green after Tasks 5 and 6 wire them into both connectors is the signal
that the send-ordering and heartbeat-scheduling behavior still works.

```java
// src/main/java/com/jilali/core/ws/SequentialSender.java
package com.jilali.core.ws;

import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Serializes async sends over a single WebSocket so concurrent callers never interleave
 * partial writes — {@code WebSocket.sendText}/{@code sendBinary} each return a future that
 * must complete before the next send starts, or frames can arrive out of order or truncated.
 * This is the same chaining pattern both upstream connectors used to hand-roll independently.
 */
public final class SequentialSender {

    private volatile CompletableFuture<WebSocket> chain = CompletableFuture.completedFuture(null);

    /**
     * Queue a send. {@code sendOp} runs only after every previously queued send has completed
     * (successfully or not); {@code onError} runs if it throws or completes exceptionally.
     */
    public synchronized void enqueue(Supplier<CompletableFuture<WebSocket>> sendOp, Consumer<Throwable> onError) {
        chain = chain
            .handle((r, t) -> null)
            .thenCompose(r -> sendOp.get())
            .exceptionally(e -> {
                onError.accept(e);
                return null;
            });
    }

    /** Reset the chain, e.g. after a reconnect where any in-flight sends are now moot. */
    public void reset() {
        chain = CompletableFuture.completedFuture(null);
    }
}
```

- [ ] **Step 6: Implement `HeartbeatPump`**

```java
// src/main/java/com/jilali/core/ws/HeartbeatPump.java
package com.jilali.core.ws;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Owns a single virtual-thread scheduler for one periodic heartbeat. {@link #start} cancels
 * any previously-scheduled ping before scheduling the new one, so a server-driven interval
 * change (LiveHub tells us the interval only after connecting, and it can change) never leaks
 * the old schedule.
 */
public final class HeartbeatPump implements AutoCloseable {

    private final ScheduledExecutorService scheduler;
    private volatile ScheduledFuture<?> future;

    public HeartbeatPump(String threadName) {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().name(threadName).factory());
    }

    /** Start a fixed-rate ping, first firing after {@code initialDelay}, then every {@code period}. */
    public synchronized void start(Duration initialDelay, Duration period, Runnable pingAction) {
        cancelCurrent();
        long initSec = Math.max(0, initialDelay.toSeconds());
        long periodSec = Math.max(1, period.toSeconds());
        future = scheduler.scheduleAtFixedRate(pingAction, initSec, periodSec, TimeUnit.SECONDS);
    }

    /** Convenience for a fixed cadence where the first ping fires after one full period. */
    public void start(Duration period, Runnable pingAction) {
        start(period, period, pingAction);
    }

    public synchronized void stop() {
        cancelCurrent();
    }

    private void cancelCurrent() {
        ScheduledFuture<?> f = future;
        if (f != null) { f.cancel(false); future = null; }
    }

    @Override
    public void close() {
        stop();
        scheduler.shutdownNow();
    }
}
```

- [ ] **Step 7: Compile and run the full test suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL — all existing tests plus the 3 new `ExponentialBackoffTest` cases pass.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/jilali/core/ws src/test/java/com/jilali/core/ws
git commit -m "feat(core): add shared WS infrastructure (backoff, sequential sender, heartbeat pump)"
```

---

### Task 2: Move `inflate`/`copyPayload` into `HtImPacketFramer`

**Files:**
- Modify: `src/main/java/com/jilali/im/HtImPacketFramer.java`

**Interfaces:**
- Consumes: nothing from Task 1.
- Produces: `static byte[] inflate(byte[] data)`, `static byte[] copyPayload(byte[] data, int payloadLen)` — both package-private, used by Task 3's `HtImFrameDecoder`.

- [ ] **Step 1: Add `inflate` and `copyPayload` to `HtImPacketFramer`**

Current file is `src/main/java/com/jilali/im/HtImPacketFramer.java` (115 lines). Add the two
methods and the two extra imports they need (`java.util.Arrays`, `java.util.zip.Inflater`).

Modify the import block at the top (currently lines 3-8):

```java
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
```

Add these two methods right after `deflate` (after the closing brace of the existing `deflate`
method, before `parseHeader`):

```java
    /** Copy the payload region (after the 20-byte header) out of a raw inbound packet. */
    static byte[] copyPayload(byte[] data, int payloadLen) {
        byte[] payload = new byte[payloadLen];
        System.arraycopy(data, HEADER_LEN, payload, 0, payloadLen);
        return payload;
    }

    /** Zlib-inflate a byte array; if it isn't zlib-compressed (doesn't start with 0x78),
     *  returns it unchanged. Tries both wrapped and raw-deflate modes since HelloTalk's
     *  server has been observed sending both. */
    static byte[] inflate(byte[] data) {
        if (data == null || data.length == 0) return data;
        if ((data[0] & 0xFF) != 0x78) return data; // not zlib compressed

        for (boolean nowrap : new boolean[]{false, true}) {
            try {
                Inflater inf = new Inflater(nowrap);
                inf.setInput(data);
                byte[] out = new byte[data.length * 8];
                int n = inf.inflate(out);
                inf.end();
                return Arrays.copyOf(out, n);
            } catch (Exception ignored) {
                // try the other mode
            }
        }
        return null;
    }
```

- [ ] **Step 2: Compile**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL (nothing calls these new methods yet, but they compile standalone).

- [ ] **Step 3: Run the full test suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL — no behavior changed yet (the connector still has its own private
copies of this logic until Task 5).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/jilali/im/HtImPacketFramer.java
git commit -m "refactor(im): move inflate/copyPayload into HtImPacketFramer"
```

---

### Task 3: `HtImFrameDecoder` — pure byte-level decoding

**Files:**
- Create: `src/main/java/com/jilali/im/HtImFrameDecoder.java`
- Test: `src/test/java/com/jilali/im/HtImFrameDecoderTest.java`

**Interfaces:**
- Consumes: `HtImPacketFramer.copyPayload(byte[], int)`, `HtImPacketFramer.inflate(byte[])` (Task 2), `HtImPacketFramer.Header`, `HtImPacketFramer.HEADER_LEN`, `HtImPacketFramer.PKT_PUSH`, `HtImPacketFramer.buildPacket(long, int, byte[])`, `HtImPacketFramer.deflate(byte[])`, `QqTeaCipher.decrypt`/`encrypt` (existing, `com.jilali.crypto`).
- Produces: `HtImFrameDecoder(ObjectMapper om)`; sealed interface `HtImFrameDecoder.F2Push` with records `Receipt(String msgId)`, `Poke()`, `Json(JsonNode root)`, `Unknown(int firstByte, byte[] bytes)`, `DecryptFailed()`, `Ignored()`; record `HtImFrameDecoder.OfflinePacket(Header header, F2Push body)`; methods `Optional<JsonNode> decodeF1(byte[] data, int payloadLen)`, `F2Push decodeF2(byte[] data, int payloadLen, byte[] sessionKey)`, `boolean decodeTypingStatus(byte[] data, int payloadLen, byte[] sessionKey, int keyType)`, `Optional<OfflinePacket> decodeOfflinePacket(String base64, byte[] sessionKey)` — all used by Task 5's rewritten `HtImUpstreamConnector`.

- [ ] **Step 1: Write the failing tests**

```java
// src/test/java/com/jilali/im/HtImFrameDecoderTest.java
package com.jilali.im;

import static com.jilali.im.HtImPacketFramer.HEADER_LEN;
import static com.jilali.im.HtImPacketFramer.buildPacket;
import static com.jilali.im.HtImPacketFramer.deflate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.crypto.QqTeaCipher;
import com.jilali.im.HtImFrameDecoder.F2Push;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

class HtImFrameDecoderTest {

    private final ObjectMapper om = new ObjectMapper();
    private final HtImFrameDecoder decoder = new HtImFrameDecoder(om);
    private static final byte[] SESSION_KEY = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    @Test
    void decodeF1ParsesDeflatedJson() {
        byte[] json = "{\"status\":0,\"data\":{}}".getBytes(StandardCharsets.UTF_8);
        byte[] packet = buildPacket(1L, 0, deflate(json));

        Optional<JsonNode> result = decoder.decodeF1(packet, packet.length - HEADER_LEN);

        assertTrue(result.isPresent());
        assertEquals(0, result.get().path("status").asInt());
    }

    @Test
    void decodeF1IgnoresNonJsonPayload() {
        byte[] packet = buildPacket(1L, 0, deflate("not json".getBytes(StandardCharsets.UTF_8)));

        Optional<JsonNode> result = decoder.decodeF1(packet, packet.length - HEADER_LEN);

        assertTrue(result.isEmpty());
    }

    @Test
    void decodeF2DecryptsInflatesAndParsesJson() {
        byte[] json = "{\"msg_type\":\"text\",\"from_id\":5}".getBytes(StandardCharsets.UTF_8);
        byte[] compressed = deflate(json);
        byte[] encrypted = QqTeaCipher.encrypt(compressed, SESSION_KEY);
        byte[] packet = buildPacket(1L, 0, encrypted);

        F2Push result = decoder.decodeF2(packet, packet.length - HEADER_LEN, SESSION_KEY);

        var jsonPush = (F2Push.Json) result;
        assertEquals("text", jsonPush.root().path("msg_type").asText());
    }

    @Test
    void decodeF2WithoutSessionKeyIsIgnored() {
        byte[] packet = buildPacket(1L, 0, "irrelevant".getBytes(StandardCharsets.UTF_8));

        F2Push result = decoder.decodeF2(packet, packet.length - HEADER_LEN, null);

        assertEquals(new F2Push.Ignored(), result);
    }

    @Test
    void decodeF2RecognizesReadReceipt() {
        byte[] plain = new byte[40];
        plain[0] = 0x25;
        byte[] msgIdBytes = "abc-123".getBytes(StandardCharsets.UTF_8);
        System.arraycopy(msgIdBytes, 0, plain, 2, msgIdBytes.length);
        byte[] encrypted = QqTeaCipher.encrypt(plain, SESSION_KEY);
        byte[] packet = buildPacket(1L, 0, encrypted);

        F2Push result = decoder.decodeF2(packet, packet.length - HEADER_LEN, SESSION_KEY);

        var receipt = (F2Push.Receipt) result;
        assertEquals("abc-123", receipt.msgId());
    }

    @Test
    void decodeF2RecognizesPoke() {
        byte[] plain = new byte[]{0x08};
        byte[] encrypted = QqTeaCipher.encrypt(plain, SESSION_KEY);
        byte[] packet = buildPacket(1L, 0, encrypted);

        F2Push result = decoder.decodeF2(packet, packet.length - HEADER_LEN, SESSION_KEY);

        assertEquals(new F2Push.Poke(), result);
    }

    @Test
    void decodeF2RecognizesUnknownFirstByte() {
        byte[] plain = new byte[]{(byte) 0xAB, 1, 2, 3};
        byte[] encrypted = QqTeaCipher.encrypt(plain, SESSION_KEY);
        byte[] packet = buildPacket(1L, 0, encrypted);

        F2Push result = decoder.decodeF2(packet, packet.length - HEADER_LEN, SESSION_KEY);

        var unknown = (F2Push.Unknown) result;
        assertEquals(0xAB, unknown.firstByte());
    }

    @Test
    void decodeTypingStatusReadsTypingBit() {
        byte[] payload = new byte[]{0, 0, 0, 0, 1, 0}; // LE uint16 at offset 4 == 1 (typing)

        boolean typing = decoder.decodeTypingStatus(
            buildPacket(1L, 0, payload), payload.length, null, 0);

        assertTrue(typing);
    }

    @Test
    void decodeTypingStatusReadsStoppedBit() {
        byte[] payload = new byte[]{0, 0, 0, 0, 0, 0}; // status 0 == stopped

        boolean typing = decoder.decodeTypingStatus(
            buildPacket(1L, 0, payload), payload.length, null, 0);

        assertFalse(typing);
    }

    @Test
    void decodeOfflinePacketDecryptsAndParsesBase64Frame() {
        byte[] json = "{\"msg_type\":\"text\",\"from_id\":5}".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = QqTeaCipher.encrypt(json, SESSION_KEY);
        byte[] raw = new byte[HEADER_LEN + encrypted.length];
        raw[2] = 1; // keyType = 1 (encrypted)
        System.arraycopy(encrypted, 0, raw, HEADER_LEN, encrypted.length);
        ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).putInt(16, encrypted.length);
        String base64 = Base64.getEncoder().encodeToString(raw);

        Optional<HtImFrameDecoder.OfflinePacket> result = decoder.decodeOfflinePacket(base64, SESSION_KEY);

        assertTrue(result.isPresent());
        var jsonPush = (F2Push.Json) result.get().body();
        assertEquals("text", jsonPush.root().path("msg_type").asText());
    }

    @Test
    void decodeOfflinePacketReturnsEmptyForTooShortInput() {
        String base64 = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});

        assertTrue(decoder.decodeOfflinePacket(base64, SESSION_KEY).isEmpty());
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew test --tests "com.jilali.im.HtImFrameDecoderTest"`
Expected: FAIL — compilation error, `HtImFrameDecoder` cannot be resolved.

- [ ] **Step 3: Implement `HtImFrameDecoder`**

```java
// src/main/java/com/jilali/im/HtImFrameDecoder.java
package com.jilali.im;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.crypto.QqTeaCipher;
import com.jilali.im.HtImPacketFramer.Header;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

import static com.jilali.im.HtImPacketFramer.HEADER_LEN;
import static com.jilali.im.HtImPacketFramer.PKT_PUSH;

/**
 * Pure byte-level decoding for ht_im/sock frames: QQTEA decrypt, zlib inflate, and JSON
 * extraction. No networking and no mutable state beyond a per-instance {@link ObjectMapper} —
 * every method is a straight function of its inputs, which is what makes the format's quirks
 * (null-padding stripped after decrypt, dual zlib-inflate modes, base64-wrapped offline
 * packets) independently testable without a live WebSocket.
 *
 * <p>Note: unlike the original inline logic this replaces, parse failures here are silent
 * (returned as {@link F2Push.Ignored} / empty {@link Optional}) rather than logged — logging
 * is the caller's job, since this class has no {@code Logger} dependency by design. The two
 * failure modes worth a caller-visible diagnostic ({@link F2Push.DecryptFailed} and
 * {@link F2Push.Unknown}) still carry enough information for the caller to log them.
 */
final class HtImFrameDecoder {

    private final ObjectMapper om;

    HtImFrameDecoder(ObjectMapper om) {
        this.om = om;
    }

    /** Decoded push body — the shape shared by live F2 frames and replayed offline packets. */
    sealed interface F2Push {
        record Receipt(String msgId) implements F2Push {}
        record Poke() implements F2Push {}
        record Json(JsonNode root) implements F2Push {}
        record Unknown(int firstByte, byte[] bytes) implements F2Push {}
        record DecryptFailed() implements F2Push {}
        record Ignored() implements F2Push {}
    }

    /** An offline packet decoded from its base64 wrapper, with the header fields recovered
     *  from its own embedded 20-byte header (fromId is used as the from_id fallback by
     *  {@link HtImNotifyMapper}, matching how a live push uses the outer frame's header). */
    record OfflinePacket(Header header, F2Push body) {}

    /**
     * Decode an F1 (0xF1) response payload: extract, zlib-inflate, and parse as JSON.
     * Returns empty if the payload doesn't inflate or isn't a JSON object. Callers decide
     * what an F1 frame with {@code cmdId == CMD_PONG} means before calling this — that case
     * carries no payload worth decoding.
     */
    Optional<JsonNode> decodeF1(byte[] data, int payloadLen) {
        byte[] raw = HtImPacketFramer.copyPayload(data, payloadLen);
        byte[] decompressed = HtImPacketFramer.inflate(raw);
        if (decompressed == null) return Optional.empty();

        String text = stripNulls(decompressed).trim();
        if (!text.startsWith("{")) return Optional.empty();

        try {
            return Optional.of(om.readTree(text));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** Decode an F2 (0xF2) push frame: QQTEA-decrypt, then classify the decrypted body. */
    F2Push decodeF2(byte[] data, int payloadLen, byte[] sessionKey) {
        if (sessionKey == null || payloadLen == 0) return new F2Push.Ignored();

        byte[] encPayload = HtImPacketFramer.copyPayload(data, payloadLen);
        byte[] decrypted;
        try {
            decrypted = QqTeaCipher.decrypt(encPayload, sessionKey);
        } catch (Exception e) {
            return new F2Push.DecryptFailed();
        }
        if (decrypted == null || decrypted.length == 0) return new F2Push.Ignored();

        return decodePushBody(decrypted);
    }

    private F2Push decodePushBody(byte[] decrypted) {
        int firstByte = decrypted[0] & 0xFF;

        if (firstByte == 0x25) {
            if (decrypted.length < 38) return new F2Push.Ignored();
            String msgId = stripNulls(Arrays.copyOfRange(decrypted, 2, 38)).trim();
            return new F2Push.Receipt(msgId);
        }
        if (firstByte == 0x08) return new F2Push.Poke();

        byte[] finalBytes;
        if (firstByte == 0x78) {
            finalBytes = HtImPacketFramer.inflate(decrypted);
            if (finalBytes == null) return new F2Push.Ignored();
        } else if (firstByte == 0x7B) {
            finalBytes = decrypted;
        } else {
            return new F2Push.Unknown(firstByte, decrypted);
        }

        String jsonStr = stripNulls(finalBytes).trim();
        if (!jsonStr.startsWith("{")) return new F2Push.Ignored();
        try {
            return new F2Push.Json(om.readTree(jsonStr));
        } catch (Exception e) {
            return new F2Push.Ignored();
        }
    }

    /** True if the typing packet's status bits (LE uint16 at offset 4) indicate "typing". */
    boolean decodeTypingStatus(byte[] data, int payloadLen, byte[] sessionKey, int keyType) {
        byte[] payload = HtImPacketFramer.copyPayload(data, payloadLen);
        if (keyType == 1 && sessionKey != null && payload.length > 0) {
            try {
                byte[] dec = QqTeaCipher.decrypt(payload, sessionKey);
                if (dec != null) payload = dec;
            } catch (Exception ignored) {
                // fall through with the un-decrypted payload, matching the original behavior
            }
        }
        if (payload.length > 0 && (payload[0] & 0xFF) == 0x78) {
            byte[] inflated = HtImPacketFramer.inflate(payload);
            if (inflated != null) payload = inflated;
        }
        return payload.length < 6 || ((payload[4] & 0xFF) | ((payload[5] & 0xFF) << 8)) == 1;
    }

    /** Decode one base64 offline packet: its own 20-byte header + optionally-encrypted body. */
    Optional<OfflinePacket> decodeOfflinePacket(String base64, byte[] sessionKey) {
        try {
            byte[] raw = Base64.getDecoder().decode(base64);
            if (raw.length < HEADER_LEN) return Optional.empty();

            ByteBuffer buf = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
            int keyType  = buf.get(2) & 0xFF;
            int cmdId    = buf.getShort(4) & 0xFFFF;
            long fromId  = buf.getInt(8) & 0xFFFFFFFFL;
            long toId    = buf.getInt(12) & 0xFFFFFFFFL;
            int bodyLen  = Math.max(0, Math.min(buf.getInt(16), raw.length - HEADER_LEN));

            byte[] payload = new byte[bodyLen];
            System.arraycopy(raw, HEADER_LEN, payload, 0, bodyLen);
            if (payload.length == 0) return Optional.empty();

            if (keyType == 1 && sessionKey != null) {
                try {
                    byte[] dec = QqTeaCipher.decrypt(payload, sessionKey);
                    if (dec != null && dec.length > 0) payload = dec;
                } catch (Exception ignored) {
                    // fall through with the un-decrypted payload, matching the original behavior
                }
            }

            Header header = new Header(PKT_PUSH, keyType, cmdId, 0, fromId, toId, bodyLen);
            return Optional.of(new OfflinePacket(header, decodePushBody(payload)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static String stripNulls(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8).replace("\0", "");
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew test --tests "com.jilali.im.HtImFrameDecoderTest"`
Expected: PASS — 10 tests, 0 failures.

- [ ] **Step 5: Run the full test suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/jilali/im/HtImFrameDecoder.java src/test/java/com/jilali/im/HtImFrameDecoderTest.java
git commit -m "feat(im): add HtImFrameDecoder, pure byte-level decoding for ht_im/sock frames"
```

---

### Task 4: `HtImNotifyMapper` — pure JSON-to-event mapping

**Files:**
- Create: `src/main/java/com/jilali/im/HtImNotifyMapper.java`
- Create: `src/test/java/com/jilali/im/HtImNotifyMapperTest.java`
- Delete: `src/test/java/com/jilali/im/HtImUpstreamConnectorMappingTest.java`

**Interfaces:**
- Consumes: `HtImPacketFramer.Header` (existing), `ImRealtimeEvent` (existing, wire contract frozen — no changes to this DTO).
- Produces: `HtImNotifyMapper(long selfUserId)`, `ImRealtimeEvent map(JsonNode root, Header h)` — used by Task 5's rewritten `HtImUpstreamConnector`.

This task moves the mapping logic that already exists (correctly, and already covered by
`HtImUpstreamConnectorMappingTest`) out of `HtImUpstreamConnector` and into its own class. The
logic itself does not change — only its home and the tests' access path do.

- [ ] **Step 1: Write the new test file (mirrors the existing `HtImUpstreamConnectorMappingTest`, calling the mapper directly instead of the connector)**

```java
// src/test/java/com/jilali/im/HtImNotifyMapperTest.java
package com.jilali.im;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.im.HtImPacketFramer.Header;
import com.jilali.im.dto.ImRealtimeEvent;
import org.junit.jupiter.api.Test;

/**
 * Covers the personal ht_im/sock notify_type pushes (stage invite, mod invite, mod
 * accepted/removed/unmuted, follow, profile visit) that scriptv2.js startwebsock() received
 * on this same channel but the BFF's port had never implemented until this mapper was added.
 */
class HtImNotifyMapperTest {

    private final ObjectMapper om = new ObjectMapper();
    private final HtImNotifyMapper mapper = new HtImNotifyMapper(1L);
    private static final Header HEADER = new Header(0xF2, 1, 16386, 0, 999L, 1L, 0);

    private ImRealtimeEvent map(String json) throws Exception {
        JsonNode root = om.readTree(json);
        return mapper.map(root, HEADER);
    }

    @Test
    void notifyType18MapsToStageInvite() throws Exception {
        ImRealtimeEvent event = map(
            "{\"notify_type\":\"18\",\"notify_info\":{\"user_id\":\"3\",\"cname\":\"VR_1_2\"}}");
        var invite = assertInstanceOf(ImRealtimeEvent.StageInvite.class, event);
        assertEquals("3", invite.userId());
        assertEquals("VR_1_2", invite.cname());
    }

    @Test
    void notifyType48MapsToModInvite() throws Exception {
        ImRealtimeEvent event = map(
            "{\"notify_type\":\"48\",\"notify_info\":{\"user_id\":\"11\",\"cname\":\"VR_1_2\"}}");
        var invite = assertInstanceOf(ImRealtimeEvent.ModInvite.class, event);
        assertEquals("11", invite.userId());
        assertEquals("VR_1_2", invite.cname());
    }

    @Test
    void notifyType34MapsToModAccepted() throws Exception {
        ImRealtimeEvent event = map("{\"notify_type\":\"34\",\"notify_info\":{\"user_id\":\"7\"}}");
        assertEquals("7", assertInstanceOf(ImRealtimeEvent.ModAccepted.class, event).userId());
    }

    @Test
    void notifyType35MapsToModRemoved() throws Exception {
        ImRealtimeEvent event = map("{\"notify_type\":\"35\",\"notify_info\":{\"user_id\":\"7\"}}");
        assertEquals("7", assertInstanceOf(ImRealtimeEvent.ModRemoved.class, event).userId());
    }

    @Test
    void notifyType40MapsToModUnmuted() throws Exception {
        ImRealtimeEvent event = map("{\"notify_type\":\"40\",\"notify_info\":{\"user_id\":\"7\"}}");
        assertEquals("7", assertInstanceOf(ImRealtimeEvent.ModUnmuted.class, event).userId());
    }

    @Test
    void notifyType53MapsToFollow() throws Exception {
        ImRealtimeEvent event = map(
            "{\"notify_type\":\"53\",\"notify_info\":{\"nickname\":\"Jilali\",\"status\":2}}");
        var follow = assertInstanceOf(ImRealtimeEvent.Follow.class, event);
        assertEquals("Jilali", follow.nickname());
        assertEquals(2, follow.status());
    }

    @Test
    void notifyType48WithNoUserIdFallsBackToConnectorsOwnUid() throws Exception {
        // Real capture: HelloTalk never includes user_id on this personal channel — the invite
        // is implicitly "you" — only cname and the inviting host_id are present.
        ImRealtimeEvent event = map(
            "{\"notify_type\":48,\"notify_info\":{\"cname\":\"VR_131331894_1782897947418799102\",\"host_id\":131331894}}");
        var invite = assertInstanceOf(ImRealtimeEvent.ModInvite.class, event);
        assertEquals("1", invite.userId()); // mapper was constructed with selfUserId=1L
        assertEquals("VR_131331894_1782897947418799102", invite.cname());
    }

    @Test
    void newVoiceVisitorMsgTypeMapsToProfileVisit() throws Exception {
        ImRealtimeEvent event = map("{\"msg_type\":\"new_voice_visitor\",\"userId\":\"148459398\"}");
        assertEquals("148459398", assertInstanceOf(ImRealtimeEvent.ProfileVisit.class, event).visitorUserId());
    }

    @Test
    void legacyVisitorFieldShapeStillMapsToProfileVisit() throws Exception {
        ImRealtimeEvent event = map("{\"notify_type\":\"90\",\"visitor_uid\":\"55\"}");
        assertEquals("55", assertInstanceOf(ImRealtimeEvent.ProfileVisit.class, event).visitorUserId());
    }

    @Test
    void cnameShareStillTakesPriorityOverNotifyMapping() throws Exception {
        ImRealtimeEvent event = map(
            "{\"notify_type\":\"1\",\"cname\":\"VR_1_2\",\"nickname\":\"Host\",\"count\":3}");
        assertInstanceOf(ImRealtimeEvent.VoiceRoomShared.class, event);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "com.jilali.im.HtImNotifyMapperTest"`
Expected: FAIL — compilation error, `HtImNotifyMapper` cannot be resolved.

- [ ] **Step 3: Implement `HtImNotifyMapper`**

```java
// src/main/java/com/jilali/im/HtImNotifyMapper.java
package com.jilali.im;

import com.fasterxml.jackson.databind.JsonNode;
import com.jilali.im.HtImPacketFramer.Header;
import com.jilali.im.dto.ImRealtimeEvent;

/**
 * Maps decoded JSON push payloads from the personal ht_im/sock channel to {@link ImRealtimeEvent}s.
 * Pure — no networking, no mutable state. Mirrors {@link com.jilali.realtime.HtNotifyMapper}'s
 * shape for the sibling LiveHub channel, which carries the same kinds of notify_type pushes in
 * a different envelope: this channel has no "event" wrapper, and (confirmed via live capture)
 * notify_info carries no user_id at all for personal notify types — see {@link #mapNotify}.
 */
final class HtImNotifyMapper {

    private final long selfUserId;

    HtImNotifyMapper(long selfUserId) {
        this.selfUserId = selfUserId;
    }

    ImRealtimeEvent map(JsonNode root, Header h) {
        if (root.has("msg_type")) {
            return switch (root.path("msg_type").asText()) {
                case "text"              -> mapText(root, h);
                case "image"             -> mapImage(root, h);
                case "gift"              -> mapGift(root, h);
                case "introduction"      -> mapIntro(root, h);
                case "new_voice_visitor" -> mapProfileVisit(root);
                default                  -> null;
            };
        }
        if (root.has("notify_type")) return mapNotify(root);
        return null;
    }

    private ImRealtimeEvent mapText(JsonNode root, Header h) {
        String fromId = textOr(root, "from_id", String.valueOf(h.fromId()));
        JsonNode t = root.path("text");
        String text = t.isObject() ? textOr(t, "text", "") : t.asText("");
        long ts = root.path("ts").asLong(System.currentTimeMillis());
        return new ImRealtimeEvent.TextMessage(fromId, text, ts);
    }

    private ImRealtimeEvent mapImage(JsonNode root, Header h) {
        String fromId = textOr(root, "from_id", String.valueOf(h.fromId()));
        String url = root.path("image").path("url").asText("");
        if (url.isBlank()) url = textOr(root, "image_url", "");
        long ts = root.path("ts").asLong(System.currentTimeMillis());
        return new ImRealtimeEvent.ImageMessage(fromId, url, ts);
    }

    private ImRealtimeEvent mapGift(JsonNode root, Header h) {
        String fromId       = textOr(root, "from_id", String.valueOf(h.fromId()));
        String fromNickname = textOr(root, "from_nickname", textOr(root, "nickname", ""));
        long   giftId       = root.path("gift_id").asLong(0);
        int    count        = root.path("gift_number").asInt(1);
        return new ImRealtimeEvent.GiftMessage(fromId, fromNickname, giftId, count);
    }

    private ImRealtimeEvent mapIntro(JsonNode root, Header h) {
        String fromId       = textOr(root, "from_id", String.valueOf(h.fromId()));
        String fromNickname = textOr(root, "from_nickname", textOr(root, "nickname", ""));
        return new ImRealtimeEvent.IntroductionMessage(fromId, fromNickname);
    }

    private ImRealtimeEvent mapNotify(JsonNode root) {
        // Room share: has cname at the top level.
        if (root.has("cname")) {
            String cname        = textOr(root, "cname", "");
            String fromNickname = textOr(root, "from_nickname", textOr(root, "nickname", ""));
            String headUrl      = root.path("head_url").isNull() ? null : textOr(root, "head_url", null);
            if (root.has("count") || root.has("voice_count")) {
                int count = root.has("count")
                    ? root.path("count").asInt(0)
                    : root.path("voice_count").asInt(0);
                return new ImRealtimeEvent.VoiceRoomShared(fromNickname, cname, headUrl, count);
            }
            return new ImRealtimeEvent.LiveRoomShared(fromNickname, cname, headUrl);
        }

        // Personal notify_type pushes: notify_info carries no user_id — it's implicitly this
        // account, since the channel is already scoped to one identity.
        JsonNode info = root.path("notify_info");
        String selfId = String.valueOf(selfUserId);
        switch (root.path("notify_type").asText("")) {
            case "18":
                return new ImRealtimeEvent.StageInvite(textOr(info, "user_id", selfId), textOr(info, "cname", ""));
            case "48":
                return new ImRealtimeEvent.ModInvite(textOr(info, "user_id", selfId), textOr(info, "cname", ""));
            case "34":
                return new ImRealtimeEvent.ModAccepted(textOr(info, "user_id", selfId));
            case "35":
                return new ImRealtimeEvent.ModRemoved(textOr(info, "user_id", selfId));
            case "40":
                return new ImRealtimeEvent.ModUnmuted(textOr(info, "user_id", selfId));
            case "53":
                return new ImRealtimeEvent.Follow(textOr(info, "nickname", ""), info.path("status").asInt(0));
            default:
                break;
        }

        // Profile visit: has visitor_uid / visitor_user_id / visitor_id (older/alternate shape).
        for (String field : new String[]{"visitor_uid", "visitor_user_id", "visitor_id"}) {
            if (root.has(field)) {
                return new ImRealtimeEvent.ProfileVisit(textOr(root, field, ""));
            }
        }

        return null;
    }

    private ImRealtimeEvent mapProfileVisit(JsonNode root) {
        // scriptv2.js startwebsock(): msg_type === "new_voice_visitor" carries a top-level userId.
        String visitorId = textOr(root, "userId", textOr(root, "user_id", ""));
        return visitorId.isEmpty() ? null : new ImRealtimeEvent.ProfileVisit(visitorId);
    }

    private static String textOr(JsonNode node, String field, String fallback) {
        JsonNode n = node.get(field);
        return (n != null && !n.isNull()) ? n.asText() : fallback;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests "com.jilali.im.HtImNotifyMapperTest"`
Expected: PASS — 10 tests, 0 failures.

- [ ] **Step 5: Delete the old test file**

The old test reached into `HtImUpstreamConnector` via a relaxed-visibility `mapPushPayload`
method that Task 5 removes. Delete it now that its coverage has moved:

```bash
rm src/test/java/com/jilali/im/HtImUpstreamConnectorMappingTest.java
```

- [ ] **Step 6: Run the full test suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL. (`HtImUpstreamConnector` still has its own, now-duplicate,
`mapPushPayload`/`mapNotify`/etc. methods at this point — that's fine, Task 5 removes them.
Nothing currently calls the deleted test, and no other code references it.)

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/jilali/im/HtImNotifyMapper.java src/test/java/com/jilali/im/HtImNotifyMapperTest.java
git add src/test/java/com/jilali/im/HtImUpstreamConnectorMappingTest.java
git commit -m "feat(im): add HtImNotifyMapper, moving JSON-to-event mapping out of the connector"
```

---

### Task 5: Rewrite `HtImUpstreamConnector` — slim down + add reconnect

**Files:**
- Modify: `src/main/java/com/jilali/im/HtImUpstreamConnector.java` (full rewrite of the file's body; public/package API unchanged)

**Interfaces:**
- Consumes: `com.jilali.core.ws.ExponentialBackoff`, `SequentialSender`, `HeartbeatPump` (Task 1); `HtImFrameDecoder` + its `F2Push` sealed type + `OfflinePacket` record (Task 3); `HtImNotifyMapper` (Task 4).
- Produces: same external contract as before — `HtImUpstreamConnector(long userId, String jwt, String deviceId, String deviceModel, ObjectMapper om)`, `void attach(Consumer<ImRealtimeEvent> eventListener, Runnable disconnectListener)`, `CompletableFuture<Void> connect()`, `void close()`. `ImEventSource` (which constructs and calls this class) needs **no changes**.

This is the task where the god class actually shrinks. `connect()`'s returned future still
only reflects the *first* connection attempt (so `ImEventSource`'s existing `.exceptionally()`
handling is unaffected) — reconnection with backoff only kicks in for a connection that was
already established and then unexpectedly dropped, triggered from `Listener.onClose()`.

- [ ] **Step 1: Replace the entire contents of `HtImUpstreamConnector.java`**

```java
// src/main/java/com/jilali/im/HtImUpstreamConnector.java
package com.jilali.im;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.core.ws.ExponentialBackoff;
import com.jilali.core.ws.HeartbeatPump;
import com.jilali.core.ws.SequentialSender;
import com.jilali.crypto.ApkSignatureGenerator;
import com.jilali.im.HtImFrameDecoder.F2Push;
import com.jilali.im.dto.ImRealtimeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.jilali.im.HtImPacketFramer.*;

/**
 * Single binary WebSocket connection to HelloTalk's {@code ht_im/sock} upstream.
 * Sends the login packet on connect, keeps a 30-second heartbeat, decrypts 0xF2 push
 * packets with the QQTEA session key received in the 0xF1 login response, and maps them
 * to {@link ImRealtimeEvent}s for downstream subscribers.
 *
 * <p>Byte-level decoding lives in {@link HtImFrameDecoder}; JSON-to-event mapping lives in
 * {@link HtImNotifyMapper}. This class owns only the WebSocket lifecycle, reconnection, and
 * dispatch between them. An unexpected close (network drop, not our own {@link #close()})
 * triggers an internal reconnect loop with capped exponential backoff — {@link #connect()}'s
 * returned future only ever reflects the first attempt, so {@code ImEventSource}'s existing
 * failure handling for "upstream unreachable from the start" is unaffected. This reconnect
 * loop is the only one in play here — {@code ImEventSource} does not itself retry, so a
 * future change adding retry there too would stack two independent backoff loops.
 */
class HtImUpstreamConnector implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(HtImUpstreamConnector.class);
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final String IM_WS_URL = "wss://api-global.hellotalk8.com/ht_im/sock";
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(30);

    private final long userId;
    private final String jwt;
    private final String deviceId;
    private final String deviceModel;
    private final ObjectMapper om;
    private final HtImFrameDecoder decoder;
    private final HtImNotifyMapper notifyMapper;

    private final SequentialSender sender = new SequentialSender();
    private final HeartbeatPump heartbeat = new HeartbeatPump("im-hb");
    private final ExponentialBackoff backoff = new ExponentialBackoff(Duration.ofSeconds(1), Duration.ofSeconds(30));

    private volatile Consumer<ImRealtimeEvent> eventListener;
    private volatile Runnable disconnectListener;

    private volatile WebSocket ws;
    private volatile boolean connected;
    private volatile boolean intentionalClose;
    private volatile byte[] sessionKey;

    HtImUpstreamConnector(long userId, String jwt, String deviceId, String deviceModel, ObjectMapper om) {
        this.userId       = userId;
        this.jwt          = jwt;
        this.deviceId     = deviceId;
        this.deviceModel  = deviceModel;
        this.om           = om;
        this.decoder      = new HtImFrameDecoder(om);
        this.notifyMapper = new HtImNotifyMapper(userId);
    }

    void attach(Consumer<ImRealtimeEvent> eventListener, Runnable disconnectListener) {
        this.eventListener      = eventListener;
        this.disconnectListener = disconnectListener;
    }

    CompletableFuture<Void> connect() {
        this.intentionalClose = false;
        log.info("IM WS connecting uid={}", userId);
        return attemptConnect();
    }

    private CompletableFuture<Void> attemptConnect() {
        return HTTP_CLIENT.newWebSocketBuilder()
            .buildAsync(URI.create(IM_WS_URL + "?userid=" + userId), new Listener())
            .thenAccept(sock -> {
                this.ws        = sock;
                this.connected = true;
                backoff.reset();
                log.info("IM WS connected uid={}", userId);
                sendLoginPacket(sock);
                sock.request(1);
            });
    }

    private void reconnectInBackground() {
        if (intentionalClose) return;
        Duration delay = backoff.nextDelay();
        log.info("IM WS reconnecting uid={} in {}ms", userId, delay.toMillis());
        CompletableFuture.runAsync(() -> {
            if (intentionalClose) return;
            attemptConnect().exceptionally(ex -> {
                log.warn("IM WS reconnect attempt failed uid={}: {}", userId, ex.getMessage());
                reconnectInBackground();
                return null;
            });
        }, CompletableFuture.delayedExecutor(delay.toMillis(), TimeUnit.MILLISECONDS));
    }

    @Override
    public void close() {
        intentionalClose = true;
        connected = false;
        heartbeat.stop();
        sender.reset();
        WebSocket sock = ws;
        if (sock != null) {
            try { sock.sendClose(1000, "normal"); } catch (Exception ignored) {}
        }
    }

    // ── login ────────────────────────────────────────────────────────────────

    private void sendLoginPacket(WebSocket sock) {
        try {
            long ts = System.currentTimeMillis();
            String apkSig = ApkSignatureGenerator.generate(deviceId, ts);
            var payload = om.writeValueAsBytes(om.createObjectNode()
                .put("jwt",                  jwt)
                .put("mobile_operator",      "Orange")
                .put("operator_country",     "ma")
                .put("android_apk_signature", apkSig)
                .put("app_version",          "6.3.40(11126,google)")
                .put("background_reconnect",  0)
                .put("channel",              "com.hellotalk.core.app.NihaotalkApplication")
                .put("client_lang",          "English")
                .put("current_version",       394024)
                .put("device_detail",         deviceModel)
                .put("device_id",             deviceId)
                .put("is_version_update",     0)
                .put("net_type",              1)
                .put("os_lang",              "en")
                .put("os_version",           "11")
                .put("terminal_type",         1));
            sendBinary(buildPacket(userId, CMD_LOGIN, payload));
        } catch (Exception e) {
            log.error("IM: failed to build login packet: {}", e.getMessage());
            emit(new ImRealtimeEvent.Error("IM login build failed: " + e.getMessage()));
        }
    }

    // ── packet dispatch ──────────────────────────────────────────────────────

    private void handlePacket(byte[] data) {
        Header h = parseHeader(data);
        if (h == null) return;

        int payloadLen = Math.min(h.payloadLen(), Math.max(0, data.length - HEADER_LEN));

        switch (h.packetType()) {
            case PKT_RESPONSE -> handleF1(h, data, payloadLen);
            case PKT_PUSH     -> handleF2(h, data, payloadLen);
            case PKT_TYPING   -> handleTyping(h, data, payloadLen);
            default           -> log.trace("IM: unknown packet type 0x{}", Integer.toHexString(h.packetType()));
        }
    }

    private void handleF1(Header h, byte[] data, int payloadLen) {
        if (h.cmdId() == CMD_PONG) {
            log.trace("IM: heartbeat pong uid={}", userId);
            return;
        }

        Optional<JsonNode> root = decoder.decodeF1(data, payloadLen);
        root.ifPresent(json -> {
            if (h.cmdId() == CMD_OFFLINE_RESPONSE) {
                handleOfflineResponse(json);
            } else if (h.cmdId() == CMD_GROUP_RESPONSE) {
                handleGroupResponse(json);
            } else {
                handleLoginResponse(json);
            }
        });
    }

    private void handleLoginResponse(JsonNode root) {
        int status = root.path("status").asInt(0);
        if (status == 2) {
            log.warn("IM: account banned uid={}", userId);
            emit(new ImRealtimeEvent.AccountStatus("banned"));
            close();
            return;
        }
        if (status == 105) {
            log.warn("IM: session id mismatch uid={}", userId);
            emit(new ImRealtimeEvent.AccountStatus("session_mismatch"));
            close();
            return;
        }

        JsonNode data = root.path("data");
        if (data.has("session_key")) {
            String key = data.path("session_key").asText();
            this.sessionKey = key.getBytes(StandardCharsets.UTF_8);
            log.info("IM: session key captured uid={}", userId);
            emit(new ImRealtimeEvent.ConnectionState("connected"));
            heartbeat.start(HEARTBEAT_INTERVAL, this::sendPing);
            // Proactively request offline DMs — two passes matching old frontend onSessionReady
            sendOfflineSyncRequest(0, CMD_OFFLINE_SYNC,      0xF0);
            sendOfflineSyncRequest(0, CMD_OFFLINE_SYNC_PAGE, 0xF2);
        }
    }

    private void handleF2(Header h, byte[] data, int payloadLen) {
        // Always ACK first
        sendBinary(buildAck(data));

        F2Push push = decoder.decodeF2(data, payloadLen, sessionKey);
        dispatchPush(push, h);
    }

    /** @return true if this push resulted in an emitted {@link ImRealtimeEvent}. */
    private boolean dispatchPush(F2Push push, Header h) {
        switch (push) {
            case F2Push.Receipt r -> {
                emit(new ImRealtimeEvent.ReadReceipt(r.msgId()));
                return true;
            }
            case F2Push.Poke ignored -> {
                log.debug("IM: F2 0x08 poke uid={} — triggering offline sync", userId);
                sendOfflineSyncRequest(0, CMD_OFFLINE_SYNC, 0xF0);
                return false;
            }
            case F2Push.Json j -> {
                ImRealtimeEvent event = notifyMapper.map(j.root(), h);
                log.info("IM: F2 push uid={} notify_type={} msg_type={} mapped={} raw={}",
                    userId, j.root().path("notify_type").asText(null), j.root().path("msg_type").asText(null),
                    event != null ? event.getClass().getSimpleName() : "DROPPED", j.root());
                if (event != null) { emit(event); return true; }
                return false;
            }
            case F2Push.Unknown u -> {
                log.info("IM: F2 unknown first byte 0x{} uid={} len={} hex=[{}]",
                    Integer.toHexString(u.firstByte()), userId, u.bytes().length, toHex(u.bytes()));
                return false;
            }
            case F2Push.DecryptFailed ignored -> {
                log.warn("IM: F2 QQTEA decrypt failed uid={}", userId);
                return false;
            }
            case F2Push.Ignored ignored -> {
                return false;
            }
        }
    }

    private void handleTyping(Header h, byte[] data, int payloadLen) {
        if (h.cmdId() != CMD_TYPING) return;
        boolean isTyping = decoder.decodeTypingStatus(data, payloadLen, sessionKey, h.keyType());
        emit(new ImRealtimeEvent.TypingIndicator(String.valueOf(h.fromId()), isTyping));
    }

    // ── offline message sync ─────────────────────────────────────────────────

    private void sendOfflineSyncRequest(long lastId, int cmdId, int flag) {
        try {
            byte[] jsonBody = om.writeValueAsBytes(om.createObjectNode().put("last_id", lastId));
            byte[] compressed = deflate(jsonBody);
            sendBinary(buildPacket(userId, cmdId, flag, compressed));
            log.info("IM: sent offline sync cmdId={} flag=0x{} last_id={}", cmdId, Integer.toHexString(flag), lastId);
        } catch (Exception e) {
            log.warn("IM: failed to send offline sync request: {}", e.getMessage());
        }
    }

    private void handleOfflineResponse(JsonNode root) {
        log.info("IM: offline raw response uid={}: {}", userId, root);
        JsonNode data = root.path("data");
        JsonNode packetList = data.path("packet_list");
        if (!packetList.isArray()) {
            log.info("IM: offline response has no packet_list (code={})", root.path("code").asInt(-1));
            return;
        }

        int emitted = 0;
        for (JsonNode item : packetList) {
            String b64 = item.asText(null);
            if (b64 == null || b64.isEmpty()) continue;
            Optional<HtImFrameDecoder.OfflinePacket> offline = decoder.decodeOfflinePacket(b64, sessionKey);
            if (offline.isPresent() && dispatchPush(offline.get().body(), offline.get().header())) {
                emitted++;
            }
        }
        log.info("IM: offline response {} packets, emitted {} events uid={}", packetList.size(), emitted, userId);

        // Pagination: if the server returned items there may be more
        long nextLastId = data.path("last_id").asLong(0);
        if (packetList.size() > 0 && nextLastId > 0) {
            sendOfflineSyncRequest(nextLastId, CMD_OFFLINE_SYNC_PAGE, 0xF2);
        }
    }

    private void handleGroupResponse(JsonNode root) {
        JsonNode msgs = root.path("data").path("msgs");
        if (!msgs.isArray()) {
            log.info("IM: group sync response no msgs (code={})", root.path("code").asInt(-1));
            return;
        }
        int emitted = 0;
        for (JsonNode msg : msgs) {
            String senderId   = textOr(msg, "sender_id", "");
            String senderName = textOr(msg, "sender_name", senderId);
            String roomName   = textOr(msg, "room_name", textOr(msg, "cname", ""));
            String text       = msg.path("text").path("text").asText(
                                    msg.path("text").asText(""));
            if (!senderId.isEmpty()) {
                emit(new ImRealtimeEvent.GroupMessage(senderId, senderName, roomName, text));
                emitted++;
            }
        }
        log.info("IM: group sync response {} msgs, emitted {} uid={}", msgs.size(), emitted, userId);
    }

    // ── heartbeat / send ─────────────────────────────────────────────────────

    private void sendPing() {
        if (connected) sendBinary(buildHeartbeat(userId));
    }

    private void sendBinary(byte[] data) {
        WebSocket sock = this.ws;
        if (sock == null || !connected) return;
        ByteBuffer buf = ByteBuffer.wrap(data);
        sender.enqueue(() -> sock.sendBinary(buf, true),
            e -> log.warn("IM WS send failed uid={}: {}", userId, e.getMessage()));
    }

    private void emit(ImRealtimeEvent event) {
        Consumer<ImRealtimeEvent> l = eventListener;
        if (l != null) l.accept(event);
    }

    // ── utilities ────────────────────────────────────────────────────────────

    private static String textOr(JsonNode node, String field, String fallback) {
        JsonNode n = node.get(field);
        return (n != null && !n.isNull()) ? n.asText() : fallback;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        int maxBytes = Math.min(bytes.length, 64);
        for (int i = 0; i < maxBytes; i++) {
            hex.append(String.format("%02x", bytes[i] & 0xFF));
            if (i < maxBytes - 1) hex.append(' ');
        }
        return hex.toString();
    }

    // ── WebSocket.Listener ───────────────────────────────────────────────────

    private class Listener implements WebSocket.Listener {

        private final java.io.ByteArrayOutputStream binaryBuf = new java.io.ByteArrayOutputStream(4096);

        @Override
        public CompletionStage<?> onBinary(WebSocket ws, ByteBuffer data, boolean last) {
            byte[] chunk = new byte[data.remaining()];
            data.get(chunk);
            binaryBuf.write(chunk, 0, chunk.length);
            if (last) {
                byte[] frame = binaryBuf.toByteArray();
                binaryBuf.reset();
                handlePacket(frame);
            }
            ws.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            connected = false;
            heartbeat.stop();
            log.info("IM WS closed uid={} status={} reason={}", userId, statusCode, reason);
            if (intentionalClose) {
                Runnable l = disconnectListener;
                if (l != null) l.run();
            } else {
                reconnectInBackground();
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            log.warn("IM WS error uid={}: {}", userId, error.getMessage());
            emit(new ImRealtimeEvent.Error("IM upstream error: " + error.getMessage()));
        }
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run the full test suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL — `HtImNotifyMapperTest` (10 cases), `HtImFrameDecoderTest`
(10 cases), `ExponentialBackoffTest` (3 cases), plus every pre-existing test in the project,
all green.

- [ ] **Step 4: Manual review checklist against the original file**

Confirm each of these behaviors survived the rewrite (compare against the version at
`git show HEAD~4:src/main/java/com/jilali/im/HtImUpstreamConnector.java` if you want the
pre-refactor text side by side):

- Login packet field set and order unchanged (15 fields, same names/values).
- ACK is sent for every F2 frame before checking the session key, exactly as before.
- Offline sync is triggered from three places: after the session key arrives, on a 0x08 poke,
  and via pagination when `last_id > 0` — all three still present.
- Account-banned (`status == 2`) and session-mismatch (`status == 105`) still close the
  connection and emit `AccountStatus`.
- The heartbeat still fires every 30s only after login succeeds (`heartbeat.start(...)` is
  called from `handleLoginResponse`, not from `connect()`).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/jilali/im/HtImUpstreamConnector.java
git commit -m "refactor(im): slim HtImUpstreamConnector to WS lifecycle + dispatch, add reconnect with backoff"
```

---

### Task 6: `HtLiveHubUpstreamConnector` — safe frames + shared utilities + reconnect

**Files:**
- Modify: `src/main/java/com/jilali/realtime/HtLiveHubUpstreamConnector.java` (full rewrite; constructor signature changes)
- Modify: `src/main/java/com/jilali/realtime/RoomEventSource.java` (thread `ObjectMapper` through to the new constructor param)

**Interfaces:**
- Consumes: `com.jilali.core.ws.ExponentialBackoff`, `SequentialSender`, `HeartbeatPump` (Task 1); existing `HtNotifyMapper` (unchanged).
- Produces: `HtLiveHubUpstreamConnector(HtNotifyMapper mapper, ObjectMapper om)` (constructor signature changed — was `(HtNotifyMapper mapper)`), `attach(...)`, `connect(String userId, String cname, boolean isVisitor)`, `close()` — same method names/params otherwise.

- [ ] **Step 1: Replace the entire contents of `HtLiveHubUpstreamConnector.java`**

```java
// src/main/java/com/jilali/realtime/HtLiveHubUpstreamConnector.java
package com.jilali.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jilali.core.ws.ExponentialBackoff;
import com.jilali.core.ws.HeartbeatPump;
import com.jilali.core.ws.SequentialSender;
import com.jilali.realtime.dto.RoomRealtimeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * One LiveHub upstream WebSocket connection per room. An unexpected close (network drop, not
 * our own {@link #close()}) triggers an internal reconnect loop with capped exponential
 * backoff — {@link #connect} only reflects the first attempt in its returned future, so
 * {@code RoomEventSource}'s existing failure handling for "upstream unreachable from the
 * start" is unaffected. This reconnect loop is the only one in play here — {@code
 * RoomEventSource} does not itself retry, so a future change adding retry there too would
 * stack two independent backoff loops.
 */
public class HtLiveHubUpstreamConnector implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(HtLiveHubUpstreamConnector.class);
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final String LIVEHUB_WS_URL = "wss://uploadprocn.hellotalk8.com/livehub/ws/conn";

    private final HtNotifyMapper mapper;
    private final ObjectMapper om;
    private final SequentialSender sender = new SequentialSender();
    private final HeartbeatPump heartbeat = new HeartbeatPump("livehub-hb");
    private final ExponentialBackoff backoff = new ExponentialBackoff(Duration.ofSeconds(1), Duration.ofSeconds(30));

    private volatile Consumer<RoomRealtimeEvent> eventListener;
    private volatile Runnable disconnectListener;

    private volatile WebSocket ws;
    private volatile Session session;
    private volatile boolean intentionalClose;

    public HtLiveHubUpstreamConnector(HtNotifyMapper mapper, ObjectMapper om) {
        this.mapper = mapper;
        this.om = om;
    }

    public void attach(Consumer<RoomRealtimeEvent> eventListener, Runnable disconnectListener) {
        this.eventListener = eventListener;
        this.disconnectListener = disconnectListener;
    }

    public CompletableFuture<Void> connect(String userId, String cname, boolean isVisitor) {
        this.session = new Session(Long.parseLong(userId), cname, isVisitor, 60, false);
        this.intentionalClose = false;
        return attemptConnect();
    }

    private CompletableFuture<Void> attemptConnect() {
        Session s = session;
        log.debug("LiveHub WS connecting to: {}?user_id={}&cname={}&is_visitor={}",
            LIVEHUB_WS_URL, s.userId, s.cname, s.isVisitor);

        return HTTP_CLIENT.newWebSocketBuilder()
            .buildAsync(URI.create(LIVEHUB_WS_URL
                + "?user_id=" + s.userId + "&cname=" + s.cname + "&is_visitor=" + s.isVisitor),
                new Listener())
            .thenAccept(sock -> {
                this.ws = sock;
                session = withConnected(session, true);
                backoff.reset();
                log.info("LiveHub WS connected cname={}", session.cname);
                send(initFrame());
                sock.request(1);
            });
    }

    private void reconnectInBackground() {
        if (intentionalClose) return;
        Duration delay = backoff.nextDelay();
        log.info("LiveHub WS reconnecting cname={} in {}ms", session.cname, delay.toMillis());
        CompletableFuture.runAsync(() -> {
            if (intentionalClose) return;
            attemptConnect().exceptionally(ex -> {
                log.warn("LiveHub WS reconnect attempt failed cname={}: {}", session.cname, ex.getMessage());
                reconnectInBackground();
                return null;
            });
        }, CompletableFuture.delayedExecutor(delay.toMillis(), TimeUnit.MILLISECONDS));
    }

    @Override
    public void close() {
        intentionalClose = true;
        Session s = session;
        if (s == null) return;
        session = withConnected(s, false);
        heartbeat.stop();
        sender.reset();
        if (ws != null) {
            try { ws.sendClose(1000, "normal"); } catch (Exception ignored) {}
        }
    }

    private String initFrame() {
        return writeJson(om.createObjectNode()
            .put("user_id", session.userId)
            .put("cname", session.cname)
            .put("action", 1));
    }

    private String heartbeatFrame() {
        return writeJson(om.createObjectNode()
            .put("cname", session.cname)
            .put("user_id", session.userId)
            .put("action", 2)
            .put("is_visitor", session.isVisitor));
    }

    private String ackFrame(String msgId) {
        return writeJson(om.createObjectNode()
            .put("msg_id", msgId)
            .put("action", 3)
            .put("user_id", session.userId)
            .put("cname", session.cname)
            .put("is_visitor", session.isVisitor));
    }

    private String writeJson(ObjectNode node) {
        try {
            return om.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build LiveHub frame", e);
        }
    }

    private void handleFrame(String text) {
        log.trace("LiveHub RX cname={}: {}", session.cname, text);

        mapper.heartbeatSec(text)
            .ifPresentOrElse(hbSec -> {
                    session = withHeartbeatInterval(session, hbSec);
                    sendHeartbeat();
                    long delaySec = Math.max(1, hbSec - 5);
                    heartbeat.start(Duration.ofSeconds(delaySec), Duration.ofSeconds(hbSec), this::sendHeartbeat);
                },
                () -> {
                    if (mapper.isHeartbeatResponse(text)) {
                        // server ack of our heartbeat — the pump is already scheduled, nothing to do
                    } else {
                        mapper.msgId(text).ifPresent(this::sendAck);
                        mapper.map(text).ifPresent(event -> {
                            Consumer<RoomRealtimeEvent> l = eventListener;
                            if (l != null) l.accept(event);
                        });
                    }
                });
    }

    private void sendHeartbeat() {
        if (session.connected) send(heartbeatFrame());
    }

    private void sendAck(String msgId) {
        send(ackFrame(msgId));
    }

    private void send(String json) {
        WebSocket s = this.ws;
        if (s == null || !session.connected) return;
        sender.enqueue(() -> s.sendText(json, true),
            e -> log.warn("LiveHub WS send failed cname={}: {}", session.cname, e.getMessage()));
    }

    private static Session withConnected(Session s, boolean connected) {
        return new Session(s.userId, s.cname, s.isVisitor, s.heartbeatIntervalSec, connected);
    }

    private static Session withHeartbeatInterval(Session s, long hbSec) {
        return new Session(s.userId, s.cname, s.isVisitor, hbSec, s.connected);
    }

    private record Session(long userId, String cname, boolean isVisitor, long heartbeatIntervalSec, boolean connected) {}

    private class Listener implements WebSocket.Listener {

        private final StringBuilder textBuffer = new StringBuilder(512);

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            textBuffer.append(data);
            if (last) {
                String frame = textBuffer.toString();
                textBuffer.setLength(0);
                handleFrame(frame);
            }
            ws.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            session = withConnected(session, false);
            heartbeat.stop();
            log.info("LiveHub WS closed cname={} status={} reason={}", session.cname, statusCode, reason);
            if (intentionalClose) {
                Runnable listener = disconnectListener;
                if (listener != null) listener.run();
            } else {
                reconnectInBackground();
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            log.warn("LiveHub WS error cname={}: {}", session.cname, error.getMessage());
            Consumer<RoomRealtimeEvent> l = eventListener;
            if (l != null) {
                l.accept(new RoomRealtimeEvent.Error("LiveHub upstream error: " + error.getMessage()));
            }
        }
    }
}
```

- [ ] **Step 2: Update `RoomEventSource` to pass `ObjectMapper` through**

Current file (`src/main/java/com/jilali/realtime/RoomEventSource.java`) has this constructor:

```java
    public RoomEventSource(HtNotifyMapper mapper, JilaliProperties properties, ObjectMapper om) {
        this.mapper = mapper;
        this.connectorUserId = UidExtractor.uidAsString(properties.defaultAuthToken(), om);
        log.info("RoomEventSource: connector userId={}", connectorUserId);
    }
```

Add a field for `om` (it's currently a constructor parameter used once and discarded) and
store it. Change the field declarations block (currently):

```java
    private final HtNotifyMapper mapper;
    private final String connectorUserId;
```

to:

```java
    private final HtNotifyMapper mapper;
    private final ObjectMapper om;
    private final String connectorUserId;
```

Change the constructor to:

```java
    public RoomEventSource(HtNotifyMapper mapper, JilaliProperties properties, ObjectMapper om) {
        this.mapper = mapper;
        this.om = om;
        this.connectorUserId = UidExtractor.uidAsString(properties.defaultAuthToken(), om);
        log.info("RoomEventSource: connector userId={}", connectorUserId);
    }
```

Change the connector construction site inside `subscribe(String cname)` from:

```java
            HtLiveHubUpstreamConnector upstream = new HtLiveHubUpstreamConnector(mapper);
```

to:

```java
            HtLiveHubUpstreamConnector upstream = new HtLiveHubUpstreamConnector(mapper, om);
```

- [ ] **Step 3: Compile**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run the full test suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL — `HtNotifyMapperTest` and `RoomRealtimeEventTest` (untouched by
this task) still pass, confirming the `realtime` package's behavior is unchanged.

- [ ] **Step 5: Manual review checklist against the original file**

- `initFrame()` sends exactly `{user_id, cname, action}` — no `is_visitor` key (matches the
  original; only `heartbeatFrame`/`ackFrame` include it).
- `heartbeatFrame()` sends exactly `{cname, user_id, action, is_visitor}`.
- `ackFrame()` sends exactly `{msg_id, action, user_id, cname, is_visitor}`.
- Heartbeat still starts with `heartbeatIntervalSec - 5` as the *initial* delay and
  `heartbeatIntervalSec` as the *period* (not the same value for both, unlike the IM
  connector's fixed 30/30).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/jilali/realtime/HtLiveHubUpstreamConnector.java src/main/java/com/jilali/realtime/RoomEventSource.java
git commit -m "refactor(realtime): build LiveHub frames via ObjectMapper, add reconnect with backoff"
```

---

### Task 7: Full-repo verification

**Files:** none (verification only)

**Interfaces:** none — this task confirms everything from Tasks 1-6 integrates correctly.

- [ ] **Step 1: Clean build**

Run: `./gradlew clean build -x test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Full test suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL. Confirm the test report includes (in addition to every
pre-existing test class): `ExponentialBackoffTest` (3), `HtImFrameDecoderTest` (10),
`HtImNotifyMapperTest` (10) — and that `HtImUpstreamConnectorMappingTest` no longer exists.

```bash
find build/test-results/test -name "TEST-*.xml" | xargs grep -l "tests=" | xargs -I{} sh -c 'grep -o "name=\"[^\"]*\" tests=\"[0-9]*\" .*failures=\"[0-9]*\" errors=\"[0-9]*\"" {} | head -1'
```

Expected: every line shows `failures="0" errors="0"`.

- [ ] **Step 3: Confirm no stray references to removed/moved members**

```bash
grep -rn "mapPushPayload\|HtImUpstreamConnectorMappingTest" src/
```

Expected: no output (both were fully removed/renamed in Tasks 4-5).

- [ ] **Step 4: Line-count sanity check**

```bash
wc -l src/main/java/com/jilali/im/HtImUpstreamConnector.java
```

Expected: roughly 260-300 lines (down from 624) — confirms the god-class decomposition
actually reduced the file's size rather than just moving code around cosmetically.

- [ ] **Step 5: Commit the plan/spec docs if not already committed**

```bash
git status
```

If `docs/superpowers/plans/2026-07-01-realtime-hardening.md` or the spec doc show as
uncommitted, commit them now (they should already be committed from the brainstorming phase —
this step is a safety check).

- [ ] **Step 6: Final note for manual (non-automatable) verification**

Neither connector's live reconnect behavior can be exercised by a unit test (it requires a
real or fake upstream WebSocket server to actually drop mid-session). After deploying, watch
the logs for `"IM WS reconnecting"` / `"LiveHub WS reconnecting"` lines if an upstream drop
ever occurs, to confirm the backoff loop is firing as designed. No action needed now — this
is a note for whoever deploys this change, not a task step.
