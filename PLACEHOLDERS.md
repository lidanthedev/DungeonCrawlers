# DungeonCrawlers placeholders

PlaceholderAPI is optional. DungeonCrawlers registers the `dungeoncrawlers`
expansion only when PlaceholderAPI is installed and enabled.

Player-context placeholders use the player supplied by PlaceholderAPI. When the
player is not in a dungeon, booleans return `false`, counters return `0`, and
text values return an empty string. Resolution reads immutable in-memory
snapshots only, so it does not start work, perform I/O, or change a run.

## Player context

| Placeholder | Example | Description |
| --- | --- | --- |
| `%dungeoncrawlers_player_name%` | `LidanTheGamer` | Player name. |
| `%dungeoncrawlers_player_uuid%` | `418dcb3a-...` | Player UUID. |
| `%dungeoncrawlers_player_in_dungeon%` | `true` | Whether the player belongs to an active run. `in_dungeon` is an alias. |
| `%dungeoncrawlers_player_instance%` | `d05c...` | Current instance UUID. `player_instance_id` and `instance_id` are aliases. |
| `%dungeoncrawlers_player_floor%` | `floor_1` | Current floor id. `player_floor_id` and `floor_id` are aliases. |
| `%dungeoncrawlers_player_floor_name%` | `The Crypt` | Current floor display name. |
| `%dungeoncrawlers_player_has_class%` | `true` | Whether the player has selected a class in the current run. |
| `%dungeoncrawlers_player_class%` | `Berserker` | Selected class display name, with MiniMessage formatting removed. |
| `%dungeoncrawlers_player_class_id%` | `berserker` | Selected configured class id. |
| `%dungeoncrawlers_player_class_locked%` | `false` | Whether class selection is locked because the run has progressed beyond `PREPARING`. |
| `%dungeoncrawlers_player_state%` | `ghost` | Player lifecycle state. |
| `%dungeoncrawlers_player_alive%` | `true` | Whether the player is alive. |
| `%dungeoncrawlers_player_ghost%` | `false` | Whether the player is a ghost. |
| `%dungeoncrawlers_player_respawn_seconds%` | `43` | Rounded-up ghost time remaining. `ghost_seconds` is an alias. |
| `%dungeoncrawlers_player_deaths%` | `1` | Deaths recorded in the current run. |
| `%dungeoncrawlers_player_secrets%` | `4/7` | Found and total secrets. |
| `%dungeoncrawlers_player_secrets_found%` | `4` | Secrets found in the current run. |
| `%dungeoncrawlers_player_secrets_total%` | `7` | Total secrets in the current run. |
| `%dungeoncrawlers_player_score%` | `286` | Final score after the run is finalized. |
| `%dungeoncrawlers_player_rank%` | `S` | Final rank after the run is finalized. |
| `%dungeoncrawlers_player_skill_score%` | `96` | Final skill category score. |
| `%dungeoncrawlers_player_time_score%` | `98` | Final time category score. |
| `%dungeoncrawlers_player_exploration_score%` | `90` | Final exploration category score. |
| `%dungeoncrawlers_player_bonus_score%` | `2` | Final bonus category score. |
| `%dungeoncrawlers_player_elapsed_time%` | `14m 32s` | Run duration. |
| `%dungeoncrawlers_player_elapsed_seconds%` | `872` | Run duration in seconds. |
| `%dungeoncrawlers_player_current_room%` | `3` | Viewer's physical room index, or `0` when unavailable. |
| `%dungeoncrawlers_player_current_room_id%` | `crypt_large_01` | Viewer's physical room template id. |

The shorter names `score`, `rank`, `deaths`, `players`, `alive`, `ghosts`,
`secrets_found`, and `secrets_total` are also available in player context.
Current-room secret placeholders are accepted for scoreboard compatibility and
return `0` because secrets are tracked for the whole run.

## Dungeon sidebar

The TAB configuration fragment is `server-config/tab/dungeon-scoreboard.yml`.
Its conditional board displays only while the viewer belongs to a dungeon.

| Placeholder | Example | Description |
| --- | --- | --- |
| `%dungeoncrawlers_rooms_cleared%` | `3` | Cleared combat rooms. |
| `%dungeoncrawlers_rooms_total%` | `9` | Total combat rooms, including miniboss rooms. |
| `%dungeoncrawlers_clear_percent%` | `33` | Integer combat-room completion percentage, without the percent sign. |
| `%dungeoncrawlers_sidebar_floor%` | `Floor I • Normal` | Floor and difficulty, with legacy colors. |
| `%dungeoncrawlers_sidebar_phase%` | `Clear the dungeon` | Current objective or class-selection readiness. |
| `%dungeoncrawlers_sidebar_room_secrets%` | `1/3` | Found/total secrets in the room the viewer is standing in. Returns `0/0` between rooms or outside the dungeon world. |
| `%dungeoncrawlers_sidebar_deaths%` | `2` | Total party deaths. |
| `%dungeoncrawlers_sidebar_score%` | `Score: 305 (S+)` | Final score/rank line; empty before finalization. |
| `%dungeoncrawlers_sidebar_party_1%` | `[B] LidanTheGamer 1,235❤` | Party member class, name, and current HP in small caps. HP color follows remaining percentage. Slots 1–5 are supported; unused slots return empty. Ghosts/dead players show `☠`; offline players show `ᴏꜰꜰʟɪɴᴇ`. |

HP is read on the server thread and published once per second for TAB's
asynchronous readers. Displayed HP rounds up and uses thousands separators. HP is green at 75% or higher, yellow at 50–75%, gold at 25–50%, and red below 25%. Maximum HP and physical room location are also published on the server thread. All sidebar text uses small caps while preserving color codes. The room count updates independently for each viewer, including cleared rooms; it is separate from the overall run secret count.

`%dungeoncrawlers_player_current_room_secrets%` returns the same found/total count; `_found` and `_total` return individual numbers.

TAB can treat a literal `%` between placeholders as the beginning of another
placeholder. Keep the literal percent sign at the end of a line's placeholders:

```yaml
- '&fCleared: &a%dungeoncrawlers_rooms_cleared%/%dungeoncrawlers_rooms_total% &7(&a%dungeoncrawlers_clear_percent%%&7)'
```

Use `/tab parse <player> <text>` to check the complete line in TAB; a successful
`/papi parse` alone does not exercise TAB's parsing behavior.

## Direct instance lookup

Use a UUID in the placeholder name:

`%dungeoncrawlers_instance_<instance-id>_<field>%`

For a player supplied by PlaceholderAPI, use `this` to resolve that player's
active instance:

`%dungeoncrawlers_instance_this_<field>%`

Supported fields are:

`exists`, `id`, `state`, `floor`, `floor_id`, `floor_name`, `seed`, `players`,
`current_players`, `alive`, `ghosts`, `deaths`, `score`, `rank`, `skill`,
`time`, `exploration`, `bonus`, `elapsed_seconds`, `elapsed_formatted`,
`rooms`, `current_room`, `current_room_id`, `boss`, `boss_encounter`,
`reward_seconds_remaining`, and `timeout_seconds_remaining`.

Example: `%dungeoncrawlers_instance_d05c2748-421a-40ec-9832-c8196658c646_state%`
returns `running`. `%dungeoncrawlers_instance_this_state%` returns the state
of the supplied player's active instance. A missing instance or player outside
a dungeon returns `false` for `exists`, `0` for numeric fields, and an empty
value for text fields.

## Global values

| Placeholder | Example | Description |
| --- | --- | --- |
| `%dungeoncrawlers_active_instances%` | `2` | Active prepared runs plus runs currently generating. |
| `%dungeoncrawlers_running_instances%` | `1` | Runs in combat or the boss encounter. |
| `%dungeoncrawlers_completed_instances%` | `1` | Runs in completion/reward state. |
| `%dungeoncrawlers_generating_instances%` | `0` | Runs in planning, journaling, or pasting. |
| `%dungeoncrawlers_active_players%` | `4` | Participants in non-failed runs. |
| `%dungeoncrawlers_version%` | `1.0` | Plugin version. |
| `%dungeoncrawlers_debug%` | `false` | Whether debug mode is enabled. |
