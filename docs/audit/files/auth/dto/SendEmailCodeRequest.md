# SendEmailCodeRequest

`src/main/java/com/jilali/auth/dto/SendEmailCodeRequest.java`

## Purpose
The request body for `POST /api/auth/signup/send-email-code` — asks the BFF to trigger HelloTalk to email a verification code to the given address.

## Responsibilities
- Carry the target `email` from the frontend to `AuthController.sendEmailCode`.
- Enforce `@NotBlank @Email`.

## Public API
- `record SendEmailCodeRequest(@NotBlank @Email String email)` — single validated component; serde-deserialized accessor.

## Dependencies
- `@Serdeable`; `jakarta.validation` `@Email`/`@NotBlank`.
- Depended on BY: `AuthController.sendEmailCode(@Valid @Body SendEmailCodeRequest)`. Grep-confirmed: `AuthController` only.

## Coupling and cohesion analysis
Maximally cohesive, zero domain coupling. A minimal, correctly-validated request DTO.

## Code smells
- **Data Class:** appropriate.
- Field pattern (`@NotBlank @Email String email`) duplicates `LoginRequest`/`SignupCheckRequest` — trivial.

## Technical debt
- None of note. No rate-limiting concern is expressed here (that would live in the controller/service), but sending verification emails is a spammable operation worth rate-limiting somewhere in the stack.

## Duplicate logic
- Shares the `email` validation pattern with other request DTOs; not worth deduplicating. Unlike `NicknameCheckRequest`, there is no single-field upstream twin — the upstream `SendEmailCodeUpstreamRequest` carries `{behavior_validate, email, scene}`, so real transformation occurs.

## Dead or unused code
- None. Deserialized/validated on the send-email-code route.

## Refactoring recommendations
- None required at the DTO level. Consider rate-limiting the endpoint upstream of this DTO.
