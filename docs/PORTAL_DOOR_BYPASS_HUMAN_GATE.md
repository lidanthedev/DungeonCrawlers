# Closed-door portal entry

At the start of a boss portal countdown, record whether the final combat door was opened through the room-door interaction. Clearing the final room only unlocks the door and does not count as opening it. If it was still closed, run the usual countdown, then kill all remaining dungeon participants and fail the run instead of teleporting or spawning the boss. Opening the door during that countdown does not cancel the penalty. Leaving the portal still aborts the countdown normally; a later legal activation works normally. Explicit admin `boss start` remains a direct testing command.

The terminal party death counts one death for each remaining alive participant, including offline participants. Existing ghosts retain their recorded deaths; removed players are untouched. All revive timers are cancelled, including Runic timers. Failure scoring observes the completed party death state. Inventory is unaffected by dungeon death handling.

Party title: `Shortcut Unlocked!` / `Destination: the graveyard.` Chat: `You skipped the door. The boss skipped your survival.` Messages are sent after normal failure notices so the funny title stays visible.

## Verification, 2026-10-09

- Java 21 build passed with 312 tests, no failures, errors or skips, including external API shading verification.
- Regression coverage distinguishes unlocking from opening, failed door attempts, per-instance state and cleanup; verifies countdown expiry, latched entry state, abort/retry, no boss spawn or teleport, and one-shot terminal party death across alive, ghost, offline, removed and Runic participants.
- JAR `build/libs/DungeonCrawlers-1.0.jar`, SHA-256 `6122184992d9cbd37f248f3f5b5cf5a23daf74d34a8d8317337c1fb0a194b3cd`, uploaded to Modern Cave Crawl (`fa696721`) and reloaded using `cc reload all` at 18:24:08 UTC. Configuration validation and recovery IDLE passed. The reload produced the previously observed Paper dependency/classloader watchdog dump while GriffinAddon enabled, then recovered.
- Debug-only floor_1 Normal run `2ddad34c-a1ed-492e-a69d-2d2d98f6b7d8` started with LidanTheGamer. Its first-room mob reconciliation exhausted the configured respawn budget, so the portal entrance remained closed. No templates or configuration were edited.
- Admin `portal start` at 18:25:12 exercised the same countdown entry method used by the portal listener. Status still reported the active countdown at 18:25:14; the player was alive with zero deaths at 18:25:16. At 18:25:17 the run was FAILED. At 18:25:26 the player was a ghost with one death and no revive scheduled.
- Native reading-period cleanup completed at 18:25:28 and returned the player to the original DeepMines coordinates `(1158.5, 311.0, 71.5)` in the original survival mode. Follow-up instance and slot checks found zero active instances and all 20 slots free. Debug runs award no progression or loot.

Only one test player was online. Physical portal entry, multiplayer display and title appearance remain visually unchecked; party state and countdown routing have automated coverage.
