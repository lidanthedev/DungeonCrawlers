# Dungeon support items

These Epic CaveCrawlers items activate by right clicking in the main hand. Their abilities affect living players within 16 blocks, except Mending Wand, which reaches 8 blocks. In a running dungeon, recipients must belong to the same run. Outside dungeons, they affect nearby players in the same world without requiring party membership. Spectators and dungeon ghosts cannot cast or receive support effects. Any class can use them. Dungeon healing uses the caster's Healer level bonus when applicable. Healing percentages refer to each recipient's maximum health.

| Item ID | Item | Ability | Effect | Cooldown |
| --- | --- | --- | --- | --- |
| `DC_AEGIS_STANDARD` | Aegis Standard | Rallying Aegis | Heal 10%; +35% Defense for 10 seconds | 25 seconds |
| `DC_BASTION_HORN` | Bastion Horn | Bastion's Call | Heal 5%; 25% less incoming damage for 10 seconds | 35 seconds |
| `DC_RENEWAL_STAFF` | Renewal Staff | Wellspring | Heal 30%; heal 5% each second for 10 seconds | 25 seconds |
| `DC_DAWNLIGHT_TOME` | Dawnlight Tome | Dawn's Benediction | Heal 15%; +25% Strength and Intelligence for 10 seconds | 30 seconds |
| `DC_WAR_STANDARD` | War Standard | Warcry | +35% Strength, +25% Crit Damage and +15% outgoing damage for 10 seconds | 30 seconds |
| `DC_WIND_STANDARD` | Wind Standard | Tailwind | +30% Speed and +25 Attack Speed for 10 seconds | 25 seconds |
| `DC_VITALITY_TOTEM` | Vitality Totem | Verdant Pact | Heal 15%; +25% Health for 10 seconds | 35 seconds |
| `DC_FOCUS_ORB` | Focus Orb | Arcane Concord | +40% Intelligence and +500 Ability Damage for 10 seconds | 30 seconds |
| `DC_MENDING_WAND` | Mending Wand | Mending Circle | Heal 100 health plus 10% of max health within 8 blocks | 8 seconds |

Mending Circle costs 100 mana. It uses CaveCrawlers' existing healing-wand approach: `ZOMBIE_SWORD_HEAL`/`InstantHealAbility` uses `StatsManager` to apply a flat heal plus a percentage of the recipient's maximum health. It consumes no mana and starts no cooldown when every eligible recipient is already at full health. The other support abilities cost no mana by default. Tailwind preserves the native Speed and Attack Speed caps. Attack Speed and Ability Damage boosts add native stat points, so they work from a zero baseline.

Aegis Standard, War Standard and Wind Standard raise upright banner scenes built from `BlockDisplay` entities: a planted pedestal, pole, crossbar, colored cloth, trim and emblems on both sides. The cloth moves gently while the pole stays vertical. Particle rings outline the 16-block area for 10 seconds. Nearby recipients receive the buff once when the standard is raised; entering afterward does not grant it. The scene ends on expiry, caster death or logout, world or dungeon context change, run completion or plugin shutdown. Other support items show a single area pulse when cast.

All healing, regeneration, stat bonuses and damage bonuses/reduction are half strength outside dungeons. Duration, range, mana cost and cooldown stay the same. For example, overworld Warcry grants +17.5% Strength, +12.5% Crit Damage and +7.5% damage; Mending Circle heals 50 health plus 5% maximum health. Temporary buffs do not carry across worlds or between overworld and dungeon contexts.

Vitality Totem uses a Beacon item, so it cannot activate vanilla Totem of Undying resurrection. Startup migrates the old persisted item material while preserving admin stats and appearance metadata. Existing inventory copies are converted, and an exact-ID resurrection guard covers old copies retrieved from storage. Ordinary vanilla totems are unaffected.

Administrators can obtain them through CaveCrawlers:

```text
/cc item give <player> DC_AEGIS_STANDARD 1
/cc item give <player> DC_BASTION_HORN 1
/cc item give <player> DC_RENEWAL_STAFF 1
/cc item give <player> DC_DAWNLIGHT_TOME 1
/cc item give <player> DC_WAR_STANDARD 1
/cc item give <player> DC_WIND_STANDARD 1
/cc item give <player> DC_VITALITY_TOTEM 1
/cc item give <player> DC_FOCUS_ORB 1
/cc item give <player> DC_MENDING_WAND 1
```

DungeonCrawlers registers the nine abilities at startup and installs missing item definitions into CaveCrawlers' item files. CaveCrawlers item reloads preserve the abilities and existing inventory IDs. Existing definitions are retained, including administrator changes to item stats and appearance. The bundled `support-items.yml` records installation defaults. Ability effects are controlled by DungeonCrawlers; cooldowns, mana, and action-bar feedback use the normal CaveCrawlers item-ability flow.

On DungeonCrawlers shutdown, owned abilities, settings variants, listeners, and active item registrations are removed. The persisted definitions remain for the next startup.

Validation on 2026-10-10: Java 21 `./gradlew clean build --no-daemon` passed all 423 tests and the external-plugin shading check. The final `./gradlew build --no-daemon` also passed. Coverage includes upright display geometry at different player yaw/pitch angles, partial-spawn cleanup, replacement and expiry; overworld half-strength effects and nearby-player eligibility; context changes; wand flat/percentage healing and no-waste mana/cooldown behavior; native stat caps; and legacy totem conversion and resurrection prevention.

Deployed to Modern Cave Crawl (`fa696721`) with matching local/remote JAR SHA-256 `4cc4ff7836cebb9e4ec4eeff2eab304ffb5c8e3145135d775b647e922ef56b50`. There were no active instances before reload. At 15:31 UTC, `cc reload all` enabled DungeonCrawlers with `startsEnabled=true`. All nine items passed `dungeon compatibility item <ID>` after `cc reload items`, and the persisted Vitality definition was verified as `minecraft:beacon`. Compatibility automated checks passed; its separate Human Gate 0 remains blocked on existing manual checks. Client banner appearance, particle visibility, multiplayer right-click effects and live fatal-damage prevention remain unchecked.
