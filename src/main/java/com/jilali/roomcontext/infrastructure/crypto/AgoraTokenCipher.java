package com.jilali.roomcontext.infrastructure.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/** LiveHub returns the Agora RTC token AES-encrypted; the browser SDK needs the plain token.
 *  AES-128-ECB, no padding, fixed key, hex-encoded ciphertext. Tokens already starting with
 *  {@code 006}/{@code 007} are plain and pass through untouched. Ported from the legacy
 *  com.jilali.room.AgoraTokenCipher (a self-contained crypto utility that happened to live in
 *  a feature package) - zero dependency on any legacy feature code. */
public final class AgoraTokenCipher {

    private AgoraTokenCipher() {}

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
            int end = plain.length;
            while (end > 0 && plain[end - 1] == 0) {
                end--;
            }
            return new String(plain, 0, end, StandardCharsets.UTF_8);
        } catch (Exception _) {
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
