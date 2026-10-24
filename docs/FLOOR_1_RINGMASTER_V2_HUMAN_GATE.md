# Grand carnival upgrade

Modern Cave Crawl, Pterodactyl `fa696721`; 2026-10-10 Asia/Jerusalem
(server console timestamps use 2026-10-09 UTC). This supersedes the v1 fight
and arena dimensions in `FLOOR_1_RINGMASTER_HUMAN_GATE.md`.

## Build and deployment

- Java 21, `./gradlew clean build --no-daemon`: 337 tests, zero failures;
  external plugin shading verification passed.
- JAR: `build/libs/DungeonCrawlers-1.0.jar`.
- SHA-256: `0b4ad7ae7ae7aa560b7461cc964d2c118f746cf0bb1245385abeac661c8b1b83`.
- `python3 deploy.py` uploaded successfully. `cc reload all` completed;
  recovery blockers zero and starts enabled. `dungeon config validate`
  reported valid at 21:18:04 UTC.
- Pterodactyl MCP wrote the repository MythicMobs configuration. `mm reload`
  completed and mob info verified 55M first-life HP, 75M encore HP and the new
  1.8M-HP `RingmasterAcrobat`. The previous config is backed up at
  `/plugins/DungeonCrawlers/backups/ringmaster-before-grand-20261010.yml`.
  Reload also reports existing errors in unrelated fishing/deepmines skills.

## Fight

The two lives now have display-entity jester models, a six-second mask-break
transformation, original note-block music, teleport cues, and seven patterns:
balloon fans, spinning cards, exploding gift boxes, card rings with a safe gap,
two-arm carousel with horses, three spotlight pulses, and six falling scythes.
The first act also summons performers and acrobats. Displays and hazards share
the encounter cleanup; each instance has a 256-display and 40-projectile cap.

The original v2 carousel turned 90 degrees over 16 seconds. The speed revision
below supersedes that timing. Spotlight has a 7.5-second evacuation warning. Finale
triggers once below 30% of the second life's health. Party HP scaling and
existing difficulty scaling each apply once.

## Arena placement

- Generated schematic: 113 × 59 × 113, with an 85-block movement floor,
  cover, tiered stands, giant suit panels and jester heads, a striped dome,
  crown spire, and suspended chandelier. Roughly seven times v1's stage area.
- Authoring world: `ringmaster_authoring`; selection `256,64,0` through
  `368,122,112`. Arrival: `312.5,66,92.5`, facing the hub.
- Schematic SHA-256:
  `87ccb612e27ef4217657ce28b01eeb6b0210adb67346f21dcbe9c8fb3fdc37a6`.
- Installed FAWE requires filename first, then format:
  `//schem load ringmaster-grand-arena.schem sponge.2`. A format-first command
  did not load this clipboard. The schematic's explicit fixed origin allows
  `//paste -a -s -o` independently of the player's movement.
- Boss marker `332,66,56` verified by vanilla block query. Arrival floor and
  two blocks of headroom verified before teleport at 21:17:23 UTC. Only the
  arrival chunk was temporarily loaded for verification and then released.
- Room capture `ringmaster_grand_preview` succeeded. Metadata bounds
  `0,0,0` through `112,58,112`, boss `76,2,56`, reward `56,2,18`, and five
  player markers x `50,53,56,59,62`, y `2`, z `94`.
- Floor 1 still references `f1boss`, boss `MadRingmaster`, encounter
  `ringmaster`. The user can save this selection with
  `/dungeon room update f1boss`.

## Live encounter evidence

Temporary floor `ringmaster_grand_smoke` (number 99), Normal, seed `10010`,
instance `a7aaf109-931d-46d5-9ce2-f9e6d2eae1e2`, generated five placements.
The two-member party selected classes and opened its preparation door; first
room activation logged at 21:19:27 UTC.

- At 21:20:15, boss status reported active encounter `ringmaster`.
- At 21:20:31, entity queries found the first form, performers, and acrobats.
- At 21:21:04, a block-display query verified the correct instance ownership
  tag. Text displays were also present. The first form and adds had cleared
  during the transition, with the run still BOSS.
- At 21:21:17, the encore was alive at 116.25M HP, verifying the two-player
  multiplier of 1.55 on its 75M base. Its wing transformations were present.
- At 21:22:06, boss status reported `Ringmaster encore defeated` and completion
  pending, with reward location `5056,66,8018`. At 21:22:24 the run was
  COMPLETED with a total duration of 2:25, including preceding dungeon rooms.
- A repeated boss kill reported no active entity. Bounded entity queries
  found no boss/adds/text displays; an explicit block-display absence query
  verified models cleared at 21:22:53. Cleanup completed at 21:22:56.
- Temporary floor and room were removed. Recoverable room backup:
  `backups/authoring/20261009-212308-257-ringmaster_grand_preview`.
- At 21:23:23 the authoring arrival floor/headroom was verified again. Lidan
  was returned at 21:23:26 and the full selection restored. Position and
  dimension queries confirmed `312.5,66,92.5` in `ringmaster_authoring`.
  Temporary chunk loading and WorldEdit's world override were removed.
  Configuration valid and active instances zero at 21:23:39–42.

## Remaining calibration

Automated tests cover attack ordering, transitions, geometry, outsider/ghost
damage filtering, and display cleanup/budget reclamation. Baseline-gear
solo/five-player balance and client visual readability require gameplay
feedback. The live queries establish both lives, actor/display creation,
completion and cleanup; they do not establish that every attack was shown
before the party killed the encore.

## Carousel movement and speed revision, 2026-10-10

- The user reported that the slow floor arms left the stationary boss open to
  excessive damage. During CAROUSEL, the boss now keeps its melee AI enabled and
  retargets the nearest living participant on every encounter tick. Other casts
  retain their existing AI behavior; carousel adds no invulnerability.
- Warning remains two seconds. Rotation is now 45 degrees per second, one full
  turn in eight seconds, versus 5.625 degrees per second previously. The attack
  ends at ten seconds and the next pattern can start at fourteen seconds. Arms
  retain their arena-center origin while the boss moves. The cue directs players
  toward the hub, where following the rotation requires less movement.
- Java 21 `./gradlew build --no-daemon` passed all 348 tests, zero failures,
  errors or skips; external API shading and `git diff --check` passed. New tests
  exercise real carousel cast/tick AI and targeting behavior, the ten-second
  duration, the next cast at fourteen seconds, and warning/rotation boundaries.
  Graphify updated to 3718 nodes and 12917 edges.
- JAR SHA-256:
  `be1406787bb598dcdec0fe70f8523e664ce7ea7f795cc9626b9563d14f86e4d0`.
  `python3 deploy.py` uploaded successfully to `fa696721`. No instances were
  active before `cc reload all`; actual DungeonCrawlers reload completed at
  00:49:10 UTC. All five addons completed their reloads. The broad reload caused
  a ten-second watchdog thread dump while Griffin loaded its content; the server
  recovered and continued responding.
- Configuration valid at 00:49:16 UTC. Operations at 00:49:17 UTC reported zero
  active instances/reservations, recovery Idle, starts Enabled and no blockers.
- Updated speed and movement are regression-tested and deployed. A live fight
  with the new timing and client visual readability remain unverified.


## Damage calibration against live Floor 1, 2026-10-10

The live `floor_1.yml` uses CryptZombie, CryptSkeleton and CryptSpider, each
configured at 400,000 base damage in `plugins/MythicMobs/Mobs/dungeon_mobs.yml`.
LostAdventurer and AngryArchaeologist are the minibosses, each at 1,000,000.
The unused CryptGuardian definition is 1,250,000. Ringmaster Act I previously
matched a normal mob, and the encore and most hazards were below a miniboss.

| Attack | Previous base damage | New base damage |
| --- | ---: | ---: |
| Act I melee | 400,000 | 2,000,000 |
| Encore melee | 900,000 | 3,000,000 |
| Balloon fan / box shrapnel | 500,000 | 1,500,000 |
| Card fan / card ring | 650,000 | 2,000,000 |
| Jack-in-the-box blast | 700,000 | 2,500,000 |
| Carousel arm | 800,000 | 2,400,000 |
| Spotlight pulse | 1,100,000 | 3,500,000 |
| Falling scythe | 1,400,000 | 4,500,000 |

These are base values before player defense and existing difficulty modifiers.
CaveCrawlers' `DamageEntityListener.onPlayerDamaged` applies defense reduction
at HIGHEST priority, after `BukkitDifficultyService.incoming` at HIGH. Hazards
still use `player.damage(amount, boss)` and keep the boss as the damage source.
No defense bypass or extra difficulty multiplier was introduced. The live
Spigot attack-damage cap is 1e12, above every new value. Mythic's Damage field
sets melee damage; Java hazard values must be changed separately. See the
[official mob configuration reference](https://wiki.mythiccraft.io/mythicmobs/Mobs/Mobs).
Warnings, attack timing, hit cooldowns, boss health, and performer damage retain
their previous values.

Validation used an isolated checkout of committed `47ef6f0` to exclude another
active chat's unfinished Foundry changes. Java 21 `clean build --no-daemon`
passed all 354 tests, zero failures/errors/skips, and external API shading checks.
The carousel adapter regression now checks zero damage during the warning,
2,400,000 damage with boss attribution on contact, and no repeat within the
900ms hit cooldown. Graphify AST updates succeeded in both checkouts.

Built and uploaded JAR SHA-256:
`f787eedcea5aa6f3ae72d33eafae7451754b2017fee81a76852e2c51329154e9`.
The live Mythic file was backed up as
`plugins/MythicMobs/Mobs/ringmaster.yml.before-damage-20261010` and only the two
boss melee fields were changed. `mm reload` finished at 01:23:15 UTC, loading
133 mobs; unrelated existing skill/targeter errors appeared, with no Ringmaster
error. Live file readback matched the intended change. Instances were zero
before `cc reload all`; actual DungeonCrawlers reload completed 01:23:27 UTC.
Configuration validated at 01:23:59 UTC. Operations at 01:24:02 UTC reported zero
instances/reservations, recovery Idle, starts Enabled, and no blockers.

No live post-defense hit measurements or solo/five-player fight were performed.
The comparison establishes the damage increase relative to live enemies; final
gear-specific difficulty remains subject to gameplay feedback.
