# ProfileLimitationsResponse

`src/main/java/com/jilali/user/dto/ProfileLimitationsResponse.java` (50 lines)

## Purpose
Response from `GET /profile/v2/limitations`. Uses `code`/`msg` envelope. Tells the client which profile fields are editable and what caps/cooldowns apply — drives the edit-profile UI's disabled states.

## Responsibilities
- Wrap edit-permission booleans plus per-category tag caps and language-modify cooldown.

## Public API
- `int code`, `String msg`, `@Nullable LimitationsData data`.
  - `LimitationsData`: `@Nullable TagLimit tagLimit`, `@Nullable LangLimit langLimit`, `boolean modifyNationality`, `boolean modifyGender`, `boolean modifyBirthday`, `boolean modifyBirthdayByAdmin`, `boolean isModifyRestricted`.
  - `TagLimit`: eight `@Nullable Integer` caps (`hobbyLimit` via `hobby_lmit`, `travellingLimit`, `hometownLimit`, `educationLimit`, `occupationLimit`, `mbtiLimit`, `zodiacSignLimit`, `bloodTypeLimit`) — deliberately mirrors upstream's `_lmit` misspelling.
  - `LangLimit`: `@Nullable Integer limitDays`, `@Nullable Long nextModifyTs`.

## Dependencies
Depended on by `ProfileController.limitations`, `ProfileBundleService`, `ProfileBundleResponse` (embeds `LimitationsData`), `ProfileClient`, and referenced in `UserTagsResponse`'s javadoc.

## Coupling and cohesion analysis
Cohesive edit-affordances model. `LimitationsData` is reached into by `ProfileBundleResponse` (nested-record coupling).

## Code smells
- **Boolean-flag heavy** `LimitationsData`.
- `code`/`msg` envelope duplication.

## Technical debt
- The eight tag caps in `TagLimit` mirror the eight tag categories in `UserInfoResponse.TagsInfo`, but `UserTagsResponse` only provides 5 of them — a domain inconsistency spanning three DTOs.

## Duplicate logic
- `code`/`msg` envelope shared with the `code`/`msg` family.
- **Tag-category triple**: `TagLimit`'s 8 categories vs `UserInfoResponse.TagsInfo`'s 8 categories vs `UserTagsResponse.UserTagsData`'s 5 categories — three DTOs enumerate overlapping tag categories with slightly different sets and naming (`zodiacSign` vs `constellation`).

## Dead or unused code
Live — used by the controller and bundle service.

## Refactoring recommendations
1. Fold into `CodeMsgEnvelope<LimitationsData>`.
2. Define the tag categories once (an enum) and reference it across `TagLimit`, `TagsInfo`, and `UserTagsData`.
