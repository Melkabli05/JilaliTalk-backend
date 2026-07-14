package com.jilali.auth;

import io.micronaut.core.order.Ordered;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.ClientFilter;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.http.cookie.Cookie;

/**
 * Resolves the inbound {@code jilali_session} cookie to a real, per-user HelloTalk JWT and
 * injects it as this call's upstream credential — the one place a stored credential is read for
 * a per-user request, never sent to or stored by the frontend.
 * <p>
 * This is what makes login meaningful beyond {@code /api/auth/*}: once this filter finds a
 * session, every other controller in the app (rooms, profile, comments, ...) starts acting as
 * the real logged-in user instead of the shared {@code jilali.default-auth-token} service
 * account, with no changes needed in those controllers.
 * <p>
 * Ordered to run after {@link com.jilali.core.HeaderPropagationFilter} (default order — a real
 * header the frontend already sent still wins) and before
 * {@link com.jilali.core.DefaultHeadersClientFilter} ({@code Integer.MAX_VALUE} — the shared
 * fallback token only applies when neither a real header nor a session supplied one).
 */
@ClientFilter(serviceId = "jlhub")
public final class SessionAuthClientFilter implements Ordered {

    private static final int ORDER = 100;

    private final AuthSessionRepository sessions;

    public SessionAuthClientFilter(AuthSessionRepository sessions) {
        this.sessions = sessions;
    }

    @RequestFilter
    public void resolve(MutableHttpRequest<?> downstream) {
        if (downstream.getHeaders().get("authorization") != null) {
            return;
        }
        ServerRequestContext.currentRequest().ifPresent(inbound ->
            inbound.getCookies().findCookie(AuthController.SESSION_COOKIE)
                .map(Cookie::getValue)
                .flatMap(sessions::find)
                .ifPresent(session -> {
                    downstream.header("authorization", "Bearer " + session.jwt());
                    downstream.header("x-ht-token", "Bearer " + session.jwt());
                    downstream.header("x-ht-uid", String.valueOf(session.helloTalkUid()));
                }));
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
