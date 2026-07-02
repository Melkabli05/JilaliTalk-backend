package com.jilali.crypto;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.bouncycastle.crypto.BlockCipher;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.paddings.BlockCipherPadding;
import org.bouncycastle.crypto.paddings.PKCS7Padding;
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPInputStream;

/**
 * Encrypts and decrypts HelloTalk's ht/encbin payloads.
 * AES-256-ECB with PKCS7 padding using BC's low-level API directly.
 * This avoids JCA provider routing issues on GraalVM Native Image.
 */
public final class EncbinUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private EncbinUtil() {}

    public static byte[] encrypt(Object payload, String sharedSecretHex) {
        try {
            byte[] plaintext = MAPPER.writeValueAsBytes(payload);
            byte[] key = hexStringToBytes(sharedSecretHex);
            byte[] output = new byte[plaintext.length + 32];
            int len = crypt(plaintext, key, output, true);
            byte[] result = new byte[len];
            System.arraycopy(output, 0, result, 0, len);
            return result;
        } catch (java.io.IOException e) {
            throw new RuntimeException("ht/encbin serialization failed: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("ht/encbin encryption failed: " + e.getClass().getName() + " " + e.getMessage(), e);
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
            byte[] key = hexStringToBytes(sharedSecretHex);
            byte[] output = new byte[encrypted.length + 32];
            int len = crypt(encrypted, key, output, false);
            byte[] result = new byte[len];
            System.arraycopy(output, 0, result, 0, len);
            return maybeGunzip(result);
        } catch (java.io.IOException e) {
            throw new RuntimeException("ht/encbin decompression failed", e);
        } catch (Exception e) {
            throw new RuntimeException("ht/encbin decryption failed", e);
        }
    }

    private static int crypt(byte[] input, byte[] key, byte[] output, boolean forEncryption) throws Exception {
        try {
            BlockCipher engine = new AESEngine();
            BlockCipherPadding padding = new PKCS7Padding();
            PaddedBufferedBlockCipher cipher = new PaddedBufferedBlockCipher(engine, padding);
            cipher.init(forEncryption, new org.bouncycastle.crypto.params.KeyParameter(key));
            int len = cipher.processBytes(input, 0, input.length, output, 0);
            len += cipher.doFinal(output, len);
            return len;
        } catch (Exception e) {
            throw new RuntimeException("AES crypt failed: keyLen=" + key.length + " inputLen=" + input.length, e);
        }
    }

    private static byte[] hexStringToBytes(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int hi = Character.digit(hex.charAt(i), 16);
            int lo = Character.digit(hex.charAt(i + 1), 16);
            out[i / 2] = (byte) ((hi << 4) | lo);
        }
        return out;
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
