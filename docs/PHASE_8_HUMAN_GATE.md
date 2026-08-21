# Phase 8 Human Gate

Phase 8 covers lethal damage, ghost state, logout/rejoin, escape, recovery, and instance wipe.

Use the development server `fa696721` (Modern Cave Crawl). For player-only checks, run the command as the authorized test player with `/sudo LidanTheGamer <command>`; do not use `/sudo bigbou`.

## Gate checklist

- [ ] Start a run with one or more players and select all classes.
- [ ] Deal lethal damage to one participant. The player is changed to `SPECTATOR`, receives the ghost message, keeps inventory and effects, cannot interact, break, or place blocks, and does not receive duplicate death transitions.
- [ ] With another participant alive and online, wait 60 seconds (or use the admin player-death/player-revive diagnostics; admin revive schedules a three-second countdown). The ghost returns to survival near an alive participant and receives the revive message.
- [ ] Disconnect a ghost before the timer expires, reconnect, and verify the original revive deadline is preserved rather than restarted. Repeating logout/rejoin must not duplicate or reset the timer.
- [ ] Run `/sudo LidanTheGamer dungeon escape`, `/sudo LidanTheGamer dungeon leave`, and `/sudo LidanTheGamer spawn` while inside a run. Each path removes the player, restores the captured location and attributes, and is not teleported back by later room or instance callbacks.
- [ ] Verify a player who has escaped cannot select a class, open a dungeon door, or be restored by a later revive attempt.
- [ ] Disconnect every active alive participant (or use `/dungeon instance wipe <instance-id>`). The instance is wiped, combat/generation/secret state is cleaned, and captured players are restored immediately or on their next join.
- [ ] Restart the server while a participant is offline or ghosted. On rejoin, the persisted recovery snapshot restores the player once and is then deleted.
- [ ] Verify `/dungeon player info <instance-id> <player-uuid>`, `/dungeon player death`, `/dungeon player ghost`, `/dungeon player revive`, `/dungeon player remove`, and `/dungeon instance wipe` report clear PASS/FAIL results.

## Deferred verification

- [ ] Two-player simultaneous lethal damage and simultaneous logout/rejoin race (deferred to the final phase when two accounts are available).
  - Pass criteria: each participant transitions at most once, no participant is revived after a wipe, and exactly one terminal cleanup occurs.
