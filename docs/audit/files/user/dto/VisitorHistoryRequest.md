## Purpose

Inbound request DTO for the `POST /api/profile/visitors` endpoint. Carries the cursor and required `cname` to fetch a paged list of users who visited the caller's profile.

## Public API

- `record VisitorHistoryRequest(@NotNull Long cname, int index)` — `cname` is the caller's own id (the BFF derives the visitor cursor from a different upstream field per `Md5Util.visitorHistorySign`); `index` is the cursor (default 0).

## Dependencies

- Inbound: `user/ProfileController#visitors(@Body VisitorHistoryRequest)`.
- Validated via `micronaut-validation` (`@NotNull`).
- The actual visitor-history upstream call is the only call site for the `Md5Util.visitorHistorySign` (caller + timestamp + index).
- Maps directly into `room/dto/VisitRequest` for the cache-keyed profile enrichment pass (`ProfileBundleService`).

## Coupling and cohesion

Single-shape request record. Cohesive; no notable coupling beyond its single endpoint.

## Code smells

- `cname` is a `Long` but the field semantically names a *user id* (named "cname" because it's the caller's own id and the variable name got copy-pasted from a different field that was actually a channel-name). The doc comment should be clearer.
- The wire payload upstream accepts (per `old_hellotalk/scriptv2.js` and `bq0/c.smali:953-1055`) is: `{device_type, client_ts, index, device_id, sign, client_ver, update_ts, client_os}`. None of those 8 fields are present in this DTO — the BFF's `ProfileController.visitors()` method synthesizes them all from the BFF's own state (device-id, app version, current timestamp, sign = `MD5(jid + jid + client_ts)`). So **this DTO is really just a thin "index" carrier**, not a wire shape. The field name `cname` is misleading because no upstream field shares the name.

## Technical debt

- Misleading field name (`cname`).
- No nullability on `index` (it's a primitive `int` so no @NotNull applies, but a negative `index` is rejected only upstream — add a `@PositiveOrZero` constraint if the framework supports it on primitives via validation annotations).

## Duplicate logic

None — this is the only cursor-style request shape in `user/`.

## Dead or unused code

None.

## Java 25 modernization opportunities

- The single `Long` field could be a `long` with the framework's primitive-friendly null check at deserialization. Marginal.

## Micronaut built-in opportunities

- Add `@PositiveOrZero` on `index` (or convert to a custom `@Cursor` annotation) to fail-fast at the seam rather than relying on upstream to reject.

## Refactoring recommendations

1. **Low**: rename `cname` → `callerUserId` (or just `userId`) — the field's role is the BFF-side caller identification, not a HelloTalk "channel name."
2. **Low**: clarify the doc comment to make the field's role unambiguous.
3. **Low**: move the field validation into a typed value (e.g. `record VisitorHistoryRequest(@NotNull CallerId caller, Cursor cursor)`) so the type system carries the contract.
