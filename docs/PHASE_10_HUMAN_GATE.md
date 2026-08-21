# Phase 10 Human Gate

Status: PASS

## Required checks

- [x] Run `/dungeon score simulate true 2 8 0 0`; hover the result and verify `Skill: 96`.
- [x] Run `/dungeon score simulate false 0 8 0 0`; verify the result is marked `FAIL` and the hover shows `Skill: 0`.
- [x] Run `/dungeon score simulate true 0 20 0 0`; verify `Time: 76` in the hover.
- [x] Run `/dungeon score simulate true 0 8 11 12`; verify `Exploration: 92` (half-up rounding).
- [x] Run `/dungeon score simulate true 0 8 0 0`; verify zero configured secrets produce `Exploration: 100`.
- [x] Verify rank boundaries with `/dungeon score simulate true 2 8 7 10` (`266`, A),
  `/dungeon score simulate true 0 8 7 10` (`270`, S), and
  `/dungeon score simulate true 0 8 0 0` (`300`, S+).
- [x] Hover every result and confirm the multiline categories are present; when bonus providers are
  configured, each bonus fact includes its key, points, and detail.

## Automated evidence

- Java 21 Gradle test suite: all tests passing (count recorded by the build output).
- The final score report evaluates bonus providers once, sorts them by priority and ID, ignores
  duplicate fact keys, clamps bonus points to 0..100, and is immutable for renderer/entitlement reuse.

## Evidence

- Tester: LidanTheGamer
- Date: 2026-08-22
- Server: `fa696721` (Modern Cave Crawl)
- JAR SHA-256: `5CD61B31BF490F0F9E3522CEE3083BBF6A66AC237E0CD8DF6F1C1B9A8D07707B`
- Deployment: `python deploy.py` uploaded the JAR; `cc reload all` reported DungeonCrawlers reloaded.
- Console evidence: `296 (S)`, `200 (B, failed)`, `276 (S)`, `292 (S)`, `300 (S+)`,
  `266 (A)`, `270 (S)`, and `300 (S+)` were observed. Hover payloads confirmed Skill, Time,
  Exploration, Bonus, Total, and Rank lines; automated tests cover bonus-fact detail rendering and
  duplicate-fact/provider-order behavior.
