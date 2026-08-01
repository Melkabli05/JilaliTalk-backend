package com.jilali.auth;

import com.jilali.auth.dto.AuthResponse;
import com.jilali.auth.dto.LoginRequest;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.validation.Valid;

import java.time.Duration;
import java.util.Optional;

/**
 * HTTP boundary only — cookie plumbing and outcome-to-status mapping, nothing else. Every
 * business decision (is this login valid?) lives in {@link HelloTalkAuthService} and arrives
 * here as an already-decided {@link LoginOutcome} to render.
 * <p>
 * Response shape ({@code {user: AuthUser}}) and endpoint paths are fixed by the Angular
 * frontend's pre-existing {@code core/auth/auth.service.ts}/{@code auth.store.ts} — this
 * controller is the server side of an already-defined contract, not a free design.
 * <p>
 * The browser only ever sees {@link #SESSION_COOKIE}, an opaque HttpOnly id, for
 * authenticating *to this BFF* — never the real HelloTalk JWT used for upstream calls (see
 * {@link SessionAuthClientFilter}). It does receive an {@code imJwt} in the response body,
 * deliberately: the frontend uses that one to open its own {@code ht_im/sock} connection later,
 * a different concern (see {@code com.jilali.auth.dto.AuthUserResponse}'s docs).
 */
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/api/auth")
public class AuthController {

    public static final String SESSION_COOKIE = "jilali_session";
    private static final Duration SESSION_MAX_AGE = Duration.ofDays(30);

    private final HelloTalkAuthService authService;

    public AuthController(HelloTalkAuthService authService) {
        this.authService = authService;
    }

    @Post("/login")
    public HttpResponse<?> login(@Valid @Body LoginRequest request) {
        return switch (authService.login(request.email(), request.password())) {
            case LoginOutcome.Authenticated(var session, var user) ->
                withSessionCookie(HttpResponse.ok(new AuthResponse(user)), session.id());
            case LoginOutcome.InvalidCredentials ignored ->
                HttpResponse.status(HttpStatus.UNAUTHORIZED);
        };
    }

    @Post("/logout")
    public HttpResponse<Void> logout(HttpRequest<?> request) {
        sessionId(request).ifPresent(authService::logout);
        return HttpResponse.<Void>noContent().cookie(Cookie.of(SESSION_COOKIE, "")
            .httpOnly(true)
            .secure(true)
            .sameSite(io.micronaut.http.cookie.SameSite.None)
            .path("/")
            .maxAge(0));
    }

    @Get("/me")
    public HttpResponse<AuthResponse> me(HttpRequest<?> request) {
        return sessionId(request)
            .flatMap(authService::currentUser)
            .map(AuthResponse::new)
            .map(HttpResponse::ok)
            .orElseGet(() -> HttpResponse.status(HttpStatus.UNAUTHORIZED));
    }

    private Optional<String> sessionId(HttpRequest<?> request) {
        return request.getCookies().findCookie(SESSION_COOKIE).map(Cookie::getValue);
    }

    /**
     * {@code secure(true)} + {@code SameSite=None} are required now that the frontend
     * (vercel.app) and this backend (onrender.com) live on different real domains — the
     * browser silently drops a {@code SameSite=Lax} (the default) cookie on cross-site
     * fetch/XHR calls, which was causing every post-login request to fall back to the
     * shared {@code jilali.default-auth-token} service account instead of the real
     * per-user session (visible as {@code token-source=default} in
     * {@code DefaultHeadersClientFilter} logs). {@code Secure} cookies still work on
     * {@code http://localhost} because browsers treat localhost as a secure context, so
     * this doesn't break local dev.
     */
    private MutableHttpResponse<?> withSessionCookie(MutableHttpResponse<?> response, String sessionId) {
        return response.cookie(Cookie.of(SESSION_COOKIE, sessionId)
            .httpOnly(true)
            .secure(true)
            .sameSite(io.micronaut.http.cookie.SameSite.None)
            .path("/")
            .maxAge(SESSION_MAX_AGE));
    }
}