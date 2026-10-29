# Phase 7 Human Gate Notes

Phase 7 secret discovery and blessing checks are recorded during the live gate.

## Deferred verification

- [ ] Two-player simultaneous secret discovery race (deferred to the final phase)
  - Reason: two-player synchronized interaction was not performed during this gate.
  - Setup: put two party members in the same run and have both right-click the same undiscovered secret at the same time.
  - Pass criteria: exactly one discovery/reward and one `Secret already found` response; the blessing level must not be applied twice.

`CHEST` markers are blessing secrets and `TRAPPED_CHEST` markers are standard secrets. Both interactions are cancelled after the plugin handles them.

As of 2026-10-10, first discovery of a trapped secret chest fully restores the
finder's native health and mana and sends `Secret discovered! Health and mana
fully restored.` Repeated discoveries, off-hand clicks, ghosts, and players
outside the run do not refill resources. Blessing chests retain their existing
behavior. Automated evidence and deployment are recorded in
`DUNGEON_IMPROVEMENTS_HUMAN_GATE.md`; the new live click gate remains open.
