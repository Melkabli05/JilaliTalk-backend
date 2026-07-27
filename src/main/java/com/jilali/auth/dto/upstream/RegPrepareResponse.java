package com.jilali.auth.dto.upstream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Response of {@code POST /user_register_center/v3/reg/prepare} (ht/encbin) — binds a
 * NetEase Yidun {@code HTIRISK_<UUID>} anti-cheat token to the device for the duration
 * of this signup session. Per FINDINGS.md §7.3:
 *   "request DTO Ls21/e = RegPrepareReq(bind_id, irisk_token) → binds a fresh
 *    NetEase Yidun HTIRISK_<UUID> token to the device for the duration of this
 *    signup session. Refreshed when remote config has refresh_irisk_token=true."
 *
 * <p>The exact response shape isn't confirmed from static analysis (the original implementation
 * never captured it — the request was best-effort and the response was discarded). What we
 * need is whatever the server returns that we can send back on the next {@code /v3/check} call
 * as the {@code irisk_token} field. {@code @JsonIgnoreProperties(ignoreUnknown=true)} so
 * the decoder doesn't choke on extra fields the upstream adds.
 *
 * <p>Nullable: even after the bind, the upstream might return an error envelope with
 * no irisk_token. The caller (HelloTalkAuthService) handles null by sending an empty
 * string on the /v3/check call, which the upstream treats as 'no token' per the
 * Gson-doesn't-serialize-null-fields behavior described in FINDINGS.md §7.2.
 */
@Serdeable
@JsonIgnoreProperties(ignoreUnknown = true)
public record RegPrepareResponse(
    @JsonProperty("irisk_token") String iriskToken
) {}