package com.jilali.core;

import io.micronaut.http.HttpStatus;

/**
 * Raised when Jilali returns a non-zero envelope {@code code}, or an unexpected upstream status.
 * Carries the original upstream code so the error handler can map it to a sensible HTTP status
 * and an RFC 9457 problem detail for our frontend.
 */
public final class JilaliException extends RuntimeException {

    private final int upstreamCode;
    private final HttpStatus status;

    public JilaliException(int upstreamCode, String message, HttpStatus status) {
        super(message);
        this.upstreamCode = upstreamCode;
        this.status = status;
    }

    public int upstreamCode() {
        return upstreamCode;
    }

    public HttpStatus status() {
        return status;
    }

    /**
     * Maps known Jilali error codes to HTTP statuses. Pattern-matching switch keeps the mapping
     * declarative; unknown codes degrade to 502 (we reached upstream but it refused).
     */
    public static JilaliException fromCode(int code, String msg) {
        var status = switch (code) {
            case 100002 -> HttpStatus.BAD_REQUEST;          // "bad request" (e.g. caller not host)
            case 190032 -> HttpStatus.UNPROCESSABLE_ENTITY; // VoiceManagerUpdateFailed
            default -> HttpStatus.BAD_GATEWAY;
        };
        return new JilaliException(code, msg == null ? "Upstream error" : msg, status);
    }
}
