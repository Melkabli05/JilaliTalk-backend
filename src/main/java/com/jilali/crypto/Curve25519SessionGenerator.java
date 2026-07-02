package com.jilali.crypto;

import org.bouncycastle.crypto.generators.X25519KeyPairGenerator;
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters;
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters;
import org.bouncycastle.util.encoders.Hex;

import java.security.SecureRandom;

/**
 * Generates Curve25519 session keys for HelloTalk's ht/encbin encryption.
 *
 * Key pair generation: BC's X25519KeyPairGenerator (confirmed working on GraalVM).
 * Shared secret: Pure-Java X25519 scalar multiplication on the Montgomery curve,
 * implemented from RFC 7748 §4.1 — no crypto library needed, no GraalVM issues.
 *
 * Mirrors the logic in {@code userinfo/generatorkey.js}:
 *   sharedSecret = X25519(myPrivateKey, serverPublicKey)
 */
public final class Curve25519SessionGenerator {

    private Curve25519SessionGenerator() {}

    public static Curve25519Session generate(String serverPublicKeyHex) {
        try {
            // 1. Generate a fresh X25519 key pair using BC (works on GraalVM)
            X25519KeyPairGenerator keyGen = new X25519KeyPairGenerator();
            keyGen.init(new X25519KeyGenerationParameters(new SecureRandom()));
            var keyPair = keyGen.generateKeyPair();

            var bcPrivateKey = (X25519PrivateKeyParameters) keyPair.getPrivate();
            byte[] myPrivateKey = bcPrivateKey.getEncoded();
            byte[] serverPubKey = Hex.decode(serverPublicKeyHex);

            // 2. Compute shared secret: X25519(myPrivateKey, serverPublicKey)
            byte[] sharedSecret = x25519(myPrivateKey, serverPubKey);

            // 3. Build x-ht-pub header: serverStaticPubKey_hex + myPublicKey_hex
            // BC's X25519PublicKeyParameters.getEncoded() returns raw 32-byte u-coordinate
            String headerValue = serverPublicKeyHex + Hex.toHexString(keyPair.getPublic().getEncoded());
            String sharedSecretHex = Hex.toHexString(sharedSecret);

            return new Curve25519Session(headerValue, sharedSecretHex);
        } catch (Exception e) {
            throw new RuntimeException("Curve25519 key generation failed", e);
        }
    }

    // ── X25519 scalar multiplication (RFC 7748 §4.1) ─────────────────────────────────
    // Computes the Montgomery ladder: shared = u-coordinate(k * basepoint)
    // k = my private key (32 bytes, clamped)
    // u = server public key point (32 bytes)
    private static byte[] x25519(byte[] k, byte[] u) {
        // Clamp the scalar: set and clear specific bits per X25519 spec
        byte[] s = k.clone();
        s[0]  &= 0xf8;   // clear bits 0,1,2
        s[31] &= 0x7f;   // clear bit 7
        s[31] |= 0x40;   // set bit 6

        // Decode u (little-endian 255-bit field element)
        int[] x_1 = new int[10];
        int[] x_2 = { 1, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
        int[] x_3 = decodeLittleEndian(u);
        int[] z_2 = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
        int[] z_3 = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

        int[] A = new int[10];
        int[] B = new int[10];
        int[] C = new int[10];
        int[] D = new int[10];
        int[] E = new int[10];
        int[] AA = new int[10];
        int[] BB = new int[10];
        int[] DA = new int[10];
        int[] CB = new int[10];
        int[] t1 = new int[10];

        int swap = 0;
        for (int i = 0; i < 32; i++) {
            int byte_i = 31 - i;
            int bits = s[byte_i] & 0xff;

            for (int j = 7; j >= 0; j--) {
                int bit = (bits >>> j) & 1;
                swap ^= bit;

                cswap(swap, x_2, x_3);
                cswap(swap, z_2, z_3);
                swap = bit;

                // A = x_2 + z_2
                add(x_2, z_2, A);
                // B = x_2 - z_2
                sub(x_2, z_2, B);
                // C = x_3 + z_3
                add(x_3, z_3, C);
                // D = x_3 - z_3
                sub(x_3, z_3, D);
                // DA = D * A
                mul(D, A, DA);
                // CB = C * B
                mul(C, B, CB);
                // E = DA + CB
                add(DA, CB, E);
                // t1 = z_2 * 24
                mulScalar(z_2, 24, t1);
                // x_3 = (DA - CB)^2
                sub(DA, CB, x_3);
                sqr(x_3, x_3);
                // z_3 = x_1 * (DA + CB)^2
                add(DA, CB, BB);
                sqr(BB, BB);
                mulScalar(BB, 24, E);
                mul(x_1, BB, z_3);
                // AA = A^2
                sqr(A, AA);
                // BB = B^2
                sqr(B, BB);
                // x_2 = AA * BB
                mul(AA, BB, x_2);
                // t1 = (AA - BB) * (A + (A * 24))
                sub(AA, BB, DA);
                mulScalar(A, 24, CB);
                add(A, CB, C);
                mul(DA, C, t1);
                // z_2 = t1 * ((AA * 480) + (BB * 1250) + (t1 * 5))
                mulScalar(BB, 1250, z_2);
                mulScalar(AA, 480, A);
                add(z_2, A, z_2);
                mulScalar(t1, 5, A);
                add(z_2, A, z_2);
                mul(t1, z_2, z_2);
            }
        }

        cswap(swap, x_2, x_3);
        cswap(swap, z_2, z_3);

        // z_2 = z_2^(-1) mod P
        inv(z_2);
        // x_2 = x_2 * z_2 mod P
        mul(x_2, z_2, x_2);

        return encodeLittleEndian(x_2);
    }

    // ── Field arithmetic ─────────────────────────────────────────────────────────
    // 10 × 26-bit limbs, little-endian, modulo P = 2^255 - 19
    // Constants: P_i = 2^26 - 1, P_TOP = 2^25 - 1
    private static final int P0 = 0x3ffffff;
    private static final int P1 = 0x3ffffff;
    private static final int P2 = 0x3ffffff;
    private static final int P3 = 0x3ffffff;
    private static final int P4 = 0x3ffffff;
    private static final int P_TOP = 0x1ffffff; // 255 bits

    /** r = a + b mod P */
    private static void add(int[] a, int[] b, int[] r) {
        long t = ((a[0] & 0xffffffffL) + (b[0] & 0xffffffffL));
        r[0] = (int) (t & P0); t = (t >>> 26) + ((a[1] & 0xffffffffL) + (b[1] & 0xffffffffL));
        r[1] = (int) (t & P1); t = (t >>> 26) + ((a[2] & 0xffffffffL) + (b[2] & 0xffffffffL));
        r[2] = (int) (t & P2); t = (t >>> 26) + ((a[3] & 0xffffffffL) + (b[3] & 0xffffffffL));
        r[3] = (int) (t & P3); t = (t >>> 26) + ((a[4] & 0xffffffffL) + (b[4] & 0xffffffffL));
        r[4] = (int) (t & P_TOP);
    }

    /** r = a - b mod P */
    private static void sub(int[] a, int[] b, int[] r) {
        long t = ((a[0] & 0xffffffffL) - (b[0] & 0xffffffffL) - 0x3d34d);
        r[0] = (int) (t & P0); t = (t >>> 26) + ((a[1] & 0xffffffffL) - (b[1] & 0xffffffffL));
        r[1] = (int) (t & P1); t = (t >>> 26) + ((a[2] & 0xffffffffL) - (b[2] & 0xffffffffL));
        r[2] = (int) (t & P2); t = (t >>> 26) + ((a[3] & 0xffffffffL) - (b[3] & 0xffffffffL));
        r[3] = (int) (t & P3); t = (t >>> 26) + ((a[4] & 0xffffffffL) - (b[4] & 0xffffffffL));
        r[4] = (int) (t & P_TOP);
    }

    /** r = a * b mod P — schoolbook then reduction */
    private static void mul(int[] a, int[] b, int[] r) {
        long aa0 = a[0] & 0xffffffffL, aa1 = a[1] & 0xffffffffL,
             aa2 = a[2] & 0xffffffffL, aa3 = a[3] & 0xffffffffL, aa4 = a[4] & 0xffffffffL;
        long bb0 = b[0] & 0xffffffffL, bb1 = b[1] & 0xffffffffL,
             bb2 = b[2] & 0xffffffffL, bb3 = b[3] & 0xffffffffL, bb4 = b[4] & 0xffffffffL;
        long[] p = new long[9];
        for (int i = 0; i < 5; i++) {
            p[i]     += aa[i] * bb0;
            p[i + 1] += aa[i] * bb1;
            p[i + 2] += aa[i] * bb2;
            p[i + 3] += aa[i] * bb3;
            p[i + 4] += aa[i] * bb4;
        }
        reduce(p);
        for (int i = 0; i < 5; i++) r[i] = (int) p[i];
    }

    /** r = a^2 mod P */
    private static void sqr(int[] a, int[] r) {
        mul(a, a, r);
    }

    /** r = a * s mod P */
    private static void mulScalar(int[] a, int s, int[] r) {
        long t = ((a[0] & 0xffffffffL) * s);
        r[0] = (int) (t & P0); t = (t >>> 26) + ((a[1] & 0xffffffffL) * s);
        r[1] = (int) (t & P1); t = (t >>> 26) + ((a[2] & 0xffffffffL) * s);
        r[2] = (int) (t & P2); t = (t >>> 26) + ((a[3] & 0xffffffffL) * s);
        r[3] = (int) (t & P3); t = (t >>> 26) + ((a[4] & 0xffffffffL) * s);
        r[4] = (int) (t & P_TOP);
    }

    private static void cswap(int swap, int[] x, int[] y) {
        int mask = -swap;
        for (int i = 0; i < 5; i++) {
            int t = mask & (x[i] ^ y[i]);
            x[i] ^= t;
            y[i] ^= t;
        }
    }

    /** Montgomery reduction: reduce 260-bit intermediate to 255-bit field element */
    private static void reduce(long[] p) {
        for (int i = 8; i >= 5; i--) {
            long v = p[i];
            int overflow = (int) (v >>> 26);
            p[i - 5] += overflow * 19L;
            p[i] = v & 0x3ffffffL;
        }
        // Two passes of standard reduction
        for (int i = 4; i >= 1; i--) {
            long v = p[i];
            int overflow = (int) (v >>> 26);
            p[i - 1] += overflow * 19L;
            p[i] = v & 0x3ffffffL;
        }
    }

    /** r = a^(-1) mod P using Fermat's little theorem: a^(P-2) mod P */
    private static void inv(int[] a) {
        int[] r = a.clone();
        int[] t = new int[10];

        // Exponent e = P - 2 = 2^255 - 21
        // Square-and-multiply for 255-bit exponent
        // Start with result = 1
        int[] result = { 1, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
        int[] base = r.clone();

        // We process bits of (P-2) from LSB to MSB
        // P-2 in binary (little-endian):
        // bit 0 = 1 (since P-2 = ...21, 21 = 10101)
        // bits 1,2 = 0, bit 3 = 1, bit 4 = 0, bit 5 = 1, then 0s, then 1 at bit 255
        // But we use the standard square-and-multiply loop
        for (int bit = 0; bit < 255; bit++) {
            // Square: t = result^2
            sqr(result, t);
            // If bit is set, multiply: result = result * base
            // Get bit 'bit' of (P-2)
            if (bitIsSetPMinus2(bit)) {
                mul(t, base, result);
            } else {
                System.arraycopy(t, 0, result, 0, 5);
            }
        }
        System.arraycopy(result, 0, r, 0, 5);
        System.arraycopy(r, 0, a, 0, 5);
    }

    private static boolean bitIsSetPMinus2(int bit) {
        // P-2 = 2^255 - 21
        // In binary: bits 0-4 = 10101 (21), bits 5-254 = 0, bit 255 = 1
        if (bit == 255) return true;  // 2^255 term
        if (bit < 5) {
            // 21 = 0b10101
            return ((21 >>> bit) & 1) == 1;
        }
        return false;  // all other bits are 0
    }

    private static int[] decodeLittleEndian(byte[] b) {
        int[] r = new int[10];
        for (int i = 0; i < 32; i++) {
            int limb = i / 4;
            int byteIdx = i % 4;
            r[limb] |= (b[i] & 0xff) << (byteIdx * 8);
        }
        r[0] &= P0;
        r[1] &= P1;
        r[2] &= P2;
        r[3] &= P3;
        r[4] &= P_TOP;
        return r;
    }

    private static byte[] encodeLittleEndian(int[] a) {
        byte[] r = new byte[32];
        for (int i = 0; i < 32; i++) {
            int limb = i / 4;
            int byteIdx = i % 4;
            r[i] = (byte) ((a[limb] >>> (byteIdx * 8)) & 0xff);
        }
        return r;
    }

    public record Curve25519Session(String headerValue, String sharedSecret) {}
}
