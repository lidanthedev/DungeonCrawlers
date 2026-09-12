# Phase 8 Human Gate

Phase 8 covers lethal damage, ghost state, logout/rejoin, escape, recovery, and instance wipe.

Use the development server `fa696721` (Modern Cave Crawl). For player-only checks, run the command as the authorized test player with `/sudo LidanTheGamer <command>`; do not use `/sudo bigbou`.

## Gate status

PASS for the previously completed non-concurrent lifecycle/admin checks, including multi-participant
behavior exercised serially. The active-run disconnect-to-ghost correction is recorded in the Phase 15
gate for live re-verification; the two-player concurrency race remains deferred to the final phase.

## Gate checklist

- [x] Start a run with one or more players and select all classes.
- [x] Deal lethal damage to one participant. The player becomes invisible `SURVIVAL`, receives the ghost message, keeps inventory and effects, cannot damage or receive damage, cannot interact, break, or place blocks, and does not receive duplicate death transitions.
- [x] With another participant alive and online, wait 60 seconds (or use the admin player-death/player-revive diagnostics; admin revive schedules a three-second countdown). The ghost returns to survival near an alive participant and receives the revive message.
- [x] Disconnect a ghost before the timer expires, reconnect, and verify the original revive deadline is preserved rather than restarted. Repeating logout/rejoin must not duplicate or reset the timer.
- [ ] Disconnect an `ALIVE` participant after the run starts. Verify the player is recorded as an offline
  `GHOST` with exactly one additional death and a 60-second revive deadline; reconnect must preserve that
  deadline.
- [x] Run `/sudo LidanTheGamer dungeon escape`, `/sudo LidanTheGamer dungeon leave`, and `/sudo LidanTheGamer spawn` while inside a run. Each path removes the player, restores the captured location and attributes, and is not teleported back by later room or instance callbacks.
- [x] Verify a player who has escaped cannot select a class, open a dungeon door, or be restored by a later revive attempt.
- [x] Disconnect every active alive participant (or use `/dungeon instance wipe <instance-id>`). The instance is wiped, combat/generation/secret state is cleaned, and captured players are restored immediately or on their next join.
- [x] Restart the server while a participant is offline or ghosted. The restart kicked players out of the dungeon and restored them.
- [x] Verify `/dungeon player info <instance-id> <player-uuid>`, `/dungeon player death`, `/dungeon player ghost`, `/dungeon player revive`, `/dungeon player remove`, and `/dungeon instance wipe` report clear PASS/FAIL results.

## Deferred verification

- [ ] Two-player simultaneous lethal damage and simultaneous logout/rejoin race (deferred to the final phase; not yet performed).
  - Pass criteria: each participant transitions at most once, no participant is revived after a wipe, and exactly one terminal cleanup occurs.
