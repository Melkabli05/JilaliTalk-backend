# AuthResponse

`src/main/java/com/jilali/auth/dto/AuthResponse.java`

## Purpose
A one-field wrapper record `{ user: AuthUserResponse }` that matches the Angular frontend's `AuthResponse` type (`core/auth/auth.service.ts`). It exists purely so the JSON envelope shape (`{user: ...}`) is `{user:...}` rather than a bare `AuthUserResponse` object.

## Responsibilities
- Provide the `{user: ...}` JSON envelope for the `GET /api/auth/me` response.

## Public API
- `record AuthResponse(AuthUserResponse user)` — single component `user` (non-null in practice; Jackson-serialized accessor).

## Dependencies
- `@Serdeable`; `AuthUserResponse` (same package).
- Depended on BY: `AuthController.me(...)` returns `HttpResponse<AuthResponse>`. Grep-confirmed: `AuthController` only. Note login/signup do **not** use this wrapper — the controller builds their `{user: ...}` bodies inline (see Duplicate logic / smells).

## Coupling and cohesion analysis
Maximally cohesive (one field) and minimally coupled. It is a serialization-shape adapter dictated by a frontend contract that predates the backend, so its existence is justified even though it holds no logic.

## Code smells
- **Data Class:** a pure structural record with no behavior. Acceptable — this is exactly what a DTO should be, and it exists to satisfy an external wire contract.
- **Inconsistent usage (contract smell, not local):** only `me` uses this wrapper type; `login`/`completeSignup` in `AuthController` produce the same `{user: ...}` shape ad hoc. The envelope is thus expressed two different ways for one contract.

## Technical debt
- Minor: the `{user: ...}` convention is enforced by a typed record in one place and by inline map/object construction in others. Nothing keeps them consistent except discipline.

## Duplicate logic
- The `{user: AuthUserResponse}` envelope is reconstructed inline by `AuthController.login`/`completeSignup` instead of reusing this record — a small, avoidable divergence.

## Dead or unused code
- None. Framework-serialized; used by `AuthController.me`. The generated `user()` accessor is invoked by Jackson/serde, not dead.

## Refactoring recommendations
- Use this record (or a shared factory) for the login and signup success bodies too, so the `{user: ...}` envelope has a single source of truth.
