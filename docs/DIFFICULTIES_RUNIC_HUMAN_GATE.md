# Difficulty and Runic validation

Date: 2026-10-02 UTC. Server: Modern Cave Crawl, `fa696721`.
Status: implementation deployed; solo checks passed; party/offline and visual checks remain open.

## Build and prerequisites

- Java 21 `./gradlew clean build --no-daemon` passed. The final regression suite contains 276 tests with no failures or skips. External plugin shading verification passed.
- DungeonCrawlers JAR SHA-256: `3f2014579ec3c880e9b60c9262012d98203e5b6d87852d940fa302a10d3d2dd4`.
- CaveCrawlers native API: commit `e640e5111bf3e6cb12f97964f0ea07e6ccf75bfc`; 220 tests executed successfully, 35 environment-dependent tests skipped. H2 award-receipt rollback/reconnect regressions executed successfully.
- CaveCrawlAddon API: commit `54ed322a9d1ccdedbfb188bfc876baab16861ae8`; its five tests passed. DungeonCrawlers also tests native pet definition refresh and lore formatting.
- Native JAR SHA-256 values: CaveCrawlers `9a2620fb1b98bcb4a64b09f652d6139cff8ef416df57e9b56523f0566d6b71c4`; CaveCrawlAddon `1672c38a3dc32ec24b987cf9cf5aec0b0b71005369c8de4a056a690681834582`.
- CI installs the exact native API commits from source. The addon uses an authorized read-only deploy key; credentials are excluded from the repository.

## Deployment and live content

Uploads through `python deploy.py` succeeded. The final code upload occurred at 03:35 UTC.
`cc reload all` then enabled DungeonCrawlers at 03:35:15, with recovery finding zero blockers and starts enabled.
All subsequent reloads use `cc reload all`, per the user's instruction.

Targeted backups were taken before updating the plugin JARs and floor configuration.
The live CaveCrawlers files were created through Pterodactyl and read back:

- `skills/dungeon.yml`: max level 60, native curve, no objectives, no permanent stat rewards.
- `items/RUNIC_FRAGMENT.yml`: Epic material, amethyst shard.
- `items/RUNIC_PET.yml`: Legendary PET, amethyst block. Shop acquisition remains owner-managed.

`dungeon config validate` passed. Existing live floors migrated to schema 2 with their custom content preserved.

## Recorded solo evidence

These checks record the earlier build. Its death-forgiveness results are historical; the current Runic scoring rule is described below under Runic Bonus score.

| Check | Evidence | Result |
| --- | --- | --- |
| Preparation gives no XP | Preparation-only runs timed out/cancelled; later native save still showed Dungeon total XP 0 | Passed |
| Pet acquisition | User confirmed the reissued item works and Runic is active | Passed |
| Native lore style | Golden ability headings, native stat colors/symbols and formatted numbers asserted against the native pet API | Automated pass; visual confirmation pending |
| Solo pet revival | Normal run `fe5cda0e-647d-40c3-a19d-53ebb79865fd`: 03:27:54 death announced five seconds; 03:28:00 alive; 03:28:03 actual deaths 1, forgiven 1, charge used true | Passed |
| Completion XP and unlock | Same run completed at 03:28:27; native save committed Dungeon total XP 100; subsequent Hard admission succeeded | Passed |
| Duplicate completion | Second boss-kill command rejected as inactive; later native reload retained total XP 100 | Passed |
| Hellish pet exception | Debug run `c76a6f15-18e5-44ba-ac1a-ea5585b21ca0`: 03:36:25 five-second timer; 03:36:32 actual deaths 1, forgiven 1, charge used true | Passed |
| Current ghost position | At 03:36:29 the ghost moved to x=5006.5434, z=5008.7957; post-revival position at 03:36:35 matched | Passed |
| MAGIC_FIND multiplication | Normal effective value 100.1; Hellish effective value 125.125 | Passed |
| Once per run/no ordinary revival | Second Hellish death at 03:37:03 wiped the solo party; state remained Ghost, deaths 2, no revive scheduled | Passed |
| Debug progression disabled | Hellish diagnostics reported progression false; subsequent native save still showed Dungeon XP 100 | Passed |
| Runic upgrade and glow | Impossible debug run `69d620c6-0ba2-4a5f-bd03-32b1db32a354`: forced CryptZombie at 03:45:45; health 25,000, Runic name, Glowing=1 | Passed; particle/color appearance awaits visual confirmation |
| Failure XP | Hard run `38d57bf5-6eb8-410d-a270-38e530c100a8` started combat at 03:39:16 and was cancelled; native save committed total XP 112.5, a 12.5 increase | Passed |
| Cleanup | `dungeon instance list` at 03:42:11 showed zero active instances; recovery had no blockers | Passed |

## Reload defect found and repaired

Native config reload mutated/replaced `SkillInfo` objects used as map keys. This created duplicate combat/mining entries and allowed zero defaults to overwrite the test players' progress.
The native map now uses stable skill IDs, and accesses update the definition without creating a second skill. Regression coverage exercises both mutation and replacement.
Both test players' prior combat/mining XP were restored from the captured save values using the native admin restoration path without normal XP events or level-up rewards. Database commit logs confirmed restoration. Reloads at 03:26 and 03:35 read back the original levels/progress, with one record per skill.

## Remaining human checks

- [ ] Party class duplication, uniqueness, admission and distance behavior with both players.
- [ ] Ghost participant receives full successful completion XP and the next tier.
- [ ] Offline participant receives completion XP once after reconnect and retains the unlock.
- [ ] Pet swapping during the five-second ghost window preserves the latched revival.
- [ ] Party Runic bonus appears in the Bonus category without erasing deaths, including runs without a revival.
- [ ] Visual confirmation of the updated pet lore and Runic purple glow/particles.
- [ ] Natural ordinary/miniboss fragment delivery and a Runic boss's four fragments.
- [ ] Reopening completion rewards preserves the frozen offers.

Both players were online, but `party list` at 03:38:20 showed no party. These checks are not claimed as passed. Automated regressions cover damage boundaries, boss exclusions, ghost protection, fixed four-fragment killer ownership, duplicate death events, debug loot suppression, durable inventory overflow, progression recovery and frozen reward weighting.

## Difficulty menu follow-up

2026-10-02: Difficulty IDs autocomplete for normal starts and debug generation. The menu uses distinct colored icons and grouped lore, leaves all four corners empty, and centers Void, Hardcore and Impossible beneath the first seven tiers.

- Java 21 build passed all 278 tests with no failures, errors or skips. Regression checks exercise actual Lamp prefix completion and the opened inventory's slot layout, names and distinct icons.
- Uploaded `build/libs/DungeonCrawlers-1.0.jar`, SHA-256 `ec97da8cac22fca0e35d71b17d15c5a70f09ba847370d7494cb5af714ad4b180`.
- `cc reload all` at 15:48:45 UTC enabled the addon; recovery reported zero blockers and `startsEnabled=true` at 15:49:00.
- Reload emitted transient Essentials command-send exceptions and a ten-second watchdog dump; the server resumed and DungeonCrawlers remained enabled.
- `sudo LidanTheGamer dungeon start floor_1` ran at 15:49:27 with no command exception. Configuration validation passed at 15:49:32. Client appearance and client tab completion still await player confirmation.

## Runic stats, completion revival and door clicks

2026-10-02: Runic now uses native global pet stats of +1 Health, +0.5 Defense and +0.1 Magic Find per level. Runic Power adds dungeon-only health of 0.5% per level, reaching +50% at level 100, and stacks multiplicatively with the Dungeon skill. The live item description was edited and read back through Pterodactyl.

Successful finalization revives all online ghosts at their current positions before publishing the reward chest. Offline ghosts revive when reconnecting during the reward period. This clears timers and ghost restrictions while retaining all deaths. Completion also revives pending Runic ghosts immediately.

Preparation and combat doors accept main-hand left/right block clicks even when an item ability cancelled the event. Both use the shared participant/alive gate; ghosts and off-hand duplicates cannot activate doors.

- Java 21 build passed all 284 tests with no failures, errors or skips. Regressions cover native pet stat scaling, percentage health inside/outside dungeons, completion/reconnect/retry/death counts, reward interaction after ghost cleanup, and actual event dispatch for cancelled/uncancelled clicks, both door types, ghosts and off-hand clicks.
- Uploaded final JAR SHA-256 `22472c4c7b69d7a58baf0cad6c21d6e3e52cd6fcb458e246d0301e71b812b907`. No active dungeons remained before deployment.
- `cc reload all` at 16:14:33 UTC enabled the addon; recovery reported zero blockers and `startsEnabled=true` at 16:14:46. A transient ten-second reload watchdog dump occurred; the server resumed.
- Configuration validation passed at 16:15:07. `/pets` opened for LidanTheGamer at 16:15:16 without a command exception. Real-player confirmation of the new percentage buff, completion chest access and ability-item door clicks remains pending.

## Runic Bonus score

2026-10-02: Runic no longer removes a death. If any remaining participant has Runic active at final scoring, the run gains +2 in the Bonus category, once per run, regardless of deaths or revival. The bonus also appears on failed-run results. The existing score facts freeze the bonus for rendering, chest eligibility, persistence and placeholders.

- Removed the transient forgiven-death counter. Successful pet revival still consumes its one charge, retaining the actual death count. Completion no longer waits for the five-second pet timer before restoring ghosts for rewards.
- Java 21 clean build passed all 285 tests with zero failures, errors or skips. Regressions cover active/inactive bonus, zero/one/two deaths, success/failure, duplicate bonus deduplication, preserved pet-revival death counts and the revised lore.
- Updated the live pet item description through Pterodactyl and read it back.
- Final uploaded JAR SHA-256 `a33b591451d19d715739dc38265dfaf8cec3287214f201b37be4eda299c9b4d3`.
- No active dungeons remained at 18:25:05 UTC. `cc reload all` ran at 18:25:11; recovery reported zero blockers and `startsEnabled=true` at 18:25:26. Configuration validation passed at 18:26:00. Client Bonus-category display still awaits player confirmation.

## Six-row difficulty menu

2026-10-02: Expanded the selector to six rows. Gray glass frames the perimeter while all four corners remain empty. The first seven tiers occupy the third row; Void, Hardcore and Impossible are centered on the fourth. The information icon is centered above the tiers and a bottom-center Close button dismisses the menu without starting a run.

- Java 21 clean build passed all 285 tests with zero failures, errors or skips. The inventory regression checks all border slots, empty corners, tier placement and names, and dispatches the Close click.
- Uploaded JAR SHA-256 `d4a9c280087efddbde9f9a4de4f071423e7de3ab5d4c886a10e56cb0299499d8`. No active instances remained before deployment at 19:40:56 UTC.
- `cc reload all` ran at 19:41:54 UTC. The addon enabled at 19:41:59 and recovery reported zero blockers with `startsEnabled=true` at 19:42:05.
- Configuration validation passed at 19:42:18. `sudo LidanTheGamer dungeon start floor_1` ran at 19:42:23 without a command exception. Client appearance still awaits player confirmation.

## Full glass background

2026-10-02: The latest layout replaces the empty-corner border with gray glass in every unused slot, including all four corners. Tier, information and Close controls keep their positions.

- Java 21 build passed all 285 tests with zero failures, errors or skips. The inventory regression checks every slot is occupied and all unused slots contain gray glass.
- Uploaded JAR SHA-256 `de22541656711d598ee8897772832ed3099672dc09e5c88eb7ef8bb9298479fd`. No active instances remained at 19:46:27 UTC.
- `cc reload all` ran at 19:47:14 UTC; recovery reported zero blockers and `startsEnabled=true` at 19:47:24. Configuration validation passed at 19:47:36.
- Reopened the menu for LidanTheGamer at 19:47:39 without a command exception. Client appearance remains subject to player confirmation.
