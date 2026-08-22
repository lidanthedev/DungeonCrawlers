# Phase 14 Operations Runbook

This runbook applies to the DungeonCrawlers development server `fa696721` (`Modern Cave Crawl`).
Do not use it for another server without an explicit phase decision.

## Before deployment

1. Run `./gradlew clean build --no-daemon` with Java 21.
2. Record `sha256sum build/libs/DungeonCrawlers-1.0.jar` and the commit SHA.
3. Create a Pterodactyl backup covering the server configuration, schematics, runtime records,
   and generated world data. Keep the backup until the replacement build passes its human gate.
4. Upload with `python deploy.py`; never commit `.env` or print its credentials.

The plugin already retains versioned config and authoring backups under its data directory. The
durable runtime repository contains generation journals, player snapshots, entitlements, and
claims; do not delete those directories during a cleanup or rollback.

## Health check after deploy

Run `cc reload all`, then verify:

```text
/dungeon operations
/dungeon config validate
/dungeon repository
```

The operations report must show recovery complete, starts enabled, no blockers, and no queued or
in-flight repository work. Inspect the console for `[OPS]` cleanup deadline alerts or recovery
blockers before allowing a player gate.

## Rollback

If the build fails, do not deploy or reload. If the deployed build fails its health check:

1. Stop new starts with the configured admission/reload path; do not delete runtime records.
2. Restore the prior deployable JAR from the recorded artifact or the Pterodactyl backup.
3. If server data was changed, restore the matching backup of config, schematics, runtime, and
   world data as one coherent snapshot. Never mix a newer runtime repository with an older JAR
   unless the compatibility gate explicitly permits it.
4. Run `cc reload all`, `/dungeon operations`, and `/dungeon repository`.
5. If a cleanup deadline or recovery blocker remains, keep the slot/journal blocked and escalate;
   do not force-release it or infer a financial/reward outcome.

Record the failed JAR SHA, prior JAR SHA, backup identifier, console error, and final operations
report in the phase human-gate document.
