package com.jilali.im;

import com.jilali.client.JilaliGateway;
import com.jilali.im.dto.ImRealtimeEvent;
import com.jilali.user.dto.UserInfo;
import com.jilali.user.dto.UserInfoResponse;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Resolves user-identity fields (nickname, avatar) on IM realtime events whose raw upstream
 * payload omits them — most importantly {@link ImRealtimeEvent.ProfileVisit}, where HelloTalk's
 * {@code new_voice_visitor} push carries only the visitor's user id. Without this enrichment the
 * frontend would render "169335562 visited your profile" instead of a real name.
 *
 * <p>Backed by {@link JilaliGateway#userInfo(long)}, which is {@code @Cacheable("user-info")} —
 * warm hits are sub-millisecond in-process Caffeine lookups (no upstream round-trip), and the
 * bounded-elastic scheduler keeps the one per cold-uid Curve25519 call from blocking the IM
 * socket's I/O thread. Each event type only enriches when its identity fields are empty
 * (already-populated events pass through with {@code Mono.just(event)} and no work).
 */
@Singleton
public class ImEventEnricher {

    private static final Logger log = LoggerFactory.getLogger(ImEventEnricher.class);

    private final JilaliGateway gateway;

    public ImEventEnricher(JilaliGateway gateway) {
        this.gateway = gateway;
    }

    /** Returns the same event with nickname/headUrl filled in where the raw upstream payload omitted them. */
    public Mono<ImRealtimeEvent> enrich(ImRealtimeEvent event) {
        return switch (event) {
            case ImRealtimeEvent.ProfileVisit pv         -> enrichProfileVisit(pv);
            case ImRealtimeEvent.Follow f                -> enrichFollow(f);
            case ImRealtimeEvent.GiftMessage g           -> enrichGift(g);
            case ImRealtimeEvent.IntroductionMessage i   -> enrichIntroduction(i);
            default                                       -> Mono.just(event);
        };
    }

    private Mono<ImRealtimeEvent> enrichProfileVisit(ImRealtimeEvent.ProfileVisit pv) {
        long uid = parseUid(pv.visitorUserId());
        if (uid <= 0 || isFilled(pv.nickname(), pv.headUrl())) return Mono.just(pv);
        return resolveAsync(uid).map(info -> new ImRealtimeEvent.ProfileVisit(
            pv.visitorUserId(),
            firstFilled(pv.nickname(), info.nickname()),
            firstFilled(pv.headUrl(), headUrlOf(info))
        ));
    }

    private Mono<ImRealtimeEvent> enrichFollow(ImRealtimeEvent.Follow f) {
        long uid = parseUid(f.userId());
        if (uid <= 0 || isFilled(f.nickname(), f.headUrl())) return Mono.just(f);
        return resolveAsync(uid).map(info -> new ImRealtimeEvent.Follow(
            f.userId(),
            firstFilled(f.nickname(), info.nickname()),
            firstFilled(f.headUrl(), headUrlOf(info)),
            f.status()
        ));
    }

    private Mono<ImRealtimeEvent> enrichGift(ImRealtimeEvent.GiftMessage g) {
        long uid = parseUid(g.fromUserId());
        if (uid <= 0 || isFilled(g.fromNickname(), g.fromHeadUrl())) return Mono.just(g);
        return resolveAsync(uid).map(info -> new ImRealtimeEvent.GiftMessage(
            g.fromUserId(),
            firstFilled(g.fromNickname(), info.nickname()),
            firstFilled(g.fromHeadUrl(), headUrlOf(info)),
            g.giftId(),
            g.count()
        ));
    }

    private Mono<ImRealtimeEvent> enrichIntroduction(ImRealtimeEvent.IntroductionMessage i) {
        long uid = parseUid(i.fromUserId());
        if (uid <= 0 || isFilled(i.fromNickname(), i.fromHeadUrl())) return Mono.just(i);
        return resolveAsync(uid).map(info -> new ImRealtimeEvent.IntroductionMessage(
            i.fromUserId(),
            firstFilled(i.fromNickname(), info.nickname()),
            firstFilled(i.fromHeadUrl(), headUrlOf(info))
        ));
    }

    /** Caffeine lookups are in-process and non-blocking. Cold misses pay one upstream round-trip —
     *  bounded-elastic keeps that off the IM I/O thread that called us. */
    private Mono<UserInfo> resolveAsync(long userId) {
        return Mono.fromCallable(() -> gateway.userInfo(userId))
            .subscribeOn(Schedulers.boundedElastic())
            .onErrorResume(ex -> {
                // Partial enrichment is acceptable — frontend falls back to "Someone" / no avatar
                // rather than dropping the notification entirely.
                log.warn("ImEventEnricher: userInfo({}) failed: {}", userId, ex.getMessage());
                return Mono.empty();
            });
    }

    private static boolean isFilled(String nickname, String headUrl) {
        return (nickname != null && !nickname.isBlank())
            || (headUrl != null && !headUrl.isBlank());
    }

    private static String firstFilled(String fromEvent, String fromUser) {
        if (fromEvent != null && !fromEvent.isBlank()) return fromEvent;
        return fromUser != null ? fromUser : "";
    }

    private static String headUrlOf(UserInfo info) {
        UserInfoResponse.UserInfoItem d = info.details();
        return d != null && d.base() != null ? d.base().headUrl() : null;
    }

    private static long parseUid(String s) {
        if (s == null || s.isBlank()) return 0;
        try { return Long.parseLong(s.trim()); } catch (NumberFormatException e) { return 0; }
    }
}