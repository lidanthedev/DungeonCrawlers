# Dungeon classes and abilities

Class bonuses use the player's Dungeon skill level, from 0 through 60. Each class retains its flat configured bonuses and gains another 2% per level on its primary stats. Level 20 gives +40%; level 60 gives +120%. The class percentage multiplies the stats after flat bonuses, alongside blessings and the existing general Dungeon skill multiplier.

| Class | Percentage stats | DROP ability | Effect | Cooldown |
| --- | --- | --- | --- | --- |
| Berserker | Strength | Bloodrage | +25% strength and outgoing damage, plus 1% per Dungeon level, for 10 seconds | 30 seconds |
| Mage | Intelligence | Stormcall | One lightning strike against every dungeon enemy in the current room; 10 seconds of glowing | 25 seconds |
| Tank | Defense, health | Iron Challenge | Every room enemy targets the Tank; 50% to 60% less incoming damage for 10 seconds, based on Dungeon level | 35 seconds |
| Archer | Crit damage | Cataclysm Arrow | Fire an explosive arrow; an 8-block spherical blast hits dungeon enemies in the impact room | 30 seconds |
| Healer | Health, outgoing healing | Lifesurge | Fully heal living party members in the same run; regenerate 2% to 5% maximum health per second for 10 seconds before outgoing healing bonuses | 60 seconds |

## Class passive update, 2026-11-14

All progression uses the native Dungeon skill, clamped to levels 0 through 60. Existing
configured flat bonuses and primary-stat scaling remain unchanged.

| Class | Passive at level 0 | Passive at level 60 |
| --- | --- | --- |
| Berserker | Dungeon kills heal 2% maximum health | Dungeon kills heal 5% maximum health |
| Mage | 25% faster mana regeneration | 100% faster mana regeneration |
| Tank | Redirect 80% of nearby allies' damage within 8 blocks | Same 80% redirect within 12 blocks |
| Healer | 25% faster health regeneration within 12 blocks | 100% faster health regeneration within 12 blocks |
| Archer | +15 Attack Speed while holding a bow | +45 Attack Speed while holding a bow |

Berserker kill healing triggers at most once per second, preventing a room-wide
kill from immediately refilling health. Only owned dungeon enemies count, including
boss performers. The Ringmaster's first-life transition does not award healing.

Mage and Healer bonuses supplement the native once-per-second rates of 2% maximum
mana and 1% maximum health plus 1.5 health. A Healer helps themselves and living
allies in the same run and world. Only the strongest nearby aura applies. These
bonuses stop outside RUNNING/BOSS, on death, in spectator mode and after departure.

Archer's bonus adds Attack Speed points, rather than multiplying a base stat that
can be zero. The native total cap remains 100. CaveCrawlers shortbows already use
that stat for their shot cooldown; this does not alter vanilla bow draw time.

Tank's 80% redirect and 50%-maximum-health per-hit cap do not scale with level.
The nearest eligible Tank receives a single transfer after the ally's mitigation;
the ally keeps 20% of that hit. Transfers use native damage events without applying
defense a second time, and honor the Tank's active reduction, absorption and hit
cap. Tanks do not redirect to other Tanks. Invulnerable, creative, spectator,
offline, foreign-run and ghost Tanks cannot protect allies. Ordinary hit-immunity
ticks cannot swallow redirected damage. If another listener cancels the transfer,
the ally retains the original damage; a Tank becoming a dungeon ghost still
absorbs its share. The cap prevents a one-shot from full health, but several hits
or a hit while already injured can still defeat the Tank.

Stormcall and Cataclysm Arrow gain another 0.5% damage per Dungeon level, reaching
30% at level 60, in addition to their existing intelligence/strength scaling.
Iron Challenge improves from 50% to 60% reduction over the same levels. Lifesurge
still fully heals the party, but its ten-second regeneration is now 2% to 5% per
second before the configured outgoing healing bonus. Its 60-second cooldown is
unchanged. This replaces the former flat 8% regeneration so the new passive and
existing Healer scaling do not make recovery excessive. Bloodrage keeps its
existing 25% plus 1% per level strength/damage burst and 30-second cooldown.

The class menu shows each current passive, radius, limit and level-adjusted active
effect. No new configuration fields or migration are required.

Stormcall uses CaveCrawlers ability damage with base 4,000 and Intelligence scaling 10. Cataclysm Arrow uses base 4,000 and Strength scaling 10. Both use the native projectile damage path for damage attribution and difficulty modifiers, including while holding a bow. Blasts do not break blocks or damage party members.

The Healer percentage increases outgoing healing from these dungeon abilities and the [support items](CLASS_SUPPORT_ITEMS.md). Each heal uses its recipient's maximum health. Regeneration uses the strongest active regeneration effect; damage reduction uses the strongest active reduction effect. Repeated copies of the same support ability refresh its effect rather than multiplying it.

Class abilities require a living, online player in a running dungeon or boss fight. DROP keeps equipment in the player's inventory, including on cooldown or an unsuccessful cast. Outside a dungeon, dropping items works normally. Empty-room Stormcall and Iron Challenge casts do not spend cooldown. Casts run through the normal CaveCrawlers item-ability flow: native per-player cooldowns, and success and cooldown messages on the action bar instead of chat. Cooldowns survive reconnects and switching runs until they expire, and reset when the plugin restarts. Class selection remains locked after the start door opens.

`classes.yml` schema 2 adds `stat-percent-per-level` and `healing-percent-per-level`. Boosted YAML migrates existing files while preserving configured flat bonuses. Menu lore displays the player's Dungeon level, actual class percentage, ability effect, DROP trigger, and cooldown.

Automated coverage includes level scaling, preserved configuration migrations, DROP cancellation, room and instance targeting, ghost restrictions, cooldowns, damage reduction before the lethal-damage handler, support recipients, effect expiry, and projectile cleanup. Physical DROP input and simultaneous multiplayer combat still need an in-game check.

Validation on 2026-10-10: Java 21 `./gradlew clean build --no-daemon` passed all 413 tests and the external-plugin shading check. The native ItemAbility regression verifies activation waits for Paper to restore the dropped weapon, and a player who leaves before that callback cannot cast. Built JAR SHA-256: `89e9a40119e9a65fb0f2146f2d0a5989303f2d9d1ed8653a1e995d9b87bd25cc`. With explicit deployment approval, uploaded this JAR to the Modern Cave Crawl development server (`fa696721`) and verified the remote checksum matches. `cc reload all` enabled DungeonCrawlers successfully with recovery reporting `startsEnabled=true`. The server migrated `classes.yml` to schema 2 and persisted all four native support item definitions. `dungeon compatibility` reported automated checks passed; its pre-existing Human Gate 0 remains blocked on separate manual checks. After `cc reload items`, `dungeon compatibility item <ID>` passed serialization for all four support items. The reload briefly triggered the Paper watchdog; commands responded normally afterward. Physical DROP and multiplayer ability effects remain unverified in game.
