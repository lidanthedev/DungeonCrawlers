# Phase 14 Human Gate - Disable, reload, and operations

Status: IN PROGRESS

Phase 14 freezes admission and callbacks before teardown, restores captured online players,
retains offline recovery snapshots, exposes operational diagnostics, and refuses forced reload
while completion is pending. Active generation journals and slots remain owned by startup
recovery when a plugin disable cannot safely finish world cleanup.

## Operational checks

Use an administrator account with `dungeoncrawlers.admin.reload`:

```text
/dungeon operations
/dungeon reload
/dungeon reload force
```

`/dungeon operations` must report zero stale active instances, reservations, occupied slots,
repository queue entries, and terminal writes after an idle reload. A cleanup deadline alert is
diagnostic only: it must not release a clearing slot or reservation automatically.

## Required checks

- [x] Java 21 clean build, full tests, deploy, and `cc reload all` on server `fa696721`.
- [x] Idle `/dungeon operations` reports `activeInstances=0`, `reservations=0`,
  `occupiedSlots=0`, and an idle repository.
- [x] Idle `/dungeon reload force` completes without leaving admission paused.
- [x] Create a fresh Pterodactyl backup before the next stateful restart drill. Backup
  `phase14-operations-bdcc613` was created after the old backup was deleted; a follow-up create
  request correctly reported the configured three-backup limit.
- [ ] Start during GENERATING/PASTING, run `/dungeon reload force`, and confirm online players
  return to their exact saved location while the journal is cleared or startup-recoverable.
- [ ] Put a player into GHOST, then use the safe reload path. Confirm no late revive callback,
  stale ghost state, entity, protection region, or reservation remains.
- [ ] Enter the custom BOSS encounter, use `/dungeon reload force`, and confirm the boss/entity
  callbacks stop and the player is restored without a second completion.
- [ ] During `COMPLETION_PENDING`, confirm `/dungeon reload force` is rejected before admission
  is paused or any cleanup callback runs. Wait for the reward period to close before reloading.
- [ ] During `FAILED`, confirm the failed-reading cleanup remains retryable and no stale slot or
  player mapping remains after the deadline.
- [ ] During `DELIVERY_PENDING` or `OWNED`, use the Phase 12 delivery pause/recovery controls and
  confirm disable/rejoin delivers the exact item once with no automatic debit retry.
- [ ] Confirm an ambiguous `DEBIT_ATTEMPTED` record remains reconciliation-required after reload;
  no automatic charge/refund/retry occurs.
- [ ] Restart with an offline captured player and confirm the durable snapshot is restored on
  the next join; an unsuccessful restore remains pending for retry.
- [ ] Trigger a cleanup past 30 seconds with the test gateway and confirm one `[OPS]` deadline
  alert while the slot and reservation remain blocked.

## Automated coverage

The current automated gate covers callback freezing, deadline-callback suppression, ghost-tick
suppression, forced cleanup deadline alert deduplication, and unsafe lease retention. Generation
late-callback, repository receipt timeout/failure, completed-run conversion, and the full player
restart matrix remain release-blocking follow-up checks for this phase.

## Recorded evidence

- 2026-08-23: checkpoint `3371f34` passed focused callback-freeze tests, full clean build, and
  deploy. JAR SHA-256 was `a41c2920ec1059763a88396bf7397efdabc3df5ab33f49b064d7e05942c85647`.
- 2026-08-23: checkpoint `8380817` passed the full clean build and test suite. JAR SHA-256 was
  `70164a825e38613e62c8898ee38531da91152d673611db64920d853493b6ff47`.
- 2026-08-23: `cc reload all` reported DungeonCrawlers reloaded on `fa696721`; `/dungeon operations`
  reported `activeInstances=0 reservations=0 occupiedSlots=0`, zero cleanup alerts, and an idle
  repository. Idle `/dungeon reload force` completed with a passing config hash.
- 2026-08-23: The documentation-checkpoint JAR was rebuilt and uploaded successfully with SHA-256
  `fafe0c874bae1f45df240145ead52331cb3198435a5e89a2ca9b923d91c6c056`. A Pterodactyl backup
  request was refused at the configured three-backup limit; existing backups were left untouched.
- 2026-08-23: reload-dispatch guard checkpoint `206985c` passed the full clean build and was
  deployed with JAR SHA-256 `24c6853d412114fe0905474f3ba0cdfc3e4720a71c48337e2043789dac18cd18`.
- 2026-08-23: Pterodactyl backup `phase14-operations-bdcc613` was created and locked before the
  stateful restart drills; a second create request was refused at the three-backup limit.
- 2026-08-23: Debug generation instance `e4ac9f30-fcf9-4b29-90cd-343fc359e53e` was admitted with
  the maximum 5-second delay, then `/dungeon reload force` canceled it. The client reported a
  passing reload hash; operations ended with zero active instances, reservations, and occupied
  slots, cleanup `started=1 completed=1 failed=0`, and an idle repository. Exact player snapshot
  restoration remains a separate check because debug generation does not capture a player.
- 2026-08-23: Live player-recovery drill on instance `72e7ce28-14b5-4f04-a46c-ff135832606f`
  started through `/sudo LidanTheGamer dungeon start floor_1`; `cc reload all` ran while the
  player was online. Disable logged `snapshots_restored online=1`, and post-reload operations
  reported zero instances, reservations, occupied slots, and repository work. The player was no
  longer inside a dungeon room afterward. The admin door-open and boss-start commands were also
  verified to refuse before the lifecycle is RUNNING; those checks require one physical start-door
  interaction to continue.

See [PHASE_14_OPERATIONS_RUNBOOK.md](PHASE_14_OPERATIONS_RUNBOOK.md) for backup and rollback steps.
