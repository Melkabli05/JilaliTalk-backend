package com.jilali.translate;

import com.jilali.translate.dto.TranslateRequest;
import com.jilali.translate.dto.TranslateResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.validation.Valid;

/**
 * AI-translates arbitrary text via the upstream translator (see {@link TranslateService}).
 */
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/api/translate")
public class TranslateController {

    private final TranslateService service;

    public TranslateController(TranslateService service) {
        this.service = service;
    }

    @Post
    public TranslateResponse translate(@Valid @Body TranslateRequest request) {
        String translated = service.translate(request.text(), request.targetLang());
        return new TranslateResponse(translated);
    }
}