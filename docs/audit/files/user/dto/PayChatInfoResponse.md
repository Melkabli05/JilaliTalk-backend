# PayChatInfoResponse

`src/main/java/com/jilali/user/dto/PayChatInfoResponse.java` (34 lines)

## Purpose
Response from `GET /profile/v1/get_pay_chat_info?to_id={userId}`. Uses `code`/`msg` envelope. Reports whether "paid chat" (a monetization gate on replies) applies between the viewer and a target user.

## Responsibilities
- Wrap the paid-chat status payload between two accounts.

## Public API
- `int code`, `String msg`, `@Nullable PayChatInfoData data`.
  - `PayChatInfoData`: `boolean otherSideSwitch`, `boolean mineSwitch`, `boolean payRelation`, `boolean overHistoryChatCount`, `int payVal`, `long validTime`, `boolean otherSideVersionPay`, `long payValUpdateTs`, `boolean otherSideInitiateSwitch`, `boolean mineInitiateSwitch` (all snake_case `@JsonProperty`).

## Dependencies
Depended on by `ProfileController.payChatInfo`, `ProfileBundleService` (as `PayChatInfoResponse.PayChatInfoData` in the bundle), `ProfileBundleResponse`, and `ProfileClient`.

## Coupling and cohesion analysis
Cohesive `code`/`msg` envelope; its nested `PayChatInfoData` is embedded directly into `ProfileBundleResponse`, coupling the bundle to this type's inner record.

## Code smells
- **Boolean-flag heavy** payload (7 booleans) — acceptable, mirrors the wire.
- `code`/`msg` envelope duplication (see package doc).

## Technical debt
Minimal — capture-informed, well-documented.

## Duplicate logic
- `code`/`msg` envelope shared with `BlockListResponse`, `ProfileLimitationsResponse`, `ProfileMeResponse`, `ReminderMomentResponse`, `UserLangsResponse` — candidate for `CodeMsgEnvelope<T>`.
- Only-in-bundle usage extracts `.data()` — same nested-`Data` extraction pattern as `ProfileStatsResponse`/`ProfileLimitationsResponse`/`ReminderMomentResponse` in `ProfileBundleService`.

## Dead or unused code
Live — used by the controller and the bundle service.

## Refactoring recommendations
1. Fold into a shared `CodeMsgEnvelope<PayChatInfoData>`.
