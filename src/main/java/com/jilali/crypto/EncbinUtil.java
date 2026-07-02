package com.jilali.crypto;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.Security;
import java.util.HexFormat;
import java.util.zip.GZIPInputStream;

/**
 * Encrypts and decrypts HelloTalk's ht/encbin payloads.
 * AES-256-ECB with PKCS5 padding.
 *
 * Uses Java's built-in Cipher with BC as the provider — this avoids
 * GraalVM Native Image's SunJCE routing issues while using a working
 * AES implementation.
 */
public final class EncbinUtil {

    private static final String ALGORITHM = "AES/ECB/PKCS5Padding";

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    static {
        // Register BC as the preferred provider for AES
        Security.insertProviderAt(new BouncyCastleProvider(), 1);
    }

    private EncbinUtil() {}

    public static byte[] encrypt(Object payload, String sharedSecretHex) {
        try {
            byte[] plaintext = MAPPER.writeValueAsBytes(payload);
            byte[] key = HexFormat.of().parseHex(sharedSecretHex);
            Cipher cipher = Cipher.getInstance(ALGORITHM, "BC");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
            return cipher.doFinal(plaintext);
        } catch (java.io.IOException e) {
            throw new RuntimeException("ht/encbin serialization failed", e);
        } catch (Exception e) {
            throw new RuntimeException("ht/encbin encryption failed", e);
        }
    }

    public static <T> T decrypt(byte[] encrypted, String sharedSecretHex, Class<T> clazz) {
        try {
            byte[] json = decryptToJson(encrypted, sharedSecretHex);
            return MAPPER.readValue(json, clazz);
        } catch (java.io.IOException e) {
            throw new RuntimeException("ht/encbin deserialization failed", e);
        } catch (Exception e) {
            throw new RuntimeException("ht/encbin decryption failed", e);
        }
    }

    public static byte[] decryptToJson(byte[] encrypted, String sharedSecretHex) {
        try {
            byte[] key = HexFormat.of().parseHex(sharedSecretHex);
            Cipher cipher = Cipher.getInstance(ALGORITHM, "BC");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"));
            byte[] decrypted = cipher.doFinal(encrypted);
            return maybeGunzip(decrypted);
        } catch (java.io.IOException e) {
            throw new RuntimeException("ht/encbin decompression failed", e);
        } catch (Exception e) {
            throw new RuntimeException("ht/encbin decryption failed", e);
        }
    }

    private static byte[] maybeGunzip(byte[] data) throws java.io.IOException {
        if (data.length > 2 && (data[0] & 0xff) == 0x1f && (data[1] & 0xff) == 0x8b) {
            try (var gzis = new GZIPInputStream(new ByteArrayInputStream(data));
                 var baos = new ByteArrayOutputStream()) {
                gzis.transferTo(baos);
                return baos.toByteArray();
            }
        }
        return data;
    }
}
