# `com.jilali.manager.ManagerController`

## Purpose
HTTP controller exposing room moderator/manager operations (list, set, approve, judge) at `/api/managers`. Delegates to the downstream `JilaliClient` and unwraps `JilaliResponses` envelopes before returning.

## Public API

- **Constructor** `ManagerController(JilaliClient client)` — injected HTTP client to the upstream room service.
- `@Get list(@NotBlank String cname, @QueryValue("host_id") long hostId)` — returns `ManagerListResponse`; `GET /api/managers`.
- `@Post set(@Valid @Body SetManagerRequest request)` — assigns/removes a manager; returns `204 No Content`; `POST /api/managers`.
- `@Post("/approve") approve(@Valid @Body ApproveManagerRequest request)` — approves a pending manager action; returns `204 No Content`.
- `@Get("/judge") judge(@NotBlank String cname, @QueryValue("host_id") long hostId)` — returns `ManagerJudgeResponse` (online status).

All routes run on `TaskExecutors.BLOCKING`.

## Coupling
Imports `JilaliClient` and `JilaliResponses` (likely from `com.jilali.client`), DTOs from `com.jilali.manager.dto`. Cross-references: see `docs/audit/files/client/JilaliClient.md`.

## Notes
No `@Secured`/role-based authorization annotations are present on any endpoint. Any caller able to reach the controller can promote/demote room managers or approve manager operations — this is a real security concern if the route is not fronted by an upstream auth filter that enforces a moderator/host role. Each handler trusts the `cname`/`host_id`/`user_id` from the request without ownership verification.
