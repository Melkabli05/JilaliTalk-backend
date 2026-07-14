package com.jilali.auth;

import com.jilali.auth.dto.AuthUserResponse;

/**
 * Result of {@link HelloTalkAuthService#login}. Models "wrong email/password" as an expected,
 * data-level outcome rather than an exception — a real upstream/network failure still throws
 * {@link com.jilali.core.JilaliException}, since that's genuinely exceptional, but invalid
 * credentials are a routine branch every caller must handle. The sealed hierarchy makes the
 * {@code switch} in {@link AuthController} exhaustive at compile time.
 * <p>
 * {@code Authenticated} carries both the raw {@link AuthSession} (the controller needs
 * {@code session.id()} for the cookie) and the already-built {@link AuthUserResponse} response
 * body (already profile-enriched) — the controller renders both without doing any business
 * logic of its own.
 */
public sealed interface LoginOutcome {

    record Authenticated(AuthSession session, AuthUserResponse user) implements LoginOutcome {}

    record InvalidCredentials() implements LoginOutcome {}
}
