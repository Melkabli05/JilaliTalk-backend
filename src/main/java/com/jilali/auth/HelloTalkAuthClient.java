package com.jilali.auth;

import com.jilali.auth.dto.upstream.LoginResponse;

import java.util.Optional;

/**
 * Port to HelloTalk's own auth microservice ({@code /user_register_center/**}). Deliberately
 * narrow (Interface Segregation) — only the operations this feature needs, not folded into the
 * shared {@code JilaliClient}. Each method is a complete business operation, not a raw wire
 * call: {@link #login} internally runs the two-step pre_login+login exchange
 * (see {@code re_output/FINDINGS.md} §7.1) so callers never need to know that protocol detail.
 */
public interface HelloTalkAuthClient {

    /**
     * Runs the full pre_login + login exchange. Returns {@link Optional#empty()} for any
     * failure short of a hard transport/decode error (which still throws
     * {@link com.jilali.core.JilaliException}) — the exact upstream error-code shape for wrong
     * credentials isn't confirmed from static analysis, so any rejection at either step is
     * conservatively treated as invalid credentials.
     */
    Optional<LoginResponse> login(String email, String password);
}