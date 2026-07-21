# RegPrepareRequest

`src/main/java/com/jilali/auth/dto/upstream/RegPrepareRequest.java`

## Purpose
The wire body for `POST /user_register_center/v3/reg/prepare` (ht/encbin) — binds an anti-cheat token for a signup session. This BFF sends `iriskToken` empty because it cannot produce a real NetEase device-attestation token.

## Responsibilities
- Represent the reg/prepare request: `bindId` (assumed to be the shared device id) and `iriskToken` (sent empty).

## Public API
Record components (wire names in parentheses):
- `String bindId (bind_id)`, `String iriskToken (irisk_token)`. Jackson-serialized accessors.

## Dependencies
- `@JsonProperty`, `@Serdeable`.
- Depended on BY: `HelloTalkAuthClientImpl.regPrepare` (constructs it). Grep-confirmed: `HelloTalkAuthClientImpl` only.

## Coupling and cohesion analysis
High cohesion (one wire contract). Low coupling. A minimal upstream DTO.

## Code smells
- **Data Class:** appropriate.
- No static factory, but with only two fields the client can construct it directly without the risk `EmailLoginRequest` has.

## Technical debt
- Encodes two documented verification gaps: `bindId` is assumed (not smali-confirmed) to be the shared device id, and `iriskToken` is intentionally empty because real device attestation is impossible for this BFF. This is the "anti-cheat ceiling" that `SignupOutcome.Rejected` warns about — upstream may reject signup on this basis alone. Brittle by nature, but documented.

## Duplicate logic
- Shares the `device_id`/`irisk_token` field family with `EmailLoginRequest`/`SignCheckRequest` — inherent to matching distinct endpoints, not harmful.

## Dead or unused code
- None. Constructed in `regPrepare`; accessors serialized.

## Refactoring recommendations
- None required. If `bind_id` provenance is ever confirmed (or found to differ from the device id), update accordingly.
