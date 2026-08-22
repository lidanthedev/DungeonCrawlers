# Phase 13 Human Gate - Run and result deadlines

Status: PASS

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
- [x] Start another run and open the door. Advance 3,540 seconds and confirm one active-run
  warning. Advance 60 seconds and confirm the run becomes failed, the failure score is shown, and
  no boss or room callbacks continue progressing.
- [x] Advance 10 more seconds after the failed result. Confirm the player is restored and the
  generated instance, lifecycle, combat, portal, and central callbacks are cleaned up.
- [x] Complete a boss encounter and confirm the reward chest is available while the run is in
  its completed reward period. Advance 240 seconds and confirm the one-minute warning.
- [x] Advance to the final ten seconds and confirm the closing title countdown appears once per
  second. Advance to five minutes and confirm the chest, run, lifecycle, and generated instance
  are closed and players are restored.
- [x] Repeat the completed-result check with all active participants leaving early. Confirm the
  completed group closes immediately and no stale central registration remains.
- [x] Confirm `/dungeon reward info <instance>` and reward claiming remain available during the
  completed reward period, and that deadline cleanup does not duplicate or remove a durable claim.

## Automated coverage

The core tests cover exact deadline boundaries, one-shot warnings, BOSS-state timeout, the ten
second failed reading period, completion countdown deduplication, early empty-group cleanup, and
portal completion advancing to `COMPLETED` with a five-minute deadline.

## Recorded evidence

Human-gate evidence was captured against the deployed Phase 13 build.

- 2026-08-22: Clean build and full test suite passed; final deployed JAR SHA-256 was
  `6eb9869daa655237e4b9f12635d8d322e6caaf77d3f693d4361b2468b4b7d375`. Deployment and
  `cc reload all` reported `DungeonCrawlers reloaded!` on server `fa696721`.
- 2026-08-22: Live preparation check on instance `64b28008-44e9-438e-b0d9-60303464e1fe` showed
  `Class selection closes in 1 minute.` in the client log, then cleanup restored the player and
  `instance info` reported `DESTROYED` with the slot free.
- 2026-08-22: Repeat check on instance `36ed5de5-e6b9-4500-8c0c-47f89b85991c` confirmed the
  corrected player message `preparation timed out; preparation deadline ended and player restored`;
  server cleanup again reported the instance `DESTROYED` and all four slots free.
- 2026-08-23: Running deadline check on instance `a92a9a83-38cc-4529-990f-1a68af619a81` confirmed
  the one-minute warning, `Dungeon failed: run time limit reached.`, score `0 (D)`, and the
  failed-reading close message. After `+10s`, the player was restored, the instance was
  `DESTROYED`, all four slots were free, protection regions and permits were zero, and the
  repository had no in-flight operations.
- 2026-08-23: Completed-reward check on instance `49250fd3-560e-4148-84fa-bec58a16f656`
  confirmed score `200 (B)`, four reward offers, `Reward chest closes in 1 minute.`, and a
  one-second-at-a-time final countdown advance. Advancing past the deadline closed the run and
  left the instance `DESTROYED`, all slots free, protection regions at zero, and the repository
  idle. Titles are client-rendered and are not serialized in the chat log.
- 2026-08-23: Early-leave check on instance `f1477807-a7ba-49d6-8cdb-d2a0c2d08f28` completed the
  boss, issued `/spawn`, restored the exact snapshot location, reported `no online active alive
  player remains`, and cleaned up immediately.
- 2026-08-23: Reward-claim check on instance `0efc532a-5cdc-48cb-b911-5eb68bd2b68f` completed
  the boss and claimed `wooden` during the completed period. The client reported `Reward
  purchased. Delivering items...` and `Reward delivered to your inventory.`; reward info showed
  the durable claim state `DELIVERED` before the instance was closed past its deadline.
