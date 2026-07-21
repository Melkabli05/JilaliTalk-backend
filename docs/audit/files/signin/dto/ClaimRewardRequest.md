# ClaimRewardRequest

## Purpose
Request body for `POST /api/signin/room-level-reward` — claims the room-level reward for the given host/cname.

## Public API
Record `ClaimRewardRequest`:
- `@JsonProperty("host_id") @Positive long hostId` — host id, must be positive.
- `@NotBlank String cname` — cname, must be non-blank.

## Coupling
Used only by `SigninController.claimRoomLevelReward` and serialized via Micronaut Serde.

## Notes
Bean-validation is enforced (`@Valid` on the controller); invalid payloads are rejected before reaching the client.