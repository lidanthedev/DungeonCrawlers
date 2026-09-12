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
| `%dungeoncrawlers_player_current_room%` | `3` | Active combat room index, or `0` when unavailable. |
| `%dungeoncrawlers_player_current_room_id%` | `crypt_large_01` | Active combat room template id. |

The shorter names `score`, `rank`, `deaths`, `players`, `alive`, `ghosts`,
`secrets_found`, and `secrets_total` are also available in player context.
Current-room secret placeholders are accepted for scoreboard compatibility and
return `0` because secrets are tracked for the whole run.

## Direct instance lookup

Use a UUID in the placeholder name:

`%dungeoncrawlers_instance_<instance-id>_<field>%`

Supported fields are:

`exists`, `id`, `state`, `floor`, `floor_id`, `floor_name`, `seed`, `players`,
`current_players`, `alive`, `ghosts`, `deaths`, `score`, `rank`, `skill`,
`time`, `exploration`, `bonus`, `elapsed_seconds`, `elapsed_formatted`,
`rooms`, `current_room`, `current_room_id`, `boss`, `boss_encounter`,
`reward_seconds_remaining`, and `timeout_seconds_remaining`.

Example: `%dungeoncrawlers_instance_d05c2748-421a-40ec-9832-c8196658c646_state%`
returns `running`. A missing instance returns `false` for `exists`, `0` for
numeric fields, and an empty value for text fields.

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
