#!/usr/bin/env python3
"""Independent asset checks for the built rooms; no Minecraft server needed."""
import gzip
import math
import struct
import unittest
from collections import deque
from build_foundry_floor import ROOMS, ROOT, Room, boss, chamber, lower_platform


class NbtReader:
    def __init__(self, data):
        self.data, self.offset = data, 0

    def read(self, count):
        result = self.data[self.offset:self.offset + count]
        self.offset += count
        assert len(result) == count
        return result

    def number(self, fmt):
        return struct.unpack(fmt, self.read(struct.calcsize(fmt)))[0]

    def string(self):
        return self.read(self.number(">H")).decode()

    def value(self, kind):
        if kind == 1:
            return self.number(">b")
        if kind == 2:
            return self.number(">h")
        if kind == 3:
            return self.number(">i")
        if kind == 7:
            return self.read(self.number(">i"))
        if kind == 8:
            return self.string()
        if kind == 9:
            subtype, count = self.number(">B"), self.number(">i")
            return [self.value(subtype) for _ in range(count)]
        if kind == 10:
            result = {}
            while (subtype := self.number(">B")) != 0:
                name = self.string()
                result[name] = self.value(subtype)
            return result
        if kind == 11:
            return [self.number(">i") for _ in range(self.number(">i"))]
        raise AssertionError(f"unsupported tag {kind}")


class FloorAssetsTest(unittest.TestCase):
    def test_all_schematics_round_trip_and_markers_have_support(self):
        for name, dimensions in ROOMS.items():
            with self.subTest(name=name):
                width, height, length, kind = dimensions
                room = Room(name, width, height, length, kind)
                boss(room) if kind == "boss" else chamber(room)
                room.seal()
                room.validate()
                reader = NbtReader(gzip.decompress((ROOT / f"{name}.schem").read_bytes()))
                self.assertEqual(10, reader.number(">B"))
                self.assertEqual("", reader.string())
                root = reader.value(10)
                self.assertEqual({"Schematic"}, set(root))
                nbt = root["Schematic"]
                self.assertEqual(3, nbt["Version"])
                self.assertEqual((width, height, length), (nbt["Width"], nbt["Height"], nbt["Length"]))
                blocks = nbt["Blocks"]
                self.assertFalse(any("sign" in block.split("[")[0] for block in blocks["Palette"]), name)
                self.assertTrue(all(entity["Id"] == "minecraft:jigsaw" for entity in blocks["BlockEntities"]), name)
                palette = {value: key for key, value in blocks["Palette"].items()}
                data, position = blocks["Data"], 0
                for y in range(height):
                    for z in range(length):
                        for x in range(width):
                            value, shift = 0, 0
                            while True:
                                byte = data[position]; position += 1
                                value |= (byte & 127) << shift
                                if not byte & 128:
                                    break
                                shift += 7
                            self.assertEqual(room.blocks.get((x, y, z), "minecraft:air"), palette[value])
                self.assertEqual(position, len(data))
                for entity in blocks["BlockEntities"]:
                    self.assertEqual(3, len(entity["Pos"]))
                jigsaws = [entity for entity in blocks["BlockEntities"] if entity["Id"] == "minecraft:jigsaw"]
                self.assertEqual(len(room.connectors), len(jigsaws))
                for connector in jigsaws:
                    self.assertIn(connector["Data"]["name"], ("dungeoncrawlers:entrance", "dungeoncrawlers:exit"))
                    self.assertEqual("dungeoncrawlers:connector", connector["Data"]["target"])

    def test_crucible_islands_and_ring_remain_connected_after_spoke_retraction(self):
        cells = {(x, z) for x in range(-32, 33) for z in range(-32, 33)
                 if math.hypot(x, z) <= 32 and lower_platform(x, z)}
        for axis in range(4):
            removed = {(r * (1 if axis == 0 else -1), w) if axis % 2 == 0
                       else (w, r * (1 if axis == 1 else -1)) for r in range(10, 28) for w in range(-2, 3)}
            walkable = cells - removed
            queue, visited = deque([(0, 0)]), {(0, 0)}
            while queue:
                x, z = queue.popleft()
                for dx, dz in [(1, 0), (-1, 0), (0, 1), (0, -1)]:
                    point = x + dx, z + dz
                    if point in walkable and point not in visited:
                        visited.add(point); queue.append(point)
            for refuge in [(17, 17), (-17, 17), (17, -17), (-17, -17), (0, 30), (30, 0)]:
                self.assertIn(refuge, visited, (axis, refuge))

    def test_normal_rooms_have_supported_routes_and_reachable_secrets(self):
        for name, dimensions in ROOMS.items():
            width, height, length, kind = dimensions
            if kind == "boss":
                continue
            room = Room(name, width, height, length, kind); chamber(room); room.seal()
            markers = {p[:3] for p in room.markers} | set(room.connectors)

            def clear(x, y, z):
                return (x, y, z) in markers or room.blocks.get((x, y, z), "minecraft:air") == "minecraft:air"

            def standable(x, y, z):
                return clear(x, y, z) and clear(x, y + 1, z) and not clear(x, y - 1, z)

            start = room.cx, 3, 1
            queue, visited = deque([start]), {start}
            while queue:
                x, y, z = queue.popleft()
                for dx, dz in [(1, 0), (-1, 0), (0, 1), (0, -1)]:
                    for dy in (0, 1, -1):
                        point = x + dx, y + dy, z + dz
                        if point in visited or not (0 <= point[0] < width and 0 <= point[2] < length):
                            continue
                        if standable(*point):
                            visited.add(point); queue.append(point)
            if kind not in ("start", "portal"):
                self.assertIn((room.cx, 3, length - 2), visited, name)
            for x, y, z in room.secrets:
                self.assertTrue(any((x + dx, y, z + dz) in visited for dx, dz in [(1, 0), (-1, 0), (0, 1), (0, -1)]),
                                (name, "unreachable secret", x, y, z))
            for x, y, z, material in room.markers:
                if material in ("gray_concrete_powder", "yellow_concrete_powder"):
                    self.assertTrue((x, y, z) in visited, (name, "unreachable mob", x, y, z))

    def test_every_exterior_face_is_solid_except_authored_connectors(self):
        for name, (width, height, length, kind) in ROOMS.items():
            room = Room(name, width, height, length, kind)
            boss(room) if kind == "boss" else chamber(room)
            room.seal()
            portals = {(cx + dx, cy + dy, cz) for cx, cy, cz in room.connectors
                       for dx in (-1, 0, 1) for dy in (-1, 0, 1)}
            boundary = {(x, y, z) for x in range(width) for y in range(height) for z in range(length)
                        if x in (0, width - 1) or y in (0, height - 1) or z in (0, length - 1)}
            holes = {p for p in boundary if room.blocks.get(p, "minecraft:air") == "minecraft:air"}
            self.assertFalse(holes - portals, (name, "unintended exterior holes", sorted(holes - portals)[:10]))
            for cx, cy, cz in room.connectors:
                for dx in (-1, 0, 1):
                    for dy in (-1, 0, 1):
                        if dx or dy:
                            self.assertEqual("minecraft:air", room.blocks[cx + dx, cy + dy, cz])
            for x, y, z, material in room.markers:
                self.assertEqual("minecraft:" + material, room.blocks[x, y, z], (name, "overwritten marker"))
            for x, y, z in room.secrets:
                self.assertTrue(room.blocks[x, y, z].startswith(("minecraft:chest[", "minecraft:trapped_chest[")),
                                (name, "overwritten secret", x, y, z))

    def test_more_secrets_and_enemies_have_bounded_supported_spawns(self):
        for name, (width, height, length, kind) in ROOMS.items():
            if kind == "boss":
                continue
            room = Room(name, width, height, length, kind); chamber(room); room.seal(); room.validate()
            self.assertEqual(2 if kind in ("start", "portal") else 4, len(room.secrets), name)
            mobs = [p for p in room.markers if p[3] in ("gray_concrete_powder", "yellow_concrete_powder")]
            self.assertEqual(2 if kind == "warden" else 0 if kind in ("start", "portal") else
                             9 if kind in ("forge", "turbine") else 8 if kind == "archive" else 7, len(mobs), name)
            self.assertEqual(len(mobs), len({p[:3] for p in mobs}), name)
            for x, y, z, _ in mobs:
                self.assertTrue(1 < x < width - 2 and 1 < z < length - 2, (name, "spawn at outer wall"))


if __name__ == "__main__":
    unittest.main()
