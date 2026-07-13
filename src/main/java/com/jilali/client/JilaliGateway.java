package com.jilali.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.core.JilaliException;
import com.jilali.core.JilaliProperties;
import com.jilali.core.JwtUtil;
import com.jilali.crypto.Cc2018Cipher;
import com.jilali.crypto.Curve25519SessionGenerator;
import com.jilali.crypto.EncbinUtil;
import com.jilali.room.AgoraTokenCipher;
import com.jilali.stage.dto.DeviceControlRequest;
import com.jilali.stage.dto.KickRequest;
import com.jilali.stage.dto.PublisherTokenResponse;
import com.jilali.stage.dto.RaiseHandApprovalRequest;
import com.jilali.stage.dto.RaiseHandRequest;
import com.jilali.stage.dto.StageActionRequest;
import com.jilali.stage.dto.StageInviteApprovalRequest;
import com.jilali.stage.dto.StageInviteRequest;
import com.jilali.stage.dto.StageListResponse;
import com.jilali.user.dto.RoomUserProfileResponse;
import com.jilali.user.dto.UserInfo;
import com.jilali.user.dto.UserInfoRequest;
import com.jilali.user.dto.UserInfoResponse;
import com.jilali.vip.dto.UseVipExperienceCardRequest;
import com.jilali.vip.dto.VipExperienceCard;
import com.jilali.vip.dto.VipExperienceCardRecordsRequest;
import io.micronaut.cache.annotation.Cacheable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.context.ServerRequestContext;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The seam between our application and Jilali. Only the methods that perform real work beyond
 * envelope unwrapping live here:
 * <ul>
 *   <li>{@code userInfo} — encrypted ht/encbin call with Curve25519 key exchange</li>
 *   <li>{@code roomUserProfile} — bin/cc2018-decoded call, the only source of per-target-user follow status</li>
 *   <li>{@code publisherToken} — AES decryption of the upstream Agora token</li>
 *   <li>{@code claimVipTrial} — finds and activates an unused 24h VIP-trial card perk</li>
 * </ul>
 * All other endpoints are plain pass-throughs and are called directly from controllers via
 * {@link JilaliClient} + {@link JilaliResponses#unwrap}.
 */
@Singleton
public class JilaliGateway {

    private static final Logger log = LoggerFactory.getLogger(JilaliGateway.class);

    private static final ObjectMapper ROOM_PROFILE_MAPPER = new ObjectMapper()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    /** scene_id/feature_id identifying the 24h VIP trial perk on a VIP experience card. */
    private static final String VIP_TRIAL_SCENE_ID = "30000";
    private static final String VIP_TRIAL_FEATURE_ID = "00001";
    private static final String VIP_TRIAL_CARD_VERSION = "v1";

    private final JilaliClient client;
    private final VipExperienceCardClient vipClient;
    private final HttpClient httpClient;
    private final JilaliProperties properties;

    public JilaliGateway(JilaliClient client, VipExperienceCardClient vipClient,
                          @Client("jlhub") HttpClient jlhubClient, JilaliProperties properties) {
        this.client = client;
        this.vipClient = vipClient;
        this.httpClient = jlhubClient;
        this.properties = properties;
    }

    /** Exposes the raw client for callers that need direct envelope access. */
    public JilaliClient client() {
        return client;
    }

    // ---- Stage convenience helpers (unwrap + throw, so callers stay clean) ----

    public StageListResponse stageList(int busiType, String cname) {
        return JilaliResponses.unwrap(client.stageList(busiType, cname));
    }

    public void stageJoin(StageActionRequest body) {
        JilaliResponses.unwrap(client.stageJoin(body));
    }

    public void stageQuit(StageActionRequest body) {
        JilaliResponses.unwrap(client.stageQuit(body));
    }

    public void raiseHand(RaiseHandRequest body) {
        JilaliResponses.unwrap(client.raiseHand(body));
    }

    public void stageKick(KickRequest body) {
        JilaliResponses.unwrap(client.stageKick(body));
    }

    public void raiseHandApproval(RaiseHandApprovalRequest body) {
        JilaliResponses.unwrap(client.raiseHandApproval(body));
    }

    public void stageInvite(StageInviteRequest body) {
        JilaliResponses.unwrap(client.stageInvite(body));
    }

    public void stageInviteApproval(StageInviteApprovalRequest body) {
        JilaliResponses.unwrap(client.stageInviteApproval(body));
    }

    public void deviceControl(DeviceControlRequest body) {
        JilaliResponses.unwrap(client.deviceControl(body));
    }

    /**
     * Finds an unused 24h-VIP-trial perk on a card the current user owns and activates it. This
     * is the "Claim" action behind the watch-limit dialog (mirrors old_hellotalk's
     * {@code getVip24h()}) — called explicitly by the user, not silently, since it's the frontend
     * that decides whether to claim, join as a ghost listener, or leave (see
     * {@code BaseRoomStore.handleWatchLimitReached}).
     *
     * @return {@code true} if a trial was found and claimed, {@code false} if the user has none left.
     */
    public boolean claimVipTrial() {
        Long userId = currentUserId();
        if (userId == null) {
            return false;
        }
        var records = JilaliResponses.unwrap(
            vipClient.queryUserRecord(new VipExperienceCardRecordsRequest(userId, true, true)));
        if (records == null) {
            return false;
        }
        var card = records.cards().stream()
            .filter(JilaliGateway::ownsUnusedTrial)
            .findFirst();
        if (card.isEmpty()) {
            return false;
        }
        JilaliResponses.unwrap(vipClient.useCard(new UseVipExperienceCardRequest(
            card.get().id(), VIP_TRIAL_FEATURE_ID, VIP_TRIAL_SCENE_ID, userId, VIP_TRIAL_CARD_VERSION)));
        log.info("Auto-claimed 24h VIP trial for user {}", userId);
        return true;
    }

    static boolean ownsUnusedTrial(VipExperienceCard card) {
        var features = card.detail() == null ? null : card.detail().cardFeatures();
        boolean hasTrial = features != null && features.stream()
            .anyMatch(f -> VIP_TRIAL_SCENE_ID.equals(f.sceneId()) && VIP_TRIAL_FEATURE_ID.equals(f.featureId()));
        if (!hasTrial) {
            return false;
        }
        var used = card.usedFeatures();
        return used == null || used.stream()
            .noneMatch(u -> VIP_TRIAL_SCENE_ID.equals(u.sceneId()) && VIP_TRIAL_FEATURE_ID.equals(u.featureId()));
    }

    /**
     * Resolves the calling user's id from the JWT actually in effect for upstream calls — the
     * inbound {@code Authorization} header if the frontend sent one, else the same default token
     * {@code DefaultHeadersClientFilter} falls back to. The frontend doesn't track its own user id
     * before a room is joined (it only learns it from {@code voice_room_info}'s response), and
     * doesn't send an {@code x-ht-uid} header at all yet — so the JWT already carrying this
     * request's identity is the only reliable source. Public so callers like
     * {@link com.jilali.user.ProfileBundleService} can tell whether a requested profile is the
     * viewer's own (to decide which extra calls to fan out).
     */
    public Long currentUserId() {
        var inbound = ServerRequestContext.currentRequest().orElse(null);
        String header = inbound == null ? null : inbound.getHeaders().get("authorization");
        String token = header != null && !header.isBlank() ? header : "Bearer " + properties.defaultAuthToken();
        return JwtUtil.uidFromBearer(token);
    }

    /**
     * Fetches HelloTalk user info via the encrypted ht/encbin endpoint.
     * Uses a direct HTTP call to set per-request ht/encbin headers correctly.
     * <p>
     * Cached by {@code userId} alone (see {@code user-info} cache in application.yml) — the
     * returned profile doesn't depend on which caller asked, only on whose profile it is. Every
     * room roster, comment author, and notification avatar look this same handful of user IDs
     * up repeatedly, and each miss costs a full Curve25519 handshake + AES round-trip, so a short
     * TTL removes most of that cost without serving badly stale profiles.
     *
     * @param userId the HelloTalk user ID to look up
     * @return clean UserInfo record
     */
    @Cacheable("user-info")
    public UserInfo userInfo(long userId) {
        var session = Curve25519SessionGenerator.generate(properties.serverPubKeyHex());
        var request = UserInfoRequest.forUser(userId);
        byte[] encryptedPayload = EncbinUtil.encrypt(request, session.sharedSecret());

        String token = properties.defaultAuthToken();
        String deviceId = properties.deviceId();
        // x-ht-uid must identify who is making this call (the shared service account the JWT
        // authenticates as), never the profile being looked up, and — critically — it must match
        // the uid embedded in whichever token is actually attached below. This call always uses
        // the fixed service-account token (not the caller's own forwarded JWT — see the
        // @Cacheable doc above: the cache is keyed by userId alone, so the upstream identity used
        // to fetch it must stay constant regardless of who's asking). currentUserId() prioritizes
        // the caller's *own* inbound JWT when present, which mismatches this fixed token's uid —
        // that mismatch is why every userInfo() call upstream was rejected with BAD_REQUEST.
        // Deriving the uid from `token` itself keeps the header and the token self-consistent.
        Long callerUid = JwtUtil.uidFromBearer("Bearer " + token);

        HttpRequest<byte[]> httpRequest = HttpRequest.POST("/profile/v2/userinfo", encryptedPayload)
            .header("ht-content-type", "ht/encbin")
            .header("Content-Type", "application/octet-stream")
            .header("Accept", "*/*")
            .header("Accept-Charset", "UTF-8,*;q=0.5")
            .header("Accept-Encoding", "gzip")
            .header("Accept-Language", "en-MA;q=1.0, fr-MA;q=0.9, ar-MA;q=0.8")
            .header("Cache-Control", "no-cache")
            .header("User-Agent", "android;6.1.0;SM-A908N;11;" + (callerUid != null ? callerUid : userId))
            .header("x-ht-version", "6.1.0")
            .header("x-ht-timezone", ".00")
            .header("x-ht-tzid", "Africa/Casablanca")
            .header("x-ht-ui-mode", "1")
            .header("x-ht-channel", "google")
            .header("x-ht-device", "SM-A908N#720X1280#360#360#320#20.4")
            .header("x-ht-os-version", "11")
            .header("x-ht-os", "android")
            .header("x-ht-lang", "English")
            .header("x-ht-uid", callerUid != null ? String.valueOf(callerUid) : "")
            .header("x-ht-did", deviceId)
            .header("x-ht-build", "135")
            .header("x-ht-token", "Bearer " + token)
            .header("Authorization", "Bearer " + token)
            .header("x-ht-pub", session.headerValue());

        BlockingHttpClient blockingClient = httpClient.toBlocking();
        byte[] responseBytes;
        try {
            responseBytes = blockingClient.retrieve(httpRequest, byte[].class);
        } catch (HttpClientResponseException e) {
            String body = e.getResponse().getBody(String.class).orElse("<no body>");
            log.error("userInfo upstream error: status={}, body={}", e.getStatus(), body);
            throw new JilaliException(1, "Upstream userinfo failed: " + e.getStatus(), HttpStatus.BAD_GATEWAY);
        }
        if (responseBytes == null || responseBytes.length == 0) {
            throw new JilaliException(1, "Empty userinfo response", HttpStatus.BAD_GATEWAY);
        }
        UserInfoResponse raw = EncbinUtil.decrypt(responseBytes, session.sharedSecret(), UserInfoResponse.class);
        return raw.toUserInfo();
    }

    /**
     * Room-scoped, per-target-user profile lookup ({@code GET livehub/user/profile}) — this is
     * the one place the upstream API exposes the viewer's follow relation to an arbitrary user
     * ({@code data.follow_stat.status}); {@link #userInfo} (the general {@code /profile/v2/userinfo}
     * endpoint) never returns it. No request-side encryption or key exchange is needed (it's a
     * plain GET), only response decoding via {@link Cc2018Cipher#decode}.
     */
    public RoomUserProfileResponse roomUserProfile(long userId, String cname, int busiType) {
        byte[] encoded = client.userProfile(busiType, cname, userId);
        if (encoded == null || encoded.length == 0) {
            throw new JilaliException(1, "Empty room user-profile response", HttpStatus.BAD_GATEWAY);
        }
        byte[] json;
        try {
            json = Cc2018Cipher.decode(encoded);
        } catch (RuntimeException e) {
            throw new JilaliException(1, "Room user-profile decode failed: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
        try {
            return ROOM_PROFILE_MAPPER.readValue(json, RoomUserProfileResponse.class);
        } catch (java.io.IOException e) {
            throw new JilaliException(1, "Room user-profile deserialization failed: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    /**
     * Decrypts the upstream Agora publisher token for {@code cname}.
     * LiveHub returns it AES-encrypted like the join token, so it goes through
     * {@link AgoraTokenCipher#decrypt(String, byte[])}.
     */
    public PublisherTokenResponse publisherToken(String cname, byte[] agoraCipherKey) {
        PublisherTokenResponse upstream = JilaliResponses.unwrap(client.publisherRtcToken(cname));
        String token = upstream != null ? upstream.token() : null;
        if (token == null || token.isBlank()) {
            throw new JilaliException(1, "Upstream returned null publisher token for " + cname, HttpStatus.BAD_GATEWAY);
        }
        return new PublisherTokenResponse(AgoraTokenCipher.decrypt(token, agoraCipherKey));
    }
}
