# Dungeon difficulties, Dungeon skill, and Runic pet

Status: requirements agreed; implementation has not started.
Date: 2026-10-02.

## Scope and source of truth

Add ten selectable difficulties, per-floor difficulty unlocks, a Dungeon skill using CaveCrawlers progression, Runic enemies and bosses, Runic Fragment drops, and a Runic pet integrated with CaveCrawlAddon.

The user's written decisions override the reference screenshots. In particular:

- Replace the passive-regeneration restriction with no ordinary revival from Hellish upward. Healing and passive regeneration remain available.
- Remove bonus materials, bonus essence, and Singularity Core entirely from this feature.
- Experience bonuses apply to Dungeon skill XP, not vanilla XP or Combat skill XP.
- Luck bonuses multiply MAGIC_FIND inside the dungeon.
- Replace spellcaster enemies with Runic variants of existing dungeon enemies.
- Create the Runic pet and its item. Do not implement crafting, fragment exchanges, or a shop. The user will add the pet to a shop.
- Prefer Pterodactyl edits for live item and skill definitions. Gameplay rules belong in DungeonCrawlers code and versioned dungeon configuration.

This file is a local planning artifact. Do not commit it, credentials, build output, or unrelated existing work.

## Difficulty values

Percentages below are increases or reductions relative to the existing floor's baseline. Existing floor mob definitions remain the baseline.

| Difficulty | Enemy health | Duplicate-class damage reduction | Extra incoming enemy damage | Extra incoming enemy damage while alone | Dungeon XP multiplier | MAGIC_FIND multiplier | Ordinary revival |
|---|---:|---:|---:|---:|---:|---:|---|
| Normal | 1.0x | 0% | 0% | 0% | 1.00x | 1.00x | Yes |
| Hard | 1.0x | 5% | 0% | 0% | 1.25x | 1.05x | Yes |
| Insane | 1.5x | 10% | 0% | 0% | 1.50x | 1.10x | Yes |
| Extreme | 1.5x | 15% | 0% | 0% | 1.75x | 1.15x | Yes |
| Demonic | 2.0x | 20% | 0% | 0% | 2.00x | 1.20x | Yes |
| Hellish | 2.5x | 25% | 20% | 25% | 2.50x | 1.25x | No |
| Death | 2.5x | 30% | 50% | 50% | 3.00x | 1.30x | No |
| Void | 3.0x | 40% | 80% | 100% | 3.50x | 1.35x | No |
| Hardcore | 4.0x | 50% | 140% | 150% | 4.00x | 1.40x | No |
| Impossible | 5.0x | 60% | 200% | 200% | 5.00x | 1.50x | No |

Enemy baseline damage is unchanged. The incoming-damage factors above provide the difficulty's damage increase. Apply each factor once.

### Duplicate classes

- A class is duplicated when at least two remaining run participants selected it.
- Apply the listed reduction once to each player using that duplicated class, regardless of how many players share it.
- Affect all damage that player deals to dungeon enemies, including melee, projectiles, and abilities.
- Other classes receive no reduction.
- Implementation default: ghosts and offline participants still count toward class duplication while registered; leaving/removal stops counting them. Being dead must not become a way to remove a class penalty.

### Nearby enemies and isolation

Both mechanics begin at Hellish.

- A normal enemy or miniboss takes 20% less player damage while another living, non-boss dungeon enemy from the same instance is within 8 blocks.
- This reduction does not stack. Bosses neither receive it nor provide it to nearby enemies.
- A player is alone when no other alive, online participant from the same instance is within 12 blocks.
- Ghosts, offline players, removed participants, and players in other instances do not count as nearby allies.
- Solo runs receive the full isolation penalty.
- Use three-dimensional distance. No line-of-sight condition is required.
- Incoming difficulty and isolation factors multiply. Hellish while alone is 1.20 x 1.25 = 1.50x baseline enemy damage; Impossible while alone is 3 x 3 = 9x.
- Apply incoming penalties only to dungeon enemy attacks, including attributable enemy projectiles and abilities. Falls, lava, environmental hazards, and player attacks do not receive them.

## Selection and per-floor unlocks

- Normal starts unlocked for every player on each floor they can otherwise access.
- Successfully clearing a difficulty unlocks the next difficulty on that same floor.
- Clearing Floor I does not unlock difficulties on Floor II.
- Every participating party member must have the selected difficulty unlocked. Checking only the leader is insufficient.
- The leader selects difficulty before generation/admission. A solo player acts as leader.
- Freeze the chosen difficulty and its configuration in the run context; it cannot change mid-run.
- On success, unlock the next tier for all remaining registered participants, including ghosts and offline players. Removed participants receive no completion unlock.
- Failed runs never unlock the next tier. Completing Impossible has no further unlock.
- Persist progress by player UUID and stable floor ID. Repeated completion callbacks must be harmless, and progress must not regress.

Provide both command and GUI access:

- `/dungeon start <floor> <difficulty>` starts an explicitly selected tier.
- `/dungeon start <floor>` opens difficulty selection instead of silently starting a run.
- The menu shows all ten tiers, exact effects, locked states, and the preceding clear required.
- Recheck membership, leader status, floor access, and every member's unlock when the start action executes. A stale menu must not bypass admission.
- Include difficulty in run information, final results, and relevant placeholders.
- Reuse the existing menu library and MiniMessageUtils for messages, names, lore, and titles.

## Dungeon skill and XP

### Existing progression

Create live CaveCrawlers skill ID `dungeon`, display name `Dungeon`, maximum level 60. Use its existing skill XP curve, persistence, GUI, level-up processing, and XP gain event.

The inspected live `skill-need-xp` list has 60 entries. It starts at 50 XP and ends at 1,000,000,000 XP for the last level. Do not introduce a separate dungeon XP curve or XP database. Higher-floor reward amounts can be tuned independently without changing this shared curve.

Use no automatic kill objectives or permanent stat rewards for this skill. DungeonCrawlers awards run-result XP and applies the conditional stat bonus.

### Award calculation

Default base completion XP = `100 * floor.number^2`.
Default failure XP = `base completion XP * 0.10`.
Final award = `base outcome XP * selected difficulty XP multiplier`.

Store explicit per-floor XP settings with these defaults, so higher floors can be tuned beyond the initial formula. Preserve fractional XP supported by the existing skill system.

| Floor | Normal completion | Normal failure | Impossible completion | Impossible failure |
|---|---:|---:|---:|---:|
| I | 100 | 10 | 500 | 50 |
| II | 400 | 40 | 2,000 | 200 |
| III | 900 | 90 | 4,500 | 450 |

- Combat must actually start before any run XP is earned. Generation, preparation, and a cancelled/unopened start door grant zero XP.
- Award XP once when a participant's outcome is finalized, not per kill, room, secret, or tick.
- Successful completion awards full completion XP to every remaining participant, alive, ghost, online, or offline.
- Failure awards failure XP. Already-earned XP and fragments are retained.
- Implementation default: voluntary escape/removal after combat starts finalizes that player's failure award. It excludes them from later completion XP and unlocks. Preparation-only departures earn nothing.
- Treat a started run terminated by timeout, wipe, or administrative cancellation as a failure; cleanup must not silently discard its award.
- Offline XP is persisted as pending and delivered when the player's CaveCrawlers persistence session is ready after reconnect. Do not mutate an unloaded offline profile.
- Freeze the outcome, amount, floor, and difficulty at finalization; reconnecting or reloading must not change the award.
- Use the native skill grant path, including SkillXpGainEvent and ordinary level-up behavior, so the active pet can gain XP through its existing integration.

### Dungeon-only combat bonus

For Dungeon level L, multiply these seven combat stats by `1 + L / 100` inside an active dungeon:

`HEALTH`, `DEFENSE`, `STRENGTH`, `CRIT_DAMAGE`, `CRIT_CHANCE`, `INTELLIGENCE`, `ABILITY_DAMAGE`.

Level 0 gives no increase. Level 60 gives +60%. Exclude `DAMAGE`, `ATTACK_SPEED`, `SPEED`, `MAGIC_FIND`, mining stats, and the current `MANA` resource. Do not add artificial caps or permanently modify equipment/skill stats.

Extend the current stat calculation path. Recalculate from original incoming stats each time; never multiply an already-modified result repeatedly. Remove conditional bonuses when the player leaves or cleanup runs.

## MAGIC_FIND and completion chests

- Difficulty multiplies the player's dungeon MAGIC_FIND rather than adding points. For example, 100 MAGIC_FIND becomes 150 on Impossible; zero stays zero.
- Apply this after the dungeon's normal class/blessing and Runic pet stat contributions. The Dungeon skill does not itself boost MAGIC_FIND.
- Native CaveCrawlers drops already support a chance modifier based on MAGIC_FIND. Reuse that behavior; do not apply the difficulty multiplier twice.
- Ordinary Runic fragment chance follows `baseChance * (1 + effective MAGIC_FIND / 100)`, bounded to a valid probability.
- Boss fragment quantity remains exactly four.
- Add a per-reward-entry `magic-find-sensitive` flag, default false. Mark intended rare completion rewards explicitly; do not infer rarity from names or multiply every chest weight.
- For a flagged entry, effective weight = `baseWeight * (1 + effective MAGIC_FIND / 100)`; other entries keep their base weight.
- Preserve existing roll count, score locks, price, unique-roll behavior, and amount ranges. A weight change changes relative probability, not item quantity.
- Snapshot effective MAGIC_FIND per participant before completion cleanup and freeze the rolled offers. Reopening previews, reconnecting, or changing gear after completion never rerolls rewards.
- Offline participants use their latest captured in-run MAGIC_FIND, not a recomputed value from a missing online player.

## Runic enemies and bosses

| Difficulty | Runic normal/miniboss spawn chance | Ordinary Runic fragment chance before MAGIC_FIND | Runic boss chance |
|---|---:|---:|---:|
| Normal through Extreme | 0% | Not applicable | 0% |
| Demonic | 0.1% | 10% | 0% |
| Hellish | 0.2% | 20% | 0% |
| Death | 0.3% | 30% | 0% |
| Void | 0.4% | 40% | 1% |
| Hardcore | 0.5% | 50% | 2% |
| Impossible | 0.6% | 60% | 3% |

- Normal enemies and minibosses are eligible. Bosses use the separate boss chance.
- Implementation default: roll the final boss's Runic status once and upgrade the existing boss. Do not add a second boss or change the encounter's required kill count.
- A Runic variant retains the original enemy's abilities and gets an additional 10x health and 5x attack damage, stacked over difficulty.
- Example: an Impossible Runic enemy has 50x baseline health. Its attacks deal 15x baseline damage to a grouped player, or 45x to an isolated player, before normal player defenses.
- Runic enemies have a visible purple glow, a clear Runic name, and purple emitted particles. These apply to Runic bosses too.
- Assign all status and modifiers to the exact dungeon instance/entity. Other MythicMobs using the same ID outside dungeons are unaffected.
- Reuse NamedRandomFactory with separate streams for Runic status and loot. A spawn retry for the same logical slot must retain its Runic status instead of rerolling.
- Scale health before combat, updating both maximum and current health through compatible MythicMobs/entity APIs. Do not edit a shared mob definition at runtime.
- Choose one damage application path for the Runic factor and verify melee, projectiles, and Mythic skills. Avoid applying it in both base attributes and a listener.
- Emit particles through the existing central update path at a bounded cadence. Clean up entity glow/team membership and callbacks with instance cleanup.

### Fragment delivery

Create CaveCrawlers item ID `RUNIC_FRAGMENT` through the live item configuration.

- A normal Runic enemy or miniboss rolls its tier-specific chance once on a legitimate kill. Success awards one fragment to the killer.
- A Runic boss guarantees four fragments to the killer. MAGIC_FIND affects neither that guarantee nor the quantity.
- Only an eligible player kill from the same run qualifies. Implementation default: deaths with no valid participating player killer award no fragments.
- Deliver directly to the killer; use the existing safe inventory/overflow delivery path where it fits.
- Award and preserve fragments even if the run later fails. They are not material/essence difficulty bonuses.
- Administrative removal, despawn reconciliation, cleanup, and duplicate death callbacks must not award fragments.
- Handle the boss drop before completion tears down its identity, participant context, or MAGIC_FIND data.
- Use a durable delivery record where delayed/overflow delivery requires recovery. Do not silently drop or duplicate rewards on retries.

## Runic pet

Create CaveCrawlers item ID `RUNIC_PET`, item type PET, rarity LEGENDARY. Register its behavior through CaveCrawlAddon's existing pet system. Use its normal claiming, collection, active-pet selection, visuals, persistence, and leveling.

- Maximum pet level: 100.
- Preferred skill: `dungeon`.
- Use existing pet XP costs and existing behavior for XP from other skills.
- While active anywhere, each pet level grants +1 HEALTH, +0.5 DEFENSE, and +0.1 MAGIC_FIND through native BasePet stats.
- At level 100 this is +100 HEALTH, +50 DEFENSE, and +10 MAGIC_FIND before dungeon multipliers.
- Runic Power grants an additional 0.5% HEALTH per pet level while inside a dungeon, reaching +50% at level 100. Multiply after native global stat additions; stack multiplicatively with the Dungeon skill.
- The item/lore describes its buffs, five-second revive, and once-per-player-per-run limit.
- Do not implement crafting or a shop.

### Revival contract

1. On a real lethal hit/death, check whether the Runic pet is active and this player still has the run's charge.
2. Enter the existing movable ghost state and latch a Runic revival for five seconds later. Switching pets afterward does not cancel the latched ability. Equipping Runic only after death does not retroactively trigger it.
3. Ghost restrictions remain in force: no attacks, damage taken, enemy targeting, block interaction, or access reserved for alive players. Movement within the active dungeon remains possible.
4. A latched, online Runic revival keeps the run open if the player is the last living participant or is solo. Ordinary 60-second ghosts do not acquire this protection.
5. After five seconds, revive at the ghost's current location inside its active instance, restore full health, and clear ghost effects. Do not teleport to a teammate or the original death location.
6. Consume the player's charge only when revival succeeds. All deaths remain counted for scoring.
7. Further deaths use the tier's ordinary rule: existing 60-second revival on Normal through Demonic, no ordinary revival on Hellish through Impossible.

The charge is per player and run, not per pet item. Pet switching, additional copies, withdrawal/reclaim, or reconnecting must not reset it. Reserve the pending charge so repeated lethal callbacks cannot schedule multiple revives.

Implementation defaults for edge cases:

- Disconnect-to-ghost is not a lethal death and does not trigger or consume the pet ability.
- If disconnected during the countdown, retain the pending state while the run remains valid; do not revive an offline player or keep an empty/offline party alive indefinitely. An elapsed pending revive can execute on reconnect only if that run is still active.
- Leaving, timeout, or run termination cancels pending revival. Never revive into a cleaned world or another instance.
- If the current location is outside valid instance bounds or unsafe, use the existing in-instance recovery/safe-location path. The normal valid-location case must preserve the exact current ghost location.
- Hellish+ ghosts with no pending pet revive have no gameplay revival timer. They can remain ghosts while teammates finish; ghost invisibility/protection and UI must work without `reviveAt`.
- Administrative debug revival remains a permission-protected maintenance action. It does not consume a pet charge or erase a death.
- No new post-revive invulnerability perk is part of this scope.

Final score counts every death. An active Runic pet on a remaining participant gives the run +2 points in the Bonus category, once per run, whether or not it revived anyone. This bonus is evaluated when the final score is calculated, then frozen for messages, chest locks, persistence and placeholders. Completion revives everyone immediately, including pending Runic ghosts, without changing death counts.

## Existing implementation boundaries

The repository already supplies the main mechanisms; extend them instead of building parallel systems.

| Responsibility | Existing boundary | Planned change |
|---|---|---|
| Admission and preparation | `commands/DungeonPhaseFiveCommand`, `core/run/RunPreparationService` | Select/validate tier; freeze difficulty and combat-start eligibility |
| Configuration | `config/registry/ConfigModels`, `ConfigLoader`, `ConfigRegistryService` | Difficulty definitions, floor XP, sensitive reward entries, validated snapshots |
| Mob and boss spawning | `integration/BukkitCombatMobGateway`, `BukkitBossGateway`, `integration/mythic/MythicMobsAdapter` | Apply instance-specific health/Runic status once |
| Enemy identity and death | `BukkitEntityIdentity`, `BukkitBossIdentity`, `BukkitCombatListener` | Identify modifiers, safe one-shot drops, and cleanup |
| Player lifecycle | `core/lifecycle/PlayerLifecycleService`, `BukkitDungeonLifecycleListener` | Revival policy, latched pet timer, pending-revive wipe handling |
| Ghost presentation and revival | `BukkitGhostState`, lifecycle notices in `DungeonCrawlers` | Indefinite ghosts and current-location Runic revival |
| Stats | `core/stats/StatAggregationService`, `BukkitDungeonRunListener` | Conditional Dungeon level, pet buffs, and MAGIC_FIND |
| Damage | CaveCrawlers damage events and Bukkit/Mythic attribution | Shared scoped multipliers for all supported attack paths |
| Final results | `core/score/ScoreService`, completion/failure callbacks in `DungeonCrawlers` | Forgiven deaths, frozen XP/unlock outcomes, completion MAGIC_FIND |
| Completion reward rolls | `core/reward/RewardRoller`, `RewardEntitlementService` | Sensitive weights and frozen participant MAGIC_FIND |
| Persistence | `DurableRepository`, completed-run codecs and existing claim delivery | Per-floor progress and recoverable run/player XP/delivery receipts |
| Pets | CaveCrawlAddon `BasePet`, `PetsManager`, `ActivePet` | Register a Runic definition; inspect active pet at lethal transition |

Keep platform-neutral rules in core and Bukkit/CaveCrawlers/CaveCrawlAddon adapters in integration. Add only narrow helpers needed to share these rules.

### Dependency and persistence checks

- The inspected sibling CaveCrawlAddon checkout uses group `me.lidan`, project name `CaveCrawlAddon`, version `1.0`; its `PetsManager.registerPet` and `getActivePet` are public.
- DungeonCrawlers already has `mavenLocal()`. Resolve the actual local published artifact and add CaveCrawlAddon as compileOnly. No local CaveCrawlAddon Maven artifact was found during planning; its build applies maven-publish but currently defines no publication. Preparing an actual artifact/publication is an implementation prerequisite, not a completed step.
- Add an enabled-plugin check and appropriate soft dependency before loading addon-linked classes. Dungeon gameplay must still load safely when CaveCrawlAddon is absent; report unavailable pet support clearly.
- Extend the no-external-shading check to include `me/lidan/caveCrawlAddon/`.
- Confirm the APIs against the resolved build artifacts and live server versions. The sibling source's `DamageCalculationEvent` and `SkillsManager.giveXp` may differ from DungeonCrawlers' currently pinned CaveCrawlers v2.0.0 artifact.
- Normal online skill grants can use the native manager and XP event. Offline delivery must wait for a healthy, current persistence session.
- A DungeonCrawlers pending record alone cannot guarantee exactly-once XP across a crash between native XP mutation and delivery acknowledgment. Before shipping recoverable XP, verify an existing receipted native grant; if absent, add the smallest CaveCrawlers transaction/API that persists a run/player award ID with the skill XP update. Preserve native level-up/XP-event semantics. Do not bypass skill persistence or claim crash safety from a queue alone.
- Reuse versioned durable writes and stable keys such as instance/player/outcome. Add backward-compatible codecs/defaults for old completed-run records; old records must not retroactively earn new XP or unlock tiers.

## Config and live-content changes

Proposed DungeonCrawlers changes:

- New versioned `difficulties.yml` containing the ten definitions and Runic rates above.
- Per-floor completion XP and failure factor, with defaults computed from the floor number when migrating existing floors.
- Per-reward-entry MAGIC_FIND sensitivity, default false for existing entries.
- Include new configuration in validation, configuration hashing, backup capture, and immutable run snapshots.
- Bump schema versions for changed Boosted YAML files and add migration coverage. Preserve existing floor IDs, rewards, custom values, and backup retention behavior.
- Version any expanded durable record independently from YAML.

Proposed live content through Pterodactyl on `fa696721`, Modern Cave Crawl:

- `/plugins/CaveCrawlers/skills/dungeon.yml`: normal CaveCrawlers SkillInfo, maxLevel 60, existing curve, no kill objectives/permanent stat rewards.
- `/plugins/CaveCrawlers/items/RUNIC_FRAGMENT.yml`: fragment ItemInfo.
- `/plugins/CaveCrawlers/items/RUNIC_PET.yml`: Legendary PET ItemInfo with appropriate appearance and lore.
- Modify MythicMobs live content only if the verified APIs require a configuration hook for scoped Runic effects/attack attribution. Avoid cloning every mob for every difficulty.
- Capture targeted config backups, validate the existing live serialization format, and read back changed files. Avoid overwriting unrelated definitions.

Live configuration changes and deployment happen during implementation, not as part of writing this plan.

## Implementation order

1. **Difficulty admission and configuration.** Add the ten-tier model/config, immutable selected tier, per-floor unlock persistence, command argument, and GUI. Reject locked members before reserving/generating a run.
2. **Combat and conditional stats.** Apply health, duplicate-class damage, isolation, nearby-enemy protection, MAGIC_FIND, and Dungeon-level bonuses through shared scoped paths. Verify all damage sources and cross-instance isolation.
3. **Dungeon progression and completion rolls.** Create the live Dungeon skill, freeze XP/unlock outcomes, implement native receipted XP delivery including offline reconnect, and add sensitive chest weights without changing deterministic offers.
4. **Runic enemies and fragments.** Add stable Runic spawn rolls, multipliers, purple presentation, ordinary drop rolls, and Void+ boss upgrades/four-fragment delivery.
5. **Runic pet and lifecycle.** Resolve the compile-only addon integration, register the pet/item, add global pet stats and dungeon-only percentage health, latch five-second revival, revise wipe/ghost UI behavior, and add the unconditional active-pet +2 Bonus score.
6. **Migrations, server validation, and release evidence.** Run automated checks, build, deploy, exercise the human gate, and record the actual evidence. Keep coherent implementation phases on separate branches/PRs from updated master as repository guidelines require.

## Verification and acceptance

Extend existing JUnit/Mockito tests where behavior already lives. Add small focused suites only for genuinely new rules.

- Every tier's numeric values, inclusive chance boundaries, valid finite config values, and invalid config rejection.
- Per-floor unlock isolation, all-member admission, stale GUI checks, leader authorization, repeated success, and offline/ghost unlock persistence.
- HP scaling happens once, including spawn retries and bosses. Unrelated enemies/instances are unaffected.
- Duplicate classes affect all supported player damage paths once. Unique classes and removed participants behave correctly.
- Nearby-enemy protection begins at Hellish, does not stack, excludes bosses, and respects 8-block/instance boundaries.
- Isolation uses 12 blocks, excludes ghosts/offline players, applies to solo, multiplies correctly, and never boosts environmental damage.
- Dungeon skill uses the native curve/event/persistence. Its seven stats receive exactly +L%, while excluded stats and players outside dungeons do not.
- Combat never started means zero XP. Completion/failure amounts scale by floor/tier; online ghosts and offline participants get full completion XP on success.
- Repeated terminal callbacks/reconnects cannot duplicate XP or unlocks. Exercise grant/ack crash recovery through the verified native receipt mechanism.
- Sensitive chest entries change relative weights; unflagged weights remain unchanged. Same frozen input/seed yields the same offer; reopening/changing gear never rerolls it.
- Runic normal/miniboss and boss rates use separate stable streams. Native mob abilities remain; purple glow/particles are visible and cleaned up.
- Ordinary fragments use killer MAGIC_FIND and configured chance; bosses give exactly four. Despawn/admin cleanup gives none; inventory overflow/retries are safe.
- Pet active at death schedules one five-second timer. Switching during ghost preserves it; equipping after death does not create it.
- Solo/last-player death remains active during the pending pet timer. Successful revival uses the ghost's moved location, full health, and retains the death penalty.
- A second death cannot reuse the charge, including after pet swaps, additional pet copies, or reconnect. No revival erases a death.
- Hellish+ ghosts without a timer remain protected and visible to the correct lifecycle/UI logic. Entire-party death with no viable pending pet revival still wipes.
- Disconnect, escape, timeout, addon absence, reload, and plugin disable leave no pending revival callbacks, stat bonuses, glow entries, or stale entities.
- Old configs/results migrate without lost custom data, fabricated progression, or duplicate rewards.

Run Java 21 `./gradlew test`, then `./gradlew clean build`; verify CaveCrawlers and CaveCrawlAddon APIs are not shaded. After code changes, run `graphify update .`.

For live validation, use the repository server and player-testing skills. Build first, record JAR SHA-256, deploy with `python deploy.py`, reload through Pterodactyl with `cc reload all`, and confirm addon reload success before testing. Use the permitted player sender for GUI/commands, and at least two players for class duplication, isolation, unlock admission, and ghost/offline completion. Use a controlled debug path to force rare Runic cases instead of waiting for natural 0.1% spawns. Debug-forced encounters must not create ordinary player progression or farmable rewards.

Record actual checks in a new human-gate document. Acceptance requires successful automated checks and live evidence for both solo and party pet revival, higher-tier no-revive behavior, offline XP/unlocks, Runic drops, and stable completion chest rewards.

## Planning completion

All requested product decisions are resolved. The remaining work is implementation and verification. Explicit implementation defaults above cover routine edge cases; tuning XP and drop rates remains configurable without changing the agreed mechanics.
