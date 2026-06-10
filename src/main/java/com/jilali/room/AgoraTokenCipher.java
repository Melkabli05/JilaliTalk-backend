package com.jilali.room;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * LiveHub returns the Agora RTC token in {@code rtc_info.token} AES-encrypted, not as a plain
 * Agora token. The browser SDK needs the decrypted value: a plain Agora token embeds the App ID
 * after its {@code 006}/{@code 007} version prefix, and Agora's gateway extracts the App ID from
 * that token. Hand it the still-encrypted blob and the gateway can't find an App ID, which surfaces
 * as {@code CAN_NOT_GET_GATEWAY_SERVER: invalid vendor key, can not find appid}.
 * <p>
 * The scheme matches the original HelloTalk web client: AES-128 in ECB mode with no padding, a
 * fixed key, and a hex-encoded ciphertext. Tokens that already start with {@code 006}/{@code 007}
 * are plain and pass through untouched.
 */
final class AgoraTokenCipher {

    // ASCII "15helloTCJTALK20" — the 16-byte AES key used by the upstream.
    private static final byte[] KEY = "15helloTCJTALK20".getBytes(StandardCharsets.US_ASCII);

    private AgoraTokenCipher() {
    }

    /** Returns the plain Agora token, or the input unchanged if it is already plain or undecryptable. */
    static String decrypt(String token) {
        if (token == null || token.isEmpty()) {
            return token;
        }
        if (token.startsWith("006") || token.startsWith("007")) {
            return token;
        }
        try {
            byte[] ciphertext = hexToBytes(token);
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(KEY, "AES"));
            byte[] plain = cipher.doFinal(ciphertext);
            // NoPadding leaves trailing NUL bytes from the upstream's zero-padding; strip them.
            int end = plain.length;
            while (end > 0 && plain[end - 1] == 0) {
                end--;
            }
            return new String(plain, 0, end, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Not valid hex / not our scheme — leave the value as-is rather than dropping it.
            return token;
        }
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        if ((len & 1) != 0) {
            throw new IllegalArgumentException("odd-length hex");
        }
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int hi = Character.digit(hex.charAt(i), 16);
            int lo = Character.digit(hex.charAt(i + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("non-hex character");
            }
            out[i / 2] = (byte) ((hi << 4) | lo);
        }
        return out;
    }
}
