package com.jilali.auth;

import com.jilali.auth.dto.upstream.SignCheckResponse;

/**
 * Result of {@link HelloTalkAuthClient#signupCheck}. Three outcomes:
 *
 * <ul>
 *   <li>{@link #accepted(SignCheckResponse)} — upstream returned a verify_token;
 *       the caller must fall back to the standard email login pipeline (§7.1) to
 *       obtain a real JWT.</li>
 *   <li>{@link #rejected(int, String)} with a non-zero {@code upstreamStatus} —
 *       the upstream envelope explicitly refused (wrong code, too many attempts,
 *       account banned, …); the status is the same code table the Android client's
 *       {@code h21/e0} smali maps. The Angular frontend maps these to specific UX.</li>
 *   <li>{@link #rejected(int, String)} with {@code upstreamStatus == 0} — a
 *       transport-level or envelope-parse failure with no upstream status to report.</li>
 * </ul>
 */
public sealed interface SignupCheckOutcome {

    record Accepted(SignCheckResponse data) implements SignupCheckOutcome {}

    record Rejected(int upstreamStatus, String upstreamMsg) implements SignupCheckOutcome {}

    static SignupCheckOutcome accepted(SignCheckResponse data) {
        return new Accepted(data);
    }

    static SignupCheckOutcome rejected(int upstreamStatus, String upstreamMsg) {
        return new Rejected(upstreamStatus, upstreamMsg);
    }
}