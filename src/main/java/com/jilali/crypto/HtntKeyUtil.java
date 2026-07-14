package com.jilali.crypto;

/**
 * Computes the {@code htntkey} field used in HelloTalk's signup-flow request body
 * (class {@code com.hellotalk.sign.register.data.SignCheckReqV3}, smali id {@code Ls21/i;}).
 *
 * <p>Algorithm verified by static analysis of {@code libhellotalk-tea.so} v6.3.70:
 * <pre>
 *   keyTable = { 1: "15helloTCJTALK20",
 *                2: "TCJhellotalka22c",
 *                3: "abccdfef#*" }   // ← this util
 *   getMd5WithKey(s, n)  ≡  MD5(s + keyTable[n-1])    // n ∈ {1, 2, 3}; n ≥ 4 → null
 *   htntkey              =  getMd5WithKey(deviceId + loginType + timestampMs, 3)
 * </pre>
 *
 * <p>Provenance:
 * <ul>
 *   <li>{@code Java_com_hellotalk_utils_TeaUtils_getMd5WithKey} at {@code 0xce3c} (692 B) — smali call site
 *       in {@code s21/i.smali} constructs the inner string via {@code StringBuilder.append(deviceId).append(loginType).append(t)}
 *       and passes it to this native function with {@code keyIndex=3}.</li>
 *   <li>Key table at file offset {@code 0x1d470} (3 entries) — extracted via {@code .rela.dyn} relocations;
 *       entry 3 → ASCII {@code "abccdfef#*"}.</li>
 *   <li>{@code calculateMD5} at {@code 0xb25c} builds the standard MD5 IV constants
 *       {@code 0x67452301 / 0xefcdab89 / 0x98badcfe / 0x10325476} via {@code mov + movk lsl #16}
 *       and runs the standard 4-round schedule. {@code MessageDigest.getInstance("MD5")}
 *       produces bit-identical output.</li>
 *   <li>Hex encoder at {@code 0xb778} is plain {@code snprintf("%02x", byte)} — verified by the
 *       {@code "%02x"} format string at file offset {@code 0x6833}.</li>
 * </ul>
 *
 * <p>Note: {@code htntkey} is currently used only by the <b>signup</b> flow ({@code /v3/check}),
 * not by the actual email login ({@code /v3/login}). This util exists for parity/future-proofing —
 * it does not need to be wired into {@code AuthService.loginWithEmail} as of v6.3.70.
 */
public final class HtntKeyUtil {

    /** Suffix used by {@code keyIndex=3}. Extracted from {@code libhellotalk-tea.so:0x1d470}. */
    private static final String KEY = "abccdfef#*";

    private HtntKeyUtil() {}

    /**
     * Returns {@code MD5(deviceId + loginType + timestampMs + "abccdfef#*")} as lowercase hex.
     *
     * @param deviceId    DeviceVQHelper-generated device identifier (or jilalibff's equivalent).
     * @param loginType   1 for email, 2 for phone, 3 for Facebook, … (see smali {@code s21/i.smali}).
     * @param timestampMs {@code System.currentTimeMillis()} — must match the {@code t} field in the same request.
     */
    public static String compute(String deviceId, int loginType, long timestampMs) {
        return Md5Util.md5Hex(deviceId + loginType + timestampMs + KEY);
    }
}