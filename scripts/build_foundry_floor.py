#!/usr/bin/env python3
"""Build the shipped Foundry pack as deterministic Sponge v3 schematics, without dependencies.

Run this after editing architecture. Assertions check connector clearances, marker support,
secret reachability, and the lower arena's collision geometry before writing any assets.
"""
import gzip
import hashlib
import json
import math
from pathlib import Path
import struct

ROOT = Path(__file__).resolve().parents[1] / "src/main/resources/foundry/templates"
ROOMS = {
    "foundry_start": (41, 23, 33, "start"),
    "foundry_resonance": (49, 27, 49, "bell"),
    "foundry_counterweight": (49, 29, 57, "balance"),
    "foundry_splitforge": (57, 25, 49, "forge"),
    "foundry_archives": (41, 31, 57, "archive"),
    "foundry_turbine": (57, 35, 57, "turbine"),
    "foundry_catwalk": (41, 33, 65, "parkour"),
    "foundry_warden": (57, 33, 49, "warden"),
    "foundry_portal": (41, 29, 41, "portal"),
    "foundry_heart": (81, 49, 81, "boss"),
}


def string(value):
    encoded = value.encode("utf-8")
    return struct.pack(">H", len(encoded)) + encoded


def tag(kind, name, payload):
    return bytes([kind]) + string(name) + payload


def integer(name, value):
    return tag(3, name, struct.pack(">i", value))


def text(name, value):
    return tag(8, name, string(value))


def compound(name, payload):
    return tag(10, name, payload + b"\0")


class Room:
    def __init__(self, name, width, height, length, kind):
        self.name, self.width, self.height, self.length, self.kind = name, width, height, length, kind
        self.blocks, self.entities = {}, {}
        self.markers, self.secrets, self.connectors = [], [], []
        self.cx, self.cz = width // 2, length // 2

    def put(self, x, y, z, block):
        assert 0 <= x < self.width and 0 <= y < self.height and 0 <= z < self.length, (self.name, x, y, z)
        self.blocks[x, y, z] = "minecraft:" + block

    def box(self, x1, y1, z1, x2, y2, z2, block):
        for y in range(y1, y2 + 1):
            for z in range(z1, z2 + 1):
                for x in range(x1, x2 + 1):
                    self.put(x, y, z, block)

    def marker(self, x, y, z, material):
        self.put(x, y, z, material)
        self.markers.append((x, y, z, material))

    def connector(self, kind, z):
        facing = "north" if z == 0 else "south"
        self.box(self.cx - 1, 3, z, self.cx + 1, 5, z, "air")
        self.put(self.cx, 4, z, f"jigsaw[orientation={facing}_up]")
        self.entities[self.cx, 4, z] = text("Id", "minecraft:jigsaw") + compound("Data", text("name", f"dungeoncrawlers:{kind}") \
            + text("target", "dungeoncrawlers:connector") + text("pool", "minecraft:empty") + text("final_state", "minecraft:air") \
            + text("joint", "rollable"))
        self.connectors.append((self.cx, 4, z))

    def secret(self, x, y, z, blessing=False):
        self.put(x, y, z, "chest[facing=south,type=single,waterlogged=false]" if blessing
                 else "trapped_chest[facing=south,type=single,waterlogged=false]")
        self.secrets.append((x, y, z))

    def seal(self):
        # Decorative roofs and secret excavation must never expose the clipboard's exterior.
        for x in range(self.width):
            for z in range(self.length):
                self.put(x, 0, z, "reinforced_deepslate")
                self.put(x, self.height - 1, z, "deepslate_tiles")
        for y in range(1, self.height - 1):
            for x in range(self.width):
                for z in (0, self.length - 1):
                    if any(abs(x - cx) <= 1 and 3 <= y <= 5 and z == cz for cx, _, cz in self.connectors):
                        continue
                    self.put(x, y, z, "deepslate_bricks")
            for z in range(self.length):
                for x in (0, self.width - 1):
                    self.put(x, y, z, "deepslate_bricks")

    def validate(self):
        for x, y, z in self.connectors:
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    if dx or dy:
                        assert self.blocks.get((x + dx, y + dy, z), "minecraft:air") == "minecraft:air"
        for x, y, z, _ in self.markers:
            assert self.blocks.get((x, y - 1, z), "minecraft:air") != "minecraft:air", (self.name, "unsupported", x, y, z)
            assert self.blocks.get((x, y + 1, z), "minecraft:air") == "minecraft:air", (self.name, "headroom", x, y, z)
        for x, y, z in self.secrets:
            assert self.blocks.get((x, y + 1, z), "minecraft:air") == "minecraft:air"
            assert self.blocks.get((x, y - 1, z), "minecraft:air") != "minecraft:air"

    def schematic(self):
        palette, data = {"minecraft:air": 0}, bytearray()
        for y in range(self.height):
            for z in range(self.length):
                for x in range(self.width):
                    index = palette.setdefault(self.blocks.get((x, y, z), "minecraft:air"), len(palette))
                    while index >= 128:
                        data.append((index & 127) | 128)
                        index >>= 7
                    data.append(index)
        payload = integer("Version", 3) + integer("DataVersion", 3955)
        for name, size in [("Width", self.width), ("Height", self.height), ("Length", self.length)]:
            payload += tag(2, name, struct.pack(">h", size))
        payload += tag(11, "Offset", struct.pack(">iiii", 3, 0, 0, 0))
        payload += compound("Metadata", b"".join(integer("WEOffset" + axis, 0) for axis in "XYZ"))
        blocks = compound("Palette", b"".join(integer(name, value) for name, value in palette.items()))
        blocks += tag(7, "Data", struct.pack(">i", len(data)) + data)
        block_entities = b""
        for xyz, content in self.entities.items():
            block_entities += tag(11, "Pos", struct.pack(">iiii", 3, *xyz)) + content + b"\0"
        blocks += tag(9, "BlockEntities", bytes([10]) + struct.pack(">i", len(self.entities)) + block_entities)
        payload += compound("Blocks", blocks)
        payload += tag(9, "Entities", bytes([10]) + struct.pack(">i", 0))
        # The project's FAWE adapter reads Sponge v3, including its nested unnamed root.
        return compound("", compound("Schematic", payload))


def lower_platform(x, z):
    r = math.hypot(x, z)
    return r <= 8 or r <= 32 and (abs(x) <= 2 or abs(z) <= 2) or 29 <= r <= 32 \
        or abs(abs(x) - 17) <= 6 and abs(abs(z) - 17) <= 6 or r <= 29 and abs(abs(x) - abs(z)) <= 1.5


def boss(room):
    c = room.cx
    for x in range(-39, 40):
        for z in range(-39, 40):
            r = math.hypot(x, z)
            if r <= 38:
                room.put(c + x, 0, c + z, "crying_obsidian" if (x + z) % 6 == 0 else "blackstone")
                if r <= 32:
                    if lower_platform(x, z):
                        room.put(c + x, 1, c + z, "polished_blackstone_bricks" if r < 29 else "cracked_deepslate_tiles")
                        if abs(r - 8) < .7 or r >= 31 or abs(abs(x) - 17) == 6 and abs(abs(z) - 17) <= 6:
                            room.put(c + x, 1, c + z, "ochre_froglight")
                    else:
                        room.put(c + x, 0, c + z, "magma_block")
                    floor = "deepslate_tiles"
                    if abs(r - 9) < .65 or abs(r - 28) < .7:
                        floor = "oxidized_cut_copper"
                    if abs(x) <= 1 or abs(z) <= 1:
                        floor = "polished_diorite"
                    room.put(c + x, 11, c + z, floor)
                if 34 <= r <= 37:
                    for y in range(1, 40):
                        room.put(c + x, y, c + z, "deepslate_bricks" if y % 8 else "oxidized_cut_copper")
                    if (x + z) % 5 == 0:
                        for y in (5, 18, 31):
                            room.put(c + x, y, c + z, "sea_lantern")
                roof = 40 + int(6 * max(0, 1 - r / 38))
                room.put(c + x, roof, c + z, "tinted_glass" if abs(x) > 2 and abs(z) > 2 else "oxidized_cut_copper")
                if abs(math.sin(math.atan2(z, x) * 4)) < .06 and r > 5:
                    room.put(c + x, roof - 1, c + z, "sea_lantern")
    # Four destructible piers. The adapter turns their exact blocks into moving display piers.
    for x in (-18, 18):
        for z in (-18, 18):
            room.box(c + x - 1, 12, c + z - 1, c + x + 1, 27, c + z + 1, "polished_deepslate")
            room.box(c + x - 2, 28, c + z - 2, c + x + 2, 29, c + z + 2, "oxidized_cut_copper")
            room.put(c + x, 30, c + z, "sea_lantern")
    # A chained heart hangs over the central crucible; all players can circle underneath it.
    for y in range(30, 44):
        room.put(c, y, c, "chain[axis=y,waterlogged=false]")
    for dx in range(-4, 5):
        for dy in range(-4, 5):
            for dz in range(-4, 5):
                if abs(dx) + abs(dy) + abs(dz) <= 5:
                    room.put(c + dx, 27 + dy, c + dz, "amethyst_block" if (dx + dy + dz) % 3 else "sea_lantern")
    for i, x in enumerate((-6, -3, 0, 3, 6)):
        room.marker(c + x, 12, c + 27, "emerald_block")
    room.marker(c, 12, c, "red_concrete_powder")
    room.marker(c, 12, c - 27, "lime_concrete_powder")
    for x, z in [(0, 0), (0, 27), (0, -27), (17, 17), (-17, -17), (17, -17), (-17, 17)]:
        assert lower_platform(x, z)
        assert room.blocks.get((c + x, 1, c + z)) is not None


def chamber(room):
    c, d, kind = room.cx, room.cz, room.kind
    for x in range(room.width):
        for z in range(room.length):
            dx, dz = x - c, z - d
            r = math.hypot(dx, dz)
            inside = {
                "bell": r <= 21,
                "balance": abs(dx) <= 17 and abs(dz) <= 23 or r <= 23,
                "forge": abs(dx) <= 25 and abs(dz) <= 9 or abs(dx) <= 11 and abs(dz) <= 21,
                "archive": abs(dx) <= 16 and abs(dz) <= 24,
                "turbine": 9 <= r <= 25 or abs(dx) <= 3 or abs(dz) <= 3,
                "parkour": abs(dx) <= 15 and abs(dz) <= 28,
                "warden": r <= 22 or abs(dx) <= 24 and abs(dz) <= 8,
                "start": abs(dx) <= 16 and abs(dz) <= 12,
                "portal": r <= 17,
            }[kind] or abs(dx) <= 3
            if not inside:
                room.box(x, 0, z, x, room.height - 1, z, "deepslate_bricks")
                continue
            room.box(x, 0, z, x, 2, z, "deepslate_bricks")
            room.put(x, 2, z, "deepslate_tiles" if (x // 4 + z // 4) % 2 else "polished_deepslate")
            if (abs(dx) == 3 or z % 9 == 0) and abs(dx) <= 3:
                room.put(x, 2, z, "oxidized_cut_copper")
            roof = min(room.height - 2, 13 + int(8 * max(0, 1 - r / 25)))
            room.box(x, roof, z, x, room.height - 1, z, "deepslate_bricks")
            if (x + z) % 17 == 0:
                room.put(x, roof - 1, z, "sea_lantern")
    # Follow each room's footprint with arched windows, alcoves and alternating buttresses.
    floor_cells = {(x, z) for x, y, z in room.blocks if y == 2}
    for x, z in sorted(floor_cells):
        if all((x + dx, z + dz) in floor_cells for dx, dz in [(1, 0), (-1, 0), (0, 1), (0, -1)]):
            continue
        roof = min(room.height - 2, 13 + int(8 * max(0, 1 - math.hypot(x - c, z - d) / 25)))
        for y in range(3, roof):
            window = 6 <= y <= 11 and (x + z) % 7 in (2, 3, 4)
            room.put(x, y, z, "cyan_stained_glass" if window else "oxidized_cut_copper" if (x + z) % 7 == 0 else "deepslate_bricks")
        if (x + z) % 7 == 3:
            room.put(x, 5, z, "sea_lantern")
    # Thin ribs, tall arched windows, and service balconies frame a different silhouette in each room.
    for z in range(5, room.length - 5, 9):
        for side in (-1, 1):
            x = c + side * (12 if kind in ("start", "portal", "archive", "parkour") else 16)
            if room.blocks.get((x, 2, z)):
                room.box(x, 3, z, x, 11, z, "oxidized_cut_copper")
                room.put(x, 12, z, "sea_lantern")
                for dx in (-1, 1):
                    room.put(x + dx, 9, z, "iron_bars")
    embellish(room)
    if kind == "start":
        for x in (-6, -3, 0, 3, 6):
            room.marker(c + x, 3, 6, "emerald_block")
        room.marker(c - 7, 3, 10, "orange_concrete_powder")
        room.connector("exit", room.length - 1)
        extra_secrets(room)
        return
    room.connector("entrance", 0)
    if kind == "portal":
        room.box(c - 3, 3, d - 1, c + 3, 9, d - 1, "obsidian")
        room.box(c - 2, 4, d - 1, c + 2, 8, d - 1, "nether_portal[axis=x]")
        extra_secrets(room)
        return
    room.connector("exit", room.length - 1)
    if kind == "bell":
        for dx, dz in [(-8, -8), (8, -8), (8, 8), (-8, 8)]:
            room.put(c + dx, 3, d + dz, "chiseled_copper")
            room.put(c + dx, 5, d + dz, "bell[attachment=ceiling,facing=south,powered=false]")
            room.put(c + dx, 6, d + dz, "oxidized_cut_copper")
    elif kind == "balance":
        for dx in (-8, 8):
            room.box(c + dx - 2, 2, d - 2, c + dx + 2, 2, d + 2, "cut_copper")
            room.put(c + dx, 3, d, "heavy_weighted_pressure_plate[power=0]")
            for y in range(8, 20):
                room.put(c + dx, y, d, "chain[axis=y,waterlogged=false]")
        room.put(c, 3, d - 5, "lever[face=floor,facing=north,powered=false]")
    elif kind == "forge":
        for dx in (-16, 16):
            room.box(c + dx - 4, 3, d - 4, c + dx + 4, 5, d + 4, "polished_blackstone_bricks")
            room.box(c + dx - 3, 6, d - 3, c + dx + 3, 6, d + 3, "magma_block")
            room.box(c + dx - 1, 7, d - 1, c + dx + 1, 16, d + 1, "deepslate_bricks")
        for dx in (-9, 9):
            room.box(c + dx - 2, 3, d - 8, c + dx + 2, 3, d + 8, "oxidized_cut_copper")
    elif kind == "archive":
        for dx in (-11, -7, 7, 11):
            room.box(c + dx, 3, 12, c + dx, 7, room.length - 12, "bookshelf")
        room.box(c - 16, 6, 10, c - 13, 6, room.length - 10, "dark_oak_planks")
        for z in range(5, 11):
            room.box(c - 16, 3, z, c - 14, z - 4, z, "dark_oak_planks")
        room.secret(c - 15, 7, room.length - 12, True)
    elif kind == "turbine":
        for angle in range(0, 360, 45):
            for r in range(10, 22):
                x, z = round(c + r * math.cos(math.radians(angle))), round(d + r * math.sin(math.radians(angle)))
                room.put(x, 4 + r // 6, z, "cut_copper")
        room.box(c - 2, 11, d - 2, c + 2, 12, d + 2, "sea_lantern")
        for y in range(13, 30):
            room.put(c, y, d, "chain[axis=y,waterlogged=false]")
    elif kind == "parkour":
        # Three-block running jumps cross the maintenance trench; a fall returns to the last landing.
        room.box(c - 12, 2, 13, c + 12, 2, room.length - 13, "air")
        for z in range(12, room.length - 11, 5):
            room.box(c - 2, 2, z, c + 2, 2, z + 1, "oxidized_cut_copper")
        room.box(c - 13, 7, 22, c - 10, 7, room.length - 16, "polished_blackstone_bricks")
        for z in range(12, 23):
            room.box(c - 13, 2, z, c - 11, min(7, 2 + (z - 12) // 2), z, "polished_blackstone_bricks")
        room.secret(c - 12, 8, room.length - 17, True)
    elif kind == "warden":
        room.box(c - 5, 3, d - 4, c + 5, 3, d + 4, "oxidized_cut_copper")
        for dx in (-10, 10):
            room.box(c + dx - 2, 3, d - 2, c + dx + 2, 14, d + 2, "polished_blackstone_bricks")
            room.put(c + dx, 15, d, "shroomlight")
        room.marker(c, 4, d, "yellow_concrete_powder")
    # Safe, distributed spawn markers. Every authored room uses distinct placement around its machinery.
    if kind != "warden":
        positions = [(c - 5, 8), (c + 5, room.length - 9)] if kind != "parkour" else [(c - 5, 7), (c + 5, room.length - 8)]
        if kind in ("forge", "turbine"):
            offset = 10 if kind == "turbine" else 6
            positions += [(c - offset, d - offset), (c + offset, d + offset)]
        if kind == "archive":
            positions += [(c, d)]
        for x, z in positions:
            room.put(x, 2, z, "polished_deepslate")
            room.box(x, 3, z, x, 4, z, "air")
            room.marker(x, 3, z, "gray_concrete_powder")
    if kind not in ("archive", "parkour"):
        # A low crawl tunnel enters a maintenance cache behind a pillar, with visible scrape marks.
        room.box(c - 12, 2, 5, c - 7, 2, 7, "polished_blackstone_bricks")
        room.box(c - 12, 3, 5, c - 7, 4, 7, "air")
        room.box(c - 12, 5, 5, c - 7, 5, 7, "deepslate_bricks")
        room.put(c - 8, 5, 6, "air")
        room.secret(c - 11, 3, 6)
    extra_secrets(room)
    reinforce_encounters(room)


def arch(room, z):
    c = room.cx
    for side in (-1, 1):
        x = c + side * 10
        room.box(x - 1, 3, z, x + 1, 8, z, "polished_blackstone_bricks")
        room.box(x - 1, 9, z - 1, x + 1, 9, z + 1, "waxed_oxidized_cut_copper")
        for step in range(5):
            room.put(c + side * (9 - step), 10 + step // 2, z, "waxed_cut_copper")
        room.put(x, 7, z - 1, "ochre_froglight")
    room.box(c - 4, 12, z, c + 4, 12, z, "waxed_cut_copper")
    room.put(c, 11, z, "chain[axis=y,waterlogged=false]")
    room.put(c, 10, z, "lantern[hanging=true,waterlogged=false]")


def embellish(room):
    """Visible construction layers: monumental entry arches, floor inlays and working machinery."""
    c, d, kind = room.cx, room.cz, room.kind
    for z in (9, room.length - 10):
        arch(room, z)
    for z in range(4, room.length - 4):
        for x in (c - 2, c + 2):
            room.put(x, 2, z, "waxed_oxidized_cut_copper")
        if z % 6 == 0:
            room.put(c, 2, z, "chiseled_deepslate")
    # Staggered roof ribs have solid masonry above them, including every change of vault height.
    for z in range(15, room.length - 13, 8):
        for dx in range(-10, 11):
            y = min(room.height - 4, 11 + (10 - abs(dx)) // 3)
            room.put(c + dx, y, z, "waxed_oxidized_cut_copper")
    if kind == "bell":
        for dx, dz in [(-8, -8), (8, -8), (8, 8), (-8, 8)]:
            for x in range(-2, 3):
                for z in range(-2, 3):
                    room.put(c + dx + x, 2, d + dz + z,
                             "polished_diorite" if abs(x) == 2 or abs(z) == 2 else "waxed_oxidized_cut_copper")
            for y in range(7, 12):
                room.put(c + dx, y, d + dz, "chain[axis=y,waterlogged=false]")
        for dx in (-15, 15):
            room.box(c + dx - 1, 3, d - 2, c + dx + 1, 5, d + 2, "chiseled_deepslate")
            room.put(c + dx, 6, d, "amethyst_block")
            room.put(c + dx, 7, d, "end_rod[facing=up]")
    elif kind == "balance":
        for dx in (-8, 8):
            room.box(c + dx - 3, 20, d - 3, c + dx + 3, 21, d + 3, "waxed_cut_copper")
            for z in (d - 3, d + 3):
                room.box(c + dx - 2, 6, z, c + dx + 2, 6, z, "iron_bars")
        room.box(c - 4, 5, d + 8, c + 4, 5, d + 12, "polished_blackstone_bricks")
        for step in range(4):
            room.box(c - 2, 2, d + 4 + step, c + 2, 2 + step, d + 4 + step, "waxed_cut_copper")
        for dx in (-3, 3):
            room.put(c + dx, 6, d + 10, "anvil[facing=north]")
        room.put(c, 6, d + 11, "redstone_lamp[lit=true]")
    elif kind == "forge":
        for dx in (-16, 16):
            for dz in (-5, 5):
                room.box(c + dx - 3, 3, d + dz, c + dx + 3, 4, d + dz, "blast_furnace[facing=south,lit=true]")
                room.box(c + dx - 3, 5, d + dz, c + dx + 3, 5, d + dz, "waxed_cut_copper")
            for step in range(4):
                room.box(c + dx - 2, 2, d - 10 + step, c + dx + 2, 2 + step, d - 10 + step, "polished_blackstone_bricks")
            for dz in (-2, 2):
                room.put(c + dx - 5, 3, d + dz, "anvil[facing=east]")
                room.put(c + dx + 5, 3, d + dz, "smithing_table")
    elif kind == "archive":
        for z in range(15, room.length - 13, 8):
            for dx in (-4, 4):
                room.put(c + dx, 3, z, "lectern[facing=south,has_book=false,powered=false]")
                room.put(c + dx, 3, z + 2, "dark_oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]")
            room.put(c, 3, z, "flower_pot")
        for dx in (-11, -7, 7, 11):
            room.box(c + dx, 8, 12, c + dx, 8, room.length - 12, "dark_oak_slab[type=bottom,waterlogged=false]")
    elif kind == "turbine":
        for angle in range(0, 360, 45):
            x, z = round(c + 19 * math.cos(math.radians(angle))), round(d + 19 * math.sin(math.radians(angle)))
            room.box(x - 1, 3, z - 1, x + 1, 7, z + 1, "waxed_oxidized_cut_copper")
            room.put(x, 8, z, "redstone_lamp[lit=true]")
        for y in range(13, 20):
            for dx, dz in [(-3, -3), (3, -3), (-3, 3), (3, 3)]:
                room.put(c + dx, y, d + dz, "iron_bars")
    elif kind == "parkour":
        for z in range(16, room.length - 14, 9):
            for dx in (-14, 14):
                room.box(c + dx, 3, z, c + dx, 10, z, "waxed_oxidized_cut_copper")
                room.put(c + dx, 11, z, "sea_lantern")
                room.box(c + dx, 12, z, c + dx, 19, z, "chain[axis=y,waterlogged=false]")
        for z in range(14, room.length - 13):
            room.put(c - 11, 0, z, "ochre_froglight")
            room.put(c + 11, 0, z, "ochre_froglight")
    elif kind == "warden":
        for dx in (-17, 17):
            for dz in (-8, 8):
                room.box(c + dx - 1, 3, d + dz - 1, c + dx + 1, 6, d + dz + 1, "chiseled_deepslate")
                room.put(c + dx, 7, d + dz, "anvil[facing=north]")
                room.put(c + dx, 8, d + dz, "soul_lantern[hanging=false,waterlogged=false]")
        room.box(c - 4, 3, d - 6, c + 4, 3, d - 5, "waxed_cut_copper")
    elif kind == "start":
        for dx in (-10, 10):
            room.box(c + dx, 3, 13, c + dx, 5, 19, "chiseled_deepslate")
            room.put(c + dx, 6, 16, "soul_lantern[hanging=false,waterlogged=false]")
    elif kind == "portal":
        for dx in (-8, 8):
            room.box(c + dx - 1, 3, d - 3, c + dx + 1, 8, d - 1, "crying_obsidian")
            room.put(c + dx, 9, d - 2, "soul_lantern[hanging=false,waterlogged=false]")
        room.box(c - 5, 2, d + 2, c + 5, 2, d + 5, "polished_blackstone_bricks")


def extra_secrets(room):
    c, kind = room.cx, room.kind
    relics = {"start": "smithing_table", "bell": "amethyst_block", "balance": "anvil[facing=north]",
              "forge": "blast_furnace[facing=south,lit=true]", "archive": "chiseled_bookshelf[facing=south]",
              "turbine": "redstone_lamp[lit=true]", "parkour": "lodestone", "warden": "skeleton_skull[rotation=0]",
              "portal": "crying_obsidian"}
    # Each service vault is excavated from solid masonry, with a deliberate entrance and a sealed back.
    z = room.length - 12
    for side in (-1, 1):
        x = c + side * (c - 6)
        room.box(x - 3, 2, z - 3, x + 3, 6, z + 3, "polished_blackstone_bricks")
        room.box(x - 2, 3, z - 2, x + 2, 5, z + 2, "air")
        room.box(min(c + side * 5, x), 2, z - 1, max(c + side * 5, x), 2, z + 1, "waxed_cut_copper")
        room.box(min(c + side * 5, x), 3, z - 1, max(c + side * 5, x), 5, z + 1, "air")
        room.put(x, 6, z, "ochre_froglight")
        room.box(x - side * 2, 3, z - 1, x - side * 2, 5, z - 1, "chiseled_deepslate")
        room.put(x + side, 3, z + 1, relics[kind])
        room.secret(x, 3, z - 1)
    if kind in ("start", "portal"):
        return
    # A real staircase reaches the inspection gallery; rails and a roof keep it inside the dungeon.
    x = c + 13
    room.box(c + 4, 2, 7, x + 1, 2, 9, "waxed_cut_copper")
    room.box(c + 4, 3, 7, x + 1, 5, 9, "air")
    room.box(x - 1, 7, 12, x + 1, 7, room.length - 13,
             "dark_oak_planks" if kind == "archive" else "waxed_oxidized_cut_copper")
    room.box(x - 1, 8, 12, x + 1, 10, room.length - 13, "air")
    for step in range(6):
        room.box(x - 1, 2, 7 + step, x + 1, 2 + step, 7 + step, "waxed_cut_copper")
        room.box(x - 1, 3 + step, 7 + step, x + 1, 5 + step, 7 + step, "air")
    for z in range(14, room.length - 13):
        room.put(x - 2, 8, z, "iron_bars")
        room.put(x + 2, 8, z, "iron_bars")
        if z % 8 == 0:
            room.put(x, 11, z, "lantern[hanging=true,waterlogged=false]")
    room.secret(x, 8, room.length - 14, kind not in ("archive", "parkour"))


def reinforce_encounters(room):
    c, d = room.cx, room.cz
    if room.kind == "warden":
        positions = [(c, 3, d - 12, "yellow_concrete_powder")]
    else:
        z_front, z_back = (11, room.length - 9) if room.kind == "parkour" else (d - 5, d + 5)
        offset = 12 if room.kind == "turbine" else 5
        positions = [(c + dx, 3, z, "gray_concrete_powder") for dx in (-offset, offset) for z in (z_front, z_back)]
        positions += [(c + 13, 8, room.length - 21, "gray_concrete_powder")]
    for x, y, z, material in positions:
        room.put(x, y - 1, z, "polished_deepslate")
        room.box(x, y, z, x, y + 1, z, "air")
        room.marker(x, y, z, material)


def build_all(output=ROOT):
    output.mkdir(parents=True, exist_ok=True)
    manifest = {}
    for name, (width, height, length, kind) in ROOMS.items():
        room = Room(name, width, height, length, kind)
        boss(room) if kind == "boss" else chamber(room)
        room.seal()
        room.validate()
        data = gzip.compress(room.schematic(), mtime=0)
        (output / f"{name}.schem").write_bytes(data)
        manifest[name] = {"size": [width, height, length], "kind": kind,
                          "sha256": hashlib.sha256(data).hexdigest(), "blocks": len(room.blocks),
                          "secrets": len(room.secrets),
                          "mobs": sum(material in ("gray_concrete_powder", "yellow_concrete_powder")
                                      for _, _, _, material in room.markers)}
        print(f"{name}: {width}x{height}x{length}, {len(room.blocks)} authored blocks, {len(data)} compressed bytes")
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")


if __name__ == "__main__":
    build_all()
