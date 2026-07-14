package com.jilali.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** MD5 utility — used for HelloTalk's email login password hashing. */
public final class Md5Util {

    private Md5Util() {}

    /**
     * Returns the lowercase hex MD5 of {@code input}.
     * Used for the double-MD5 password scheme in email/password login.
     */
    public static String md5Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(digest);
        } catch (Exception e) {
            throw new RuntimeException("MD5 failed", e);
        }
    }

    /**
     * Computes HelloTalk's email-login password hash:
     * {@code MD5(MD5(password + cnonce) + nonce)}.
     *
     * <p>Verified against the smali implementation in {@code sd/d.smali} (method {@code a(password)}):
     * <pre>
     * hash1 = MD5(password + cnonce)
     * passwd = MD5(hash1 + nonce)
     * </pre>
     *
     * @param password     Plain-text password.
     * @param cnonce       Client nonce from the pre-login response.
     * @param nonce        Server nonce from the pre-login response.
     */
    public static String emailPasswordHash(String password, String cnonce, String nonce) {
        String step1 = md5Hex(password + cnonce);
        return md5Hex(step1 + nonce);
    }

    /**
     * A static salt appended to the {@code sign} formula below — NOT the {@code behavior_validate}
     * anti-cheat token, despite an earlier version of this method assuming so. Recovered from
     * smali {@code rd/a;-><init>}, which reads it from a lazily-initialized constant
     * ({@code Lzw0/a;->b}, a {@code kotlin.Lazy<String>}) rather than the {@code behavior_validate}
     * field (which is a separate, mutable field on the same class, set later via its own setter
     * and never read by the sign computation).
     *
     * <p>The constant itself is obfuscated in the APK as four {@code int[]} char-code arrays
     * (see {@code y41/c.smali}, the lambda backing {@code Lzw0/a;->b}), decoded by XOR-ing each
     * int with {@code 0x5A} (the fallback/default case of {@code uz/a;->invoke}, the transform
     * function) and concatenating the four resulting 8-character strings with no separator.
     * Decoded by hand from the raw int arrays (not run in an emulator) and cross-checked bit by
     * bit against the XOR-0x5A transform.
     */
    private static final String LOGIN_SIGN_SECRET = "Q5bw4aJ9Pp16MYxsErWSYaxKzn4wy2ed";

    /**
     * Computes the {@code sign} field HelloTalk's {@code /v3/login} request requires:
     * {@code MD5("client_version=" + v + "&deviceid=" + deviceId + "&login_type=" + loginType
     * + "&ts=" + ts + LOGIN_SIGN_SECRET)}.
     *
     * <p>Verified directly against smali {@code rd/a.smali}'s constructor (the {@code Lrd/a}
     * device-fingerprint base class shared by every {@code /v3/login} request variant) — the
     * field concatenation order and the identity of the trailing constant were both re-derived
     * from the actual bytecode, not carried over from the (incorrect, behavior_validate-based)
     * prose description in {@code FINDINGS.md} §7.1.
     *
     * @param clientVersion {@code versionName} of the impersonated build (see {@link ApkSignatureGenerator#VERSION_NAME}).
     * @param deviceId      The device id sent as {@code device_id} elsewhere in the same request.
     * @param loginType     {@code 1} for email/password login.
     * @param timestampMs   {@code System.currentTimeMillis()} at request time — must match the request's {@code ts} field.
     */
    public static String loginSignature(String clientVersion, String deviceId, int loginType, long timestampMs) {
        String data = "client_version=" + clientVersion
            + "&deviceid=" + deviceId
            + "&login_type=" + loginType
            + "&ts=" + timestampMs
            + LOGIN_SIGN_SECRET;
        return md5Hex(data);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
