package com.jilali.auth.dto.upstream;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Body of {@code POST /user_register_center/v3/reg/prepare} (ht/encbin) — binds an anti-cheat
 * token for this signup session. {@code bindId} is assumed to be the same device id used
 * everywhere else in this BFF; not independently confirmed against smali (see the auth
 * implementation plan's "known verification gaps"). {@code iriskToken} is sent empty — this
 * BFF cannot produce a real NetEase device-attestation token.
 */
@Serdeable
public record RegPrepareRequest(
    @JsonProperty("bind_id") String bindId,
    @JsonProperty("irisk_token") String iriskToken
) {}
