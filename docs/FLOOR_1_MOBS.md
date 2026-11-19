# Floor 1 room enemies

Lagless CaveCrawl Beta (`8c4cafbc`) uses the Hypixel Floor I room roster, adapted to
its existing millions-scale health and damage. The Ringmaster encounter is unchanged.
Roster reference: https://hypixelskyblock.minecraft.wiki/w/Catacombs_Mobs

Normal enemies: Crypt Lurker, Crypt Undead, Crypt Dreadlord, Tank Zombie, Zombie
Grunt, Undead Skeleton, Scared Skeleton, Skeleton Grunt, Skeleton Soldier, Sniper,
Crypt Souleater, Lonely Spider, Cellar Spider, Prince and Crypt Guard.

Minibosses are Young, Unstable, Holy and Superior Lost Adventurers, plus Angry
Archaeologist. All five open with three arrows, firing half a second apart
once they see a target within 28 blocks. After the third shot they draw swords
and rush with four seconds of Speed II. They gain Resistance IV during their
1.5-second golden apple or potion animation, then resume sword combat.
Lost Adventurers play three bite sounds while eating.
Young gains speed above half health, Unstable has a nearby lightning pulse,
Holy regenerates, and Superior has stronger breath. Archaeologist has stronger
damage reduction and diamond armor.

As of 2026-11-20, all 20 room mobs have 25% higher base damage. Their explicit
projectile, breath and lightning damage values also increased by 25%; health is
unchanged.

These are config adaptations using vanilla entities and MythicMobs mechanics.
Crypt Undead, Prince and Crypt Guard spawn at room markers rather than exploded tombs.
Skeleton merging, Hypixel loot, skins, secrets, puzzle NPCs and The Watcher are
outside this room-enemy rollout.

## Configuration and selection

`server-config/mythicmobs/Mobs/dungeoncrawlers_floor1.yml` and the matching `Skills/`
file deploy beneath `plugins/MythicMobs/`. The floor snapshot at
`server-config/dungeoncrawlers/floors/floor_1.yml` deploys beneath
`plugins/DungeonCrawlers/floors/`. These files are operator-managed, not installed
by the plugin. Preserve subsequent server edits when deploying them again.

Combat requirements select from the entire normal or miniboss pool with the
existing named random streams, seeded by run and room. A small room can therefore
select entries beyond the beginning of its pool. Respawns retain the original
choice, and a repeated seed reproduces the choices.
This selector correction applies to every floor's configured pools.

Before replacing config, back it up and retain the newest two copies. Upload via
the configured `lagless:` rclone remote with `--inplace --contimeout 15s --timeout
30s`, then download and compare. Follow the CaveCrawlers server skill's activity
checks and countdown rules before loading MythicMobs definitions and reloading the
addon. Confirm actual enable completion and `/dungeon config validate` success.

## Live verification, 2026-11-13

- Java 21 `clean build --no-daemon` passed all 469 tests and external API shading
  verification. The new regression covers full pool reachability at single markers,
  seeded replay and unchanged types after respawn. `graphify update .` completed.
- Deployed source commit `0ec7e9f`, plugin `1.0+0ec7e9f`, to CaveCrawl Beta.
  The uploaded JAR was downloaded and byte-compared. SHA-256:
  `c96dd7996b0dd2eec01ec9e3fd4a083709f96674beb199430da89a76045a9b4c`.
- The three live config files were downloaded and byte-compared. The previous floor
  file is `plugins/DungeonCrawlers/backups/floor_1-before-hypixel-roster-20261113.yml`.
  The two newest direct floor backups remain on the server; older direct copies
  were retained locally before rotation.
- Before reload, only vanished LidanTheGamer was online, active dungeon instances
  were zero, and Kuudra reported `running=false`. Reload was immediate. MythicMobs
  completed with 228 mobs and 344 skills. The final addon reload enabled
  DungeonCrawlers, loaded three floors and 26 rooms, enabled starts with no recovery
  blockers, and completed FishingAddon's `Fishing content loaded.` callback.
- Debug Floor 1 seed `1113` generated 12 placements. Normal room activation and
  administrative progression spawned all 15 ordinary types, plus Archaeologist,
  Unstable and Holy minibosses. Young and Superior also passed direct spawn checks.
  A damaged Young probe healed from 9,000,000 to 10,800,000 health under the live
  server's healing rules. No errors named this pack or its `DCF1_` IDs.
- The initial generated floor file had a duplicate `boss` key and was rejected at
  enable. Its replacement passed strict duplicate-key parsing and live validation;
  DungeonCrawlers was re-enabled successfully before further tests.
- Test-instance cleanup completed. Final checks reported zero active instances,
  no tagged probes, valid config, starts enabled and no recovery blockers. Existing
  unrelated MythicMobs definitions still report Jasper, platypus, RRT and STATE
  warnings. The first global reload also logged an unknown persisted `RUNIC_PET`.
- Full Survival clears, every special attack, exact Hypixel AI and multiplayer
  balance remain unverified. Testing used administrative activation and clears.

## Damage and bow opener update, 2026-11-20

- Raised all 20 mob base-damage values and explicit skill damage by 25%, preserving
  health, boss configuration and the class setup. All four Lost Adventurers start
  ranged, switch once within six blocks, and restore their current weapon after
  eating. A focused Java 21 config regression passed; strict YAML parsing checked
  duplicate keys, every local skill reference and unchanged health. Graph updated.
- Downloaded live originals matched the repository. Each MythicMobs file was
  backed up beside the original as `dungeoncrawlers_floor1.yml.before-combat-20261120`,
  uploaded and downloaded again for byte comparison. No plugin JAR changed.
- LidanTheGamer and Eyalc were online; active instances were zero and Kuudra was
  not running. The 60-second update countdown completed, followed by MythicMobs
  reload: 228 mobs, 347 skills and actual reload completion. DungeonCrawlers loaded
  three floors and 26 rooms with starts enabled and no recovery blockers;
  FishingAddon completed its content callback. Live config validation passed.
- The reload logged Essentials AsyncPlayerSendCommandsEvent exceptions also seen
  during previous global reloads. MythicMobs retained unrelated Jasper, platypus,
  numeric-placeholder, MIR, RRT and STATE warnings; none named this Floor 1 pack.
- Read-only comparison against live `plugins/MythicMobs/Mobs/dungeon_mobs.yml`:
  CryptZombie, CryptSkeleton and CryptSpider each have 400,000 base damage. New
  Zombie/Skeleton Grunts have 500,000 (+25%); Cellar Spider 750,000 (+87.5%). Tank
  Zombie (375,000) and Lonely Spider (312,500) remain below the old baseline. The
  original LostAdventurer and AngryArchaeologist have 1,000,000 damage; new normal
  Adventurers and Archaeologist have 1,250,000, Superior has 1,500,000.
- This update did not run a Survival combat test: actual arrows, the weapon
  transition and resulting player damage need gameplay confirmation. Values above
  compare config base damage before defence, dungeon scaling or class effects.
