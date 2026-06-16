package com.jilali.core;

import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.ClientFilter;
import io.micronaut.http.annotation.ResponseFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs the raw response body for any failed Jilali upstream call.
 * Runs after all other filters so it sees the final response as received.
 */
@ClientFilter(serviceId = "jlhub")
public class JilaliErrorResponseFilter implements Ordered {

    private static final Logger log = LoggerFactory.getLogger(JilaliErrorResponseFilter.class);

    @ResponseFilter
    public void onError(HttpRequest<?> request, HttpResponse<?> response) {
        if (response.getStatus().getCode() >= 400) {
            String body = response.getBody(String.class).orElse("<empty>");
            log.warn("[jlhub] {} {} {} → body: {}",
                request.getMethod(), request.getPath(),
                response.getStatus(), body);
        }
    }

    @Override
    public int getOrder() {
        return Integer.MAX_VALUE; // run last
    }
}
