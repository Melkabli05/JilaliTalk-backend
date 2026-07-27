package com.jilali.auth;

import com.jilali.auth.dto.AuthUserResponse;

/**
 * Result of {@link HelloTalkAuthService#signup}. {@code Rejected} carries both a
 * short {@code upstreamStatus} (the raw {@code status} field from the
 * {@code /user_register_center} envelope — same code table the Android client's
 * {@code h21/e0} smali maps, e.g. {@code 208} = verification_code_error,
 * {@code 105} = too many attempts within 24h, {@code 109} = password_format_incorrect,
 * {@code 125} = your_account_has_been_hidden, {@code 212} = verification_failed)
 * and a human-readable {@code reason} mapped from that status — so the Angular
 * frontend can pick the right UX without having to guess. The {@code 0} status is
 * reserved for envelope-level failures (transport, parse) where there is no
 * upstream status to report.
 */
public sealed interface SignupOutcome {

    record Created(AuthSession session, AuthUserResponse user) implements SignupOutcome {}

    record Rejected(int upstreamStatus, String reason) implements SignupOutcome {}
}