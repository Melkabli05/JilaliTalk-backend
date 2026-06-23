package com.jilali.core;

import com.jilali.auth.AuthController;
import com.jilali.auth.HelloTalkTokenPoolRepository;
import com.jilali.auth.SessionRepository;
import io.micronaut.core.order.Ordered;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.ClientFilter;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.context.ServerRequestContext;

/**
 * Resolves the inbound {@code jilali_session} cookie to the logged-in JilaliTalk user's
 * assigned HelloTalk JWT (see {@code com.jilali.auth}) and injects it as this call's upstream
 * Authorization — the one place a HelloTalk credential is read for a per-user request, never
 * sent to or stored by the frontend.
 *
 * <p>Ordered to run after {@link HeaderPropagationFilter} (a real per-call header the frontend
 * already sent still wins — it never does today, since the frontend is cookie-only, but the
 * precedence is correct either way) and before {@link DefaultHeadersClientFilter} (the shared
 * service-account fallback only applies when neither this filter nor the frontend supplied a
 * token — e.g. no session, or a session with no token assigned yet).
 */
@ClientFilter(serviceId = "jlhub")
public final class SessionAuthClientFilter implements Ordered {

    private final SessionRepository sessions;
    private final HelloTalkTokenPoolRepository tokenPool;

    public SessionAuthClientFilter(SessionRepository sessions, HelloTalkTokenPoolRepository tokenPool) {
        this.sessions = sessions;
        this.tokenPool = tokenPool;
    }

    @RequestFilter
    public void resolve(MutableHttpRequest<?> downstream) {
        if (downstream.getHeaders().get("authorization") != null) {
            return;
        }
        ServerRequestContext.currentRequest().ifPresent(inbound ->
            inbound.getCookies().findCookie(AuthController.SESSION_COOKIE)
                .flatMap(cookie -> sessions.resolveUserId(cookie.getValue()))
                .flatMap(tokenPool::findJwtForUser)
                .ifPresent(jwt -> {
                    downstream.header("authorization", "Bearer " + jwt);
                    downstream.header("x-ht-token", "Bearer " + jwt);
                }));
    }

    @Override
    public int getOrder() {
        return 100;
    }
}
