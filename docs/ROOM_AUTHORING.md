# Room authoring

1. Run `/dungeon room setup` and place the supplied marker items in a WorldEdit selection.
2. Use `/dungeon selection markers` to inspect the marker count and positions.
3. Run `/dungeon selection validate <type> <encounters>` and fix every reported error.
4. Save with `/dungeon room create <id> <type> <encounters>` or update an existing room with `/dungeon room update <id>`.

## Canonical markers

| Marker | Block | Rule |
| --- | --- | --- |
| Entrance | JIGSAW named `dungeoncrawlers:entrance` | Normal/miniboss and portal rooms require one. |
| Exit / door | JIGSAW named `dungeoncrawlers:exit` | Normal and START rooms require one. |
| Normal mob | `GRAY_CONCRETE_POWDER` | Requires NORMAL capability in normal rooms. |
| Miniboss mob | `YELLOW_CONCRETE_POWDER` | Requires MINIBOSS capability in normal rooms. |
| Player spawn | `EMERALD_BLOCK` | Required by START and BOSS rooms. |
| Class selector | `ORANGE_CONCRETE_POWDER` | START only; optional; maximum one; spawns the class-menu NPC when Citizens is available. |
| Boss spawn | `RED_CONCRETE_POWDER` | BOSS only; exactly one. |
| Reward chest | `LIME_CONCRETE_POWDER` | BOSS only; exactly one. |
| Blessing chest | `CHEST` | Secret blessing marker. |
| Standard secret | `TRAPPED_CHEST` | Standard secret marker. |
| Portal | connected `NETHER_PORTAL` blocks | PORTAL only; exactly one connected component. |

Jigsaws must use the expected connector target, pool, final state, and horizontal orientation. The Jigsaw kit items carry the marker id in `dungeoncrawlers:room_marker`; a placement listener applies the native fields when Bukkit does not expose them through `BlockStateMeta`. Ordinary Jigsaws are untouched.

The portal kit entry is a named `FLINT_AND_STEEL` tool because `NETHER_PORTAL` is a block, not a valid item stack. Light an obsidian frame to create the canonical connected portal-block marker.

During generation, authoring markers are removed after the room paste. The transformed START selector offset is then used to spawn a temporary Citizens `Class Selector` NPC one block above the marker. If Citizens is absent, the marker is still removed and no NPC is created.
