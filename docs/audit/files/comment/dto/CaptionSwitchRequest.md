# CaptionSwitchRequest.java

`src/main/java/com/jilali/comment/dto/CaptionSwitchRequest.java`

## Purpose
Inbound request body for `POST /api/captions/switch`. Toggles captions on/off for a `(cname, busiType)` pair.

## Public API
Record `CaptionSwitchRequest(@JsonProperty("busi_type") int busiType, @NotBlank String cname, @JsonProperty("caption_status") int captionStatus, @JsonProperty("is_try_out") boolean isTryOut)`:
- `busiType` — upstream `busi_type`.
- `cname` — room id, `@NotBlank`.
- `captionStatus` — upstream `caption_status`.
- `isTryOut` — upstream `is_try_out`.

## Coupling
- Imported by: `CommentController.switchCaption` (`@Body @Valid`) only.
- Imports: `com.fasterxml.jackson.annotation.JsonProperty`, `io.micronaut.serde.annotation.Serdeable`, `jakarta.validation.constraints.NotBlank`.

## Notes
- Snake_case `@JsonProperty` renames on this request DTO are at odds with `BffSendCommentRequest` (camelCase) and `SendCommentResponse` (plain) — see the cross-package snake_case/camelCase naming inconsistency called out in `SendCommentResponse`.
- Thin pass-through shape; no business logic.
