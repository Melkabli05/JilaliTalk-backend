package com.jilali.roomcontext.infrastructure.client;

import com.jilali.core.AuthTokenHolder;
import com.jilali.core.JwtUtil;
import io.micronaut.http.context.ServerRequestContext;

/** Resolves the calling user's uid, preferring the real inbound Authorization header (the
 *  actual frontend user's own JWT) over the shared service-account token fallback. Shared by
 *  every adapter/service that needs "who is making this call" - previously duplicated in
 *  VipUpstreamAdapter and ProfileBundleService. */
public final class CallerIdentity {

    private CallerIdentity() {}

    public static Long currentUserId(AuthTokenHolder authToken) {
        var inbound = ServerRequestContext.currentRequest().orElse(null);
        String header = inbound == null ? null : inbound.getHeaders().get("authorization");
        String token = header != null && !header.isBlank() ? header : "Bearer " + authToken.get();
        return JwtUtil.uidFromBearer(token);
    }
}
