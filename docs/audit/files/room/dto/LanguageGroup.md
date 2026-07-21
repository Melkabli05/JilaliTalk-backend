# LanguageGroup.java

`src/main/java/com/jilali/room/dto/LanguageGroup.java`

## Purpose
Element of the bare-array payload from `language_group/{voice,live}` — a language filter grouping.

## Responsibilities
Carry a language id, its member language ids, and their count.

## Public API (record fields)
- `@JsonProperty("lang_id") int langId`
- `@Nullable List<Integer> langs`
- `@JsonProperty("langs_len") int langsLen`

## Dependencies
- Imports `@JsonProperty`, `@Nullable`, `@Serdeable`.
- Returned (as `List<LanguageGroup>`, cached) by `RoomController.languageGroupsVoice/Live`; produced by `JilaliClient.languageGroupVoice/Live`.

## Coupling and cohesion analysis
Cohesive, minimal.

## Code smells
- `langsLen` is a redundant denormalized count of `langs.size()` — a derivable field carried verbatim from upstream (mild).

## Technical debt
`langsLen` can drift from `langs` length.

## Duplicate logic
`langId` + `langs` pairing echoes `Channel.langId`/`Channel.langs` but this is a different (grouping) concept.

## Dead or unused code
None.

## Refactoring recommendations
Could drop `langsLen` and derive from `langs`, but harmless as an upstream passthrough.
