package com.jilali.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.crypto.ApkSignatureGenerator;
import io.micronaut.core.order.Ordered;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.ClientFilter;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.context.ServerRequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Supplies sensible defaults for device-context headers on every outbound Jilali call.
 * Runs after {@link HeaderPropagationFilter} so that:
 * <ul>
 *   <li>Real auth headers (authorization, x-ht-token) take priority — they come from the frontend.</li>
 *   <li>Device-context defaults (x-ht-version, x-ht-os, etc.) are used when the frontend omits them.</li>
 *   <li>Tracing headers are always generated fresh per call to maintain distributed trace hygiene.</li>
 * </ul>
 * <p>
 * {@code x-ht-uid} is special: the upstream rejects requests that omit it ({@code err_param}),
 * but the Angular frontend doesn't know its caller uid without decoding the JWT, and adding JWT
 * decoding to every HttpInterceptor would couple it to the auth scheme. We derive it here from
 * the inbound {@code Authorization} header's JWT instead — the same trick
 * {@link com.jilali.client.JilaliGateway#currentUserId} uses for the encrypted {@code userInfo}
 * path, generalized via {@link JwtUtil#uidFromBearer}.
 */
@ClientFilter(serviceId = "jlhub")
public class DefaultHeadersClientFilter implements Ordered {

    private static final Logger log = LoggerFactory.getLogger(
        DefaultHeadersClientFilter.class
    );

    private final JilaliProperties properties;
    private final AuthTokenHolder authToken;
    private final ObjectMapper om;

    public DefaultHeadersClientFilter(JilaliProperties properties, AuthTokenHolder authToken, ObjectMapper om) {
        this.properties = properties;
        this.authToken = authToken;
        this.om = om;
    }

    @RequestFilter
    public void addDefaults(MutableHttpRequest<?> downstream) {
        var h = downstream.getHeaders();

        // User-Agent MUST match the exact format HelloTalk's Android client emits per
        // re_output smali (vm/b.smali:170-213 + o.smali:3894-3927 +
        // constraintlayout/a.smali:137-146):
        //     "android;<clientVer>;<deviceModel>;<osVersion>;<jid>"
        // — semicolon-delimited, with the caller's own uid (NOT target/visitor) at the end.
        // Our previous default of "JilaliBff/1.0" or even the "ios;6.1.0;iPhone;18.5" stub
        // did not match this format and the upstream rejects the request as unauthenticated,
        // returning {"code":400,"msg":"no data currently"} even when the body sign is correct.
        // Always overwrite, even if the inbound already has a User-Agent (which is normally
        // the framework's curl/8.14.1 default and would otherwise be left in place by the
        // "if missing" gate that used to live here).
        downstream.header("User-Agent", buildUserAgent());

        // X-HT-Session is a second mandatory authentication header (re_output smali
        // vm/b.smali:173-179 + ja/r.smali:2737+). It's built as base64(xTEA(json{"uid","os",
        // "version","area_code","session"})) — implementing the encryption key derivation
        // is non-trivial (see ja/r.smali → Lmx/a;->H + Lcom/hellotalk/utils/TeaUtils), and
        // upstream may also accept requests without it on lower-security endpoints. For now
        // we omit it; if upstream keeps rejecting with code:400 even after the User-Agent fix,
        // this is the next thing to add.
        // downstream.header("X-HT-Session", buildXhtSession());

        // If the frontend didn't send a known header, supply the default.
        for (var header : properties.forwardedHeaders()) {
            if (h.get(header) == null) {
                var defaultValue = defaultFor(header);
                if (defaultValue != null) {
                    downstream.header(header, defaultValue);
                }
            }
        }

        // Always generate fresh tracing — don't reuse inbound trace IDs.
        downstream.header("x-b3-sampled", "1");
        downstream.header("x-b3-traceid", "%032x".formatted(System.nanoTime()));
        downstream.header(
            "x-b3-spanid",
            "%016x".formatted(System.nanoTime() & 0xFFFF_FFFF_FFFFL)
        );
        downstream.header(
            "x-request-start",
            String.valueOf(System.currentTimeMillis())
        );
    }

    private String defaultFor(String header) {
        return switch (header) {
            case "authorization", "x-ht-token" -> "Bearer " + authToken.get();
            // Replaced by the unconditional buildUserAgent() in addDefaults above. Kept
            // here only because forwardedHeaders("user-agent") still references it; any caller
            // using the forwarded-headers config will still get the smali-correct value.
            case "user-agent" -> buildUserAgent();
            case "x-ht-version" -> "6.1.0";
            case "x-ht-os" -> "ios";
            case "x-ht-channel" -> "AppStore";
            case "x-ht-lang" -> "English";
            case "x-ht-ui-mode" -> "1";
            case "x-ht-timezone" -> "1.00";
            case "x-ht-tzid" -> "Africa/Casablanca";
            case "x-ht-device" -> "iPhone";
            case "x-ht-os-version" -> "18.5";
            case "x-ht-build" -> "135";
            case "x-ht-did" -> "373b9d3f80345ace3ed2679e78de84cafc7cd481";
            case "accept-language" -> "en-MA;q=1.0, fr-MA;q=0.9, ar-MA;q=0.8";
            // Derive the caller uid from the inbound JWT. The frontend never sends x-ht-uid,
            // and upstream rejects requests that omit it (err_param).
            case "x-ht-uid" -> deriveCallerUid();
            default -> null;
        };
    }

    /**
     * Reads the inbound {@code Authorization} header (set by the frontend or, in its absence,
     * the default token from {@link JilaliProperties}) and extracts the {@code uid} claim. Returns
     * {@code null} if no JWT is available or its payload has no uid — the filter then skips the
     * header, which is the same fail-soft behavior as the other defaults.
     */
    private String deriveCallerUid() {
        String inboundAuth = ServerRequestContext.currentRequest()
            .map(req -> req.getHeaders().get("authorization"))
            .orElse(null);
        String token = (inboundAuth != null && !inboundAuth.isBlank())
            ? inboundAuth
            : "Bearer " + authToken.get();
        Long uid = JwtUtil.uidFromBearer(token);
        log.debug("[jlhub] deriveCallerUid: token-source={}, uid={}",
            (inboundAuth != null && !inboundAuth.isBlank()) ? "inbound" : "default",
            uid);
        if (uid == null) {
            log.debug("[jlhub] could not derive x-ht-uid from inbound JWT");
            return null;
        }
        return String.valueOf(uid);
    }

    /**
     * Builds the smali-canonical User-Agent string for the configured BFF account:
     *     {@code android;<clientVer>;<deviceModel>;<osVersion>;<jid>}
     *
     * <p>Reverse-engineered directly from {@code vm/b.smali:170-213} +
     * {@code org/slf4j/helpers/o.smali:3894-3927} (the {@code m(I)Ljava/lang/String;} helper that
     * {@code vm.b.a()} calls when populating the {@code "User-Agent"} header for any
     * logged-in client request): semicolon-delimited concatenation of the platform string,
     * the app's version name (the same {@link ApkSignatureGenerator#VERSION_NAME}), the
     * Android device model (from {@link JilaliProperties#deviceModel()}, matching the
     * upstream {@code /user_profile_visitor/v2/my_history} and all other personal-API
     * endpoints' device-fingerprint header), the Android OS release version, and finally the
     * caller's own numeric uid.
     */
    private String buildUserAgent() {
        return "android;%s;%s;%s;%s".formatted(
            ApkSignatureGenerator.VERSION_NAME,
            properties.deviceModel(),
            "18.5",  // TODO: read from System.getProperty("ro.build.version.release") when we
                       //       run on a real Android device; for the BFF dev box this is a
                       //       safe default since the only thing that varies is the device
                       //       model string above.
            UidExtractor.uidAsLong(authToken.get(), om)
        );
    }

    @Override
    public int getOrder() {
        return Integer.MAX_VALUE; // run after HeaderPropagationFilter
    }
}