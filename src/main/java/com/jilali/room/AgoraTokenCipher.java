package com.jilali.room;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * LiveHub returns the Agora RTC token in {@code rtc_info.token} AES-encrypted, not as a plain
 * Agora token. The browser SDK needs the plain token (it carries the App ID after its
 * {@code 006}/{@code 007} version prefix). Hand it the still-encrypted blob and the gateway
 * can't find an App ID, which surfaces as {@code CAN_NOT_GET_GATEWAY_SERVER: invalid vendor key,
 * can not find appid}.
 * <p>
 * The scheme matches the original HelloTalk web client: AES-128 in ECB mode with no padding,
 * a fixed key, and a hex-encoded ciphertext. Tokens that already start with {@code 006}/
 * {@code 007} are plain and pass through untouched.
 */
public final class AgoraTokenCipher {

    private AgoraTokenCipher() {
    }

    /**
     * Decrypts using the key from config ({@code jilali.agora-cipher-key}).
     * @see #decrypt(String, byte[])
     */
    public static String decrypt(String token, byte[] key) {
        if (token == null || token.isEmpty()) {
            return token;
        }
        if (token.startsWith("006") || token.startsWith("007")) {
            return token;
        }
        try {
            byte[] ciphertext = hexToBytes(token);
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"));
            byte[] plain = cipher.doFinal(ciphertext);
            // NoPadding leaves trailing NUL bytes from the upstream's zero-padding; strip them.
            int end = plain.length;
            while (end > 0 && plain[end - 1] == 0) {
                end--;
            }
            return new String(plain, 0, end, StandardCharsets.UTF_8);
        } catch (Exception _) {
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
