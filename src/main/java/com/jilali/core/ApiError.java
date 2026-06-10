package com.jilali.core;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Our own RFC 9457-shaped error body, served as {@code application/problem+json}.
 * <p>
 * Deliberately hand-rolled rather than pulling in {@code micronaut-problem-json}: that module
 * wraps a third-party library and, by default, strips the {@code detail} field unless the
 * exception is one of a couple of specific types. For a gateway we want full, predictable control
 * over the error shape with no extra dependency — this record gives exactly that.
 *
 * @param type    URI reference identifying the problem type (defaults to "about:blank")
 * @param title   short, human-readable summary
 * @param status  HTTP status code
 * @param detail  human-readable explanation specific to this occurrence
 * @param upstreamCode original Jilali code, when the failure originated upstream
 */
@Serdeable
public record ApiError(
        String type,
        String title,
        int status,
        String detail,
        @Nullable Integer upstreamCode) {

    public static ApiError of(int status, String title, String detail, @Nullable Integer upstreamCode) {
        return new ApiError("about:blank", title, status, detail, upstreamCode);
    }
}
