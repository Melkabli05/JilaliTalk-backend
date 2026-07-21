package com.jilali.roomcontext.api;

import com.jilali.roomcontext.api.dto.TranslateRequest;
import com.jilali.roomcontext.api.dto.TranslateResponse;
import com.jilali.roomcontext.domain.service.TranslationService;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.validation.Valid;

/** New-architecture controller, temporarily mounted under {@code /api/v2} so it can coexist
 *  with the legacy {@code /api/translate} controller during the Phase 3-4 verification window
 *  (see docs/room-redesign/07-migration-roadmap.md). Cut over to {@code /api/translate} at
 *  Phase 5, once verified. */
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/api/v2/translate")
public class TranslateController {

    private final TranslationService translationService;

    public TranslateController(TranslationService translationService) {
        this.translationService = translationService;
    }

    @Post
    public TranslateResponse translate(@Valid @Body TranslateRequest request) {
        String translated = translationService.translate(request.text(), request.targetLang());
        return new TranslateResponse(translated);
    }
}
