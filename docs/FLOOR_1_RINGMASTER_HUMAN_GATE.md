# Floor 1 ringmaster validation

Validated on Modern Cave Crawl, Pterodactyl server `fa696721`, 2026-10-09 UTC.

## Build and deployment

- Java 21, `./gradlew clean build --no-daemon`: 332 tests, zero failures or errors. External plugin shading check passed.
- Final JAR: `build/libs/DungeonCrawlers-1.0.jar`.
- SHA-256: `a66fe9e0c3f1b0e861dbc9e86c3ff20e2832598ee536618066c2475486f31ef0`.
- `python3 deploy.py` uploaded the final JAR successfully. `cc reload all` completed; DungeonCrawlers loaded two floors, 15 rooms, two encounters, with recovery blockers zero and starts enabled.
- `dungeon config validate` reported valid after reload.
- The final targeting change additionally checks projectile shooters. Its regression test rejects another instance and ghosts while allowing living participants. The live fight below ran just before this last change.

## Live configuration

Pterodactyl MCP wrote `/plugins/MythicMobs/Mobs/ringmaster.yml`. `mm reload` and `mm mobs info` verified all three definitions: `MadRingmaster`, `MadRingmasterEncore`, and `RingmasterPerformer`.

Pterodactyl MCP changed `/plugins/DungeonCrawlers/floors/floor_1.yml` to schema 3, boss `MadRingmaster`, encounter `ringmaster`. Existing custom templates, rewards, and floor number were preserved. The pre-change backup is `/plugins/DungeonCrawlers/backups/floor_1-before-ringmaster-20261009.yml`.

Floor 1 continues to reference `f1boss`. The user will update that room from the new authoring arena. Version 3 migration preserves other existing custom boss definitions.

## Arena and room capture

`scripts/build_ringmaster_arena.py` produced the schematic and passed its marker and dimension assertions. The schematic was uploaded to `/plugins/FastAsyncWorldEdit/schematics/ringmaster-arena.schem` and loaded using `//schem load sponge ringmaster-arena.schem`.

The new VoidGen world is `ringmaster_authoring`. Actual selection is `30,65,0` through `68,81,38`, inclusive, 39×17×39. The movement floor is roughly 33 blocks across with a 5-block central support, four outer cover pillars, four suit glyphs, a lit rim, and a striped canopy.

Temporary room capture succeeded, proving authoring accepts this schematic. Captured relative markers:

- Boss: `29,2,19`.
- Reward: `19,2,5`.
- Five player spawns: x `15,17,19,21,23`, y `2`, z `33`.

Vanilla block checks verified the boss marker at `59,67,19` and reward marker at `49,67,5`. The user was returned to `49.5,67,32.5`, facing the hub, and the full WorldEdit selection was restored. Save with `/dungeon room update f1boss`.

The initial schematic paste needed an explicit `sponge` format. The generated v2 Offset did not anchor its center at the paste position on this FAWE version; the pasted selection began at the player's block position. Use the actual selection above for this build.

## Live encounter test

Temporary floor `ringmaster_smoke`, Normal, seed `7637527`, instance `801635e5-1211-448c-9065-4c2dbf9b3e88`, used the newly captured arena. The two-member party selected classes and opened its start door.

- `dungeon boss info` reported active encounter `ringmaster`.
- At 20:48:23, entity queries found the first boss and two Undead Performers in the arena.
- The party killed the first form naturally. At 20:48:27, an administrative kill found no active entity during the transition. At 20:48:31, the run remained BOSS, with no premature completion.
- At 20:48:53, boss status reported `Ringmaster encore defeated` and `Completion pending`, with the expected reward location. This verifies the second life spawned and final death completed the run.
- A bounded query verified the first form and performers had cleared. A repeated kill reported no active boss.
- Instance cleanup completed. Temporary floor and room were removed; authoring retained a recoverable room backup. No temporary floor remains active.

## Remaining player checks

This establishes deployment, room capture, minion spawning, the fake-death transition, completion, and cleanup. It does not calibrate baseline gear damage or the 2–3 minute target. Solo/five-player balance, every second-act attack, reduced-particle readability, concurrent-instance testing, and all disconnect/reload paths still need gameplay testing. Existing reward pools remain in use; the proposed Cane and Jester Fragments were not added.
