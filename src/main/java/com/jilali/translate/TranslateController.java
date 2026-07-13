package com.jilali.translate;

import com.jilali.client.JilaliGateway;
import com.jilali.translate.dto.TranslateRequest;
import com.jilali.translate.dto.TranslateResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.validation.Valid;

/**
 * AI-translates arbitrary text via HelloTalk's {@code ai_translator} service
 * (see {@link JilaliGateway#aiTranslate}).
 */
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/api/translate")
public class TranslateController {

    private final JilaliGateway gateway;

    public TranslateController(JilaliGateway gateway) {
        this.gateway = gateway;
    }

    @Post
    public TranslateResponse translate(@Valid @Body TranslateRequest request) {
        String translated = gateway.aiTranslate(request.text(), request.targetLang());
        return new TranslateResponse(translated);
    }
}
