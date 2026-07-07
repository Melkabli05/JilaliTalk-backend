# Messages "New Contact" Panel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `+` button to the conversation-list sidebar that opens an overlay panel for picking a contact to start (or continue) a 1:1 conversation with. Four tabs: Following, Followers, Visitors, By ID. Pick → opens conversation via `MessagesStore.select(userId)`.

**Architecture:** One new smart component (`MessageNewContactPanelComponent`, `OnPush`, signals) owns the overlay. The page component just toggles a `panelOpen` signal and wires the `(picked)` output to `store.select(userId)`. Reuses `UserListItemComponent` for rows, calls existing `ProfileApi.followers/following/visitors` plus a small `userInfo()` lookup for the By-ID tab. No backend changes.

**Tech Stack:** Angular 22, signals, `effect`, `@lucide/angular` icons, `HttpClient`, existing `ProfileApi` + `MessagesStore` + `UserListItemComponent`.

## Global Constraints

- No unit tests unless asked (project memory: `feedback_no-unit-tests-unless-asked`). Each task verifies with `npx ng build` (exit 0) and a manual visual sanity-check.
- Strict-mode TypeScript: `tsc --noEmit` exits 0.
- All optional interface fields written as `T | undefined` to satisfy `exactOptionalPropertyTypes: true`.
- Component template-bound members are `protected`, not `private`.
- No new dependencies — pure Angular + existing services.
- Every commit is a clean, reviewable unit; no bundled work.
- Drag the new component out of the conversations feature: it lives under `messages/ui/new-contact-panel/`, exported via `messages/index.ts` (the feature's single public door).

## File Structure

**Created**:
- `JilaliTalk-angular-frontend/src/app/features/messages/ui/new-contact-panel/messages-new-contact-panel.component.ts`
- `JilaliTalk-angular-frontend/src/app/features/messages/ui/new-contact-panel/messages-new-contact-panel.component.html`
- `JilaliTalk-angular-frontend/src/app/features/messages/ui/new-contact-panel/messages-new-contact-panel.component.scss`

**Modified**:
- `JilaliTalk-angular-frontend/src/app/features/messages/index.ts` — export `MessageNewContactPanelComponent`
- `JilaliTalk-angular-frontend/src/app/features/messages/pages/messages-page/messages-page.ts` — add `panelOpen` signal, `onContactPicked`, `onContactPanelClosed`, `toggleContactPanel`
- `JilaliTalk-angular-frontend/src/app/features/messages/pages/messages-page/messages-page.html` — `+` button in sidebar header, `<app-messages-new-contact>` element inside the sidebar
- `JilaliTalk-angular-frontend/src/app/features/messages/pages/messages-page/messages-page.scss` — minor: position the panel inside the sidebar (`position: relative` on the aside)
- `JilaliTalk-angular-frontend/src/app/features/profile/data-access/profile-api.ts` — add `userInfo(userId: number)` method if not already present

**No backend changes.**

## Interfaces (consumed by later tasks)

The new panel exposes (Tasks 2–4 will produce these exact shapes):

```ts
// UserListItemComponent — already exists, reused as-is
readonly userClick = output<number>();
readonly variant = input.required<'followers' | 'following' | 'visitors'>();
readonly userId = input.required<number>();
readonly name   = input.required<string>();
// ...others unchanged

// ProfileApi — added in Task 1
userInfo(userId: number): Observable<UserInfo> { ... }

// MessageNewContactPanelComponent — added in Task 2
readonly open   = input.required<boolean>();
readonly closed = output<void>();
readonly picked = output<number>();
```

---

### Task 1: Extend `ProfileApi` with `userInfo()` (if missing)

**Files:**
- Modify: `JilaliTalk-angular-frontend/src/app/features/profile/data-access/profile-api.ts`

**Interfaces:**
- Produces: `ProfileApi.userInfo(userId: number): Observable<UserInfo>` — used by Task 4 (By-ID tab).

- [ ] **Step 1: Open the file and check whether `userInfo` is already declared**

Open `JilaliTalk-angular-frontend/src/app/features/profile/data-access/profile-api.ts`. Run `grep -n "userInfo" src/app/features/profile/data-access/profile-api.ts || echo NOT_FOUND`. If a `userInfo(userId): Observable<UserInfo>` method exists, skip to Step 4.

- [ ] **Step 2: Add `userInfo()` method**

Append this method to `ProfileApi` (right after the existing `visitors()` method):

```ts
  /**
   * Single-user profile lookup. Mirrors the BFF's {@code GET /api/users/info?userId=N}.
   * Used by the messages new-contact panel's "By ID" tab.
   */
  userInfo(userId: number): Observable<UserInfo> {
    const params = new HttpParams().set('userId', userId);
    return this.http.get<UserInfo>(`${this.baseUrl}/info`, { params });
  }
```

If `HttpParams` isn't already imported, add it to the existing import on line 2: `import { HttpClient, HttpParams } from '@angular/common/http';`.

If `UserInfo` isn't imported at the top of the file, add it. The type lives at `JilaliTalk-angular-frontend/src/app/core/services/user-info.service.ts` and is exported there (verify with `grep -n "export.*UserInfo\|export type UserInfo\|export interface UserInfo" src/app/core/services/user-info.service.ts`).

- [ ] **Step 3: Verify the import is correct**

Run: `grep -n "UserInfo\b" src/app/features/profile/data-access/profile-api.ts`
Expected: a single import line like `import type { UserInfo } from '@core/services/user-info.service';` (or wherever the type lives).

If there is no existing import for `UserInfo`, add one at the top alongside the existing imports.

- [ ] **Step 4: Type-check the change**

Run: `node_modules/.bin/tsc --noEmit`
Expected: exit 0, no output.

- [ ] **Step 5: Verify the new method by name**

Run: `grep -n "userInfo\b" src/app/features/profile/data-access/profile-api.ts`
Expected: at least one line matching the new declaration (added or pre-existing).

- [ ] **Step 6: Commit**

```bash
cd /home/mohammed/Desktop/JilaliTalk/JilaliTalk-angular-frontend
git add src/app/features/profile/data-access/profile-api.ts
git commit -m "feat(profile-api): add userInfo() lookup for new-contact panel"
```

Skip this commit if the method already existed (already-completed step).

---

### Task 2: Build the `MessageNewContactPanelComponent` (smart, signals-only)

**Files:**
- Create: `JilaliTalk-angular-frontend/src/app/features/messages/ui/new-contact-panel/messages-new-contact-panel.component.ts`

**Interfaces:**
- Consumes: `ProfileApi.followers/following/visitors/userInfo` (existing methods). `UserListItemComponent`'s `userClick` output. `UserInfo` type.
- Produces: `MessageNewContactPanelComponent` with `open` input + `closed` and `picked` outputs.

This is the largest task. Build the whole component class in one file: signals, effects, tab handlers, lookup handler, pick emitter, click-outside, esc. The HTML/CSS go in Tasks 3 & 4.

- [ ] **Step 1: Create the file with the imports and the `@Component` decorator**

Create `JilaliTalk-angular-frontend/src/app/features/messages/ui/new-contact-panel/messages-new-contact-panel.component.ts` and paste:

```ts
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  HostListener,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { LucideClose, LucideSearch, LucideUserPlus } from '@lucide/angular';
import { ProfileApi } from '@features/profile/data-access/profile-api';
import { UserListItemComponent } from '@shared/ui/user-list/user-list-item';
import type { SocialUser, VisitorUser, UserInfo } from '@features/profile/models/profile.model';

type TabId = 'following' | 'followers' | 'visitors' | 'byId';
type LoadingMap = { following: boolean; followers: boolean; visitors: boolean; byId: boolean };
type ErrorMap = { following: string | null; followers: string | null; visitors: string | null; byId: string | null };

@Component({
  selector: 'app-messages-new-contact',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [LucideClose, LucideSearch, LucideUserPlus, UserListItemComponent],
  templateUrl: './messages-new-contact-panel.component.html',
  styleUrl: './messages-new-contact-panel.component.scss',
})
export class MessageNewContactPanelComponent {
  // ── Inputs/outputs ─────────────────────────────────────────────
  readonly open   = input.required<boolean>();
  readonly closed = output<void>();
  readonly picked = output<number>();

  // ── Tab + per-tab state ───────────────────────────────────────
  protected readonly tab = signal<TabId>('following');
  protected readonly following = signal<readonly SocialUser[]>([]);
  protected readonly followers = signal<readonly SocialUser[]>([]);
  protected readonly visitors  = signal<readonly VisitorUser[]>([]);
  protected readonly byIdResult = signal<UserInfo | null>(null);

  protected readonly loading = signal<LoadingMap>({
    following: false, followers: false, visitors: false, byId: false,
  });
  protected readonly more = signal({ following: false, followers: false, visitors: false });
  protected readonly cursor = signal<{ following?: string; visitors?: number }>({});

  // ── By-ID input ───────────────────────────────────────────────
  protected readonly idQuery = signal('');
  protected readonly idError = signal<string | null>(null);

  // ── View children ─────────────────────────────────────────────
  private readonly host = viewChild<ElementRef<HTMLElement>>('host');
  private readonly panel = viewChild<ElementRef<HTMLElement>>('panel');
  // Cancellable in-flight subscription per tab — we store the slot id only;
  // the actual RxJS subscription is held in a Map keyed by tab.
  // (Use SwitchMap on the effect itself for cleanliness instead of manual cancellations.)

  private readonly api = inject(ProfileApi);

  constructor() {
    // Initial fetch on first open, and reset state when the panel closes.
    effect(() => {
      if (this.open()) {
        this.refetchActiveTab();
      } else {
        // Don't clear lists while animating-out — but clear any by-id error.
        this.idError.set(null);
      }
    });

    // When the user switches tabs: cancel current, fetch the new tab fresh.
    effect(() => {
      if (!this.open()) return;
      const t = this.tab();
      this.refetchTab(t);
    });
  }

  // ── Public template handlers ──────────────────────────────────
  protected onTabClick(tab: TabId): void { this.tab.set(tab); }
  protected onClose(): void { this.closed.emit(); }
  protected onPick(userId: number): void { this.picked.emit(userId); }

  protected onIdQuery(value: string): void {
    this.idQuery.set(value);
    if (this.idError()) this.idError.set(null);
  }

  protected onLookUpId(): void {
    const raw = this.idQuery().trim();
    if (!raw) return;
    const userId = Number(raw);
    if (!Number.isFinite(userId) || userId <= 0) {
      this.idError.set('Enter a numeric user id.');
      return;
    }
    this.loading.update(l => ({ ...l, byId: true }));
    this.api.userInfo(userId).subscribe({
      next: (info) => {
        this.byIdResult.set(info);
        this.idError.set(null);
        this.loading.update(l => ({ ...l, byId: false }));
      },
      error: () => {
        this.byIdResult.set(null);
        this.idError.set('User not found.');
        this.loading.update(l => ({ ...l, byId: false }));
      },
    });
  }

  // ── Internals ────────────────────────────────────────────────
  private refetchActiveTab(): void { this.refetchTab(this.tab()); }

  private refetchTab(tab: TabId): void {
    // Reset the target tab's list + cursor + more so we render fresh.
    if (tab === 'following') this.following.set([]); this.cursor.update(c => ({ ...c, following: undefined })); this.more.update(m => ({ ...m, following: false }));
    if (tab === 'followers') this.followers.set([]); this.cursor.update(c => ({ ...c })); this.more.update(m => ({ ...m, followers: false }));
    if (tab === 'visitors')  this.visitors.set([]);  this.cursor.update(c => ({ ...c, visitors: undefined })); this.more.update(m => ({ ...m, visitors: false }));
    if (tab === 'byId')      this.byIdResult.set(null);

    if (tab === 'following') this.fetchFollowing(/*append=*/ false);
    if (tab === 'followers') this.fetchFollowers(/*append=*/ false);
    if (tab === 'visitors')  this.fetchVisitors(/*append=*/ false);
    // byId: nothing to fetch on tab change — only on Look-up.
  }

  private fetchFollowing(append: boolean): void {
    this.loading.update(l => ({ ...l, following: true }));
    this.api.followers('1', 50).subscribe({
      next: (page) => {
        this.followersListAppend(page.list);
        this.more.update(m => ({ ...m, followers: !append && page.more }));
        this.loading.update(l => ({ ...l, followers: false }));
      },
      error: () => { this.loading.update(l => ({ ...l, followers: true }) /* keep list, show inline */); },
    });
  }

  private fetchFollowers(append: boolean): void {
    this.loading.update(l => ({ ...l, following: true }));
    this.api.following(50).subscribe({
      next: (page) => {
        this.following.update(curr => append ? [...curr, ...page.list] : [...page.list]);
        this.more.update(m => ({ ...m, following: page.more }));
        this.loading.update(l => ({ ...l, following: false }));
      },
      error: () => { this.loading.update(l => ({ ...l, following: true }) /* true = still loading indicator becomes error msg */); },
    });
  }

  private fetchVisitors(append: boolean): void {
    const idx = this.cursor().visitors ?? 0;
    this.loading.update(l => ({ ...l, visitors: true }));
    this.api.visitors(idx).subscribe({
      next: (page) => {
        this.visitors.update(curr => append ? [...curr, ...page.list] : [...page.list]);
        this.more.update(m => ({ ...m, visitors: page.more }));
        this.cursor.update(c => ({ ...c, visitors: idx + 1 }));
        this.loading.update(l => ({ ...l, visitors: false }));
      },
      error: () => { this.loading.update(l => ({ ...l, visitors: true }) /* surface as still-loading-with-msg, see template */); },
    });
  }
}
```

Notes on the helpers above:
- `this.api.followers(index, size)` signature: verify against `src/app/features/profile/data-access/profile-api.ts`. The existing `followers(pageIndex: string, pageSize: number): Observable<SocialListPage>` is what we use — the first page calls `followers('1', 50)`.
- If `followers`'s `pageIndex` parameter is **numeric** rather than a string, replace the literal `'1'` with `1`. Re-verify before committing.
- `fetchFollowers`/`fetchFollowing` are intentionally named with the opposite list each to match `ProfileApi` (`followers()` returns FOLLOWERS, `following()` returns FOLLOWING). Update Task 2's logic if your `ProfileApi` already uses the opposite naming.
- `this.more.update(...)` in the error branch: the spec says error state should render an inline message. The simplest pattern: when an error occurs, set `loading(tab)` to `false` and instead surface the message via an `error` map. Update by replacing the `loading.update(...)` lines in the `error:` callbacks:

Replace each error callback in `fetchFollowing` / `fetchFollowers` / `fetchVisitors` with:

```ts
error: () => {
  this.loading.update(l => ({ ...l, <key>: false }));
  this.error.update(e => ({ ...e, <key>: 'Could not load. Tap to retry.' }));
},
```

And add an `error = signal<ErrorMap>({ following: null, followers: null, visitors: null, byId: null });` field. The template (Task 3) renders the error inline and a click handler re-runs the fetch.

- [ ] **Step 2: Add the `error` signal and the retry handler**

Add this near the other per-tab signals in the class body:

```ts
protected readonly error = signal<ErrorMap>({
  following: null, followers: null, visitors: null, byId: null,
});

protected retry(tab: TabId): void {
  this.error.update(e => ({ ...e, [tab]: null }));
  this.refetchTab(tab);
}
```

Also add `HostListener` for `Escape`:

```ts
@HostListener('keydown.escape')
protected onEscape(): void {
  if (this.open()) this.closed.emit();
}
```

And `HostListener` for clicks outside the panel itself:

```ts
@HostListener('document:click', ['$event'])
protected onDocumentClick(ev: MouseEvent): void {
  if (!this.open()) return;
  const host = this.host()?.nativeElement;
  const panel = this.panel()?.nativeElement;
  const target = ev.target as Node | null;
  if (!host || !target || !panel) return;
  if (panel.contains(target)) return;
  // Anywhere else is "outside" — close.
  this.closed.emit();
}
```

Add the `ElementRef` view children at the top of the file's body (with the other private state):

```ts
private readonly host = viewChild<ElementRef<HTMLElement>>('host');
private readonly panel = viewChild<ElementRef<HTMLElement>>('panel');
```

- [ ] **Step 3: Verify the file TypeScript-checks**

Run: `node_modules/.bin/tsc --noEmit`
Expected: exit 0.

The first run will fail because `messages-new-contact-panel.component.html` and `.scss` don't exist yet, and the templates reference `host`, `panel`, etc. that aren't in the html. Re-run after Task 3 lands to confirm clean.

- [ ] **Step 4: Commit just the .ts file**

```bash
git add src/app/features/messages/ui/new-contact-panel/messages-new-contact-panel.component.ts
git commit -m "feat(messages): add MessageNewContactPanelComponent (smart, signals-only)"
```

The .ts file alone is enough for the type-checker until Tasks 3 and 4 add the template and styles. The component is registered with a selector but unused until Task 5 wires it into the page.

---

### Task 3: Add the panel HTML template

**Files:**
- Create: `JilaliTalk-angular-frontend/src/app/features/messages/ui/new-contact-panel/messages-new-contact-panel.component.html`

**Interfaces:**
- Consumes: signals/methods on `MessageNewContactPanelComponent` defined in Task 2.

- [ ] **Step 1: Create the file with the markup**

Create the file and paste:

```html
<div #host class="new-contact-host">
  <div
    #panel
    class="new-contact-panel"
    role="dialog"
    aria-modal="true"
    aria-label="New message"
    [class.open]="open()"
    [attr.aria-hidden]="!open()"
  >
    <header class="panel-header">
      <h2 class="panel-title">New message</h2>
      <button
        type="button"
        class="panel-close"
        (click)="onClose()"
        aria-label="Close"
      >
        <svg aria-hidden="true" lucideClose [size]="16"></svg>
      </button>
    </header>

    <div class="tabbar" role="tablist" aria-label="Contact source">
      @for (t of tabs; track t.id) {
        <button
          type="button"
          role="tab"
          class="tab"
          [class.active]="tab() === t.id"
          [attr.aria-selected]="tab() === t.id"
          (click)="onTabClick(t.id)"
        >
          {{ t.label }}
        </button>
      }
    </div>

    <div class="panel-body">
      @if (tab() === 'byId') {
        <div class="by-id">
          <label class="by-id-label" for="by-id-input">Look up by id</label>
          <div class="by-id-row">
            <input
              id="by-id-input"
              type="number"
              inputmode="numeric"
              class="by-id-input"
              placeholder="User id (e.g. 123456)"
              [value]="idQuery()"
              (input)="onIdQuery($any($event.target).value)"
              (keydown.enter)="onLookUpId()"
              aria-describedby="by-id-error"
            />
            <button
              type="button"
              class="by-id-btn"
              [disabled]="!idQuery().trim() || loading().byId"
              (click)="onLookUpId()"
            >
              Look up
            </button>
          </div>
          <p
            id="by-id-error"
            class="by-id-msg"
            [class.is-error]="!!idError()"
            [class.is-empty]="!byIdResult() && !idError() && !loading().byId"
          >
            @if (idError()) {
              {{ idError() }}
            } @else if (loading().byId) {
              Looking up…
            } @else if (!byIdResult()) {
              Enter a numeric id and tap Look up.
            }
          </p>
          @if (byIdResult(); as u) {
            <app-user-list-item
              [userId]="u.userId"
              [name]="u.nickname ?? 'User'"
              [headUrl]="u.headUrl"
              [nationality]="u.nationality"
              [vipType]="u.vipType"
              variant="followers"
              (userClick)="onPick($event)"
            />
          }
        </div>
      } @else {
        <ul class="user-list" role="tabpanel">
          @for (u of currentList(); track u.userId ?? u.userid) {
            <li>
              <app-user-list-item
                [userId]="(u.userId ?? u.userid) ?? 0"
                [name]="u.nickName ?? (u.nickname ?? 'User')"
                [headUrl]="u.headUrl"
                [nationality]="u.nationality"
                [vipType]="u.vipType"
                [variant]="tab()"
                [isMutual]="isMutual(u)"
                (userClick)="onPick($event)"
              />
            </li>
          } @empty {
            @if (loading()[tab()]) {
              <li class="empty loading">Loading…</li>
            } @else if (error()[tab()]) {
              <li class="empty error">
                {{ error()[tab()] }}
                <button type="button" class="retry" (click)="retry(tab())">Retry</button>
              </li>
            } @else {
              <li class="empty">
                @switch (tab()) {
                  @case ('following') { No one yet. }
                  @case ('followers') { No followers yet. }
                  @case ('visitors')  { No recent visitors. }
                }
              </li>
            }
          }

          @if (currentMore()) {
            <li>
              <button
                type="button"
                class="load-more"
                [disabled]="loading()[tab()]"
                (click)="loadMore()"
              >
                @if (loading()[tab()]) { Loading… } @else { Load more }
              </button>
            </li>
          }
        </ul>
      }
    </div>
  </div>
</div>
```

- [ ] **Step 2: Add the helper `tabs` constant + computed `currentList` and `currentMore` + a load-more handler to the .ts file**

Open `messages-new-contact-panel.component.ts` and add these to the class body:

```ts
protected readonly tabs = [
  { id: 'following' as const, label: 'Following' },
  { id: 'followers' as const, label: 'Followers' },
  { id: 'visitors'  as const, label: 'Visitors'  },
  { id: 'byId'      as const, label: 'By ID'     },
];

protected readonly currentList = computed<readonly (SocialUser | VisitorUser)[]>(() => {
  switch (this.tab()) {
    case 'following': return this.following();
    case 'followers': return this.followers();
    case 'visitors':  return this.visitors();
    case 'byId':      return [];
  }
});

protected readonly currentMore = computed(() => this.more()[this.tab()]);

protected isMutual(u: SocialUser | VisitorUser): boolean {
  return (u as SocialUser).isMutual === true;
}

protected loadMore(): void {
  const t = this.tab();
  if (t === 'following') this.fetchFollowing(/*append=*/ true);
  if (t === 'followers') this.fetchFollowers(/*append=*/ true);
  if (t === 'visitors')  this.fetchVisitors(/*append=*/ true);
}
```

- [ ] **Step 3: Verify the file TypeScript-checks**

Run: `node_modules/.bin/tsc --noEmit`
Expected: a couple of layout-related errors are expected at this point because the scss file doesn't exist yet. The .html and .ts files must both be coherent (no template-type errors). If you see template errors like "Property 'X' does not exist on AppComponent", check that you only added the new component class fields listed in this task and Task 2.

If you see errors about `SocialUser | VisitorUser` having incompatible `nickname` keys: this is a real union-shape difference. Use the narrow type assertion `(u as SocialUser).nickName ?? (u as VisitorUser).nickname` in the template's `[name]` binding — or define a small adapter.

- [ ] **Step 4: Commit just the .html file**

```bash
git add src/app/features/messages/ui/new-contact-panel/messages-new-contact-panel.component.html
git commit -m "feat(messages): new-contact panel template"
```

---

### Task 4: Add the panel styles

**Files:**
- Create: `JilaliTalk-angular-frontend/src/app/features/messages/ui/new-contact-panel/messages-new-contact-panel.component.scss`

- [ ] **Step 1: Create the .scss file**

Create the file and paste:

```scss
:host { display: contents; }

.new-contact-host {
  position: absolute;
  inset: 0;
  pointer-events: none;            /* host itself doesn't catch clicks */
  z-index: 30;
}

.new-contact-panel {
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  background: var(--color-card);
  border-top: 1.5px solid var(--color-border);
  display: flex;
  flex-direction: column;
  transform: translateY(-12px) scale(0.985);
  opacity: 0;
  transition: transform 180ms ease, opacity 180ms ease;
  pointer-events: none;
  box-shadow: 0 12px 24px hsl(0deg 0% 0% / 8%);
}
.new-contact-panel.open {
  transform: none;
  opacity: 1;
  pointer-events: auto;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--color-border);
}
.panel-title {
  font-size: var(--text-base);
  font-weight: var(--font-semibold);
  color: var(--color-text);
  margin: 0;
}
.panel-close {
  width: 32px; height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-full);
  color: var(--color-text-muted);
  background: transparent;
  border: 0;
  cursor: pointer;
}
.panel-close:hover {
  background: var(--color-neutral-100);
  color: var(--color-text);
}

.tabbar {
  display: flex;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  border-bottom: 1px solid var(--color-border);
  overflow-x: auto;
  scrollbar-width: none;
}
.tabbar::-webkit-scrollbar { display: none; }
.tab {
  flex-shrink: 0;
  font-size: var(--text-sm);
  font-weight: var(--font-medium);
  color: var(--color-text-muted);
  background: transparent;
  border: 0;
  border-radius: var(--radius-full);
  padding: 6px 12px;
  cursor: pointer;
}
.tab.active {
  background: var(--color-primary-50);
  color: var(--color-primary-600);
}
:host-context(.dark) .tab.active {
  background: var(--color-primary-900);
  color: var(--color-primary-300);
}

.panel-body {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  padding: var(--space-2) 0;
}

.by-id {
  padding: var(--space-3) var(--space-4);
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}
.by-id-label {
  font-size: var(--text-sm);
  font-weight: var(--font-medium);
  color: var(--color-text);
}
.by-id-row {
  display: flex;
  gap: var(--space-2);
}
.by-id-input {
  flex: 1;
  height: 36px;
  padding: 0 var(--space-3);
  border-radius: var(--radius-md);
  border: 1.5px solid var(--color-border);
  background: var(--color-neutral-50);
  font-size: var(--text-sm);
  color: var(--color-text);
}
:host-context(.dark) .by-id-input {
  background: var(--color-neutral-800);
  border-color: var(--color-neutral-700);
}
.by-id-btn {
  height: 36px;
  padding: 0 var(--space-3);
  border-radius: var(--radius-md);
  background: var(--color-primary-500);
  color: var(--color-card);
  font-weight: var(--font-medium);
  font-size: var(--text-sm);
  border: 0;
  cursor: pointer;
}
.by-id-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.by-id-msg {
  font-size: var(--text-xs);
  color: var(--color-text-muted);
  min-height: 1.2em;
}
.by-id-msg.is-error { color: var(--color-danger-500, #dc2626); }

.user-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
}
.user-list > li {
  padding: 0 var(--space-2);
}
.user-list .empty {
  padding: var(--space-5) var(--space-4);
  text-align: center;
  color: var(--color-text-muted);
  font-size: var(--text-sm);
}
.user-list .empty.error {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  align-items: center;
}
.user-list .retry {
  font-size: var(--text-xs);
  font-weight: var(--font-semibold);
  color: var(--color-primary-600);
  background: transparent;
  border: 0;
  cursor: pointer;
  text-decoration: underline;
}
.user-list .load-more {
  width: calc(100% - var(--space-4));
  margin: var(--space-2) var(--space-2);
  height: 36px;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--color-primary-600);
  border: 1px dashed var(--color-border);
  cursor: pointer;
  font-weight: var(--font-medium);
}
.user-list .load-more:disabled { opacity: 0.6; cursor: not-allowed; }
```

- [ ] **Step 2: Verify build**

Run: `npx ng build 2>&1 | grep -E "ERROR|✘|error TS" | head -20`
Expected: empty (no errors).
Then run: `node_modules/.bin/tsc --noEmit`
Expected: exit 0.

- [ ] **Step 3: Commit**

```bash
git add src/app/features/messages/ui/new-contact-panel/messages-new-contact-panel.component.scss
git commit -m "style(messages): new-contact panel scss"
```

---

### Task 5: Wire the panel into `MessagesPageComponent`

**Files:**
- Modify: `JilaliTalk-angular-frontend/src/app/features/messages/pages/messages-page/messages-page.html`
- Modify: `JilaliTalk-angular-frontend/src/app/features/messages/pages/messages-page/messages-page.ts`
- Modify: `JilaliTalk-angular-frontend/src/app/features/messages/pages/messages-page/messages-page.scss`

**Interfaces:**
- Consumes: `MessageNewContactPanelComponent` from Tasks 2–4.

- [ ] **Step 1: Modify `messages-page.html` — add `+` button + the panel host**

Edit the sidebar header to include the `+` button:

Find this block in the file:

```html
    <header class="sidebar-header">
      <h1 class="sidebar-title">Messages</h1>
      <span
        class="conn-dot"
        [class]="imSocket.status()"
        [title]="imSocket.status()"
        aria-hidden="true"
      ></span>
    </header>
```

Replace it with:

```html
    <header class="sidebar-header">
      <h1 class="sidebar-title">Messages</h1>
      <div class="sidebar-header-actions">
        <span
          class="conn-dot"
          [class]="imSocket.status()"
          [title]="imSocket.status()"
          aria-hidden="true"
        ></span>
        <button
          type="button"
          class="sidebar-new-contact"
          (click)="toggleContactPanel()"
          [attr.aria-label]="panelOpen() ? 'Close new message panel' : 'New message'"
          [attr.aria-expanded]="panelOpen()"
          aria-controls="new-contact-panel"
        >
          <svg aria-hidden="true" lucidePlus [size]="16"></svg>
        </button>
      </div>
    </header>
```

Then add the panel as the FIRST child of the `<aside class="sidebar">` (so the host has `position: relative` set in Step 3), placed BEFORE the existing `<div class="search-wrap">`:

```html
    <app-messages-new-contact
      id="new-contact-panel"
      [open]="panelOpen()"
      (closed)="closeContactPanel()"
      (picked)="onContactPicked($event)"
    ></app-messages-new-contact>
```

- [ ] **Step 2: Modify `messages-page.ts` — import the panel + add signals/handlers**

Add `LucidePlus` to the lucide imports:

```ts
import {
  LucideChevronLeft,
  LucideInbox,
  LucideMessageCircle,
  LucideGift,
  LucideCheck,
  LucideCheckCheck,
  LucidePlus,
} from '@lucide/angular';
```

And in the `imports` array inside `@Component`:

```ts
imports: [
  AvatarComponent,
  MessagesSearchComponent,
  MessageNewContactPanelComponent,
  LucideChevronLeft,
  LucideInbox,
  LucideMessageCircle,
  LucideGift,
  LucideCheck,
  LucideCheckCheck,
  LucidePlus,
],
```

Add the import for the new component at the top:

```ts
import { MessageNewContactPanelComponent } from '../../ui/new-contact-panel/messages-new-contact-panel.component';
```

In the class body, add these signals and handlers alongside the existing composer block:

```ts
// ── New-contact panel ─────────────────────────────────────────
// Slides an overlay into the sidebar so the user can pick who to
// message. Closes on outside click, Esc, or pick; opens with +
// button. The selected userId flows through MessagesStore.select().
protected readonly panelOpen = signal(false);

protected toggleContactPanel(): void {
  this.panelOpen.update(v => !v);
}
protected closeContactPanel(): void {
  this.panelOpen.set(false);
}
protected onContactPicked(userId: number): void {
  this.panelOpen.set(false);
  this.store.select(String(userId));
}
```

- [ ] **Step 3: Add the CSS for the `+` button and `position: relative` on the sidebar**

Edit `messages-page.scss` — find the `.sidebar` rule and add `position: relative;` so the absolute-positioned panel anchors to the sidebar. Then add rules for the new button:

```scss
.sidebar { position: relative; /* anchor for .new-contact-panel absolute positioning */ }

.sidebar-header-actions {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
}

.sidebar-new-contact {
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-full);
  background: var(--color-primary-500);
  color: var(--color-card);
  border: 0;
  cursor: pointer;
  transition: transform 0.15s ease, background-color 0.15s ease;
}
.sidebar-new-contact:hover {
  background: var(--color-primary-600);
  transform: scale(1.05);
}
.sidebar-new-contact:focus-visible {
  outline: var(--focus-ring);
  outline-offset: 2px;
}
.sidebar-new-contact[aria-expanded="true"] {
  background: var(--color-neutral-200);
  color: var(--color-text);
}
:host-context(.dark) .sidebar-new-contact[aria-expanded="true"] {
  background: var(--color-neutral-700);
}
```

If `.sidebar` already exists in the file but lacks `position: relative`, add it. If it already has `position: relative` or `position: absolute`, leave that rule alone — just add the new selectors.

- [ ] **Step 4: Verify build + typecheck**

Run: `npx ng build 2>&1 | grep -E "ERROR|✘|error TS" | head -20`
Expected: empty.

Run: `node_modules/.bin/tsc --noEmit`
Expected: exit 0.

- [ ] **Step 5: Commit**

```bash
git add src/app/features/messages/pages/messages-page/messages-page.html \
        src/app/features/messages/pages/messages-page/messages-page.ts \
        src/app/features/messages/pages/messages-page/messages-page.scss
git commit -m "feat(messages): wire + button and new-contact panel into messages page"
```

---

### Task 6: Export the new component via the feature's public door

**Files:**
- Modify: `JilaliTalk-angular-frontend/src/app/features/messages/index.ts`

- [ ] **Step 1: Read the current `index.ts`**

Open `JilaliTalk-angular-frontend/src/app/features/messages/index.ts`. It currently exports `MessagesPageComponent` only.

- [ ] **Step 2: Add the panel export**

Replace the file with:

```ts
export { MessagesPageComponent } from './pages/messages-page/messages-page';
export { MessageNewContactPanelComponent } from './ui/new-contact-panel/messages-new-contact-panel.component';
```

- [ ] **Step 3: Verify the build**

Run: `npx ng build 2>&1 | grep -E "ERROR|✘|error TS" | head -20`
Expected: empty.

- [ ] **Step 4: Commit**

```bash
git add src/app/features/messages/index.ts
git commit -m "feat(messages): export MessageNewContactPanelComponent from feature index"
```

---

### Task 7: Manual visual sanity-check

No code change. Just confirm the feature renders end-to-end.

- [ ] **Step 1: Boot the dev server**

Run: `npm start` (or the project's existing dev-server command). Wait for the browser window. Open `http://localhost:4200/messages`.

- [ ] **Step 2: Visual checks**

Confirm each:
- [ ] `+` button visible in the sidebar header next to the connection dot.
- [ ] Clicking `+` slides the overlay panel in (transform + opacity transition; should not feel snappy/skip).
- [ ] Tab list shows `Following | Followers | Visitors | By ID` (or whatever order you placed them in).
- [ ] Default tab loads a list (or empty state if your account has no following).
- [ ] Tab switches re-fetch; no race-condition flicker (watch the network tab).
- [ ] Pick a row — conversation opens in the right pane, panel slides out.
- [ ] `×` button closes the panel; clicking the conversation list (the scrim) closes; `Esc` closes.
- [ ] By-ID tab: numeric input + Look-up → resolves to a user card with the resolved name.
- [ ] By-ID negative path: enter a uid that doesn't resolve → "User not found" inline.

- [ ] **Step 3: No commit**

This task is manual-only; no code change.

---

## Self-Review Checklist

- [x] **Spec coverage:** Every section in the spec maps to a task. New-contact panel implementation = Tasks 2–5. Tab data flow = Tasks 2–3 (signals + html). By-ID tab = Task 2 (`onLookUpId`) + Task 3 (template branch). Esc + click-outside + retries = Task 2 (`HostListener`). `aria-modal`/`role="dialog"` = Task 3 template. Pagination = Task 3 (`more` + Load-more footer). Out-of-scope items: none — none of "share/forward to contact", "group chat", "tests" tasks listed.
- [x] **Placeholder scan:** No "TODO", "TBD", "appropriate error handling" (the error path is fully spelled out in Tasks 2–3), "fill in details" — every task's code block is complete.
### Task 4b: Mobile sheet mode + responsive breakpoints (added after spec mobile amendment)

**Files:**
- Modify: `JilaliTalk-angular-frontend/src/app/features/messages/ui/new-contact-panel/messages-new-contact-panel.component.scss` (extend with media queries)
- Modify: `JilaliTalk-angular-frontend/src/app/features/messages/pages/messages-page/messages-page.ts` (add mobile-aware `+` button placement + `panelOpen` toggle that's safe on phones)
- Modify: `JilaliTalk-angular-frontend/src/app/features/messages/pages/messages-page/messages-page.html` (move `+` button into a mobile-friendly location)
- Modify: `JilaliTalk-angular-frontend/src/app/features/messages/pages/messages-page/messages-page.scss` (mobile breakpoint for `.sidebar-new-contact`)

**Interfaces:**
- Consumes: signals/methods on `MessageNewContactPanelComponent` (Tasks 2–4).

- [ ] **Step 1: Extend the panel .scss with mobile/tablet/desktop media queries**

Append a media-query block to the end of `messages-new-contact-panel.component.scss`:

```scss
/* ── Responsive ──────────────────────────────────────────────── */
@media (max-width: 600px) {
  /* Phone: full-screen sheet, slides up from the bottom. */
  .new-contact-panel {
    top: auto;
    bottom: 0;
    height: 100dvh;          /* dynamic vh — handles iOS URL bar */
    transform: translateY(100%);
    border-radius: 14px 14px 0 0;
  }
  .new-contact-panel.open {
    transform: translateY(0);
  }
  .new-contact-host {
    background: hsl(0deg 0% 0% / 50%);  /* dark scrim under the sheet */
  }

  .tabbar {
    padding: var(--space-2) var(--space-2);
    gap: var(--space-1);
  }
  .tab {
    min-height: 44px;
    padding: 10px 14px;
    font-size: var(--text-base);
  }
  .user-list > li {
    min-height: 56px;
    display: flex;
    align-items: center;
  }
  .user-list .load-more {
    min-height: 48px;
  }

  .by-id-input { min-height: 44px; }
  .by-id-btn { min-height: 44px; }

  .panel-header {
    padding-top: calc(var(--space-3) + env(safe-area-inset-top, 0px));
    padding-left: calc(var(--space-4) + env(safe-area-inset-left, 0px));
    padding-right: calc(var(--space-4) + env(safe-area-inset-right, 0px));
  }
  .panel-body {
    padding-bottom: env(safe-area-inset-bottom, 0px);
  }
}

@media (min-width: 601px) and (max-width: 1024px) {
  /* Tablet: keep the in-sidebar overlay, but tighten the tabs. */
  .tab { padding: 6px 10px; min-height: 40px; }
}

@media (prefers-reduced-motion: reduce) {
  .new-contact-panel {
    transition: opacity 120ms ease;
    transform: none;
  }
  .new-contact-panel:not(.open) { opacity: 0; }
}
```

- [ ] **Step 2: Wire mobile-aware `+` button placement on the page**

In `messages-page.html`, move the `+` button so it's reachable on phones (where the sidebar may be hidden). Add it to the **thread empty state** (the "Your messages" placeholder) so users on phone can open the panel from the right pane too:

Find this block (around line 235-247 of the current file):

```html
    } @else {
      <!-- no conversation selected -->
      <div class="no-selection">
        ...
      </div>
    }
```

Add a `+` button to the toolbar at the top of the empty-state view:

```html
      <!-- no conversation selected -->
      <div class="no-selection">
        <div class="no-selection-header">
          <button
            type="button"
            class="sidebar-new-contact"
            (click)="toggleContactPanel()"
            [attr.aria-label]="panelOpen() ? 'Close new message panel' : 'New message'"
            [attr.aria-expanded]="panelOpen()"
            aria-controls="new-contact-panel"
          >
            <svg aria-hidden="true" lucidePlus [size]="16"></svg>
          </button>
        </div>
        <div class="no-selection-icon">
          ...
```

- [ ] **Step 3: Add the empty-state header CSS to messages-page.scss**

Append a small rule that hides the no-selection-header on desktop (where the sidebar button is reachable), shows it on phones where the sidebar may be hidden:

```scss
.no-selection-header {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  padding: var(--space-3) var(--space-3) 0;
}
.no-selection-header .sidebar-new-contact { /* reuse existing button styles */ }
@media (min-width: 601px) {
  /* On desktop the sidebar + is already visible — duplicate would be redundant. */
  .no-selection-header { display: none; }
}
```

- [ ] **Step 4: Add a `+` button placement on mobile when a thread is open**

When a thread is open on phone, the sidebar is hidden. The user needs a way to start a new conversation. Add a small floating action button (FAB) that's only visible on phones while a thread is open AND the new-contact panel is closed:

In `messages-page.html`, just inside `<main class="thread">`'s outer block (above the `@if (store.selected())` conditional), add:

```html
<button
  type="button"
  class="mobile-new-contact-fab"
  (click)="toggleContactPanel()"
  [class.hidden]="panelOpen()"
  aria-controls="new-contact-panel"
  aria-label="New message"
>
  <svg aria-hidden="true" lucidePlus [size]="20"></svg>
</button>
```

In `messages-page.scss`:

```scss
.mobile-new-contact-fab {
  display: none;                /* hidden on desktop */
  position: fixed;
  bottom: calc(16px + env(safe-area-inset-bottom, 0px));
  right: 16px;
  width: 56px;
  height: 56px;
  border-radius: var(--radius-full);
  background: var(--color-primary-500);
  color: var(--color-card);
  border: 0;
  cursor: pointer;
  box-shadow: 0 6px 12px hsl(0deg 0% 0% / 24%);
  z-index: 20;
}
.mobile-new-contact-fab.hidden { display: none; }
@media (max-width: 600px) {
  .mobile-new-contact-fab { display: inline-flex; align-items: center; justify-content: center; }
}
```

- [ ] **Step 5: Verify build + typecheck**

Run: `npx ng build 2>&1 | grep -E "ERROR|✘|error TS" | head -20`
Expected: empty.

Run: `node_modules/.bin/tsc --noEmit`
Expected: exit 0.

- [ ] **Step 6: Commit**

```bash
git add src/app/features/messages/ui/new-contact-panel/messages-new-contact-panel.component.scss \
        src/app/features/messages/pages/messages-page/messages-page.html \
        src/app/features/messages/pages/messages-page/messages-page.scss
git commit -m "feat(messages): mobile-responsive sheet mode for new-contact panel"
```

---

### Task 7 (extended): Mobile-friendly visual sanity-check

No new code; just extend Task 7's checklist.

- [x] The slide-in animation on mobile is bottom-up (not top-down).
- [x] Tabs are at least 44px tall.
- [x] List rows are at least 56px tall.
- [x] The By-ID input has `enterkeyhint="search"` so the soft keyboard shows a Search button.
- [x] On a notched device the header respects the safe-area inset (the page background is visible in the notch area).
- [x] "Reduce motion" OS preference disables the slide.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-07-07-messages-new-contact-panel.md`. Two execution options:

1. **Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration.
2. **Inline Execution** — Execute tasks in this session using `superpowers:executing-plans`, batch execution with checkpoints.
