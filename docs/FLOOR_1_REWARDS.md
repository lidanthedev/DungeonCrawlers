# Floor 1 rewards

The Mad Ringmaster pool uses Hypixel Floor I's five chest tiers and rare boss gear as inspiration:
https://hypixelskyblock.minecraft.wiki/w/Dungeon_Reward_Chest
The contents are circus equipment and weapons. There are no enchanted books, crafting fragments,
or promised resurrection effects. Each chest rolls one item; the existing claim flow permits one
chest choice per player per run. Rare Mask, Cane and Scythe weights respond to Magic Find.

## Items and prices

Verified against Modern Cave Crawl's native `plugins/CaveCrawlers/sell.yml` and item definitions
on 2026-10-30. Evoker Boots sell for 64,000, Warden Helmet for 320,000, Wither weapons for
640,000, and Dragon Chestplate for 1,600,000 coins. These are NPC sell values, not auction estimates.

| Item | Main stats | Native sell price |
| --- | --- | ---: |
| Jester Cap | 220 Health, 180 Defense, 35 Strength, 100 Intelligence | 64,000 |
| Jester Coat | 350 Health, 260 Defense, 50 Strength, 80 Intelligence | 64,000 |
| Jester Trousers | 280 Health, 220 Defense, 40 Strength, 80 Intelligence | 64,000 |
| Acrobat Boots | 200 Health, 160 Defense, 30 Strength, 50 Intelligence, 60 Speed | 64,000 |
| Stage Cutlass | 400 Damage, 200 Strength, 20 Crit Damage | 64,000 |
| Cardthrower | 400 Damage, 220 Strength, 25 Crit Damage; native two-arrow shortbow | 160,000 |
| Ringmaster Mask | 450 Health, 300 Defense, 60 Strength, 200 Intelligence | 320,000 |
| Ringmaster's Cane | 200 Damage, 250 Intelligence, 5 Ability Damage; violet native laser, 100 mana, 1s cooldown | 320,000 |
| Encore Scythe | 600 Damage, 300 Strength, 40 Crit Damage, 20 Attack Speed | 640,000 |

The common armor is around Evoker/Warden progression. Mask trades some Warden defense and
strength for health and intelligence. Scythe is below the Wither Dagger's 700 Damage / 350 Strength;
Cardthrower stays below the Wither Bow's three arrows at 500 Damage. Dragon armor has 1,600
Health and Defense on one piece; Jasper Sword has 2,200 Damage and 1,100 Strength.

`reward-items.yml` seeds native item files and missing `/sell` entries once. Later administrator
item edits and sell prices survive addon and native item reloads. Native definitions remain on
disk when the addon is disabled, so already claimed items keep their identity.

## Chests

All probabilities below are per chest at zero Magic Find, before difficulty modifiers.
The five ordinary items split their combined probability equally.

| Chest | Base cost | Score | Ordinary gear | Cardthrower | Mask | Cane | Scythe |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Wooden | Free | 0 | 100% | 0% | 0% | 0% | 0% |
| Gold | 200,000 | 0 | 90% | 8% | 1% | 1% | 0% |
| Diamond | 320,000 | 0 | 75% | 15% | 5% | 4% | 1% |
| Emerald | 640,000 | 230 | 60% | 20% | 8% | 9% | 3% |
| Obsidian | 1,280,000 | 270 | 40% | 25% | 13% | 15% | 7% |

At zero Magic Find, the expected sell value of each paid pool is below its cost even with the
maximum difficulty discount and Runic discount together. Players can preview and buy profitable
individual rolls, which is intentional. Wooden guarantees one 64,000-coin item per completed run.
Higher Magic Find changes these odds; long-term coin output still needs real run-rate measurements.

## Migration and validation

Floor schema 5 backs up older floors before migration. Only the exact shipped wooden
UNDEAD_ESSENCE / CRYPT_FRAGMENT placeholder pool on `floor_1` is replaced automatically.
Custom pools, boss pointers, floor numbers and authored template references survive.
The live server's custom placeholder/debug pool requires an explicit backed-up replacement.

Java 21 clean build passes 466 tests, including native item registration, administrator edit
preservation, migration backups/idempotence, shipped item references, weights, score gates and
sell-value checks. Live deployment evidence follows below. Combat feel, client visuals and
long-term economic output require gameplay feedback.

## Live evidence, 2026-10-30

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
