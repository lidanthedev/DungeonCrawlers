# Phase 13 Human Gate - Run and result deadlines

Status: IN PROGRESS

Phase 13 adds one central deadline state machine for preparation, active runs, failed-run
reading, and completed reward periods. The development-only test tick advances the same central
callbacks used by the normal server tick, so a full server restart is not required for this gate.

## Test controls

Use the admin permission `dungeoncrawlers.admin.generation`:

```text
/dungeon tick reset-test
/dungeon tick advance-test <seconds>
```

`advance-test` accumulates from the previous test tick. Reset it before each independent run or
deadline scenario. The normal one-second scheduler continues to run between commands.

## Required checks

- [x] Build, deploy, run `cc reload all`, and record the JAR SHA-256.
- [x] Start a generated run, select all classes, and leave the door unopened. Advance 240 seconds
  and confirm one preparation warning. Advance 60 more seconds and confirm the run is cancelled,
  the captured player state is restored, and the generated instance is cleaned up.
- [ ] Start another run and open the door. Advance 3,540 seconds and confirm one active-run
  warning. Advance 60 seconds and confirm the run becomes failed, the failure score is shown, and
  no boss or room callbacks continue progressing.
- [ ] Advance 10 more seconds after the failed result. Confirm the player is restored and the
  generated instance, lifecycle, combat, portal, and central callbacks are cleaned up.
- [ ] Complete a boss encounter and confirm the reward chest is available while the run is in
  its completed reward period. Advance 240 seconds and confirm the one-minute warning.
- [ ] Advance to the final ten seconds and confirm the closing title countdown appears once per
  second. Advance to five minutes and confirm the chest, run, lifecycle, and generated instance
  are closed and players are restored.
- [ ] Repeat the completed-result check with all active participants leaving early. Confirm the
  completed group closes immediately and no stale central registration remains.
- [ ] Confirm `/dungeon reward info <instance>` and reward claiming remain available during the
  completed reward period, and that deadline cleanup does not duplicate or remove a durable claim.

## Automated coverage

The core tests cover exact deadline boundaries, one-shot warnings, BOSS-state timeout, the ten
second failed reading period, completion countdown deduplication, early empty-group cleanup, and
portal completion advancing to `COMPLETED` with a five-minute deadline.

## Recorded evidence

Human-gate evidence will be added after the first deployed Phase 13 build.

- 2026-08-22: Clean build and full test suite passed; final deployed JAR SHA-256 was
  `6eb9869daa655237e4b9f12635d8d322e6caaf77d3f693d4361b2468b4b7d375`. Deployment and
  `cc reload all` reported `DungeonCrawlers reloaded!` on server `fa696721`.
- 2026-08-22: Live preparation check on instance `64b28008-44e9-438e-b0d9-60303464e1fe` showed
  `Class selection closes in 1 minute.` in the client log, then cleanup restored the player and
  `instance info` reported `DESTROYED` with the slot free.
- 2026-08-22: Repeat check on instance `36ed5de5-e6b9-4500-8c0c-47f89b85991c` confirmed the
  corrected player message `preparation timed out; preparation deadline ended and player restored`;
  server cleanup again reported the instance `DESTROYED` and all four slots free.
- Remaining running/BOSS and completed-reward checks require opening the generated start door and
  completing or administratively starting a boss encounter in the live client.
