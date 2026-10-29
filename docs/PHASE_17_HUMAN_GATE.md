# Phase 17 human test gate

Run this checklist on the development server after building and deploying the plugin. Record the result and server log excerpts before continuing the phase.

## A. Class GUI

- [ ] Start a solo run and wait for preparation to finish.
- [ ] Run `/dungeon class menu` as a player; the GUI opens with the floor's configured classes, icons, stat bonuses, requirements, and selected state.
- [ ] Select a class; the selection persists, the door state refreshes, and the action bar/message updates.
- [ ] Repeat with a party and confirm every active member must choose before the door becomes ready.

## B. Guards and locking

- [ ] Confirm a non-member cannot select through a stale GUI or selector interaction.
- [ ] Open the start door, then confirm the GUI is closed and class selection is locked.
- [ ] If the selector NPC remains, right-clicking it reports the locked state and does not change the run.

## C. Citizens integration

- [ ] With Citizens enabled, confirm the generated START room has one NPC named `Class Selector` at the transformed marker location.
- [ ] Right-click it and confirm it opens the same GUI as `/dungeon class menu`.
- [ ] Cancel, time out, or complete cleanup and confirm the NPC is removed.
- [ ] Restart the server and confirm no selector NPC is restored from disk.

## D. No Citizens fallback

- [ ] Disable or remove Citizens and restart DungeonCrawlers.
- [ ] Confirm one clean `Class Selector NPC: No` log line and no class-feature startup failure.
- [ ] Confirm generation, `/dungeon class menu`, text selection, and the start door still work.
- [ ] Confirm the orange marker is removed after paste and no NPC is spawned.

## E. Room authoring

- [ ] Run `/dungeon room setup` and confirm one clearly named item for every canonical marker; unrelated inventory items remain.
- [ ] Place entrance/exit Jigsaws and confirm their native fields are correct; ordinary Jigsaws remain unchanged.
- [ ] Validate START with zero and one orange marker; confirm only one succeeds.
- [ ] Validate non-START rooms with orange markers and confirm rejection.
- [ ] Confirm output includes marker counts, `Class Selector NPC: Yes/No`, and a Citizens-unavailable warning when applicable.

## F. PlaceholderAPI

- [ ] Check `%dungeoncrawlers_player_has_class%`, `%dungeoncrawlers_player_class%`, and `%dungeoncrawlers_player_class_id%` before and after selection.
- [ ] Check `%dungeoncrawlers_player_class_locked%` before and after opening the door.
- [ ] Check the same placeholders outside a dungeon; they return safe empty/false values.

## G. Safety and presentation

- [ ] Exercise cancel, timeout, reload, disconnect, and plugin-disable cleanup; no stale GUI, NPC, marker, or player snapshot remains.
- [ ] Confirm no unrelated blocks, Jigsaws, inventory items, or persisted Citizens NPCs are changed.
- [ ] Confirm player-facing output contains no legacy section-sign formatting and logs contain no repeated NPC cleanup errors.

## Recorded human-gate findings

2026-09-14 findings from the live A–G pass:

- A. The class menu opened correctly and every party member could choose. Selection persisted, but the GUI flashed as it reopened instead of refreshing in place.
- B. A non-member sent to the preparation area received `You are not preparing a dungeon.` The GUI closed when the door opened. The selector NPC remained and rejected later selection with `Class selection is locked once the dungeon starts.`
- C. With Citizens enabled, the START-room `Class Selector` NPC spawned, cleanup removed it, and `cc reload all` left no NPC behind. The NPC skin was random on each run; the skin-config retest remains open until the configured skin is visually confirmed.
- D. With Citizens unloaded, no NPC or selector marker was present and the dungeon still worked.
- E. `/dungeon room setup` returned the expected markers, Jigsaw fields were correct, and START validation rejected two `ORANGE_CONCRETE_POWDER` markers while allowing zero or one.
- F. PlaceholderAPI returned `true`, `Berserker`, `berserker`, and `false` before the door, then `true` for the lock after the door. Outside a dungeon, the placeholders returned safe empty/false values.
- G. The follow-up implementation removed internal class/reward ids from player-facing GUI text and changed unaffordable rewards to a locked state. Repeat this check after the skin retest.

The live server config was checked on the same date. It contains `schema-version: 6` and
`class-selector.npc.skin: LidanTheGamer`. The value is in the correct location; the random skin
was caused by the Citizens trait lookup in the integration, not by the server YAML.
