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
- [ ] While a participant is in a dungeon, run `/spawn` and confirm EssentialsX can change their
  world, the participant is removed and remains at the requested destination, and the command is
  not cancelled by DungeonCrawlers. Reconnect once and confirm the old snapshot is not applied.
  As an admin, teleport into the dungeon and back to another world; confirm cross-world teleports
  are not blocked while same-world dungeon bounds protection remains active.

## Automated coverage

The phase tests cover simultaneous secret discovery, lethal transitions and wipe, disconnect-to-ghost
state, logout/reconnect after wipe, exact preparation and active-run warning boundaries, portal
ownership, recovered reward-session initialization, world-change leave handling, and cross-world
teleport bypass. Existing reservation, door, reward-claim, callback-freeze, and cleanup tests remain
part of the full suite.

## Recorded evidence

- 2026-09-05: Corrected preparation and active-run warning windows to emit one minute before their
  deadlines. Active-run disconnects now record one death and transition an `ALIVE` participant to
  offline `GHOST` while preserving the revive deadline across reconnect. Focused and full Java 21
  tests passed for both fixes.
- 2026-09-05: Deterministic Phase 15 race tests passed for secret discovery, party lethal/wipe
  transitions, logout/reconnect after wipe, portal ownership, and recovered reward sessions. The
  full Java test suite passed on the Phase 15 branch. Live two-client checks remain open.
- 2026-09-05: Java 21 clean build, full tests, and external-plugin shading verification passed for
  the warning and disconnect fixes. JAR SHA-256 was
  `b407410ca1685f678eea42ed5421b218ce4ba1f5c89a597e1735c529713ba3b9`; it uploaded to
  server `fa696721`, and `cc reload all` completed. Final operations reported zero active instances,
  reservations, occupied slots, cleanup alerts, and repository work. Config validation passed with
  hash `b6d42cd6079e48e58af252c5e8ce51587e044e3b5d58bc42d481752379b9ee0d`, and the plugin list
  showed DungeonCrawlers enabled. The reload also emitted an unrelated EssentialsX/Paper command-tree
  stack trace during PlugMan re-registration; the server settled and DungeonCrawlers diagnostics
  remained clean.
- 2026-09-05: Cross-world leave handling was deployed in JAR commit `3f3ab22` with SHA-256
  `73466111208a5792b812d55006e18d021d1a19cdf47cc246a15139de7d2a0ebe`. `cc reload all`
  reloaded DungeonCrawlers successfully; operations, config validation, and repository diagnostics
  all passed with no active instances, blockers, queued work, or in-flight work. The live `/spawn`
  and administrator teleport checks remain open.
- 2026-09-05: Live admin smoke test on disposable instance `62a58935-72e5-40a6-a92d-276de2d51425`
  teleported `LidanTheGamer` into `minecraft:dungeon_instances`, accepted `/spawn`, and confirmed
  the player changed to `minecraft:deepmines`. The debug instance cleanup completed successfully;
  the participant destination-preserving leave check still requires the physical start-door flow.
- 2026-09-05: Destination-preserving world-change exit was committed in `1688d85`. Java 21 clean
  build, full tests, and external-plugin shading verification passed. JAR SHA-256 was
  `07a22e6039a190b9198aa6b571fe2a56fda312879861595b416bf4eef4acd21b`; it uploaded to server
  `fa696721`, and `cc reload all` completed. Post-reload operations reported zero active instances,
  reservations, occupied slots, cleanup failures, deadline alerts, late callbacks, queued work,
  and in-flight repository work. Configuration validation passed with hash
  `b6d42cd6079e48e58af252c5e8ce51587e044e3b5d58bc42d481752379b9ee0d`. The physical participant
  `/spawn` plus reconnect check remains open.
