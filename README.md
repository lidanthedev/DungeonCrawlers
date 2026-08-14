# DungeonCrawlers

Java 21 Paper addon for CaveCrawlers.

Build and test with:

```bash
./gradlew clean build
```

The packaged plugin is written to `build/libs/DungeonCrawlers-1.0.jar`. Required server plugins are CaveCrawlers, FastAsyncWorldEdit, MythicMobs, Vault, and ProtocolLib. Parties and Essentials are optional and use fail-closed/explicit fallback paths.

Phase 1 admin diagnostics are available under `/dungeon`: `config validate`, `reload`,
`floor|room|class|blessing info <id>`, `state simulate`, `score simulate`, `repository`,
and `reservation race`. Configuration reload validates a complete immutable candidate and
preserves the active snapshot on any validation, backup, or reservation-gate failure.
