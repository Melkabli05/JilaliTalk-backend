package com.jilali.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

/**
 * Generates the {@code android_apk_signature} field HelloTalk's {@code ht_im/sock} login
 * packet requires.
 *
 * <p>Verified byte-for-byte against the real native {@code TeaUtils.xConnInfo} (in
 * {@code libhellotalk-tea.so}, disassembled with Capstone/pyelftools) rather than trusted as a
 * port: the native code's embedded HMAC key string and the first 998 hex chars of its embedded
 * certificate blob are byte-identical to the constants below, and {@code xConnInfo} itself does
 * the HMAC by calling back into Java — {@code com.hellotalk.utils.StringUtils.hmacSha256(String,
 * String)} — whose smali confirms the key argument is used as its raw UTF-8 string bytes
 * ({@code key.getBytes("UTF-8")} straight into {@code SecretKeySpec}), <b>not</b> hex-decoded
 * into 32 raw bytes first. That is genuinely easy to get backwards, hence spelling it out.
 *
 * <p>The concatenation order ({@code sig1 + tsSec + viValue + sig2 + deviceId}) was not
 * re-derived from the disassembly (the HMAC key/cert/behavior match was already a strong enough
 * correctness signal); if signatures are ever rejected server-side, that's the next thing to
 * verify against the native code directly.
 */
public final class ApkSignatureGenerator {

    private static final String HMAC_KEY = "fe0629ad30d48b5bf1e82865404694fe8525200575f5c4339debf5e8ff571c6e";

    /** A captured X.509 certificate, hex-encoded - only the first 998 hex chars are ever used
     *  (split into two halves straddling the timestamp+version in the signed data), matching
     *  the native code's own {@code substring(0, 499)} / {@code substring(499, 998)} split. */
    private static final String APK_SIG = "3082035930820241a003020102020461e4cac1300d06092a864886f70d01010b0500305c310b300906035504061302434e31123010060355040813096775616e67646f6e673111300f060355040713087368656e7a68656e310b3009060355040a13026a79310b3009060355040b13026a79310c300a06035504031303776c683020170d3132313031363032303033365a180f32303637303732303032303033365a305c310b300906035504061302434e31123010060355040813096775616e67646f6e673111300f060355040713087368656e7a68656e310b3009060355040a13026a79310b3009060355040b13026a79310c300a06035504031303776c6830820122300d06092a864886f70d01010105000382010f003082010a0282010100947e44daa5fe6b440513b2f206196f9a535da8a2a83841bfb430218322e95b513a5ae62bcea16330027e78557b701cc51ca6a02de45820592444244f456182fe6f7acf2283a085fb2258a445c9a3080ce236112bcbaeef77d4cf7fd4fa0e788799c2a372ed71b8805c20ed313333599f4db298ea10992e976d96157b642686b357b57dbca4d5ffcae60e8c5e3a77ba6b441e2f04194b6209275153199dca2b24845787f6bf777fc274c0b6cfaec2ba73ed84b910334d046234cb31bb094245d6bd00b6371025b216b26aef2348dce9c4f90bd8830748f8a82359beb15a9364062c7f1240a340d7d2212bfe77eded19885adb0fe0ac342cb78e594927be381aed0203010001a321301f301d0603551d0e04160414a526c8345e98a551da247300ad1feb87a389a106300d06092a864886f70d01010b0500038201010037b9a2297d4b21ec2e020306755b12d46e2ef3ac655787f81e1ebe7ed110e24b207667462feea52baf8dd115e58a816336ada3a866014afb07459f82ae789300148f291dd361b2be448e0bbe6039811de92b44b6cf7c9864bf4d4cc0ab5bb953f401970aecaff8f83012eb5b744fa43af618f79ed0914433aaea1619bad1fc0e1d41a68d072d7d7e5961a950b496df5c8a3881e33f7ac2b09cb5613a91f98e0aab8d896be91c80b565ec5d94a44ef17c2dbe109a3204cdaa4c9c2e8806e71520af48b9511601c8b7b76da0ec802f896c5c6f8c9c6194da3a3f33a5257365900c6cbf36b64c879b20011e770c2c2534d482789c86c0d008287292ffd40de22c41";

    /**
     * {@code "{versionName}({versionCode},{channel})"} — must track the app build this BFF is
     * impersonating. Fixed at {@code 6.3.70(11276,google)} to match {@code test_reverse_engineering.apk}
     * (package {@code com.hellotalk}, versionName {@code 6.3.70}, versionCode {@code 11276}, per
     * its AndroidManifest) — the previous value here (@code 6.3.40(11126,google)}) was a stale
     * older capture, not this build.
     */
    private static final String VI_VALUE = "6.3.70(11276,google)";

    /** {@code versionName} of the impersonated build — shared with any other class that needs
     *  to send {@code client_version} consistently with this one (e.g. login/signup requests). */
    public static final String VERSION_NAME = "6.3.70";

    /** {@code versionCode} of the impersonated build — shared the same way as {@link #VERSION_NAME}. */
    public static final int VERSION_CODE = 11276;

    private ApkSignatureGenerator() {}

    public static String generate(String deviceId, long timestampMs) {
        String tsSec = String.valueOf(timestampMs / 1000);
        String sig1 = APK_SIG.substring(0, 499);
        String sig2 = APK_SIG.length() > 499 ? APK_SIG.substring(499, Math.min(998, APK_SIG.length())) : "";
        String data = sig1 + tsSec + VI_VALUE + sig2 + deviceId;
        return hmacHex(HMAC_KEY, data) + tsSec;
    }

    private static String hmacHex(String keyStr, String dataStr) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keyStr.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] result = mac.doFinal(dataStr.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(result);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("APK signature HMAC failed", e);
        }
    }
}
