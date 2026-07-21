package com.jilali.roomcontext.infrastructure.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.jilali.core.AuthTokenHolder;
import com.jilali.core.JilaliException;
import com.jilali.core.JilaliProperties;
import com.jilali.core.JwtUtil;
import com.jilali.crypto.Cc2018Cipher;
import com.jilali.crypto.Curve25519SessionGenerator;
import com.jilali.crypto.EncbinUtil;
import com.jilali.roomcontext.infrastructure.dto.user.RoomUserProfileResponse;
import com.jilali.roomcontext.infrastructure.dto.user.UserInfo;
import com.jilali.roomcontext.infrastructure.dto.user.UserInfoRequest;
import com.jilali.roomcontext.infrastructure.dto.user.UserInfoResponse;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Dedicated caller for the two non-declarative upstream calls this bounded context needs:
 *  the Curve25519-encrypted {@code POST /profile/v2/userinfo} (ht/encbin wire format) and the
 *  cc2018-encoded {@code GET /livehub/user/profile}. Both need raw header/byte-level control
 *  that a declarative @Client interface can't express, so - like the legacy
 *  client.JilaliGateway they replace - they use an imperative {@code @Client("jlhub") HttpClient}
 *  directly. Zero dependency on the legacy client.JilaliGateway/client.JilaliClient god
 *  interface: this is this bounded context's own independent implementation. */
@Singleton
public class UserProfileEncryptedClient {

    private static final Logger log = LoggerFactory.getLogger(UserProfileEncryptedClient.class);
    private static final ObjectMapper ROOM_PROFILE_MAPPER = new ObjectMapper()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final HttpClient httpClient;
    private final JilaliProperties properties;
    private final AuthTokenHolder authToken;
    private final UserJilaliClient userClient;

    public UserProfileEncryptedClient(@Client("jlhub") HttpClient httpClient, JilaliProperties properties,
                                       AuthTokenHolder authToken, UserJilaliClient userClient) {
        this.httpClient = httpClient;
        this.properties = properties;
        this.authToken = authToken;
        this.userClient = userClient;
    }

    public UserInfo fetchUserInfo(long userId) {
        var session = Curve25519SessionGenerator.generate(properties.serverPubKeyHex());
        var request = UserInfoRequest.forUser(userId);
        byte[] encryptedPayload = EncbinUtil.encrypt(request, session.sharedSecret());

        String token = authToken.get();
        String deviceId = properties.deviceId();
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

        BlockingHttpClient blocking = httpClient.toBlocking();
        byte[] responseBytes;
        try {
            responseBytes = blocking.retrieve(httpRequest, byte[].class);
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

    public RoomUserProfileResponse fetchRoomUserProfile(long userId, String cname, int busiType) {
        byte[] encoded = userClient.userProfile(busiType, cname, userId);
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
}
