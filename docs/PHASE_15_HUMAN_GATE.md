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
- [ ] Disconnect one ALIVE participant from a RUNNING run. Confirm the participant becomes an
  offline `GHOST`, the death count increases exactly once, the 60-second revive deadline is set,
  and reconnect preserves the deadline without resetting it.
- [ ] Coordinate both players logging out, then rejoining. Confirm a wiped run cannot be resurrected
  by either reconnect and no late ghost revive occurs.
- [ ] Have both players enter the same boss portal at the same time. Confirm exactly one countdown
  owner, one countdown callback, and one boss start; the other entry must not create a second
  countdown.
- [ ] Reopen or reconnect reward views at the same time. Confirm each participant keeps the same
  session and rolled offers, with no reroll or duplicate reward entitlement.
- [ ] Repeat the Phase 9 countdown cleanup check and confirm the portal callback and countdown owner
  are removed exactly once.
- [ ] Start a fresh run and confirm the preparation and active-run warning messages appear one minute
  before their respective deadlines, not one minute after the run starts.

## Automated coverage

The phase tests cover simultaneous secret discovery, lethal transitions and wipe, disconnect-to-ghost
state, logout/reconnect after wipe, exact preparation and active-run warning boundaries, portal
ownership, and recovered reward-session initialization. Existing reservation, door, reward-claim,
callback-freeze, and cleanup tests remain part of the full suite.

## Recorded evidence

- 2026-09-05: Deterministic Phase 15 race tests passed for secret discovery, party lethal/wipe
  transitions, logout/reconnect after wipe, portal ownership, and recovered reward sessions. The
  full Java test suite passed on the Phase 15 branch. Live two-client checks and the deploy
  checkpoint remain open.
- 2026-09-05: Java 21 clean build, full tests, and external-plugin shading verification passed. JAR
  SHA-256 was `fdf8628e8f9f5267f80347133b690a0905fe618a760f8a2e02438b9dcd2607ea`; it uploaded to
  server `fa696721`, and `cc reload all` completed. Final operations reported zero active instances,
  reservations, occupied slots, cleanup alerts, and repository work. Config validation passed with
  hash `b6d42cd6079e48e58af252c5e8ce51587e044e3b5d58bc42d481752379b9ee0d`, and the plugin list
  showed DungeonCrawlers enabled. The reload also emitted an unrelated EssentialsX/Paper command-tree
  stack trace during PlugMan re-registration; the server settled and DungeonCrawlers diagnostics
  remained clean.
