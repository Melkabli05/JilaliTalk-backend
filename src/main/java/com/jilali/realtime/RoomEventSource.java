package com.jilali.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.core.AuthTokenHolder;
import com.jilali.core.UidExtractor;
import com.jilali.realtime.dto.RoomCcRealtimeEvent;
import com.jilali.realtime.dto.RoomRealtimeEvent;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Pub-sub bridge: browser tabs subscribe per room, first subscriber opens the LiveHub upstream, last leaves closes it. */
@Singleton
public class RoomEventSource {

    private static final Logger log = LoggerFactory.getLogger(RoomEventSource.class);

    private final HtNotifyMapper mapper;
    private final HtCcNotifyMapper ccMapper;
    private final ObjectMapper om;
    private final AuthTokenHolder authToken;
    private final Map<String, HtLiveHubUpstreamConnector> connectors = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();
    private final Map<String, Sinks.Many<RoomRealtimeEvent>> sinks = new ConcurrentHashMap<>();
    /** Per-room CC (AI captioning) sink — separate from the room sink so subscribers can opt in
     *  to subtitles without subscribing to the full room event stream. */
    private final Map<String, Sinks.Many<RoomCcRealtimeEvent>> ccSinks = new ConcurrentHashMap<>();
    /** Tracks how many CC subscribers are active per room, so we know when the upstream's CC
     *  listener wiring can be torn down. */
    private final Map<String, AtomicInteger> ccCounts = new ConcurrentHashMap<>();
    /** Per-room audience roster revision — incremented on every event that changes the roster so clients can skip unnecessary refetches. */
    private final Map<String, AtomicInteger> audienceRevisions = new ConcurrentHashMap<>();
    /** Last connection-state per room, so a subscriber joining after the upstream is already
     *  connected (e.g. a second browser tab on the same room) gets the current state
     *  immediately instead of waiting for a transition that already happened. */
    private final Map<String, RoomRealtimeEvent.ConnectionState> lastConnectionState = new ConcurrentHashMap<>();

    public RoomEventSource(HtNotifyMapper mapper, HtCcNotifyMapper ccMapper, AuthTokenHolder authToken, ObjectMapper om) {
        this.mapper = mapper;
        this.ccMapper = ccMapper;
        this.om = om;
        this.authToken = authToken;
    }

    public Flux<RoomRealtimeEvent> subscribe(String cname) {
        boolean first = counts.computeIfAbsent(cname, k -> new AtomicInteger(0))
            .incrementAndGet() == 1;

        if (first) {
            HtLiveHubUpstreamConnector upstream = new HtLiveHubUpstreamConnector(mapper, ccMapper, om);
            connectors.put(cname, upstream);

            Sinks.Many<RoomRealtimeEvent> sink = sinkFor(cname);
            upstream.attach(
                event -> emitAndTrackState(cname, event),
                () -> {
                    log.info("RoomEventSource: upstream disconnected for cname='{}'", cname);
                    sink.tryEmitComplete();
                    Sinks.Many<RoomCcRealtimeEvent> ccSink = ccSinks.remove(cname);
                    if (ccSink != null) ccSink.tryEmitComplete();
                }
            );
            // Wire CC channel too — the connector fans CC-shaped frames here. We attach it
            // unconditionally because the discriminator is the frame shape, not subscription
            // state; the ccSinks map may have no subscribers and the emit is a no-op then.
            upstream.attachCc(event -> {
                Sinks.Many<RoomCcRealtimeEvent> ccSink = ccSinks.get(cname);
                if (ccSink != null) ccSink.tryEmitNext(event);
            });

            emitAndTrackState(cname, new RoomRealtimeEvent.ConnectionState("connecting"));

            String connectorUserId = UidExtractor.uidAsString(authToken.get(), om);
            log.info("RoomEventSource: connector userId={} cname='{}'", connectorUserId, cname);
            upstream.connect(connectorUserId, cname, true)
                .exceptionally(ex -> {
                    log.error("RoomEventSource: upstream connection failed for cname='{}': {}", cname, ex.getMessage());
                    sink.tryEmitNext(new RoomRealtimeEvent.Error("Upstream connection failed: " + ex.getMessage()));
                    sink.tryEmitComplete();
                    return null;
                });

            log.info("RoomEventSource: opened upstream for cname='{}'", cname);
            return sink.asFlux();
        }

        RoomRealtimeEvent.ConnectionState state = lastConnectionState.getOrDefault(
            cname, new RoomRealtimeEvent.ConnectionState("disconnected"));
        return Flux.concat(Flux.just(state), sinkFor(cname).asFlux());
    }

    /**
     * Subscribe to the AI-captioning / subtitle stream for a room. CC frames ride the same
     * upstream WebSocket as room events but are routed to a separate sink; subscribers here
     * do NOT see room-channel events, and vice versa.
     *
     * <p>Note: this does not itself open the upstream — the upstream is opened by the first
     * room subscriber (subscribe). If nobody is subscribed to the room channel yet, the CC
     * sink stays cold and the flux completes immediately. Callers that want both streams
     * must call subscribe(cname) first.
     */
    public Flux<RoomCcRealtimeEvent> subscribeCc(String cname) {
        ccCounts.computeIfAbsent(cname, k -> new AtomicInteger(0)).incrementAndGet();
        Sinks.Many<RoomCcRealtimeEvent> sink = ccSinks.computeIfAbsent(cname,
            k -> Sinks.many().multicast().directBestEffort());
        return sink.asFlux();
    }

    /** Drop a CC subscriber. Idempotent — safe to call without a prior {@link #subscribeCc}. */
    public void unsubscribeCc(String cname) {
        AtomicInteger count = ccCounts.get(cname);
        if (count == null) return;
        int remaining = count.decrementAndGet();
        if (remaining <= 0) {
            ccCounts.remove(cname, count);
            // Only drop the sink if the room channel has no subscribers either — otherwise the
            // room subscriber's upstream keeps producing CC events and we want them delivered.
            if (counts.get(cname) == null || counts.get(cname).get() <= 0) {
                Sinks.Many<RoomCcRealtimeEvent> sink = ccSinks.remove(cname);
                if (sink != null) sink.tryEmitComplete();
            }
        }
    }

    private void emitAndTrackState(String cname, RoomRealtimeEvent event) {
        if (event instanceof RoomRealtimeEvent.ConnectionState cs) {
            lastConnectionState.put(cname, cs);
        }
        emitWithRevisionBump(cname, event);
    }

    public void unsubscribe(String cname) {
        AtomicInteger count = counts.get(cname);
        if (count == null) return;
        int remaining = count.decrementAndGet();
        if (remaining <= 0) {
            counts.remove(cname, count);
            log.info("RoomEventSource: last subscriber left cname='{}', closing upstream", cname);
            HtLiveHubUpstreamConnector upstream = connectors.remove(cname);
            if (upstream != null) upstream.close();
            Sinks.Many<RoomRealtimeEvent> sink = sinks.remove(cname);
            if (sink != null) sink.tryEmitComplete();
            // Safe to drop: clients always reset their local revision to -1 on join, so a
            // revisited room simply starts counting from 0 again instead of leaking forever.
            audienceRevisions.remove(cname);
            lastConnectionState.remove(cname);
            // Tear down the CC sink too if no CC subscribers remain — closing the upstream
            // already stops frames from arriving, but we want subscribers to see completion.
            Sinks.Many<RoomCcRealtimeEvent> ccSink = ccSinks.remove(cname);
            if (ccSink != null) ccSink.tryEmitComplete();
            ccCounts.remove(cname);
        }
    }

    private Sinks.Many<RoomRealtimeEvent> sinkFor(String cname) {
        return sinks.computeIfAbsent(cname, k ->
            Sinks.many().multicast().directBestEffort()
        );
    }

    /**
     * Injects an event into a room's live stream without any upstream involvement — used by
     * {@code GhostPublishController} to synthesize a stage_join/stage_quit for a ghost publisher.
     * Upstream (HelloTalk's livehub) has no concept of an audience member publishing without
     * joining the stage roster, so this is the BFF's own mediation: every currently-connected
     * browser tab subscribed to {@code cname} receives the event exactly as if it came from
     * upstream. A room with no active subscribers (nobody has opened {@code /ws/ht/{cname}}
     * yet) silently drops the event — there's nobody to notify and no upstream connection to
     * piggyback on, so it's a no-op rather than an error.
     */
    public void emitSynthetic(String cname, RoomRealtimeEvent event) {
        if (counts.get(cname) == null || counts.get(cname).get() <= 0) {
            log.debug("RoomEventSource: emitSynthetic no-op, no subscribers for cname='{}'", cname);
            return;
        }
        emitWithRevisionBump(cname, event);
    }

    /**
     * Returns the current audience roster revision for a room. The revision increments on every
     * event that changes the audience roster (user_join, user_quit, stage_join, room_kick).
     * Returns 0 if the room has no active subscribers.
     */
    public int audienceRevision(String cname) {
        AtomicInteger rev = audienceRevisions.get(cname);
        return rev == null ? 0 : rev.get();
    }

    private void bumpAudienceRevision(String cname) {
        audienceRevisions.computeIfAbsent(cname, k -> new AtomicInteger(0)).incrementAndGet();
    }

    private void emitWithRevisionBump(String cname, RoomRealtimeEvent event) {
        // Only audience-roster-affecting variants bump the revision counter (the
        // /audience-reconcile drift-correct poll uses this to decide whether a
        // refetch is needed). Sealed-pattern switch means the compiler enforces
        // exhaustiveness and we don't have to update this site every time a new
        // RoomRealtimeEvent variant is added — the switch falls through silently,
        // which is exactly the desired behavior (new variants simply don't bump
        // the revision unless explicitly listed).
        if (audienceAffecting(event)) {
            bumpAudienceRevision(cname);
        }
        sinkFor(cname).tryEmitNext(event);
    }

    /**
     * Pure predicate that names the four audience-roster-affecting variants, extracted
     * so a sealed-pattern switch can enforce exhaustiveness on the {@code RoomRealtimeEvent}
     * hierarchy in one place.
     */
    private static boolean audienceAffecting(RoomRealtimeEvent event) {
        return switch (event) {
            case RoomRealtimeEvent.UserJoin ignored -> true;
            case RoomRealtimeEvent.UserQuit ignored -> true;
            case RoomRealtimeEvent.StageJoin ignored -> true;
            case RoomRealtimeEvent.RoomKick ignored -> true;
            default -> false;
        };
    }
}
