# Room preview verification

## Behavior

`/dungeon room preview <id>` requires a player and `dungeoncrawlers.admin.authoring`. It autocompletes configured room IDs, loads only the selected template, and generates a single placement in the dedicated instance world. The shared slot allocator and player reservations prevent overlap with other instances or an active dungeon. Bounds must fit the slot. The shared generation journal is acknowledged before FAWE pastes, and the slot stays blocked until clear and journal deletion finish.

Preview does not register preparation, combat, lifecycle, secrets, bosses, or rewards. Progression is disabled. The player enters spectator mode only after paste, destination chunk loading, and durable return-state acknowledgement. `/dungeon room preview stop`, disconnect, leaving the world, and admin instance cleanup close it. Reload retains the generation journal and any unfinished player recovery snapshot for startup recovery.

## Automated evidence, 2026-10-09

- Java 21 clean build passed, followed by the final build with 309 tests, no failures, errors, or skips. External plugin API shading verification passed.
- Actual Lamp command-tree tests cover room ID prefixes, admin permissions, and dispatch of the literal `preview stop` command.
- Regression tests cover isolated allocation beside a live instance, journal acknowledgement before paste, clear acknowledgement before slot release, unknown rooms, progression rejection, selected-template loading, and slot bounds.
- Player boundary tests cover delayed chunk/snapshot callbacks, quit/cancel/admin cleanup, failed chunk/snapshot/teleport, original location/game mode restoration, and plugin disable retaining the durable return snapshot.
- Multiverse 5.3.4 defers world game mode enforcement by one tick after the world-change event. Recovery reapplies the saved mode three ticks after restoration and deletes the snapshot only after confirming that mode. Preview entry also reapplies spectator mode after world enforcement. Regression tests reproduce the delayed override.

Multiverse scheduling reference: [MVPlayerListener, 5.3.4](https://github.com/Multiverse/Multiverse-Core/blob/5.3.4/src/main/java/org/mvplugins/multiverse/core/listeners/MVPlayerListener.java).

## Live evidence

Server: `fa696721`, Modern Cave Crawl. Test player: LidanTheGamer. All timestamps are UTC on 2026-10-09.

Final JAR: `build/libs/DungeonCrawlers-1.0.jar`. SHA-256: `348d59f423c2283fdc806b6894c054f91f8062d49b7ac24a7ef1b10a3e134ae6`.

The initial normal, boss, and portal previews generated one placement each and cleaned successfully. Live testing exposed Multiverse overwriting creative return mode. A one-tick correction still failed on the server; the final three-tick correction passed. The first reload emitted the previously observed watchdog dump while PlugMan remapped the plugin and then recovered.

- 17:55:16: deployed final JAR and ran `cc reload all`. Configuration validation passed at 17:57:57; recovery was IDLE with no blockers.
- 17:58:07: `dungeon_start` preview `50914b6f-ae6b-44e4-8095-f9130f384595` generated one placement. Entity data confirmed spectator mode (`3`) in `minecraft:dungeon_instances`.
- 17:58:22: `preview stop` completed cleanup. Entity data confirmed creative mode (`1`) in the original `minecraft:deepmines` world at the exact original coordinates `(1162.3434250622552, 310.5, 56.946304991258025)`. All 20 slots were free.
- 17:58:50: `crypt_hallway` preview `399e8780-730f-484e-acad-cffaa812a83b` generated one placement and entered spectator mode. Ran `cc reload all` while it was active at 17:58:55.
- 17:59:06–17:59:21: startup recovery discovered and cleared one instance with no blockers. The player returned to the same exact DeepMines coordinates in creative mode; all 20 slots were free.

Client room appearance and disconnect/reconnect rendering require visual confirmation. Recovery ordering and disconnect cancellation have automated coverage.
