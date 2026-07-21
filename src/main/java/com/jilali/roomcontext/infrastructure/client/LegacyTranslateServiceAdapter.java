package com.jilali.roomcontext.infrastructure.client;

import com.jilali.roomcontext.domain.service.TranslationService;
import com.jilali.translate.TranslateService;
import jakarta.inject.Singleton;

/**
 * Wraps the legacy {@code com.jilali.translate.TranslateService} (Curve25519 handshake + AES
 * encrypted-field codec + SSE parsing) as this bounded context's {@link TranslationService}
 * port implementation. The legacy service is validated-correct and stays untouched per the
 * migration strategy ("existing implementation remains untouched during migration") - this is
 * a strangler-fig anti-corruption layer, not a reimplementation.
 */
@Singleton
public class LegacyTranslateServiceAdapter implements TranslationService {

    private final TranslateService legacy;

    public LegacyTranslateServiceAdapter(TranslateService legacy) {
        this.legacy = legacy;
    }

    @Override
    public String translate(String text, String targetLang) {
        return legacy.translate(text, targetLang);
    }
}
