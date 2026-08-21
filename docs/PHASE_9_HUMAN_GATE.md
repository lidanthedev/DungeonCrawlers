# Phase 9 Human Gate

Status: BLOCKED pending the deferred simultaneous portal-entry concurrency check.

## Required checks

- [x] Complete a normal run through the final combat room and enter the physical Nether Portal.
- [x] Confirm the triggering player owns a five-second countdown; a second player cannot replace it.
- [x] Step out before zero, verify the countdown aborts, then re-enter and confirm it restarts once.
- [x] Let the countdown reach zero and verify every active alive participant is forwarded to distinct
  Emerald boss-spawn markers in the isolated BOSS room.
- [x] Run the configured `basic` encounter, verify the MythicMob spawns at the Red marker, and confirm
  only that exact entity death completes the encounter.
- [x] Verify an unrelated entity death does not complete the encounter.
- [x] Confirm completion enters `COMPLETION_PENDING`, identifies the Lime marker as the reward location,
  and never conflates the Red boss spawn with the Lime reward location.
- [x] Exercise `/dungeon portal start|abort|status` and `/dungeon boss info|start|kill|cleanup`.
- [x] Configure `boss.encounter: multistage_test` in a test floor and run the built-in custom multistage
  encounter; verify each stage requires the exact active entity.
- [x] Trigger a boss start failure with a missing MythicMob and verify the encounter reports `FAILED`.
- [x] Remove an active boss without a death event and verify the tick path reports `FAILED`.
- [x] Set `boss.encounter: factory_failure_test`, exercise the factory-creation failure, and verify the
  run fails closed without spawning a boss.
- [x] Clean up during the countdown and during an active boss encounter; verify no entity, callback, or
  portal state remains.

## Deferred concurrency check

- [ ] Two players entering the same portal at the same time (deferred to the final regression phase).

## Evidence

Evidence (staging run):

- Tester: LidanTheGamer
- Date: 2026-08-21
- Server: `fa696721` (Modern Cave Crawl)
- JAR SHA-256: `AE1075DDED4267C1EE7566B4C60D07BFD8ED2E662ABC204CA66F2130D2998163`
- Verified: normal final-room traversal, five-second ownership/abort/restart flow, two-player Emerald
  spawn assignment, exact-boss filtering, basic completion message, admin portal/boss commands, and
  multistage progression.
- Multistage evidence: `boss encounter active encounter=multistage_test` followed by
  `status=BOSS reward=Point[x=5005, y=65, z=8005]`.
- Completion evidence: `status=COMPLETION_PENDING reward=Point[x=5005, y=65, z=8005]`.
- Cleanup evidence: countdown and active-boss cleanup both completed successfully.
- Failure evidence: missing `CryptGuardianNO` produced `status=FAILED`; removing the active multistage
  entity produced `multistage test entity disappeared at stage 1` and `status=FAILED`.
- Factory evidence: `IllegalStateException: factory failure test` with `status=FAILED` and no boss spawned.
- Pending: the deferred simultaneous two-player portal-entry race.
