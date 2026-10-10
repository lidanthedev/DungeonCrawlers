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

    def sign(self, x, y, z, lines):
        self.put(x, y, z, "spruce_sign[rotation=8,waterlogged=false]")
        messages = [json.dumps({"text": line, "color": "aqua"}, separators=(",", ":")) for line in lines]
        messages += ['{"text":""}'] * (4 - len(messages))
        payload = tag(9, "messages", bytes([8]) + struct.pack(">i", 4) + b"".join(string(v) for v in messages)) \
            + text("color", "black") + tag(1, "has_glowing_text", b"\x01")
        self.entities[x, y, z] = text("Id", "minecraft:sign") + compound("Data", compound("front_text", payload) + compound("back_text", payload))

    def secret(self, x, y, z, blessing=False):
        self.put(x, y, z, "chest[facing=south,type=single,waterlogged=false]" if blessing
                 else "trapped_chest[facing=south,type=single,waterlogged=false]")
        self.secrets.append((x, y, z))

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
    room.sign(c + 3, 12, c + 28, ["THE CHAINBOUND", "ARCHITECT", "Gold warns. Cyan saves.", "The deck is a prison."])


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
                continue
            room.put(x, 0, z, "deepslate_bricks")
            room.put(x, 2, z, "deepslate_tiles" if (x // 4 + z // 4) % 2 else "polished_deepslate")
            if (abs(dx) == 3 or z % 9 == 0) and abs(dx) <= 3:
                room.put(x, 2, z, "oxidized_cut_copper")
            roof = min(room.height - 2, 13 + int(8 * max(0, 1 - r / 25)))
            room.put(x, roof, z, "deepslate_bricks")
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
    if kind == "start":
        for x in (-6, -3, 0, 3, 6):
            room.marker(c + x, 3, 6, "emerald_block")
        room.marker(c - 7, 3, 10, "orange_concrete_powder")
        room.sign(c + 5, 3, 11, ["SUNDERED FOUNDRY", "The bells remember.", "Balance holds the heart.", "Class up. Break free."])
        room.connector("exit", room.length - 1)
        return
    room.connector("entrance", 0)
    if kind == "portal":
        room.box(c - 3, 3, d - 1, c + 3, 9, d - 1, "obsidian")
        room.box(c - 2, 4, d - 1, c + 2, 8, d - 1, "nether_portal[axis=x]")
        room.sign(c + 5, 3, d + 3, ["THE HEART GATE", "Veyra waits beyond.", "Stay together.", "Watch the chains."])
        return
    room.connector("exit", room.length - 1)
    if kind == "bell":
        names = ["I: EMBER", "II: TIDE", "III: STORM", "IV: VOID"]
        for i, (dx, dz) in enumerate([(-8, -8), (8, -8), (8, 8), (-8, 8)]):
            room.put(c + dx, 3, d + dz, "chiseled_copper")
            room.put(c + dx, 5, d + dz, "bell[attachment=ceiling,facing=south,powered=false]")
            room.put(c + dx, 6, d + dz, "oxidized_cut_copper")
            room.sign(c + dx, 3, d + dz + 2, [names[i], "Right-click the rune", "Follow the sung order.", "Mistakes restart it."])
        room.sign(c + 5, 3, 7, ["RESONANCE VAULT", "Read the floating clue.", "One voice opens the seal.", "Each bell sings once."])
    elif kind == "balance":
        for dx in (-8, 8):
            room.box(c + dx - 2, 2, d - 2, c + dx + 2, 2, d + 2, "cut_copper")
            room.put(c + dx, 3, d, "heavy_weighted_pressure_plate[power=0]")
            for y in range(8, 20):
                room.put(c + dx, y, d, "chain[axis=y,waterlogged=false]")
        room.put(c, 3, d - 5, "lever[face=floor,facing=north,powered=false]")
        room.sign(c + 5, 3, d - 7, ["COUNTERWEIGHT ENGINE", "Two plates. Two souls.", "Hold balance for 3s.", "Solo: use the latch."])
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
        room.sign(c + 4, 3, 7, ["ASHEN ARCHIVES", "She chained the sky.", "We hid the final verse.", "Search the mezzanine."])
    elif kind == "turbine":
        for angle in range(0, 360, 45):
            for r in range(10, 22):
                x, z = round(c + r * math.cos(math.radians(angle))), round(d + r * math.sin(math.radians(angle)))
                room.put(x, 4 + r // 6, z, "cut_copper")
        room.box(c - 2, 11, d - 2, c + 2, 12, d + 2, "sea_lantern")
        for y in range(13, 30):
            room.put(c, y, d, "chain[axis=y,waterlogged=false]")
        room.sign(c + 5, 3, 7, ["STORM TURBINE", "Its blades still turn.", "Keep off the yellow line.", "Follow the outer ring."])
    elif kind == "parkour":
        # Three-block running jumps cross the maintenance trench; a fall returns to the last landing.
        room.box(c - 12, 2, 13, c + 12, 2, room.length - 13, "air")
        for z in range(12, room.length - 11, 5):
            room.box(c - 2, 2, z, c + 2, 2, z + 1, "oxidized_cut_copper")
        room.box(c - 13, 7, 22, c - 10, 7, room.length - 16, "polished_blackstone_bricks")
        for z in range(12, 23):
            room.box(c - 13, 2, z, c - 11, min(7, 2 + (z - 12) // 2), z, "polished_blackstone_bricks")
        room.secret(c - 12, 8, room.length - 17, True)
        room.sign(c + 5, 3, 8, ["BROKEN SKYWAY", "Sprint the copper steps.", "Falls reset your footing.", "Upper route: hidden loot."])
    elif kind == "warden":
        room.box(c - 5, 3, d - 4, c + 5, 3, d + 4, "oxidized_cut_copper")
        for dx in (-10, 10):
            room.box(c + dx - 2, 3, d - 2, c + dx + 2, 14, d + 2, "polished_blackstone_bricks")
            room.put(c + dx, 15, d, "shroomlight")
        room.marker(c, 4, d, "yellow_concrete_powder")
        room.sign(c + 5, 3, 8, ["THE LAST WARDEN", "Break the chainkeeper.", "The heart gate awakens.", "No bell will save you."])
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


def build_all(output=ROOT):
    output.mkdir(parents=True, exist_ok=True)
    manifest = {}
    for name, (width, height, length, kind) in ROOMS.items():
        room = Room(name, width, height, length, kind)
        boss(room) if kind == "boss" else chamber(room)
        room.validate()
        data = gzip.compress(room.schematic(), mtime=0)
        (output / f"{name}.schem").write_bytes(data)
        manifest[name] = {"size": [width, height, length], "kind": kind,
                          "sha256": hashlib.sha256(data).hexdigest(), "blocks": len(room.blocks)}
        print(f"{name}: {width}x{height}x{length}, {len(room.blocks)} authored blocks, {len(data)} compressed bytes")
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")


if __name__ == "__main__":
    build_all()
