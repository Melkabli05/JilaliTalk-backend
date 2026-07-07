# Spec — Messages "New Contact" Panel

## Goal

Add a `+` button to the **conversation-list sidebar** of the Messages page that opens an overlay panel for picking a person to start (or continue) a 1:1 conversation with. Recipients are grouped into four tabs:

- **Following** — people the current user follows
- **Followers** — people who follow the current user
- **Visitors** — recent profile visitors
- **By ID** — free-text lookup by numeric user id (resolves via `GET /api/users/info`)

Pick → `MessagesStore.select(userId)` opens the conversation in the right pane, panel closes.

## Background / Scope

Today, `MessagesStore.dispatch` only adds a conversation when a DM **arrives** from upstream (`text_message`/`image_message`/etc. events). There is no way for the user to **start** a conversation with someone they haven't received a DM from — the conversation row doesn't exist, and even if it did, the existing composer always requires the user to have picked one from the list. This new-contact panel fills exactly that gap.

The BFF already exposes every endpoint required:

- `GET /api/users/followers?lang=en&page_index=<cursor>&page_size=50` → `SocialListEnvelope { data: { list: SocialUser[], more, count, page_index } }`
- `GET /api/users/following?page_size=50` → same envelope
- `POST /api/users/visitors` → `VisitorsEnvelope { data: { list: VisitorUser[], more, count } }`
- `GET /api/users/info?userId=N` → `UserInfo`

And the Angular `ProfileApi` already has `followers()` / `following()` / `visitors()` wired through `HttpClient` (used by `profile-page.component.ts`).

**No backend work needed.** This is a frontend-only feature.

## In scope

- `+` button in the conversation-list sidebar header.
- Overlay panel rendered inside the sidebar, sliding down from below the header.
- Four tabs: Following, Followers, Visitors, By ID.
- Each tab fetches its own list independently. Pagination via the `more` boolean.
- "By ID" tab: numeric input + Look-up button; on success show the resolved user; on failure show inline "User not found".
- Picking a contact from any tab emits `picked.emit(userId)`. The page wires that to `MessagesStore.select(userId)` and closes the panel.
- Closing behavior: `×` button, `Esc` key, click-outside (anywhere outside the panel; the sidebar scrim or the thread pane both qualify).
- Empty + loading + error states per tab.
- Accessibility: `aria-modal="true"` + `role="dialog"`, focus-trap while open, `Esc` closes, focus returns to the `+` button on close.
- Coalesced/single in-flight request per tab (rapid tab switches cancel earlier fetches).
- Fresh fetch on every open (no cache layer; the panel is rarely opened and the roundtrip is cheap).

## Out of scope

- New BFF endpoints (none needed).
- Multi-select / group-chat composer (each pick opens a single conversation; group messaging is already handled by the existing `room-group` realtime channel, not this feature).
- "Share to a contact" or message-forwarding from an existing conversation (separate feature).
- Avatars / profile cards in the panel — `UserListItem` already shows avatar + nickname + nationality + (mutual/visits meta). No new visual layer needed.
- Unit/integration tests. The repository convention is "no unit tests unless asked" (`feedback_no-unit-tests-unless-asked`); per CLAUDE.md this feature ships without new tests.

## Architecture

### Layer split

- `MessageNewContactPanelComponent` (NEW, `OnPush`, dumb): owns the overlay. Inputs `open`, outputs `closed` and `picked`.
- `MessagesPageComponent` (existing): owns the `+` button. Holds the `panelOpen` signal and a `onContactPicked(userId)` handler that calls `store.select(userId)` and clears the panel.
- `ProfileApi` (existing, reused as-is): `followers()`, `following()`, `visitors()` already exist.
- A small `userInfo(userId)` lookup is added by either (a) injecting the existing `UserController` infrastructure via `HttpClient` direct call, or (b) extending `ProfileApi.userInfo()` if the latter doesn't exist — to confirm during implementation and pick whichever is shorter.

No new service is created if the existing `ProfileApi` already exposes all four methods (likely true after `userInfo()` is added).

### State on the panel (signals only)

- `tab: 'following' | 'followers' | 'visitors' | 'byId'`
- `following: SocialUser[]`, `followers: SocialUser[]`, `visitors: VisitorUser[]`, `byIdResults: UserInfo | null`
- `loading: { following, followers, visitors, byId }` (per-tab loading flag, so the loading shimmer only renders for the active tab)
- `cursors: { following?: pageIndex, visitors?: pageIndex }` (the `following` endpoint cursorizes on a string; `visitors` cursorizes on an int)
- `more: { following, followers, visitors }: boolean` — drives the "Load more…" footer button
- `error: { ... }: string | null` — last error per tab (rendered as inline text, not toast)
- `idQuery: string`, `idError: string | null` — By-ID input state

All state is component-local. No store/services beyond `ProfileApi`.

## Visual layout

```
┌─────────────────── sidebar ────────────────────┐
│  Messages                       [conn-dot] [+] │   <- header with new + button
├───────────────────────────────────────────────┤
│  ┌─ search ─┐                                  │
│  └─────────┘                                   │
│  24 conversations                              │
├───────────────────────────────────────────────┤
│  (existing list)         ▲ panel overlays here │
│                          │ (scrim)              │
│                          │                      │
│                  ┌───────┴────────┐             │
│                  │ New message  × │             │   <- panel header
│                  ├────────────────┤             │
│                  │ Following | … │             │   <- tab bar
│                  ├────────────────┤             │
│                  │  ⊙ Mohammed … │             │   <- list (UserListItem)
│                  │  ⊙ someone2  … │             │
│                  │  …              │             │
│                  │   Load more     │             │   <- footer button
│                  └────────────────┘             │
└───────────────────────────────────────────────┘
```

The right pane (thread + composer) stays interactive; only the sidebar is grayed. Picking from the list, click-outside, or `×` closes the overlay.

## Data flow on open

```
panel.open → true
  onTab(tab):  cancel previous tab's in-flight Observable; clear that tab's list+error;
               set new tab's loading=true; fetch first page; on success → append list,
                 set more=response.more, set cursor=response.page_index, set loading=false;
                 on error → set error text, set loading=false.
  onScrollToBottom(): if more && !loadingNext: fetch next page, append, update cursor+more.
  onLookUpId(id):
    fetch UserInfo(id)
      on success: byIdResults = response; idError = null;
      on 404 / catch error: byIdResults = null; idError = "User not found".
  onPick(userId):
    picked.emit(userId)
    // in the page: hide panel + store.select(String(userId))
  onClose() / press Esc / click outside: closed.emit(); page hides panel.
```

## API contracts (recap)

All four endpoints already exist; no backend changes:

| Endpoint | Returns | Cursor |
|---|---|---|
| `GET /api/users/followers?lang=en&page_index=…&page_size=50` | `SocialListEnvelope { data: { list: SocialUser[], more, count, page_index } }` | `page_index` (string) |
| `GET /api/users/following?page_size=50` | same | (latest N, no cursor in v1) |
| `POST /api/users/visitors` `{ index: number }` | `VisitorsEnvelope { data: { list: VisitorUser[], more, count } }` | `index` (number) |
| `GET /api/users/info?userId=N` | `UserInfo` | — |

The `UserInfo` and `VisitorUser` types live in `features/profile/models/profile.model.ts`. `SocialUser` likewise.

## Angular component outline

```ts
@Component({
  selector: 'app-messages-new-contact',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    LucideClose, LucidePlus, LucideSearch, LucideUsers, LucideUserCheck, LucideUserPlus,
    UserListItemComponent,
  ],
  templateUrl: './messages-new-contact-panel.html',
  styleUrl: './messages-new-contact-panel.scss',
})
export class MessageNewContactPanelComponent {
  readonly open     = input.required<boolean>();
  readonly closed   = output<void>();
  readonly picked   = output<number>();
  // ... signals as listed above.
}
```

### Inputs / outputs

- `open: boolean` — when true, the panel animates in (transform + opacity); when false, it animates out and is `pointer-events: none`.
- `closed: void` — emit when the user dismisses (× button, Esc, click-outside).
- `picked: number` — emit the `userId` the user chose. The page is responsible for closing the panel after this emit.

### Event handlers (template bridge methods)

- `onTab(tab)` — switch tabs.
- `onScrollToBottom()` — infinite-scroll trigger.
- `onLookUpId()` — call `ProfileApi.userInfo(query)` or new tiny `UserApi`.
- `onPick(userId: number)` — emit `picked.emit(userId)`.
- `onClose()` — emit `closed.emit()`.

### Effects

- An effect on `open` cancels any in-flight subscription and triggers an initial fetch for whichever tab is currently active.
- A second effect on `tab` (running only when `open`) cancels the previous tab's in-flight fetch, clears that list+error+cursor, and triggers a fresh first-page fetch.
- A third effect on the scroll-trigger element (content container's `IntersectionObserver` or `scroll` event) loads the next page when `more && !loadingNext`.

## Edge cases

- **Self**: upstream never returns the caller in their own follower/following lists — no filter needed.
- **Already have a conversation**: `MessagesStore.select(userId)` activates the row in the list without creating a duplicate. The picked user is already mapped in `_convMap` if a DM has been received previously.
- **Tab switch spam**: every effect re-load runs `switchMap`-style cancellation so only the latest tab's response is accepted.
- **By-ID with no user**: BFF returns null or 404; render "User not found" inline below the input.
- **Click-outside**: a host-level `(click)` listener scoped to the panel container checks `$event.target` against the panel element; click outside emits `closed`.
- **`Esc`**: a `(keydown.escape)` listener on the host element.
- **`+` button toggle**: hide the `+` button while the panel is open; show `×` (close) on the panel header instead. Keeps the affordance obvious.
- **Long lists**: cap page-size at 50 (BFF default); show a "Load more…" footer button when `more` is true.
- **Discarded fetch**: when the user closes the panel mid-fetch, the in-flight subscription is unsubscribed; no data leaks into state.
- **Avatar placeholder**: `UserListItem` already handles `headUrl ?? ''` cleanly.

## Accessibility

- The panel wrapper: `role="dialog"`, `aria-modal="true"`, `aria-label="New message"`.
- Tab bar: `role="tablist"`. Each tab: `role="tab"`, `aria-selected`. Lists under tabs: `role="tabpanel"` with `aria-labelledby` pointing to the active tab.
- By-ID input: `<input type="number" inputmode="numeric">` with `aria-describedby` linking to the inline error or "Look up" button.
- Focus management: on open, focus moves to the first tab. On `Esc` or close, focus returns to the `+` button.
- Keyboard navigation: arrow keys inside the tab bar cycle tabs; `Tab` inside the list cycles rows; `Enter` on a row emits `picked(userId)`.

## Implementation plan (Phase 4)

1. Add the `+` button to `messages-page.html` header, plus the `<app-messages-new-contact>` element inside the sidebar.
2. Build `messages-new-contact-panel.component.{ts,html,scss}`.
3. Wire the `(picked)` output to `onContactPicked(userId)` which calls `store.select(userId)`.
4. If `ProfileApi.userInfo()` doesn't exist yet, add it.
5. Visual QA via `ng build` and screenshot pass-through.

Each step must be a single commit; no bundled work.

## Acceptance criteria

1. Typing the `+` button reveals the panel without route navigation.
2. All four tabs render their respective user list (or a "no X yet" empty state when the list is empty).
3. Picking a row opens that user's conversation in the right pane and the panel hides.
4. The By-ID tab resolves a numeric user id to a card and "Look up" / Enter on the input triggers it.
5. Esc, click-outside, and the `×` button all close the panel without selecting a contact.
6. Rapid tab switching does not produce race-condition flicker (only the latest response wins).
7. `ng build` exits 0 and `tsc --noEmit` exits 0.
