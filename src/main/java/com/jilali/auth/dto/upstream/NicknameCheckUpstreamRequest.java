package com.jilali.auth.dto.upstream;

import io.micronaut.serde.annotation.Serdeable;

/**
 * Body of {@code POST /user_register_center/v3/reg/profile_check} (ht/encbin), matching smali
 * {@code Ls21/d;}: {@code {nickname}}. A nickname availability/validity check, independent of
 * the rest of the signup pipeline — despite the endpoint name, it does not gate {@code /v3/check}.
 */
@Serdeable
public record NicknameCheckUpstreamRequest(String nickname) {}
