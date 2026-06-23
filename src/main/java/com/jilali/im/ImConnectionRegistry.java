package com.jilali.im;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.auth.HelloTalkTokenPoolRepository;
import com.jilali.core.JilaliProperties;
import com.jilali.im.dto.ImEvent;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One {@code ht_im/sock} connection per logged-in JilaliTalk user — keyed by JilaliTalk user
 * id (the platform account, see {@code com.jilali.auth}), not by room cname. Mirrors
 * {@code com.jilali.realtime.RoomRealtimeRegistry}'s lifecycle pattern (create on first
 * subscriber, tear down on last) at the per-user rather than per-room granularity.
 *
 * <p>Requires the user to have a real HelloTalk JWT assigned in the token pool — a user with
 * none simply gets no connection (and the controller surfaces that as "unavailable", not an
 * error), matching the rest of the platform-auth design's "decoupled until a token exists"
 * stance.
 */
@Singleton
public class ImConnectionRegistry {

    private static final Logger log = LoggerFactory.getLogger(ImConnectionRegistry.class);
    private static final int BACKPRESSURE_LIMIT = 256;

    private final HtImMessageMapper mapper;
    private final ObjectMapper om;
    private final HelloTalkTokenPoolRepository tokenPool;
    private final JilaliProperties properties;
    private final Map<Long, UserContext> contexts = new ConcurrentHashMap<>();

    public ImConnectionRegistry(HtImMessageMapper mapper, ObjectMapper om,
                                 HelloTalkTokenPoolRepository tokenPool, JilaliProperties properties) {
        this.mapper = mapper;
        this.om = om;
        this.tokenPool = tokenPool;
        this.properties = properties;
    }

    /** @return empty if this user has no HelloTalk JWT assigned yet. */
    public Optional<Flux<ImEvent>> subscribe(long jilaliUserId) {
        Optional<String> jwt = tokenPool.findJwtForUser(jilaliUserId);
        if (jwt.isEmpty()) return Optional.empty();
        long helloTalkUid = uidFromJwt(jwt.get());

        UserContext ctx = contexts.computeIfAbsent(jilaliUserId, id -> new UserContext(id, helloTalkUid));
        ctx.subscriberCount.incrementAndGet();
        if (ctx.connector == null) {
            connect(ctx, jwt.get());
        }
        return Optional.of(ctx.sink.asFlux());
    }

    /** Decodes the {@code uid} claim from the JWT payload — same approach as
     *  {@code RoomRealtimeRegistry.resolveUserId}, duplicated rather than shared since the
     *  two registries are otherwise independent (per-room LiveHub vs. per-user ht_im/sock). */
    private static long uidFromJwt(String jwt) {
        String[] parts = jwt.split("\\.");
        if (parts.length < 2) throw new IllegalArgumentException("Not a JWT: missing payload segment");
        byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
        try {
            var node = new ObjectMapper().readTree(payloadBytes);
            return node.path("uid").asLong();
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not decode 'uid' from JWT", e);
        }
    }

    public void unsubscribe(long jilaliUserId) {
        UserContext ctx = contexts.get(jilaliUserId);
        if (ctx == null) return;
        if (ctx.subscriberCount.decrementAndGet() <= 0) {
            contexts.remove(jilaliUserId);
            HtImSocketConnector c = ctx.connector;
            if (c != null) c.close();
            log.info("ht_im/sock: last subscriber left jilaliUserId={} — connection closed", jilaliUserId);
        }
    }

    private void connect(UserContext ctx, String jwt) {
        HtImSocketConnector connector = new HtImSocketConnector(mapper, om);
        connector.attach(
            event -> ctx.sink.tryEmitNext(event),
            () -> log.info("ht_im/sock: disconnected jilaliUserId={}", ctx.jilaliUserId));
        ctx.connector = connector;

        connector.connect(ctx.helloTalkUid, jwt, properties.deviceId())
            .whenComplete((ignored, ex) -> {
                if (ex != null) {
                    log.warn("ht_im/sock: connect failed jilaliUserId={}: {}", ctx.jilaliUserId, ex.getMessage());
                    ctx.sink.tryEmitNext(new ImEvent.ConnectionState("disconnected"));
                    return;
                }
                ctx.sink.tryEmitNext(new ImEvent.ConnectionState("connecting"));
            });
    }

    private static final class UserContext {
        final long jilaliUserId;
        final long helloTalkUid;
        final Sinks.Many<ImEvent> sink = Sinks.many().multicast().onBackpressureBuffer(BACKPRESSURE_LIMIT);
        final AtomicInteger subscriberCount = new AtomicInteger(0);
        volatile HtImSocketConnector connector;

        UserContext(long jilaliUserId, long helloTalkUid) {
            this.jilaliUserId = jilaliUserId;
            this.helloTalkUid = helloTalkUid;
        }
    }
}
