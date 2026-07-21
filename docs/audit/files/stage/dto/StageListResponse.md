# StageListResponse

`src/main/java/com/jilali/stage/dto/StageListResponse.java`

## Purpose
Response for `GET /list` — whether the host is present in the room, plus the list of current stage members.

## Responsibilities
Carry `is_host_in_room` and a null-safe list of `StageMember`.

## Public API
Record `StageListResponse`:
- `boolean isHostInRoom` (`@JsonProperty("is_host_in_room")`).
- `List<StageMember> list` — `@Nullable` component; **overridden accessor** returns `List.of()` when null (null-object pattern for empty upstream lists).

## Dependencies
- `@Serdeable`, `@JsonProperty`, `@Nullable`, `java.util.List`, `StageMember`.
- Depended on by: `StageController.list`, `JilaliGateway.stageList`, `JilaliClient`, and reused by `RoomJoinService` / `JoinBundleResponse` (composed into the room-join bundle).

## Coupling and cohesion analysis
Cohesive. Good practice: the overridden `list()` accessor guarantees non-null to consumers, avoiding NPEs downstream. Reused beyond stage (room-join bundle), so it is a shared read model — mild cross-package coupling but a deliberate composition.

## Code smells
- Minor: the null-collapsing accessor is a small piece of **logic in a DTO** (justified and idiomatic here).

## Technical debt
None significant.

## Duplicate logic
The null→`List.of()` accessor pattern is duplicated in `VipExperienceCardRecordsResponse.cards()` — a candidate for a shared helper.

## Dead or unused code
None; consumed by the controller and the room-join bundle.

## Java 25 modernization opportunities
Already a record. The null-safe accessor is a clean idiom; no change needed beyond possibly a shared `nullToEmpty` utility.

## Micronaut built-in opportunities
Serde handles the `@Nullable` list correctly; no validation needed on a response DTO.

## Refactoring recommendations
Extract the shared null→empty-list idiom into a small utility to remove duplication with the VIP records response.
