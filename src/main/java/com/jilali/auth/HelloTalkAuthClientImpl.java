package com.jilali.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.auth.dto.upstream.EmailLoginRequest;
import com.jilali.auth.dto.upstream.EmailPreLoginRequest;
import com.jilali.auth.dto.upstream.EmailPreLoginResponse;
import com.jilali.auth.dto.upstream.HelloTalkEnvelope;
import com.jilali.auth.dto.upstream.LoginResponse;
import com.jilali.auth.dto.upstream.NicknameCheckUpstreamRequest;
import com.jilali.auth.dto.upstream.RegPrepareRequest;
import com.jilali.auth.dto.upstream.SendEmailCodeUpstreamRequest;
import com.jilali.auth.dto.upstream.SignCheckRequest;
import com.jilali.auth.dto.upstream.SignCheckResponse;
import com.jilali.core.JilaliException;
import com.jilali.core.JilaliProperties;
import com.jilali.crypto.ApkSignatureGenerator;
import com.jilali.crypto.Cc2018Cipher;
import com.jilali.crypto.Curve25519SessionGenerator;
import com.jilali.crypto.EncbinUtil;
import com.jilali.crypto.HtntKeyUtil;
import com.jilali.crypto.Md5Util;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Real implementation of {@link HelloTalkAuthClient}: hand-built HTTP calls (not a declarative
 * {@code @Client} interface — these endpoints need per-call {@code bin/cc2018}/{@code ht/encbin}
 * body encoding and custom headers, the same situation {@code JilaliGateway.userInfo()}/
 * {@code aiTranslate()} are already in) against HelloTalk's {@code /user_register_center/**}
 * and {@code /user_info_updater/**} auth microservice.
 * <p>
 * This BFF has no real Android device to read telemetry from, so the device-fingerprint fields
 * every request needs (os_lang, net_type, jail_break, ...) are static persona constants below —
 * the same simplification {@code DefaultHeadersClientFilter} already makes for the iOS persona
 * used by every other endpoint in this app.
 */
@Singleton
public final class HelloTalkAuthClientImpl implements HelloTalkAuthClient {

    private static final Logger log = LoggerFactory.getLogger(HelloTalkAuthClientImpl.class);

    /**
     * {@code NON_NULL} inclusion is required, not cosmetic: the real app serializes these
     * bodies with Gson, which omits null fields by default (unlike Jackson, which emits literal
     * {@code null}). A request with an explicit {@code "email_verify_code":null} was rejected
     * by upstream with a plain (non-cc2018) {@code {"status":100,"msg":"invalid params"}} —
     * confirmed live, not theorized — until this was added.
     */
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    /** Android device-fingerprint persona — see class docs. */
    private static final String OS_TYPE_ANDROID = "android";
    private static final String OS_VERSION = "14";
    private static final String OS_LANG = "en";
    private static final String CLIENT_LANG = "en";
    private static final String APPSTORE_COUNTRY = "MA";
    private static final String CHANNEL = "google";
    private static final int NET_TYPE_WIFI = 1;
    private static final int LOGIN_TYPE_EMAIL = 1;
    private static final int ANDROID_OS_TYPE = 1;
    /** No real SIM to read from — real captured traffic confirms the app sends the literal
     *  placeholder {@code "--"} in this case, not an empty string. */
    private static final String NO_SIM_PLACEHOLDER = "--";

    /**
     * iOS persona for the {@code /v3/login} step only — every real captured
     * {@code /v3/login} request in this project's reference traffic (`hellotalsniffer`) is from
     * this exact device/build combination, which got past all structural validation (reached
     * the anti-fraud stage, {@code status:126 "need behavior validate"} — not a generic
     * {@code status:100} rejection). Reused verbatim rather than invented, since there is no
     * real Android `/v3/login` traffic anywhere in this project to verify an Android persona
     * against, and this build's own {@code android_apk_signature} was never confirmed live.
     */
    private static final int IOS_OS_TYPE = 0;
    private static final String IOS_HEADER_VERSION = "6.3.40";
    private static final String IOS_LOGIN_CLIENT_VERSION = "6.3.40(93)";
    private static final int IOS_CLIENT_VERSION_NUM = 394024;
    private static final String IOS_OS_VERSION = "26.4.2";
    private static final String IOS_DEVICE_MODEL = "iPhone13,2";
    private static final String IOS_BUILD = "93";
    private static final String IOS_CHANNEL = "AppStore";

    /**
     * {@code behavior_validate} confirmed live to be a presence check, not a cryptographic one:
     * a real login with this field omitted got {@code status:126 "need behavior validate"}; the
     * identical request with this field set to an arbitrary non-empty string succeeded outright
     * (real JWT, real profile back). Upstream almost certainly still risk-scores the session
     * server-side using whatever real signals it has (IP, device_id history, etc.) — this value
     * only satisfies "a value was supplied," it is not a forged device-attestation token, and
     * real NetEase Yidun tokens (when this BFF ever has one, e.g. relayed from a real client)
     * should be used here instead.
     */
    private static final String BEHAVIOR_VALIDATE_PLACEHOLDER = "jilalibff-no-sdk-available";

    private final HttpClient httpClient;
    private final JilaliProperties properties;

    public HelloTalkAuthClientImpl(@Client("jlhub") HttpClient httpClient, JilaliProperties properties) {
        this.httpClient = httpClient;
        this.properties = properties;
    }

    @Override
    public Optional<LoginResponse> login(String email, String password) {
        String deviceId = properties.deviceId();
        Optional<EmailPreLoginResponse> preLogin = preLogin(email, deviceId);
        if (preLogin.isEmpty()) {
            return Optional.empty();
        }
        return performLogin(preLogin.get(), password, deviceId);
    }

    private Optional<EmailPreLoginResponse> preLogin(String email, String deviceId) {
        var request = EmailPreLoginRequest.of(email, deviceId, ApkSignatureGenerator.VERSION_NAME);
        byte[] response = cc2018Exchange("/user_register_center/v3/pre_login", request, "pre_login", this::androidHeaders)
            .orElse(null);
        if (response == null) {
            return Optional.empty();
        }
        return readEnvelope(response, EmailPreLoginResponse.class, "pre_login");
    }

    private Optional<LoginResponse> performLogin(EmailPreLoginResponse preLogin, String password, String deviceId) {
        long ts = System.currentTimeMillis();
        String passwd = Md5Util.emailPasswordHash(password, preLogin.cnonce(), preLogin.nonce());
        // sign's client_version component is the *same* string as the client_version JSON field
        // ("6.3.40(93)", with build suffix) — confirmed by tracing rd/a.smali's register flow:
        // the "k" field (client_version, from TeaUtils.getVI()) is assigned to v2, and v2 is
        // never reassigned before being reused as the sign StringBuilder's client_version
        // component. An earlier version of this method used the bare header version instead —
        // wrong, and very possibly the reason every prior login attempt got a generic
        // "invalid request parameter" rather than reaching the anti-fraud stage.
        String sign = Md5Util.loginSignature(IOS_LOGIN_CLIENT_VERSION, deviceId, LOGIN_TYPE_EMAIL, ts);
        var accountLogin = new EmailLoginRequest.AccountLogin(preLogin.userId(), passwd, null);
        var request = new EmailLoginRequest(
            NO_SIM_PLACEHOLDER, NO_SIM_PLACEHOLDER,
            LOGIN_TYPE_EMAIL, "log in", IOS_OS_TYPE,
            ts,
            null,
            deviceId,
            IOS_LOGIN_CLIENT_VERSION,
            IOS_CLIENT_VERSION_NUM,
            IOS_OS_VERSION, "en", "en",
            IOS_DEVICE_MODEL,
            APPSTORE_COUNTRY,
            sign,
            "", 0, NET_TYPE_WIFI, 0, BEHAVIOR_VALIDATE_PLACEHOLDER, "",
            accountLogin);

        String uid = String.valueOf(preLogin.userId());
        byte[] response = cc2018Exchange("/user_register_center/v3/login", request, "login",
            req -> iosLoginHeaders(req, uid)).orElse(null);
        if (response == null) {
            return Optional.empty();
        }
        Optional<LoginResponse> parsed = readEnvelope(response, LoginResponse.class, "login");
        return parsed.filter(r -> r.userInfo() != null && r.userInfo().jwt() != null && !r.userInfo().jwt().isBlank());
    }

    @Override
    public void regPrepare() {
        try {
            var request = new RegPrepareRequest(properties.deviceId(), "");
            encbinPost("/user_register_center/v3/reg/prepare", request);
        } catch (RuntimeException e) {
            log.warn("reg/prepare failed (best-effort, continuing signup): {}", e.getMessage());
        }
    }

    @Override
    public void sendEmailCode(String email) {
        encbinPost("/user_register_center/v3/send_email_code", SendEmailCodeUpstreamRequest.forSignup(email));
    }

    @Override
    public void checkNickname(String nickname) {
        encbinPost("/user_register_center/v3/reg/profile_check", new NicknameCheckUpstreamRequest(nickname));
    }

    @Override
    public Optional<SignCheckResponse> signupCheck(String email, String password, String emailVerifyCode) {
        String deviceId = properties.deviceId();
        long t = System.currentTimeMillis();
        String htntkey = HtntKeyUtil.compute(deviceId, LOGIN_TYPE_EMAIL, t);
        var request = SignCheckRequest.forEmailSignup(
            email, password, emailVerifyCode,
            ApkSignatureGenerator.VERSION_NAME, CLIENT_LANG, deviceId,
            t, htntkey, NO_SIM_PLACEHOLDER, NO_SIM_PLACEHOLDER);

        var session = Curve25519SessionGenerator.generate(properties.serverPubKeyHex());
        byte[] encrypted = EncbinUtil.encrypt(request, session.sharedSecret());
        byte[] response = encbinExchange("/user_register_center/v3/check", encrypted, session.headerValue(), "signupCheck")
            .orElse(null);
        if (response == null) {
            return Optional.empty();
        }
        SignCheckResponse parsed = EncbinUtil.decrypt(response, session.sharedSecret(), SignCheckResponse.class);
        return Optional.ofNullable(parsed).filter(r -> r.verifyToken() != null && !r.verifyToken().isBlank());
    }

    // ---- shared wire plumbing ----

    private Optional<byte[]> cc2018Exchange(String path, Object requestBody, String stage,
                                             UnaryOperator<MutableHttpRequest<byte[]>> headers) {
        try {
            byte[] plaintext = MAPPER.writeValueAsBytes(requestBody);
            byte[] encoded = Cc2018Cipher.encode(plaintext);
            // Content-Type is the literal codec name on the wire ("bin/cc2018") — confirmed
            // against real captured traffic (hellotalsniffer's user_register_center_v3_pre_login
            // / _login captures). There is no separate "ht-content-type" header on the wire at
            // all; that string only exists as a Retrofit annotation in the decompiled app,
            // consumed internally and never sent. An earlier version of this method sent
            // "ht-content-type: bin/cc2018" plus "Content-Type: application/octet-stream" —
            // upstream rejected that with a plain (non-cc2018) {"status":100,"msg":"invalid
            // params"}, confirmed live against a real account.
            HttpRequest<byte[]> httpRequest = headers.apply(HttpRequest.POST(path, encoded))
                .header("Content-Type", "bin/cc2018");
            return Optional.of(httpClient.toBlocking().retrieve(httpRequest, byte[].class));
        } catch (HttpClientResponseException e) {
            log.debug("{} rejected by upstream: status={}", stage, e.getStatus());
            return Optional.empty();
        } catch (java.io.IOException e) {
            throw JilaliException.upstreamFailure(stage, e);
        }
    }

    /**
     * Decodes a {@code bin/cc2018} response and unwraps it from the
     * {@link HelloTalkEnvelope} {@code {status,msg,data}} shape every {@code /user_register_center}
     * response uses — confirmed live (a real {@code pre_login} response is
     * {@code {"status":0,"msg":"success","data":{"user_id":...,"cnonce":...,"nonce":...}}}, not
     * the bare {@code data} shape). An earlier version of this method deserialized the envelope
     * directly into the data DTO; with {@code FAIL_ON_UNKNOWN_PROPERTIES=false} that silently
     * produced a null/zeroed object instead of erroring, corrupting every field the login step
     * depends on (cnonce/nonce/user_id) without any visible failure at this layer.
     */
    private <T> Optional<T> readEnvelope(byte[] response, Class<T> dataType, String stage) {
        try {
            byte[] json = Cc2018Cipher.decode(response);
            var envelopeType = MAPPER.getTypeFactory().constructParametricType(HelloTalkEnvelope.class, dataType);
            HelloTalkEnvelope<T> envelope = MAPPER.readValue(json, envelopeType);
            if (!envelope.isSuccess()) {
                log.debug("{} rejected by upstream: status={} msg={}", stage, envelope.status(), envelope.msg());
                return Optional.empty();
            }
            // Redact any "jwt" value before logging — this may be a real LoginResponse.userInfo.jwt.
            log.debug("{} decoded envelope (jwt redacted): {}", stage,
                new String(json, java.nio.charset.StandardCharsets.UTF_8)
                    .replaceAll("\"jwt\"\\s*:\\s*\"[^\"]*\"", "\"jwt\":\"***REDACTED***\""));
            return Optional.ofNullable(envelope.data());
        } catch (RuntimeException | java.io.IOException e) {
            log.warn("{} response decode failed: {} — raw response ({} bytes) as text: {}",
                stage, e.getMessage(), response.length,
                new String(response, java.nio.charset.StandardCharsets.UTF_8));
            return Optional.empty();
        }
    }

    private void encbinPost(String path, Object requestBody) {
        var session = Curve25519SessionGenerator.generate(properties.serverPubKeyHex());
        byte[] encrypted = EncbinUtil.encrypt(requestBody, session.sharedSecret());
        encbinExchange(path, encrypted, session.headerValue(), path)
            .orElseThrow(() -> JilaliException.upstreamFailure(path, null));
    }

    private Optional<byte[]> encbinExchange(String path, byte[] encryptedBody, String pubHeader, String stage) {
        try {
            // Content-Type is "application/json" on the wire for ht/encbin too — confirmed
            // against a real captured `profile/v2/userinfo` request (raw encrypted bytes as the
            // body despite the JSON content-type declaration; upstream apparently keys off this
            // exact literal value regardless of the body's actual shape). Again, no separate
            // "ht-content-type" header exists on the wire.
            HttpRequest<byte[]> httpRequest = androidHeaders(HttpRequest.POST(path, encryptedBody))
                .header("Content-Type", "application/json")
                .header("x-ht-pub", pubHeader);
            return Optional.ofNullable(httpClient.toBlocking().retrieve(httpRequest, byte[].class));
        } catch (HttpClientResponseException e) {
            log.debug("{} rejected by upstream: status={}", stage, e.getStatus());
            return Optional.empty();
        }
    }

    private MutableHttpRequest<byte[]> androidHeaders(MutableHttpRequest<byte[]> request) {
        return request
            .header("Accept", "*/*")
            .header("Accept-Charset", "UTF-8,*;q=0.5")
            .header("Accept-Encoding", "gzip")
            .header("Cache-Control", "no-cache")
            .header("User-Agent", "android;" + ApkSignatureGenerator.VERSION_NAME + ";" + properties.deviceModel() + ";" + OS_VERSION)
            .header("x-ht-version", ApkSignatureGenerator.VERSION_NAME)
            .header("x-ht-os", OS_TYPE_ANDROID)
            .header("x-ht-device", properties.deviceModel())
            .header("x-ht-os-version", OS_VERSION)
            .header("x-ht-lang", "English")
            .header("x-ht-channel", CHANNEL)
            .header("x-ht-did", properties.deviceId());
        // Authorization/x-ht-token/x-ht-uid are deliberately left unset here — real captured
        // traffic shows the app attaches whatever credential it has cached even on a pre-auth
        // call like pre_login, so falling through to DefaultHeadersClientFilter's shared-token
        // default (the same thing every other endpoint in this BFF does) matches that behavior;
        // an earlier version of this method explicitly blanked them out on a wrong hypothesis
        // that they were the cause of an upstream rejection — they weren't (see cc2018Exchange's
        // Content-Type fix for the actual cause).
    }

    /**
     * iOS persona for {@code /v3/login} only — matches the real captured request headers in
     * {@code hellotalsniffer/data/captured/organized_captures_new/user_register_center_v3_login_binary/}
     * field-for-field (device model, os version, build, channel). {@code uid} comes from the
     * pre_login response, matching the real app's own {@code user-agent} format
     * ({@code ios;<version>;<device>;<os_version>;<uid>}).
     */
    private MutableHttpRequest<byte[]> iosLoginHeaders(MutableHttpRequest<byte[]> request, String uid) {
        return request
            .header("Accept", "*/*")
            .header("Accept-Encoding", "gzip")
            .header("User-Agent", "ios;" + IOS_HEADER_VERSION + ";" + IOS_DEVICE_MODEL + ";" + IOS_OS_VERSION + ";" + uid)
            .header("x-ht-version", IOS_HEADER_VERSION)
            .header("x-ht-os", "ios")
            .header("x-ht-device", IOS_DEVICE_MODEL)
            .header("x-ht-os-version", IOS_OS_VERSION)
            .header("x-ht-lang", "English")
            .header("x-ht-channel", IOS_CHANNEL)
            .header("x-ht-build", IOS_BUILD)
            .header("x-ht-uid", uid)
            .header("x-ht-did", properties.deviceId());
    }
}
