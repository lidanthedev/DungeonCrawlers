# DungeonCrawlers commands

All commands use the `/dungeon` root. Player-facing messages use the plugin's MiniMessage formatting and the class menu is player-only.

## Player commands

| Command | Permission | Purpose |
| --- | --- | --- |
| `/dungeon start <floor> [difficulty]` | `dungeoncrawlers.use` | Leader selects the difficulty; omitted tier opens the menu. All members must unlock it on that floor. |
| `/dungeon class menu` | `dungeoncrawlers.command.class` | Open the class GUI while the run is still preparing. |
| `/dungeon class list` | `dungeoncrawlers.use` | List floor-allowed classes as clickable text. |
| `/dungeon class select <id>` | `dungeoncrawlers.use` | Select a class by its configured id. |
| `/dungeon whereami` | `dungeoncrawlers.use` | Show the current dungeon location. |
| `/dungeon reward open` | `dungeoncrawlers.use` | Open completed rewards. |

The class menu and selector NPC recheck run membership, snapshot readiness, allowed class ids, and the PREPARING state on every selection. Selection is locked after the start door opens, while the NPC may remain present as a locked signpost until cleanup.

`/dungeon completions add <player> <floor> <difficulty> [amount]` requires `dungeoncrawlers.admin.progression` (operators by default). Amount defaults to 1 and must be positive. It adds persistent completion credit for the selected floor/tier and unlocks through the next tier, without XP, loot or a simulated run. It supports online players and previously joined offline players, and works with debug mode disabled. Each party member needs their own unlocks.

Example: `/dungeon completions add LidanTheGamer floor_1 hardcore 1` unlocks Impossible and all lower tiers on floor_1. Grant the same credit to Eldan39 to test together.

Difficulty diagnostics require `dungeoncrawlers.admin.debug`:

- `/dungeon difficulty info [player]` reports effective MAGIC_FIND, deaths and the pet charge.
- `/dungeon instance generate-difficulty-debug <floor> <tier> <seed>` uses normal preparation with unlock checks bypassed. It awards no Dungeon XP, unlocks or Runic fragments.
- `/dungeon runic force <entity-uuid>` upgrades an enemy only in a debug instance.
- `/dungeon door interact` exercises the same class/snapshot gate and lifecycle as a start-door click.

Generation, Runic forcing and door interaction require debug mode to be enabled.

## Room authoring

| Command | Permission | Purpose |
| --- | --- | --- |
| `/dungeon room setup` | `dungeoncrawlers.admin.room` | Refresh the canonical marker kit without replacing unrelated items. |
| `/dungeon selection markers` | `dungeoncrawlers.admin.authoring` | Count and inspect markers in the WorldEdit selection. |
| `/dungeon selection validate <type> <encounters>` | `dungeoncrawlers.admin.authoring` | Validate marker counts, connector metadata, and room-type rules. |
| `/dungeon room info <id>` | `dungeoncrawlers.admin.config` | Inspect the configured room definition. |
| `/dungeon room create <id> <type> <encounters>` | `dungeoncrawlers.admin.authoring` | Capture, validate, and save a room. |
| `/dungeon room update <id>` | `dungeoncrawlers.admin.authoring` | Replace an authored room while preserving its configured definition. |
| `/dungeon room delete <id>` | `dungeoncrawlers.admin.authoring` | Delete an unused authored room. |
| `/dungeon room paste <id> <rotation>` | `dungeoncrawlers.admin.authoring` | Paste an authored room for inspection. |

`<type>` is `normal`, `start`, `portal`, or `boss`. `<encounters>` is `none`, `normal`, `miniboss`, `normal,miniboss`, or `miniboss,normal`. The validator reports the total marker count and `Class Selector NPC: Yes/No`. It also warns when Citizens is unavailable.

## Diagnostics

`/dungeon help` shows the common player and administrator commands. Other phase and compatibility commands remain available to operators with their existing `dungeoncrawlers.admin.*` permissions; use `/dungeon help` and the command suggestions for those paths.

## Admin autocomplete

Tab completes configured floor, room, class, blessing, difficulty and reward IDs, player names, and known instance IDs. Room creation and selection validation suggest room types and encounter lists, including `normal,miniboss` and the reverse order. Typing `normal,` suggests the remaining miniboss capability. Room paste and connection tests suggest rotations `0`, `90`, `180`, and `270`.

Door commands suggest cardinal facings and door states; state simulation suggests dungeon states. Reward reconciliation suggests claims waiting for review and `charged`/`not-charged`; delivery pause suggests `on`/`off` and boolean aliases. Compatibility and mob-spawn commands read the native CaveCrawlers item and MythicMobs ID registries.

Combat room indices follow the selected instance, including the player-relative `this` alias. Mob kill/remove suggest living required entities in the selected room. Runic forcing suggests tracked dungeon enemy UUIDs; its existing debug-instance restriction still applies. Runtime lists update as rooms, entities and claim states change.

New room names, seeds, amounts, coordinates, durations and reconciliation evidence remain free input. Autocomplete does not execute commands or change their permission/validation rules.
