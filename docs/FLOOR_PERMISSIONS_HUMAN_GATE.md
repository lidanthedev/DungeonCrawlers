# Floor start permissions — 2026-10-11

`/dungeon start <floor> [difficulty]` requires `dungeoncrawlers.use` and
`dungeoncrawlers.start.<floor-id>` on the requester. Bundled floor_1/floor_3 nodes
default to operators. The shared start method also protects difficulty-menu
callbacks and admin debug starts; party difficulty unlock rules still apply.

- Source commit: `c50720a` on `codex/class-abilities-support`.
- Java 21 `./gradlew clean build --no-daemon`: PASS, 437 tests, no failures or skips;
  external plugin shading check passed.
- Regression coverage: missing/wrong/exact floor grants, custom floor IDs,
  permission revocation after opening the difficulty menu, base-use denial,
  and bundled permission defaults.
- Uploaded successfully to development server `fa696721` (Modern Cave Crawl).
- Local and remote JAR SHA-256:
  `7925d27ed9bcee0fb6a6b4caed81da7ce275a85af977bed05b1e1a12047b1922`.
- User explicitly chose immediate reload while a dungeon was running.
  `cc reload all` was sent at 14:47:54 UTC; DungeonCrawlers enabled at 14:48:09.
  Recovery cleared the interrupted run and reported `startsEnabled=true` at 14:48:22.
- `/dungeon config validate`: PASS. `/dungeon compatibility`: automated checks
  passed at 14:48:40; existing Human Gate 0 manual checks remain blocked.
- `/dungeon instance list` reported zero instances at 14:48:42. A new run was
  admitted at 14:48:47 and generated successfully at 14:48:50.
- The broad reload logged Essentials command-tree event errors and a 15-second
  watchdog stall; it recovered and the validation commands completed normally.
- Live non-operator command/menu denial and allowed start have not been exercised.

Manual acceptance: a player with base use permission and no floor grant receives
a denial without starting generation or opening the difficulty menu. Granting
floor_1 allows its normal start path while floor_3 stays denied. Removing that
grant while the menu is open prevents a subsequent difficulty click from starting.
