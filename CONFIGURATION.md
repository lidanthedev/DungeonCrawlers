# DungeonCrawlers configuration

The plugin loads `config.yml`, `classes.yml`, `rooms.yml`, `difficulties.yml`, and the floor files under `floors/` as immutable registry snapshots. Invalid reloads leave the active snapshot unchanged.

## Difficulties and Dungeon progression

`difficulties.yml`, schema 1, contains all ten tiers. Probabilities and reductions are fractions,
such as `runic-chance: 0.001` for 0.1%. Its defaults preserve the agreed health, duplicate-class,
incoming damage, isolation, XP and MAGIC_FIND multipliers. Hellish and above disable ordinary
revival while keeping healing. Nearby non-boss enemies grant one 20% damage reduction within
8 blocks. Isolation requires no alive, online teammate within 12 blocks and includes solo runs.
Runic bosses are eligible only on Void and above.

Floor schema 2 adds configurable XP:

```yaml
dungeon-xp:
  completion: 100.0 # default: 100 × floor number²
  failure-factor: 0.1
```

Migration backs up schema-1 floors and preserves custom settings. Both XP amounts receive
the difficulty multiplier. Combat must start. Successful remaining participants, including
ghosts and offline players, get completion XP and the next unlock on that floor. Offline XP
waits for a healthy native persistence session. Native receipts prevent repeated grants.

Reward item entries may set `magic-find-sensitive: true`. The default is false. Sensitive
weights multiply by `1 + MAGIC_FIND / 100`, and offers freeze the sampled value and roll.

The native CaveCrawlers `dungeon` skill must exist with max level 60, its normal XP curve,
empty objectives and no permanent stat rewards. Each level adds 1% to HEALTH, DEFENSE,
STRENGTH, CRIT_DAMAGE, CRIT_CHANCE, INTELLIGENCE and ABILITY_DAMAGE inside dungeons.

Native items `RUNIC_FRAGMENT` and `RUNIC_PET` are configured in CaveCrawlers. Runic enemies
retain their abilities, gain another 10× health and 5× attack damage, and glow purple with
particles. Ordinary/miniboss fragment chances use MAGIC_FIND and award one fragment to
the killer. Runic bosses award exactly four. Inventory overflow uses the durable mailbox.

With CaveCrawlAddon enabled, the Legendary Runic pet uses normal pet leveling to 100 and
prefers Dungeon XP. Per pet level, its global bonuses are +1 HEALTH, +0.5 DEFENSE and
+0.1 MAGIC_FIND. Runic Power additionally boosts HEALTH by 0.5% per pet level inside
dungeons, reaching +50% at level 100 and multiplying with the Dungeon skill bonus.
Active at death, it latches a five-second revival once per player per run.
Switching pets during the ghost countdown preserves that revival. It restores the player's
current ghost position and forgives that death only after revival succeeds. Shop acquisition
is configured separately by the server owner.

Successful completion revives remaining online ghosts for reward access, retaining their
death penalties. Offline ghosts revive on reconnect during the reward period. Dungeon doors
accept main-hand left/right clicks even when an item ability cancels the click; ghosts cannot
open doors.

## Class selection

`floors/<floor>.yml` controls the classes available in a run:

```yaml
classes:
  allowed: [berserker, mage, tank, healer, archer]
```

The ids must exist in `classes.yml`. The class menu reads the configured display name, icon, and stat modifiers from that registry; it does not contain a hardcoded class list.

## Room authoring

`config.yml` contains the authoring policy for emerald player-spawn markers:

```yaml
authoring:
  emerald-marker-policy: replace # replace or retain
```

The temporary Citizens class-selector NPC can use a fixed player skin:

```yaml
class-selector:
  npc:
    skin: "SkinPlayerName"
```

Leave `skin` empty to keep Citizens' default skin behavior.

## Runtime periods

`config.yml` has a `timings` section for the plugin's runtime periods. Values are whole seconds and
include preparation and run deadlines, revive and portal countdowns, reward access windows, score
timing, cleanup and teleport safety windows, action-bar throttling, repository shutdown grace, and
pending recovery retention. Existing config files receive these defaults automatically when they
migrate to schema 6.

Room metadata is versioned separately. Schema 3 adds the optional START-room class-selector offset; schema 2 metadata remains readable with no selector. Use the authoring commands to regenerate metadata rather than editing it by hand.

Citizens is a soft dependency. No Citizens configuration is required: the plugin creates selector NPCs in a temporary in-memory registry and removes them with the run. The class GUI and class commands do not depend on Citizens.

See [COMMANDS.md](COMMANDS.md) and [room authoring](docs/ROOM_AUTHORING.md) for the complete workflow.
