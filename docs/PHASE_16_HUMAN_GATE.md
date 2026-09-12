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

## Required checks

- [ ] Run a Java 21 clean build and full test suite, deploy the JAR, and run `cc reload all`.
- [ ] Run `/dungeon help` as a normal player and as an administrator. Confirm player commands are
  visible to both, admin commands require their permissions, and debug commands are marked and
  hidden or rejected when debug mode is off.
- [ ] With `debug: false`, run `/dungeon config validate` and a normal `/dungeon start` as an
  administrator/player as appropriate. Confirm the config result is readable and no debug marker
  or per-tick spam appears. Enable debug for `/dungeon operations` and `/dungeon repository`,
  confirm readable summaries with no raw Java record output, then disable debug again.
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
- [ ] Reconnect while viewing or reopening rewards for every participant. Confirm no ghost state,
  no reroll, no duplicate claim, and each player retains the same offers.
- [ ] Disconnect and reconnect during an active run. Confirm ghost/revive messaging and bounded
  invisibility remain correct, then confirm a wiped run cannot be resurrected.
- [ ] If PlaceholderAPI is installed, check `%dungeoncrawlers_in_dungeon%`,
  `%dungeoncrawlers_instance_<uuid>_state%`, `%dungeoncrawlers_instance_<uuid>_score%`,
  `%dungeoncrawlers_player_deaths%`, and `%dungeoncrawlers_active_instances%` in and outside a run.
  Confirm unknown or unavailable contexts resolve safely. Repeat the plugin reload with
  PlaceholderAPI absent if practical and confirm DungeonCrawlers still enables.
- [ ] Run the generation, portal, boss, and cleanup diagnostics with debug enabled only. Confirm
  FAWE failures remain actionable, boss fallback behavior is readable, and real warnings/errors are
  still logged when debug is disabled.

## Automated coverage

The full suite remains the required regression check. Add focused tests for any production-polish
behavior whose result cannot be verified safely on the live server, especially placeholder context
resolution, message rendering, configuration validation, reload admission, and GUI click safety.

## Evidence

Record the build commit, JAR SHA-256, server id, reload result, test commands, and live observations
here as checks are completed. Do not mark this gate passed until every required check has evidence.
