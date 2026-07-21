# NicknameCheckRequest

`src/main/java/com/jilali/auth/dto/NicknameCheckRequest.java`

## Purpose
The request body for `POST /api/auth/signup/check-nickname` — a nickname availability/validity check during the signup flow.

## Responsibilities
- Carry the `nickname` string from the frontend to `AuthController.checkNickname`.
- Enforce `@NotBlank`.

## Public API
- `record NicknameCheckRequest(@NotBlank String nickname)` — single non-blank component; serde-deserialized accessor.

## Dependencies
- `@Serdeable`; `jakarta.validation` `@NotBlank`.
- Depended on BY: `AuthController.checkNickname(@Valid @Body NicknameCheckRequest)`. Grep-confirmed: `AuthController` only.

## Coupling and cohesion analysis
Maximally cohesive (one field), zero domain coupling. A minimal inbound request DTO. Good.

## Code smells
- **Data Class:** appropriate for a request DTO.
- **Near-duplicate of the upstream `NicknameCheckUpstreamRequest`** (also a single `nickname` field). See Duplicate logic.

## Technical debt
- None of note.

## Duplicate logic
- This BFF-facing DTO and `dto/upstream/NicknameCheckUpstreamRequest` are both a single-field `{nickname}` record. The service/client maps one to the other with no transformation beyond re-wrapping — a thin, near-identity mapping layer. This is a small instance of the "needless mapping layer" smell (see the package docs), though the two-layer separation is arguably consistent with keeping upstream wire types isolated from the BFF contract.

## Dead or unused code
- None. Deserialized/validated on the check-nickname route.

## Refactoring recommendations
- None strictly required. If the BFF/upstream split is ever relaxed for trivial single-field checks, these two could collapse into one type.
