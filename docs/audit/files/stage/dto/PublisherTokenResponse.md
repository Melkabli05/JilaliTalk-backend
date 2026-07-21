# PublisherTokenResponse

`src/main/java/com/jilali/stage/dto/PublisherTokenResponse.java`

## Purpose
Response for `GET /publisher-token` — the decrypted Agora RTC token carrying publisher privilege, used by clients before publishing audio (mirrors HelloTalk web's `switchToPublisher`).

## Responsibilities
Carry a single decrypted `token` string.

## Public API
Record `PublisherTokenResponse`:
- `String token` — the plain (AES-decrypted) Agora token. No validation (response DTO); may be produced from upstream where `JilaliGateway.publisherToken` guards against null/blank and throws `BAD_GATEWAY`.

## Dependencies
- `@Serdeable` only.
- Depended on by: `StageController.publisherToken`, `JilaliGateway.publisherToken`, `JilaliClient` (also the upstream/encrypted read type before decryption).

## Coupling and cohesion analysis
Trivially cohesive single-field wrapper. Reused as both the upstream (encrypted) and downstream (decrypted) shape — a slight double duty but harmless.

## Code smells
- None material. Arguably a **single-field wrapper** that could be a bare `String`, but the record documents intent and keeps the JSON shape `{ "token": "..." }`, which is preferable for an API response.

## Technical debt
None.

## Duplicate logic
None.

## Dead or unused code
None; serialized response.

## Java 25 modernization opportunities
Already a minimal record; nothing to modernize.

## Micronaut built-in opportunities
None needed (response DTO, Serde-handled).

## Refactoring recommendations
Keep as-is. Optionally add an `expiresAt` field if the client needs to schedule token renewal.
