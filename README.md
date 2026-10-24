# DungeonCrawlers

Java 21 Paper addon for CaveCrawlers.

Build and test with:

```bash
./gradlew clean build
```

The packaged plugin is written to `build/libs/DungeonCrawlers-1.0.jar`. Required server plugins are CaveCrawlers, FastAsyncWorldEdit, MythicMobs, Vault, and ProtocolLib. Parties, Essentials, PlaceholderAPI, and Citizens are optional. Without Citizens, the class command and GUI continue to work and only the selector NPC is disabled.

Players can use `/dungeon class menu` during the preparation phase. The menu reads the configured classes allowed by the selected floor, shows each class icon and configured stat bonuses, and closes when the start door opens. `/dungeon class list` and `/dungeon class select <id>` remain available as text alternatives.

Administrators can run `/dungeon room setup` to receive the canonical room-marker kit, then use `/dungeon selection validate <type> <encounters>` before `/dungeon room create <id> <type> <encounters>`. The START-room `ORANGE_CONCRETE_POWDER` marker is optional and may appear at most once; it creates a temporary `Class Selector` NPC when Citizens is available. See [room authoring](docs/ROOM_AUTHORING.md), [configuration](CONFIGURATION.md), and [commands](COMMANDS.md).

PlaceholderAPI support is documented in [PLACEHOLDERS.md](PLACEHOLDERS.md), including the class placeholders `%dungeoncrawlers_player_has_class%`, `%dungeoncrawlers_player_class%`, `%dungeoncrawlers_player_class_id%`, and `%dungeoncrawlers_player_class_locked%`.

Phase 1 admin diagnostics are available under `/dungeon`: `config validate`, `reload`,
`floor|room|class|blessing info <id>`, `state simulate`, `score simulate`, `repository`,
and `reservation race`. Configuration reload validates a complete immutable candidate and
preserves the active snapshot on any validation, backup, or reservation-gate failure.
