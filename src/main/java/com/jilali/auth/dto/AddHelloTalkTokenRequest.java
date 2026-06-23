package com.jilali.auth.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/** Body for manually registering a real, out-of-band-obtained HelloTalk JWT into the pool
 *  (see HelloTalkTokenPoolRepository — there is no self-service way to mint these). */
@Serdeable
public record AddHelloTalkTokenRequest(
    @Positive long helloTalkUid,
    @NotBlank String jwt,
    String label
) {}
