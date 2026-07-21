# `com.jilali.manager.dto.ApproveManagerRequest`

## Purpose
Request body for `POST /api/managers/approve`, carrying the action type and the target room scope so the upstream service can record an approval of a pending manager operation.

## Public API

Record fields (all serialized via Jackson `@JsonProperty` where named):
- `String operationType` — `@NotBlank`; wire name `operation_type`; discriminator describing the operation being approved.
- `String cname` — `@NotBlank`; room/community name the approval applies to.
- `long hostId` — `@JsonProperty("host_id")`; host/owner the operation targets.

## Coupling
Imported by `ManagerController.approve`. Underlying call: `client.approveManager(request)`.

## Notes
Part of the `cname` + `host_id` (room-scope) family with an `operationType` discriminator instead of `user_id`. The `operationType` value is opaque to this layer — its vocabulary is owned by the upstream service.
