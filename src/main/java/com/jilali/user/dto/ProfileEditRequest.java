package com.jilali.user.dto;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for POST /profile/v1/modify_baseinfo — a generic partial-update endpoint. The
 * real app sends only the field(s) actually being changed per call, alongside {@code os_type}/
 * {@code version} every time.
 * <p>
 * {@code learnLang2}/{@code learnLang2Level}/{@code teachLang2}/{@code teachLang2Level} are the
 * only field names confirmed against real captures. {@code nationality} is a reasonable-name
 * guess (never observed being written — the captured account had {@code modify_nationality:
 * false} the whole time, per {@code profile/v2/limitations}, so no write was ever possible to
 * capture) — verify against a live capture before relying on it. Nickname/gender/signature/tag
 * edits are NOT modeled here at all: no capture ever exercised them, and guessing their field
 * names for a write endpoint risks silently corrupting data if wrong.
 */
@Serdeable
public record ProfileEditRequest(
    @Nullable @JsonProperty("birthday") String birthday,
    @Nullable @JsonProperty("nationality") String nationality,
    @Nullable @JsonProperty("learn_lang2") Integer learnLang2,
    @Nullable @JsonProperty("learn_lang2_level") Integer learnLang2Level,
    @Nullable @JsonProperty("teach_lang2") Integer teachLang2,
    @Nullable @JsonProperty("teach_lang2_level") Integer teachLang2Level,
    @JsonProperty("os_type") int osType,
    @JsonProperty("version") String version
) {}
