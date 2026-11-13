# Floor 1 room enemies

Lagless CaveCrawl Beta (`8c4cafbc`) uses the Hypixel Floor I room roster, adapted to
its existing millions-scale health and damage. The Ringmaster encounter is unchanged.
Roster reference: https://hypixelskyblock.minecraft.wiki/w/Catacombs_Mobs

Normal enemies: Crypt Lurker, Crypt Undead, Crypt Dreadlord, Tank Zombie, Zombie
Grunt, Undead Skeleton, Scared Skeleton, Skeleton Grunt, Skeleton Soldier, Sniper,
Crypt Souleater, Lonely Spider, Cellar Spider, Prince and Admin.

Minibosses: Young, Unstable, Holy and Superior Lost Adventurers, plus Angry
Archaeologist. Adventurers have dragon breath, ranged attacks and at most four
golden-apple heals. Young gains speed above half health, Unstable has a nearby
lightning pulse, Holy regenerates, and Superior has stronger breath. Archaeologist
has stronger damage reduction, diamond armor and limited healing potions.

These are config adaptations using vanilla entities and MythicMobs mechanics.
Crypt Undead, Prince and Admin spawn at room markers rather than exploded tombs.
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

Before replacing config, back it up and retain the newest two copies. Upload via
the configured `lagless:` rclone remote with `--inplace --contimeout 15s --timeout
30s`, then download and compare. Follow the CaveCrawlers server skill's activity
checks and countdown rules before loading MythicMobs definitions and reloading the
addon. Confirm actual enable completion and `/dungeon config validate` success.
