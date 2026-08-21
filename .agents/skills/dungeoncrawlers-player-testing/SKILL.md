---
name: dungeoncrawlers-player-testing
description: "Test DungeonCrawlers player-facing commands on the development server, including commands that require a player sender."
---

# DungeonCrawlers Player Testing

Use this skill when live testing needs a real player sender rather than the server console.

- On server `fa696721` (Modern Cave Crawl), use `/sudo LidanTheGamer <command>` to execute a player command as `LidanTheGamer`.
- Prefer `/sudo` for player-only DungeonCrawlers commands such as `/dungeon start`, class selection, teleport, `whereami`, and secret or blessing interactions when a second account is not available.
- Capture the resulting chat output and verify both the command result and the relevant in-game state.
- Do not use `/sudo bigbou`; the authorized test player is `LidanTheGamer`.
- Keep destructive or cleanup commands within the user-requested test scope.

For server deployment, reload, and console commands, also follow the project `dungeoncrawlers-server` skill.
