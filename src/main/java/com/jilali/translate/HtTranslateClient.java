package com.jilali.translate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.core.JilaliException;
import com.jilali.translate.dto.AiTranslateUpstreamRequest;
import com.jilali.translate.dto.TranslateUpstreamHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Default {@link TranslateClient} that posts directly to the livehub translator service. Built on
 * the same {@code @Client("jlhub")} bean everything else in the gateway uses — same connection
 * pool, timeouts, and header-propagation filters, so proven-working upstream plumbing is reused
 * untouched.
 */
@Singleton
public class HtTranslateClient implements TranslateClient {

    private static final Logger log = LoggerFactory.getLogger(HtTranslateClient.class);
    private static final String UPSTREAM_PATH = "/translate/v2/ai_translator/translate";

    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public HtTranslateClient(@Client("jlhub") HttpClient httpClient, ObjectMapper mapper) {
        this.httpClient = httpClient;
        this.mapper = mapper;
    }

    @Override
    public String postAiTranslate(AiTranslateUpstreamRequest body, TranslateUpstreamHeaders headers) {
        byte[] bodyBytes;
        try {
            bodyBytes = mapper.writeValueAsBytes(body);
        } catch (IOException e) {
            throw JilaliException.upstreamFailure("serialize translate request", e);
        }

        HttpRequest<byte[]> request = HttpRequest.POST(UPSTREAM_PATH, bodyBytes)
                .header("Content-Type", "application/json")
                .header("Accept", headers.accept())
                .header("x-translate-pub", headers.translatePub())
                .header("x-translate-uid", headers.translateUid())
                .header("x-translate-os", headers.translateOs())
                .header("x-translate-build", headers.translateBuild())
                .header("x-translate-version", headers.translateVersion())
                .header("User-Agent", headers.userAgent())
                .header("Authorization", headers.authorization());

        BlockingHttpClient blocking = httpClient.toBlocking();
        byte[] responseBytes;
        try {
            responseBytes = blocking.retrieve(request, byte[].class);
        } catch (HttpClientResponseException e) {
            String upstreamBody = e.getResponse().getBody(String.class).orElse("<no body>");
            log.error("ai_translator upstream error: status={}, body={}", e.getStatus(), upstreamBody);
            throw JilaliException.upstreamFailure("translate call", e);
        }
        if (responseBytes == null || responseBytes.length == 0) {
            throw JilaliException.upstreamFailure("translate call (empty response)", null);
        }
        return new String(responseBytes, StandardCharsets.UTF_8);
    }
}