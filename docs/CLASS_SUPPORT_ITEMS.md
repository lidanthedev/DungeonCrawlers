# Dungeon support items

These Epic CaveCrawlers items activate by right clicking in the main hand. Their abilities affect living party members within 16 blocks in the same active dungeon, except Mending Wand, which reaches 8 blocks. Any class can use them. Healing uses the caster's Healer level bonus when applicable. Healing percentages refer to each recipient's maximum health.

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

Mending Circle costs 100 mana. It uses CaveCrawlers' existing healing-wand approach: `StatsManager` applies a flat heal plus a percentage of the recipient's maximum health. It consumes no mana and starts no cooldown when every eligible recipient is already at full health. The other support abilities cost no mana by default.

Aegis Standard, War Standard and Wind Standard place a temporary banner model using an `ItemDisplay` at the cast location. A particle ring outlines the 16-block radius, with particles inside the area, for 10 seconds. The party receives the buff once when the standard is raised; entering the area afterward does not grant it. The display and particles end when the caster dies or leaves the run, the run ends, or the plugin shuts down. Other support items show a single area pulse when cast.

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
