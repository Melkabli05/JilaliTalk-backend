# ReceiveFriendSentCardRequest

## Purpose
Request body for `POST /api/vip-experience-card/receive-friend-card` — accepts a friend-sent VIP experience card by its record id.

## Public API
Record `ReceiveFriendSentCardRequest`:
- `@JsonProperty("record_id") @Positive long recordId` — id of the friend's sent card record the caller is collecting. Must be > 0.

## Coupling
Validated via `jakarta.validation`; consumed by `VipExperienceCardController.receiveFriendCard` and forwarded to `VipExperienceCardClient.receiveFriendSentCard`.

## Notes
Single field, no user/caller identity — relies on the controller to authorize the recipient (which the controller itself does not do; see `VipExperienceCardController` note). `@Positive` permits 0-or-greater; `jakarta` semantics with `long` only reject `<= 0` here, not negatives, since `@Positive` excludes zero too — confirm against upstream whether `recordId=0` is a valid sentinel.
