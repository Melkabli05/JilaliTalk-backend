# Room Removal: Stay, Go Invisible, Add Notification

## Summary

Replace the current "kick from room" behavior — which fires a toast and
navigates the user out via `onLeave()` — with a softer handling: keep the
user in the room as an invisible listener (no longer on the stage or
audience roster), record the kick in the notification panel with the
manager's identity, and render an in-room event card so the user can see
who removed them.

The block persists until the user leaves the page (refresh or
navigate-away); manual "Go visible" and stage actions are refused while
kicked.

## Motivation

Today, receiving a `room_kick` event in `room-page-base.ts` causes an
immediate `onLeave()` navigation. This is jarring: the user loses all
room context (chat history, who was on stage, the topic) and cannot
recover easily. A "ghost listener" state — still able to read the room
silently — is a friendlier middle ground and matches the convention
already used by the "VIP limit" and "region block" join paths in
`base-room-store.ts`.

The notification panel addition turns the kick into a persistent,
reviewable event rather than a transient toast.

## In scope

- The frontend `room_kick` handler in `room-page-base.ts`.
- A new `_kicked` guard on `BaseRoomStore` (blocks `setVisibility(true)`
  until `leaveRoom()`).
- Calling existing methods on `StageStore` (`removeStageUser`),
  `NotificationStore` (`addUserEvent`), and `CommentsStore`
  (`pushUserEventCard`) from the new kick handler — none of these
  stores themselves change. `AudienceStore` needs no call at all; its
  own existing `room_kick` handling already removes the user.
- A new optional `managerId` / `managerHeadUrl` field on the `room_kick`
  realtime event type so the notification panel can render the
  manager's profile.
- A pure helper `kick-handler.util.ts` extracted for testability.
- Unit tests for the helper (Vitest).

## Out of scope

- Server-side "kicked from this room" persistence (no upstream API
  contract exists in the captured HelloTalk traffic for this; out of
  scope until needed).
- Re-join mechanism via the notification panel (notification click
  opens the manager's profile; deep-linking into the room from the
  panel is a separate feature).
- Changes to `mod-store.ts`, `user-action-modal.ts`, or any host/moderator
  UX — this spec only changes what happens to the kicked user.

## Current behavior (today)

In `room-page-base.ts` lines 130–135, the `bffEventEffect` short-circuits
on `room_kick`:

```ts
if (event.type === 'room_kick' && Number(event.userId) === this.roomStore.userId()) {
  this.toast.warning(`You were removed from the room by ${event.managerName}`);
  void this.onLeave();
  return;
}
```

`onLeave()` (line 378) tears down the WebSocket, leaves the room, and
navigates to the room list. No persistent notification; no event card
beyond what `comments-store.ts` already pushes for the kicked user.

## Target behavior

When the local user's `room_kick` event fires, run a single orchestrator
that:

1. Flips `roomStore.setVisibility(false)`. The user becomes invisible
   immediately. The header's "Go visible" toggle and any future
   identity-revealing action check the new `_kicked` flag and refuse
   while set.
2. If `roomStore.isOnStage()`, calls `api.leaveStage(cname, busiType)`
   (best-effort) and `stageStore.removeStageUser(userId)` to drop the
   user from the local stage list immediately (the BFF may or may not
   also emit a `stage_kick` event; the explicit remove guarantees
   the user is off-stage on this client regardless).
3. The audience list removal is **already handled by
   `AudienceStore`'s own `room_kick` switch case** at
   `audience-store.ts:137`. No work needed here.
4. Fires a warning toast: `You were removed from "{roomName}" by
   {managerName}. You are now an invisible listener.`
5. Adds a notification via
   `notificationStore.addUserEvent({ type: 'warning', title: 'Removed
   from "{roomName}" by {managerName}', message: 'You are now an
   invisible listener in this room.', userId: managerId ?? undefined,
   avatarUrl: managerHeadUrl ?? null, nickname: managerName })`.
   Persisted to `localStorage` by the existing `NotificationStore`
   effect. Clicking the entry opens the manager's `UserInfoModal`
   (existing panel behavior for `userId`-linked entries).
6. Pushes the existing `'room_kick'` event card into
   `commentsStore.pushUserEventCard('room_kick', userId, 'roomkick',
   { nickname, managerName })` — already supported by
   `event-card.ts` and already handled by `comments-store.ts`.

The `_kicked` flag is cleared by `BaseRoomStore.leaveRoom()` (called
during navigation away), so a subsequent deep-link or refresh re-enters
as a fresh user. The notification panel entry persists independently of
this flag.

## Files affected

| File | Change |
|---|---|
| `JilaliTalk-angular-frontend/src/app/core/realtime/room-realtime-events.ts` | Add optional `managerId?: string` and `managerHeadUrl?: string \| null` to the `room_kick` event variant. |
| `JilaliTalk-angular-frontend/src/app/features/room/state/base-room-store.ts` | Add `_kicked` signal + `setVisibility(true)` guard + clear on `leaveRoom`. |
| `JilaliTalk-angular-frontend/src/app/features/room/state/stage-store.ts` | No new method needed — `removeStageUser(uid)` already exists (used today in `room-page.ts:493`) and is called directly from the kick handler. |
| `JilaliTalk-angular-frontend/src/app/features/room/data/kick-handler.util.ts` | **New.** Pure `processKick(event, deps)` helper returning `{ shouldReturn: boolean }` to indicate whether the caller should return early (i.e., the event wasn't for the local user). |
| `JilaliTalk-angular-frontend/src/app/features/room/data/kick-handler.util.spec.ts` | **New.** Vitest tests for the helper covering: not-me event, not on stage, on stage, `leaveStage` failure, `managerId` missing, full happy path. |
| `JilaliTalk-angular-frontend/src/app/features/room/pages/room-page-base.ts` | Replace lines 130–135 with a single call to `processKick(...)`. Inject `NotificationStore`. |
| `JilaliTalk-angular-frontend/src/app/features/room/feature/room-header/room-header.ts` | No change. The existing "Go visible" handler calls `roomStore.setVisibility(true)` in the page component (`room-page.ts:365`, `video-room-page.ts:414`) — that method now refuses when `_kicked` is set, so the toggle silently no-ops for the kicked user. Behavior emerges from the `BaseRoomStore` guard; no template or header-component change needed. |

`comments-store.ts`, `event-card.ts`, and `audience-store.ts` are
already wired for `room_kick` (audience removal + event card) — no
changes there.

## Event payload change

`room-realtime-events.ts` line 74 currently:

```ts
| { readonly type: 'room_kick'; readonly userId: string; readonly nickname: string; readonly managerName: string; readonly cname: string }
```

Becomes:

```ts
| { readonly type: 'room_kick'; readonly userId: string; readonly nickname: string; readonly managerName: string; readonly cname: string; readonly managerId?: string; readonly managerHeadUrl?: string | null }
```

Both new fields are optional. If the BFF hasn't been updated to emit
them, the frontend still works: the notification entry has
`userId: undefined` and renders without an avatar or click-to-profile,
and the toast/message still includes `managerName` (which is on the
existing payload).

## State machine on `BaseRoomStore`

`setVisibility(true)` gates on `!_kicked()` — if `_kicked()` is true,
the call is a no-op (no error toast; the user is already in the kicked
state). This is the only guard needed: `setRole()` is called exactly
once today, at initial room join (`room-page.ts:274`,
`video-room-page.ts:332`) — there is no live "become a moderator again"
call path mid-session for the local user (mod_accepted/mod_removed for
the local user only surface as a toast via `ImBootstrapService`, per
`im-bootstrap.service.ts:105-108` — they don't call `setRole`). So
guarding `setRole` would protect against a call path that doesn't
exist; not adding that guard keeps the change minimal.

`_kicked` is set to `true` only by the kick handler. It is cleared by
`leaveRoom()` (i.e., during navigation away).

There is no automatic re-flip to visible. Once kicked, the user stays
kicked until the room page unmounts.

## Error handling

- `leaveStage()` failure: log via `console.warn`, do not show error
  toast. Server has already removed the user; local state flips anyway.
- `stageStore.removeStageUser()` for a user not currently on stage:
  no-op (existing method, simple `filter`).
- `managerId` missing on the event: notification entry added with
  `userId: undefined`; comment panel card unaffected.
- `_kicked` guard blocking `setVisibility(true)`: silent no-op. The
  "Go visible" toggle is the user-visible signal that this is blocked;
  no toast.

## Testing strategy

- **Unit (Vitest):** Test `kick-handler.util.ts` exhaustively with a
  fake `deps` object. Cover at minimum:
  - event for a different user → returns `{ shouldReturn: true }`,
    no other side effects.
  - event for the local user, not on stage → no `leaveStage` call,
    notification added, toast fired.
  - event for the local user, on stage as moderator → `leaveStage`
    called, `removeStageUser` called.
  - `leaveStage` rejects → logged warning, `removeStageUser` still called.
  - `managerId` missing → notification entry has no `userId`.
- **Manual:** Trigger a kick from a second account, observe toast,
  verify notification entry appears with manager avatar, verify
  comment-panel card shows, verify "Go visible" toggle no-ops.
- **Regression:** The existing `room_kick` branch in
  `comments-store.ts` (line 287) still pushes the event card —
  untouched. The existing `_kicked` flag is additive, not modifying
  any existing state.

## Open risks

- The new `managerId` field is optional. If the BFF never starts
  emitting it, notification entries will lack a clickable avatar.
  Acceptable for now; revisit if the data becomes available.
- Local "ghost" state is per-session. If the user closes the tab and
  reopens the same room, they're a fresh user. The notification panel
  is the durable record.

## Acceptance criteria

- A kick for the local user results in the user remaining on the room
  page in invisible listener state.
- The toast fires once with the room name and manager name.
- A warning entry appears at the top of the notification panel with
  the manager's avatar (if `managerId` is present) and is clickable to
  the manager's profile.
- The in-room event card renders identifying the kicked user and the
  manager.
- "Go visible" toggle in the room header is non-functional until the
  user navigates away or refreshes.
- All existing Vitest specs continue to pass; new helper spec passes.
- `ng build --configuration=development` and `npm run verify` clean.