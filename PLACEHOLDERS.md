# DungeonCrawlers placeholders

The PlaceholderAPI expansion is optional. It is registered only when PlaceholderAPI is installed and enabled.
All values are read from the in-memory dungeon snapshots and return an empty value when the player or instance is
not available. Placeholder resolution never starts a scan, performs I/O, or throws for malformed context.

Use placeholders with the `dungeoncrawlers` identifier:

| Placeholder | Value |
| --- | --- |
| `%dungeoncrawlers_in_dungeon%` | `true` when the player is a participant in an active dungeon, otherwise `false` |
| `%dungeoncrawlers_instance_id%` | Active dungeon UUID, or empty |
| `%dungeoncrawlers_instance_state%` | Preparation, running, boss, completion, or failed state, or empty |
| `%dungeoncrawlers_floor%` | Active floor id, or empty |
| `%dungeoncrawlers_score%` | Current score when a completed result is available, otherwise empty |
| `%dungeoncrawlers_player_state%` | `alive`, `ghost`, or `removed`, or empty |
| `%dungeoncrawlers_deaths%` | Player deaths in the active run, or empty |
| `%dungeoncrawlers_ghost_seconds%` | Rounded-up ghost seconds remaining, or `0` |
| `%dungeoncrawlers_players%` | Number of active participants, or `0` |
| `%dungeoncrawlers_alive%` | Number of alive participants, or `0` |
| `%dungeoncrawlers_ghosts%` | Number of ghost participants, or `0` |
| `%dungeoncrawlers_secrets_found%` | Discovered secrets when the active snapshot is available, or `0` |
| `%dungeoncrawlers_secrets_total%` | Total secrets when the active snapshot is available, or `0` |

The expansion is a soft dependency. Removing PlaceholderAPI does not prevent DungeonCrawlers from starting.
