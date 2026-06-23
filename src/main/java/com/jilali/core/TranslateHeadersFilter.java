package com.jilali.core;

import com.jilali.core.JilaliProperties;
import io.micronaut.core.order.Ordered;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.ClientFilter;
import io.micronaut.http.annotation.RequestFilter;

/**
 * Adds HelloTalk translate-service specific headers to every downstream request to the translate
 * service. Runs after {@link HeaderPropagationFilter} so forwarded inbound headers take precedence.
 *
 * <ul>
 *   <li>{@code x-translate-pub} — static public-key signature token (environment variable)
 *   <li>{@code x-translate-os} — always {@code ios}
 *   <li>{@code x-translate-build} — always {@code 70} (HelloTalk app build)
 *   <li>{@code x-translate-version} — always {@code 6.3.0}
 * </ul>
 *
 * <p>The {@code authorization} header is propagated from the inbound request by {@link
 * HeaderPropagationFilter} (the frontend sends the HelloTalk JWT).
 */
@ClientFilter(serviceId = "translate")
public final class TranslateHeadersFilter implements Ordered {

    private final JilaliProperties config;

    public TranslateHeadersFilter(JilaliProperties config) {
        this.config = config;
    }

    @RequestFilter
    public void addTranslateHeaders(MutableHttpRequest<?> downstream) {
        downstream.header("x-translate-os", "ios");
        downstream.header("x-translate-build", "70");
        downstream.header("x-translate-version", "6.3.0");

        if (!config.translatePubKey().isBlank()) {
            downstream.header("x-translate-pub", config.translatePubKey());
        }
    }

    @Override
    public int getOrder() {
        return 200;
    }
}
