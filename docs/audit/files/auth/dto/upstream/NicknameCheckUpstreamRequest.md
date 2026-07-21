# NicknameCheckUpstreamRequest

`src/main/java/com/jilali/auth/dto/upstream/NicknameCheckUpstreamRequest.java`

## Purpose
The wire body for `POST /user_register_center/v3/reg/profile_check` (ht/encbin codec), matching smali `Ls21/d;`: a single `{nickname}` field. A nickname availability/validity check that, despite the endpoint name, does **not** gate `/v3/check`.

## Responsibilities
- Represent the single-field nickname-check request on the wire.

## Public API
- `record NicknameCheckUpstreamRequest(String nickname)` — single component; Jackson-serialized accessor.

## Dependencies
- `@Serdeable`.
- Depended on BY: `HelloTalkAuthClientImpl.checkNickname` (constructs it). Grep-confirmed: `HelloTalkAuthClientImpl` only.

## Coupling and cohesion analysis
Maximally cohesive (one field), zero coupling beyond serialization. A minimal wire DTO.

## Code smells
- **Data Class:** appropriate.
- **Near-identity twin of the BFF-facing `dto/NicknameCheckRequest`:** both are single-field `{nickname}` records. See Duplicate logic.

## Technical debt
- None of note.

## Duplicate logic
- `dto/NicknameCheckRequest` (BFF-facing) and this upstream DTO are structurally identical single-`nickname` records, and the client maps one to the other with no transformation. This is the clearest instance of a **needless mapping layer** in the package: two types, two files, one field, no behavioral difference. The only justification is the general policy of isolating upstream wire types from BFF request types (validation annotations differ: the BFF one has `@NotBlank`). Worth naming, low severity.

## Dead or unused code
- None. Constructed in `checkNickname`; accessor serialized onto the wire.

## Refactoring recommendations
- Acceptable as-is under the "isolate upstream types" policy, but if that policy is relaxed for trivial single-field checks, this and `dto/NicknameCheckRequest` could collapse into one type, removing the redundant mapping.
