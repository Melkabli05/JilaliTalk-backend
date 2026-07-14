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

    /** Convenience constructor for errors that don't originate from an upstream code. */
    public JilaliException(String message, HttpStatus status) {
        this(0, message, status);
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
            case 100003, 100004 -> HttpStatus.UNAUTHORIZED; // session expired / not authenticated
            case 100005 -> HttpStatus.FORBIDDEN;            // insufficient permissions
            case 190032 -> HttpStatus.UNPROCESSABLE_ENTITY; // VoiceManagerUpdateFailed
            // Non-VIP watch limit exceeded and no VIP-trial card left to auto-claim. Deliberately
            // NOT 403 — the frontend's error interceptor force-navigates to a full-page /error/403
            // on any 403, which would eject the user from the room instead of showing an inline
            // "upgrade to VIP" message.
            case 190041 -> HttpStatus.PAYMENT_REQUIRED;
            case 200001, 200002 -> HttpStatus.NOT_FOUND;    // room / user not found
            case 300001 -> HttpStatus.CONFLICT;             // already in room / already joined
            case 300002 -> HttpStatus.GONE;                 // room closed / expired
            case 400001 -> HttpStatus.TOO_MANY_REQUESTS;   // rate limited
            default -> HttpStatus.BAD_GATEWAY;
        };
        return new JilaliException(code, msg == null ? "Upstream error" : msg, status);
    }

    /**
     * Builds a {@link JilaliException} for failures that didn't come from a structured upstream
     * error code — I/O errors, malformed bodies, empty responses, etc. Always surfaces as
     * {@code 502 Bad Gateway} because we reached upstream but couldn't turn its reply into a
     * useful response.
     */
    public static JilaliException upstreamFailure(String stage, Throwable cause) {
        String message = stage + " failed: " + (cause != null ? cause.getMessage() : "unknown");
        return new JilaliException(0, message, HttpStatus.BAD_GATEWAY);
    }
}
