# ClaimTaskRewardRequest

## Purpose
Request body for `POST /api/signin/task-reward` — claims the reward for a completed voice task.

## Public API
Record `ClaimTaskRewardRequest`:
- `@JsonProperty("host_id") @Positive long hostId` — host id, must be positive.
- `@NotBlank String cname` — cname, must be non-blank.
- `@JsonProperty("task_id") int taskId` — id of the voice task whose reward is being claimed.

## Coupling
Used only by `SigninController.claimTaskReward` and serialized via Micronaut Serde.

## Notes
Bean-validation is enforced (`@Valid` on the controller).