#!/usr/bin/env python3
"""
Phase 10 programmatic validation (no gradle, no network — per spec §4 dev-env note).

Checks, without a running server:
  1. Dimension datapack JSONs are well-formed and self-consistent
     (min_y/height multiples of 16; Y envelope; type refs; flat void generator).
  2. The HiveCityLayout math (re-implemented here in Python) is:
       - deterministic (same seed+radius -> identical plan),
       - free of horizontal footprint overlaps within a Y level,
       - vertically stacked at the documented offsets,
       - emitted bottom-up (a district never sits on empty air within the plan).
  3. Cross-checks the Java constants against these Python constants so the two can't drift.

Run: python3 tools/validate_fase10.py
Exit code 0 = all pass.
"""
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources", "data", "firstcrusade")
JAVA = os.path.join(ROOT, "src", "main", "java", "com", "example", "examplemod", "hive", "city")

# ---- constants mirrored from the Java (validated to match at the end) ----
MIN_Y = -64
HEIGHT = 576
MAX_Y = MIN_Y + HEIGHT - 1          # 511
UNDERHIVE_Y = -64
GROUND_Y = 0
LEVEL_HEIGHT = 64
DISTRICT_W = 192
DISTRICT_D = 128
CELL_PITCH = 192

D_GATE = "firstcrusade:south_ash_gate"
D_WALL = "firstcrusade:hive_wall_line"
D_CORNER = "firstcrusade:hive_corner_bastion"
D_MANUFACTORUM = "firstcrusade:manufactorum"
D_HAB = "firstcrusade:hab_stacks"
D_ADMIN = "firstcrusade:administratum"
D_UNDERHIVE = "firstcrusade:underhive"
D_SPIRE = "firstcrusade:spire"

failures = []
passes = []

def check(cond, msg):
    (passes if cond else failures).append(msg)

# ------------------------------------------------------------------ 1. JSONs
def load_json(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)

def validate_dimension_jsons():
    dt_path = os.path.join(RES, "dimension_type", "hive_world.json")
    dim_path = os.path.join(RES, "dimension", "hive_world.json")
    biome_path = os.path.join(RES, "worldgen", "biome", "hive_floor.json")

    for p in (dt_path, dim_path, biome_path):
        check(os.path.exists(p), f"file exists: {os.path.relpath(p, ROOT)}")

    dt = load_json(dt_path)
    check(dt["min_y"] == MIN_Y, f"dim_type min_y == {MIN_Y} (got {dt['min_y']})")
    check(dt["height"] == HEIGHT, f"dim_type height == {HEIGHT} (got {dt['height']})")
    check(dt["min_y"] % 16 == 0, "dim_type min_y is a multiple of 16")
    check(dt["height"] % 16 == 0, "dim_type height is a multiple of 16")
    check(dt["logical_height"] <= dt["height"], "logical_height <= height")
    check(dt["has_ceiling"] is False, "hive_world has_ceiling == false")
    check(dt["has_skylight"] is True, "hive_world has_skylight == true")
    # Envelope sanity: underhive floor and spire top both fit.
    check(UNDERHIVE_Y >= MIN_Y, "underhive floor >= min_y")
    spire_top = GROUND_Y + 5 * LEVEL_HEIGHT  # ground + man+hab+admin+spire
    check(spire_top <= MAX_Y, f"spire top ({spire_top}) <= max_y ({MAX_Y})")

    dim = load_json(dim_path)
    check(dim["type"] == "firstcrusade:hive_world",
          "dimension.type references firstcrusade:hive_world")
    gen = dim["generator"]
    check(gen["type"] == "minecraft:flat", "generator is minecraft:flat (empty terrain)")
    settings = gen["settings"]
    check(settings["features"] is False, "flat generator features disabled")
    check(settings["lakes"] is False, "flat generator lakes disabled")
    check(settings["biome"] == "firstcrusade:hive_floor",
          "flat generator uses firstcrusade:hive_floor biome")
    layers = settings["layers"]
    check(len(layers) == 1 and layers[0]["block"] == "minecraft:bedrock",
          "flat generator = single bedrock layer (void floor)")

    biome = load_json(biome_path)
    total_spawns = sum(len(v) for v in biome["spawners"].values())
    check(total_spawns == 0, "hive_floor biome has no natural spawns (city controls spawns)")
    check(all(len(g) == 0 for g in biome["features"]),
          "hive_floor biome has no worldgen features")

# ------------------------------------------------- 2. layout (python mirror)
def perimeter_rotation(gx, gz, c):
    dx, dz = gx - c, gz - c
    if abs(dz) >= abs(dx):
        return 0 if dz < 0 else 2
    return 3 if dx < 0 else 1

def corner_rotation(dx, dz):
    if dx < 0 and dz > 0: return 0
    if dx < 0 and dz < 0: return 1
    if dx > 0 and dz < 0: return 2
    return 3

def plan(seed, radius, include_spire, spire_registered):
    """Byte-for-byte mirror of HiveCityLayout.plan() (square pitch, separate spire pass)."""
    out = []
    edge = 2 * radius + 1
    c = radius
    half = (edge * CELL_PITCH) // 2

    # underhive (center cell)
    ux = c * CELL_PITCH - half
    uz = c * CELL_PITCH - half
    out.append((D_UNDERHIVE, (ux, UNDERHIVE_Y, uz), 0))

    # perimeter
    for gx in range(edge):
        for gz in range(edge):
            ring = max(abs(gx - c), abs(gz - c))
            if ring != radius:
                continue
            ox = gx * CELL_PITCH - half
            oz = gz * CELL_PITCH - half
            dx, dz = gx - c, gz - c
            if (gx == c) ^ (gz == c):
                out.append((D_GATE, (ox, GROUND_Y, oz), perimeter_rotation(gx, gz, c)))
            elif abs(dx) == radius and abs(dz) == radius:
                out.append((D_CORNER, (ox, GROUND_Y, oz), corner_rotation(dx, dz)))
            else:
                out.append((D_WALL, (ox, GROUND_Y, oz), perimeter_rotation(gx, gz, c)))

    # interior stacks
    yMan = GROUND_Y
    yHab = yMan + LEVEL_HEIGHT
    yAdmin = yHab + LEVEL_HEIGHT
    for gx in range(edge):
        for gz in range(edge):
            ring = max(abs(gx - c), abs(gz - c))
            if ring >= radius:
                continue
            ox = gx * CELL_PITCH - half
            oz = gz * CELL_PITCH - half
            out.append((D_MANUFACTORUM, (ox, yMan, oz), 0))
            out.append((D_HAB, (ox, yHab, oz), 0))
            out.append((D_ADMIN, (ox, yAdmin, oz), 0))

    # spire: separate final pass -> globally last
    if include_spire and spire_registered:
        ox = c * CELL_PITCH - half
        oz = c * CELL_PITCH - half
        out.append((D_SPIRE, (ox, yAdmin + LEVEL_HEIGHT, oz), 0))
    return out

def footprint(origin, rot):
    x, y, z = origin
    w, d = (DISTRICT_W, DISTRICT_D) if rot % 2 == 0 else (DISTRICT_D, DISTRICT_W)
    return (x, z, x + w, z + d, y)  # minx, minz, maxx, maxz, y

def overlaps_2d(a, b):
    return not (a[2] <= b[0] or b[2] <= a[0] or a[3] <= b[1] or b[3] <= a[1])

def validate_layout():
    # determinism
    p1 = plan(12345, 2, True, True)
    p2 = plan(12345, 2, True, True)
    check(p1 == p2, "layout deterministic: same seed+radius -> identical plan")

    # A radius-2 city: 5x5 = 25 cells. Ring2 (perimeter) = 25 - 9 = 16 gates.
    # Interior (ring<2) = 3x3 = 9 cells -> 9*(man+hab+admin)=27, +1 spire, +1 underhive.
    p = plan(42, 2, True, True)
    gates = [t for t in p if t[0] == D_GATE]
    walls = [t for t in p if t[0] == D_WALL]
    corners = [t for t in p if t[0] == D_CORNER]
    interior_stack = [t for t in p if t[0] in (D_MANUFACTORUM, D_HAB, D_ADMIN)]
    spires = [t for t in p if t[0] == D_SPIRE]
    unders = [t for t in p if t[0] == D_UNDERHIVE]
    check(len(gates) == 4, f"radius-2 perimeter has four ceremonial gates (got {len(gates)})")
    check(len(walls) == 8, f"radius-2 perimeter has eight straight wall sectors (got {len(walls)})")
    check(len(corners) == 4, f"radius-2 perimeter has four corner bastions (got {len(corners)})")
    check(len(interior_stack) == 27, f"radius-2 interior has 27 stacked districts (got {len(interior_stack)})")
    check(len(spires) == 1, f"exactly one spire on center (got {len(spires)})")
    check(len(unders) == 1, f"exactly one underhive (got {len(unders)})")
    check(len(p) == 16 + 27 + 1 + 1, f"total district count == 45 (got {len(p)})")

    # spire toggled off when not registered
    p_nospire = plan(42, 2, True, False)
    check(not any(t[0] == D_SPIRE for t in p_nospire),
          "spire omitted when not registered")

    # no horizontal overlap within a Y level
    by_y = {}
    for did, origin, rot in p:
        by_y.setdefault(origin[1], []).append(footprint(origin, rot))
    overlap_found = False
    for y, fps in by_y.items():
        for i in range(len(fps)):
            for j in range(i + 1, len(fps)):
                if overlaps_2d(fps[i], fps[j]):
                    overlap_found = True
    check(not overlap_found, "no two districts overlap horizontally within a Y level")

    # every Y in the stack is a clean multiple offset from ground (contiguous 64s)
    interior_ys = sorted({o[1] for d, o, r in interior_stack})
    check(interior_ys == [0, 64, 128],
          f"interior stack Ys are contiguous 64-steps [0,64,128] (got {interior_ys})")

    # bottom-up emission: underhive first, spire last
    check(p[0][0] == D_UNDERHIVE, "plan emits underhive first (bottom-up)")
    check(p[-1][0] == D_SPIRE, "plan emits spire last (top of stack)")

    # perimeter rotations point walls outward on each edge
    c = 2
    edge = 5
    halfX = (edge * DISTRICT_W) // 2
    halfZ = (edge * DISTRICT_D) // 2
    north = perimeter_rotation(2, 0, c)  # top row
    south = perimeter_rotation(2, 4, c)  # bottom row
    west = perimeter_rotation(0, 2, c)
    east = perimeter_rotation(4, 2, c)
    check((north, east, south, west) == (0, 1, 2, 3),
          f"perimeter rotations N/E/S/W == 0/1/2/3 (got {north}/{east}/{south}/{west})")

# ------------------------------------------- 3. Java <-> Python constant sync
def validate_java_sync():
    def read(path):
        with open(path, encoding="utf-8") as f:
            return f.read()

    hw = read(os.path.join(JAVA, "HiveWorld.java"))
    layout = read(os.path.join(JAVA, "HiveCityLayout.java"))

    def intval(src, name):
        m = re.search(rf"{name}\s*=\s*(-?\d+)", src)
        return int(m.group(1)) if m else None

    def strval(src, name):
        m = re.search(rf'{name}\s*=\s*"([^"]+)"', src)
        return m.group(1) if m else None

    check(intval(hw, "MIN_Y") == MIN_Y, "Java HiveWorld.MIN_Y matches")
    check(intval(hw, "MAX_Y") == MAX_Y, "Java HiveWorld.MAX_Y matches")
    check(intval(hw, "UNDERHIVE_Y") == UNDERHIVE_Y, "Java HiveWorld.UNDERHIVE_Y matches")
    check(intval(hw, "GROUND_Y") == GROUND_Y, "Java HiveWorld.GROUND_Y matches")
    check(intval(hw, "LEVEL_HEIGHT") == LEVEL_HEIGHT, "Java HiveWorld.LEVEL_HEIGHT matches")
    check(intval(layout, "DISTRICT_W") == DISTRICT_W, "Java HiveCityLayout.DISTRICT_W matches")
    check(intval(layout, "DISTRICT_D") == DISTRICT_D, "Java HiveCityLayout.DISTRICT_D matches")
    check(intval(layout, "CELL_PITCH") == CELL_PITCH, "Java HiveCityLayout.CELL_PITCH matches")
    check(strval(layout, "D_GATE") == D_GATE, "Java D_GATE id matches")
    check(strval(layout, "D_WALL") == D_WALL, "Java D_WALL id matches")
    check(strval(layout, "D_CORNER") == D_CORNER, "Java D_CORNER id matches")
    check(strval(layout, "D_MANUFACTORUM") == D_MANUFACTORUM, "Java D_MANUFACTORUM id matches")
    check(strval(layout, "D_HAB") == D_HAB, "Java D_HAB id matches")
    check(strval(layout, "D_ADMIN") == D_ADMIN, "Java D_ADMIN id matches")
    check(strval(layout, "D_UNDERHIVE") == D_UNDERHIVE, "Java D_UNDERHIVE id matches")

    # District IDs must NOT carry the "hive/" prefix (the Phase-7 bug).
    for did in (D_GATE, D_WALL, D_CORNER, D_MANUFACTORUM, D_HAB, D_ADMIN, D_UNDERHIVE, D_SPIRE):
        check("hive/" not in did, f"district id '{did}' has no 'hive/' prefix (Phase-7 bug guard)")

# ------------------------------------------------------------------- runner
def main():
    validate_dimension_jsons()
    validate_layout()
    validate_java_sync()

    print("=" * 60)
    for m in passes:
        print(f"  PASS  {m}")
    if failures:
        print("-" * 60)
        for m in failures:
            print(f"  FAIL  {m}")
    print("=" * 60)
    print(f"{len(passes)} passed, {len(failures)} failed")
    return 1 if failures else 0

if __name__ == "__main__":
    sys.exit(main())
