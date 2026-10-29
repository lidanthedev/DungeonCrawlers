# Dungeon improvements — 2026-10-10

## Implemented

- Required combat enemies are reconciled once per second as well as on Paper removal events. Missing enemies use the configured bounded respawn budget; exhausted recovery releases only the missing requirement and unlocks the next room once all requirements are resolved. This path does not generate death events or loot. Initial spawn errors still fail activation safely.
- Reconciliation waits for the world, room chunks, and chunk entities to be loaded. Administrative clear suppresses removal callbacks before removing entities, preventing reentrant respawns. The explicit administrative `mob remove` suppression behavior is retained.
- Successful GUI and command class selections save `dungeoncrawlers:last_dungeon_class` in each player's persistent data. Preparation applies a saved class after snapshot acknowledgement only if the floor still allows it. Manual changes remain available before the start door opens.
- A conditional dungeon scoreboard uses the existing TAB/PlaceholderAPI installation. It shows floor/difficulty, objective, rooms cleared, secrets, elapsed time, total party deaths, party class/status, and the exact finalized score/rank. Leaving a dungeon restores TAB's existing fallback board through its display conditions.
- Generation/layout/name data is published on the server thread for asynchronous TAB placeholder requests. Finalized runs retain their final elapsed time.

## Automated evidence

- Java 21 `./gradlew clean build --no-daemon` succeeded; the final `./gradlew build --no-daemon` also succeeded after the administrative-clear race fix.
- 346 tests, zero failures/errors/skips. New regressions cover missed removal events, exhausted recovery, unloaded chunks/entities/worlds, administrative removal reentrancy, saved choices across service/adapter recreation, invalid/locked choices, party readiness, asynchronous floor placeholders, party ghost/offline status, and finalized score/time.
- External plugin APIs were not shaded. `git diff --check` passed.
- `build/libs/DungeonCrawlers-1.0.jar` SHA-256: `edc733000eb86f7aafcab30d096edf01078449e9f14d81f9ad5a02a0ff269306`.
- TAB fragment: `server-config/tab/dungeon-scoreboard.yml`; merged live config parses and stays within Minecraft's 15-line limit.

## Live evidence and remaining gate

- Uploaded the tested JAR with `python3 deploy.py` to authorized server `fa696721`.
- TAB config backup: `/plugins/TAB/config.backup-pre-dungeons-20261010.yml`.
- The server finished its independently initiated restart at 00:05:36 UTC. Ran `cc reload all`; actual DungeonCrawlers reload completed at 00:06:07 UTC with 2 floors, 16 rooms, and 2 boss encounters. Ran `tab reload`; TAB confirmed success at 00:06:10 UTC.
- `dungeon config validate` returned `Configuration is valid` at 00:06:18 UTC. `dungeon operations` reported zero active instances/reservations/occupied slots, recovery Idle, starts Enabled, and no recovery blockers at 00:06:33 UTC.
- `papi parse LidanTheGamer %dungeoncrawlers_in_dungeon%|%dungeoncrawlers_rooms_total%|%dungeoncrawlers_sidebar_party_1%` returned `false|0|` outside a run at 00:06:37 UTC. The live TAB config and its backup were read back and matched the intended contents exactly.
- No players were online during deployment. A client visual check and a real two-player run are still pending: select a class once, enter another run to confirm Auto Class, inspect sidebar switching on leave, and test a disappearing required enemy during combat. Adapter recreation verifies the persistent-data key; a complete server restart with a player's saved choice has not yet been exercised.

## Sidebar placeholder and HP follow-up

- The client screenshot exposed a TAB delimiter issue: a literal `%` before the room-counter placeholders caused TAB to leave those counters unresolved. PAPI alone parsed the same line successfully. Reordered the line to show counters first and percentage last; the live TAB parser reproduced the old failure and confirmed the corrected line.
- Party lines now display current Bukkit HP, rounded up with thousands separators and `❤`. Ghosts, dead players, and zero-HP players display `☠`; offline and removed labels retain their existing behavior. Main-thread publication once per second keeps TAB's asynchronous reads away from live Player state.
- Java 21 build passed with 346 tests, zero failures/errors/skips, and the external API shading check. Regression coverage verifies HP updates, rounding, ghost/dead/zero-HP states, offline state, and no asynchronous health reads. Graphify was updated; `git diff --check` passed.
- Deployed JAR SHA-256: `efe7d0bf9487f902a9c52911b59b0a2f6d2eef85d58b8437539ea38b7639f8e1`. TAB backup before the correction: `/plugins/TAB/config.backup-pre-dungeon-hp-20261010.yml`. Live config readback matched the intended content; TAB reload succeeded at 00:30:07 UTC.
- The user explicitly authorized reloading and starting a fresh run. Actual DungeonCrawlers reload completed at 00:32:32 UTC; config validation passed at 00:32:45 UTC. The first fresh run started and activated its first room, then ended; the user opened and stopped an arena preview afterward.
- Started a replacement native Floor 1 Normal run, `f253a14a-49d2-4616-944d-7d72341b235f`. At 00:35:01 UTC, live `tab parse LidanTheGamer %dungeoncrawlers_sidebar_party_1%` returned `[B] LidanTheGamer 27,500❤`. At 00:35:03 UTC, TAB returned `Cleared: 1/9 (11%)` with no unresolved placeholders. At 00:35:05 UTC, PAPI returned `berserker|running|Clear the dungeon`, confirming the saved class in the new run.
- Client appearance has not been reconfirmed with a new screenshot. Skull behavior is regression-tested; no death was forced in the player's live run. The earlier two-player, full-restart persistence, and live disappearing-mob gates remain open.

## Block interactions and secret restoration

- Protected instance regions now deny vanilla block use except at registered preparation/combat doors, secret/blessing chest locations, and finalized reward chests. Decorated pots, flower pots, barrels, switches and unregistered decorative chests are blocked. Both hands, left/right clicks and physical actions are covered; duplicate off-hand block use is denied even at progression locations. The listener preserves item-use decisions and does not affect normal-world or unreserved-world interactions.
- First discovery of a trapped secret chest heals the finder with the native `StatsManager.healPlayerPercent(player, 100D)` API and sets current native MANA to current INTELLIGENCE, the native mana capacity. Chat reports `Secret discovered! Health and mana fully restored.` Already-found secrets do not refill again, blessing chests retain their behavior, and nonliving/removed participants cannot discover a secret through the listener.
- Java 21 build passed all 354 tests, zero failures/errors/skips, plus external API shading and diff checks. Regression coverage includes both hands/physical actions on pots and other decorations, registered block exceptions, unchanged outside-world interactions/item decisions, actual preparation/combat door dispatch, finder-only refill, native mana capacity, repeat discovery, failure, ghost and non-participant restrictions.
- JAR SHA-256: `d576c18831f274136997beddc38857907bbe36cd435464253cc7859ba7230386`. Uploaded with `python3 deploy.py`; no instances were active at the reload check. `cc reload all` completed the actual DungeonCrawlers reload at 01:03:37 UTC. The broad reload briefly produced a ten-second watchdog dump, then all addons finished and the server recovered.
- Config validation passed at 01:03:47 UTC. Operations at 01:03:50 UTC reported zero instances/reservations, recovery Idle, starts Enabled and no blockers. Graphify updated to 3736 nodes / 13020 edges.
- Live player clicks on decorations and a newly discovered secret chest have not yet been exercised after this reload. Automated event tests cover the new behavior; the earlier deferred gates remain open.

## Health colors, current-room secrets and small caps

- Party HP uses current/max native Bukkit health, colored green at 75% and above, yellow from 50% to below 75%, gold from 25% to below 50%, and red below 25%. Ghost/dead skulls and offline/removed states remain distinct. Sidebar labels, floor/objective text, class/name lines and final score use small caps; legacy color codes remain intact.
- Current-room secret counts use each viewer's physical location and the existing SecretDiscoveryService room lookup, including previously cleared rooms. Room counts show found/total separately from the whole run's count. Bukkit location and health reads remain on the server thread, published once per second for asynchronous TAB readers. Between rooms, outside the dungeon world, offline and after leaving, the room count returns 0/0.
- Java 21 clean build passed all 425 tests without failures/errors/skips and passed external API shading verification. Tests cover health thresholds and max-health changes, formatting, independent viewers in different rooms, discovery updates, movement, world/offline/leave fallbacks and asynchronous snapshot reads. The revised TAB fragment parses as YAML with exactly 15 lines.
- Uploaded to Modern Cave Crawl fa696721 with matching local/remote JAR SHA-256 `c8df358af2094a9e5f27a4ee5543e0850c474a5e2ee75c2dc28b43aa296ef53a`. Confirmed zero active instances before `cc reload all`. DungeonCrawlers enabled at 16:39:42 UTC, and recovery reopened admission with startsEnabled=true at 16:39:50 UTC.
- Replaced only TAB's dungeon-board section, preserving all other sections exactly. Backup: `/plugins/TAB/config.backup-pre-room-secrets-20261010.yml`. Config readback matched the prepared update. TAB reload succeeded and dungeon config validation passed. Live TAB parsing resolved complete small-caps room-secrets and cleared-room lines outside a run; PAPI returned `0/0|0|0` for room count/found/total fallbacks.
- Client font appearance and active-run HP colors/room counts remain unchecked in game. Automated tests cover their behavior; no player was moved or injured for verification.
