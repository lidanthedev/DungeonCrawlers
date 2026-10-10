# Dungeon classes and abilities

Class bonuses use the player's Dungeon skill level, from 0 through 60. Each class retains its flat configured bonuses and gains another 2% per level on its primary stats. Level 20 gives +40%; level 60 gives +120%. The class percentage multiplies the stats after flat bonuses, alongside blessings and the existing general Dungeon skill multiplier.

| Class | Percentage stats | DROP ability | Effect | Cooldown |
| --- | --- | --- | --- | --- |
| Berserker | Strength | Bloodrage | +25% strength and outgoing damage, plus 1% per Dungeon level, for 10 seconds | 30 seconds |
| Mage | Intelligence | Stormcall | One lightning strike against every dungeon enemy in the current room; 10 seconds of glowing | 25 seconds |
| Tank | Defense, health | Iron Challenge | Every enemy in the current room targets the Tank; 50% less incoming damage for 10 seconds | 35 seconds |
| Archer | Crit damage | Cataclysm Arrow | Fire an explosive arrow; an 8-block spherical blast hits dungeon enemies in the impact room | 30 seconds |
| Healer | Health, outgoing healing | Lifesurge | Fully heal living party members anywhere in the same run; regenerate 8% of their maximum health each second for 10 seconds | 60 seconds |

Stormcall uses CaveCrawlers ability damage with base 100 and Intelligence scaling 3. Cataclysm Arrow uses base 200 and Strength scaling 5. Both use the native projectile damage path for damage attribution and difficulty modifiers, including while holding a bow. Blasts do not break blocks or damage party members.

The Healer percentage increases outgoing healing from these dungeon abilities and the [support items](CLASS_SUPPORT_ITEMS.md). Each heal uses its recipient's maximum health. Regeneration uses the strongest active regeneration effect; damage reduction uses the strongest active reduction effect. Repeated copies of the same support ability refresh its effect rather than multiplying it.

Class abilities require a living, online player in a running dungeon or boss fight. DROP keeps equipment in the player's inventory, including on cooldown or an unsuccessful cast. Outside a dungeon, dropping items works normally. Empty-room Stormcall and Iron Challenge casts do not spend cooldown. Cooldowns survive reconnects and switching runs until they expire, and reset when the plugin restarts. Class selection remains locked after the start door opens.

`classes.yml` schema 2 adds `stat-percent-per-level` and `healing-percent-per-level`. Boosted YAML migrates existing files while preserving configured flat bonuses. Menu lore displays the player's Dungeon level, actual class percentage, ability effect, DROP trigger, and cooldown.

Automated coverage includes level scaling, preserved configuration migrations, DROP cancellation, room and instance targeting, ghost restrictions, cooldowns, damage reduction before the lethal-damage handler, support recipients, effect expiry, and projectile cleanup. Physical DROP input and simultaneous multiplayer combat still need an in-game check.
