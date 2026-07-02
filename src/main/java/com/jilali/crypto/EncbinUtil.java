package com.jilali.crypto;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.bouncycastle.crypto.BlockCipher;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.paddings.BlockCipherPadding;
import org.bouncycastle.crypto.paddings.PKCS7Padding;
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.bouncycastle.util.encoders.Hex;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPInputStream;

/**
 * Encrypts and decrypts HelloTalk's ht/encbin payloads.
 * Uses BC's low-level AESEngine + PKCS7 padding directly — no JCA Provider routing,
 * which avoids GraalVM Native Image issues with SunJCE's AES implementation.
 */
public final class EncbinUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private EncbinUtil() {}

    public static byte[] encrypt(Object payload, String sharedSecretHex) {
        try {
            byte[] plaintext = MAPPER.writeValueAsBytes(payload);
            byte[] key = Hex.decode(sharedSecretHex);
            byte[] output = new byte[plaintext.length + 32];
            int len = crypt(plaintext, key, output, true);
            byte[] result = new byte[len];
            System.arraycopy(output, 0, result, 0, len);
            return result;
        } catch (java.io.IOException e) {
            throw new RuntimeException("ht/encbin serialization failed", e);
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
            byte[] key = Hex.decode(sharedSecretHex);
            byte[] output = new byte[encrypted.length + 32];
            int len = crypt(encrypted, key, output, false);
            byte[] result = new byte[len];
            System.arraycopy(output, 0, result, 0, len);
            return maybeGunzip(result);
        } catch (java.io.IOException e) {
            throw new RuntimeException("ht/encbin decompression failed", e);
        }
    }

    /**
     * AES-256-ECB with PKCS7 padding using BC's low-level API.
     */
    private static int crypt(byte[] input, byte[] key, byte[] output, boolean forEncryption) {
        try {
            BlockCipher engine = new AESEngine();
            BlockCipherPadding padding = new PKCS7Padding();
            PaddedBufferedBlockCipher cipher = new PaddedBufferedBlockCipher(engine, padding);
            cipher.init(forEncryption, new org.bouncycastle.crypto.params.KeyParameter(key));
            int len = cipher.processBytes(input, 0, input.length, output, 0);
            len += cipher.doFinal(output, len);
            return len;
        } catch (Exception e) {
            throw new RuntimeException("BC AES cipher failed", e);
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
