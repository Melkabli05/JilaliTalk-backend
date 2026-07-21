# StageController

`src/main/java/com/jilali/stage/StageController.java`

## Purpose
HTTP controller exposing HelloTalk voice-room "stage" mechanics under `/api/stage`: listing stage members, joining/quitting the stage, the raise-hand queue, moderator kick, invites and their approvals, device (mic/cam) control, and issuing a publisher-privilege Agora token. It is a thin proxy: every endpoint delegates one-to-one to `JilaliGateway`.

## Responsibilities
- Map 9 HTTP routes to `JilaliGateway` calls.
- Bind and validate request bodies/query params (`@Valid`, `@NotBlank`).
- Hold the decoded `agoraCipherKey` (from `JilaliProperties`) and pass it to `gateway.publisherToken` so the upstream AES-encrypted token can be decrypted.
- Return `204 No Content` for all mutating actions; return DTOs for the two GET reads.

## Public API
- `StageController(JilaliGateway gateway, JilaliProperties properties)` — constructor injection; eagerly decodes `properties.agoraCipherKey()` to a `byte[]` (US-ASCII).
- `StageListResponse list(int busiType=2, @NotBlank String cname)` — `GET /list`.
- `HttpResponse<Void> join(@Valid StageActionRequest)` — `POST /join`.
- `HttpResponse<Void> quit(@Valid StageActionRequest)` — `POST /quit`.
- `HttpResponse<Void> raiseHand(@Valid RaiseHandRequest)` — `POST /raise-hand`.
- `HttpResponse<Void> kick(@Valid KickRequest)` — `POST /kick`.
- `HttpResponse<Void> raiseHandApproval(@Valid RaiseHandApprovalRequest)` — `POST /raise-hand/approval`.
- `HttpResponse<Void> invite(@Valid StageInviteRequest)` — `POST /invite`.
- `HttpResponse<Void> inviteApproval(@Valid StageInviteApprovalRequest)` — `POST /invite/approval`.
- `HttpResponse<Void> deviceControl(@Valid DeviceControlRequest)` — `POST /device-control`.
- `PublisherTokenResponse publisherToken(@NotBlank String cname)` — `GET /publisher-token`.

## Dependencies
- Imports/injects: `JilaliGateway`, `JilaliProperties`, and all 8 stage DTOs.
- Micronaut: `@Controller`, `@Get/@Post`, `@Body`, `@QueryValue`, `@ExecuteOn(TaskExecutors.BLOCKING)`, `HttpResponse`; jakarta `@Valid`, `@NotBlank`.
- Depends on it: nothing (grep shows only self). It is an edge component invoked by the framework router.
- Downstream: all methods route through `JilaliGateway` → `JilaliClient` (upstream `jlhub`).

## Coupling and cohesion analysis
High cohesion — every method concerns the stage concept. Coupling to `JilaliGateway` is high but intentional (facade). Coupling to 8 distinct DTO types is the notable smell: the controller imports 8 near-identical request shapes for what is essentially one command family. `@ExecuteOn(BLOCKING)` is correct since the gateway makes blocking upstream calls.

## Code smells
- **Shotgun Surgery / DTO proliferation:** 7 of the request bodies are thin "action on a stage" shapes (see `stage-dto.md`). Adding a field common to stage actions (e.g. a trace id) means editing up to 7 classes plus this controller.
- **Primitive Obsession:** `busiType`, `switchType`, `deviceType`, `approvalType`, `inviteType`, `raisehandType`, `role` are all bare `int` discriminators with no enum/range validation (lines 40, and via DTOs).
- Mild **Feature Envy** absent — logic is correctly pushed into the gateway.

## Technical debt
- `busiType` default of `2` (line 40) is a magic number with no named constant or documentation of allowed values.
- No enum typing on any of the `*_type` discriminators; invalid values pass validation and are forwarded upstream untranslated.
- Authorization is entirely delegated upstream (see below); no defense-in-depth in the BFF.

## Duplicate logic
Not within this file, but the controller is the consumer of the duplicated DTO family — see the overlap table in `docs/audit/packages/stage-dto.md`. The 7 mutating handlers are structurally identical (`gateway.x(request); return noContent();`) differing only by DTO type and gateway method.

## Dead or unused code
None. All methods are `@Get`/`@Post` routes — reachable by the router even with no in-repo callers (grep confirms only self-references).

## Java 25 modernization opportunities
- Model the stage-action requests as a **sealed interface** `StageAction permits Join, Quit, RaiseHand, Kick, ...` with **records** for each variant, then dispatch with **pattern-matching `switch`** in the gateway. This replaces 7 near-duplicate DTO classes + 7 handler methods with one polymorphic command.
- If routes must stay distinct (they do, for REST shape), a shared `sealed interface StageMemberAction` carrying `cname()`/`busiType()` as an accessor contract would still let the gateway treat them uniformly.

## Micronaut built-in opportunities
- **micronaut-security:** No `@Secured` anywhere in the codebase (grep confirms). All stage moderation endpoints (`/kick`, `/invite`, `/raise-hand/approval`) are effectively `permitAll`; authorization depends solely on the upstream HelloTalk service validating the propagated session JWT. Adding `@Secured(SecurityRule.IS_AUTHENTICATED)` at minimum, and ideally a room-moderator check, would give defense-in-depth. Actor identity is safe (derived from the session JWT via `SessionAuthClientFilter`, not from the body), but there is no BFF-side guard that the actor moderates `cname`.
- **micronaut-validation** is used but thinly — only `@NotBlank`/`@Positive`. `@Min`/`@Max` on the `int` discriminators would catch bad enum values at the edge.

## Refactoring recommendations
1. Collapse the 7 stage-action DTOs into a sealed command family (Java 25) or a single generic DTO + enum discriminator; keep distinct routes but share the payload contract.
2. Introduce enums for `busiType`, `deviceType`, `switchType`, `approvalType`, `inviteType`, `raisehandType`, `role`.
3. Add `@Secured(IS_AUTHENTICATED)` and, if feasible, a moderator-status pre-check before kick/approval actions.
4. Replace the magic `busiType=2` default with a named constant.
