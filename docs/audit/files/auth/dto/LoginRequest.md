# LoginRequest

`src/main/java/com/jilali/auth/dto/LoginRequest.java`

## Purpose
The request body for `POST /api/auth/login` — the credentials a browser submits to log in to this BFF.

## Responsibilities
- Carry `email` + `password` from the frontend to `AuthController.login`.
- Enforce basic input validation (`@NotBlank`, `@Email`).

## Public API
- `record LoginRequest(@NotBlank @Email String email, @NotBlank String password)` — both components non-null/non-blank; `email` must be a valid address. Serde-deserialized accessors.

## Dependencies
- `@Serdeable`; `jakarta.validation` `@Email`/`@NotBlank`.
- Depended on BY: `AuthController.login(@Valid @Body LoginRequest)`. Grep-confirmed: `AuthController` only.

## Coupling and cohesion analysis
High cohesion, zero coupling to domain internals. A textbook inbound request DTO with declarative validation. Good.

## Code smells
- **Data Class:** intended and appropriate for a request DTO.
- Very mild Primitive Obsession (raw `String password`), but wrapping credentials in value types would be over-engineering here.

## Technical debt
- None of note. No max-length constraint on `password`/`email`, so oversized inputs are accepted up to framework limits — negligible.

## Duplicate logic
- The `@NotBlank @Email String email` field pattern repeats across `LoginRequest`, `SendEmailCodeRequest`, and `SignupCheckRequest`. Trivial and not worth deduplicating.

## Dead or unused code
- None. Deserialized and validated by Micronaut on the login route.

## Refactoring recommendations
- None required. Optionally add length bounds for defense-in-depth.
