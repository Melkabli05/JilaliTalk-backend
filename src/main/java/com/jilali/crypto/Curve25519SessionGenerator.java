package com.jilali.crypto;

import com.jilali.core.JilaliProperties;
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

    /**
     * Generates a fresh X25519 key pair and derives the shared secret with the server.
     *
     * @param serverPublicKeyHex the server's static public key (from config: jilali.server-pub-key-hex)
     * @return session containing the {@code x-ht-pub} header value and AES key material
     */
    public static Curve25519Session generate(String serverPublicKeyHex) {
        X25519KeyPairGenerator keyGen = new X25519KeyPairGenerator();
        keyGen.init(new X25519KeyGenerationParameters(new SecureRandom()));
        var keyPair = keyGen.generateKeyPair();

        var myPrivateKey = (X25519PrivateKeyParameters) keyPair.getPrivate();
        var myPublicKey  = (X25519PublicKeyParameters) keyPair.getPublic();

        X25519Agreement agreement = new X25519Agreement();
        agreement.init(myPrivateKey);
        byte[] sharedSecretBytes = new byte[32];
        agreement.calculateAgreement(
            new X25519PublicKeyParameters(Hex.decode(serverPublicKeyHex), 0),
            sharedSecretBytes, 0);

        String headerValue  = serverPublicKeyHex + Hex.toHexString(myPublicKey.getEncoded());
        String sharedSecret = Hex.toHexString(sharedSecretBytes);

        return new Curve25519Session(headerValue, sharedSecret);
    }

    public record Curve25519Session(String headerValue, String sharedSecret) {}
}
