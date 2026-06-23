package com.jilali.auth.dto;

import io.micronaut.serde.annotation.Serdeable;

/** Wire shape for the logged-in JilaliTalk account — never carries a HelloTalk JWT or this
 *  account's session id; the frontend gets only what it needs to render "logged in as X". */
@Serdeable
public record AuthUserResponse(long userId, String nickname, String email, String headUrl) {}
