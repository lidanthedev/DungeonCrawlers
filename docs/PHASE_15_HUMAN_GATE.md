# Phase 15 Human Gate - Concurrent player transitions

Status: IN PROGRESS

Phase 15 closes the remaining concurrency checks carried forward from the earlier gates. The
player-facing state machines must accept one ordered result when two clients act at the same time,
keep party-wide effects one-shot, and preserve stable reward sessions across reconnect or preview
races.

The Phase 14 backup gate remains independently in progress; this document does not claim that
backup gate passed or change its retain-until-replacement requirement.

The 2026-09-06 two-client check supplied partial live evidence and exposed a stale ghost-visibility
case after a wiped run. That case is fixed and deployed below. Checks that were not exercised remain
open.

## Test controls

The active-run deadline can be checked without waiting an hour. After opening the start door, run
`/dungeon instance advance <instance-id> 3540`; the run should emit its one-minute warning. Advancing
the same instance another 60 seconds forces the deadline, and advancing 10 more seconds completes
failed-run cleanup. `/dungeon instance time <instance-id>` shows the real time, instance scheduler time,
global speed, and instance advance offset. Instance advances affect only the selected active instance
and are discarded when it is cleaned up.
For a real-time check, run `/dungeon tick speed-test 60` after opening the door: one real second advances
about one dungeon minute, so the warning should arrive after roughly 59 seconds. Run
`/dungeon tick speed-reset-test` after the check. The speed control applies to all central DungeonCrawlers
timers on this server and is intended for disposable admin tests.

## Required checks

- [x] Run the Java 21 clean build, full tests, deploy the JAR, and run `cc reload all` on server
  `fa696721`.
- [x] With two players in the same run, trigger the same secret at the same time. Confirm exactly
  one discovery and blessing award, while the other player receives `Secret already found`; no
  duplicate blessing or discovery is allowed.
- [x] With two players in the same RUNNING run, trigger lethal damage at the same time. Confirm
  each player transitions at most once, deaths do not increment twice, and the run emits one wipe
  when no online active alive player remains.
- [x] Disconnect one ALIVE participant from a RUNNING run. Confirm the participant becomes an
  offline `GHOST`, the death count increases exactly once, the 60-second revive deadline is set,
  and reconnect preserves the deadline without resetting it. The live check on instance
  `9a0f3f5e-ba12-4aa3-a2f0-a503f95dd340` reported `state=GHOST deaths=1` with a `reviveAt` and
  reconnected successfully.
- [x] Coordinate both players logging out, then rejoining. Confirm the last disconnect marks the run
  failed and cleans the dungeon immediately, without waiting for the failed-reading period. Confirm a
  wiped run cannot be resurrected by either reconnect, no late ghost revive occurs, and the retained
  snapshot restores each player once, visibly, without invulnerability or non-collision. Repeat this
  after commits `db49f05` and `3a8af6a`. The live check passed: the last disconnect failed and cleaned
  the run immediately, and reconnect did not resurrect it.
- [x] Have both players enter the same boss portal at the same time. Confirm exactly one countdown
  owner, one countdown callback, and one boss start; the other entry must not create a second
  countdown.
- [ ] Close and reopen the reward GUI, then have each participant disconnect and rejoin before running
  `/dungeon reward open <instance-id>` again. Compare `/dungeon reward info <instance-id>` before and
  after. Each participant must keep the same rolled offers and session, with no reroll or duplicate
  reward entitlement. The previous check exposed a bug where disconnecting while viewing rewards
  incorrectly changed the participant to `GHOST`; commit `84b0e3f` fixes it. The live check on instance
  `198d96e0-6c01-4bfb-8097-6a0eeaaa1c9f` verified this for `LidanTheGamer_`; repeat the disconnect and
  reconnect for `LidanTheGamer` before marking the item passed. After revival, reward selection still
  worked.
- [x] Repeat the Phase 9 cleanup check. Run `/dungeon portal start <instance-id>`, verify
  `/dungeon portal status <instance-id>` shows `COUNTDOWN`, run `/dungeon portal abort <instance-id>`,
  and verify the next status has no active owner. Repeat with `/dungeon boss cleanup <instance-id>`
  during an active boss, then check `dungeon operations` and `dungeon repository` for no leftovers.
  The live portal and boss-cleanup verification passed.
- [x] Start a fresh run without selecting a class or opening the door and confirm the preparation
  warning appears one minute before its deadline. The live check produced `Class selection closes in
  1 minute.` after four minutes, then correctly kicked the participant when the deadline expired.
- [x] Continue a run after opening the door and confirm the active-run warning appears one minute
  before its deadline, not one minute after the run starts. The user confirmed the 600x live check
  emitted the warning, reached `Dungeon failed: run time limit reached.`, and completed failed-run
  cleanup with the player restored.
- [x] While a participant is in a dungeon, run `/spawn` and confirm EssentialsX can change their
  world, the participant is removed and remains at the requested destination, and the command is
  not cancelled by DungeonCrawlers. The live `/spawn` check passed. As an admin, teleport into the
  dungeon and back to another world; confirm cross-world teleports are not blocked while same-world
  dungeon bounds protection remains active.
- [x] After the participant `/spawn` check, reconnect once and confirm the old snapshot is not
  applied. The live check confirmed the participant stayed at the `/spawn` destination.

## Automated coverage

The phase tests cover simultaneous secret discovery, lethal transitions and wipe, disconnect-to-ghost
state, logout/reconnect after wipe, exact preparation and active-run warning boundaries, portal
ownership, recovered reward-session initialization, world-change leave handling, cross-world
teleport bypass, bounded ghost invisibility, wiped-reconnect ghost suppression, and ghost cleanup
after player restoration. The all-participants-offline wipe path also uses immediate cleanup while
retaining reconnect snapshots. Existing reservation, door, reward-claim, callback-freeze, and cleanup
tests remain part of the full suite.

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
- 2026-09-06: User live feedback confirmed one secret award for simultaneous secret clicks,
  simultaneous lethal handling, disconnect/reconnect ghost behavior, one boss-portal countdown,
  the preparation warning, and `/spawn`. The death count was not read during the check. The same
  feedback exposed a player remaining invisible after a wiped run restored them; reward-view
  reconnect, Phase 9 cleanup, and the active-run warning were not exercised.
- 2026-09-06: Commit `c599156` bounds ghost invisibility to the remaining revive duration, prevents
  a failed reconnect into a wiped run from scheduling ghost presentation, and clears ghost state on
  every successful snapshot restore or world-change exit. Focused tests and the Java 21 clean build,
  full test suite, and external-plugin shading verification passed. JAR SHA-256 was
  `db013c6fd4680bc7bccdb1cfd703d6dd99dca2dc0b4ed9c53372c32acb316c12`; it uploaded to server
  `fa696721`, and `cc reload all` completed. Post-reload `dungeon operations` reported zero active
  instances, reservations, occupied slots, cleanup failures, deadline alerts, late callbacks, and
  repository work. `dungeon config validate` passed with hash
  `b6d42cd6079e48e58af252c5e8ce51587e044e3b5d58bc42d481752379b9ee0d`.
- 2026-09-06: Commits `db49f05` and `3a8af6a` make an all-participants-offline wipe clean the run
  immediately after failed-result persistence instead of waiting through the ten-second reading
  period. The cleanup path retains offline snapshots for reconnect and avoids restoring a player from
  inside the quitting event. Focused and full Java 21 tests passed. JAR SHA-256 was
  `a910f246b1dcdf8f4370b4ef38f5141410c88882d6f784b31a7db0b3de0dd313`; it uploaded to server
  `fa696721`, and `cc reload all` completed. Post-reload `dungeon operations` reported zero active
  instances, reservations, occupied slots, cleanup failures, deadline alerts, late callbacks, and
  repository work. `dungeon config validate` passed with hash
  `2d114238db68b044bd4a0b2cf4761d59343c5fabb9bfb603f9e0e033737ece43`.
- 2026-09-06: User confirmed the all-participants-offline live check passed: the last disconnect failed
  and cleaned the dungeon immediately, and reconnect did not resurrect the wiped run. The
  all-disconnected cleanup check is now passed.
- 2026-09-12: User confirmed the single-disconnect check with `state=GHOST deaths=1` and a populated
  `reviveAt`; reconnect worked. Class-selection expiry correctly kicked the participant, and the
  `/spawn` plus reconnect check did not restore the old snapshot. Disconnecting from the reward view
  incorrectly entered `GHOST`; reward selection worked after revival, so the reward reconnect check
  remains open. Portal and boss-cleanup verification subsequently passed. The active-run deadline
  check remains open; the test-tick controls above avoid waiting nearly 59 minutes.
- 2026-09-12: Commit `84b0e3f` prevents disconnects during the completed reward period from creating a
  ghost while still recording the participant offline. Focused lifecycle tests and the full Java 21
  clean build passed. JAR SHA-256 was
  `ec3a204b9a985d188e001150f27e042d27719a364ebc63f960466812f4da15b5`; it uploaded to server
  `fa696721`, and `cc reload all` enabled DungeonCrawlers successfully. Post-reload operations,
  configuration validation, and repository diagnostics were clean.
- 2026-09-12: Commit `89760af` adds `/dungeon tick speed-test <multiplier>` and
  `/dungeon tick speed-reset-test` for real-time deadline checks. The Java 21 clean build, full test
  suite, shadow-JAR shading verification, and live console command smoke test passed. JAR SHA-256 was
  `6073f702bad3f41821661190d1bef05dedf46762a77f9f72346e412689c98dff`; it uploaded to server
  `fa696721`, and `cc reload all` enabled DungeonCrawlers successfully. The live speed command
  returned `60x real time`, the reset command returned `1x real time`, and post-reload operations,
  configuration validation, and repository diagnostics were clean.
- 2026-09-12: Commit `a1153d2` added manual time controls and a live console smoke test. That global
  control was subsequently replaced by instance-scoped advancement after a fresh preparation could
  inherit the previous test timeline.
- 2026-09-12: Commit `bb79c1f` replaces the global advance with
  `/dungeon instance advance <instance-id> <time>` and adds `/dungeon instance time <instance-id>`.
  The advance accepts seconds or `s`, `m`, and `h` suffixes, dispatches only the selected instance,
  and removes its offset during cleanup; the global test rate also resets when no instance remains.
  The Java 21 clean build, full test suite, shadow-JAR shading verification, and live command
  registration smoke test passed. JAR SHA-256 was
  `8655c61fe6a441ea3217a9d3ff16498e589ebc5eef767bb88cbaf9c9a6e2bd5a`; it uploaded to server
  `fa696721`, and `cc reload all` enabled DungeonCrawlers successfully. Operations, configuration
  validation, and repository diagnostics were clean.
- 2026-09-12: The user completed the player-facing active-run deadline check with
  `/dungeon tick speed-test 600`: the one-minute warning appeared, the run reached its time limit,
  and failed-run cleanup restored the player. The active-run warning check is now passed.
- 2026-09-12: The user also confirmed the instance-scoped deadline check reached
  `Dungeon failed: run time limit reached.`, emitted the expected failed score, and completed failed-run
  cleanup with the player restored. The instance-scoped advance path is working as intended.
- 2026-09-12: On instance `198d96e0-6c01-4bfb-8097-6a0eeaaa1c9f`, the reward info before and after
  reconnect retained both participants and their exact rolls (`UNDEAD_ESSENCE` amounts 5 and 7).
  `LidanTheGamer_` reconnected without entering `GHOST`, with no reroll, and reward selection still
  worked. The other participant's reconnect remains to be checked.
