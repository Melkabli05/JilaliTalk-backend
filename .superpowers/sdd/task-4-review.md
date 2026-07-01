# Task 4 Review: HtImNotifyMapper

## Verdict: Approved

## Summary
`HtImNotifyMapper.java` matches the brief byte-for-byte (msg_type switch, notify_type switch with
selfUserId fallback for 18/48/34/35/40/53, cname-priority room-share check, profile-visit field
fallbacks, `textOr` helper). Diffing against the pre-move `HtImUpstreamConnector` confirms the
mapping logic (field names, fallback order, defaults) is unchanged — only in-code comments were
dropped, which matches the brief's specified source exactly.

`HtImUpstreamConnector` now delegates both call sites (`handleF2` and `decodeOfflinePacket`) to
`notifyMapper.map(root, h)`; the old `mapPushPayload`/`mapText`/`mapImage`/`mapGift`/`mapIntro`/
`mapNotify`/`mapProfileVisit` methods are fully removed — no leftover/dead mapping code. The
connector's own `textOr` helper remains but is still used by `handleGroupResponse`, which is
unrelated to the extracted notify-mapping logic, so it's not dead code.

`HtImNotifyMapperTest.java` contains all 10 cases from the deleted
`HtImUpstreamConnectorMappingTest` (renamed slightly, e.g.
`notifyType48WithNoUserIdFallsBackToSelf` vs `...FallsBackToConnectorsOwnUid`), with equivalent
assertions. The old test file is confirmed deleted from the working tree.

`./gradlew clean test` — BUILD SUCCESSFUL, all suites pass, including
`TEST-com.jilali.im.HtImNotifyMapperTest.xml` (tests=10, failures=0, errors=0).

No unused imports found in `HtImNotifyMapper.java` (JsonNode, Header, ImRealtimeEvent all used).
No YAGNI or naming issues found — `HtImNotifyMapper` is package-private, pure, matches the
existing `HtNotifyMapper` pattern referenced in its javadoc.

## Critical issues
None.

## Important issues
None.
