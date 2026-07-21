# ImSendController

`src/main/java/com/jilali/im/ImSendController.java` — `@Controller("/api/im/messages")`, `@ExecuteOn(TaskExecutors.BLOCKING)`.

## Purpose
HTTP send-side of the IM channel: thin glue letting the Angular frontend fire the binary-WS primitives (read receipt, typing indicator, 1:1 send) that the legacy JS client built directly. Builds the correct binary packet and hands it to `ImEventSource.sendOutbound`.

## Responsibilities
- Expose `POST /{userId}/read`, `/{userId}/typing`, `/{userId}/send`.
- Re-derive the caller uid from the live auth token (`callerUserId`).
- Shape outbound JSON for six message kinds (`buildSendMessageJson`).
- Define the request DTOs.

## Public API
- `ImSendController(AuthTokenHolder authToken, ObjectMapper om, ImEventSource imEventSource)`.
- `HttpResponse<Void> read(long userId, ReadReceiptRequest body)`.
- `HttpResponse<Void> typing(long userId, TypingRequest body)`.
- `HttpResponse<Void> send(long userId, SendMessageRequest body)`.
- `static String buildSendMessageJson(SendMessageRequest body, long toId)` (package-visible; tested).
- Nested `@Serdeable` records: `ReadReceiptRequest(@NotBlank String msgId, Integer chatType)`, `TypingRequest(boolean typing)`, `IntroductionRequest(long userId, String nickname, String headUrl, String sex, Integer age, String nationality, String bio)`, `GiftRequest(long id, String name, Map<String,String> multiName, String smallPic, String bigPic, String animUrl, long diamondVal, int giftType)`, `SendMessageRequest(...15 components...)`.
- Private `callerUserId()`.

## Dependencies
- Injected: `AuthTokenHolder`, `ObjectMapper`, `ImEventSource`.
- Uses `HtImPacketFramer` (buildReadReceipt/buildTypingIndicator/buildPrivateMessagePacket), `UidExtractor`.
- Depended on by: HTTP clients only (framework-invoked). `buildSendMessageJson` used by `ImSendControllerTest`. Grep-verified.

## Coupling and cohesion analysis
Two responsibilities: (1) HTTP endpoint glue, (2) legacy JSON body-shaping (`buildSendMessageJson`, ~115 lines). The body-shaper is the bulk of the class and is really a protocol-serialization concern misplaced in a controller. Coupling to `HtImPacketFramer` and `ImEventSource` is appropriate for the glue role.

## Code smells
- **Long Method**: `buildSendMessageJson` (lines 112-226) — a ~115-line method with a nested `switch` and per-kind `LinkedHashMap` assembly. Clear Long Method.
- **Feature Envy / Primitive Obsession**: builds deeply nested `Map<String,Object>` envelopes by hand instead of typed records; magic field names and constants (`version=394024`, `"Chat List"`, zeros) scattered inline.
- **`new ObjectMapper()`** created twice inside `buildSendMessageJson` (lines 149, 225) instead of reusing the injected `om` — wasteful and a subtle inconsistency (the injected Micronaut-configured mapper is bypassed). Because the method is `static`, it can't see `om`. Notable smell.
- **Data Clump**: `from_id`/`to_id`/`msg_id`/`from_nickname` recur across kinds.
- `SendMessageRequest` is a 15-field "kitchen-sink" record with kind-specific nullable fields — a mild **God DTO**; a sealed hierarchy per kind would be cleaner.

## Technical debt
- Hand-built JSON maps mirror reverse-engineered APK shapes; each field carries a smali citation — fragile and hard to test exhaustively.
- The `static` method forcing `new ObjectMapper()` is a direct debt: two throwaway mappers per send.
- `voice_room`/`live_link` pass `Object roomData` straight through — untyped passthrough.

## Duplicate logic
No realtime counterpart — room actions go through REST `RoomController`/`JilaliClient`, not a WS send path (documented in `RoomSocketController`: "Frontend actions go through REST controllers, never here"). So the IM send-over-WS path is an IM-only design. The `buildSendMessageJson` gift/text/image shaping loosely mirrors the *inbound* `HtImNotifyMapper` shapes (send_gift, text, image, introduction, voice_room, live_link) — the same six kinds, encoded outbound here and decoded inbound there. Keeping the encode/decode field lists in sync is a latent Shotgun Surgery pair.

## Dead or unused code
None. Endpoints framework-invoked; `buildSendMessageJson` tested. Record accessors used by Jackson/Serde. Grep-verified.

## Java 25 modernization opportunities
- The `switch (body.kind())` (lines 156-209) is a string switch — with a sealed `SendPayload` hierarchy (one record per kind) it becomes an exhaustive **switch on a sealed interface with record patterns**, eliminating the null-field kitchen-sink record and the `IOException`-on-unknown-kind default.
- Replace hand-built `Map<String,Object>` envelopes with `@Serdeable record`s serialized via the injected mapper.
- Making the method non-static lets it use the injected `ObjectMapper`.

## Micronaut built-in opportunities
- Already idiomatic Micronaut controller (`@Controller`, `@Post`, `@Body`, `@PathVariable`, `@Serdeable`, `@ExecuteOn(BLOCKING)`, bean validation `@NotBlank`).
- Use the **injected Micronaut `ObjectMapper`/`JsonMapper`** instead of `new ObjectMapper()` — Micronaut Serde is the configured serializer; bypassing it risks config drift.
- Bean validation could cover more (e.g. `@NotNull` on kind, `@Pattern` on allowed kinds) to move the `IOException`-on-bad-kind into a 400 automatically.

## Refactoring recommendations
1. Extract `buildSendMessageJson` into a dedicated `ImOutboundMessageEncoder` bean using the injected `ObjectMapper` and typed `@Serdeable` records.
2. Model `SendMessageRequest` as a sealed hierarchy (TextSend/ImageSend/GiftSend/IntroSend/RoomShareSend) → exhaustive switch, no nullable clumps.
3. Name the magic constants (`version`, `source`, notify flags).
4. Validate `kind` via bean validation so unknown kinds return 400, not 500.
