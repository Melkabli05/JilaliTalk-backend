package com.jilali.translate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.core.JilaliException;
import com.jilali.core.JilaliProperties;
import com.jilali.core.JwtUtil;
import com.jilali.crypto.Curve25519SessionGenerator;
import com.jilali.translate.codec.EncryptedFieldCodec;
import com.jilali.translate.dto.AiTranslateUpstreamRequest;
import com.jilali.translate.dto.SseChunk;
import com.jilali.translate.dto.TranslateUpstreamHeaders;
import io.micronaut.cache.annotation.Cacheable;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Orchestrates a single {@code POST /api/translate} request:
 * <ol>
 *   <li>Derives a fresh Curve25519 session against the translator's static public key.</li>
 *   <li>Encrypts the user's text under that session key and base64-encodes it for transport.</li>
 *   <li>Delegates the actual HTTP call to {@link TranslateClient}.</li>
 *   <li>Parses the SSE stream, decrypting and concatenating each chunk's {@code result} field
 *       in arrival order.</li>
 * </ol>
 * <p>
 * Cached by {@code (text, targetLang)} so a popular comment translated into a given language
 * pays the encrypted handshake + AES round-trip once across all viewers. The cache key is set by
 * {@link Cacheable#value()} and the cache itself is defined in {@code application.yml}.
 */
@Singleton
public class TranslateService {

    private static final Logger log = LoggerFactory.getLogger(TranslateService.class);

    private static final String APP_ID = "HelloTalk";
    private static final String MODEL = "qwen_mt_plus";

    private static final String SSE_DATA_PREFIX = "data: ";
    private static final String SSE_DONE_SENTINEL = "data: [DONE]";

    private final TranslateClient client;
    private final EncryptedFieldCodec codec;
    private final JilaliProperties properties;
    private final ObjectMapper mapper;

    public TranslateService(TranslateClient client,
                            EncryptedFieldCodec codec,
                            JilaliProperties properties,
                            ObjectMapper mapper) {
        this.client = client;
        this.codec = codec;
        this.properties = properties;
        this.mapper = mapper;
    }

    /**
     * Translates {@code text} into {@code targetLang} via the upstream AI translator.
     *
     * @throws JilaliException when the upstream call fails or returns no usable chunks
     */
    @Cacheable("ai-translate")
    public String translate(String text, String targetLang) {
        var session = Curve25519SessionGenerator.generate(properties.translateServerPubKeyHex());
        long uid = jwtUid(properties);

        TranslateUpstreamHeaders headers = TranslateUpstreamHeaders.forSession(session, uid, properties);
        AiTranslateUpstreamRequest body = new AiTranslateUpstreamRequest(
                targetLang,
                null,
                codec.encryptAndBase64(text, session.sharedSecret()),
                APP_ID,
                MODEL,
                /* transliterate */ false,
                /* fallback */ null
        );

        String sse = client.postAiTranslate(body, headers);
        return concatenateChunks(sse, session.sharedSecret());
    }

    /** Resolves the caller's uid from the JWT that will be sent upstream. */
    private static long jwtUid(JilaliProperties properties) {
        Long uid = JwtUtil.uidFromBearer("Bearer " + properties.defaultAuthToken());
        return uid != null ? uid : 0L;
    }

    /**
     * Parses the SSE stream into a sequence of decrypted chunks concatenated in arrival order.
     * Filters out non-{@code data:} lines (the empty separators SSE uses between events) and the
     * {@code data: [DONE]} sentinel. Individual malformed chunks are logged and skipped, but an
     * entirely-empty stream is an error — upstream would never deliberately return zero chunks
     * for a valid request.
     */
    private String concatenateChunks(String sse, String sharedSecretHex) {
        String joined = sse.lines()
                .filter(line -> line.startsWith(SSE_DATA_PREFIX) && !line.equals(SSE_DONE_SENTINEL))
                .map(this::parseChunkOrNull)
                .filter(Objects::nonNull)
                .map(chunk -> codec.decryptBase64(chunk.result(), sharedSecretHex))
                .collect(Collectors.joining());

        if (joined.isEmpty()) {
            throw JilaliException.upstreamFailure("translate call (no SSE chunks decoded)", null);
        }
        return joined;
    }

    private SseChunk parseChunkOrNull(String line) {
        try {
            return mapper.readValue(line.substring(SSE_DATA_PREFIX.length()), SseChunk.class);
        } catch (Exception e) {
            log.warn("ai_translator: failed to decode SSE chunk: {}", line, e);
            return null;
        }
    }
}