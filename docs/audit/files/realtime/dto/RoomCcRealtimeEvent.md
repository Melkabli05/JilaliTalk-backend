# RoomCcRealtimeEvent

`src/main/java/com/jilali/realtime/dto/RoomCcRealtimeEvent.java` — `public sealed interface` (Jackson `@JsonTypeInfo` NAME on `type`).

## Purpose
Sealed union of realtime events from the AI-captioning / subtitle channel of the LiveHub socket (`LiveCCNotify` on Android). Constructed by hand in `HtCcNotifyMapper`, serialized to the browser by `RoomSocketController`.

## Public API (variants)
- `SubtitleStart` (type=`subtitle_start`) — cname, speakerId, speakerNickname, speakerHeadUrl, nationality, roleType, id.
- `SubtitleEnd` (`subtitle_end`) — cname.
- `SubtitleDisabled` (`subtitle_disabled`) — cname.
- `SubtitleLine` (`subtitle_line`) — cname, id, text, userId, nickName, headUrl, nationality, roleType, createAt, updateAt, resultId, enabled, expiredAt.
- `SubtitleExperienceActivated` (`subtitle_experience_activated`) — cname, userId.
- `SubtitleExpired` (`subtitle_expired`) — cname, expiredAt (ms epoch).
- `Raw` (`raw`) — originalType, payload (catch-all, e.g. kill-set 5/7/9/10/11).
- `Error` (`error`) — message.

## Coupling
Produced by `HtCcNotifyMapper`; relayed via `HtLiveHubUpstreamConnector` → `RoomEventSource` ccSinks → `RoomSocketController`.

## Notes
CC-channel sibling of `RoomRealtimeEvent`; both are hand-built (never deserialized), sharing one physical socket split by `notify_info` shape. Analogous role to `com.jilali.im.dto.ImRealtimeEvent` as its package's browser-facing sealed event union.
