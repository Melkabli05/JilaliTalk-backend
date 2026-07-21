# RoomLevelConfigResponse.java

`src/main/java/com/jilali/room/dto/RoomLevelConfigResponse.java`

## Purpose
Room-level configuration list (XP thresholds, rewards, equity perks per room level) for the room level panel.

## Responsibilities
Model the `voice/room_level/config` payload and its nested reward/equity/exp sub-objects.

## Public API (record + nested records)
- `RoomLevelConfigResponse(@Nullable List<RoomLevelItem> items)`
- `RoomLevelItem` — `roomLevel:int`, `experience:int`, `bigLevel:int`, `bigLevelNew:int`, `roomStyleUrl:@Nullable String`, `levelName:@Nullable String`, `levelUpExperience:int`, `rewards:@Nullable List<RewardItem>`, `equitys:@Nullable List<Equity>`, `exp:@Nullable ExpData`.
- `ExpData` — `exp:int`, `maxExp:int`.
- `RewardItem` — `id:int`, `giftId:int`, `type:int`, `cardType:int`, `name:String`, `number:int`, `icon:String`, `multiName:@Nullable String`.
- `Equity` — 24 fields (id, name, multiName, icon, multiContent, status, sort, createdAt, updatedAt, equityType, number, outsideIcon, giftId, giftType, thum, cardType, thumDark, iconDark, outsideIconDark, labelFontColor, thumV2, iconV2, roomName).

## Dependencies
- Imports `@JsonProperty`, `@Nullable`, `@Serdeable`.
- Produced by `JilaliClient` (room level config). **Composed by `signin.dto.RoomLevelBundleResponse`** as its `config` field (cross-package reuse via composition — good).

## Coupling and cohesion analysis
Cohesive around room-level config. `Equity`'s 24 fields make it a large record. Reused across packages via composition (`RoomLevelBundleResponse`), which is the desirable pattern.

## Code smells
- **Large record / Data Clump**: `Equity` (24 fields) with many parallel dark/v2 icon variants — could group the icon variants.
- `status`/`type`/`equityType`/`cardType`/`giftType` untyped int codes (**Primitive Obsession**).

## Technical debt
- **Cross-package field duplication (confirmed)**: this file's nested `RewardItem` is field-for-field identical to `com.jilali.signin.dto.RewardItem` (id, giftId, type, cardType, name, number, icon, multiName) — used by `signin.dto.RoomLevelRewardResponse`. Two identical `RewardItem` records in two packages.

## Duplicate logic
- `room.dto.RoomLevelConfigResponse.RewardItem` == `signin.dto.RewardItem` (exact field set; only `multiName` nullability annotation differs). Prime cross-package consolidation target.

## Dead or unused code
None (framework-serialized).

## Refactoring recommendations
- Promote a single shared `RewardItem` (in a common/shared DTO location) used by both `room.dto.RoomLevelConfigResponse` and `signin.dto.RoomLevelRewardResponse`.
- Group `Equity`'s icon variants (`icon`/`iconDark`/`iconV2`, `thum`/`thumDark`/`thumV2`, `outsideIcon`/`outsideIconDark`) into a small nested value type.

## Cross-reference
`signin.dto.RoomLevelBundleResponse` (composes this), `signin.dto.RoomLevelRewardResponse` + `signin.dto.RewardItem` (duplicate reward shape).
