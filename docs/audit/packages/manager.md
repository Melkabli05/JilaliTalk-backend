# `com.jilali.manager` — voice-room manager (moderator) role

## Purpose

Voice-room "manager" (moderator) role lifecycle: assigning/removing managers in a room, approving manager-related operations. Smallest feature package by file count.

## File responsibilities (1 root + 5 dto = 6 files)

| File | One-line summary |
|---|---|
| `ManagerController.java` | The single controller for `/api/manager/*` endpoints. |
| `dto/ApproveManagerRequest.java` | Approve a pending manager-related operation (carries an `operation_type` discriminator). |
| `dto/Manager.java` | A "manager" record — likely `{userId, cname, role, grantedAt, grantedBy, …}`. |
| `dto/ManagerJudgeResponse.java` | Server's response to a "judge manager candidacy" decision. |
| `dto/ManagerListResponse.java` | Paged list of managers in a room. |
| `dto/SetManagerRequest.java` | Grant/revoke manager role — `{cname, host_id, userId, action, busiType}`. |

## Dependencies

- **Inbound**: Angular frontend consumes the REST endpoints.
- **Outbound**: `client.JilaliClient` only.

## ⚠ Security gap (confirmed by audit agent)

`ManagerController` has **no `@Secured`, no role check, no ownership check, no comparison of the caller's uid against `host_id`** anywhere in the file. The `set`, `approve`, `judge`, and `list` endpoints all trust `cname`/`host_id`/`user_id` from the request body verbatim. Any caller able to reach the controller can promote or demote moderators, or approve manager-related operations.

This is mitigated only by an upstream `auth.SessionAuthClientFilter` (which confirms the caller IS a logged-in user but doesn't check *which* user, and runs only on outbound HTTP-to-HelloTalk calls, not on the inbound REST surface). On the REST surface, this controller has effectively no authorization check.

This is likely the most important security bug in the entire codebase. Required-action flag for the rewrite.

## Improvement opportunities

1. **CRITICAL**: add a `@Secured` (Micronaut Security) or equivalent authorization layer before any production use, OR add a hand-rolled check that compares the inbound `Authorization` JWT's `uid` against the target `host_id` (or against a moderator list stored elsewhere) before executing `set`/`approve`/`judge`. Confirm whether an upstream gateway is trusted to enforce this — if not, ship the fix in-jilalibff.
2. **Medium — DTO cluster**: the 4 `*Request` DTOs share a common `(cname, userId)` shape with one extra action-specific field each. Same refactor pattern as `stage`: a Java 25 sealed `ManagerAction` interface would consolidate.
3. **Low**: `Manager.role`, `SetManagerRequest.action`, `SetManagerRequest.busiType` are raw `int`s with no `micronaut-validation` constraints — meaning is owned entirely by upstream, which is risky if upstream adds new role/action codes.
