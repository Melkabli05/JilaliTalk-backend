# HtImNotifyMapper

`src/main/java/com/jilali/im/HtImNotifyMapper.java` — package-private final class.

## Purpose
Maps decoded JSON push payloads from the personal `ht_im/sock` DM channel to `ImRealtimeEvent`s. Pure, no networking, no mutable state (only `selfUserId`). The DM/personal counterpart of `com.jilali.realtime.HtNotifyMapper`.

## Responsibilities
- Dispatch on top-level `msg_type` (text/image/send_gift/introduction/voice_room/live_link/new_voice_visitor) or `notify_type` (18/48/34/35/40/53 + `new_voice_visitor`).
- Build the concrete `ImRealtimeEvent` record for each shape, defensively pulling fields with fallbacks.
- Resolve sender identity correctly for nested room-share shapes (voice_room/live_link) from the room object's own `user_id`/`host_id`, falling back to the envelope header.

## Public API
- `HtImNotifyMapper(long selfUserId)`.
- `ImRealtimeEvent map(JsonNode root, Header h)` — sole entry point; returns `null` for unmapped/dropped frames.
- All other methods private: `mapText`, `mapImage`, `mapGift`, `mapIntro`, `mapVoiceRoom`, `mapLiveLink`, `mapProfileVisit`, `mapNotify`, and static helpers `textOr`, `nullableText` (2 overloads), `nullableAny`.

## Dependencies
- Imports: `JsonNode`, `HtImPacketFramer.Header`, `ImRealtimeEvent`.
- Constructed by `HtImUpstreamConnector` (`new HtImNotifyMapper(userId)`); called from `dispatchPush`. Exercised by `HtImNotifyMapperTest`. Grep-verified: only the connector + test reference it.

## Coupling and cohesion analysis
High cohesion (all JSON→event mapping). Efferent coupling to `ImRealtimeEvent` variants and to the wire shapes. Tight but appropriate. The `Header h` parameter creates a coupling to the binary framer purely to source a fallback `fromId` — a small leak of transport detail into the mapper.

## Code smells
- **Long Method / large switch**: `map` + `mapNotify` (lines 194-261) mix a `cname`-branch room-share path, a string `notify_type` switch, and a visitor-field loop — three dispatch strategies in one method.
- **Feature Envy / Primitive Obsession**: every mapper repeats the `textOr(root,"from_id",String.valueOf(h.fromId()))` + nickname + head_url triple (mapText/mapImage/mapGift/mapIntro/mapVoiceRoom/mapLiveLink) — a "sender identity" concept that is never a type, extracted 6×.
- **Duplicated dispatch**: `mapVoiceRoom`/`mapLiveLink` are near-identical (voice_room vs live_link key, active_number vs none, name vs activity_name).
- **Duplicated profile-visit logic**: `mapNotify`'s inline `visitor_*` loop (lines 250-259) overlaps heavily with the dedicated `mapProfileVisit` (283-295) — two implementations of "resolve the visitor, drop if self."
- **Stringly-typed notify_type**: `switch` on `"18"/"48"/...` string literals.

## Technical debt
- The `mapNotify` `new_voice_visitor` fallback loop duplicates `mapProfileVisit`; the `msg_type`-keyed path already routes `new_voice_visitor` to `mapProfileVisit`, so the `notify_type` loop path is a second, subtly different resolution — a refactor hazard (Shotgun Surgery if visitor logic changes).
- Heavy smali-citation comments document prior bugs (**recently fixed**): gift label `send_gift` vs `gift`, voice_room/live_link uid sourced from room not envelope, `server_ts` timestamps. Evidence of prior technical debt and insufficient automated coverage of inbound shapes.

## Duplicate logic — comparison with realtime `HtNotifyMapper`
Both are singleton-style pure notify mappers keyed on `notify_type`, both use identical private `textOr(node,field,fallback)` helpers and `userId(info)`/`cname(info)` idioms.

| Aspect | `HtImNotifyMapper` (im) | `HtNotifyMapper` (realtime) |
|---|---|---|
| Entry | `map(JsonNode root, Header h)` | `Optional<RoomRealtimeEvent> map(String text)` |
| Input | pre-parsed `JsonNode` + binary header | raw JSON string (parses itself) |
| Dispatch | `msg_type` string switch, then `notify_type` string switch | `notify_type` string switch on `event.notify_info` |
| Shared notify types | 18/48/34/35/40/53 → StageInvite/ModInvite/ModAccepted/ModRemoved/ModUnmuted/Follow | same integers → same-named room events |
| Identity fallback | envelope `h.fromId()` / `selfUserId` | `user_id` field, drop if 0 |
| Return sentinel | `null` | `Optional` |
| Helpers | `textOr`, `nullableText` | `textOr`, `userId`, `cname` |

The `notify_type` 18/34/35/40/48/53 handling is semantically duplicated across both mappers (same event families, different DTO namespaces `ImRealtimeEvent.*` vs `RoomRealtimeEvent.*`). ~40 lines of parallel switch arms. A shared `NotifyType` enum + a shared identity-extraction helper could de-duplicate the field plumbing, but the event *types* differ so the switch bodies cannot merge without unifying the two DTO hierarchies.

## Dead or unused code
None. All private methods reachable from `map`. Grep-verified sole callers are connector + test.

## Java 25 modernization opportunities
- The `msg_type` string switch (lines 22-40) is already a `switch` expression — good. The `notify_type` switch (222-241) uses classic `case:` with `break`/`return`; convert to a `switch` expression returning the event.
- Extract a `SenderIdentity(String id, String nickname, String headUrl)` record built once per frame — removes the 6× repeated triple and is a clean record-pattern candidate.
- `mapVoiceRoom`/`mapLiveLink` could be unified via a small parametrized helper (room key + record constructor reference).

## Micronaut built-in opportunities
None — pure function, intentionally not a bean (needs per-connection `selfUserId`). Contrast the realtime `HtNotifyMapper`, which *is* a `@Singleton` (stateless). If IM's self-id were passed per-call instead of per-construction, this too could become a `@Singleton`, aligning the two packages.

## Refactoring recommendations
1. Introduce `SenderIdentity` record + one extractor; reuse across all `mapX`.
2. Collapse `mapVoiceRoom`/`mapLiveLink` into one parametrized method.
3. Delete the duplicate `new_voice_visitor` loop in `mapNotify`; route solely through `mapProfileVisit`.
4. Share a `NotifyType` enum with `HtNotifyMapper` for the common 18/34/35/40/48/53 family.
5. Consider making it a `@Singleton` with `map(root, h, selfUserId)` to match realtime's bean model.
