package com.jilali.crypto;

import org.bouncycastle.crypto.generators.X25519KeyPairGenerator;
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters;
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.X25519PublicKeyParameters;
import org.bouncycastle.util.encoders.Hex;

import java.security.SecureRandom;

/**
 * Generates Curve25519 session keys for HelloTalk's ht/encbin encryption.
 *
 * Uses BC's X25519PrivateKeyParameters.generateSecret() directly on the private key
 * to compute the shared secret — this is the correct BC API and avoids the broken
 * X25519Agreement class on GraalVM Native Image.
 *
 * Mirrors {@code userinfo/generatorkey.js}.
 */
public final class Curve25519SessionGenerator {

    private Curve25519SessionGenerator() {}

    public static Curve25519Session generate(String serverPublicKeyHex) {
        try {
            // 1. Generate a fresh X25519 key pair using BC
            var keyGen = new X25519KeyPairGenerator();
            keyGen.init(new X25519KeyGenerationParameters(new SecureRandom()));
            var keyPair = keyGen.generateKeyPair();

            var myPriv = (X25519PrivateKeyParameters) keyPair.getPrivate();
            var myPub = (X25519PublicKeyParameters) keyPair.getPublic();

            // 2. Compute shared secret: ECDH(myPriv, serverPub)
            // Call generateSecret() directly on the private key — NOT X25519Agreement
            byte[] serverPubBytes = Hex.decode(serverPublicKeyHex);
            byte[] sharedSecret = new byte[32];
            myPriv.generateSecret(new X25519PublicKeyParameters(serverPubBytes, 0), sharedSecret, 0);

            // 3. Build x-ht-pub header: serverPubKey_hex + myPubKey_hex
            String headerValue = serverPublicKeyHex + Hex.toHexString(myPub.getEncoded());
            String sharedSecretHex = Hex.toHexString(sharedSecret);

            return new Curve25519Session(headerValue, sharedSecretHex);
        } catch (Exception e) {
            throw new RuntimeException("Curve25519 key generation failed", e);
        }
    }

    public record Curve25519Session(String headerValue, String sharedSecret) {}
}
