package com.jilali.crypto;

import org.bouncycastle.crypto.agreement.X25519Agreement;
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator;
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters;
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.X25519PublicKeyParameters;
import org.bouncycastle.util.encoders.Hex;

import java.security.SecureRandom;

/**
 * Generates Curve25519 session keys for HelloTalk's ht/encbin encryption.
 * Mirrors the logic in the Node.js {@code generatorkey.js}.
 */
public final class Curve25519SessionGenerator {

    private Curve25519SessionGenerator() {}

    /** Server's static public key (confirmed from APK decompilation). */
    private static final String SERVER_PUB_KEY_HEX =
        "f684f611b895a5d3abc124a20ca2dfd397662318cfd4fd74b80aba478c17ca68";

    /**
     * Generates a fresh X25519 key pair and derives the shared secret with the server.
     *
     * @return session containing the {@code x-ht-pub} header value and AES key material
     */
    public static Curve25519Session generate() {
        X25519KeyPairGenerator keyGen = new X25519KeyPairGenerator();
        keyGen.init(new X25519KeyGenerationParameters(new SecureRandom()));
        var keyPair = keyGen.generateKeyPair();

        var myPrivateKey = (X25519PrivateKeyParameters) keyPair.getPrivate();
        var myPublicKey  = (X25519PublicKeyParameters) keyPair.getPublic();

        X25519Agreement agreement = new X25519Agreement();
        agreement.init(myPrivateKey);
        byte[] sharedSecretBytes = new byte[32];
        agreement.calculateAgreement(
            new X25519PublicKeyParameters(Hex.decode(SERVER_PUB_KEY_HEX), 0),
            sharedSecretBytes, 0);

        String headerValue  = SERVER_PUB_KEY_HEX + Hex.toHexString(myPublicKey.getEncoded());
        String sharedSecret = Hex.toHexString(sharedSecretBytes);

        return new Curve25519Session(headerValue, sharedSecret);
    }

    public record Curve25519Session(String headerValue, String sharedSecret) {}
}