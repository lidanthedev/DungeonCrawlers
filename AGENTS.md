# Repository Guidelines

## Project Structure

DungeonCrawlers is a Java 21 Paper addon for CaveCrawlers.

- `src/main/java/me/lidan/dungeonCrawlers/` contains the plugin. Keep platform-neutral dungeon behavior in `core/`; put Bukkit and optional-plugin adapters in `integration/`. `commands/`, `config/`, `authoring/`, `persistence/`, and `compatibility/` own their matching boundaries.
- `src/test/java/` contains JUnit 5 and Mockito tests that mirror production packages.
- `src/main/resources/` contains `plugin.yml` and versioned YAML for config, floors, rooms, classes, and blessings.
- `docs/` records phase human-gate evidence. `.agents/skills/` contains live server/player-testing procedures; read the current handoff in `work/handoffs/` for phase context, then verify it against the tree.

## Build, Test, and Development Commands

Use Java 21 and the Gradle wrapper:

```bash
./gradlew test
./gradlew clean build
./gradlew test --tests 'me.lidan.dungeonCrawlers.core.generation.GenerationServiceTest'
```

`clean build` runs tests, produces `build/libs/DungeonCrawlers-1.0.jar`, and verifies external plugin APIs were not shaded. CI runs `./gradlew clean build --no-daemon`. For live validation, build first, then follow the server skill; deployment is `python deploy.py` with credentials kept in the ignored `.env`.

## Coding Style and Conventions

Use four-space indentation, lowercase package names, `UpperCamelCase` types, `lowerCamelCase` methods/fields, and `UPPER_SNAKE_CASE` constants. Preserve existing formatting; no separate formatter or linter task is configured. Keep optional integrations behind enabled-plugin checks, keep external APIs compile-only, and route player-facing text through `MiniMessageUtils`. When changing Boosted YAML, update its `schema-version` and migration coverage.

## Testing Guidelines

Name tests `*Test` and place them beside the matching package. Add regression coverage for behavior, config migrations, persistence/versioning, lifecycle/concurrency, and adapter changes. No coverage threshold is configured. Human-gate documents supplement automated tests; use the relevant `docs/PHASE_*_HUMAN_GATE.md` and player-testing skill for player-facing changes.

## Commits and Pull Requests

Follow the history’s scoped Conventional Commit style, such as `feat(phase9): ...`, `fix(snapshot): ...`, or `docs(phase8): ...`. Keep each phase on its own branch/PR from updated `master`, preserve unrelated work, and do not amend or reset others’ changes. PRs should summarize behavior, tests, config/resource changes, and human-gate evidence, link the phase/issue, and have green CI. Never commit credentials, build output, or generated `dungeoncrawlers-plan` files.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

When the user types `/graphify`, use the installed graphify skill or instructions before doing anything else.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- Dirty graphify-out/ files are expected after hooks or incremental updates; dirty graph files are not a reason to skip graphify. Only skip graphify if the task is about stale or incorrect graph output, or the user explicitly says not to use it.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
