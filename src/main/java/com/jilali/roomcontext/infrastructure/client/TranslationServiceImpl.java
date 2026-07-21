package com.jilali.roomcontext.infrastructure.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.core.AuthTokenHolder;
import com.jilali.core.JilaliException;
import com.jilali.core.JilaliProperties;
import com.jilali.core.JwtUtil;
import com.jilali.crypto.Curve25519SessionGenerator;
import com.jilali.crypto.EncbinUtil;
import com.jilali.roomcontext.domain.service.TranslationService;
import com.jilali.roomcontext.infrastructure.dto.translate.AiTranslateUpstreamRequest;
import com.jilali.roomcontext.infrastructure.dto.translate.SseChunk;
import com.jilali.roomcontext.infrastructure.dto.translate.TranslateUpstreamHeaders;
import io.micronaut.cache.annotation.Cacheable;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.stream.Collectors;

/** Native implementation of the Translation domain service - dedicated to this bounded context,
 *  zero dependency on the legacy translate.* feature package. Orchestrates the Curve25519
 *  session handshake, AES-256-ECB field encryption, the dedicated upstream call
 *  ({@link HtTranslateUpstreamClient}), and SSE chunk decryption/concatenation - the same
 *  algorithm as the legacy implementation (verified correct in the prior audit), re-expressed
 *  natively here rather than delegated to legacy code. */
@Singleton
public class TranslationServiceImpl implements TranslationService {

    private static final Logger log = LoggerFactory.getLogger(TranslationServiceImpl.class);
    private static final String APP_ID = "HelloTalk";
    private static final String MODEL = "qwen_mt_plus";
    private static final String SSE_DATA_PREFIX = "data: ";
    private static final String SSE_DONE_SENTINEL = "data: [DONE]";

    private static final Base64.Encoder BASE64_ENCODER = Base64.getEncoder();
    private static final Base64.Decoder BASE64_DECODER = Base64.getDecoder();

    private final HtTranslateUpstreamClient client;
    private final JilaliProperties properties;
    private final AuthTokenHolder authToken;
    private final ObjectMapper mapper;

    public TranslationServiceImpl(HtTranslateUpstreamClient client, JilaliProperties properties,
                                   AuthTokenHolder authToken, ObjectMapper mapper) {
        this.client = client;
        this.properties = properties;
        this.authToken = authToken;
        this.mapper = mapper;
    }

    /**
     * Cache note: {@code @Cacheable("ai-translate")} is keyed on {@code (text, targetLang)}
     * alone. Translation output has no per-user attribution in the product, and the upstream
     * JWT/uid must be consistent with whichever token is in the cache key regardless of caller.
     * So {@link #jwtUid} deliberately pins to the shared default token (unlike
     * {@link CallerIdentity#currentUserId}, which intentionally prefers the inbound caller's
     * token elsewhere) - switching this to a per-caller uid would silently corrupt the cache
     * (different callers' results would collide) without changing any observable behavior.
     *
     * @throws JilaliException when the upstream call fails or returns no usable chunks
     */
    @Override
    @Cacheable("ai-translate")
    public String translate(String text, String targetLang) {
        var session = Curve25519SessionGenerator.generate(properties.translateServerPubKeyHex());
        long uid = jwtUid();

        TranslateUpstreamHeaders headers = TranslateUpstreamHeaders.forSession(session, uid, authToken);
        AiTranslateUpstreamRequest body = new AiTranslateUpstreamRequest(
                targetLang, null, encryptAndBase64(text, session.sharedSecret()),
                APP_ID, MODEL, false, null);

        String sse = client.postAiTranslate(body, headers);
        return concatenateChunks(sse, session.sharedSecret());
    }

    /** Resolves the shared default token's uid - see the cache-consistency note on {@link #translate}. */
    private long jwtUid() {
        Long uid = JwtUtil.uidFromBearer("Bearer " + authToken.get());
        return uid != null ? uid : 0L;
    }

    private String encryptAndBase64(String plaintext, String sharedSecretHex) {
        byte[] encrypted = EncbinUtil.encryptRaw(plaintext.getBytes(StandardCharsets.UTF_8), sharedSecretHex);
        return BASE64_ENCODER.encodeToString(encrypted);
    }

    private String decryptBase64(String base64Ciphertext, String sharedSecretHex) {
        byte[] decrypted = EncbinUtil.decryptRaw(BASE64_DECODER.decode(base64Ciphertext), sharedSecretHex);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    private String concatenateChunks(String sse, String sharedSecretHex) {
        String joined = sse.lines()
                .filter(line -> line.startsWith(SSE_DATA_PREFIX) && !line.equals(SSE_DONE_SENTINEL))
                .map(this::parseChunkOrNull)
                .filter(chunk -> chunk != null && chunk.data() != null && chunk.data().result() != null)
                .map(chunk -> decryptBase64(chunk.data().result(), sharedSecretHex))
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
