package com.jilali.crypto;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * The reduced-round TEA cipher behind {@code TeaUtils.xTEADecrypt}/{@code xTEAEncrypt} in
 * {@code libhellotalk-tea.so} — 16 rounds (not TEA's usual 32), a non-standard CBC-like
 * plaintext/ciphertext-feedback chaining, and a custom padding scheme with a 7-byte all-zero
 * integrity trailer. Takes the 16-byte key as an explicit argument; callers decide where that
 * key comes from (embedded in the payload, a negotiated session key, etc — see
 * {@link Cc2018Cipher} and {@code com.jilali.websocket.HtImPayloadCipher} for the two framings
 * this app uses it under).
 *
 * <p>Extracted from {@link Cc2018Cipher}'s original self-contained implementation once a second
 * consumer ({@code ht_im/sock}, confirmed to call the exact same native function) needed the same
 * primitive — both were independently verified against real native code before this split, so
 * the extraction itself carries no new correctness risk as long as {@code Cc2018CipherTest}
 * still passes byte-for-byte against its native-derived vectors after the refactor.
 */
public final class TeaCipher {

    private static final int DELTA = 0x61C88647;
    private static final int MIN_HEADER_LEN = 3;
    private static final int TRAILER_LEN = 7;
    public static final int KEY_LEN = 16;
    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

    private TeaCipher() {}

    /** @throws IllegalArgumentException if plaintext is empty — the native codec rejects zero-length input. */
    public static byte[] encrypt(byte[] plaintext, byte[] key) {
        if (plaintext == null || plaintext.length == 0) {
            throw new IllegalArgumentException("cannot encrypt empty/null plaintext");
        }
        requireKey(key);

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

        return encryptChain(padded, key);
    }

    public static byte[] decrypt(byte[] ciphertext, byte[] key) {
        if (ciphertext == null || ciphertext.length == 0 || ciphertext.length % 8 != 0) {
            throw new IllegalArgumentException("ciphertext length must be a positive multiple of 8: "
                + (ciphertext == null ? "null" : ciphertext.length));
        }
        requireKey(key);

        byte[] padded = decryptChain(ciphertext, key);
        int len = padded.length;
        for (int i = len - TRAILER_LEN; i < len; i++) {
            if (padded[i] != 0) {
                throw new IllegalStateException("TEA trailer check failed (wrong key or corrupt data)");
            }
        }

        int extra = padded[0] & 0x07;
        int headerLen = extra + MIN_HEADER_LEN;
        int dataLen = len - headerLen - TRAILER_LEN;
        if (dataLen < 0) {
            throw new IllegalStateException("TEA decoded length underflow (headerLen=" + headerLen + ", total=" + len + ")");
        }
        byte[] out = new byte[dataLen];
        System.arraycopy(padded, headerLen, out, 0, dataLen);
        return out;
    }

    private static void requireKey(byte[] key) {
        if (key == null || key.length != KEY_LEN) {
            throw new IllegalArgumentException("key must be exactly " + KEY_LEN + " bytes");
        }
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

    /** Decrypts an already block-aligned buffer. Returns the raw padded plaintext (header + data + trailer intact). */
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

    /** Encrypts an already block-aligned buffer. */
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
