# Dungeon support items

These Epic CaveCrawlers items activate by right clicking in the main hand. Their abilities affect living party members within 16 blocks in the same active dungeon. Any class can use them. Healing uses the caster's Healer level bonus when applicable. Healing percentages refer to each recipient's maximum health.

| Item ID | Item | Ability | Effect | Cooldown |
| --- | --- | --- | --- | --- |
| `DC_AEGIS_STANDARD` | Aegis Standard | Rallying Aegis | Heal 10%; +35% Defense for 10 seconds | 25 seconds |
| `DC_BASTION_HORN` | Bastion Horn | Bastion's Call | Heal 5%; 25% less incoming damage for 10 seconds | 35 seconds |
| `DC_RENEWAL_STAFF` | Renewal Staff | Wellspring | Heal 30%; heal 5% each second for 10 seconds | 25 seconds |
| `DC_DAWNLIGHT_TOME` | Dawnlight Tome | Dawn's Benediction | Heal 15%; +25% Strength and Intelligence for 10 seconds | 30 seconds |

Administrators can obtain them through CaveCrawlers:

```text
/cc item give <player> DC_AEGIS_STANDARD 1
/cc item give <player> DC_BASTION_HORN 1
/cc item give <player> DC_RENEWAL_STAFF 1
/cc item give <player> DC_DAWNLIGHT_TOME 1
```

DungeonCrawlers registers the four abilities at startup and installs missing item definitions into CaveCrawlers' item files. CaveCrawlers item reloads preserve the abilities and existing inventory IDs. Existing definitions are retained, including administrator changes to item stats and appearance. The bundled `support-items.yml` records installation defaults. Ability effects and cooldowns are controlled by DungeonCrawlers.

On DungeonCrawlers shutdown, owned abilities, settings variants, listeners, and active item registrations are removed. The persisted definitions remain for the next startup.
