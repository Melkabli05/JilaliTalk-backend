package com.jilali.crypto;

import org.bouncycastle.crypto.generators.X25519KeyPairGenerator;
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters;
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.X25519PublicKeyParameters;
import org.bouncycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.XECPrivateKey;
import java.security.spec.X509EncodedKeySpec;

/**
 * Generates Curve25519 session keys for HelloTalk's ht/encbin encryption.
 *
 * Uses Java 17+ built-in X25519 (SunEC) for both key generation and key agreement.
 *
 * Mirrors the logic in {@code userinfo/generatorkey.js}.
 */
public final class Curve25519SessionGenerator {

    private Curve25519SessionGenerator() {}

    public static Curve25519Session generate(String serverPublicKeyHex) {
        try {
            // 1. Generate a fresh X25519 key pair using Java's built-in SunEC
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("X25519");
            keyGen.initialize(256, new SecureRandom());
            KeyPair keyPair = keyGen.generateKeyPair();

            // 2. ECDH key agreement using SunEC
            javax.crypto.KeyAgreement ka = javax.crypto.KeyAgreement.getInstance("X25519");
            ka.init(keyPair.getPrivate());

            // 3. Decode server's raw 32-byte public key and wrap it for KeyAgreement
            byte[] serverPubKeyBytes = Hex.decode(serverPublicKeyHex);
            byte[] serverPubKeyX509 = encodeRawToX509(serverPubKeyBytes);
            KeyFactory kf = KeyFactory.getInstance("X25519");
            PublicKey serverPubKey = kf.generatePublic(new X509EncodedKeySpec(serverPubKeyX509));

            ka.doPhase(serverPubKey, true);
            byte[] sharedSecret = ka.generateSecret();

            // 4. Build x-ht-pub header: serverStaticPubKey_hex + myPublicKey_hex
            byte[] myPubRaw = extractRawU((java.security.interfaces.XECPublicKey) keyPair.getPublic());

            String headerValue = serverPublicKeyHex + Hex.toHexString(myPubRaw);
            String sharedSecretHex = Hex.toHexString(sharedSecret);

            return new Curve25519Session(headerValue, sharedSecretHex);
        } catch (Exception e) {
            throw new RuntimeException("Curve25519 key generation failed", e);
        }
    }

    /**
     * Wraps a raw 32-byte X25519 public key coordinate into X.509 SPKI format.
     */
    private static byte[] encodeRawToX509(byte[] raw32) {
        // RFC 8410 X25519 public key SPKI:
        //   SEQUENCE { OID 1.3.101.110, NULL } || BIT STRING { 0x00 || u }
        byte[] algoId = Hex.decode("302a300506032b6571043d302b020100300506032b6570042204");
        byte[] bitString = new byte[35];
        bitString[0] = 0x03;         // BIT STRING tag
        bitString[1] = 0x21;         // 33 contents bytes
        bitString[2] = 0x00;         // unused bits = 0
        System.arraycopy(raw32, 0, bitString, 3, 32);
        byte[] spki = new byte[algoId.length + bitString.length + 4];
        int pos = 0;
        spki[pos++] = 0x30;                           // SEQUENCE tag
        spki[pos++] = (byte) (algoId.length + bitString.length + 2);
        System.arraycopy(algoId, 0, spki, pos, algoId.length);
        pos += algoId.length;
        System.arraycopy(bitString, 0, spki, pos, bitString.length);
        return spki;
    }

    /**
     * Extracts the raw 32-byte u-coordinate from Java's XECPublicKey.
     */
    private static byte[] extractRawU(java.security.interfaces.XECPublicKey pubKey) {
        BigInteger u = pubKey.getU();
        byte[] raw = u.toByteArray();
        // BigInteger.toByteArray() returns sign-magnitude: if high bit set, adds 0x00 prefix
        if (raw.length == 33 && raw[0] == 0x00) {
            byte[] r = new byte[32];
            System.arraycopy(raw, 1, r, 0, 32);
            return r;
        } else if (raw.length < 32) {
            byte[] r = new byte[32];
            System.arraycopy(raw, 0, r, 32 - raw.length, raw.length);
            return r;
        }
        return raw;
    }

    public record Curve25519Session(String headerValue, String sharedSecret) {}
}
