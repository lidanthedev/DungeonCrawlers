# DungeonCrawlers

Java 21 Paper addon for CaveCrawlers. Development follows the dependency-ordered phase gates in the local architecture pack; each phase is developed on its own `agent/phase-*` branch and is not published until its human gate passes.

Build and test Phase 0 with:

```bash
./gradlew clean build
```

The packaged plugin is written to `build/libs/DungeonCrawlers-1.0.jar`. Required server plugins are CaveCrawlers, FastAsyncWorldEdit, MythicMobs, and Vault. Parties and Essentials are optional and use fail-closed/explicit fallback paths.

See [the Phase 0 human gate](docs/PHASE_0_HUMAN_GATE.md) before testing on staging. The PR remains blocked until [the supported stack record](docs/SUPPORTED_STACK.md) is complete and marked PASS.
