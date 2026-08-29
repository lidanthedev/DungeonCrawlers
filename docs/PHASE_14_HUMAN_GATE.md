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
- [x] With two online players in a RUNNING dungeon, run `cc reload all` and confirm both return
  to their exact saved locations while the run is cleared.
- [x] Put a player into GHOST, then use the safe reload path. Confirm no late revive callback,
  stale ghost state, entity, protection region, or reservation remains.
- [x] Enter the custom BOSS encounter, use `/dungeon reload force`, and confirm the boss/entity
  callbacks stop and the player is restored without a second completion.
- [ ] During `COMPLETION_PENDING`, confirm `/dungeon reload force` is rejected before admission
  is paused or any cleanup callback runs. Wait for the reward period to close before reloading.
- [x] During `FAILED`, confirm the failed-reading cleanup remains retryable and no stale slot or
  player mapping remains after the deadline.
- [x] During `DELIVERY_PENDING` or `OWNED`, use the Phase 12 delivery pause/recovery controls and
  confirm disable/rejoin delivers the exact item once with no automatic debit retry.
- [ ] Confirm an ambiguous `DEBIT_ATTEMPTED` record remains reconciliation-required after reload;
  no automatic charge/refund/retry occurs.
- [x] Disable/reload with an offline captured player and confirm the durable snapshot is restored
  on the next join; an unsuccessful restore remains pending for retry. A full server restart is
  still not separately exercised.
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
- 2026-08-23: Live slow-generation disable drill on instance `fc55ad1e-3bff-4593-841e-6aae9844abb8`
  observed `status=PASTING` before `cc reload all`. Disable retained the journal with
  `snapshots_restored online=0 offline_retained=true`; startup recovery reported
  `discovered=1 cleared=1 blocked=0`, and final operations were clean with zero active instances,
  reservations, occupied slots, cleanup alerts, or repository work. This validates generation
  journal and slot recovery; the debug path does not capture player snapshots.
- 2026-08-23: Live player-recovery drill on instance `72e7ce28-14b5-4f04-a46c-ff135832606f`
  started through `/sudo LidanTheGamer dungeon start floor_1`; `cc reload all` ran while the
  player was online. Disable logged `snapshots_restored online=1`, and post-reload operations
  reported zero instances, reservations, occupied slots, and repository work. The player was no
  longer inside a dungeon room afterward. The admin door-open and boss-start commands were also
  verified to refuse before the lifecycle is RUNNING; those checks require one physical start-door
  interaction to continue.
- 2026-08-23: Delivery recovery drill on instance `6f89e27f-6211-4ad1-8637-e5a12cf5ce03` reset
  entitlements, paused delivery, and claimed `wooden`. After `cc reload all`, player recovery
  reported `discovered=1 cleared=1`; chat reported `Reward delivered to your inventory.` and
  reward info showed claim `fad3c504-a8b5-3138-990d-69b7dbddf27d` in `DELIVERED`. Operations
  remained idle with no queued or in-flight repository work.
- 2026-08-23: Single-player ghost drill on instance `923cacf8-d09c-4a1b-b7a3-f83ea1992d6c`
  passed the admin ghost transition, then correctly entered `no online active alive player
  remains`, failed-reading cleanup, and player restoration. A subsequent `cc reload all` left
  operations clean. A ghost surviving reload cannot be exercised with one participant because
  the run closes immediately; a second active participant is required for that case.
- 2026-08-23: Boss reload drill on instance `3b59d7e8-d3cd-4b79-95d9-4a256753b044` reached
  `status=BOSS` with encounter `basic`. `cc reload all` recovered and cleared one run; final
  operations reported zero active instances, reservations, occupied slots, and repository work.
- 2026-08-23: Failed-reading reload drill on instance `d66de7e1-79b9-4efc-8282-faa1b2045498`
  advanced the central test clock by 3,600 seconds. The client showed `Dungeon failed: run time
  limit reached.` and score `0 (D)`; reload recovery discovered and cleared one run, then
  operations returned to zero active instances, reservations, occupied slots, and queued work.
- 2026-08-23: Two-player ghost reload drill on instance `6e420c42-815d-4e8a-8d93-3fc7325bbca1`
  verified `LidanTheGamer=GHOST` with `LidanTheGamer_=ALIVE` and a 60-second revive deadline.
  `cc reload all` recovered and cleared the run without a late revive; after recovery both
  lifecycle mappings were gone and operations reported zero instances, reservations, occupied
  slots, and repository work.
- 2026-08-23: Two-player online snapshot drill on instance `bc8bbee0-2759-450f-a230-d3c3633cb02f`
  ran `cc reload all` after both players cleared room 1. Disable logged
  `snapshots_restored online=2 offline_retained=true`; startup recovery discovered and cleared
  one run. Both players confirmed they returned to their exact pre-dungeon locations, and final
  operations reported zero active instances, reservations, occupied slots, and repository work.
- 2026-08-29: Offline snapshot reload drill on instance `4e41ff32-6c93-4e1c-b9c7-875702e1053b`
  closed `LidanTheGamer_` while the run was active, then ran `cc reload all`. On rejoin, the
  client reported `[PASS] recovery restore=exact snapshot location restored`; the captured
  location `[1158.722052881312, 311.0, 66.54000463954272]` and rotation `[-174.14871,
  17.249968]` matched exactly. Recovery reported `discovered=1 cleared=1`, and final operations
  were clean with zero active instances, reservations, occupied slots, cleanup alerts, or
  repository work.
- 2026-08-29: Slow-generation cancellation probe on instance `d7470fa8-20ba-48c9-af3b-a05cd70bc5b8`
  used `generate-debug-slow floor_1 12345 5000`; `/dungeon instance info` observed
  `status=PASTING` at `placement 1 spider_den`. `/dungeon reload force` cleaned the instance,
  and direct entity-data checks showed `LidanTheGamer` still at `[1162.5020862342462, 310.5,
  54.04470515942776]` with rotation `[13.757994, 20.269152]`. Final operations were clean with
  zero active instances, reservations, occupied slots, cleanup alerts, or repository work. This
  is partial evidence only: the debug generation path does not capture player snapshots, so the
  normal-start early-generation snapshot check remains open.

See [PHASE_14_OPERATIONS_RUNBOOK.md](PHASE_14_OPERATIONS_RUNBOOK.md) for backup and rollback steps.
