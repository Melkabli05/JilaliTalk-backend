# BlockListResponse

`src/main/java/com/jilali/user/dto/BlockListResponse.java` (29 lines)

## Purpose
Response from `GET /report_logic/v2/black/list` (the caller's block list). Uses the `code`/`msg` envelope.

## Responsibilities
- Wrap the block-list payload; leave the per-item shape as untyped maps because no populated capture was ever observed.

## Public API
- `int code` — non-null primitive.
- `String msg` — non-null (not annotated `@Nullable`).
- `BlockListData data` — `@Nullable`.
  - `BlockListData.blackList` — `@JsonProperty("black_list")`, `@Nullable List<Map<String,Object>>`.

## Dependencies
Depended on by `ProfileController.blocklist` and `ProfileClient`.

## Coupling and cohesion analysis
Cohesive envelope wrapper. Coupling low.

## Code smells
- **Primitive Obsession / stringly-typed payload**: `List<Map<String,Object>>` for the block items — an untyped map leaked to the frontend. Documented as intentional (never observed populated), but still a typing gap.
- **Inconsistent nullability**: `msg` is not `@Nullable` here, whereas `RoomUserProfileResponse.msg` and `UserInfoResponse.msg` (same `code`/`msg` envelope) ARE `@Nullable`.

## Technical debt
- The `blackList` map list must be typed once a populated response is captured (self-documented in the javadoc).

## Duplicate logic
- Shares the exact `int code / String msg / @Nullable Data` envelope with `PayChatInfoResponse`, `ProfileLimitationsResponse`, `ProfileMeResponse`, `ReminderMomentResponse`, `UserLangsResponse` — the `code`/`msg` envelope family. Prime candidate for a generic `CodeMsgEnvelope<T>`.

## Dead or unused code
Live — returned by `ProfileController.blocklist`.

## Refactoring recommendations
1. Type `blackList` when a populated capture exists.
2. Fold into a shared `CodeMsgEnvelope<BlockListData>` generic.
3. Mark `msg` `@Nullable` for consistency with sibling envelopes.
