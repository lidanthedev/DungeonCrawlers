# Floor 1 boss: The Mad Ringmaster

Status: implemented and deployed, 2026-10-09. The arena is built in `ringmaster_authoring`; Lidan will save it over `f1boss`. See [live validation](FLOOR_1_RINGMASTER_HUMAN_GATE.md). The design below remains the reference for unverified balance targets and proposed rewards.

The user wants a Bonzo and Jevil mashup in the supplied purple carousel arena. Keep Bonzo's two lives, undead performers, and balloon magic. Give the second life Jevil's card suits, spinning attack patterns, and scythe climax. Make the personality theatrical and mischievous. The fight should look chaotic while giving players clear rules they can learn.

## Intended experience

- Floor 1 Normal should take roughly 2–3 minutes for an appropriately geared party learning the fight. Experienced or overgeared players can finish faster.
- Support the current 1–5 player range. Every required dodge must work with ordinary walking or sprinting, without a movement ability, healer, resource pack, or particular class.
- Two health bars, one fake death, and a short finale within the second life. No third full health bar.
- Use one major arena attack at a time. Give melee players time to attack between casts, and stop spawning adds during the second life.

Working name: **The Mad Ringmaster**. A purple-and-blue jester carries a balloon cane. In the second life, the mask cracks, the staff becomes a scythe, and card-suit effects replace the cheerful balloon effects.

## Arena assessment and changes

The screenshot has the right identity: a circular stage, striped canopy, purple floor, dark surroundings, and a carousel spindle. Keep that silhouette and palette.

The central cylinder appears to take a large share of the visible arena. It hides the boss and teammates, restricts sightlines, and could encourage players to camp on the opposite side. The dark edges also make hazard boundaries harder to read. These are concerns inferred from one perspective, not measurements of the build.

Recommended layout, with proposed dimensions rather than dimensions inferred from the image:

| Part | Build target | Purpose |
| --- | --- | --- |
| Stage | About 33 blocks across, flat throughout | Room for five players and readable dodges |
| Central spindle | About 5 blocks across at ground level | Preserve the carousel without the current large obstruction |
| Movement space | About 12–14 blocks from hub to rim, before local cover | Keep a continuous route around the hub |
| Canopy | At least 9 blocks above the floor | Leave room to see falling scythes and projectile warnings |
| Cover | Four 2×2 pillars, about 3 blocks high, near the outer rim | Brief protection from direct balloon shots |
| Floor regions | Four quarters with large heart, spade, club, and diamond motifs | Identify the region an attack will hit |
| Boundary | Clearly lit, physically enclosed rim | No accidental fall deaths or knockback into the void |

Trim the wide cylinder base and any floor protrusions that snag movement. Keep the canopy's wide shape overhead; make its ground support slim. Avoid climbable cover, safe ledges, or holes. Leave a clear route on both sides of every cover pillar.

Use pale trim and recessed lighting to separate the floor quarters. Keep permanent purple decoration dimmer than attack warnings. Pair color with suit shapes, floor outlines, sound, and short text cues. Do not require players to distinguish red from green.

The boss fights on the playable ring, never on top of the hub. Give it four authored ground positions between cover pillars. It moves between casts and stays still while casting. The entry and reward positions sit outside the active movement route, with the reward chest becoming available through the existing completion flow.

If the existing ring already has enough space, start by shrinking the hub and improving lighting. Expand the floor only if the movement test still feels cramped.

## Fight sequence

### Entrance, about 4 seconds

The boss bows beside the spindle. Players can spread out without taking damage. Show the first health bar and one short line: "A full house! Let's see who stays for the encore!"

### Act I: The opening show

The boss moves between ground positions, attacks, then pauses. Players learn to sidestep projectiles and deal with a few adds.

**Balloon fan.** After a 1-second cane windup, fire three large, visible balloons toward one living player's position. Lock the aim at the end of the windup; the balloons do not track afterward. Leave gaps wide enough to sidestep. Ordinary cover blocks these shots, and impacts cause no terrain damage. Allow at least 2.5 seconds between fans.

**Undead performers.** Summon two weak performers about every 15 seconds, with four alive at most. In solo play, summon one at a time with two alive at most. They pressure players who stand still, but do not need to die before the boss can take damage. Give their arrival a visible ground cue. They drop no independent boss loot or completion credit.

The boss remains damageable, including during casts. Give at least 3 seconds without a new cast or reposition after each attack cycle so melee can land hits.

### Fake death, about 4 seconds

The first life reaches zero. Remove all adds, projectiles, and active hazards. The boss collapses, the bar disappears, and the music pauses briefly. Do not show victory, award completion, spawn the reward chest, or trigger player revival as a reward.

Then the mask cracks. "Curtain call? I haven't even started spinning!" The second form appears at a clearly marked, unoccupied ground position. Restore the new life to full health, about 1.25 times the first life's health budget. There is no damage during the transition, and the first cast gives its full warning afterward.

### Act II: The world revolves

Stop summoning performers. Alternate these attacks with a 3–4 second recovery window. The initial order is fixed so a new party can learn it. Later cycles may vary the starting quarter, while preserving the same warnings and escape routes.

| Attack | Warning and pattern | Player response |
| --- | --- | --- |
| Carousel sweep | A chime and floor outline warn for 1.5 seconds. One narrow radial hazard crosses the ring through a quarter turn over about 6 seconds. Mark the travel direction before it starts. | Move ahead of the sweep or follow behind it around the hub. |
| Suit spotlight | Mark one floor quarter with its suit outline for 3.5 seconds, then pulse it once. The other three quarters remain safe. | Leave the marked quarter before the pulse. |
| Balloon fan | Reuse the first act's three-balloon pattern, with card-shaped effects mixed in visually. Keep the gaps and warning. | Sidestep or use nearby cover. |

The carousel is a visible floor hazard rather than a stream of physical balloons. Cover does not protect players standing in its marked path. Direct balloon shots still collide with cover. Make this difference visible with ground outlines versus airborne projectiles.

For the first prototype, start the sweep around 2 blocks wide and never reverse it mid-cast. Its speed must let a player at the outer rim keep up by walking on an unobstructed route. Check that the hub and cover do not trap players between the sweep and a wall. If this fails, slow the sweep or shorten its arc.

Suit spotlight hits only one quarter on Normal. Before choosing a quarter, verify that living players there can reach safe floor during the warning. Delay the cast if necessary. A cosmetic suit choice must never change the rules without a cue.

### Finale: The last laugh

Once the second life falls below 25%, replace its next carousel cast with one short scythe sequence. It happens at most once. Strong damage can finish the fight before the finale starts.

Over roughly 6–8 seconds, drop three large scythes one after another. Each marks a roughly 2-block-radius circle at a living player's current position, locks that position, and gives at least 1.5 seconds to leave it. No homing after the marker appears. Each impact produces one brief ground pulse; the visible outline remains until the danger ends.

Do not combine the finale with suit spotlight, balloons, adds, or a global unavoidable hit. The boss stays damageable. Surviving the finale grants a 5-second attack window while the boss laughs and catches its breath, then the normal second-act cycle resumes if needed.

The final death stops every attack immediately and completes the dungeon once.

## Starting balance targets

These values are prototype targets, not verified CaveCrawlers damage numbers.

- Measure against an appropriately geared Floor 1 player after ordinary mitigation. Aim for a balloon hit to remove about 10–15% of that reference player's health, a sweep or spotlight hit 20–25%, and a scythe hit 25–30%. Implement ordinary damage values through the existing combat/stat path, not damage equal to a percentage of each victim's maximum health.
- One positioning mistake should hurt. A single hit should not kill a healthy baseline player. Tank stats and defensive skills must retain their value.
- A continuous sweep can damage the same player at most once per 0.75 seconds, so collision checks cannot drain health every tick. A spotlight or scythe impact hits each player only once per pulse.
- Start with first-life health sufficient for about 45–60 seconds of active damage for the reference party. Give the second life about 25% more health and account for movement downtime when checking the total duration.
- If adding party health scaling, use the admitted party size once at encounter start. A proposed starting factor is `1 + 0.55 × (players − 1)`, applied once to both lives after existing difficulty health scaling. Do not reduce health when someone dies or leaves. Do not scale projectile density with party size.
- Preserve the existing difficulty modifiers. Calibrate Normal first; do not silently add a second difficulty multiplier or make warnings shorter for higher tiers in the initial version.

## Rewards

Proposed first-release identity: a **Ringmaster's Cane** that fires a small balloon fan, and **Jester Fragments** for its upgrade path. Names and item IDs remain proposals. Put drops in the existing end-of-run reward offers, not loose items from the fake death or performers. Set damage, prices, and drop rates only after checking the current item economy. A revival mask can wait; it would require separate balance against dungeon ghost and revival rules.

## Smallest implementation path

The bundled Floor 1 now uses `MadRingmaster` and encounter `ringmaster`, with floor schema version 3. Its bundled template remains `crypt_guardian_arena`; the live floor uses the authored `f1boss` template. Existing custom floor definitions retain their configured boss during migration. The live boss and encounter were explicitly changed through Pterodactyl MCP, with a backup. Maximum party size remains five.

The existing `EncounterFactory` contract supports start, tick, entity death, cleanup, and completion. `MultistageTestEncounter` demonstrates a two-stage death transition, but is a test encounter rather than the finished fight. `EncounterContext` now supplies the transformed arena center and rotation alongside the boss spawn. `BukkitRingmasterArena` uses the existing run/lifecycle services for targeting and the central encounter tick for attacks and cleanup.

Implement one dedicated ringmaster encounter through that contract. Let DungeonCrawlers own the two lives, stage changes, active instance state, and completion. Use the existing MythicMobs integration for forms and compatible attacks after checking the installed skill capabilities. Avoid a second boss framework or a new dependency. The four ground positions and arena bounds should follow the room template's placement transform, not hard-coded world coordinates.

Implemented scope:

- `scripts/build_ringmaster_arena.py` generates a 39×17×39 schematic with a roughly 33-block movement stage, 5-block hub, four cover pillars, suit glyphs, lighting, one boss marker, one reward marker, and five player markers. Existing authoring captures its marker metadata.
- One encounter and its registration, with the minimum Bukkit adapter for player targeting, damage, and effects.
- `server-config/mythicmobs/Mobs/ringmaster.yml` defines `MadRingmaster`, `MadRingmasterEncore`, and `RingmasterPerformer`; all three IDs loaded live. Attacks are owned by DungeonCrawlers and need no premium Mythic skills.
- Floor schema migration coverage and explicit live boss/encounter configuration. New CaveItems and rewards remain proposals and were not added.
- Existing portal death callbacks, difficulty scaling, ghosts, reward completion, and cleanup paths.
- Focused encounter tests and a player-facing human gate. Graph update after code changes.

Transition failure, missing mob IDs, disconnects, forced reload, instance cancellation, and plugin disable must cancel pending casts and remove that instance's boss, adds, projectiles, and displays. A failed second spawn must fail the encounter without issuing rewards. Ghosts and players who left cannot be targeted or damage the boss.

## Acceptance before release

1. Walk and sprint a full lap with five players. Check both routes around cover, all suit-quarter boundaries, and melee access to every boss position.
2. Test the sweep from the outer rim and each cover pillar. A player can escape without a movement skill, and no permanent camping spot avoids the whole fight.
3. Check visibility with ordinary client settings and reduced particles. Warnings remain readable against the purple floor, without relying on music or color alone.
4. Test solo and five-player fights with baseline gear. Tune damage and health from observed hit counts and completion times rather than importing Hypixel's numbers.
5. Verify first death gives no completion or rewards, even for simultaneous lethal hits. Second death gives exactly one completion, including during the finale.
6. Verify deaths/ghosts, leaving, revival, second-spawn failure, reload, and cancellation. No orphan mobs, effects, callbacks, or delayed damage survive cleanup.
7. Check two simultaneous dungeon instances for isolated targeting and cleanup, and verify attack damage receives existing difficulty modifiers exactly once.

## Grand carnival revision, 2026-10-10

The user requested a much larger arena and a more elaborate fight after the
first live prototype. The implementation now uses a 113×59×113 arena with an
85-block movement floor, stands, monumental suit cards and jester heads,
chandelier and striped dome. The boss has two display-entity jester forms,
a six-second transformation, original note-block music, and seven attack
patterns including gift boxes, spinning card fans, a gapped card ring,
two-arm carousel with horses, three spotlight pulses and six falling scythes.
Performer adds now include fast baby-zombie acrobats. First/second base HP is
55M/75M before existing difficulty and party multipliers.

These values supersede the smaller first prototype above. Current deployment,
room capture and test evidence is in `FLOOR_1_RINGMASTER_V2_HUMAN_GATE.md`.

### Carousel speed revision, 2026-10-10

After gameplay feedback, the carousel keeps the boss's melee AI and living-player
targeting active throughout the cast. Two seconds of warning precede a full
360-degree rotation over eight seconds, eight times the prior angular speed.
The attack ends at ten seconds, and the next pattern can start at fourteen
seconds. The floor arms stay centered on the arena as the boss moves. The cue
directs players toward the hub, where following the arms requires less movement.
This supersedes the earlier requirement to follow the sweep at the outer rim.

## Original reference basis

Bonzo's undead summons, revival, balloon barrage, and cover are documented in the [Hypixel SkyBlock community wiki](https://hypixelskyblock.minecraft.wiki/w/Bonzo). Jevil's carousel, card-suit patterns, and falling scythe climax are documented in the [Deltarune community wiki](https://deltarune.wiki/w/Jevil). All arena dimensions, timings, damage targets, dialogue, rewards, and the combined fight structure above are original proposals for this project.
