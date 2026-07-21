# ReminderMomentResponse

`src/main/java/com/jilali/user/dto/ReminderMomentResponse.java` (28 lines)

## Purpose
Response from `GET /profile/v1/reminder_moment?to={userId}`. Uses `code`/`msg` envelope. Supplies copy for a "nudge them to post a Moment" CTA when viewing another user.

## Responsibilities
- Wrap the reminder type + CTA strings.

## Public API
- `int code`, `String msg`, `@Nullable ReminderMomentData data`.
  - `ReminderMomentData`: `int reminderMomentType` (`reminder_moment_type`; 0 = no nudge), `@Nullable String reminderDesc`, `@Nullable String buttonDesc`, `@Nullable String afterClickDesc`.

## Dependencies
Depended on by `ProfileController.reminderMoment`, `ProfileBundleService`, `ProfileBundleResponse` (embeds `ReminderMomentData`), and `ProfileClient`.

## Coupling and cohesion analysis
Cohesive CTA-copy envelope. `ReminderMomentData` reached into by `ProfileBundleResponse`.

## Code smells
- `code`/`msg` envelope duplication.
- **Weak typing of a mode**: `reminderMomentType == 0` sentinel plus three empty strings encodes "no nudge" implicitly.

## Technical debt
Minimal.

## Duplicate logic
- `code`/`msg` envelope shared with the `code`/`msg` family.

## Dead or unused code
Live — used by controller and bundle.

## Refactoring recommendations
1. Fold into `CodeMsgEnvelope<ReminderMomentData>`.
