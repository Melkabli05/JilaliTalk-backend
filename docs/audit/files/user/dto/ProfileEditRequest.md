# ProfileEditRequest

`src/main/java/com/jilali/user/dto/ProfileEditRequest.java` (30 lines)

## Purpose
Request body for `POST /profile/v1/modify_baseinfo` — a generic partial-update endpoint; the app sends only the field(s) being changed plus `os_type`/`version`.

## Responsibilities
- Carry an optional subset of editable base-profile fields.

## Public API
- `@Nullable String birthday`, `@Nullable String nationality`, `@Nullable Integer learnLang2` (`learn_lang2`), `@Nullable Integer learnLang2Level`, `@Nullable Integer teachLang2`, `@Nullable Integer teachLang2Level`, `int osType` (`os_type`), `String version`.

## Dependencies
Depended on by `ProfileController.edit` and `ProfileClient`.

## Coupling and cohesion analysis
Cohesive partial-update payload. Low coupling.

## Code smells
- **Primitive Obsession**: lang IDs/levels as raw `Integer` with no enum/value type.
- **Uncertain field names**: `nationality` is an unverified guess (documented) — a correctness risk on a WRITE endpoint.
- **No validation** on `osType`/`version` (required-every-call per the javadoc but not enforced).

## Technical debt
- All-nullable partial-update record with no server-side guard that at least one editable field is present.
- `nationality` name unverified against a real capture.

## Duplicate logic
- The `learnLang2`/`teachLang2` fields conceptually overlap the language-pair data in `UserInfoResponse.LangInfo` and `UserLangsResponse` — the same "user languages" domain expressed three different ways (write request, full profile read, langs read).

## Dead or unused code
Live — bound by `ProfileController.edit`.

## Refactoring recommendations
1. Verify `nationality` against a live capture before relying on it.
2. Add a bean-validation cross-check that at least one editable field is non-null.
