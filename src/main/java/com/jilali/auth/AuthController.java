package com.jilali.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jilali.auth.dto.AuthResponse;
import com.jilali.core.JilaliProperties;
import com.jilali.core.UidExtractor;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import jakarta.inject.Singleton;

/**
 * Stub auth controller — login is not implemented yet.
 * {@code GET /api/auth/me} returns the hardcoded BFF identity derived from
 * {@code jilali.default-auth-token} so that the Angular frontend becomes "authenticated"
 * and can connect directly to the IM messaging server using the same credentials.
 *
 * Replace this with real session/cookie auth when the login feature is built.
 */
@Singleton
@Controller("/api/auth")
public class AuthController {

    private final AuthResponse.AuthUser hardcodedUser;

    public AuthController(JilaliProperties properties, ObjectMapper om) {
        long uid = UidExtractor.uidAsLong(properties.defaultAuthToken(), om);
        this.hardcodedUser = new AuthResponse.AuthUser(
            uid, "Jilali Light", "", null,
            properties.defaultAuthToken(), properties.deviceId(), properties.deviceModel()
        );
    }

    /** Returns the currently "logged-in" user from the hardcoded JWT. */
    @Get("/me")
    public AuthResponse me() {
        return new AuthResponse(hardcodedUser);
    }

    /** Stub — real login not implemented yet. */
    @Post("/login")
    public AuthResponse login() {
        return new AuthResponse(hardcodedUser);
    }

    /** Stub — real logout not implemented yet. */
    @Post("/logout")
    public void logout() {}
}
