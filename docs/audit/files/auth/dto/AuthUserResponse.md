# AuthUserResponse

`src/main/java/com/jilali/auth/dto/AuthUserResponse.java`

## Purpose
The BFF-facing user identity returned by login, signup, and `me`. It matches the Angular frontend's `AuthUser` interface (`core/auth/auth.store.ts`) field-for-field — that frontend was built ahead of this backend against this exact shape, so the contract is fixed externally. It carries the user's basic profile plus a deliberately-exposed set of IM credentials the browser uses to open its own `ht_im` socket.

## Responsibilities
- Represent the authenticated user for the frontend: `userId`, `nickname`, `email`, `headUrl`.
- Additionally expose `imJwt`/`imDeviceId`/`imDeviceModel` — a deliberate, documented exception to "never expose the JWT," because the frontend persists them client-side (`AuthStore.persistImCredentials`) to open its own IM connection. This is a different concern from the per-request upstream credential `SessionAuthClientFilter` resolves server-side, which the browser never sees.
- Provide two static factories for the two enrichment states.

## Public API
Record components (all Jackson/serde-serialized accessors):
- `long userId` — HelloTalk uid.
- `@Nullable String nickname` — null when profile lookup fails/was skipped.
- `String email` — account email (non-null).
- `@Nullable String headUrl` — avatar URL; null when profile lookup fails.
- `@Nullable String imJwt` — the HelloTalk JWT, exposed for the browser's own IM socket.
- `@Nullable String imDeviceId` — device id for the IM socket.
- `@Nullable String imDeviceModel` — device model for the IM socket (null in the `withoutProfile` path).

Static factories:
- `static AuthUserResponse withoutProfile(AuthSession session)` — minimal response (null nickname/headUrl/deviceModel) when profile enrichment fails or is skipped; login/signup already succeeded, so enrichment failure degrades gracefully.
- `static AuthUserResponse of(AuthSession session, @Nullable String nickname, @Nullable String headUrl, String deviceModel)` — full response.

## Dependencies
- `com.jilali.auth.AuthSession` (source of `helloTalkUid`/`email`/`jwt`/`deviceId`); `@Nullable`; `@Serdeable`.
- Depended on BY: `HelloTalkAuthService.buildAuthUser` (constructs via factories), `LoginOutcome.Authenticated`, `SignupOutcome.Created`, `AuthResponse` (wraps it), `AuthController` (renders it). Grep-confirmed across these five.

## Coupling and cohesion analysis
High cohesion — a single identity value object with two named constructors capturing its two valid shapes. Coupling to `AuthSession` is appropriate (the factories map domain -> DTO in one place). Good use of static factory methods over exposing the raw canonical constructor to callers.

## Code smells
- **Security-relevant field exposure (by design, but worth flagging):** `imJwt` puts the real HelloTalk JWT into the response body the browser receives. This is documented and intentional (the frontend needs it for its own socket), but it means the "never expose the JWT" invariant that `SessionAuthClientFilter`/`AuthSession` protect is deliberately broken here. The trade-off should be revisited if the IM socket could ever be proxied server-side instead.
- **Data Class:** pure data + factories, no behavior. Acceptable for a DTO.
- Mild **Long Parameter List** in `of(...)` (four params) — tolerable.

## Technical debt
- The `withoutProfile` path returns `imDeviceModel = null` while `of` supplies it — an asymmetry the frontend must tolerate. Minor.
- Because the shape is frontend-dictated, it cannot be simplified unilaterally even where fields (e.g. exposing `imJwt`) are questionable.

## Duplicate logic
- None. The factories centralize the `AuthSession` -> DTO mapping rather than duplicating it at call sites.

## Dead or unused code
- None. Both factories are called from `HelloTalkAuthService.buildAuthUser`; accessors are serde-invoked.

## Refactoring recommendations
- Revisit whether `imJwt` must be shipped to the browser at all — if the IM socket can be proxied through the BFF (like other upstream calls), this field and the client-side token persistence could be removed, restoring the "JWT never leaves the backend" invariant.
- If kept, consider documenting `imDeviceModel` nullability more explicitly or making both factories symmetric.
