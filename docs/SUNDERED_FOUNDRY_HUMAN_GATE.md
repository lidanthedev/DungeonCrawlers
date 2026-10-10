# Sundered Foundry human gate

Date: 2026-10-10, Asia/Jerusalem. Target development server: Modern Cave Crawl, Pterodactyl `fa696721`.

## Automated evidence

- Java 21 `./gradlew clean build --no-daemon` passed 374 tests with no failures; the final `./gradlew build --no-daemon` passed all 375 tests, including external-plugin shading verification. A prior run hit the existing `FileDurableRepositoryTest.reservedTerminalLaneBypassesNormalSaturation` timing race. Its isolated retry and the next full clean build passed. No persistence code was changed.
- Added boss state-machine tests cover introduction, exact transformation timing, Impossible-only mechanics, a burst crossing both thresholds, one transformation without respawn, delayed one-time victory, missing entities, animation exceptions, wipe during transformation and invalid configuration.
- Added puzzle tests cover wrong-sequence reset, separate instance progress, distinct multiplayer holders, continuous hold time, solo latch requirements and disconnect/ghost fallback.
- Combat regression proves mob kills cannot open the next room before the mechanism is solved, and objective cleanup runs once.
- Scene regression executes the whole transformation and bridge mutation, verifies thousands of real block changes, enforces bounds/display limits, and restores every saved block state and display exactly once.
- Pack tests cover resource registration, settings validation/hash changes and schema-3 migration that preserves administrator edits.
- `python3 scripts/test_foundry_floor.py`: three tests passed. They decode every shipped schematic and compare all blocks, check connector NBT and marker support, walk ordinary room routes and secret entrances, and verify every lower-arena refuge remains connected with each cardinal bridge individually removed.

## Deployment and live evidence

The user explicitly approved uploading the JAR, reloading the addon and MythicMobs, and testing with `LidanTheGamer` on `fa696721`. Deployment through `python3 deploy.py` succeeded. `cc reload all` loaded three floors, 26 rooms and three boss encounters; recovery reported zero blockers. `dungeon config validate` passed. Paper reports 1.21.10; compilation uses the project's 1.21.11 API. Chain materials resolve by name for compatibility.

Final JAR: `build/libs/DungeonCrawlers-1.0.jar`.
SHA-256: `b06404cb98d0f414021cbfc02c7501442016544a7b7961b9468e2c20c5433fd3`.

Live findings corrected before completion: the adapter requires Sponge **v3**, with an unnamed root containing `Schematic`, a `Blocks` container and nested block-entity `Data`. Initial incompatible test assets never pasted and were renamed with `.initial-invalid` / `.v2-invalid` suffixes on the development server. The regenerated v3 pack loaded all ten templates and generated ten placements. The class-selector NPC destination also needs its already-generated chunk loaded after generation releases tickets; the shared Citizens adapter now does that before spawning.

`mm reload` completed; all five new mob IDs resolve from `dungeoncrawlers_foundry.yml`, and no Foundry-specific skill errors were logged. Unrelated existing errors concern Jasper meteor, seaplatypus, MIR/RRT targeters and a deepmines STATE mechanic. This task did not edit those definitions.

Tests below used existing admin paths and vanilla damage commands where physical client input was unavailable. Boss inspection used a temporary Creative state to avoid an unattended player wipe. Snapshot restoration returned the player to Survival. These results establish server mechanics, not survival balance or client presentation.

| Check | Observed result |
| --- | --- |
| Normal entry | `1dc4d7e8-e9c7-40b6-992f-336cfd366085`: ordinary `/dungeon start floor_3 normal` generated ten placements; class selection and debug door interaction activated room 1; three required mobs were alive, none failed. |
| Normal boss | INTRO → FIRST → FINAL. Health started at 90 million; an 80-million damage command left 10 million. Boss position changed through AI movement. The sampled upper deck remained present; no TRANSFORM occurred. An unattended player death triggered failure and automatic cleanup. |
| Impossible cinematic | `d8d81038-2847-4a88-9278-0d3c7597746f`, seed 3003: health started at 450 million. A two-billion damage command was capped to 292,495,500 health. Exactly one TRANSFORM at 01:43:03 UTC and RIVEN at 01:43:17 UTC. Upper deck `(5058,75,8040)` became air; boss and player moved to Y=66. |
| Enrage and owned entities | Further actual damage entered FINAL. The encounter remained active through multiple attack cycles. NBT-tag selector found 142 owned display entities, within the configured cap. The lower refuge `(5057,65,8057)` remained solid. |
| Death and rewards | Actual lethal damage entered DYING at 01:44:35 UTC. Completion followed the five-second sequence. Deck `(5058,75,8040)` restored to polished diorite; the owned-tag selector found zero entities; player returned to Y=76. The reward marker became an ender chest. Reward info showed score 200, one participant, an available free wooden offer and a score-locked obsidian offer. Debug progression was disabled, so this does not establish victory XP. |
| Puzzle gate | `ecf5d66a-bfd5-4d89-a7dc-8799a63b213f`: vanilla lethal damage killed both required Sentinel/Cantor entities. Room 1 remained ACTIVE with “Enemies defeated · solve the room mechanism to continue.” |
| Secrets/blessings | Admin discovery registered a standard cache; repeating it returned “already discovered.” A blessing cache registered `crypt_strength`, and blessing info showed level 2. Physical discovery/reachability remains a client check. |
| Wipe during transformation | Same instance: TRANSFORM at 01:47:40 UTC; a second oversized damage command at 01:47:43 was refused as invulnerable and health remained 292,495,500. Admin wipe at 01:47:49. Before map clearing, the sampled deck was restored; owned displays were absent. Player snapshot returned Survival, then generation cleanup completed. |
| Final preparation retry | `12b9e9f9-27e0-4e95-8a3b-75392b716294` generated ten placements after the NPC chunk fix, reached first-room activation, and cleaned up. The earlier Citizens spawn-failure warning did not recur. NPC rendering/clicking still needs client verification. |
| Existing floors | `floor_fast` generated five placements and activated five miniboss mobs. `floor_1` generated twelve placements, passed the class/start gate, and started the existing `ringmaster` encounter. Both cleaned up. Ringmaster smoke emitted existing missing Crypt/LostAdventurer recovery warnings after the admin boss jump; this was not a full Floor I clear. |

## Remaining human acceptance pass

These require real client input or a party and remain unchecked:

- [ ] Confirm class-selector NPC visibility/clicking, then solve the seeded resonance puzzle physically, intentionally reset it once, and verify door opening after both puzzle and enemy completion.
- [ ] Test counterweight latch/plate interactions solo and with two players; verify clues and feedback are readable.
- [ ] Walk every secret entrance, collect caches physically, traverse the skyway and upper stair, and test fall checkpoints.
- [ ] Watch the entire cinematic from multiple positions and reduced particle settings. Assess actual chain/slab/pier motion, lighting, sound mix and telegraph visibility.
- [ ] Fight in Survival: dodge each attack, observe actual bridge retraction collision, reach guillotine refuges, jump rift rings and handle Last Weave overlaps. Tune health/damage/timing from those runs.
- [ ] Exercise disconnect/reconnect and participant departure live; wipe during introduction, retraction and final phase. Automated lifecycle/state tests cover these paths; the live wipe pass covered transformation and a Normal final-phase death.
- [ ] Complete a full ordinary run, physically open/claim rewards, and verify victory XP/progression. Admin boss jumps were used for encounter testing.
- [ ] Five-player load: synchronized presentation, collision safety, frame rate, server tick time and balance.

## Balance revision, 2026-10-10

User requested stronger Floor III mobs after comparing their low HP with Floor I. Live `/plugins/MythicMobs/Mobs/dungeon_mobs.yml` confirmed Crypt enemies at 5M HP / 400K damage and minibosses at 20M / 1M. Live `ringmaster.yml` has 55M / 2M and an Encore at 75M / 3M.

- F3 base stats now match the combat tuning table in [SUNDERED_FOUNDRY.md](SUNDERED_FOUNDRY.md). Hammer damage is 1.2M and Cantor bolt damage is 1M. Timing and phase thresholds are unchanged.
- Java 21 `./gradlew build --no-daemon` passed, including all 375 tests and the external API shading check. JAR SHA-256: `dd1ef3f6c333495302c6dc1b7b351e24a7eeac97aabf579e02f1a2ffa4732b51`.
- Uploaded successfully to development server `fa696721`; backed up the three edited server YAML files with `.before-balance-20261010`, then patched only Foundry stat values. User approved closing the active run for reload.
- `cc reload all` completed, DungeonCrawlers loaded three floors / 26 rooms / three encounters with config hash `508d4f82c922ea31c90ccd171007825a1a4279ce37ac944005c70ed51d625e1a`. `mm reload` completed with previously known unrelated pack warnings and no Foundry definition errors.
- `mm mobs info` verified all five loaded F3 HP and damage values at 08:29 UTC.
- Debug Impossible run `41615ebe-840c-4d49-ab5c-7c1b86892b04`, seed 3003, prepared and activated its first room with Sentinel and Cantor alive. Boss health NBT read at 08:30:38 UTC returned `1.0E9f`, confirming 1B solo Impossible HP without double scaling. Test run closed after the read.
- This revision did not repeat the entire cinematic or measure Survival/party fight duration. Earlier cinematic evidence remains above; balance still needs a player combat pass.
