# UserLangsResponse

`src/main/java/com/jilali/user/dto/UserLangsResponse.java` (24 lines)

## Purpose
Response from `GET /go_user_search/v1/go_user_info/get_user_langs`. Uses `code`/`msg` envelope. Lists a user's language settings.

## Responsibilities
- Wrap a list of language items.

## Public API
- `int code`, `String msg`, `@Nullable List<UserLangItem> data`.
  - `UserLangItem`: `int lang`, `int isTemp`, `int isExpiredVipSelfSetLang` (`is_expired_vip_self_set_lang`).

## Dependencies
Depended on by `ProfileController.langs` and `ProfileClient`.

## Coupling and cohesion analysis
Cohesive langs envelope. Low coupling.

## Code smells
- **Inconsistent wire naming within the nested record**: `lang`/`isTemp` are plain camelCase field names (no `@JsonProperty`), while `isExpiredVipSelfSetLang` DOES get `@JsonProperty("is_expired_vip_self_set_lang")` — mixed conventions in one 3-field record. `isTemp` likely mismatches an upstream `is_temp` key.
- **Boolean-as-Integer**: `isTemp`, `isExpiredVipSelfSetLang` are 0/1 ints.

## Technical debt
- `isTemp` without `@JsonProperty("is_temp")` is a probable silent wire-key bug (if upstream sends `is_temp`).

## Duplicate logic
- `code`/`msg` envelope shared with the `code`/`msg` family.
- The "user languages" domain overlaps `UserInfoResponse.LangInfo` (teach/learn langs) and `ProfileEditRequest.learnLang2`/`teachLang2` — three representations of language data.

## Dead or unused code
Live — returned by `ProfileController.langs`.

## Refactoring recommendations
1. Add `@JsonProperty("is_temp")` on `isTemp` (verify against a capture).
2. Model 0/1 ints as `boolean`.
