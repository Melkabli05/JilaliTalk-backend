package com.jilali.auth;

import com.jilali.auth.dto.AuthUserResponse;

/**
 * Result of {@link HelloTalkAuthService#signup}. {@code Rejected} covers both a structured
 * upstream refusal (email taken, bad verification code) and the anti-cheat ceiling documented
 * in the auth implementation plan — this BFF cannot produce a real NetEase device-attestation
 * token, so upstream may reject signup on that basis alone.
 */
public sealed interface SignupOutcome {

    record Created(AuthSession session, AuthUserResponse user) implements SignupOutcome {}

    record Rejected(String reason) implements SignupOutcome {}
}
