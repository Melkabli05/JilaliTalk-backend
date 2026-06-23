package com.jilali.auth;

import com.jilali.auth.dto.AddHelloTalkTokenRequest;
import com.jilali.auth.dto.AuthResponse;
import com.jilali.auth.dto.AuthUserResponse;
import com.jilali.auth.dto.ErrorResponse;
import com.jilali.auth.dto.LoginRequest;
import com.jilali.auth.dto.RegisterRequest;
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
 * Platform auth — JilaliTalk's own accounts, independent of HelloTalk identity (see
 * schema.sql and HelloTalkTokenPoolRepository for why those are decoupled for now).
 *
 * <p>The browser only ever receives an opaque session id, set as an HttpOnly cookie — never a
 * JWT of any kind, HelloTalk's or our own. Every other piece of identity (which HelloTalk
 * account this session's calls run as, if any) is resolved server-side per request by
 * {@link com.jilali.core.SessionAuthClientFilter}.
 */
@ExecuteOn(TaskExecutors.BLOCKING)
@Controller("/api/auth")
public class AuthController {

    public static final String SESSION_COOKIE = "jilali_session";
    private static final Duration SESSION_MAX_AGE = Duration.ofDays(30);

    private final UserRepository users;
    private final SessionRepository sessions;
    private final HelloTalkTokenPoolRepository tokenPool;
    private final PasswordHasher passwordHasher;

    public AuthController(UserRepository users, SessionRepository sessions,
                           HelloTalkTokenPoolRepository tokenPool, PasswordHasher passwordHasher) {
        this.users = users;
        this.sessions = sessions;
        this.tokenPool = tokenPool;
        this.passwordHasher = passwordHasher;
    }

    @Post("/register")
    public HttpResponse<?> register(@Valid @Body RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (users.findByEmail(email).isPresent()) {
            return HttpResponse.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("Email already registered"));
        }
        AppUser user = users.create(email, passwordHasher.hash(request.password()), request.nickname().trim());
        // Best-effort — a no-op when the pool is empty; the account still works via the shared
        // default-auth-token fallback, same as every account did before this table existed.
        tokenPool.assignNextAvailable(user.id());
        return withSessionCookie(
            HttpResponse.created(new AuthResponse(toResponse(user))),
            sessions.create(user.id()));
    }

    @Post("/login")
    public HttpResponse<?> login(@Valid @Body LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        Optional<AppUser> user = users.findByEmail(email);
        if (user.isEmpty() || !passwordHasher.matches(request.password(), user.get().passwordHash())) {
            return HttpResponse.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("Invalid email or password"));
        }
        return withSessionCookie(
            HttpResponse.ok(new AuthResponse(toResponse(user.get()))),
            sessions.create(user.get().id()));
    }

    @Post("/logout")
    public HttpResponse<Void> logout(HttpRequest<?> request) {
        sessionCookie(request).ifPresent(cookie -> sessions.delete(cookie.getValue()));
        return HttpResponse.<Void>noContent()
            .cookie(Cookie.of(SESSION_COOKIE, "").path("/").maxAge(0));
    }

    @Get("/me")
    public HttpResponse<?> me(HttpRequest<?> request) {
        return sessionCookie(request)
            .flatMap(cookie -> sessions.resolveUserId(cookie.getValue()))
            .flatMap(users::findById)
            .<HttpResponse<?>>map(u -> HttpResponse.ok(new AuthResponse(toResponse(u))))
            .orElseGet(() -> HttpResponse.status(HttpStatus.UNAUTHORIZED));
    }

    /**
     * Manually registers a real, out-of-band-obtained HelloTalk JWT into the pool. Wide open
     * for now — there is no platform-auth layer yet to gate this behind (see the auth design's
     * "two auths" split); lock this down before this ever runs anywhere but localhost.
     */
    @Post("/admin/hellotalk-tokens")
    public HttpResponse<Void> addHelloTalkToken(@Valid @Body AddHelloTalkTokenRequest request) {
        tokenPool.addToken(request.helloTalkUid(), request.jwt(), request.label());
        return HttpResponse.noContent();
    }

    private Optional<Cookie> sessionCookie(HttpRequest<?> request) {
        return request.getCookies().findCookie(SESSION_COOKIE);
    }

    private MutableHttpResponse<?> withSessionCookie(MutableHttpResponse<?> response, String sessionId) {
        // secure(true) is deliberately omitted — local dev runs over plain HTTP. Turn it on
        // (and set SameSite=None if frontend/backend ever live on different real domains)
        // when this gets the "platform auth" hardening pass mentioned in the auth design.
        return response.cookie(Cookie.of(SESSION_COOKIE, sessionId)
            .httpOnly(true)
            .path("/")
            .maxAge(SESSION_MAX_AGE));
    }

    private AuthUserResponse toResponse(AppUser user) {
        return new AuthUserResponse(user.id(), user.nickname(), user.email(), null);
    }
}
