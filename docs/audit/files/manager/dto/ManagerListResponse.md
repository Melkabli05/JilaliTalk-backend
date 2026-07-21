# `com.jilali.manager.dto.ManagerListResponse`

## Purpose
Response envelope for `GET /api/managers`, wrapping the list of manager entries returned for a room.

## Public API

Record fields:
- `@Nullable List<Manager> managerList` — `@JsonProperty("manager_list")`; nullable collection.

Convenience accessor:
- `List<Manager> managerList()` — returns the list or `List.of()` when the field is null, avoiding NPEs in consumers.

## Coupling
Returned by `ManagerController.list`. Wraps `Manager` rows (see `docs/audit/files/manager/dto/Manager.md`).

## Notes
The accessor override on a record is intentional null-safety for downstream rendering code; Jackson still binds the underlying field through its canonical accessor.
