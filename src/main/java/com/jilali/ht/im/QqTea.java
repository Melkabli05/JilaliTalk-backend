package com.jilali.ht.im;

import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Port of HelloTalk's QQTEA cipher from {@code connectwebsock.js}.
 *
 * <p>This is a custom CBC-variant of the Tiny Encryption Algorithm (TEA):
 * <ul>
 *   <li>16 rounds (not the standard 32)</li>
 *   <li>Little-endian uint32 for both data and key (default)</li>
 *   <li>Custom CBC chaining: first block decrypted raw; subsequent blocks
 *       XOR with previous plaintext before decryption and XOR with previous
 *       ciphertext after</li>
 *   <li>Random padding stripped from the front after decryption:
 *       {@code padLen = (out[0] & 0x07) + 3}</li>
 * </ul>
 *
 * <p>Key note: the session key is stored as the UTF-8 bytes of the key <em>string</em>
 * received from the server — not binary-decoded. Keys shorter or longer than 16 bytes
 * are zero-padded or truncated to exactly 16 bytes.
 *
 * <p>All methods are pure and thread-safe.
 */
public final class QqTea {

    private static final SecureRandom RNG = new SecureRandom();

    private QqTea() {}

    // ---- Public API ---------------------------------------------------------

    /**
     * Decrypts a QQTEA-encrypted payload.
     *
     * @param data the encrypted bytes; must be a multiple of 8 and at least 8 bytes
     * @param key  the session key (UTF-8 bytes of the key string from the server);
     *             padded/truncated to 16 bytes if needed
     * @return decrypted bytes with padding stripped, or {@code null} if input is invalid
     */
    public static byte[] decrypt(byte[] data, byte[] key) {
        if (data == null || data.length < 8 || data.length % 8 != 0) return null;
        key = normalizeKey(key);
        int k0 = readLE32(key, 0), k1 = readLE32(key, 4),
            k2 = readLE32(key, 8), k3 = readLE32(key, 12);

        byte[] out = new byte[data.length];

        // First 8-byte block — decrypt directly (no CBC xor)
        int c0 = readLE32(data, 0), c1 = readLE32(data, 4);
        long d = decryptBlock(c0, c1, k0, k1, k2, k3);
        int d0 = hi(d), d1 = lo(d);
        writeLE32(out, 0, d0);
        writeLE32(out, 4, d1);

        int prevD0 = d0, prevD1 = d1, prevC0 = c0, prevC1 = c1;

        for (int off = 8; off < data.length; off += 8) {
            int curC0 = readLE32(data, off), curC1 = readLE32(data, off + 4);
            long dec = decryptBlock(curC0 ^ prevD0, curC1 ^ prevD1, k0, k1, k2, k3);
            int dec0 = hi(dec), dec1 = lo(dec);
            writeLE32(out, off,     dec0 ^ prevC0);
            writeLE32(out, off + 4, dec1 ^ prevC1);
            prevD0 = dec0; prevD1 = dec1;
            prevC0 = curC0; prevC1 = curC1;
        }

        // Strip random padding: padLen = (first byte & 0x07) + 3
        int padLen = (out[0] & 0x07) + 3;
        return padLen < out.length ? Arrays.copyOfRange(out, padLen, out.length) : out;
    }

    /**
     * Encrypts plaintext with QQTEA.
     *
     * @param data the plaintext bytes
     * @param key  the session key; padded/truncated to 16 bytes if needed
     * @return encrypted bytes
     */
    public static byte[] encrypt(byte[] data, byte[] key) {
        key = normalizeKey(key);
        // Random padding header: headerLen = (8 - (data.length + 10) % 8) % 8 + 3
        int headerLen = (8 - (data.length + 10) % 8) % 8 + 3;
        int trailingLen = 7;
        byte[] padded = new byte[data.length + headerLen + trailingLen];
        padded[0] = (byte) ((headerLen - 3) & 0x07);
        byte[] rand = new byte[headerLen - 1];
        RNG.nextBytes(rand);
        System.arraycopy(rand, 0, padded, 1, rand.length);
        System.arraycopy(data, 0, padded, headerLen, data.length);
        // trailing 7 bytes stay 0

        int k0 = readLE32(key, 0), k1 = readLE32(key, 4),
            k2 = readLE32(key, 8), k3 = readLE32(key, 12);

        byte[] out = new byte[padded.length];
        int p0 = readLE32(padded, 0), p1 = readLE32(padded, 4);
        long c = encryptBlock(p0, p1, k0, k1, k2, k3);
        int c0 = hi(c), c1 = lo(c);
        writeLE32(out, 0, c0);
        writeLE32(out, 4, c1);

        int prevD0 = p0, prevD1 = p1, prevC0 = c0, prevC1 = c1;

        for (int off = 8; off < padded.length; off += 8) {
            int curP0 = readLE32(padded, off), curP1 = readLE32(padded, off + 4);
            long enc = encryptBlock(curP0 ^ prevC0, curP1 ^ prevC1, k0, k1, k2, k3);
            int curC0 = hi(enc) ^ prevD0, curC1 = lo(enc) ^ prevD1;
            writeLE32(out, off,     curC0);
            writeLE32(out, off + 4, curC1);
            prevD0 = curP0 ^ prevC0; prevD1 = curP1 ^ prevC1;
            prevC0 = curC0;          prevC1 = curC1;
        }

        return out;
    }

    // ---- TEA block cipher ---------------------------------------------------

    // Decrypt one 8-byte block. sum starts at 0xE3779B90, increases by delta each round.
    // Result packed as long: high 32 bits = v0, low 32 bits = v1.
    private static long decryptBlock(int v0, int v1, int k0, int k1, int k2, int k3) {
        int sum = 0xE3779B90;
        final int delta = 0x61C88647;
        for (int i = 0; i < 16; i++) {
            v1 -= ((v0 << 4) + k2) ^ (v0 + sum) ^ ((v0 >>> 5) + k3);
            v0 -= ((v1 << 4) + k0) ^ (v1 + sum) ^ ((v1 >>> 5) + k1);
            sum += delta;
        }
        return pack(v0, v1);
    }

    // Encrypt one 8-byte block. sum starts at 0x9E3779B9, decreases by delta each round.
    // Result packed as long: high 32 bits = v0, low 32 bits = v1.
    private static long encryptBlock(int v0, int v1, int k0, int k1, int k2, int k3) {
        int sum = 0x9E3779B9;
        final int delta = 0x61C88647;
        for (int i = 0; i < 16; i++) {
            v0 += ((v1 << 4) + k0) ^ (v1 + sum) ^ ((v1 >>> 5) + k1);
            v1 += ((v0 << 4) + k2) ^ (v0 + sum) ^ ((v0 >>> 5) + k3);
            sum -= delta;
        }
        return pack(v0, v1);
    }

    // ---- Helpers ------------------------------------------------------------

    private static byte[] normalizeKey(byte[] key) {
        if (key != null && key.length == 16) return key;
        return Arrays.copyOf(key != null ? key : new byte[0], 16);
    }

    private static long pack(int hi, int lo) {
        return ((long) hi << 32) | (lo & 0xFFFFFFFFL);
    }

    private static int hi(long packed) { return (int) (packed >>> 32); }
    private static int lo(long packed) { return (int)  packed; }

    private static int readLE32(byte[] b, int off) {
        return (b[off] & 0xFF)
            | ((b[off + 1] & 0xFF) << 8)
            | ((b[off + 2] & 0xFF) << 16)
            | ((b[off + 3] & 0xFF) << 24);
    }

    private static void writeLE32(byte[] b, int off, int v) {
        b[off]     = (byte)  v;
        b[off + 1] = (byte) (v >>  8);
        b[off + 2] = (byte) (v >> 16);
        b[off + 3] = (byte) (v >> 24);
    }
}
