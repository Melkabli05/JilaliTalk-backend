package com.jilali.core;

import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.ClientFilter;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.context.ServerRequestContext;

/**
 * Copies the caller's auth and HelloTalk/B3-tracing headers onto every outbound Jilali request.
 * <p>
 * A BFF that proxies an authenticated upstream lives or dies by this: the frontend authenticates
 * to <em>us</em>, and we must carry that identity (plus the {@code x-ht-*} device context and
 * {@code x-b3-*} trace IDs) downstream. We use {@link ServerRequestContext} to reach the inbound
 * request rather than a manually-managed ThreadLocal — Micronaut already propagates this across
 * its reactive/virtual-thread boundaries.
 * <p>
 * Scoped to the {@code jlhub} service id so it never touches other clients.
 */
@ClientFilter(serviceId = "jlhub")
public final class HeaderPropagationFilter {

    private final JilaliProperties properties;

    public HeaderPropagationFilter(JilaliProperties properties) {
        this.properties = properties;
    }

    @RequestFilter
    public void propagate(MutableHttpRequest<?> downstream) {
        ServerRequestContext.currentRequest().ifPresent(inbound -> {
            for (var header : properties.forwardedHeaders()) {
                var value = inbound.getHeaders().get(header);
                if (value != null) {
                    downstream.header(header, value);
                }
            }
        });
    }
}
