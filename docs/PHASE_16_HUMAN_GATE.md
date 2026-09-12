# Phase 16 Human Gate - Production polish

Status: IN PROGRESS

Phase 16 is a production-polish pass over the existing dungeon behavior. The gate checks that
player and administrator UX is readable, debug output is opt-in, integrations fail safely, and
the existing lifecycle remains unchanged.

## Test controls

Use the development server `fa696721` (Modern Cave Crawl). Keep `debug: false` for the normal
acceptance pass. For diagnostics-only checks, set `debug: true`, reload, run the command, and
restore `debug: false` before the final reload.

The active-run deadline can be tested without waiting an hour. After opening the start door, run
`/dungeon instance advance <instance-id> 3540`, then inspect
`/dungeon instance time <instance-id>`. Advancing the same instance another 60 seconds forces the
deadline. The advance is scoped to that instance and is removed during cleanup.

For the remaining diagnostics, use disposable runs with debug enabled. Test generation with
`/sudo LidanTheGamer dungeon instance generate-debug-slow floor_1 12345 5000`, then use
`/dungeon instance list` to copy the instance UUID and inspect it with `/dungeon instance info
<instance-id>`. Confirm the generation status is readable, then run `/dungeon instance cleanup
<instance-id>` and verify `/dungeon operations` has no leftover work. The delay must stay within
`0..5000` milliseconds. There is no safe command that injects a FAWE hang or provider failure;
those failure paths remain covered by automated tests and the Phase 14 operational assumption.

For portal and boss diagnostics, start a disposable normal run with `/sudo LidanTheGamer dungeon
start floor_1`, select a class, open the start door, and clear rooms until `The portal room is
ready.` appears. Use the instance UUID from `/dungeon instance list`, then run the following:

```text
/dungeon portal status <instance-id>
/dungeon portal start <instance-id>
/dungeon portal status <instance-id>
/dungeon portal abort <instance-id>
/dungeon portal status <instance-id>
/dungeon boss start <instance-id>
/dungeon boss info <instance-id>
/dungeon boss cleanup <instance-id>
/dungeon operations
/dungeon repository
```

The second portal status should show `COUNTDOWN`, the status after abort should show no active
owner, boss info should report a readable preparing or active encounter, and cleanup should leave
zero active instances, reservations, occupied slots, queued work, and in-flight work. Real FAWE
warnings and errors should still be logged after debug is disabled.

## Required checks

- [x] Run a Java 21 clean build and full test suite, deploy the JAR, and run `cc reload all`.
- [x] Run `/dungeon help`; the help output rendered successfully.
- [ ] Run `/dungeon help` as a normal player and as an administrator. Confirm player commands are
  visible to both, admin commands require their permissions, and debug commands are marked and
  hidden or rejected when debug mode is off.
- [x] With debug disabled, `/dungeon config validate` returned `Configuration is valid.` and
  `/dungeon operations` and `/dungeon repository` returned the debug-only unavailable message.
  After debug was enabled and soft-reloaded, both diagnostics rendered readable summaries with no
  raw Java record output.
- [ ] Restore `debug: false`, reload, and run a normal `/dungeon start`. Confirm the config result
  remains readable and no debug marker or per-tick spam appears.
- [ ] Enable debug temporarily and verify one debug command can run, then disable it and reload.
  Confirm the setting is validated and the reload reports the active configuration summary without
  leaving a mixed runtime state.
- [ ] Start a solo run and a two-player run. Confirm party-size feedback, class selection, door
  opening, room progression, and cross-world `/spawn` or administrator teleport behavior remain
  unchanged.
- [ ] Complete and fail disposable runs. Confirm multiline score output has readable rank and
  category hover details, and failure or timeout messages remain visible with debug disabled.
- [ ] Open the reward GUI. Confirm offers are ordered by minimum score from left to right, titles and
  lore use player-facing labels, locked offers explain the requirement, clicking purchase closes the
  GUI, insufficient funds leaves the offer selectable, and a full inventory rejects delivery without
  creating mailbox overflow.
- [x] Reconnect while viewing or reopening rewards for every participant. Confirm no ghost state,
  no reroll, no duplicate claim, and each player retains the same offers.
- [x] Disconnect and reconnect during an active run. Confirm ghost/revive messaging and bounded
  invisibility remain correct, then confirm a wiped run cannot be resurrected.
- [x] If PlaceholderAPI is installed, check `%dungeoncrawlers_in_dungeon%`,
  `%dungeoncrawlers_instance_<uuid>_state%` using the actual instance UUID, and
  `%dungeoncrawlers_instance_this_state%` using the supplied player's active instance,
  `%dungeoncrawlers_instance_<uuid>_score%`,
  `%dungeoncrawlers_player_deaths%`, and `%dungeoncrawlers_active_instances%`; the supported
  placeholders, including `instance_this`, resolved correctly in the exercised run.
- [ ] Confirm unknown or unavailable PlaceholderAPI contexts resolve safely. Repeat the plugin reload
  with PlaceholderAPI absent if practical and confirm DungeonCrawlers still enables.
- [x] Run the generation, portal, boss, and cleanup diagnostics with debug enabled only. The diagnostic
  sequence and full portal/boss testing completed correctly.
- [ ] Confirm FAWE failures remain actionable, boss fallback behavior is readable, and real warnings/errors
  are still logged when debug is disabled.

## Automated coverage

The full suite remains the required regression check. Add focused tests for any production-polish
behavior whose result cannot be verified safely on the live server, especially placeholder context
resolution, message rendering, configuration validation, reload admission, and GUI click safety.

## Evidence

Record the build commit, JAR SHA-256, server id, reload result, test commands, and live observations
here as checks are completed. Do not mark this gate passed until every required check has evidence.

- 2026-09-12: Checkpoint `9e3b62` passed the Java 21 `./gradlew clean build --no-daemon` with all
  244 tests, shadow-JAR verification, and external-plugin shading checks. The deployed JAR
  `build/libs/DungeonCrawlers-1.0.jar` has SHA-256
  `6b57fd3d3e0b8c4110abe90d85ab6f92225a3635c450e74429b63729b44fb9e3`.
- 2026-09-12: The JAR uploaded successfully to Pterodactyl server `fa696721`. `cc reload all`
  reported `DungeonCrawlers reloaded!`; `dungeon config validate` passed with active hash
  `2d114238db68b044bd4a0b2cf4761d59343c5fabb9bfb603f9e0e033737ece43`, and
  `dungeon compatibility` reported automated checks passed. The known `[Progress] Couldn't get the
  number...` lines were filtered from review. The same PlugMan all-plugin reload produced a Paper
  watchdog dump while reloading CaveCrawlers; DungeonCrawlers then loaded and the reload completed.
- 2026-09-12: Console `/dungeon help` rendered the prefixed interactive help list. With production
  debug disabled, `/dungeon operations` correctly returned the debug-only warning. A player-context
  PlaceholderAPI smoke test using `/sudo LidanTheGamer papi parse me
  %dungeoncrawlers_in_dungeon%` returned `false` outside a run.
- 2026-09-12: A final `cc reload all` completed with `DungeonCrawlers reloaded!`; the subsequent
  `/dungeon config validate` passed with active hash
  `2d114238db68b044bd4a0b2cf4761d59343c5fabb9bfb603f9e0e033737ece43`.
- 2026-09-12: Checkpoint `9bfbc70` passed `./gradlew clean build --no-daemon` on Java 21,
  including the full test suite, shadow-JAR verification, and external-plugin shading checks.
  The resulting `build/libs/DungeonCrawlers-1.0.jar` has SHA-256
  `401184ac1d0e2145b46a1da76593f415d4a42317d87dc083dc9c8873165c9c67`.
- 2026-09-12: The checkpoint JAR uploaded successfully to Pterodactyl server `fa696721`.
  `cc reload all` reported `DungeonCrawlers reloaded!`; `dungeon config validate` returned
  `Configuration is valid.`, and console `/dungeon instance list` rendered `Instances: 0`.
  The known `[Progress] Couldn't get the number...` lines were filtered from review.
- 2026-09-12: Checkpoint `bc1af4e` fixed instance list and info rendering for configured
  MiniMessage floor labels, so tags such as `<gold>` are parsed instead of shown literally.
  The focused renderer regression test and Java 21 `./gradlew clean build --no-daemon` passed.
  The deployed JAR `build/libs/DungeonCrawlers-1.0.jar` has SHA-256
  `27df4f8f36794228da7617fae0acfe2c95331058f4dd53281bee498b75b68288`.
  The JAR uploaded successfully to server `fa696721`; `cc reload all` reported
  `DungeonCrawlers reloaded!`, `dungeon config validate` returned `Configuration is valid.`,
  and `/dungeon instance list` returned `Instances: 0`.
- 2026-09-12: The human-gate checks for help, configuration, and diagnostics passed for the
  exercised portion. `/dungeon help` rendered successfully. With debug disabled, configuration
  validation passed and `/dungeon operations` plus `/dungeon repository` were correctly blocked.
  After a soft reload with debug enabled, both commands showed readable summaries: active instances,
  cleanup counts, recovery state, repository capacity, and queue state without raw record output.
- 2026-09-12: A solo Floor FAST run completed with readable S+ score output, correct class, door,
  room, portal, boss, reward, and `/spawn` behavior. Reward offers were ordered correctly from left
  to right. The reward reconnect and active-run ghost/revive checks are carried forward from the
  passing Phase 15 gate.
- 2026-09-12: PlaceholderAPI checks resolved `%dungeoncrawlers_instance_6e976170-15cc-4ee3-a253-
  f76173e64208_state%`, `%dungeoncrawlers_player_deaths%`, and `%dungeoncrawlers_active_instances%`.
  Before the follow-up implementation, `%dungeoncrawlers_instance_this_state%` returned `false`
  because direct instance lookups required a UUID. The remaining active-run `this` smoke test,
  unknown-context, debug-reset, and PlaceholderAPI-absent checks are open.
- 2026-09-13: Gate checkpoint `0f9dc35` passed the Java 21 `./gradlew clean build --no-daemon`
  and full test suite. The rebuilt JAR has SHA-256
  `d9a5be9ec29328b345d6f0aab9f6be9eeb5259441f49ea59c48c2b05e02e2759` and uploaded successfully
  to server `fa696721`. `cc reload all` reported `DungeonCrawlers reloaded!`; delayed
  `/dungeon config validate` returned `Configuration is valid.`; and debug diagnostics returned
  zero active instances, reservations, occupied slots, blockers, queued work, and in-flight work.
  The known `[Progress] Couldn't get the number...` lines and the unrelated PlugMan/Paper watchdog
  dump during the all-plugin reload were filtered from the DungeonCrawlers result.
- 2026-09-13: Checkpoint `75191c4` added `this` instance-placeholder resolution for the supplied
  PlaceholderAPI player, preserving UUID lookup and missing-instance fallbacks. The focused
  `DungeonPlaceholderExpansionTest` and full Java 21 clean build passed. The JAR has SHA-256
  `394008c31be3c58b626b86a3e13f2e1663a4592e9640b0f784a773669a282a68`; it was uploaded to
  server `fa696721`, `cc reload all` reported `DungeonCrawlers reloaded!`, and post-reload
  configuration, operations, and repository checks passed. The implementation returns the
  documented `false`/`0`/empty fallback outside a dungeon.
- 2026-09-13: Human-gate follow-up confirmed the active-run `instance_this` PlaceholderAPI check,
  the diagnostic sequence, and the full portal/boss testing work correctly. Unknown-context,
  PlaceholderAPI-absent, and debug-off failure/logging checks remain open.
