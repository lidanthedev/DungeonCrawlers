---
name: dungeoncrawlers-server
description: Deploy and live-test DungeonCrawlers on its Pterodactyl development server. Use after producing a new plugin JAR, when validating Minecraft commands or human gates, or whenever a task asks to deploy, reload, or test DungeonCrawlers on the server.
---

# DungeonCrawlers Server

Use the repository root as the working directory.

1. Complete the relevant automated tests and build the new JAR successfully.
2. Record the built JAR path and SHA-256 checksum.
3. Run `python deploy.py` to upload the JAR. Verify that the command succeeds before continuing.
4. Use the Pterodactyl MCP tools against server `fa696721` (`Modern Cave Crawl`) only.
5. Run the server command `cc reload all` and confirm the addons reload successfully.
6. Run the requested DungeonCrawlers test commands through the server console and capture their results.
7. Report the build, deployment, reload, and command-test evidence to the user.

Do not reload when the build or upload fails. Do not target another server, expose deployment credentials, commit `.env`, or run destructive console commands unless the user explicitly authorizes them.
