# UserBase.java

`src/main/java/com/jilali/room/dto/UserBase.java`

## Purpose
The reusable "base" user profile block LiveHub nests under `base` — identity plus the HelloTalk language-learning profile (native/teach/learn languages and levels, timezone).

## Responsibilities
Carry a user's display identity and full multi-language learning profile.

## Public API (record fields, 20)
- Identity: `@Nullable String nickname`, `@Nullable String signature`, `@JsonProperty("head_url") @Nullable String headUrl`, `@Nullable String nationality`.
- `@JsonProperty("native_lang") int nativeLang`.
- Teach: `teachLang2`, `teachLang2Level`, `teachLang3`, `teachLang3Level` (int).
- Learn: `learnLang1..5` and `learnLang1Level..5Level` (10 ints).
- `@JsonProperty("time_zone") long timeZone`.

## Dependencies
- Imports `@JsonProperty`, `@Nullable`, `@Serdeable`.
- Composed by `RoomUser.base`, `VoiceRoomInfoResponse.HostInfo.base`, `VoiceRoomInfoResponse.ReqUserInfo.base`.

## Coupling and cohesion analysis
This IS the intended shared user block — and it is correctly composed by three DTOs. But it is only *partly* the identity base: `HostUser`, `ManagerInfo`, `PinnedComment`, and `RoomUser`'s top-level fields duplicate the `{nickname, headUrl, nationality}` subset instead of composing `UserBase`.

## Code smells
- **Large record / Data Clump**: the `teachLang*`/`learnLang*` numbered fields (14 of them) are a textbook data clump — should be `List<LanguageSkill>`.
- **Primitive Obsession**: language ids and levels as bare ints.

## Technical debt
Numbered `learnLang1..5`/`teachLang2..3` fields are rigid — adding a 6th learn language means a schema change rather than a list append.

## Duplicate logic
`{nickname, headUrl, nationality}` here is the subset that `HostUser`/`RoomUser`(top-level)/`ManagerInfo`/`PinnedComment` re-declare rather than reuse. So: `UserBase` *is* reused via composition by three DTOs, but a parallel identity duplication exists across four other DTOs.

## Dead or unused code
None.

## Refactoring recommendations
- Replace the numbered teach/learn fields with `List<LanguageSkill(langId, level)>`.
- Extract a `UserIdentity(nickname, headUrl, nationality[, userId])` shared by both `UserBase` and the flat identity DTOs.
