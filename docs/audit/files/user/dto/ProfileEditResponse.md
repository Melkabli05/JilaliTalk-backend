# ProfileEditResponse

`src/main/java/com/jilali/user/dto/ProfileEditResponse.java` (12 lines)

## Purpose
Response from `POST /profile/v1/modify_baseinfo`.

## Responsibilities
- Carry the edit result envelope.

## Public API
- `int status`, `String msg`.

## Dependencies
Depended on by `ProfileController.edit` and `ProfileClient`.

## Coupling and cohesion analysis
Trivially cohesive. No `data` payload.

## Code smells
- **Envelope-naming inconsistency (notable)**: this uses `status` + `msg` — a HYBRID of the two package conventions (`status`/`message` and `code`/`msg`). Every other `status`-keyed envelope in the package pairs it with `message`, and every `msg`-keyed one pairs it with `code`. `ProfileEditResponse` is the lone `status`+`msg` mix.

## Technical debt
- The mixed `status`/`msg` naming makes a generic envelope harder to introduce and is a subtle client trap.

## Duplicate logic
- Envelope-only shape overlaps every other two-field envelope but matches none exactly due to the `status`+`msg` mix.

## Dead or unused code
Live — returned by `ProfileController.edit`.

## Refactoring recommendations
1. Align to whichever envelope generic is adopted (`status`/`message`) — or document why the wire genuinely returns `msg` here.
