package com.jilali.translate.codec;

import com.jilali.crypto.EncbinUtil;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Encrypts and decrypts single per-field payloads the way the translator endpoints want:
 * AES-256-ECB under the Curve25519-derived session key, then base64 for transport.
 * <p>
 * Lives in its own collaborator so the streaming SSE parser doesn't reallocate a
 * {@link Base64.Decoder} for every chunk, and so a future translator endpoint can reuse the same
 * field-level encryption envelope without copy-paste.
 */
@Singleton
public final class EncryptedFieldCodec {

    private static final Base64.Encoder ENCODER = Base64.getEncoder();
    private static final Base64.Decoder DECODER = Base64.getDecoder();

    /** Raw AES-256-ECB with PKCS7 padding under the shared secret. */
    public byte[] encrypt(byte[] plaintext, String sharedSecretHex) {
        return EncbinUtil.encryptRaw(plaintext, sharedSecretHex);
    }

    /** Decrypts a raw AES-256-ECB ciphertext. Counterpart to {@link #encrypt(byte[], String)}. */
    public byte[] decrypt(byte[] ciphertext, String sharedSecretHex) {
        return EncbinUtil.decryptRaw(ciphertext, sharedSecretHex);
    }

    /** Encrypts a UTF-8 string and base64-encodes the result for inline JSON transport. */
    public String encryptAndBase64(String plaintext, String sharedSecretHex) {
        return ENCODER.encodeToString(encrypt(plaintext.getBytes(StandardCharsets.UTF_8), sharedSecretHex));
    }

    /** Decrypts a base64-encoded ciphertext back to a UTF-8 string. */
    public String decryptBase64(String base64Ciphertext, String sharedSecretHex) {
        return new String(decrypt(DECODER.decode(base64Ciphertext), sharedSecretHex), StandardCharsets.UTF_8);
    }
}