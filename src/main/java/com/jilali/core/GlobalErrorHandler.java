package com.jilali.core;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Translates failures into RFC 9457-shaped {@code application/problem+json} bodies, so our
 * frontend always sees consistent, machine-readable errors with the correct HTTP status —
 * never Jilali's {@code code} buried inside a 200 body.
 * <p>
 * One handler per exception type keeps each mapping focused (SRP) rather than a god-handler
 * switching over every throwable. Built on Micronaut's native {@link ExceptionHandler} SPI;
 * no third-party error library.
 */
public final class GlobalErrorHandler {

    private static final String PROBLEM_JSON = "application/problem+json";

    @Singleton
    @Produces(PROBLEM_JSON)
    @Requires(classes = {JilaliException.class, ExceptionHandler.class})
    public static final class JilaliExceptionHandler
            implements ExceptionHandler<JilaliException, HttpResponse<ApiError>> {

        private static final Logger log = LoggerFactory.getLogger(
                JilaliExceptionHandler.class);

        @Override
        public HttpResponse<ApiError> handle(HttpRequest request, JilaliException ex) {
            log.warn("[jlhub] {} {} → JilaliException code={} msg={}",
                    request.getMethod(), request.getPath(),
                    ex.upstreamCode(), ex.getMessage());
            var body = ApiError.of(
                    ex.status().getCode(),
                    "Upstream Jilali error",
                    ex.getMessage(),
                    ex.upstreamCode());
            return HttpResponse.<ApiError>status(ex.status())
                    .contentType(MediaType.of(PROBLEM_JSON))
                    .body(body);
        }
    }

    @Singleton
    @Produces(PROBLEM_JSON)
    @Requires(classes = {HttpClientResponseException.class, ExceptionHandler.class})
    public static final class UpstreamTransportExceptionHandler
            implements ExceptionHandler<HttpClientResponseException, HttpResponse<ApiError>> {

        private static final Logger log = LoggerFactory.getLogger(
                UpstreamTransportExceptionHandler.class);

        @Override
        public HttpResponse<ApiError> handle(HttpRequest request, HttpClientResponseException ex) {
            String upstreamBody = ex.getResponse() != null
                    ? ex.getResponse().getBody(String.class).orElse("<empty>")
                    : "<no response>";
            log.warn("[jlhub] {} {} {} → upstream body: {}",
                    request.getMethod(), request.getPath(),
                    ex.getStatus(), upstreamBody);

            var body = ApiError.of(
                    HttpStatus.BAD_GATEWAY.getCode(),
                    "Jilali unreachable",
                    ex.getMessage(),
                    null);
            return HttpResponse.<ApiError>status(HttpStatus.BAD_GATEWAY)
                    .contentType(MediaType.of(PROBLEM_JSON))
                    .body(body);
        }
    }

    @Singleton
    @Produces(PROBLEM_JSON)
    @Requires(classes = {Exception.class, ExceptionHandler.class})
    public static final class FallbackExceptionHandler
            implements ExceptionHandler<Exception, HttpResponse<ApiError>> {

        @Override
        public HttpResponse<ApiError> handle(HttpRequest request, Exception ex) {
            // Catch-all for any unhandled exception — produces a consistent problem+json 500.
            // This prevents raw 500 bodies from leaking to the frontend.
            var body = ApiError.of(
                    HttpStatus.INTERNAL_SERVER_ERROR.getCode(),
                    "Internal server error",
                    ex.getMessage() != null ? ex.getMessage() : "Unexpected error",
                    null);
            return HttpResponse.<ApiError>status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.of(PROBLEM_JSON))
                    .body(body);
        }
    }
}
