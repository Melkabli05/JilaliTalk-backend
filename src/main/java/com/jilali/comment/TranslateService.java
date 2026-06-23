package com.jilali.comment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.core.JilaliException;
import com.jilali.core.JilaliProperties;
import com.jilali.crypto.Curve25519SessionGenerator;
import io.micronaut.http.HttpStatus;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Translates text via the HelloTalk AI translation API using the standard Java HttpClient.
 *
 * Flow:
 * 1. Derive Curve25519 shared secret
 * 2. POST to /translate/v1/sts with ht/encbin body → get translate key
 * 3. POST to /translate/v1/ai_translator/translate with translate key
 */
@Singleton
public class TranslateService {

    private static final Logger log = LoggerFactory.getLogger(TranslateService.class);

    private final HttpClient httpClient;
    private final ObjectMapper json;
    private final JilaliProperties properties;

    public TranslateService(ObjectMapper json, JilaliProperties properties) {
        this.json = json;
        this.properties = properties;
        this.httpClient = HttpClient.newHttpClient();
    }

    public Mono<String> translate(String text, String targetLang) {
        return Mono.fromCallable(() -> {
            var session = Curve25519SessionGenerator.generate(properties.serverPubKeyHex());

            // STS body matching the captured format.
            String stsBody = """
                {"app_id":"HelloTalk","device_id":"%s","os":"ios","os_version":"18.5","lang":"en","channel":"app_store","version":"6.2.0"}
                """.formatted(properties.deviceId());

            byte[] encryptedSts = aesEncrypt(
                    stsBody.getBytes(StandardCharsets.UTF_8), session.sharedSecret());

            var stsRequest = java.net.http.HttpRequest.newBuilder()
                    .uri(URI.create("https://api-global.hellotalk8.com/translate/v1/sts"))
                    .header("Content-Type", "application/json")
                    .header("x-translate-pub", session.headerValue())
                    .header("x-translate-os", "ios")
                    .header("x-translate-build", "70")
                    .header("x-translate-version", "6.2.0")
                    .header("x-translate-uid", "131331894")
                    .header("x-translate-channel", "app_store")
                    .header("Accept", "*/*")
                    .header("priority", "u=3")
                    .POST(BodyPublishers.ofByteArray(encryptedSts))
                    .build();

            HttpResponse<byte[]> stsResponse;
            try {
                stsResponse = httpClient.send(stsRequest, HttpResponse.BodyHandlers.ofByteArray());
            } catch (IOException | InterruptedException e) {
                throw new JilaliException(1, "STS request failed: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
            }

            if (stsResponse.statusCode() != 200) {
                throw new JilaliException(stsResponse.statusCode(),
                        "STS upstream failed: " + stsResponse.statusCode(), HttpStatus.BAD_GATEWAY);
            }

            String decryptedSts = aesDecrypt(stsResponse.body(), session.sharedSecret());
            log.info("TranslateService STS decrypted: {}", decryptedSts);
            String translateKey = extractKey(decryptedSts);

            // Translate request: plain base64 text (no encryption on text field).
            String encodedText = Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
            String translateBody = """
                {"text":"%s","app_id":"HelloTalk","model":"qwen_mt_plus","context":"%s","fallback_target_lang":null,"preferred_target_lang":"%s","transliterate":false}
                """.formatted(encodedText, encodedText, targetLang);

            // Encrypt translate body with the key from STS response.
            byte[] encryptedTranslate = aesEncrypt(
                    translateBody.getBytes(StandardCharsets.UTF_8), translateKey);

            var translateRequest = java.net.http.HttpRequest.newBuilder()
                    .uri(URI.create("https://api-global.hellotalk8.com/translate/v1/ai_translator/translate"))
                    .header("Content-Type", "application/json")
                    .header("x-translate-pub", session.headerValue())
                    .header("x-translate-os", "ios")
                    .header("x-translate-build", "115")
                    .header("x-translate-version", "6.2.0")
                    .header("x-translate-uid", "131331894")
                    .header("authorization", "Bearer " + properties.defaultAuthToken())
                    .header("Accept", "text/event-stream")
                    .POST(BodyPublishers.ofByteArray(encryptedTranslate))
                    .build();

            HttpResponse<String> translateResponse;
            try {
                translateResponse = httpClient.send(translateRequest, HttpResponse.BodyHandlers.ofString());
            } catch (IOException | InterruptedException e) {
                throw new JilaliException(1, "Translate request failed: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
            }

            if (translateResponse.statusCode() != 200) {
                throw new JilaliException(translateResponse.statusCode(),
                        "Translate upstream failed: " + translateResponse.body(), HttpStatus.BAD_GATEWAY);
            }

            return decodeSseBody(translateResponse.body());
        });
    }

    private byte[] aesEncrypt(byte[] plaintext, String keyHex) {
        try {
            byte[] key = hexToBytes(keyHex);
            SecretKeySpec secretKey = new SecretKeySpec(key, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            return cipher.doFinal(plaintext);
        } catch (Exception e) {
            throw new JilaliException(1, "AES encrypt failed: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    private String aesDecrypt(byte[] ciphertext, String keyHex) {
        try {
            byte[] key = hexToBytes(keyHex);
            SecretKeySpec secretKey = new SecretKeySpec(key, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decrypted = cipher.doFinal(ciphertext);
            int padLen = decrypted[decrypted.length - 1] & 0xff;
            return new String(decrypted, 0, Math.max(decrypted.length - padLen, 0), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new JilaliException(1, "AES decrypt failed: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        }
        return out;
    }

    private String extractKey(String jsonPayload) {
        try {
            JsonNode node = json.readTree(jsonPayload);
            JsonNode keyNode = node.path("key");
            if (keyNode.isMissingNode()) {
                keyNode = node.path("translate_key");
            }
            if (keyNode.isMissingNode()) {
                keyNode = node.path("data").path("key");
            }
            if (!keyNode.isMissingNode()) {
                return keyNode.asText();
            }
            // STS response is the raw key string.
            return jsonPayload.trim();
        } catch (Exception e) {
            return jsonPayload.trim();
        }
    }

    private String decodeSseBody(String body) {
        List<String> b64Chunks = new java.util.ArrayList<>();
        for (String line : body.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("data: ")) {
                String payload = trimmed.substring(6).trim();
                if (payload.equals("[DONE]")) break;
                b64Chunks.add(extractResultField(payload));
            }
        }
        if (b64Chunks.isEmpty()) return "";
        var sb = new StringBuilder();
        for (String b64 : b64Chunks) {
            byte[] bytes = Base64.getDecoder().decode(b64);
            sb.append(new String(bytes, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private String extractResultField(String payload) {
        try {
            JsonNode node = json.readTree(payload);
            JsonNode resultNode = node.path("data").path("result");
            if (resultNode.isMissingNode()) {
                throw new JilaliException(1, "Unexpected SSE payload: " + payload, HttpStatus.BAD_GATEWAY);
            }
            return resultNode.asText();
        } catch (JilaliException e) { throw e; }
        catch (Exception e) {
            throw new JilaliException(1, "Parse SSE chunk failed: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }
}
