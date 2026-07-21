# VoiceSignPanelResponse

## Purpose
Response for `GET /api/signin/panel` — the full voice sign-in panel payload (calendar + status flags).

## Public API
Record `VoiceSignPanelResponse`:
- `@JsonProperty("sign_list") List<SignItem> signList` — the per-day sign-in entries.
- `@JsonProperty("to_day_signed") boolean toDaySigned` — whether today's slot has already been claimed.
- `@JsonProperty("consecutive_days") int consecutiveDays` — current signed-streak length.

## Coupling
Holds `List<SignItem>` from this package; surfaced by `SigninController.panel`.

## Notes
Three-field wrapper; no nullable fields — upstream is expected to always provide all three.