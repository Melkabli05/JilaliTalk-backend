package com.jilali.translate;

import com.jilali.translate.dto.AiTranslateUpstreamRequest;
import com.jilali.translate.dto.TranslateUpstreamHeaders;

/**
 * Boundary between the translate orchestrator ({@link TranslateService}) and any concrete upstream
 * implementation. Defined so {@link TranslateService} never imports Micronaut HTTP types directly
 * — the service composes the request, hands it to the client, and gets back the raw SSE stream
 * without knowing how it traveled there.
 */
public interface TranslateClient {

    /**
     * POSTs the encoded request to the AI translator endpoint and returns the raw
     * {@code text/event-stream} response as a single UTF-8 string. The stream may be empty if
     * upstream replied with no chunks before {@code data: [DONE]} — the service decides how to
     * interpret that.
     *
     * @throws com.jilali.core.JilaliException on HTTP errors or empty responses, mapped to
     *                                          {@code 502 Bad Gateway} via
     *                                          {@link com.jilali.core.JilaliException#upstreamFailure}
     */
    String postAiTranslate(AiTranslateUpstreamRequest body, TranslateUpstreamHeaders headers);
}