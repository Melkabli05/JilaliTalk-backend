# TranslateUpstreamHeaders

`src/main/java/com/jilali/translate/dto/TranslateUpstreamHeaders.java`

## Purpose
Groups the complete translator-specific HTTP header set into a typed value. Its factory combines one ephemeral Curve25519 session, caller identity, authorization, and a fixed HelloTalk client persona.

## Public API
Canonical constructor/accessors for `translatePub: String`, `translateUid: String`, `translateOs: String`, `translateBuild: String`, `translateVersion: String`, `userAgent: String`, `authorization: String`, and `accept: String`.

- `static TranslateUpstreamHeaders forSession(Curve25519Session session, long uid, AuthTokenHolder authToken)` — creates all eight values; publishes the session header, stringifies the UID, prefixes the token with `Bearer`, and requests `text/event-stream`.

## Coupling
Constructed by `TranslateService` and consumed field-by-field by `HtTranslateClient`. The factory directly depends on `Curve25519Session` and `AuthTokenHolder`.

## Notes
- The iOS persona, build `70`, version `6.3.0`, User-Agent, and SSE accept value are hardcoded at `TranslateUpstreamHeaders.java:34-39`. These non-secret protocol constants can drift as the upstream app evolves.
- `forSession` performs no null/blank validation (`TranslateUpstreamHeaders.java:46-57`). A blank token becomes `Authorization: Bearer `, and the caller’s UID/token consistency relies entirely on `TranslateService`.
- `translatePub` exposes public key material, not the shared secret; the secret remains in the service/codec path.
