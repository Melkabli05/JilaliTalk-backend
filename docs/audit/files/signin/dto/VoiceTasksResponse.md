# VoiceTasksResponse

## Purpose
Response for `GET /api/signin/tasks` — wraps the upstream voice-tasks list as a flexible `List<Map<String, Object>>` to avoid an inner DTO while keeping the wrapper `@Serdeable`.

## Public API
Record `VoiceTasksResponse`:
- `@Nullable List<Map<String, Object>> items` — raw upstream task entries.

Convenience accessor:
- `List<Map<String, Object>> items()` — returns `items` or `List.of()` when null, so consumers never see null.

## Coupling
Constructed explicitly in `SigninController.tasks` by casting the unwrapped map; no inner DTO coupling.

## Notes
The null-safe accessor is the only reason this record is not just a one-liner — downstream code can iterate without an explicit null check.