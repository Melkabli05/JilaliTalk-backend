package com.jilali.auth.dto;

import com.jilali.auth.AuthSession;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Matches the Angular frontend's {@code AuthUser} interface
 * (`JilaliTalk-angular-frontend/src/app/core/auth/auth.store.ts`) field-for-field — that
 * frontend was built ahead of this backend, against this exact shape, so the contract is
 * fixed by the frontend, not renegotiable here.
 * <p>
 * {@code imJwt}/{@code imDeviceId}/{@code imDeviceModel} are a deliberate exception to "never
 * expose the JWT": the frontend persists them client-side to open its own {@code ht_im/sock}
 * connection later (see {@code AuthStore.persistImCredentials}) — a different concern from the
 * upstream-call credential {@link com.jilali.auth.SessionAuthClientFilter} resolves server-side
 * per request, which the browser still never sees.
 */
@Serdeable
public record AuthUserResponse(
    long userId,
    @Nullable String nickname,
    String email,
    @Nullable String headUrl,
    @Nullable String imJwt,
    @Nullable String imDeviceId,
    @Nullable String imDeviceModel
) {
    /** Minimal response when the profile lookup ({@code nickname}/{@code headUrl}) fails or is
     *  skipped — login/signup already succeeded by this point, so a profile-enrichment failure
     *  degrades gracefully rather than failing the whole request. */
    public static AuthUserResponse withoutProfile(AuthSession session) {
        return new AuthUserResponse(
            session.helloTalkUid(), null, session.email(), null,
            session.jwt(), session.deviceId(), null);
    }

    public static AuthUserResponse of(AuthSession session, @Nullable String nickname,
                                       @Nullable String headUrl, String deviceModel) {
        return new AuthUserResponse(
            session.helloTalkUid(), nickname, session.email(), headUrl,
            session.jwt(), session.deviceId(), deviceModel);
    }
}
