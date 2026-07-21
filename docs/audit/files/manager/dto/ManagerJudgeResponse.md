# `com.jilali.manager.dto.ManagerJudgeResponse`

## Purpose
Response for `GET /api/managers/judge`, indicating whether the host (or current manager subject) is online in the room.

## Public API

Record fields:
- `boolean isOnline` — `@JsonProperty("is_online")`; online status flag.

## Coupling
Returned by `ManagerController.judge`. Underlying call: `client.managerJudge(cname, hostId)`.

## Notes
Minimal payload — only conveys the boolean; room context (`cname`, `host_id`) is inferred from the query string by the caller.
