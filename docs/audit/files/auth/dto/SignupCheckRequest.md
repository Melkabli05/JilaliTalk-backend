# SignupCheckRequest

`src/main/java/com/jilali/auth/dto/SignupCheckRequest.java`

## Purpose
The request body for `POST /api/auth/signup/check` — the terminal signup step where the browser submits email + password + the emailed verification code.

## Responsibilities
- Carry `email`, `password`, `emailVerifyCode` from the frontend to `AuthController.completeSignup`.
- Enforce `@NotBlank` on all three, `@Email` on `email`.

## Public API
- `record SignupCheckRequest(@NotBlank @Email String email, @NotBlank String password, @NotBlank String emailVerifyCode)` — all three non-blank; serde-deserialized accessors.

## Dependencies
- `@Serdeable`; `jakarta.validation` `@Email`/`@NotBlank`.
- Depended on BY: `AuthController.completeSignup(@Valid @Body SignupCheckRequest)`, which passes the three fields to `HelloTalkAuthService.signup`. Grep-confirmed: `AuthController` only.

## Coupling and cohesion analysis
High cohesion, zero domain coupling. A clean inbound request DTO with declarative validation.

## Code smells
- **Data Class:** appropriate for a request DTO.
- Mild Primitive Obsession (three raw Strings), acceptable.

## Technical debt
- None of note.

## Duplicate logic
- Its three fields (`email`, `password`, `emailVerifyCode`) are a subset of the upstream `SignCheckRequest.forEmailSignup(...)` parameters and of `EmailLoginRequest.AccountLogin` (`user_id`, `passwd`, `email_verify_code`). However, real transformation happens downstream (hashing, envelope assembly, device persona), so this is not a needless mapping layer — the BFF DTO is genuinely thinner than the upstream wire type.

## Dead or unused code
- None. Deserialized/validated on the signup-check route.

## Refactoring recommendations
- None required.
