# RoomRealtimeEvent

`src/main/java/com/jilali/realtime/dto/RoomRealtimeEvent.java` — `public sealed interface` (Jackson `@JsonTypeInfo` NAME on `type`).

## Purpose
Sealed union of all room-channel realtime events. Records use camelCase and are built by hand in `HtNotifyMapper` (never deserialized), then serialized to the browser by `RoomSocketController`.

## Public API (variants, by discriminator)
- Lifecycle/presence: `ConnectionState`, `UserJoin`, `UserQuit`, `StageJoin`(wraps `StageUserEvent`), `StageQuit`, `StageRaiseHand`, `StageInvite`, `StageDeviceControl`, `StageKick`, `RoomKick`.
- Mic/mod: `MicOpened`, `MicClosed`, `ModInvite`, `ModAccepted`, `ModRemoved`, `ModUnmuted`.
- Content: `Comment`(wraps `CommentEvent`+`ReplyInfoEvent`), `Gift`(list of `GiftEvent`), `Follow`, `LuckyBag`, `RoomTopicShare`, `RoomPropsApplied`, `WhiteboardActivated/Deactivated`.
- Gift/reward entities: `GiftWish`, `RewardInfo`(+`Reward`), `PurchaseVip`, `ReceiveVipGifts`, `TreasureReward`(+`CampResult`).
- `FgUpgradeAward` (wire key `typ` → `awardType` to avoid colliding with `type` discriminator).
- Fallbacks: `Raw`, `Error`. Non-variant nested records: `StageUserEvent`, `CommentEvent`, `ReplyInfoEvent`, `GiftEvent`, `Reward`, `CampResult`.

## Coupling
Produced by `HtNotifyMapper`; relayed via `HtLiveHubUpstreamConnector` → `RoomEventSource` → `RoomSocketController`.

## Notes
Room-channel sibling of `RoomCcRealtimeEvent` (one socket, split by shape). Analogous to `com.jilali.im.dto.ImRealtimeEvent` — each package's hand-built, browser-facing sealed event union.
