#!/usr/bin/env python3
"""Generate a Sponge v2 WorldEdit schematic, with canonical boss-room markers."""
import gzip
import math
from pathlib import Path
import struct
import sys

SIZE, HEIGHT, CENTER = 113, 59, 56
STAGE_RADIUS = 42

GLYPHS = [
    ["0011100", "0011100", "1111111", "1111111", "0111110", "0001000", "0011100"],
    ["0001000", "0011100", "0111110", "1111111", "0111110", "0011100", "0001000"],
    ["0001000", "0011100", "0111110", "1111111", "1111111", "0001000", "0011100"],
    ["0110110", "1111111", "1111111", "0111110", "0011100", "0001000", "0001000"],
]


def build():
    blocks = {}

    def put(x, y, z, block):
        x, z = x + CENTER, z + CENTER
        assert 0 <= x < SIZE and 0 <= y < HEIGHT and 0 <= z < SIZE, (x, y, z)
        blocks[x, y, z] = "minecraft:" + block

    def box(x1, y1, z1, x2, y2, z2, block):
        for x in range(x1, x2 + 1):
            for y in range(y1, y2 + 1):
                for z in range(z1, z2 + 1):
                    put(x, y, z, block)

    for x in range(-CENTER, CENTER + 1):
        for z in range(-CENTER, CENTER + 1):
            r = math.hypot(x, z)
            a = math.atan2(z, x)
            q = int(((a + math.pi / 4) % math.tau) / (math.pi / 2))
            stripe = int((a % math.tau) / (math.pi / 8)) % 2
            if r <= 55:
                put(x, 0, z, "polished_blackstone")
                floor = ["blue_terracotta", "purple_concrete", "blue_concrete", "purple_terracotta"][q]
                if (x // 4 + z // 4) % 2 == 0 and r > 36:
                    floor = "black_concrete"
                if abs(abs(x) - abs(z)) <= 1 and 5 < r < 42:
                    floor = "smooth_quartz"
                if any(abs(r - radius) < .6 for radius in (8, 17, 36, 40)):
                    floor = "gold_block" if abs(r - 36) < .6 else "polished_blackstone_bricks"
                if 41 <= r <= 42:
                    floor = "sea_lantern"
                if r > 42:
                    floor = "polished_blackstone_bricks"
                put(x, 1, z, floor)
                # A soaring big-top dome, with a lit ribs-and-stars underside.
                roof = 33 + int(20 * max(0, 1 - r / 55) ** .7)
                put(x, roof, z, "purple_concrete" if stripe else "blue_concrete")
                spoke = abs(math.sin(a * 8)) < .035
                if spoke and r > 8:
                    put(x, roof - 1, z, "gold_block")
                if (x * 7 + z * 11) % 157 == 0 and r > 12:
                    put(x, roof - 1, z, "sea_lantern")
            if 42.3 <= r < 43.7:
                for y in range(2, 5):
                    put(x, y, z, "polished_blackstone_bricks")
                put(x, 5, z, "smooth_quartz")
                if (x + z) % 5 == 0:
                    put(x, 4, z, "sea_lantern")
            # Tiered spectators' boxes behind the arena wall.
            if 44 <= r < 51:
                bench_y = 4 + int((r - 44) / 2) * 3
                for y in range(2, bench_y + 1):
                    put(x, y, z, "polished_blackstone_bricks")
                put(x, bench_y + 1, z, "purple_wool" if stripe else "blue_wool")
                if int(r) % 2 == 0 and (x + z) % 3 == 0:
                    put(x, bench_y + 2, z, "gold_block")
            if 53.5 <= r <= 55:
                for y in range(2, 33):
                    put(x, y, z, "black_concrete" if y % 8 else "purple_concrete")
                put(x, 32, z, "gold_block")
                if (x + z) % 6 == 0:
                    for y in (8, 16, 24):
                        put(x, y, z, "pearlescent_froglight")
            # Slim lower spindle, suspended crystal and chandelier overhead.
            if r <= 3.5:
                for y in range(2, 5):
                    put(x, y, z, "chiseled_quartz_block" if r > 2.5 else "purple_concrete")
            if r <= 1.4:
                for y in range(5, 27):
                    put(x, y, z, "gold_block" if y % 4 == 0 else "blue_concrete")
            if r <= max(0, 6 - abs(21 - 19)) and abs(x) + abs(z) <= 6:
                for y in range(18, 25):
                    if abs(x) + abs(z) + abs(y - 21) <= 6:
                        put(x, y, z, "amethyst_block" if (x + y + z) % 3 else "sea_lantern")
            if 8.5 <= r <= 9.5:
                put(x, 26, z, "gold_block")
                if (x + z) % 3 == 0:
                    put(x, 25, z, "end_rod[facing=down]")
            if r < 9 and (abs(x) <= .5 or abs(z) <= .5):
                put(x, 27, z, "gold_block")

    # Huge suit mosaics in the floor, with a double gold frame.
    for q, (cx, cz) in enumerate([(27, 0), (0, 27), (-27, 0), (0, -27)]):
        for row, line in enumerate(GLYPHS[q]):
            for col, pixel in enumerate(line):
                if pixel == "1":
                    box(cx + col * 2 - 6, 1, cz + row * 2 - 6,
                        cx + col * 2 - 5, 1, cz + row * 2 - 5, "smooth_quartz")
        for dx in range(-9, 10):
            for dz in range(-9, 10):
                if max(abs(dx), abs(dz)) == 9:
                    put(cx + dx, 1, cz + dz, "gold_block")

    # Cover monuments sit outside the boss's four ground positions.
    for cx in (-24, 24):
        for cz in (-24, 24):
            box(cx - 2, 2, cz - 2, cx + 2, 2, cz + 2, "smooth_quartz")
            box(cx - 1, 3, cz - 1, cx + 1, 5, cz + 1, "chiseled_quartz_block")
            put(cx, 6, cz, "pearlescent_froglight")
            put(cx, 7, cz, "gold_block")

    # Four giant playing cards suspended above the seating.
    for q in range(4):
        def panel(u, y, material):
            x, z = [(48, u), (-u, 48), (-48, -u), (u, -48)][q]
            put(x, y, z, material)
        for u in range(-9, 10):
            for y in range(11, 31):
                panel(u, y, "gold_block" if abs(u) == 9 or y in (11, 30) else "smooth_quartz")
        for row, line in enumerate(GLYPHS[q]):
            for col, pixel in enumerate(line):
                if pixel == "1":
                    for du in (0, 1):
                        for dy in (0, 1):
                            panel(col * 2 - 6 + du, 27 - row * 2 - dy,
                                  "red_concrete" if q in (1, 3) else "black_concrete")

    # Monumental jester heads and curled hats in each spectator corner.
    for cx, cz in [(-35, -35), (35, -35), (-35, 35), (35, 35)]:
        box(cx - 4, 8, cz - 4, cx + 4, 9, cz + 4, "gold_block")
        box(cx - 3, 10, cz - 3, cx + 3, 16, cz + 3, "purple_concrete")
        box(cx - 3, 17, cz - 3, cx + 3, 22, cz + 3, "smooth_quartz")
        face_z = cz + (4 if cz < 0 else -4)
        for dx in (-2, 2):
            box(cx + dx, 20, face_z, cx + dx, 21, face_z, "black_concrete")
            put(cx + dx, 20, face_z, "yellow_concrete")
        put(cx, 19, face_z, "redstone_block")
        box(cx - 2, 18, face_z, cx + 2, 18, face_z, "black_concrete")
        box(cx - 4, 23, cz - 3, cx + 4, 24, cz + 3, "blue_concrete")
        for side in (-1, 1):
            for i in range(1, 7):
                dx, yy = side * (3 + i // 2), 25 + (i if i < 4 else 6 - i)
                box(cx + dx, yy, cz - 1, cx + dx + 1, yy + 1, cz + 1,
                    "purple_concrete" if side < 0 else "blue_concrete")
            put(cx + side * 7, 25, cz, "gold_block")
            put(cx + side * 7, 26, cz, "sea_lantern")

    # Four proscenium arches frame the arena from inside the tall walls.
    for q in range(4):
        for u in range(-8, 9):
            for y in range(7, 30):
                if abs(u) >= 6 or y >= 27 - abs(u) // 3:
                    x, z = [(u, -52), (52, u), (-u, 52), (-52, -u)][q]
                    put(x, y, z, "gold_block" if abs(u) in (6, 8) else "purple_concrete")

    for y in range(54, 58):
        box(-1, y, -1, 1, y, 1, "gold_block")
    put(0, 58, 0, "sea_lantern")

    # Only these blocks are canonical authoring markers.
    put(20, 2, 0, "red_concrete_powder")
    put(0, 2, -38, "lime_concrete_powder")
    for x in (-6, -3, 0, 3, 6):
        put(x, 2, 38, "emerald_block")
    assert sum(b.endswith(":red_concrete_powder") for b in blocks.values()) == 1
    assert sum(b.endswith(":lime_concrete_powder") for b in blocks.values()) == 1
    assert sum(b.endswith(":emerald_block") for b in blocks.values()) == 5
    for x, z in [(20, 0), (0, 20), (-20, 0), (0, -20), (12, -12), (-12, -12)]:
        assert (CENTER + x, 3, CENTER + z) not in blocks
        assert (CENTER + x, 2, CENTER + z) not in blocks or (x, z) == (20, 0)
    # Verify an uninterrupted lap at radius 40 and supported spawn/loot locations.
    for i in range(720):
        x, z = round(40 * math.cos(i * math.pi / 360)), round(40 * math.sin(i * math.pi / 360))
        assert (CENTER + x, 2, CENTER + z) not in blocks
    for x, z in [(20, 0), (0, -38)] + [(x, 38) for x in (-6, -3, 0, 3, 6)]:
        assert (CENTER + x, 1, CENTER + z) in blocks
    return blocks


def string(value):
    encoded = value.encode()
    return struct.pack(">H", len(encoded)) + encoded


def tag(kind, name, payload):
    return bytes([kind]) + string(name) + payload


def integer(name, value):
    return tag(3, name, struct.pack(">i", value))


def schematic(blocks, origin=(0, 0, 0)):
    palette = {"minecraft:air": 0}
    data = bytearray()
    for y in range(HEIGHT):
        for z in range(SIZE):
            for x in range(SIZE):
                block = blocks.get((x, y, z), "minecraft:air")
                index = palette.setdefault(block, len(palette))
                while index >= 128:
                    data.append((index & 127) | 128)
                    index >>= 7
                data.append(index)
    assert len(palette) < 128 and len(data) == SIZE * SIZE * HEIGHT
    payload = integer("Version", 2) + integer("DataVersion", 3955)
    for name, value in [("Width", SIZE), ("Height", HEIGHT), ("Length", SIZE)]:
        payload += tag(2, name, struct.pack(">h", value))
    payload += tag(11, "Offset", struct.pack(">iiii", 3, *origin))
    payload += tag(10, "Metadata", b"".join(integer("WEOffset" + axis, 0) for axis in "XYZ") + b"\0")
    payload += integer("PaletteMax", len(palette))
    payload += tag(10, "Palette", b"".join(integer(name, value) for name, value in palette.items()) + b"\0")
    payload += tag(7, "BlockData", struct.pack(">i", len(data)) + data)
    payload += tag(9, "BlockEntities", b"\x0a" + struct.pack(">i", 0))
    payload += tag(9, "Entities", b"\x0a" + struct.pack(">i", 0))
    return tag(10, "Schematic", payload + b"\0")


if __name__ == "__main__":
    output = Path(sys.argv[1] if len(sys.argv) > 1 else "/tmp/ringmaster-arena.schem")
    origin = tuple(map(int, sys.argv[2].split(","))) if len(sys.argv) > 2 else (0, 0, 0)
    assert len(origin) == 3
    blocks = build()
    with gzip.open(output, "wb") as stream:
        stream.write(schematic(blocks, origin))
    print(f"Saved {output}: {len(blocks)} blocks, {SIZE}x{HEIGHT}x{SIZE}, floor y=1, markers y=2")
