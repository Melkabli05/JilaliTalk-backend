package com.jilali.crypto;

import java.security.SecureRandom;
import java.util.Arrays;

/**
 * HelloTalk's {@code bin/cc2018} HTTP body codec ({@code bin/cc2019} is the same codec with a
 * gzip wrapper — gzip/gunzip that yourself around {@link #encode}/{@link #decode}).
 *
 * <p>Traced from the app's {@code wm/c} OkHttp interceptor, which for this content-type calls
 * into {@code ig1/c.y()}/{@code B()}, which delegate to the native
 * {@code TeaUtils.xTEADecryptWithKey}/{@code xTEAEncryptWithKey} in {@code libhellotalk-tea.so}
 * — the same cipher {@link TeaCipher} implements (which this class delegates to). Same block
 * transform and CBC-like chaining family as {@code QqTeaCipher} (Tencent QQ's TEA variant) too.
 *
 * <p><b>Wire format:</b> {@code payload = random_16_byte_key || ciphertext}. A fresh key is
 * generated per message — the "encryption" therefore adds no confidentiality beyond TLS (the key
 * ships in the same message it decrypts); its only effect is to make the body opaque to a naive
 * viewer. {@link #encode} uses {@link SecureRandom} for the key regardless — nothing requires
 * mirroring the app's own weaker {@code java.util.Random()} choice for outbound traffic we
 * construct ourselves.
 *
 * <p>Verified against the real native code: {@code decode} was checked against payloads produced
 * by emulating {@code libhellotalk-tea.so} directly with Unicorn (not hand-transcribed from
 * disassembly), and {@code encode}'s output was in turn verified to decrypt correctly under that
 * same native-code emulation. See {@code Cc2018CipherTest} for the vectors.
 */
public final class Cc2018Cipher {

    private static final int KEY_LEN = TeaCipher.KEY_LEN;
    private static final SecureRandom RANDOM = new SecureRandom();

    private Cc2018Cipher() {}

    /** @throws IllegalArgumentException if plaintext is empty — the native codec rejects zero-length input. */
    public static byte[] encode(byte[] plaintext) {
        byte[] key = new byte[KEY_LEN];
        RANDOM.nextBytes(key);
        byte[] ciphertext = TeaCipher.encrypt(plaintext, key);

        byte[] result = new byte[KEY_LEN + ciphertext.length];
        System.arraycopy(key, 0, result, 0, KEY_LEN);
        System.arraycopy(ciphertext, 0, result, KEY_LEN, ciphertext.length);
        return result;
    }

    public static byte[] decode(byte[] payload) {
        if (payload == null || payload.length < KEY_LEN + 8) {
            throw new IllegalArgumentException("cc2018 payload too short: "
                + (payload == null ? "null" : payload.length + " bytes"));
        }
        byte[] key = Arrays.copyOfRange(payload, 0, KEY_LEN);
        byte[] ciphertext = Arrays.copyOfRange(payload, KEY_LEN, payload.length);
        return TeaCipher.decrypt(ciphertext, key);
    }
}
