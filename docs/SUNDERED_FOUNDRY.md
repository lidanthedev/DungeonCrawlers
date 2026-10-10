# The Sundered Foundry

Original Floor III expansion for DungeonCrawlers. Veyra built a copper-and-deepslate cathedral around a chained furnace heart. The dungeon's bells, counterweights, workshops and ruined service bridges teach the machinery she uses against the party.

Players enter through `/dungeon start floor_3`. Normal is available immediately; higher tiers use the existing per-floor difficulty progression. Impossible's extra mechanics activate only for `Difficulty.IMPOSSIBLE`. Existing Floor I templates, encounter and rewards keep their configuration.

## Rooms and progression

Every generated run includes six different exploration/combat rooms and the final Chainkeeper room. The floor's explicit room pool excludes other floors' rooms; its planner consumes unused layouts before repeating. Standard jigsaw doors, mob-clear requirements, portal countdown, party snapshots, ghosts, secrets, blessings, score, XP and reward entitlements remain authoritative.

| Room | Architecture | Gameplay |
| --- | --- | --- |
| Foundry vestibule | Ribbed entry hall, monumental copper arches, class station and outfitter lockers | Party preparation, two secrets and first door |
| Resonance vault | Circular bell chamber, compass mosaics, suspended bells and amethyst resonators | Seven enemies, four secrets and a seeded verse puzzle. Wrong inputs reset it. Mob kills alone cannot clear it. |
| Counterweight engine | Elongated machinery hall, suspended copper weights and raised anvil control deck | Seven enemies and four secrets. Two alive players hold opposite plates for three seconds; solo survivors use the latch plus one plate. |
| Split forge | Cross-shaped workshop, glowing blast-furnace banks, raised smelters, smithing bays and chimneys | Nine enemies around the workshops and gallery, four secrets including the smuggler's kiln |
| Ashen archives | Tall capped bookcases, reading desks, two inspection levels and the sealed index | Eight enemies, four secrets and relics hidden above and below the shelves |
| Storm turbine | Circular gallery, eight lit governor housings, caged spindle and elevated blades | Nine enemies, four secrets, rotating display rotors and projected blade traces |
| Broken skyway | Long lit trench, chain-suspended supports, copper jump landings and elevated inspection route | Seven enemies, four secrets, running jumps and saved safe footing; falls return to the last landing |
| Last Warden | Cruciform vault, anvil monuments, soul lanterns, stepped copper dais and reliquaries | Two required Chainkeeper minibosses and four secrets; both must fall to open the heart gate |
| Heart gate | Round antechamber, crying-obsidian pylons, framed portal and last-watch vaults | Two secrets and the existing synchronized portal transition |
| Heart cathedral | 81 × 49 × 81 arena, stained roof ribs, four structural piers and chained crystal | Boss introduction and first fight; conceals the crucible below |

Trapped chests use the existing standard-secret path; ordinary chests grant existing configured blessings. Hidden caches have reachable entrances. Rewards reuse configured CaveItems IDs `UNDEAD_ESSENCE` and `CRYPT_FRAGMENT`, with larger Floor III quantities and an additional score-gated obsidian offer.

The renovated pack contains **32 secrets and 49 required enemies** across a complete run. Each combat room has an original cache, two enclosed side vaults with sightline baffles and a staircase-accessed inspection cache. The archives and skyway keep their original upper blessing cache; the other combat rooms gain one gallery blessing cache. Start and portal each have two standard secrets. All enemy placements use normal/miniboss schematic markers and existing combat clear gates.

All ten templates have a continuous foundation, solid outer walls and a closed roof. Chamber vaults have solid masonry above each roof step, and unused space around their irregular footprints is filled before secret passages are deliberately excavated. The only exterior apertures are the authored three-by-three connectors used by the existing dungeon door system. Skyway falls and crucible gaps remain deliberate internal challenges enclosed by the foundation and walls.

## Veyra, the Chainbound Architect

Veyra is a real MythicMobs actor. Impossible has two separate lives; defeating the first form starts the transformation and spawns the second form at full health. Each life uses the configured health, selected difficulty multiplier and party scaling once. Normal retains one life. The six-and-a-half-second introduction locks boss damage and AI, presents dialogue and titles, builds a rotating copper crown, suspends Veyra between four animated pier chains, and sends a cyan pulse through the arena. Players retain their cameras and controls.

| Attack | Telegraph and response |
| --- | --- |
| Forge wave | Golden starting ring, then an expanding damaging ground wave. Jump its edge. |
| Shattered brands | Marks current player locations. Spread and leave the gold circles before detonation. |
| Cantor's lance / reprisal | A fixed line targets a participant, then strikes and dashes Veyra along its direction. The final phase locks and strikes three separate lines, each with at least one second to react. Step sideways after each lock. |
| Chain draw | Warns a spoke, removes its actual collision floor and animates a suspended slab toward the chain anchor. A rotating chain cut sweeps the hub. Use the diagonal paths and jump the cut. |
| Heaven's guillotine | Cyan marks the central hub and one island. Three suspended anvil structures descend onto the other islands after a five-second default evacuation window. |
| Rift pulse | Repeated expanding jump rings plus a separately telegraphed crosscut. |
| Chain cage | Each alive player gets a fixed seven-block gold circle and a physical chain tether. Run outside your own circle to snap the chain. Remaining chains tighten, gently pull inward, and detonate once after the warning plus 2.2 seconds. Departed players are discarded. |
| Clockwork requiem | Four physical chains sweep a rotating cross at shin height. Jump the chains or move with the cross; the five-block hub is clear. Final-phase casts rotate faster and add expanding rings through the hub. |
| Counterweight verdict | Cyan floor pads appear five blocks east/west of the heart. Two or more alive players must occupy both pads; a solo survivor can hold either. Continuous occupancy for up to 1.8 seconds staggers Veyra for three seconds and tears off up to 3% max HP, leaving at least one HP for the killing blow by players. A missed deadline produces one arena-wide hit. Veyra pauses melee while channeling. |
| Last weave | Final-phase chain sweep and bridge pull overlap delayed brands and falling anvils. Leave the brand, time the sweep, then reach cyan. |

Normal rotates Forge Wave, Brands, Lance and Counterweights, with faster final-phase casts and the three-strike Reprisal at 18% health. Impossible changes arenas when the first form dies, including a single lethal hit. The second form remains invulnerable with AI paused throughout the full transformation, then rotates seven attacks, including Cage and Clockwork. At 18% of its own health, it opens the final phase with Last Weave, accelerates the chain cross and adds ring pressure. Damage to the first form cannot carry into the second life. Only the second defeat runs the victory sequence. Repeated death events for the first actor are ignored; losing the protected second actor fails and cleans the run without rewards.

## The Impossible transformation

The default sequence lasts fourteen seconds. Eight animated chains extend from ceiling anchors toward the four piers and deck sections, with sagging links that straighten under load. The party braces at the central heart. Before any deck section detaches, the group moves safely down ten blocks onto the built crucible hub.

Eight sectors of the actual upper floor disappear in sequence. Four piers and their capitals detach, large display slabs and pier models lift, roll and pull outward, and anvil impacts, dust, dialogue and note-block music accompany the movement. The lower crucible becomes the playable arena. The heart's cold crystal shell changes into magma and furnace light.

The crucible has four islands, a central hub, narrow cardinal spokes, diagonal crossings and a perimeter ring, with gaps between them. Chain draw temporarily retracts a spoke. The remaining routes connect every refuge even while that spoke is absent. Falling into a gap returns an alive player to the hub and applies a sourced boss hit, preventing attacks from below the playable surface.

At 18%, the Last Weave begins. Veyra glows, the music speeds up, attacks arrive faster and existing mechanics overlap. Death first starts a five-second chain-collapse sequence. The deck and structural blocks restore, online participants return to the original arena, and victory effects play before the existing completion/reward path runs.

## Ownership and configuration

- `core/encounter/ChainboundEncounter` owns stages, thresholds, attack scheduling, exact-entity death acceptance and completion.
- `CounterweightTrial` verifies uninterrupted pad occupancy, resets on missed samples and adapts to partner departure.
- `BukkitChainboundArena` owns participant filtering, telegraphs, damage, target selection, safety teleports, sounds and the shared boss bar.
- `BukkitFoundryScene` owns moving displays and saved original block states. Combat uses 48 reusable chain-link displays and restores cyan floor pads after every cast. It caps displays at 220 and saved states at 6,000; mutations stay within the boss arena. Restore/cleanup removes the exact owned entities and event listener.
- `FoundryPuzzle` owns instance-local solution progress. `BukkitFoundryRooms` derives all locations from generated placements and rotations and holds the existing combat room's clear gate. Puzzle instructions remain holograms; Resonance has four floating rune labels in place of physical signs. Combat cleanup removes its clues, labels and rotors.
- All boss work uses the existing encounter tick. Room effects update at 5 Hz; boss display interpolation and telegraphs update at 10 Hz. There are no additional encounter scheduler tasks or global entity scans.
- `foundry.yml` validates finite health/damage, a final threshold between zero and one, cinematic bounds and non-overlapping attack intervals. It is read when an encounter is created. Config validation and hashing include it and `rooms_foundry.yml`. Schema 2 removes `transform-threshold`; schema 1 remains readable and keeps administrator tuning without rewriting the file. Its retired threshold is ignored.
- Floor schema 4 adds `generation.room-pool`. Migration backs up schemas 1–3 and preserves configured values. An empty pool keeps the previous unrestricted planner behavior.
- On first installation, bundled templates and Foundry MythicMobs files are copied only if missing. Administrator-edited files are preserved. Run `mm reload` after initial installation to activate the new mob and skill definitions.
- `python3 scripts/build_foundry_floor.py` reproduces all schematics. `python3 scripts/test_foundry_floor.py` independently decodes their NBT/block data, rejects physical sign blocks/entities, checks every exterior face for unintended holes, verifies intact connectors/markers, walks supported routes to all secrets and enemy spawns, and checks crucible connectivity.

## Verification

See [the human gate](SUNDERED_FOUNDRY_HUMAN_GATE.md) for exact build and live-test status. Automated checks do not establish multiplayer balance, artistic quality in the client, or frame rate under five-player load; those require the documented live pass.

## Combat tuning

The 2026-10-10 balance revision uses live Floor I MythicMobs as its baseline. Crypt enemies have 5M HP and 400K damage; its minibosses have 20M HP and 1M damage. Foundry regulars now have 2–3 times that health and distinct attack strengths.

| Mob | Base health | Base damage | Special damage |
| --- | ---: | ---: | ---: |
| Riveted Sentinel | 15M | 900K | Hammer 1.2M |
| Ashen Cantor | 12M | 750K | Bolt 1M |
| Gearweb Skitter | 10M | 650K | Leap uses melee |
| Last Chainkeeper | 60M | 1.8M | Hammer 1.2M |
| Veyra | 200M | 3.5M | Arena attacks use configured boss damage |

Existing difficulty and party multipliers still apply. Solo Impossible Veyra has 1B HP per life. The arena normalizes Mythic's spawn health against the bundled default before applying `foundry.yml`, so raising the base does not multiply health twice. Existing installations need their Foundry Mythic definitions and `foundry.yml` updated explicitly; the installer preserves existing files.
