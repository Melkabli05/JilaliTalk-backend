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
 * AES-256-ECB with PKCS5 padding; content may be gzip-compressed.
 */
public final class EncbinUtil {

    /** AES-256-ECB with PKCS5 padding — server uses standard padding, not NoPadding. */
    private static final String ALGORITHM = "AES/ECB/PKCS5Padding";

    /** Upstream responses carry many groups (points, privileges, relation, …) we don't map. */
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private EncbinUtil() {}

    /**
     * Encrypts a payload object to ht/encbin bytes.
     *
     * @param payload        JSON-serializable object
     * @param sharedSecretHex 32-byte shared secret as hex string
     * @return raw encrypted bytes
     */
    public static byte[] encrypt(Object payload, String sharedSecretHex) {
        try {
            byte[] plaintext = MAPPER.writeValueAsBytes(payload);
            byte[] key = HexFormat.of().parseHex(sharedSecretHex);
            Cipher cipher = Cipher.getInstance(ALGORITHM, "BC");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
            return cipher.doFinal(plaintext);
        } catch (Exception e) {
            throw new RuntimeException("ht/encbin encryption failed", e);
        }
    }

    /**
     * Decrypts ht/encbin bytes back to a parsed JSON object.
     *
     * @param encrypted        encrypted bytes
     * @param sharedSecretHex  32-byte shared secret as hex string
     * @param clazz            target POJO class
     */
    public static <T> T decrypt(byte[] encrypted, String sharedSecretHex, Class<T> clazz) {
        try {
            byte[] key = HexFormat.of().parseHex(sharedSecretHex);
            Cipher cipher = Cipher.getInstance(ALGORITHM, "BC");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"));
            byte[] decrypted = cipher.doFinal(encrypted);
            byte[] payload = maybeGunzip(decrypted);
            return MAPPER.readValue(payload, clazz);
        } catch (Exception e) {
            throw new RuntimeException("ht/encbin decryption failed", e);
        }
    }

    private static byte[] maybeGunzip(byte[] data) throws Exception {
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