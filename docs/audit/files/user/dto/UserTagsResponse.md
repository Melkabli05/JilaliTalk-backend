# UserTagsResponse

`src/main/java/com/jilali/user/dto/UserTagsResponse.java` (48 lines)

## Purpose
Response from `GET /config-center/v1/user_tags` — the static catalog of selectable interest/identity tags shown when editing a profile. Backs the caps in `ProfileLimitationsResponse`.

## Responsibilities
- Wrap the tag catalog grouped by category, with per-category children.

## Public API
- `int code`, `int status`, `String msg`, `@Nullable String md5Sum` (`md5_sum`), `@Nullable UserTagsData data`. **Note: carries BOTH `code` AND `status`.**
  - `UserTagsData`: `@Nullable List<TagGroup> hobby, occupation, mbti, constellation, bloodType` (`blood_type`) — only 5 of 8 categories.
  - `TagGroup`: `@Nullable Integer id`, `@Nullable String categoryTitle` (`category_title`), `@Nullable List<TagChild> children`.
  - `TagChild`: `@Nullable Integer id`, `@Nullable String name`.

## Dependencies
Depended on by `ProfileController.tags` and `ProfileClient`. Javadoc cross-references `ProfileLimitationsResponse`.

## Coupling and cohesion analysis
Cohesive catalog model. Low coupling.

## Code smells
- **Envelope anomaly**: it carries BOTH `code` and `status` (plus `msg`) — the only DTO in the package mixing both envelope discriminators, making a single generic envelope awkward.
- **Category mismatch**: only 5 categories here vs 8 in `TagLimit`/`TagsInfo` (documented — travelling/hometown/education served elsewhere).

## Technical debt
- The three-way tag-category divergence (5 vs 8 vs 8) across `UserTagsData`, `TagLimit`, `TagsInfo`, plus naming drift (`constellation` here vs `zodiacSign` elsewhere).

## Duplicate logic
- **Tag-category triple** (see `ProfileLimitationsResponse.md`): `UserTagsData` (5 categories: hobby, occupation, mbti, constellation, bloodType) vs `ProfileLimitationsResponse.TagLimit` (8 caps) vs `UserInfoResponse.TagsInfo` (8 arrays). Overlapping categories: hobby, occupation, mbti, zodiac/constellation, bloodType. Same tag domain modeled three times.

## Dead or unused code
Live — returned by `ProfileController.tags`.

## Refactoring recommendations
1. Define tag categories once (enum) and reconcile the 5-vs-8 split; align `constellation`/`zodiacSign` naming.
