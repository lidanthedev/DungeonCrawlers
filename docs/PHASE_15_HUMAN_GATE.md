# Phase 15 Human Gate - Concurrent player transitions

Status: IN PROGRESS

Phase 15 closes the remaining concurrency checks carried forward from the earlier gates. The
player-facing state machines must accept one ordered result when two clients act at the same time,
keep party-wide effects one-shot, and preserve stable reward sessions across reconnect or preview
races.

The Phase 14 backup gate remains independently in progress; this document does not claim that
backup gate passed or change its retain-until-replacement requirement.

## Required checks

- [ ] Run the Java 21 clean build, full tests, deploy the JAR, and run `cc reload all` on server
  `fa696721`.
- [ ] With two players in the same run, trigger the same secret at the same time. Confirm exactly
  one discovery and blessing award, while the other player receives `Secret already found`; no
  duplicate blessing or discovery is allowed.
- [ ] With two players in the same RUNNING run, trigger lethal damage at the same time. Confirm
  each player transitions at most once, deaths do not increment twice, and the run emits one wipe
  when no online active alive player remains.
- [ ] Coordinate both players logging out, then rejoining. Confirm a wiped run cannot be resurrected
  by either reconnect and no late ghost revive occurs.
- [ ] Have both players enter the same boss portal at the same time. Confirm exactly one countdown
  owner, one countdown callback, and one boss start; the other entry must not create a second
  countdown.
- [ ] Reopen or reconnect reward views at the same time. Confirm each participant keeps the same
  session and rolled offers, with no reroll or duplicate reward entitlement.
- [ ] Repeat the Phase 9 countdown cleanup check and confirm the portal callback and countdown owner
  are removed exactly once.

## Automated coverage

The phase tests cover simultaneous secret discovery, lethal transitions and wipe, logout/reconnect
after wipe, portal ownership, and recovered reward-session initialization. Existing reservation,
door, reward-claim, callback-freeze, and cleanup tests remain part of the full suite.

## Recorded evidence

- 2026-09-05: Deterministic Phase 15 race tests passed for secret discovery, party lethal/wipe
  transitions, logout/reconnect after wipe, portal ownership, and recovered reward sessions. The
  full Java test suite passed on the Phase 15 branch. Live two-client checks and the deploy
  checkpoint remain open.
