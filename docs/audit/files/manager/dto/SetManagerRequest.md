# `com.jilali.manager.dto.SetManagerRequest`

## Purpose
Request body for `POST /api/managers`, used to add or remove a user as a room manager via an `action` discriminator.

## Public API

Record fields:
- `String cname` — `@NotBlank`; target room/community name.
- `int action` — unconstrained; operation code (e.g. add/remove) owned by upstream.
- `long userId` — `@JsonProperty("user_id")`, `@Positive`; the affected user.
- `int busiType` — `@JsonProperty("busi_type")`; business-type discriminator passed through to upstream.

## Coupling
Imported by `ManagerController.set`. Underlying call: `client.setManagers(request)`.

## Notes
Sits alongside `ApproveManagerRequest` in the room-scope (`cname` + identifier) family, differentiated by carrying a `userId`/`action`/`busiType` triple rather than an `operationType`. Only `userId` has a `@Positive` guard — `action` and `busiType` are raw `int`s with no validation.
