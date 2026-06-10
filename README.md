# JilaliTalk BFF

A Micronaut 5 / Java 25 **backend-for-frontend** that fronts the HelloTalk Jilali API. Your
frontend talks to this service; this service proxies Jilali, forwarding auth/tracing headers,
normalizing Jilali's `{code, msg, data}` envelope into clean HTTP semantics, and exposing a
tidy REST surface.

This is **not** a reimplementation of Jilali. There is no database, no business logic for room
heat/state — those live upstream. The job here is faithful, well-shaped proxying.

## Architecture

```
Frontend ──HTTP──> RoomController / StageController / …  (your API surface)
                          │
                          ▼
                   JilaliGateway      (unwraps envelopes, raises typed errors — the ONE boundary)
                          │
                          ▼
                   JilaliClient       (@Client declarative HTTP, one upstream)
                          │  + HeaderPropagationFilter (@ClientFilter forwards auth/x-ht-*/x-b3-*)
                          ▼
                       Jilali
```

Feature-first packages (`room`, `stage`, `manager`, `comment`, `user`), each with a controller and
DTO records. Cross-cutting concerns live in `core`. One shared `client` package.

### Deliberate decisions

- **No repositories / no persistence.** A gateway has no data to own; the `@Client` is the
  data-access layer. Adding JPA here would be overengineering.
- **No per-feature client.** One upstream, one base URL, one auth scheme → one `@Client`.
- **No pass-through services.** Discovery/info endpoints go controller → gateway directly. A
  service that only delegates is ceremony. Envelope-unwrapping lives in `JilaliGateway`, so
  nothing above it ever sees Jilali's `code`/`msg`/`data`.
- **Two envelopes, kept apart.** Listing endpoints return bare `{items:[...]}`; action/info
  endpoints return `{code,msg,data}`. Both are modeled honestly. A non-zero upstream `code`
  (e.g. `190032 VoiceManagerUpdateFailed`, `100002 bad request`) becomes a `JilaliException`
  mapped to a proper HTTP status + RFC 9457 `application/problem+json` body.
- **`busi_type` stays an `int` on the wire.** It reads naturally as an enum, but Micronaut Serde
  has a documented history of mishandling `@JsonValue`/`@JsonCreator` on enums with numeric codes,
  so the wire format is a plain int validated by bean validation. (An earlier draft used an enum;
  it was removed rather than risk runtime deserialization failures.)
- **The `bin/cc2018` profile endpoint** (`/api/users/{id}/profile`) is binary, not JSON. It is
  forwarded as raw bytes with the upstream content type, not deserialized.

### Java 25 features actually used

Records for every DTO and the response envelope; pattern-matching `switch` in
`JilaliException.fromCode` and `BusiType`-style code mapping; virtual threads for the blocking
proxy hops (`micronaut.executors.blocking.type: virtual`). Structured concurrency was considered
and **not** used: these slices are single downstream calls with nothing to parallelize, so it
would have been cargo-culting.

## Endpoints implemented

All endpoints across the 8 in-scope sections (Discovery, Info, Lifecycle, User Actions,
Status & Profile, Stage, Manager, Comments & Captions) are now implemented — 46 upstream calls,
one client method each, wired through the gateway to a controller route.

The deferred sections (Games & Voting, Gifts & Products, Exposure & Promotion, Host Dashboard,
Whiteboard & Screen Share, Sign-in & Tasks, Miscellaneous) remain unimplemented and follow the
identical pattern when added.

### Notable response-shape handling
- `language_group/{voice,live}` return a bare JSON **array**, not an envelope or `{items}` wrapper.
- `user_started_channel` returns a literal `null` body (handled with `@Nullable`).
- `category_topic_list`, `comment`, `comment_notify`, `batch_query_channel`, `user/list`,
  `user/status_list`, `user_record_live` use `{items}`/`{list}` wrappers, not the envelope.
- `voice_room_info`, `live_room_info`, `channel_basic_info`, the end-page and config endpoints are
  large/loosely-specified blobs, modeled as `Map<String,Object>` rather than over-transcribed.
- `user/profile` is binary (`bin/cc2018`), forwarded as raw bytes.

### Route-precedence note
A few info reads sit under the same prefix as named actions (e.g. `/api/rooms/voice/{cname}` and
`/api/rooms/voice/recommend`). Micronaut resolves static path segments ahead of path variables, so
`recommend` is not captured as a `cname`. This is correct under Micronaut's router but relies on
that precedence; if a real `cname` could ever equal a literal route name, disambiguate the paths.


## Configuration

`LIVEHUB_BASE_URL` (default `https://api-global.hellotalk8.com`). Forwarded headers are listed in
`application.yml` under `jilalk.forwarded-headers` and bound via `JilaliProperties`.

## ⚠️ Verification status — read this

This code was written against Micronaut 5.0 (GA 2026-05-20, Java 25 baseline) but **was not
compiled or run** in the authoring environment (JDK 21, no Gradle, restricted network). Treat it
as a reviewed scaffold, not a green build. Specifically:

1. **Verified against current docs:** the `@ClientFilter(serviceId=…)` + `@RequestFilter`
   pattern; that Micronaut has no built-in `ProblemDetail` type (hence the hand-rolled `ApiError`);
   the Serde enum-annotation limitation (hence `int` for `busi_type`).
2. **Not independently verified — check on first build:**
   - `ServerRequestContext.currentRequest()` returning `Optional` (long-stable, low risk).
   - That `@Get(processes = APPLICATION_OCTET_STREAM)` + `byte[]` return is the correct way to
     receive the binary profile in Micronaut 5; the `bin/cc2018` content type may need an explicit
     `@Consumes`/codec registration if Micronaut rejects the non-standard media type.
   - Plugin versions in `build.gradle` (`io.micronaut.application`, shadow) — pin to whatever the
     Micronaut 5 BOM expects.
3. **Run `./gradlew compileJava` first.** Any failures will be import/annotation specifics, not
   structural — the layering and contracts are sound.

## Build & run

```bash
./gradlew run          # virtual-thread blocking executor enabled via jvmArgs
./gradlew test
```

## Verification actually performed (authoring environment: JRE only, no javac/Gradle)

The following static checks were run and **passed**:
- Every internal type reference (`com.example.jilalk.*`) resolves to a real source file.
- Full call-chain wiring: every controller→`JilaliGateway` call has a matching gateway method,
  and every gateway→`JilaliClient` call has a matching client method.
- All annotation imports present where used (`@JsonProperty`, `@Nullable`, `@Serdeable`,
  `jakarta.validation.*`).
- Brace/parenthesis balance across all 36 source files.

**No compilation was performed** — the environment has no JDK compiler or build tool. The first
thing to run locally is `./gradlew compileJava`. There is no Gradle wrapper jar in this archive
(can't generate one without Gradle present); run `gradle wrapper --gradle-version 9.5` once, or
open in an IDE with the Micronaut plugin.
