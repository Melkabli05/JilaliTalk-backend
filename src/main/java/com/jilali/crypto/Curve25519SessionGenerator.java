package com.jilali.crypto;

import org.bouncycastle.util.encoders.Hex;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.util.HexFormat;

/**
 * Generates Curve25519 session keys for HelloTalk's ht/encbin encryption.
 * Uses Java 17+'s built-in X25519 key agreement (SunJCE), NOT BouncyCastle —
 * BC's X25519 is broken on GraalVM Native Image + Java 25.
 *
 * Mirrors the logic in {@code userinfo/generatorkey.js}:
 * ECDH(myPrivateKey, serverStaticPubKey) → shared secret.
 */
public final class Curve25519SessionGenerator {

    private Curve25519SessionGenerator() {}

    /**
     * Generates a fresh X25519 key pair and derives the shared secret with the server.
     *
     * @param serverPublicKeyHex the server's static public key (from config: jilali.server-pub-key-hex)
     * @return session containing the {@code x-ht-pub} header value and AES key material
     */
    public static Curve25519Session generate(String serverPublicKeyHex) {
        try {
            // 1. Generate a random X25519 key pair using Java's built-in generator
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("X25519");
            keyGen.initialize(256, new SecureRandom());
            KeyPair keyPair = keyGen.generateKeyPair();

            // 2. Derive shared secret: ECDH(myPrivateKey, serverPublicKey)
            // The server's public key is a raw 32-byte X25519 point — wrap it in X509EncodedKeySpec
            byte[] serverPubKeyBytes = HexFormat.of().parseHex(serverPublicKeyHex);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(
                wrap25519Point(serverPubKeyBytes, /* isPrivate= */ false)
            );
            KeyFactory keyFactory = KeyFactory.getInstance("X25519");
            PublicKey serverPubKey = keyFactory.generatePublic(keySpec);

            // 3. Do the ECDH key agreement
            javax.crypto.KeyAgreement ka = javax.crypto.KeyAgreement.getInstance("X25519");
            ka.init(keyPair.getPrivate());
            ka.doPhase(serverPubKey, true);
            byte[] sharedSecretBytes = ka.generateSecret();

            // 4. Build x-ht-pub header: serverStaticPubKey (32 bytes hex) + myPublicKey (32 bytes hex)
            byte[] myPubKeyEncoded = keyPair.getPublic().getEncoded();
            // The encoded form is X509 — strip the 12-byte algorithm ID prefix, keep the last 32 bytes
            String headerValue = serverPublicKeyHex + Hex.toHexString(extractRaw25519(myPubKeyEncoded));
            String sharedSecret = HexFormat.of().formatHex(sharedSecretBytes);

            return new Curve25519Session(headerValue, sharedSecret);
        } catch (Exception e) {
            throw new RuntimeException("Curve25519 key generation failed", e);
        }
    }

    /**
     * Wraps a raw 32-byte X25519 point into PKCS#8 / X509 encoded form.
     * Java's X25519 KeyFactory expects the IEEE P1363 encoding (algorithm ID + point),
     * not a raw 32-byte scalar.
     */
    private static byte[] wrap25519Point(byte[] raw, boolean isPrivate) {
        // PKCS#8 for private / X509 for public — 12-byte algorithm ID header
        byte[] header = isPrivate
            ? Hex.decode("302e020100300506032b657004220420")
            : Hex.decode("302a300506032b6571043d302b020100300506032b6570042204");
        byte[] result = new byte[header.length + 32];
        System.arraycopy(header, 0, result, 0, header.length);
        System.arraycopy(raw, 0, result, header.length, 32);
        return result;
    }

    /**
     * Extracts the raw 32-byte X25519 point from a Java-encoded key's byte array.
     * The encoded form is: 0x30 0xXX 0x02 0x20 <32 bytes> (SPKI for public) or
     * 0x30 0xXX 0x02 0x20 <32 bytes> (PKCS#8 for private).
     */
    private static byte[] extractRaw25519(byte[] encoded) {
        // X509 SPKI for X25519: [12-byte header][32-byte point]
        // Copy the last 32 bytes into a new array
        byte[] result = new byte[32];
        System.arraycopy(encoded, encoded.length - 32, result, 0, 32);
        return result;
    }

    public record Curve25519Session(String headerValue, String sharedSecret) {}
}
