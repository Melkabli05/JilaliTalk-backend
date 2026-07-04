# BFF Consolidation, Round 3 — Frontend Logic Dedup (No BFF Changes)

**Date:** 2026-07-04
**Scope:** `JilaliTalk-angular-frontend` only (`core/services/user-info.service.ts`, `shared/ui/user-info-modal/`, `features/room/feature/moderation/`, `features/room/data/ghost-audience.util.ts`). No `jilalibff` changes this round.

## 0. Context

Round 1 (`join-bundle`, `enrich-batch`, `audience-reconcile`, server-computed fields) and Round 2 (rooms search, shared `CategoriesService`, parallel `makeVisible()`, `UserInfoService` TTL) already captured the app's genuine multi-call and sequential-waterfall problems. A fresh audit for Round 3, starting from the same "what's redundant in the frontend that could move server-side or get deduplicated" brief, surfaced three candidates — but two were rejected on inspection (documented in §3 for the record, since ruling something out is as valuable as finding something real), and one genuine, small, verified duplication survived along with two related cleanup items.

**Explicitly rejected this round (verification, not speculation):**

- **A sign-in panel bundle** (fanning `signPanel` + `voiceTasks` + `room-level-bundle` into one call). `signin-store.ts` already gates `rewardsRef`/`tasksRef` behind `_rewardsTabActive`/`_tasksTabActive` signals — they only fire once the user clicks those tabs. An eager bundle would force every user who opens the sign-in panel but never clicks "rewards" or "tasks" to pay for two upstream calls they don't need. This is the same regression class Round 2 (§3.1) rejected for rooms-list/recommended bundling: bundling something that's already correctly lazy is a request-count loss dressed as a win.
- **A managers bundle** (fanning `managers/judge` + `managers/list`). Verified via grep: `RoomApi.judgeManager()` / `ManagerJudgeResponse` exist and are backed by a real BFF endpoint (`GET /api/managers/judge`), but nothing in the frontend calls `judgeManager()`. `isHost`/`isModerator` are derived from `RoomStore`'s `_myRole`, itself set from the room-join-bundle's `reqUserInfo.role` (`room-page.ts:274`). There is no multi-call problem here to bundle — `judge` is dead code, not an underused endpoint.

## 1. Problem

Three findings, all frontend-only, all in the room/user-info area:

1. **`UserInfoService`'s staleness guard is duplicated verbatim in three call sites.** `user-info-modal.component.ts:721`, `ghost-audience.util.ts:50`, and `user-action-modal.ts:798` each independently write:
   ```ts
   if (!getUserInfo(uid) || isStale(uid)) void fetchUserInfo(uid);
   ```
   This is real logic duplication, not just repeated boilerplate — the *decision* of "when does this uid need a re-fetch" is defined three times. If Round 2's staleness policy (5-minute TTL) ever changes shape (e.g., needs a per-field TTL, or a backoff on repeated failures), three call sites need to change in lockstep, and any future call site that forgets the `isStale` half of the guard silently reverts to "fetch once per session" — the exact bug Round 2 fixed.

2. **`ManagerJudgeResponse` / `RoomApi.judgeManager()` are dead code.** Verified: `grep -rn "judgeManager\|ManagerJudge" src/app` matches only the declaration site (`room-api.ts:161-164`) and the model (`room-model.ts:528`) — no call site anywhere in the frontend. The backend endpoint (`GET /api/managers/judge`) is real and presumably was meant to back an "am I a manager" check, but that check is already satisfied by `RoomStore.isHost`/`isModerator`. Confirmed dead, not just under-called.

3. **The identity-card header is duplicated between `user-info-modal.component.ts` and `user-action-modal.ts`.** Both render: an `app-avatar` (size `xl`, ring color driven by VIP/role), a name row (display name + optional inline badge), an optional `@username` handle, a meta-row of chips, and an optional bio/signature line. The two modals are intentionally different beyond that header — `user-info-modal.component.ts`'s own doc comment says "for moderation actions, see the room feature's `UserActionModalComponent`" — so this is a documented, deliberate split, not an accident. But the header markup itself (the avatar + name + handle + bio block) is copy-pasted, meaning a visual tweak to that header (say, adding a new badge type) must be made twice and can drift.

## 2. Approach

All three are small, independent, frontend-only fixes. No new architecture, no new BFF endpoints, no new stores.

- **#1 (staleness guard):** add one method, `UserInfoService.ensureFresh(userId: number): void`, that inlines the existing guard and calls `fetchUserInfo` (fire-and-forget, matching the current call sites' fire-and-forget usage — none of them await the result, they rely on the cache signal to update reactively). All three call sites become `this.userInfoService.ensureFresh(uid)`.
- **#2 (dead code):** remove `judgeManager()` from `RoomApi`, `ManagerJudgeResponse` from `room-model.ts`, and (backend, still no controller/route change needed — the endpoint itself is harmless to leave, since it's real backend functionality that could serve a future caller) leave `ManagerController.judge()` and `ManagerJudgeResponse.java` in place server-side. This round only removes the *unused frontend binding* — deleting the backend endpoint is a separate, larger decision (does anything else, e.g. a future admin surface, want it?) that's out of scope for a dead-code cleanup pass.
- **#3 (identity-card header):** extract a new dumb component, `shared/ui/user-identity-card/user-identity-card.component.ts`, taking `avatarUrl`, `initials`, `displayName`, `username`, `signature`, `ringColor`, and `crownType` as inputs, with one `<ng-content select="[metaChips]">` projection slot for the differing badge sets (`user-info-modal` projects online/live/streak chips; `user-action-modal` projects its role chip). Both modals replace their duplicated header markup with `<app-user-identity-card>` and keep everything below it (the stats bar, info cards, action menu) exactly as-is.

## 3. Design

### 3.1 `UserInfoService.ensureFresh()`

New method on the existing service (`core/services/user-info.service.ts`), placed next to `fetchUserInfo`:

```ts
/**
 * Triggers a re-fetch only if the cached entry is missing or stale (see isStale).
 * Fire-and-forget — callers read the result reactively via getUserInfo(), matching
 * how fetchUserInfo() is already used at every existing call site.
 */
ensureFresh(userId: number): void {
  if (!(userId > 0)) return;
  if (!this.getUserInfo(userId) || this.isStale(userId)) {
    void this.fetchUserInfo(userId);
  }
}
```

Call sites change from the 1-line guard to a 1-line call:

- `user-info-modal.component.ts:719-724` (constructor body) → `this.userInfoService.ensureFresh(this.data.userId);`
- `ghost-audience.util.ts:50` → `userInfoService.ensureFresh(uid);` (the surrounding `if (uid > 0)` wrapper in the existing code becomes redundant since `ensureFresh` re-checks `userId > 0` itself — drop the outer guard rather than double-guard.)
- `user-action-modal.ts:796-801` (constructor body) → `this.userInfoService.ensureFresh(this.data.userId ?? 0);` (note: `data.userId` is optional here per `UserActionModalData`, unlike the other two call sites where it's required — preserve the existing `uid &&` truthiness check by defaulting to 0, since `ensureFresh(0)` is already a no-op via the `userId > 0` guard).

No change to `isStale`, `getUserInfo`, `fetchUserInfo`, or the cache shape — this is purely extracting an existing inline decision into a named method.

### 3.2 Remove dead `judgeManager()` / `ManagerJudgeResponse`

Frontend-only removal:
- `features/room/data/room-api.ts:161-164` — delete `judgeManager()` method and its unused `ManagerJudgeResponse` import.
- `features/room/data/room-model.ts:528` (approx) — delete the `ManagerJudgeResponse` interface, but only after confirming (implementer must re-grep at execution time, since line numbers drift) that nothing else references it after the `room-api.ts` deletion.

The backend endpoint (`ManagerController.judge()`, `ManagerJudgeResponse.java`) is explicitly **not** touched this round — removing a real, working backend endpoint on the strength of "the current frontend doesn't call it" is a bigger, separate call (could a future admin tool, a different client, or a debugging script want it?) than a dead-frontend-code cleanup. If you want that removed too, it needs its own explicit go-ahead.

### 3.3 Shared `UserIdentityCardComponent`

New file `shared/ui/user-identity-card/user-identity-card.component.ts`:

```ts
import { Component, ChangeDetectionStrategy, input } from '@angular/core';
import { AvatarComponent } from '@shared/ui/avatar/avatar.component';

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
  styles: [/* extracted verbatim from the two modals' shared `.identity-card`/`.profile-row`,
              `.identity-main`/`.profile-info`, `.name-row`, `.user-handle`, `.meta-row`,
              `.bio` rules — implementer reconciles the two modals' near-identical CSS into
              one set of class names, preferring user-info-modal's naming since it's the
              one with the doc-comment marking it canonical for the read-only case */],
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

Two projection slots handle the divergence:
- `[nameBadge]` — `user-info-modal` projects its sex-badge SVG here; `user-action-modal` projects nothing (no equivalent badge).
- `[metaChips]` — `user-info-modal` projects VIP chip + online/live/streak chips; `user-action-modal` projects VIP chip + role chip.

Both modals keep their own `vipType()`, `onlineStatus()`, `roleLabel()`, etc. computed signals — only the layout shell moves, not the data logic.

`user-info-modal.component.ts`'s template changes from its current hand-rolled `.identity-card` block (lines ~48-91) to:

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
    <span nameBadge class="sex-badge sex-male">...</span>
  } @else if (sex() === 'female') {
    <span nameBadge class="sex-badge sex-female">...</span>
  }
  <ng-container metaChips>
    @if (vipType() === 100) { <span class="chip chip-gold">...VIP</span> }
    @else if (vipType() > 0 && vipType() < 100) { <span class="chip chip-primary">...VIP</span> }
    @if (onlineStatus(); as status) { <span class="chip" [class]="onlineChipClass()">{{ status }}</span> }
    @if (liveStatus()) { <span class="chip chip-live">LIVE</span> }
    @if (streakDays(); as streak) { <span class="chip chip-streak">{{ streak }}-day streak</span> }
  </ng-container>
</app-user-identity-card>
```

`user-action-modal.ts`'s template changes analogously, projecting only its VIP chip + role chip into `[metaChips]` and nothing into `[nameBadge]`.

## 4. Files touched

**Frontend only (`JilaliTalk-angular-frontend`):**
- `src/app/core/services/user-info.service.ts` — add `ensureFresh()`
- `src/app/shared/ui/user-info-modal/user-info-modal.component.ts` — use `ensureFresh()`; replace identity-card markup with `<app-user-identity-card>`
- `src/app/features/room/data/ghost-audience.util.ts` — use `ensureFresh()`
- `src/app/features/room/feature/moderation/user-action-modal.ts` — use `ensureFresh()`; replace identity-card markup with `<app-user-identity-card>`
- `src/app/features/room/data/room-api.ts` — delete `judgeManager()`
- `src/app/features/room/data/room-model.ts` — delete `ManagerJudgeResponse` (after confirming no other reference)
- `src/app/shared/ui/user-identity-card/user-identity-card.component.ts` — new

**Backend (`jilalibff`):** none.

## 5. Testing / verification

Matches Round 2's precedent: no new automated unit tests this round (per standing project instruction to skip dedicated unit-test steps). Verification is:

- `npx tsc --noEmit` clean.
- Manual (`/run` or `/verify`): open a room, click an audience member who isn't a moderator (opens `user-info-modal`) — confirm the identity card renders identically to before (avatar, name, VIP badge if applicable, online/live/streak chips, bio). Click a member as a host/moderator (opens `user-action-modal`) — confirm its identity card renders identically (avatar, name, VIP badge, role chip, bio) and the action menu below it is unaffected. Confirm both modals still fetch fresh user info on open when the cached entry is stale or missing (Network tab shows `/users/info` firing), and skip the fetch when the entry is fresh (Network tab shows no request) — this is the same behavior as before, just routed through `ensureFresh()`.
- Grep confirmation that `judgeManager`/`ManagerJudgeResponse` have zero remaining references after removal.

## 6. Out of scope / deferred

- **Sign-in panel bundle** — rejected, see §0. Not deferred-for-later; actively wrong given the current lazy-tab-activation design. Would need to be revisited only if product analytics ever show most users open all three tabs anyway, which would flip the lazy-loading tradeoff.
- **Managers bundle** — rejected, see §0. `judge` is dead code, not an underused endpoint; nothing to bundle.
- **Deleting the backend `judge` endpoint** — deliberately not included; removing frontend dead code is safe, removing a working backend endpoint on the same evidence is a bigger call left to a separate decision.
- **`ProfilePageComponent` still a stub** — noted during this round's audit (the Round-1 profile bundle/store/API exist but have no consumer). This is a feature build, not a consolidation, and stays out of scope here as it did in Round 2.
