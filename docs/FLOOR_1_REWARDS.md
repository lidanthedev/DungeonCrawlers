# Floor 1 rewards

The Mad Ringmaster pool uses Hypixel Floor I's five chest tiers and rare boss gear as inspiration:
https://hypixelskyblock.minecraft.wiki/w/Dungeon_Reward_Chest
The contents are circus equipment and weapons. There are no enchanted books, crafting fragments,
or promised resurrection effects. Each chest rolls one item; the existing claim flow permits one
chest choice per player per run. Rare Mask, Cane and Scythe weights respond to Magic Find.

## Items and prices

The combat baseline comes from the live Griffin Shop and Floor 8 shop on 2026-10-30.
Griffin T5 armor has 3,600 Health and Defense, 700 Strength, 600 Intelligence, 50 Crit Damage,
20 Crit Chance, 20 Ability Damage and 25 Magic Find per piece; boots add 200 Speed.
Reward armor keeps at least this combat baseline and increases Intelligence to 1,000 and
Ability Damage to 40 per ordinary piece for a starter mage set. The obsolete
Griffin-specific luck field is not part of the dungeon armor.

Griffin T4 costs 50m per helmet/boots/sword or 55m per chestplate/leggings, plus T3 and materials.
T5 costs another 100m or 150m, plus T4 and materials. Floor 8 swords range from Ruby's
1,000 Damage / 500 Strength to Jasper's 2,200 / 1,100. Phobos Bow has 1,000 Damage,
800 Strength, 70 Attack Speed, 20 Crit Chance and three arrows with yawDiff 3 / maxPower 5.

| Item | Main stats | Native sell price |
| --- | --- | ---: |
| Jester Cap, Coat, Trousers | T5 combat baseline, 1,000 Intelligence, 40 Ability Damage per piece | 64,000 each |
| Acrobat Boots | T5 combat baseline, 1,000 Intelligence, 40 Ability Damage, 200 Speed | 64,000 |
| Stage Cutlass | 1,700 Damage, 850 Strength, 50 Crit Damage | 64,000 |
| Cardthrower | 1,000 Damage, 800 Strength, 40 Crit Damage, 20 Crit Chance, 70 Attack Speed; three-arrow shortbow | 160,000 |
| Ringmaster Mask | 3,800 Health and Defense, 750 Strength, 1,200 Intelligence, 50 Ability Damage, 60 Crit Damage, 25 Crit Chance, 30 Magic Find | 320,000 |
| Ringmaster's Cane | 1,000 Damage, 400 Strength, 1,200 Intelligence, 40 Ability Damage; violet laser, 3,500 base damage, 1.5 scaling, 100 mana, 0.5s cooldown | 320,000 |
| Encore Scythe | 2,000 Damage, 1,000 Strength, 60 Crit Damage, 20 Attack Speed | 640,000 |

Every reward item has `description: null`. Ability descriptions are retained: Cardthrower says
"Instantly shoots 3 arrows." and the Cane says "Fire a violet spotlight at your target."
Native ability names, costs, cooldowns and stat/scaling tooltips remain.
NPC sell prices retain the server's existing salvage ladder: Evoker 64k, Warden 320k, Wither 640k.
These sell prices differ from shop acquisition prices, which determine the chest fee scale.

`reward-items.yml` seeds missing native item files and `/sell` entries once. Later administrator
edits survive addon and native item reloads. Rebalancing an already installed item requires
backing up and updating its native CaveCrawlers item file as well as the bundled defaults.
Native item identities remain stable, including already claimed items.

## Chests

Types and score gates follow Hypixel Floor I. Fees follow the user's server-economy requirement,
using Griffin T5's 100m/150m shop costs as reference. No Bedrock, 301-score, S+ or debug chest.
Existing difficulty and Runic discounts apply to these base fees.
The overview orders offers by score requirement, then price, then reward ID, so the three
zero-score chests appear Wooden, Gold, Diamond regardless of map iteration order.

All probabilities below are per chest at zero Magic Find. The five ordinary items split their
combined probability equally.

| Chest | Base cost | Score | Ordinary gear | Cardthrower | Mask | Cane | Scythe |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Wooden | Free | 0 | 100% | 0% | 0% | 0% | 0% |
| Gold | 10,000,000 | 0 | 90% | 8% | 1% | 1% | 0% |
| Diamond | 25,000,000 | 0 | 75% | 15% | 5% | 4% | 1% |
| Emerald | 50,000,000 | 230 | 60% | 20% | 8% | 9% | 3% |
| Obsidian | 100,000,000 | 270 | 40% | 25% | 13% | 15% | 7% |

Players preview the rolled item before choosing their one chest per run. Wooden guarantees
one piece of T5-based mage gear or the Cutlass per completion. Rare weights respond to Magic Find.

## Migration and validation

Floor schema 6 backs up older floors. The original shipped wooden essence/fragment placeholder
pool is replaced only on floor_1. Previous shipped chest fees 200k/320k/640k/1.28m migrate to
10m/25m/50m/100m. Custom fees, pools, bosses and template pointers survive. Already frozen
reward entitlements retain their recorded fees; new runs use the revised configuration.

Regression checks cover the armor's T5 combat and mage-stat minimums, three-arrow settings, absent item descriptions,
chest types/fees/score gates, native registration/admin edits, and migration backups/idempotence.
Actual combat, weapon firing, GUI claims and sales require a player test.

## Initial deployment evidence, 2026-10-30

- Built Java 21 artifact `build/libs/DungeonCrawlers-1.0.jar`, version `1.0+33eac92`.
  SHA-256 `7d303d7da58c6949a6aabf673166322077bacd5c06a8303285d648f43af47199`.
  Full build: 466 tests, zero failures/errors/skips, external API shading check passed.
  ConfigLoaderTest also passes alone after making its native YAML initialization independent.
- `deploy.py` confirmed upload to Modern Cave Crawl, `fa696721`.
- Backed up the live floor to `plugins/DungeonCrawlers/backups/floor_1-before-rewards-20261030.yml`.
  Replaced its wooden/gold/expensive/locked placeholder pools with the five production pools.
  Preserved floor number 2, `f1start`, `f1portal`, `ringmaster_grand_preview`, and Ringmaster encounter.
- Latest master rejects mismatched completion/reward deadlines. The server had 300s completion
  and 120s rewards. Backed up `config.yml` to `backups/config-before-rewards-20261030.yml` and
  changed only `timings.live-reward-window-seconds` to 300, matching completion and bundled defaults.
- `cc reload all` reloaded the core but left addons on old classloaders. Direct addon loading
  initially hit a CaveCrawlAddon Stats loader-constraint error. `cc reload addons` restored dependency
  order; DungeonCrawlers enabled at 23:21:50 UTC and recovery reported starts enabled at 23:21:56 UTC.
  No server restart was needed. Native legacy fishing/Griffin/gemstone stat warnings and unrelated
  addon config-version warnings remain; the new reward definitions use valid stats.
- `version DungeonCrawlers` confirmed `1.0+33eac92`; `dungeon config validate` passed at
  23:22:12 UTC. `dungeon floor info floor_1` reported five rewards and the original arena.
  Operations at 23:22:16 UTC: zero instances/reservations, recovery Idle, starts Enabled, no blockers.
- All nine `dungeon compatibility item DC_...` probes built and round-tripped native ItemStacks
  successfully, including the shortbow and Cane ability lore. Native `CaveCrawlers/sell.yml`
  contains all nine exact prices from the table above.
- No player inventory, balance, or dungeon run was used for these smoke checks. Actual GUI sale,
  reward claim, weapon firing, client appearance and mid-game combat balance remain unverified.

## Rebalance and ordering deployment, 2026-10-30

- Deployed version `1.0+c20897d`; JAR SHA-256
  `a3a7f49a35325fc2513ddf364e0f762c4bedad1efc030ee14affca3f13a10038`.
  Java 21 build passed 468 tests with zero failures/errors/skips and no external API shading.
- Updated all nine native item files after verifying their backups in
  `plugins/DungeonCrawlers/backups/rewards-rebalance-20261030/`.
  The installer intentionally preserves existing native definitions, so uploading defaults alone
  would not apply the revised stats, three-arrow settings or removed descriptions.
- Floor schema 6 migrated the original fees to Free/10m/25m/50m/100m. Verified live file gates
  0/0/0/230/270, exactly five chests and no locked/expensive debug chest. Authored room pointers survive.
- `cc reload all` followed by `cc reload addons` loaded the updated addon at 11:44:03 UTC.
  Legacy stat warnings, watchdog dumps during reload and Essentials asynchronous command-list
  exceptions appeared during plugin unloading. Reload completed without a restart; all addons
  reported ready at 11:44:10 UTC.
- Operations at 11:45:01 UTC: zero instances/reservations, recovery Idle, starts Enabled,
  no recovery blockers. Configuration validation passed at 11:45:14 UTC. Cardthrower, Jester Coat,
  Cane and Scythe passed native item serialization probes with the revised definitions.
- The menu ordering regression verifies Wooden/Gold/Diamond/Emerald/Obsidian from shuffled input,
  including locked high-score chests. No new player dungeon run or visual GUI check was performed.

## Starter mage deployment, 2026-10-30

- User requested Griffin T5 armor with additional Intelligence and Ability Damage. Verified all
  four live T5 armor files: 3,600 Health/Defense, 700 Strength, 600 Intelligence, 20 Ability Damage,
  50 Crit Damage, 20 Crit Chance and 25 Magic Find; boots have 200 Speed.
- Ordinary reward armor now has 1,000 Intelligence and 40 Ability Damage per piece on that T5
  baseline. Mask has 3,800 Health/Defense, 750 Strength, 1,200 Intelligence, 50 Ability Damage,
  60 Crit Damage, 25 Crit Chance and 30 Magic Find. Cane has 1,200 Intelligence / 40 Ability Damage.
  Four ordinary pieces total 4,000 Intelligence / 160 Ability Damage before class/other bonuses.
- Built and uploaded version `1.0+f7649e9`, SHA-256
  `977ff1ecbca711b8f03fbf7ad9f849b028fff90fad8d5f34271f3215e44305a8`.
  Java 21 build passed all 468 tests; the committed artifact passed the external API shading check.
  Six native definitions were backed up under `backups/rewards-mage-t5-20261030/` and updated.
- All six changed items passed live build/serialization probes. The newer live core already
  coordinates addon reloads; an unnecessary additional addon reload caused duplicate dynamic-stat
  registrations. A single `cc reload all` restored every addon successfully at 18:12:48 UTC.
  Do not follow the newer core's coordinated reload with another `cc reload addons`.
- Operations at 18:13:10 UTC: zero instances/reservations, starts Enabled, recovery Idle,
  no blockers. Config and representative item probes passed again after recovery.
  Chest prices, score gates and descriptions retain their prior configuration.
- No live combat or damage-output calibration was performed for these mage stats.
