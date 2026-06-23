package com.jilali.core;

import com.jilali.auth.AuthController;
import com.jilali.auth.SessionRepository;
import com.jilali.core.JilaliProperties;
import io.micronaut.core.order.Ordered;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.ClientFilter;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.context.ServerRequestContext;

/**
 * Adds HelloTalk translate-service specific headers to every downstream request to the translate
 * service. Runs after {@link HeaderPropagationFilter} so forwarded inbound headers take precedence.
 *
 * <ul>
 *   <li>{@code x-translate-pub} — static public-key signature token (environment variable)
 *   <li>{@code x-translate-os} — always {@code ios}
 *   <li>{@code x-translate-build} — always {@code 70} (HelloTalk app build)
 *   <li>{@code x-translate-version} — always {@code 6.3.0}
 *   <li>{@code x-translate-uid} — resolved from the Jilali session's assigned HelloTalk UID
 * </ul>
 *
 * <p>The {@code authorization} header is propagated from the inbound request by {@link
 * HeaderPropagationFilter} (the frontend sends the HelloTalk JWT) and takes precedence.
 */
@ClientFilter(serviceId = "translate")
public final class TranslateHeadersFilter implements Ordered {

    private final JilaliProperties config;
    private final SessionRepository sessions;

    public TranslateHeadersFilter(JilaliProperties config, SessionRepository sessions) {
        this.config = config;
        this.sessions = sessions;
    }

    @RequestFilter
    public void addTranslateHeaders(MutableHttpRequest<?> downstream) {
        // Propagate authorization from inbound request (HelloTalk JWT).
        ServerRequestContext.currentRequest()
                .ifPresent(inbound -> {
                    var auth = inbound.getHeaders().get("authorization");
                    if (auth != null) {
                        downstream.header("authorization", auth);
                    }
                });

        downstream.header("x-translate-os", "ios");
        downstream.header("x-translate-build", "70");
        downstream.header("x-translate-version", "6.3.0");

        if (!config.translatePubKey().isBlank()) {
            downstream.header("x-translate-pub", config.translatePubKey());
        }

        // Resolve HelloTalk UID from session and set x-translate-uid.
        ServerRequestContext.currentRequest()
                .flatMap(inbound -> inbound.getCookies().findCookie(AuthController.SESSION_COOKIE))
                .flatMap(cookie -> sessions.resolveHelloTalkUid(cookie.getValue()))
                .ifPresent(uid -> downstream.header("x-translate-uid", String.valueOf(uid)));
    }

    @Override
    public int getOrder() {
        return 200; // runs after SessionAuthClientFilter (100)
    }
}
