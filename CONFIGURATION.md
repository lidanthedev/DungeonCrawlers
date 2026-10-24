# DungeonCrawlers configuration

The plugin loads `config.yml`, `classes.yml`, `rooms.yml`, and the floor files under `floors/` as immutable registry snapshots. Invalid reloads leave the active snapshot unchanged.

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
