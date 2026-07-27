package com.jilali.auth;

import com.jilali.auth.dto.upstream.LoginResponse;
import com.jilali.auth.dto.upstream.SignCheckResponse;

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

    /** Binds an anti-cheat token for a signup session, scoped to a per-attempt {@code bindId}
     *  that upstream issues in {@link com.jilali.auth.dto.upstream.SignCheckResponse.UserInfoBean#bindId}
     *  (smali j21/b navigates to SignProfileV2Activity which calls h21/s.a(user_info.bind_id, ...)).
     *  On the FIRST /v3/check attempt the client doesn't have a bind_id yet and this is not called.
     *  Returns the captured {@code irisk_token} (a {@code HTIRISK_<UUID>} string) on success, or
     *  empty Optional on any failure (best-effort). The token is then threaded into a follow-up
     *  /v3/check call if needed. */
    Optional<String> regPrepare(String bindId);

    /** Triggers HelloTalk to email a verification code to {@code email}. */
    void sendEmailCode(String email);

    /** Nickname availability/validity check — independent of the rest of the signup pipeline. */
    void checkNickname(String nickname);

    /**
     * Terminal signup step. The returned {@link SignupCheckOutcome} carries both the parsed
     * inner {@link SignCheckResponse} data (when verify_token is present, success) and the
     * upstream envelope {@code status} + {@code msg} — so callers can map specific upstream
     * status codes (208 verification_code_error, 105 too many attempts, 109 password format,
     * 125 account hidden, 212 verification failed — same table the Android client's
     * {@code h21/e0} smali maps) to distinct UX without re-parsing the wire.
     *
     * <p>{@link SignupCheckOutcome#rejected(int, String)} with {@code upstreamStatus == 0}
     * means an envelope-level failure (transport / parse) with no upstream status to report.
     */
    SignupCheckOutcome signupCheck(String email, String password, String emailVerifyCode, String iriskToken);
}