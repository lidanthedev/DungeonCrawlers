# Rewards remain available until instance closure

The owner reported that completed chest access expired while they were still in the boss arena. Modern Cave Crawl's live configuration has a 120-second live reward window and a 300-second completion period. These independent deadlines allowed the entitlement to expire before the instance closed.

Live reward opening, preview and purchases now follow the native run's existence. They remain available beyond the old timer until instance cleanup removes that run. The adapter binds this policy to RunPreparationService; persisted offers keep their prices and rolls. Existing live claim records refresh their access deadlines before purchase so an older preflight cannot preserve the early expiration. Offline completion recovery keeps its existing deadlines.

Purchase preparation rechecks entitlement access before recording a debit attempt and immediately before calling the economy provider. If cleanup finishes during persistence, no currency is charged. Already purchased or pending rewards retain their ownership and delivery recovery after closure. Restored live rewards cannot start another purchase after reload closes their instance.

## Verification, 2026-10-09

- Java 21 build passed with 321 tests, no failures, errors or skips, including external API shading verification.
- New regression coverage opens and previews the same frozen live offers ten minutes after completion, then denies them when the instance closes. Offline recovery remains available, and restart with no open instance denies live access.
- Claim coverage restores an older provider-blocked preflight, purchases after its original deadline, checks that closing access prevents additional purchases, and delivers the owned reward after closure. Closing during either preparation or debit-attempt persistence never calls the economy withdrawal and leaves no selected claim group.
- JAR `build/libs/DungeonCrawlers-1.0.jar`, SHA-256 `f68232f77b22721851c50f67d3a594b4648d7902eea249a68dc39ca4a7051f84`, uploaded to Modern Cave Crawl (`fa696721`). A console check found zero active instances immediately before `cc reload all` at 19:37:29 UTC. DungeonCrawlers enabled at 19:37:32 and admission reopened at 19:37:38. Recovery IDLE and configuration validation passed.

No commands were executed as a player. Live chest interaction and purchase remain for the owner to verify during their own run; the automated checks cover the reported timeout and payment boundaries.

## Cancelled chest clicks, 2026-10-10

Secret chests, trapped secret chests and the completed boss Ender Chest handle right clicks even when another listener has cancelled the event. Vanilla block interaction remains cancelled after a successful dungeon action. Secret discovery keeps its existing living-participant check, and boss chest handling now uses that same check explicitly. Ghosts, removed players, nonparticipants and offhand interactions cannot trigger these actions. Reward entitlements and purchase validation remain unchanged.

Java 21 clean build passed, followed by a final build with all 424 tests passing and no failures, errors or skips. The event-dispatch regression test registers the real ghost listener and verifies cancelled and uncancelled clicks for all three chest types, living/ghost/removed states, offhand clicks and nonparticipants.

The tested JAR was uploaded to Modern Cave Crawl (`fa696721`), SHA-256 `e545f8ef193278281971ed25c52e307978a6a5f96cc8f8b068369f3a78c43aa6`. The remote checksum matched. Waited for the active boss fight to finish and confirmed zero instances before `cc reload all` at 16:02 UTC. DungeonCrawlers enabled and recovery reported `startsEnabled=true`; compatibility automated checks passed, with the existing Human Gate 0 still requiring manual checks. The broad reload briefly triggered the watchdog and recovered. Actual player chest clicks remain unchecked.

## Reward chest beacon and particles, 2026-10-11

Completed boss reward chests gain a small virtual Beacon model, a full-bright gold/white beam reaching the world's maximum height, end-rod sparkles and a gold particle ring. Three nonpersistent BlockDisplay entities render the model and beam independently of sky access; no roof or arena blocks are removed. The existing once-per-second update loop emits particles and removes the visuals after instance closure or invalidation. Replacement and plugin shutdown remove all displays, and partial initialization cleans up without blocking reward finalization.

Java 21 clean build passed all 428 tests and external-plugin shading verification. Regression coverage verifies beam height, upright geometry, no obstruction reads/block changes, particle emission, replacement, invalid entities, instance closure, idempotent shutdown and partial-spawn cleanup. JAR SHA-256: `9b6979ea5cd76fce27e02808828665de366ec9fdce4af18fa9d3ce11bd0674f5`. Client appearance and a completed live boss chest remain unchecked.

Deployed to Modern Cave Crawl (`fa696721`) with matching local/remote checksum after confirming zero active instances. `cc reload all` enabled DungeonCrawlers and recovery reported `startsEnabled=true`; configuration validation and automated compatibility checks passed. Existing Human Gate 0 remains pending its separate manual checks.
