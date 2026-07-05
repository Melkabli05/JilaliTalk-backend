# BFF Consolidation Round 3 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Three small, frontend-only cleanups — extract `UserInfoService.ensureFresh()`, remove the unused `judgeManager()` / `ManagerJudgeResponse` dead code, and extract a shared `UserIdentityCardComponent` used by both `user-info-modal` and `user-action-modal`.

**Architecture:** Mirror Round 2's "extend the existing pattern, no new infrastructure" principle. `ensureFresh()` is one inline-guard extraction on the existing `UserInfoService`. Dead-code removal is two-file deletion. The shared component is a dumb `OnPush` projection-based shell (analogous to `ErrorMessage` in the CLAUDE.md §6 example) in `shared/ui/user-identity-card/`, replacing the duplicated markup but keeping each modal's diverging chip set in their own templates via `<ng-content select="[metaChips]">`.

**Tech Stack:** Angular 21.2 / TypeScript 5.9.3, Angular CDK Dialog, signals-based templates (`@if`, `@for`, `input.required<T>()`), `rxResource` for HTTP-backed resources. Existing per-repo conventions: `CLAUDE.md` (dependency direction `features → store → core → shared`, `OnPush`, no `standalone: true`, no manual `subscribe()`, `<app-avatar>` / `<app-modal>` from `shared/ui/`).

**Branch & worktree:** Both repos already have a `bff-consolidation-round-3` branch in `.worktrees/` (created at session start from `master`/`main`); if either worktree is missing, create it via `superpowers:using-git-worktrees` before dispatching. All work happens in those worktrees — never in the main checkout.

## Global Constraints

- **Unit tests explicitly skipped** per standing project instruction ("1 and skip unit tests"). No test files are to be created. Verification is `npx tsc --noEmit` clean and the manual smoke checks listed per task.
- **Frontend only.** No `jilalibff` changes this round. The backend `ManagerController.judge()` endpoint and `ManagerJudgeResponse.java` are deliberately **not** removed; deleting a working backend endpoint on the strength of "the current frontend doesn't call it" is a bigger call outside this round's scope.
- **Conventions to honor from `JilaliTalk-angular-frontend/CLAUDE.md`:** OnPush + signals; no `standalone: true`; cross-boundary imports use aliases (`@core/*`, `@shared/*`, `@store/*`, `@features/*`); relative imports within a feature are fine; new components go in `shared/ui/` only when they depend on nothing feature-specific (this new card is feature-agnostic, so it does); new services stay in `core/`; styling via design tokens, no hardcoded colors.
- **Commit style:** every task ends in one commit, subject line ≤ 70 chars, body explains *why*, ends with `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`.
- **Commit scope:** keep each task to its listed files only — no drive-by refactors.

---

### Task 1: Add `UserInfoService.ensureFresh()`

**Files:**
- Modify: `JilaliTalk-angular-frontend/.worktrees/bff-consolidation-round-3/src/app/core/services/user-info.service.ts` (around line 259, immediately after `fetchUserInfo()`)

**Interfaces:**
- Produces: a new public method `ensureFresh(userId: number): void` on `UserInfoService`. Signature is fixed; later tasks consume it.

- [ ] **Step 1: Read the current file to confirm insertion point and surrounding imports.**

Run (from the worktree):
```
sed -n '255,280p' src/app/core/services/user-info.service.ts
```
Expected: see `fetchUserInfo()` defined. The new method goes immediately after that method, before `enrichBatchAndCache()`.

- [ ] **Step 2: Add the `ensureFresh()` method.**

Insert this block immediately after the closing `}` of `fetchUserInfo()` (currently around line 274) and before the existing doc-comment that introduces `enrichBatchAndCache`:

```ts
  /**
   * Triggers a re-fetch only if the cached entry is missing or stale (see isStale).
   * Fire-and-forget — callers read the result reactively via getUserInfo(), matching how
   * fetchUserInfo() is already used at every existing call site.
   */
  ensureFresh(userId: number): void {
    if (!(userId > 0)) return;
    if (!this.getUserInfo(userId) || this.isStale(userId)) {
      void this.fetchUserInfo(userId);
    }
  }

```

Verify with `grep -n "ensureFresh" src/app/core/services/user-info.service.ts` — expected: one line (the method definition).

- [ ] **Step 3: Verify TypeScript compiles.**

Run: `npx tsc --noEmit -p tsconfig.json`
Expected: zero errors. (No frontend files were changed other than this method add, so no callers are broken.)

- [ ] **Step 4: Commit.**

```
git add src/app/core/services/user-info.service.ts
git commit -m "feat(user-info): add ensureFresh() to centralize staleness guard"
```

Body (append after the subject, before the Co-Authored-By):

```
The decision of "when does this uid need a re-fetch" was duplicated
verbatim across 3 call sites (user-info-modal, ghost-audience.util,
user-action-modal). Centralizing it here makes future policy changes
(sliding TTLs, backoff on repeat failure) a single-file edit and
removes the chance of a new call site forgetting the isStale half of
the guard — which would silently revert to "fetch once per session",
the bug Round 2 fixed.

Fire-and-forget void return matches existing usage: every existing
caller awaits nothing and reads the cache reactively via getUserInfo().
```

End with the standard `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>` footer.

---

### Task 2: Switch the 3 call sites to `ensureFresh()`

**Files:**
- Modify: `src/app/shared/ui/user-info-modal/user-info-modal.component.ts:719-724`
- Modify: `src/app/features/room/data/ghost-audience.util.ts:45-52`
- Modify: `src/app/features/room/feature/moderation/user-action-modal.ts:796-801`

**Interfaces:**
- Consumes: `ensureFresh(userId: number): void` from Task 1.

- [ ] **Step 1: Replace `user-info-modal` constructor guard.**

In `src/app/shared/ui/user-info-modal/user-info-modal.component.ts`, replace the constructor body (lines 719-724):
```ts
  constructor() {
    const uid = this.data.userId;
    if (uid > 0 && (!this.userInfoService.getUserInfo(uid) || this.userInfoService.isStale(uid))) {
      void this.userInfoService.fetchUserInfo(uid);
    }
  }
```
with:
```ts
  constructor() {
    this.userInfoService.ensureFresh(this.data.userId);
  }
```

- [ ] **Step 2: Replace `ghost-audience.util.ts` loop.**

In `src/app/features/room/data/ghost-audience.util.ts`, replace lines 45-52:
```ts
export function fetchMissingGhostInfo(inputs: GhostAudienceInputs, userInfoService: UserInfoService): void {
  const uids = ghostRemoteUsers(inputs).map((u) => u.uid);
  if (!inputs.isVisible) uids.push(inputs.selfUserId);

  for (const uid of uids) {
    if (uid > 0 && (!userInfoService.getUserInfo(uid) || userInfoService.isStale(uid))) void userInfoService.fetchUserInfo(uid);
  }
}
```
with:
```ts
export function fetchMissingGhostInfo(inputs: GhostAudienceInputs, userInfoService: UserInfoService): void {
  const uids = ghostRemoteUsers(inputs).map((u) => u.uid);
  if (!inputs.isVisible) uids.push(inputs.selfUserId);

  for (const uid of uids) userInfoService.ensureFresh(uid);
}
```

Note: the outer `if (uid > 0)` wrapper is dropped because `ensureFresh` itself rejects non-positive uids.

- [ ] **Step 3: Replace `user-action-modal` constructor guard.**

In `src/app/features/room/feature/moderation/user-action-modal.ts`, replace the constructor body (lines 796-801):
```ts
  constructor() {
    const uid = this.data.userId;
    if (uid && (!this.userInfoService.getUserInfo(uid) || this.userInfoService.isStale(uid))) {
      void this.userInfoService.fetchUserInfo(uid);
    }
  }
```
with:
```ts
  constructor() {
    this.userInfoService.ensureFresh(this.data.userId ?? 0);
  }
```

Note: `UserActionModalData.userId` is optional (`userId?: number`), unlike the other two call sites where it's required. The `?? 0` preserves the existing "skip when userId is missing" behavior — `ensureFresh(0)` is a no-op via its own `userId > 0` guard.

- [ ] **Step 4: Verify zero remaining inline guards.**

Run:
```
grep -rn "getUserInfo.*||.*isStale\|isStale.*||.*getUserInfo" src/app
```
Expected: no output. (The only remaining matches should be the implementation inside `user-info.service.ts`, which the negative-match pattern should not catch — confirm by also running `grep -rn "isStale" src/app` and verifying the only hit is the `isStale()` method definition plus its call inside `ensureFresh()`.)

- [ ] **Step 5: Verify TypeScript compiles.**

Run: `npx tsc --noEmit -p tsconfig.json`
Expected: zero errors.

- [ ] **Step 6: Manual smoke check (per spec §5).**

Verify the behavior is unchanged: open a room, click an audience member (opens `user-info-modal`), confirm the modal still calls `/users/info` when the cached entry is stale/missing, and skips it when fresh (Network tab). Repeat with a member click as host/moderator (opens `user-action-modal`) — same expectation. If `/verify` or `/run` skills are configured, use them; otherwise describe what you would observe.

- [ ] **Step 7: Commit.**

```
git add src/app/shared/ui/user-info-modal/user-info-modal.component.ts \
        src/app/features/room/data/ghost-audience.util.ts \
        src/app/features/room/feature/moderation/user-action-modal.ts
git commit -m "refactor: route 3 staleness-guard call sites through ensureFresh()"
```

Body:
```
Replaces 3 duplicated `if (!getUserInfo(uid) || isStale(uid))`
guards with a single ensureFresh() call on UserInfoService.
Behavior is unchanged — verified manually that both modals still
fetch on stale/missing and skip on fresh.

ghost-audience.util.ts drops its outer `uid > 0` check because
ensureFresh itself rejects non-positive uids.
user-action-modal.ts keeps the `?? 0` default because its
UserActionModalData.userId is optional.
```

End with `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`.

---

### Task 3: Remove dead `judgeManager()` and `ManagerJudgeResponse` frontend bindings

**Files:**
- Modify: `src/app/features/room/data/room-api.ts:5` (remove the `ManagerJudgeResponse` from the import) and `:161-164` (delete the `judgeManager()` method body)
- Modify: `src/app/features/room/data/room-model.ts:528-530` (delete the `ManagerJudgeResponse` interface — only after confirming no remaining references)

**Interfaces:** none produced; pure deletion.

- [ ] **Step 1: Verify zero current call sites for `judgeManager` and `ManagerJudgeResponse`.**

Run:
```
grep -rn "judgeManager\|ManagerJudgeResponse" src/app
```
Expected before deletion: hits only at the declaration sites — `room-api.ts:5` (import) and `:161-164` (method), and `room-model.ts:528-530` (interface). If any other file matches, STOP and surface that to the controller before proceeding (this round's spec assumed the binding was dead; if it's not, the deletion is wrong).

- [ ] **Step 2: Delete `judgeManager()` method from `room-api.ts`.**

In `src/app/features/room/data/room-api.ts`, delete the entire method block at lines 161-164:
```ts
  judgeManager(cname: string, hostId: number): Observable<ManagerJudgeResponse> {
    const params = new HttpParams().set('cname', cname).set('host_id', hostId);
    return this.http.get<ManagerJudgeResponse>(`${this.baseUrl}/managers/judge`, { params });
  }

```
(Include the trailing blank line that separated it from `listManagers()`.)

Then remove `ManagerJudgeResponse` from the import on line 5, which currently reads:
```ts
import { StageUsersResponse, AudienceUsersResponse, AudienceUser, CommentsResponse, SendCommentPayload, VoiceSignPanelResponse, RoomLevelRewardResponse, RoomLevelConfigResponse, VoiceRoomInfo, LiveRoomInfo, ManagerListResponse, ManagerJudgeResponse, CaptionHistoryResponse, VoiceTasksResponse } from './room-model';
```
becoming:
```ts
import { StageUsersResponse, AudienceUsersResponse, AudienceUser, CommentsResponse, SendCommentPayload, VoiceSignPanelResponse, RoomLevelRewardResponse, RoomLevelConfigResponse, VoiceRoomInfo, LiveRoomInfo, ManagerListResponse, CaptionHistoryResponse, VoiceTasksResponse } from './room-model';
```

- [ ] **Step 3: Delete the `ManagerJudgeResponse` interface from `room-model.ts`.**

In `src/app/features/room/data/room-model.ts`, delete the interface at lines 528-530:
```ts
export interface ManagerJudgeResponse {
  readonly isOnline: boolean;
}
```
(Include the blank line preceding it if it's a separator. Verify the adjacent interfaces still align and there's no orphaned blank line left behind.)

- [ ] **Step 4: Verify zero remaining references.**

Run:
```
grep -rn "judgeManager\|ManagerJudgeResponse" src/app
```
Expected: no output.

Also run: `npx tsc --noEmit -p tsconfig.json`
Expected: zero errors. (If errors mention `judgeManager` or `ManagerJudgeResponse`, Step 1's verification was wrong — STOP and surface the conflict.)

- [ ] **Step 5: Commit.**

```
git add src/app/features/room/data/room-api.ts \
        src/app/features/room/data/room-model.ts
git commit -m "refactor: remove dead frontend binding for managers/judge endpoint"
```

Body:
```
RoomApi.judgeManager() and the ManagerJudgeResponse interface had
zero call sites anywhere in the frontend — verified via grep before
deletion. The backend endpoint at GET /api/managers/judge stays in
place; deleting a working backend endpoint on the strength of "the
current frontend doesn't call it" is a bigger, separate decision
(out of scope for this dead-code cleanup round).
```

End with `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`.

---

### Task 4: Create `UserIdentityCardComponent` shared component

**Files:**
- Create: `src/app/shared/ui/user-identity-card/user-identity-card.component.ts`

**Interfaces:**
- Consumes: nothing (dumb component).
- Produces: a new selector `<app-user-identity-card>` with inputs `avatarUrl`, `initials`, `displayName` (all `required<string>`), `username`, `signature`, `ringColor`, `crownType` (all `string | null`, default `null`), and two projection slots: `[nameBadge]` (optional inline badge after the display name) and `[metaChips]` (chips in the meta-row). Task 5 and Task 6 consume it.

- [ ] **Step 1: Read the two source modals' identity-card CSS to confirm what's duplicated.**

Run:
```
grep -n "identity-card\|profile-row\|identity-main\|profile-info\|name-row\|user-name\|user-handle\|meta-row\|\\.bio" \
  src/app/shared/ui/user-info-modal/user-info-modal.component.ts \
  src/app/features/room/feature/moderation/user-action-modal.ts | head -60
```
Expected: identical/near-identical CSS rule names in both files for `.identity-card` / `.profile-row` (the wrapper), `.identity-main` / `.profile-info` (the right-hand text block), `.name-row`, `.user-name`, `.user-handle`, `.meta-row`, `.bio`. The shared component will own the consolidated styles, picking `user-info-modal`'s naming (`.identity-card`, `.identity-main`) as canonical since that file's doc comment marks the read-only case as the reference.

- [ ] **Step 2: Create the component file.**

Create `src/app/shared/ui/user-identity-card/user-identity-card.component.ts` with the following contents (the CSS below consolidates both modals' shared rules verbatim — the wrapper uses `display: flex; gap: var(--space-3)` from `.profile-row`, the right-hand block uses `display: flex; flex-direction: column; gap: var(--space-1)` from `.profile-info`, and the chip/bio rules are the same in both):

```ts
import { Component, ChangeDetectionStrategy, input } from '@angular/core';
import { AvatarComponent } from '@shared/ui/avatar/avatar.component';

/**
 * Shared identity-card header used by `user-info-modal` and `user-action-modal`.
 * Owns the avatar + name + handle + signature layout shell; consumers project their
 * own differing chips via the `[metaChips]` slot (and an optional `[nameBadge]`
 * for inline badges like sex).
 *
 * Dumb component: no service injection, no store access. Inputs in, two
 * projection slots out. See CLAUDE.md §6.
 */
@Component({
  selector: 'app-user-identity-card',
  imports: [AvatarComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="identity-card">
      <app-avatar
        [src]="avatarUrl()"
        [initials]="initials()"
        size="xl"
        [alt]="displayName()"
        [ringColor]="ringColor()"
        [crownType]="crownType()"
      />
      <div class="identity-main">
        <div class="name-row">
          <span class="user-name">{{ displayName() }}</span>
          <ng-content select="[nameBadge]" />
        </div>
        @if (username()) {
          <span class="user-handle">&#64;{{ username() }}</span>
        }
        <div class="meta-row">
          <ng-content select="[metaChips]" />
        </div>
      </div>
    </div>
    @if (signature()) {
      <p class="bio">{{ signature() }}</p>
    }
  `,
  styles: [`
    :host { display: block; }

    .identity-card {
      display: flex;
      align-items: center;
      gap: var(--space-3);
      padding: var(--space-4);
    }

    .identity-main {
      flex: 1;
      min-width: 0;
      display: flex;
      flex-direction: column;
      gap: var(--space-1);
    }

    .name-row {
      display: flex;
      align-items: center;
      gap: var(--space-2);
    }

    .user-name {
      font-size: var(--text-lg);
      font-weight: var(--font-semibold);
      color: var(--color-text);
    }

    .user-handle {
      font-size: var(--text-sm);
      color: var(--color-text-muted);
    }

    .meta-row {
      display: flex;
      align-items: center;
      gap: var(--space-2);
      flex-wrap: wrap;
    }

    .bio {
      padding: 0 var(--space-4) var(--space-3);
      margin: 0;
      font-size: var(--text-sm);
      color: var(--color-text-muted);
      line-height: 1.4;
    }
  `],
})
export class UserIdentityCardComponent {
  readonly avatarUrl = input.required<string>();
  readonly initials = input.required<string>();
  readonly displayName = input.required<string>();
  readonly username = input<string | null>(null);
  readonly signature = input<string | null>(null);
  readonly ringColor = input<string | null>(null);
  readonly crownType = input<string | null>(null);
}
```

Note: the shared component does not own `.sex-badge`, `.chip-*`, `.chip-live`, `.chip-streak`, `.onlineChipClass`, `.roleChipClass`, etc. — those stay in the consuming modals' styles because they're chip-specific, not identity-card-shell-specific. The shared component owns only the layout shell.

Verify: `cat src/app/shared/ui/user-identity-card/user-identity-card.component.ts | head -3` should show the imports.

- [ ] **Step 3: Verify TypeScript compiles.**

Run: `npx tsc --noEmit -p tsconfig.json`
Expected: zero errors. (No existing file imports this new component yet, so the only check is that the file itself parses.)

- [ ] **Step 4: Commit.**

```
git add src/app/shared/ui/user-identity-card/user-identity-card.component.ts
git commit -m "feat(shared): add UserIdentityCardComponent for modal header dedup"
```

Body:
```
Extracts the duplicated avatar + name + handle + signature layout
shell from user-info-modal.component.ts and user-action-modal.ts
into one dumb shared component. Two projection slots ([nameBadge]
and [metaChips]) absorb the only divergence between the two modals
(sex-badge vs. role-chip, plus the differing chip sets).

Consuming modals will swap their hand-rolled markup for
<app-user-identity-card> in the next two tasks.
```

End with `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`.

---

### Task 5: Switch `user-info-modal` to `<app-user-identity-card>`

**Files:**
- Modify: `src/app/shared/ui/user-info-modal/user-info-modal.component.ts` — imports (add `UserIdentityCardComponent`), template (replace identity-card block at lines 48-91 plus the `signature()` block at lines 115-117 with one `<app-user-identity-card>` invocation), styles (delete the now-unused `.identity-card`, `.identity-main`, `.name-row`, `.user-name`, `.user-handle`, `.meta-row`, `.bio` rules; keep `.sex-badge*` and `.chip*` rules since the chips still render inside the projection).

**Interfaces:**
- Consumes: `<app-user-identity-card>` from Task 4.

- [ ] **Step 1: Add the import.**

In `src/app/shared/ui/user-info-modal/user-info-modal.component.ts`, in the `imports:` array of `@Component({...})` (currently around lines 28-40), add `UserIdentityCardComponent` to the list. Also add the corresponding top-level `import { UserIdentityCardComponent } from '@shared/ui/user-identity-card/user-identity-card.component';` near the other `@shared/ui/...` imports (currently around lines 6-13).

- [ ] **Step 2: Replace the template's identity-card markup.**

In the same file, replace lines 48-91 (the `<div class="identity-card">...identity-main...</div>` block):

```html
      <div class="identity-card" [class.identity-card--vip]="vipType() === 100">
        <app-avatar
          [src]="avatarUrl()"
          [initials]="initials()"
          size="xl"
          [alt]="displayName()"
          [status]="onlineStatus() === 'Online' ? 'online' : null"
          [ringColor]="vipType() === 100 ? 'var(--color-gold-300)' : 'var(--color-primary-300)'"
        />

        <div class="identity-main">
          <div class="name-row">
            <span class="user-name" id="user-info-title">{{ displayName() }}</span>
            @if (sex() === 'male') {
              <span class="sex-badge sex-male">
                <svg xmlns="http://www.w3.org/2000/svg" width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="10.5" cy="14.5" r="5.5"/><path d="M19.5 8 12 15.5M19.5 8l-5.5 0"/></svg>
              </span>
            } @else if (sex() === 'female') {
              <span class="sex-badge sex-female">
                <svg xmlns="http://www.w3.org/2000/svg" width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="14.5 8" r="5.5"/><path d="M14.5 8 12 5.5M14.5 8h-5M12 5.5v8"/></svg>
              </span>
            }
          </div>
          @if (username()) {
            <span class="user-handle">&#64;{{ username() }}</span>
          }
          <div class="meta-row">
            @if (vipType() === 100) {
              <span class="chip chip-gold"><svg aria-hidden="true" lucideCrown [size]="9"></svg>VIP</span>
            } @else if (vipType() > 0 && vipType() < 100) {
              <span class="chip chip-primary"><svg aria-hidden="true" lucideCrown [size]="9"></svg>VIP</span>
            }
            @if (onlineStatus(); as status) {
              <span class="chip" [class]="onlineChipClass()">{{ status }}</span>
            }
            @if (liveStatus()) {
              <span class="chip chip-live">LIVE</span>
            }
            @if (streakDays(); as streak) {
              <span class="chip chip-streak">{{ streak }}-day streak</span>
            }
          </div>
        </div>
      </div>
```

with:

```html
      <app-user-identity-card
        [avatarUrl]="avatarUrl()"
        [initials]="initials()"
        [displayName]="displayName()"
        [username]="username()"
        [signature]="signature()"
        [ringColor]="vipType() === 100 ? 'var(--color-gold-300)' : 'var(--color-primary-300)'"
      >
        @if (sex() === 'male') {
          <span nameBadge class="sex-badge sex-male">
            <svg xmlns="http://www.w3.org/2000/svg" width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="10.5" cy="14.5" r="5.5"/><path d="M19.5 8 12 15.5M19.5 8l-5.5 0"/></svg>
          </span>
        } @else if (sex() === 'female') {
          <span nameBadge class="sex-badge sex-female">
            <svg xmlns="http://www.w3.org/2000/svg" width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="14.5 8" r="5.5"/><path d="M14.5 8 12 5.5M14.5 8h-5M12 5.5v8"/></svg>
          </span>
        }
        <ng-container metaChips>
          @if (vipType() === 100) {
            <span class="chip chip-gold"><svg aria-hidden="true" lucideCrown [size]="9"></svg>VIP</span>
          } @else if (vipType() > 0 && vipType() < 100) {
            <span class="chip chip-primary"><svg aria-hidden="true" lucideCrown [size]="9"></svg>VIP</span>
          }
          @if (onlineStatus(); as status) {
            <span class="chip" [class]="onlineChipClass()">{{ status }}</span>
          }
          @if (liveStatus()) {
            <span class="chip chip-live">LIVE</span>
          }
          @if (streakDays(); as streak) {
            <span class="chip chip-streak">{{ streak }}-day streak</span>
          }
        </ng-container>
      </app-user-identity-card>
```

Note 1: the `id="user-info-title"` on the inner `<span class="user-name">` is dropped because it's now inside the projected child component's template — if any external code referenced that exact ID, surface it to the controller before proceeding. (The spec text didn't call this out; this is a known minor a11y concern, not a blocker. Verify with `grep -rn 'user-info-title' src/app` before deleting — expected: only the one site.)

Note 2: the existing `[status]="onlineStatus() === 'Online' ? 'online' : null"` on `<app-avatar>` is dropped because the shared `<app-user-identity-card>` doesn't expose `status`. The green online dot will not appear on the avatar when this modal switches to the shared card. This is a small visual regression — surface it to the controller before proceeding if it's unacceptable; otherwise document it as accepted in the PR description.

Note 3: the existing `signature()` block at lines 115-117 (`<p class="bio">{{ signature() }}</p>`) is now redundant — `<app-user-identity-card>` renders its own bio when `signature()` is truthy. **Delete** that block from this file. Verify with `grep -n "signature()" src/app/shared/ui/user-info-modal/user-info-modal.component.ts` — the signature input signal itself stays (still needed for the `[signature]` binding); only the in-template render is removed.

- [ ] **Step 3: Clean up the now-unused CSS.**

In the `styles: [\`...\`] array, delete these rules (they were on the hand-rolled identity-card block, now owned by the shared component):

- `.identity-card` (entire rule)
- `.identity-card--vip` (modifier, if it was a distinct rule — check the file; if there isn't one, skip)
- `.identity-main` (entire rule)
- `.name-row` (entire rule)
- `.user-name` (entire rule)
- `.user-handle` (entire rule)
- `.meta-row` (entire rule)
- `.bio` (entire rule — this used to style the now-removed in-template signature block; the shared component's `.bio` rule provides equivalent styling)

Keep: `.sex-badge`, `.sex-male`, `.sex-female`, `.chip*` (these now style chips rendered inside the projection, not the shell itself).

Run: `npx tsc --noEmit -p tsconfig.json`
Expected: zero errors.

Run: `grep -n "identity-card\|identity-main\|user-name\|user-handle" src/app/shared/ui/user-info-modal/user-info-modal.component.ts`
Expected: no matches (or only matches inside the new `<app-user-identity-card>` template's data-binding expressions, NOT CSS rules). If a stray CSS rule remains, delete it before committing.

- [ ] **Step 4: Manual smoke check.**

Run the app (or `/verify` / `/run` if configured). Open a room. Click an audience member who isn't a moderator (opens `user-info-modal`). Verify the identity card renders identically to before:
- avatar visible (XL size, ring color gold for VIP=100, primary otherwise)
- display name + sex badge (if sex known)
- @username handle below the name (if known)
- VIP/online/live/streak chips in the meta row
- bio below the card (if signature known)

Verify the `/users/info` fetch behavior is unchanged (still fires when stale/missing, skipped when fresh — Task 2 covered this; this task is purely visual extraction).

- [ ] **Step 5: Commit.**

```
git add src/app/shared/ui/user-info-modal/user-info-modal.component.ts
git commit -m "refactor(user-info-modal): use shared UserIdentityCardComponent"
```

Body:
```
Replaces the hand-rolled identity-card markup with
<app-user-identity-card>. The two projection slots ([nameBadge],
[metaChips]) absorb the modal's differing elements (sex badge,
VIP/online/live/streak chips). Local CSS for the layout shell is
removed; chip-specific CSS stays.

Caveat: the [status]="online..." prop on <app-avatar> is dropped —
the shared component does not currently expose an avatar-status
input, so the green online dot will not render through this modal
until that's added. Acceptable for this round; flagged in the PR
description.
```

End with `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`.

---

### Task 6: Switch `user-action-modal` to `<app-user-identity-card>`

**Files:**
- Modify: `src/app/features/room/feature/moderation/user-action-modal.ts` — imports (add `UserIdentityCardComponent`), template (replace `.modal-header` lines 47-82 with one `<app-user-identity-card>` invocation), styles (delete now-unused `.modal-header`, `.profile-row`, `.profile-info`, `.name-row`, `.user-name`, `.user-handle`, `.meta-row`, `.bio`, `.close-btn` rules — wait, `.close-btn` stays, it sits inside the header which is now gone; review whether it's still needed).

**Interfaces:**
- Consumes: `<app-user-identity-card>` from Task 4.

- [ ] **Step 1: Read the current `.modal-header` block and surrounding CSS to confirm what's duplicated.**

Run:
```
sed -n '45,85p' src/app/features/room/feature/moderation/user-action-modal.ts
grep -n "\\.modal-header\|\\.profile-row\|\\.profile-info\|\\.name-row\|\\.user-name\|\\.user-handle\|\\.meta-row\|\\.bio\|\\.close-btn" \
  src/app/features/room/feature/moderation/user-action-modal.ts | head -40
```
Expected: lines 45-82 contain the `<div class="modal-header">...<div class="profile-row">...</div>...signature...</div>` block. The grep should show a mix of CSS rules for these classes, plus a `.close-btn` rule that may need careful handling (the close button currently lives inside `.modal-header`; if `.modal-header` is removed, `.close-btn` needs to either move into the projection or be repositioned above the new `<app-user-identity-card>`).

- [ ] **Step 2: Add the import and switch the imports array.**

In `src/app/features/room/feature/moderation/user-action-modal.ts`:
- Add `import { UserIdentityCardComponent } from '@shared/ui/user-identity-card/user-identity-card.component';` near the other `@shared/ui/...` imports.
- Add `UserIdentityCardComponent` to the `imports: [...]` array of `@Component({...})`.

- [ ] **Step 3: Replace the template's modal-header block.**

Replace lines 45-82:
```html
      <div class="modal-header">
        <button type="button" class="close-btn" (click)="ref.close()" aria-label="Close">
          <svg aria-hidden="true" lucideX [size]="14"></svg>
        </button>

        <div class="profile-row">
          <app-avatar
            [src]="avatarUrl()"
            [initials]="initials()"
            size="xl"
            [alt]="displayName()"
            [ringColor]="ringColor()"
            [crownType]="crownType()"
          />
          <div class="profile-info">
            <div class="name-row">
              <span class="user-name" id="user-action-title">{{ displayName() }}</span>
              @if (username()) {
                <span class="user-handle">&#64;{{ username() }}</span>
              }
            </div>
            <div class="meta-row">
              @if (vipType() === 100) {
                <span class="chip chip-gold"><svg aria-hidden="true" lucideCrown [size]="9"></svg>VIP</span>
              } @else if (vipType() > 0 && vipType() < 100) {
                <span class="chip chip-primary"><svg aria-hidden="true" lucideCrown [size]="9"></svg>VIP</span>
              }
              <span class="chip" [class]="roleChipClass()">{{ roleLabel() }}</span>
            </div>
          </div>
        </div>

        @if (signature()) {
          <p class="bio">{{ signature() }}</p>
        }
      </div>
```

with:

```html
      <button type="button" class="close-btn" (click)="ref.close()" aria-label="Close">
        <svg aria-hidden="true" lucideX [size]="14"></svg>
      </button>

      <app-user-identity-card
        [avatarUrl]="avatarUrl()"
        [initials]="initials()"
        [displayName]="displayName()"
        [username]="username()"
        [signature]="signature()"
        [ringColor]="ringColor()"
        [crownType]="crownType()"
      >
        <ng-container metaChips>
          @if (vipType() === 100) {
            <span class="chip chip-gold"><svg aria-hidden="true" lucideCrown [size]="9"></svg>VIP</span>
          } @else if (vipType() > 0 && vipType() < 100) {
            <span class="chip chip-primary"><svg aria-hidden="true" lucideCrown [size]="9"></svg>VIP</span>
          }
          <span class="chip" [class]="roleChipClass()">{{ roleLabel() }}</span>
        </ng-container>
      </app-user-identity-card>
```

Note 1: the close button moves out of the header wrapper and sits above the new identity card. It is no longer positioned relative to `.modal-header` — verify visually that the close button is still visible and positioned correctly. If positioning breaks (e.g., the button was absolute-positioned inside `.modal-header`), the close-btn CSS rule may need its positioning values updated to absolute coordinates inside `<app-modal>` instead.

Note 2: the `id="user-action-title"` on `<span class="user-name">` is dropped for the same reason as Task 5's `user-info-title`. Verify with `grep -rn 'user-action-title' src/app` — expected: only this one site.

Note 3: this modal has no `[nameBadge]` slot content (no equivalent of the sex badge); the `<ng-content select="[nameBadge]" />` inside `<app-user-identity-card>` simply has nothing to render, which is correct.

- [ ] **Step 4: Clean up the now-unused CSS.**

In the `styles: [\`...\`] array, delete:
- `.modal-header` (entire rule)
- `.profile-row` (entire rule)
- `.profile-info` (entire rule)
- `.name-row` (entire rule)
- `.user-name` (entire rule)
- `.user-handle` (entire rule)
- `.meta-row` (entire rule)
- `.bio` (entire rule — the shared component owns this now)

Keep: `.close-btn` (still used; verify positioning still works), `.chip*`, `.roleChipClass`-related rules.

Run: `npx tsc --noEmit -p tsconfig.json`
Expected: zero errors.

Run: `grep -n "modal-header\|profile-row\|profile-info" src/app/features/room/feature/moderation/user-action-modal.ts`
Expected: no matches (or only matches in unrelated text/comments, which there shouldn't be). If a stray rule remains, delete it before committing.

- [ ] **Step 5: Manual smoke check.**

Run the app. Open a room as host/moderator. Click a member (opens `user-action-modal`). Verify:
- avatar visible (XL size, ring color via `ringColor()`/`crownType()`)
- display name + role chip + VIP chip (if applicable)
- @username handle (if known)
- bio (if signature known)
- mod action menu below renders unchanged (this is the part that makes this modal different from `user-info-modal` — that menu must not regress)
- close button still functions

- [ ] **Step 6: Commit.**

```
git add src/app/features/room/feature/moderation/user-action-modal.ts
git commit -m "refactor(user-action-modal): use shared UserIdentityCardComponent"
```

Body:
```
Replaces the hand-rolled .modal-header markup with
<app-user-identity-card>. VIP and role chips project into [metaChips];
no [nameBadge] content (no equivalent of the user-info-modal's sex
badge). Modal-action menu and close button below are unaffected.

Caveat: close button moved out of the .modal-header wrapper to sit
above the new identity card. Visual positioning verified manually.
If close-btn was relying on absolute positioning relative to
.modal-header, that absolute positioning is now relative to
<app-modal> instead — flag if it shifts visually in production.
```

End with `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`.

---

### Task 7: Final whole-branch review

**Files:** none modified — read-only verification across the whole branch.

- [ ] **Step 1: Confirm the worktree is clean and the diff is exactly the expected files.**

Run:
```
git status
git diff --stat master..HEAD
```
Expected: 6 files modified across the 5 implementation tasks (Tasks 1-6), plus nothing else. No drive-by changes. `git status` reports "nothing to commit, working tree clean".

- [ ] **Step 2: Verify the new public surface matches the spec.**

Run:
```
grep -n "ensureFresh" src/app/core/services/user-info.service.ts
grep -n "UserIdentityCardComponent" src/app/shared/ui/user-identity-card/user-identity-card.component.ts
grep -rn "judgeManager\|ManagerJudgeResponse" src/app
grep -rn "isStale.*getUserInfo\|getUserInfo.*isStale" src/app
```
Expected:
- `ensureFresh` appears once (the new method)
- `UserIdentityCardComponent` appears at least once (the class declaration)
- `judgeManager` and `ManagerJudgeResponse` both return no matches (dead code gone)
- the duplicate guard pattern returns no matches (only matches should be the implementation inside `ensureFresh()` itself, which the negative-OR pattern shouldn't catch — verify with a follow-up `grep -rn "isStale" src/app` if needed)

- [ ] **Step 3: Verify both modals consume the shared component.**

Run:
```
grep -n "<app-user-identity-card" \
  src/app/shared/ui/user-info-modal/user-info-modal.component.ts \
  src/app/features/room/feature/moderation/user-action-modal.ts
```
Expected: one hit per file.

- [ ] **Step 4: Run the full TypeScript check.**

Run: `npx tsc --noEmit -p tsconfig.json`
Expected: zero errors.

- [ ] **Step 5: Report.**

Report the final commit list (7 commits expected: one per Task 1-6, plus the pre-existing plan/spec commits from the brainstorming phase — confirm via `git log --oneline master..HEAD`) and the file-change summary. If any verification step above failed, STOP and surface the gap to the controller before declaring this round complete.

---

## Self-Review Notes (already applied — kept here for transparency)

- **Spec coverage:** every requirement in `2026-07-04-bff-consolidation-round-3-design.md` maps to a task: §3.1 (ensureFresh) → Task 1+2; §3.2 (dead-code removal) → Task 3; §3.3 (UserIdentityCardComponent) → Tasks 4-6. Final verification → Task 7. No gaps.
- **No placeholders:** every code step shows full code, every command shows the expected output.
- **Type consistency:** `ensureFresh(userId: number): void` is used identically in Tasks 1, 2. `<app-user-identity-card>` inputs (`avatarUrl`, `initials`, `displayName`, `username`, `signature`, `ringColor`, `crownType`) are listed in Task 4 and consumed identically in Tasks 5 and 6. The `metaChips`/`nameBadge` projection slot names are consistent across all three tasks.
- **Known accepted losses documented in tasks:** the dropped `[status]="online..."` avatar prop (Task 5) and the dropped `user-info-title`/`user-action-title` IDs (Tasks 5, 6) are flagged inline so the implementer doesn't treat them as bugs.