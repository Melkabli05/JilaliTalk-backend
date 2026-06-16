package com.jilali.ht.im;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.core.JilaliProperties;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.*;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Bridges HelloTalk's binary IM WebSocket to Angular clients as normalized JSON.
 *
 * <p>Path: {@code /ws/ht-im?jwt={jwt}}
 *
 * <p>Protocol (from {@code connectwebsock.js}):
 * <ol>
 *   <li>On open: send login packet ({@code cmdId 0x1025}) with APK signature</li>
 *   <li>Server replies ({@code 0xF1}) with session_key → start QQTEA decryption of future 0xF2</li>
 *   <li>Send heartbeat ({@code cmdId 0x9001}) every 30 seconds</li>
 *   <li>For every {@code 0xF2} push: ACK it ({@code 0xF3}), decrypt, decode, forward as JSON</li>
 *   <li>For {@code 0xF1 cmdId=16454}: offline message batch; auto-request next page if {@code last_id} set</li>
 * </ol>
 *
 * <p>One upstream connection per Angular WebSocket session (one per browser tab).
 * Thread-safe.
 */
@Singleton
@ServerWebSocket("/ws/ht-im")
@ExecuteOn(TaskExecutors.BLOCKING)
public class HtImBridgeWebSocket {

    private static final Logger log = LoggerFactory.getLogger(HtImBridgeWebSocket.class);

    private static final String HT_IM_BASE_URL = "wss://api-global.hellotalk8.com/ht_im/sock";

    // Shared across all upstream instances: HttpClient is thread-safe and designed to be reused.
    private static final java.net.http.HttpClient HTTP_CLIENT = java.net.http.HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private final HtImDecoder decoder;
    private final ObjectMapper om;
    private final JilaliProperties properties;

    private final Map<String, HtImUpstream> upstreams = new ConcurrentHashMap<>();

    public HtImBridgeWebSocket(HtImDecoder decoder, ObjectMapper om, JilaliProperties properties) {
        this.decoder    = decoder;
        this.om         = om;
        this.properties = properties;
    }

    // ---- Frontend-facing WebSocket lifecycle --------------------------------

    @OnOpen
    public void onOpen(@QueryValue String jwt, WebSocketSession session) {
        String userId = extractUid(om, jwt);
        if (userId == null) {
            log.warn("IM bridge: could not extract uid from JWT — rejecting session");
            try { session.close(); } catch (Exception ignored) {}
            return;
        }

        var upstream = new HtImUpstream(
            HT_IM_BASE_URL + "?userid=" + userId,
            userId, jwt,
            properties.deviceModel(), properties.deviceId(),
            properties.htImOperator(), properties.htImCountry(),
            decoder,
            event -> forwardToSession(session, event));
        upstreams.put(session.getId(), upstream);
        upstream.connect();

        log.info("IM bridge: session opened userId='{}'", userId);
    }

    @OnClose
    public void onClose(WebSocketSession session) {
        HtImUpstream us = upstreams.remove(session.getId());
        if (us != null) us.disconnect("frontend disconnected");
        log.info("IM bridge: session closed id='{}'", session.getId());
    }

    @OnError
    public void onError(WebSocketSession session, Throwable t) {
        log.error("IM bridge: error session='{}': {}", session.getId(), t.getMessage());
    }

    // ---- Helpers ------------------------------------------------------------

    private void forwardToSession(WebSocketSession session, HtImEvent event) {
        if (!session.isOpen()) return;
        try {
            session.sendSync(om.writeValueAsString(event));
        } catch (Exception e) {
            log.warn("IM bridge: forward failed: {}", e.getMessage());
        }
    }

    private static String extractUid(ObjectMapper om, String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) return null;
            String payload = new String(
                Base64.getUrlDecoder().decode(padBase64(parts[1])), StandardCharsets.UTF_8);
            return om.readTree(payload).path("uid").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    // Base64url strings from JWT are unpadded; Java's decoder requires padding.
    private static String padBase64(String s) {
        return switch (s.length() % 4) {
            case 2  -> s + "==";
            case 3  -> s + "=";
            default -> s;
        };
    }

    // ---- Per-session upstream connection ------------------------------------

    private static class HtImUpstream {
        private static final Logger log = LoggerFactory.getLogger(HtImUpstream.class);
        private static final int MAX_RECONNECT = 3;
        private static final long HEARTBEAT_MS  = 30_000;

        private final String url;
        private final String userId;
        private final String jwt;
        private final String deviceModel;
        private final String deviceId;
        private final String operator;
        private final String country;
        private final HtImDecoder decoder;
        private final Consumer<HtImEvent> onEvent;

        private final AtomicInteger reconnectCount = new AtomicInteger(0);
        private final ScheduledExecutorService scheduler;

        private volatile byte[] sessionKey = null;
        private volatile boolean connected  = false;
        private volatile boolean stopped    = false;
        private volatile java.net.http.WebSocket ws;
        private volatile ScheduledFuture<?> heartbeatFuture;

        HtImUpstream(
                String url, String userId, String jwt,
                String deviceModel, String deviceId,
                String operator, String country,
                HtImDecoder decoder,
                Consumer<HtImEvent> onEvent) {
            this.url         = url;
            this.userId      = userId;
            this.jwt         = jwt;
            this.deviceModel = deviceModel;
            this.deviceId    = deviceId;
            this.operator    = operator;
            this.country     = country;
            this.decoder     = decoder;
            this.onEvent     = onEvent;
            // Virtual threads are fine for scheduled tasks; they unmount during the sleep.
            this.scheduler   = Executors.newScheduledThreadPool(
                1, Thread.ofVirtual().name("ht-im-hb-" + userId).factory());
        }

        void connect() {
            if (stopped) return;
            try {
                long uid = Long.parseLong(userId);
                this.ws = HTTP_CLIENT
                    .newWebSocketBuilder()
                    .buildAsync(URI.create(url), new Listener(uid))
                    .join();
            } catch (Exception e) {
                log.error("HT IM upstream connect failed ({}): {}", userId, e.getMessage());
                scheduleReconnect();
            }
        }

        void disconnect(String reason) {
            stopped = true;
            connected = false;
            cancelHeartbeat();
            scheduler.shutdownNow();
            try {
                java.net.http.WebSocket s = this.ws;
                if (s != null) s.sendClose(1000, reason);
            } catch (Exception ignored) {}
        }

        private void sendBinary(byte[] data) {
            java.net.http.WebSocket s = this.ws;
            if (s == null || !connected) return;
            try {
                s.sendBinary(ByteBuffer.wrap(data), true).join();
            } catch (Exception e) {
                log.warn("HT IM send failed ({}): {}", userId, e.getMessage());
            }
        }

        private void scheduleHeartbeat() {
            cancelHeartbeat();
            long uid = Long.parseLong(userId);
            heartbeatFuture = scheduler.scheduleAtFixedRate(() -> {
                if (!connected || stopped) return;
                sendBinary(HtImPacket.buildHeartbeat(uid));
                log.trace("HT IM: heartbeat sent ({})", userId);
            }, HEARTBEAT_MS, HEARTBEAT_MS, TimeUnit.MILLISECONDS);
        }

        private void cancelHeartbeat() {
            ScheduledFuture<?> f = heartbeatFuture;
            if (f != null) f.cancel(false);
        }

        private void scheduleReconnect() {
            if (stopped || reconnectCount.incrementAndGet() > MAX_RECONNECT) {
                log.warn("HT IM: max reconnects reached ({})", userId);
                onEvent.accept(new HtImEvent.Error("Connection failed after " + MAX_RECONNECT + " attempts"));
                return;
            }
            long delayMs = Math.min(1_000L << reconnectCount.get(), 30_000L);
            log.info("HT IM: reconnecting {} in {}ms (attempt {})", userId, delayMs, reconnectCount.get());
            Thread.ofVirtual().name("ht-im-reconnect-" + userId).start(() -> {
                try { Thread.sleep(delayMs); } catch (InterruptedException ignored) {}
                if (!stopped) {
                    sessionKey = null;
                    connect();
                }
            });
        }

        // ---- java.net.http.WebSocket listener -------------------------------

        private class Listener implements java.net.http.WebSocket.Listener {
            private final long uid;
            private final java.io.ByteArrayOutputStream binaryBuffer = new java.io.ByteArrayOutputStream();

            Listener(long uid) { this.uid = uid; }

            @Override
            public void onOpen(java.net.http.WebSocket ws) {
                connected = true;
                reconnectCount.set(0);
                log.info("HT IM upstream connected ({})", userId);
                ws.request(1);
                try {
                    String apkSig    = ApkSig.generate(deviceId, System.currentTimeMillis());
                    byte[] loginJson = buildLoginJson(apkSig).getBytes(StandardCharsets.UTF_8);
                    sendBinary(HtImPacket.buildLogin(uid, loginJson));
                    log.debug("HT IM: login sent ({})", userId);
                } catch (Exception e) {
                    log.error("HT IM: login failed: {}", e.getMessage());
                }
                scheduleHeartbeat();
            }

            @Override
            public CompletableFuture<?> onBinary(
                    java.net.http.WebSocket ws, ByteBuffer data, boolean last) {
                byte[] chunk = new byte[data.remaining()];
                data.get(chunk);
                binaryBuffer.write(chunk, 0, chunk.length);
                ws.request(1);

                if (!last) return CompletableFuture.<Void>completedFuture(null);

                byte[] frame = binaryBuffer.toByteArray();
                binaryBuffer.reset();
                handleFrame(frame);
                return CompletableFuture.<Void>completedFuture(null);
            }

            @Override
            public CompletableFuture<?> onClose(
                    java.net.http.WebSocket ws, int statusCode, String reason) {
                connected = false;
                log.info("HT IM upstream closed {} '{}' ({})", statusCode, reason, userId);
                cancelHeartbeat();
                if (!stopped) scheduleReconnect();
                return CompletableFuture.<Void>completedFuture(null);
            }

            @Override
            public void onError(java.net.http.WebSocket ws, Throwable error) {
                connected = false;
                log.error("HT IM upstream error ({}): {}", userId, error.getMessage());
                cancelHeartbeat();
                if (!stopped) scheduleReconnect();
            }

            private void handleFrame(byte[] raw) {
                // ACK every 0xF2 push immediately, before decoding
                HtImPacket peek = HtImPacket.parse(raw);
                if (peek != null && peek.flag() == 0xF2) {
                    byte[] ack = HtImPacket.buildAck(raw);
                    if (ack != null) sendBinary(ack);
                }

                decoder.decode(raw, sessionKey).ifPresent(event -> {
                    if (event instanceof HtImEvent.SessionReady sr) {
                        // Session key is the UTF-8 bytes of the key string (JS: new TextEncoder().encode(...))
                        sessionKey = sr.sessionKey().getBytes(StandardCharsets.UTF_8);
                        log.info("HT IM: session ready userId='{}' sessionId='{}'", userId, sr.sessionId());
                    }

                    if (event instanceof HtImEvent.OfflineMessages om && om.lastId() > 0) {
                        sendBinary(HtImPacket.buildOfflineSyncTrigger(uid, om.lastId(), 16453));
                        log.debug("HT IM: offline sync continuation (last_id={})", om.lastId());
                    }

                    onEvent.accept(event);
                });
            }

            private String buildLoginJson(String apkSig) {
                return """
                    {"jwt":"%s","mobile_operator":"%s","operator_country":"%s",\
                    "android_apk_signature":"%s","app_version":"6.3.40(11126,google)",\
                    "background_reconnect":0,"channel":"com.hellotalk.core.app.NihaotalkApplication",\
                    "client_lang":"English","current_version":394024,"device_detail":"%s",\
                    "device_id":"%s","is_version_update":0,"net_type":1,"os_lang":"en",\
                    "os_version":"11","terminal_type":1}""".formatted(
                    esc(jwt), esc(operator), esc(country),
                    esc(apkSig), esc(deviceModel), esc(deviceId));
            }

            private static String esc(String s) {
                return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
            }
        }
    }
}
