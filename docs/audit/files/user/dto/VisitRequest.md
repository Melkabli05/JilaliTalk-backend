# VisitRequest

`src/main/java/com/jilali/user/dto/VisitRequest.java` (11 lines)

## Purpose
Intended request body for a "record a profile visit" action. In practice it backs nothing.

## Responsibilities
- (Intended) carry `uid`, `visitorUid`, `enter` for the visit-record call. Currently: none — never referenced.

## Public API
- `long uid`, `long visitorUid`, `String enter` — no `@JsonProperty` annotations, no validation.

## Dependencies
**None.** No controller, service, or client references it.

## Coupling and cohesion analysis
Zero coupling — orphaned. Cohesion N/A.

## Code smells
- **Dead code**: unreferenced type.
- **Primitive Obsession**: raw `long`/`String` fields.
- **Missing `@JsonProperty`**: field names (`visitorUid`, `enter`) would not match an upstream `visitor_uid`/snake_case wire — further evidence it was never wired up.

## Technical debt
- A dead type that misleads: it looks like the model for `/visit`, but `ProfileController.visit` actually binds `@Body Map<String,Object>` and constructs a `ProfileClient.VisitBody` (untyped map hand-parsing) instead.

## Duplicate logic
- Overlaps the intent of `ProfileClient.VisitBody` (the actual visit-record body). `VisitRequest`'s `{uid, visitorUid, enter}` is a would-be typed equivalent of the map that `ProfileController.visit` hand-parses.

## Dead or unused code
- **CONFIRMED DEAD (independently verified).** `grep -rn "VisitRequest" src/main/java` (excluding the `VisitorHistoryRequest` substring match) returns ONLY the declaration at `VisitRequest.java:6`. A whole-repo grep across `.java` and `.ts` (frontend) found no other reference. `ProfileController.visit` uses `Map<String,Object>` + `ProfileClient.VisitBody`, not this record. Nothing constructs, binds, or returns `VisitRequest`.

## Refactoring recommendations
1. **Either delete `VisitRequest`**, or — preferably — use it: bind `@Body @Valid VisitRequest` in `ProfileController.visit` (add `@JsonProperty` + validation) to replace the untyped `Map` hand-parsing flagged in `ProfileController.md`.
