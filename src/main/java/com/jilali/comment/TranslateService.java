package com.jilali.comment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.core.JilaliException;
import com.jilali.core.JilaliProperties;
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
import reactor.core.publisher.Mono;

/**
 * Translates text via the HelloTalk AI translation API using the standard Java HttpClient.
 *
 * <p>The upstream returns an SSE stream where each {@code data:} line contains a JSON object with a
 * base64-encoded translation fragment in {@code result}. We collect all fragments, decode them, and
 * return the full translated string.
 */
@Singleton
public class TranslateService {

    private final HttpClient httpClient;
    private final ObjectMapper json;
    private final JilaliProperties properties;

    public TranslateService(ObjectMapper json, JilaliProperties properties) {
        this.json = json;
        this.properties = properties;
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * Translates {@code text} to {@code targetLang} via the HelloTalk AI service.
     *
     * @return the translated text, never null
     */
    public Mono<String> translate(String text, String targetLang) {
        return Mono.fromCallable(() -> {
            // Text is sent as plain base64 (no additional encryption).
            String encodedText = Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
            // AES-128-CBC encryption with random IV (16 bytes), prepended to ciphertext per HelloTalk protocol.
            byte[] keyBytes = properties.agoraCipherKey().getBytes(StandardCharsets.US_ASCII);
            SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");
            Cipher aes = Cipher.getInstance("AES/CBC/PKCS5Padding");
            aes.init(Cipher.ENCRYPT_MODE, key);
            byte[] encrypted = aes.doFinal(encodedText.getBytes(StandardCharsets.UTF_8));
            byte[] iv = aes.getIV();
            byte[] ivPlusCt = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, ivPlusCt, 0, iv.length);
            System.arraycopy(encrypted, 0, ivPlusCt, iv.length, encrypted.length);
            String encryptedText = Base64.getEncoder().encodeToString(ivPlusCt);

            var body = """
                {"preferred_target_lang":"%s","context":null,"text":"%s","app_id":"HelloTalk","model":"qwen_mt_plus","transliterate":false,"fallback_target_lang":null}
                """.formatted(targetLang, encryptedText);

            var requestBuilder = java.net.http.HttpRequest.newBuilder()
                    .uri(URI.create("https://api-global.hellotalk8.com/translate/v2/ai_translator/translate"))
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .header("x-translate-pub", properties.translatePubKey())
                    .header("x-translate-os", "ios")
                    .header("x-translate-build", "70")
                    .header("x-translate-version", "6.3.0")
                    .header("x-translate-uid", "131331894")
                    .POST(BodyPublishers.ofString(body, StandardCharsets.UTF_8));

            // Add auth: fall back to service account token.
            var auth = properties.defaultAuthToken();
            if (auth != null && !auth.isBlank()) {
                requestBuilder.header("authorization", "Bearer " + auth);
            }

            HttpResponse<String> response;
            try {
                response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            } catch (IOException | InterruptedException e) {
                throw new JilaliException(1, "Translate request failed: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
            }

            if (response.statusCode() != 200) {
                throw new JilaliException(response.statusCode(), "Translate upstream failed: " + response.body(), HttpStatus.BAD_GATEWAY);
            }

            return decodeSseBody(response.body());
        });
    }

    private String decodeSseBody(String body) {
        List<String> b64Chunks = new java.util.ArrayList<>();
        for (String line : body.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("data: ")) {
                String payload = trimmed.substring(6).trim();
                if (payload.equals("[DONE]")) {
                    break;
                }
                b64Chunks.add(extractResultField(payload));
            }
        }

        if (b64Chunks.isEmpty()) {
            return "";
        }

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
                throw new JilaliException(1, "Unexpected translate SSE payload: " + payload, HttpStatus.BAD_GATEWAY);
            }
            return resultNode.asText();
        } catch (JilaliException e) {
            throw e;
        } catch (Exception e) {
            throw new JilaliException(1, "Failed to parse translate SSE chunk: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }
}
