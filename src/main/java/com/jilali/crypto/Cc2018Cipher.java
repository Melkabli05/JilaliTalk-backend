package com.jilali.crypto;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * HelloTalk's {@code bin/cc2018} HTTP body codec ({@code bin/cc2019} is the same codec with a
 * gzip wrapper — gzip/gunzip that yourself around {@link #encode}/{@link #decode}).
 *
 * <p>Traced from the app's {@code wm/c} OkHttp interceptor, which for this content-type calls
 * into {@code ig1/c.y()}/{@code B()}, which delegate to the native
 * {@code TeaUtils.xTEADecryptWithKey}/{@code xTEAEncryptWithKey} in {@code libhellotalk-tea.so}.
 * Same block transform and CBC-like chaining family as {@link QqTeaCipher} (Tencent QQ's TEA
 * variant) — kept as a separate implementation here rather than sharing code because the framing
 * differs in three ways: the key travels inside the payload instead of being negotiated
 * separately, the native side additionally checks a 7-byte all-zero trailer for integrity
 * (decrypting with the wrong key almost never produces a zero trailer by chance), and the
 * decrypted length returned is the exact original plaintext length (no leftover padding for the
 * caller to strip, unlike {@code QqTeaCipher}).
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

    private static final int DELTA = 0x61C88647;
    private static final int KEY_LEN = 16;
    private static final int MIN_HEADER_LEN = 3;
    private static final int TRAILER_LEN = 7;
    private static final SecureRandom RANDOM = new SecureRandom();

    private Cc2018Cipher() {}

    /** @throws IllegalArgumentException if plaintext is empty — the native codec rejects zero-length input. */
    public static byte[] encode(byte[] plaintext) {
        if (plaintext == null || plaintext.length == 0) {
            throw new IllegalArgumentException("cc2018 cannot encode empty/null plaintext");
        }
        byte[] key = new byte[KEY_LEN];
        RANDOM.nextBytes(key);

        int dataLen = plaintext.length;
        int headerLen = (8 - (dataLen + MIN_HEADER_LEN + TRAILER_LEN) % 8) % 8 + MIN_HEADER_LEN;
        int totalLen = headerLen + dataLen + TRAILER_LEN;

        byte[] padded = new byte[totalLen];
        padded[0] = (byte) ((headerLen - MIN_HEADER_LEN) & 0x07);
        for (int i = 1; i < headerLen; i++) {
            padded[i] = (byte) RANDOM.nextInt(256);
        }
        System.arraycopy(plaintext, 0, padded, headerLen, dataLen);
        // trailing TRAILER_LEN bytes stay zero (default array init)

        byte[] ciphertext = encryptChain(padded, key);
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
        if (ciphertext.length % 8 != 0) {
            throw new IllegalArgumentException("cc2018 ciphertext length not a multiple of 8: " + ciphertext.length);
        }

        byte[] padded = decryptChain(ciphertext, key);
        int len = padded.length;
        for (int i = len - TRAILER_LEN; i < len; i++) {
            if (padded[i] != 0) {
                throw new IllegalStateException("cc2018 trailer check failed (wrong key or corrupt data)");
            }
        }

        int extra = padded[0] & 0x07;
        int headerLen = extra + MIN_HEADER_LEN;
        int dataLen = len - headerLen - TRAILER_LEN;
        if (dataLen < 0) {
            throw new IllegalStateException("cc2018 decoded length underflow (headerLen=" + headerLen + ", total=" + len + ")");
        }
        byte[] out = new byte[dataLen];
        System.arraycopy(padded, headerLen, out, 0, dataLen);
        return out;
    }

    // --- TEA core + CBC-like chaining ---------------------------------------------------------

    private static int[] readKeyWords(byte[] key) {
        ByteBuffer kb = ByteBuffer.wrap(key).order(ByteOrder.LITTLE_ENDIAN);
        return new int[] { kb.getInt(0), kb.getInt(4), kb.getInt(8), kb.getInt(12) };
    }

    private static int[] decryptBlock(int v0, int v1, int[] k) {
        int sum = 0xE3779B90; // delta * 16 mod 2^32
        for (int i = 0; i < 16; i++) {
            int term1 = ((v0 << 4) + k[2]) ^ (v0 + sum) ^ ((v0 >>> 5) + k[3]);
            v1 = v1 - term1;

            int term2 = ((v1 << 4) + k[0]) ^ (v1 + sum) ^ ((v1 >>> 5) + k[1]);
            v0 = v0 - term2;

            sum = sum + DELTA;
        }
        return new int[] { v0, v1 };
    }

    private static int[] encryptBlock(int v0, int v1, int[] k) {
        int sum = 0x9E3779B9;
        for (int i = 0; i < 16; i++) {
            int term2 = ((v1 << 4) + k[0]) ^ (v1 + sum) ^ ((v1 >>> 5) + k[1]);
            v0 = v0 + term2;

            int term1 = ((v0 << 4) + k[2]) ^ (v0 + sum) ^ ((v0 >>> 5) + k[3]);
            v1 = v1 + term1;

            sum = sum - DELTA;
        }
        return new int[] { v0, v1 };
    }

    /** Decrypts an already-padded, block-aligned buffer. Returns the raw padded plaintext (header + data + trailer intact). */
    private static byte[] decryptChain(byte[] buffer, byte[] key) {
        int[] k = readKeyWords(key);
        ByteBuffer in = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);
        int len = buffer.length;
        byte[] out = new byte[len];
        ByteBuffer outBuf = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN);

        int c0 = in.getInt(0);
        int c1 = in.getInt(4);
        int[] d = decryptBlock(c0, c1, k);
        outBuf.putInt(0, d[0]);
        outBuf.putInt(4, d[1]);

        int prevD0 = d[0], prevD1 = d[1], prevC0 = c0, prevC1 = c1;

        for (int offset = 8; offset < len; offset += 8) {
            int curC0 = in.getInt(offset);
            int curC1 = in.getInt(offset + 4);
            int in0 = curC0 ^ prevD0;
            int in1 = curC1 ^ prevD1;
            int[] dec = decryptBlock(in0, in1, k);
            outBuf.putInt(offset, dec[0] ^ prevC0);
            outBuf.putInt(offset + 4, dec[1] ^ prevC1);
            prevD0 = dec[0];
            prevD1 = dec[1];
            prevC0 = curC0;
            prevC1 = curC1;
        }
        return out;
    }

    /** Encrypts an already-padded, block-aligned buffer. */
    private static byte[] encryptChain(byte[] buffer, byte[] key) {
        int[] k = readKeyWords(key);
        ByteBuffer in = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN);
        int len = buffer.length;
        byte[] out = new byte[len];
        ByteBuffer outBuf = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN);

        int p0 = in.getInt(0);
        int p1 = in.getInt(4);
        int[] c = encryptBlock(p0, p1, k);
        outBuf.putInt(0, c[0]);
        outBuf.putInt(4, c[1]);

        int prevD0 = p0, prevD1 = p1, prevC0 = c[0], prevC1 = c[1];

        for (int offset = 8; offset < len; offset += 8) {
            int curP0 = in.getInt(offset);
            int curP1 = in.getInt(offset + 4);
            int dec0 = curP0 ^ prevC0;
            int dec1 = curP1 ^ prevC1;
            int[] enc = encryptBlock(dec0, dec1, k);
            int curC0 = enc[0] ^ prevD0;
            int curC1 = enc[1] ^ prevD1;
            outBuf.putInt(offset, curC0);
            outBuf.putInt(offset + 4, curC1);
            prevD0 = dec0;
            prevD1 = dec1;
            prevC0 = curC0;
            prevC1 = curC1;
        }
        return out;
    }
}
