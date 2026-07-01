# Realtime hardening progress

BASE commit: ee3bd0e (fix(im): mod-invite/stage-invite notify pushes carry no user_id — default to self

## Tasks
- [x] Task 1: 6b87cf8 — Approved Shared WS infrastructure (com.jilali.core.ws)
- [x] Task 2: c334456 — Approved inflate/copyPayload in HtImPacketFramer
- - [x] Task 3: ed6a2a6 — Approved HtImFrameDecoder — pure byte-level decoding
- [x] Task 4: 2619309 — Approved HtImNotifyMapper — pure JSON-to-event mapping
- [x] Task 5: 1d85727 — Approved Rewrite HtImUpstreamConnector (slim + reconnect)
- [x] Task 6: f0a5a45 — Approved Rewrite HtLiveHubUpstreamConnector (safe frames + reconnect)
- [x] Task 7: Approved — Full-repo verification (clean build, 66 tests 0 failures, no dead code, docs committed)



## Final whole-branch review
- Round 1: NEEDS FIXES — C1 (HeartbeatPump executor leak, Critical), I1 (reconnect/close race, Important), M1/M2 (minor hardening)
- Fix commit: 2604262 — fix(realtime,im): close HeartbeatPump executor on teardown, guard reconnect race against intentional close
- Round 2: Approved — no Critical/Important issues remain, 66 tests 0 failures
