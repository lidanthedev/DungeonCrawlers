# The Sundered Foundry

Original Floor III expansion for DungeonCrawlers. Veyra built a copper-and-deepslate cathedral around a chained furnace heart. The dungeon's bells, counterweights, workshops and ruined service bridges teach the machinery she uses against the party.

Players enter through `/dungeon start floor_3`. Normal is available immediately; higher tiers use the existing per-floor difficulty progression. Impossible's extra mechanics activate only for `Difficulty.IMPOSSIBLE`. Existing Floor I templates, encounter and rewards keep their configuration.

## Rooms and progression

Every generated run includes six different exploration/combat rooms and the final Chainkeeper room. The floor's explicit room pool excludes other floors' rooms; its planner consumes unused layouts before repeating. Standard jigsaw doors, mob-clear requirements, portal countdown, party snapshots, ghosts, secrets, blessings, score, XP and reward entitlements remain authoritative.

| Room | Architecture | Gameplay |
| --- | --- | --- |
| Foundry vestibule | Ribbed entry hall, copper windows, class-selection station | Party preparation and first door |
| Resonance vault | Circular bell chamber, four compass runes, hanging bells | Interpret a seeded verse: first bell, opposite, next clockwise, remaining bell. Wrong inputs reset it. Mob kills alone cannot clear it. |
| Counterweight engine | Elongated machinery hall, suspended chains, opposing copper pads | Two distinct alive players hold opposite plates continuously for three seconds. Solo survivors use a maintenance latch plus one plate. Leaving a pad resets the timer. |
| Split forge | Cross-shaped workshop, twin raised furnaces, chimneys and service aisles | Four distributed enemies and concealed maintenance cache |
| Ashen archives | Tall library aisles and stepped mezzanine | Three enemies, environmental testimony and a blessing chest above the shelves |
| Storm turbine | Circular gallery, radial elevated blades, hanging spindle | Four enemies, rotating display rotors and projected blade traces |
| Broken skyway | Long trench, copper jump landings, ascending secret route | Running jumps, saved safe footing and upper blessing cache; falls return to the last landing |
| Last Warden | Cruciform vault, heavy anvils and raised copper dais | Chainkeeper miniboss; opens the heart gate after defeat |
| Heart gate | Round antechamber, framed portal and warning inscriptions | Existing synchronized portal transition |
| Heart cathedral | 81 × 49 × 81 arena, stained roof ribs, four structural piers and chained crystal | Boss introduction and first fight; conceals the crucible below |

Trapped chests use the existing standard-secret path; ordinary chests grant existing configured blessings. Hidden caches have reachable entrances. Rewards reuse configured CaveItems IDs `UNDEAD_ESSENCE` and `CRYPT_FRAGMENT`, with larger Floor III quantities and an additional score-gated obsidian offer.

## Veyra, the Chainbound Architect

Veyra is a real MythicMobs actor with one persistent health pool. Health settings apply before party scaling and retain the selected difficulty's existing multiplier. The six-and-a-half-second introduction locks boss damage and AI, presents dialogue and titles, builds a rotating copper crown, and sends a cyan pulse through the arena. Players retain their cameras and controls.

| Attack | Telegraph and response |
| --- | --- |
| Forge wave | Golden starting ring, then an expanding damaging ground wave. Jump its edge. |
| Shattered brands | Marks current player locations. Spread and leave the gold circles before detonation. |
| Cantor's lance | A fixed line targets a participant, then strikes and repositions Veyra near the party. Step sideways. |
| Chain draw | Warns a spoke, removes its actual collision floor and animates a suspended slab toward the chain anchor. A rotating chain cut sweeps the hub. Use the diagonal paths and jump the cut. |
| Heaven's guillotine | Cyan marks the central hub and one island. Three suspended anvil structures descend onto the other islands after a five-second default evacuation window. |
| Rift pulse | Repeated expanding jump rings plus a separately telegraphed crosscut. |
| Last weave | Final-phase chain sweep and bridge pull overlap delayed brands and falling anvils. Leave the brand, time the sweep, then reach cyan. |

Normal uses the first three attacks and a faster final phase at 18% health. Impossible changes arenas at 65% and unlocks the last four attacks. Damage is capped before the transformation threshold so one burst cannot skip the cinematic. Incoming damage and outgoing attacks pause during the cinematic; the health pool is retained and no replacement boss is spawned.

## The Impossible transformation

The default sequence lasts fourteen seconds. Eight animated chains extend from ceiling anchors toward the four piers and deck sections, with sagging links that straighten under load. The party braces at the central heart. Before any deck section detaches, the group moves safely down ten blocks onto the built crucible hub.

Eight sectors of the actual upper floor disappear in sequence. Four piers and their capitals detach, large display slabs and pier models lift, roll and pull outward, and anvil impacts, dust, dialogue and note-block music accompany the movement. The lower crucible becomes the playable arena. The heart's cold crystal shell changes into magma and furnace light.

The crucible has four islands, a central hub, narrow cardinal spokes, diagonal crossings and a perimeter ring, with gaps between them. Chain draw temporarily retracts a spoke. The remaining routes connect every refuge even while that spoke is absent. Falling into a gap returns an alive player to the hub and applies a sourced boss hit, preventing attacks from below the playable surface.

At 18%, the Last Weave begins. Veyra glows, the music speeds up, attacks arrive faster and existing mechanics overlap. Death first starts a five-second chain-collapse sequence. The deck and structural blocks restore, online participants return to the original arena, and victory effects play before the existing completion/reward path runs.

## Ownership and configuration

- `core/encounter/ChainboundEncounter` owns stages, thresholds, attack scheduling, exact-entity death acceptance and completion.
- `BukkitChainboundArena` owns participant filtering, telegraphs, damage, target selection, safety teleports, sounds and the shared boss bar.
- `BukkitFoundryScene` owns moving displays and saved original block states. It caps displays at 220 and saved states at 6,000; mutations stay within the boss arena. Restore/cleanup removes the exact owned entities and event listener.
- `FoundryPuzzle` owns instance-local solution progress. `BukkitFoundryRooms` derives all locations from generated placements and rotations and holds the existing combat room's clear gate. Combat cleanup also removes its clues and rotors.
- All boss work uses the existing encounter tick. Room effects update at 5 Hz; boss display interpolation and telegraphs update at 10 Hz. There are no additional encounter scheduler tasks or global entity scans.
- `foundry.yml` validates finite health/damage, ordered thresholds, cinematic bounds and non-overlapping attack intervals. It is read when an encounter is created. Config validation and hashing include it and `rooms_foundry.yml`.
- Floor schema 4 adds `generation.room-pool`. Migration backs up schemas 1–3 and preserves configured values. An empty pool keeps the previous unrestricted planner behavior.
- On first installation, bundled templates and Foundry MythicMobs files are copied only if missing. Administrator-edited files are preserved. Run `mm reload` after initial installation to activate the new mob and skill definitions.
- `python3 scripts/build_foundry_floor.py` reproduces all schematics. `python3 scripts/test_foundry_floor.py` independently decodes their NBT/block data and checks supported routes, accessible secrets and crucible connectivity.

## Verification

See [the human gate](SUNDERED_FOUNDRY_HUMAN_GATE.md) for exact build and live-test status. Automated checks do not establish multiplayer balance, artistic quality in the client, or frame rate under five-player load; those require the documented live pass.
