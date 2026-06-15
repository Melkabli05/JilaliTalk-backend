package com.jilali.client;

import com.jilali.core.JilaliException;
import com.jilali.core.JilaliProperties;
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
import com.jilali.user.dto.UserInfo;
import com.jilali.user.dto.UserInfoRequest;
import com.jilali.user.dto.UserInfoResponse;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * The seam between our application and Jilali. Only the two methods that perform real work
 * beyond envelope unwrapping live here:
 * <ul>
 *   <li>{@code userInfo} — encrypted ht/encbin call with Curve25519 key exchange</li>
 *   <li>{@code publisherToken} — AES decryption of the upstream Agora token</li>
 * </ul>
 * All other endpoints are plain pass-throughs and are called directly from controllers via
 * {@link JilaliClient} + {@link JilaliResponses#unwrap}.
 */
@Singleton
public class JilaliGateway {

    private static final Logger log = LoggerFactory.getLogger(JilaliGateway.class);

    private final JilaliClient client;
    private final HttpClient httpClient;
    private final JilaliProperties properties;

    public JilaliGateway(JilaliClient client, @Client("jlhub") HttpClient jlhubClient, JilaliProperties properties) {
        this.client = client;
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
     * Fetches HelloTalk user info via the encrypted ht/encbin endpoint.
     * Uses a direct HTTP call to set per-request ht/encbin headers correctly.
     *
     * @param userId the HelloTalk user ID to look up
     * @return clean UserInfo record
     */
    public UserInfo userInfo(long userId) {
        var session = Curve25519SessionGenerator.generate(properties.serverPubKeyHex());
        var request = UserInfoRequest.forUser(userId);
        byte[] encryptedPayload = EncbinUtil.encrypt(request, session.sharedSecret());

        String token = properties.defaultAuthToken();
        String deviceId = properties.deviceId();

        HttpRequest<byte[]> httpRequest = HttpRequest.POST("/profile/v2/userinfo", encryptedPayload)
            .header("ht-content-type", "ht/encbin")
            .header("Content-Type", "application/octet-stream")
            .header("Accept", "*/*")
            .header("Accept-Charset", "UTF-8,*;q=0.5")
            .header("Accept-Encoding", "gzip")
            .header("Accept-Language", "en-MA;q=1.0, fr-MA;q=0.9, ar-MA;q=0.8")
            .header("Cache-Control", "no-cache")
            .header("User-Agent", "android;6.1.0;SM-A908N;11;" + userId)
            .header("x-ht-version", "6.1.0")
            .header("x-ht-timezone", ".00")
            .header("x-ht-tzid", "Africa/Casablanca")
            .header("x-ht-ui-mode", "1")
            .header("x-ht-channel", "google")
            .header("x-ht-device", "SM-A908N#720X1280#360#360#320#20.4")
            .header("x-ht-os-version", "11")
            .header("x-ht-os", "android")
            .header("x-ht-lang", "English")
            .header("x-ht-uid", String.valueOf(userId))
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
            log.error("userInfo upstream error: status={}", e.getStatus());
            throw new JilaliException(1, "Upstream userinfo failed: " + e.getStatus(), HttpStatus.BAD_GATEWAY);
        }
        if (responseBytes == null || responseBytes.length == 0) {
            throw new JilaliException(1, "Empty userinfo response", HttpStatus.BAD_GATEWAY);
        }
        UserInfoResponse raw = EncbinUtil.decrypt(responseBytes, session.sharedSecret(), UserInfoResponse.class);
        return raw.toUserInfo();
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
