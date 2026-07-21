# VipExperienceCardRecordsResponse

## Purpose
Response body for `GET /api/vip-experience-card/records` — wraps the upstream `Content` envelope and exposes a null-safe `cards()` accessor that defaults to an empty list when the user owns no cards.

## Public API
Record `VipExperienceCardRecordsResponse`:
- `String id` — upstream recordset id / cursor.
- `@JsonProperty("user_id") long userId` — echoed owning user id.
- `Content content` — envelope; may itself be `null`.
- `@JsonProperty("vip_status") int vipStatus` — aggregated VIP status flag.

Methods:
- `List<VipExperienceCard> cards()` — returns `Content.cards` or `List.of()` when either is `null` (upstream sends `"cards": null` rather than `[]` for the empty case).

Nested record `Content`:
- `@Nullable List<VipExperienceCard> cards` — the user's owned cards.

## Coupling
Serialized via Micronaut Serde; `cards()` is the ergonomic entry point used by callers.

## Notes
Clean handling of upstream's `null`-vs-`[]` ambiguity at the boundary — the rest of the codebase can treat it as a non-null list. Inner `Content` record exists only to match upstream's wrapping envelope; if upstream ever flattens it, this whole class collapses to four direct fields. Note `id` is a `String` here (cursor-like) while the cards inside use `long id` — heterogeneous by design.
