#!/usr/bin/env python3
"""Hab Stacks v2 — distrito contínuo 192x64x64.

Reconstrói hab_block_01, transit_nexus_01 e market_chapel_01 como uma única
paisagem urbana vertical, irregular e conectada. O objetivo é evitar três caixas
isoladas: as fachadas têm recuos, torres com alturas diferentes, sacadas,
passarelas, anexos, telhados grossos, tubulações e ruas em cânion.
"""
from pathlib import Path
import json
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(Path(__file__).resolve().parent))
from hive_module_lib import ModuleBuilder, S, V, AIR, MK, RAIL_RAW  # noqa: E402

OUT = ROOT / "src/main/resources/data/firstcrusade/structures/hive"
PREV = ROOT / "tools/previews_hab_stacks_v2"
PREV.mkdir(parents=True, exist_ok=True)


def H(name, facing="north"):
    return S(name, facing=facing)


# Structure set
ABW = lambda f="north": H("armored_bulkhead_wall", f)
RSP = lambda f="north": H("recessed_steel_wall_panel", f)
GAW = lambda f="north": H("gothic_arch_wall", f)
PILLAR = S("tall_ribbed_pillar")
BUTT = lambda f="north": H("buttress_column", f)
CORNICE = lambda f="north": H("cathedral_cornice", f)
MOLD = lambda f="north": H("lower_wall_molding", f)
SPIRECAP = S("spire_cap_block")
BALCONY = lambda f="north": H("balcony_edge_trim", f)
BRIDGE = lambda f="north": H("bridge_support_block", f)
DOOR = lambda f="north": H("giant_door_segment", f)
LANCET = lambda f="north": H("narrow_lancet_recess", f)
TRI = lambda f="north": H("triangular_relief_panel", f)
WIN = lambda f="north": H("window_slot_frame", f)
FRAME = S("heavy_structural_frame")
SEAM = lambda f="north": H("vertical_seam_strip", f)

# Industrial set
PIPE = lambda f="north": H("straight_pipe", f)
ELBOW = lambda f="north": H("elbow_pipe", f)
TJUNC = lambda f="north": H("t_pipe_junction", f)
CROSS = S("cross_pipe_junction")
PCLAMP = lambda f="north": H("pipe_support_clamp", f)
VCON = S("vertical_service_conduit")
CABLE = lambda f="north": H("cable_bundle_block", f)
VENT = lambda f="north": H("vent_outlet", f)
FVENT = S("floor_vent")
LIFT = lambda f="north": H("lift_rail", f)
GANTRY = lambda f="north": H("gantry_beam", f)
ANCHOR = lambda f="north": H("suspended_track_anchor", f)
HATCH = lambda f="north": H("maintenance_hatch", f)
MACHINE = lambda f="north": H("machine_casing_block", f)
HAZ = S("hazard_grated_floor")
PEDGE = lambda f="north": H("reinforced_platform_edge", f)

# Floors, lights, details
GLOWWIN = lambda f="north": H("glowing_shrine_window", f)
STAINED = lambda f="north": H("stained_window_variant", f)
CANDLE = lambda f="north": H("candle_alcove", f)
SCONCE = lambda f="north": H("wall_sconce", f)
SHRINE = lambda f="north": H("shrine_recess", f)
BLOOD = S("bloodstained_floor_tile")
CATHF = S("cathedral_floor_tile")
METALF = S("metal_floor_plate")
FLOORGRATE = S("floor_grate")
CATHSTAIR = lambda f="north": H("cathedral_stair_block", f)
SLAB = S("landing_slab")
RAIL = lambda f="north": H("balustrade_railing", f)
SKULL = lambda f="north": H("skull_relief_panel", f)
GARGOYLE = lambda f="north": H("gargoyle_pedestal", f)
CRATE = lambda f="north": H("industrial_crate", f)
BRAZIER = S("brazier_block")

# Existing blocks and furniture
ASH = S("reinforced_ashcrete")
ASH_CR = S("cracked_reinforced_ashcrete")
STEEL = S("riveted_steel_block")
RUST = S("rusted_riveted_steel")
ARMOR = S("armored_hive_plating")
CASING = S("machine_casing")
GRATE = S("industrial_grating")
CATW = S("industrial_catwalk")
HAZP = S("hazard_stripe_panel")
CATHW = S("cathedral_wall")
GARCH = S("gothic_arch")
AQUILA = S("aquila_wall_relief")
LYEL = S("yellow_industrial_lumen")
LGRN = S("green_industrial_lumen")
LRED = S("red_emergency_lumen")
CHAIN = V("chain")
RUG = S("hive_rug")
TABLE = S("hive_table")
CHAIR = lambda f="north": H("hive_chair", f)
BENCH = lambda f="north": H("hive_bench", f)
SHELF = lambda f="north": H("shelf_unit", f)
SUPPLY = lambda f="north": H("supply_crate", f)
LAMP = S("hanging_hive_lamp")
BEACON = S("warning_beacon")
FLOOD = lambda f="north": H("industrial_floodlight", f)
TERMINAL = lambda f="north": H("wall_terminal", f)
BUST = lambda f="north": H("saint_bust", f)
AQUILA_ST = lambda f="north": H("aquila_statue", f)
SAINT = lambda f="north", p=0: S("saint_statue", facing=f, part=p)
BANNER = lambda f="north", p=0: S("aquila_banner", facing=f, part=p)
GUARDIAN = lambda f="north", p=0: S("imperial_guardian_statue", facing=f, part=p)
COG = lambda f="north": H("cogitator_console", f)
CTRL = lambda f="north": H("control_panel", f)
PROP = lambda f="north": H("imperial_propaganda_panel", f)
CONTAINER = lambda f="north": H("cargo_container", f)
SST = lambda f="north", half="bottom": S("riveted_steel_stairs", facing=f, half=half)
AST = lambda f="north", half="bottom": S("reinforced_ashcrete_stairs", facing=f, half=half)
SSL = lambda t="bottom": S("riveted_steel_slab", type=t)
RAILTRACK_X = V("rail", shape="east_west")
RAILTRACK_Z = V("rail", shape="north_south")
LADDER_E = V("ladder", facing="east")
LADDER_W = V("ladder", facing="west")

SX, SY, SZ = 192, 64, 64
b = ModuleBuilder(SX, SY, SZ, seed=95005)


def rect(x0, y0, z0, x1, y1, z1, block):
    b.fill(x0, y0, z0, x1, y1, z1, block)


def chamfered_fill(x0, y0, z0, x1, y1, z1, cut, block):
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if ((x - x0) + (z - z0) < cut or (x1 - x) + (z - z0) < cut or
                    (x - x0) + (z1 - z) < cut or (x1 - x) + (z1 - z) < cut):
                continue
            rect(x, y0, z, x, y1, z, block)


def chamfered_shell(x0, y0, z0, x1, y1, z1, cut, wall=ASH, floor=METALF, roof=STEEL):
    chamfered_fill(x0, y0, z0, x1, y0, z1, cut, floor)
    chamfered_fill(x0, y1, z0, x1, y1, z1, cut, roof)
    # Outer shell by detecting boundary footprint cells.
    footprint = set()
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if ((x - x0) + (z - z0) < cut or (x1 - x) + (z - z0) < cut or
                    (x - x0) + (z1 - z) < cut or (x1 - x) + (z1 - z) < cut):
                continue
            footprint.add((x, z))
    for x, z in footprint:
        if any((x + dx, z + dz) not in footprint for dx, dz in ((1,0),(-1,0),(0,1),(0,-1))):
            rect(x, y0 + 1, z, x, y1 - 1, z, wall)
    for x, z in footprint:
        if all((x + dx, z + dz) in footprint for dx, dz in ((1,0),(-1,0),(0,1),(0,-1))):
            rect(x, y0 + 1, z, x, y1 - 1, z, AIR)


def facade_z(x0, x1, y0, y1, z, facing, window_stride=6, lit_stride=3):
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            if y in (y0, y1) or (y - y0) % 9 == 0:
                block = CORNICE(facing) if (y - y0) % 18 == 0 else MOLD(facing)
            elif x in (x0, x1) or (x - x0) % 8 == 0:
                block = PILLAR
            else:
                block = ABW(facing) if (x + y) % 4 else RSP(facing)
            b.put(x, y, z, block)
    for i, x in enumerate(range(x0 + 3, x1 - 2, window_stride)):
        for y in range(y0 + 4, y1 - 2, 7):
            b.put(x, y, z, GLOWWIN(facing) if i % lit_stride == 0 else WIN(facing))
            if y + 1 <= y1 - 1:
                b.put(x, y + 1, z, LANCET(facing))
    for x in range(x0 + 5, x1 - 4, 13):
        b.put(x, min(y1 - 2, y0 + 12), z, TRI(facing))


def facade_x(z0, z1, y0, y1, x, facing, window_stride=6):
    for z in range(z0, z1 + 1):
        for y in range(y0, y1 + 1):
            if y in (y0, y1) or (y - y0) % 9 == 0:
                block = CORNICE(facing) if (y - y0) % 18 == 0 else MOLD(facing)
            elif z in (z0, z1) or (z - z0) % 8 == 0:
                block = PILLAR
            else:
                block = ABW(facing) if (z + y) % 4 else RSP(facing)
            b.put(x, y, z, block)
    for i, z in enumerate(range(z0 + 3, z1 - 2, window_stride)):
        for y in range(y0 + 4, y1 - 2, 7):
            b.put(x, y, z, GLOWWIN(facing) if i % 3 == 0 else WIN(facing))
            if y + 1 <= y1 - 1:
                b.put(x, y + 1, z, LANCET(facing))


def crown_gable_ns(x0, x1, z0, z1, y0, rise=7):
    r = min(rise, max(1, (x1 - x0) // 2))
    for s in range(r + 1):
        xa, xb, y = x0 + s, x1 - s, y0 + s
        if xa > xb:
            break
        for z in range(z0, z1 + 1):
            b.put(xa, y, z, CATHSTAIR("east"))
            b.put(xb, y, z, CATHSTAIR("west"))
    for z in range(z0, z1 + 1, 3):
        b.put((x0 + x1) // 2, y0 + r + 1, z, SPIRECAP)


def crown_gable_ew(x0, x1, z0, z1, y0, rise=7):
    r = min(rise, max(1, (z1 - z0) // 2))
    for s in range(r + 1):
        za, zb, y = z0 + s, z1 - s, y0 + s
        if za > zb:
            break
        for x in range(x0, x1 + 1):
            b.put(x, y, za, CATHSTAIR("south"))
            b.put(x, y, zb, CATHSTAIR("north"))
    for x in range(x0, x1 + 1, 3):
        b.put(x, y0 + r + 1, (z0 + z1) // 2, SPIRECAP)


def balcony_z(x0, x1, y, z, facing, depth_sign):
    z2 = z + depth_sign * 2
    rect(x0, y, min(z, z2), x1, y, max(z, z2), SLAB)
    rail_z = z2
    for x in range(x0, x1 + 1):
        b.put(x, y + 1, rail_z, BALCONY(facing) if x % 3 else RAIL(facing))
    for x in range(x0 + 2, x1, 7):
        rect(x, y - 4, z, x, y - 1, z2, BRIDGE(facing))
        b.put(x, y + 2, rail_z, SCONCE(facing))


def balcony_x(z0, z1, y, x, facing, depth_sign):
    x2 = x + depth_sign * 2
    rect(min(x, x2), y, z0, max(x, x2), y, z1, SLAB)
    rail_x = x2
    for z in range(z0, z1 + 1):
        b.put(rail_x, y + 1, z, BALCONY(facing) if z % 3 else RAIL(facing))
    for z in range(z0 + 2, z1, 7):
        rect(x, y - 4, z, x2, y - 1, z, BRIDGE(facing))
        b.put(rail_x, y + 2, z, SCONCE(facing))


def skybridge_x(x0, x1, y, z0, z1):
    rect(x0, y, z0, x1, y, z1, CATW)
    for x in range(x0, x1 + 1):
        b.put(x, y + 1, z0, RAIL_RAW)
        b.put(x, y + 1, z1, RAIL_RAW)
        if x % 6 == 0:
            b.put(x, y + 2, z0, LYEL)
    for x in (x0, x1):
        rect(x, y - 5, z0, x, y - 1, z1, BRIDGE("east" if x == x0 else "west"))


def skybridge_z(z0, z1, y, x0, x1):
    rect(x0, y, z0, x1, y, z1, CATW)
    for z in range(z0, z1 + 1):
        b.put(x0, y + 1, z, RAIL_RAW)
        b.put(x1, y + 1, z, RAIL_RAW)
        if z % 6 == 0:
            b.put(x0, y + 2, z, LYEL)
    for z in (z0, z1):
        rect(x0, y - 5, z, x1, y - 1, z, BRIDGE("south" if z == z0 else "north"))


def street_axis(cx):
    # Main canyon: 14-wide street with drainage and a clear 9-block-high volume.
    for z in range(SZ):
        for x in range(cx - 7, cx + 7):
            b.put(x, 1, z, METALF if x not in (cx - 6, cx + 5) else HAZ)
        for x in (cx - 5, cx + 4):
            b.put(x, 1, z, FLOORGRATE)
            b.put(x, 0, z, LGRN if z % 7 == 0 else ASH_CR)
        rect(cx - 7, 2, z, cx + 6, 9, z, AIR)
    for z in range(4, SZ, 12):
        b.put(cx - 8, 5, z, FLOOD("east"))
        b.put(cx + 7, 5, z, FLOOD("west"))


def exterior_pipe_z(x, y, z0, z1, facing):
    for z in range(z0, z1 + 1):
        b.put(x, y, z, PIPE(facing))
        if z % 8 == 0:
            b.put(x, y - 1, z, PCLAMP(facing))
    b.put(x, y, z0, ELBOW("north"))
    b.put(x, y, z1, ELBOW("south"))


def exterior_pipe_x(z, y, x0, x1, facing):
    for x in range(x0, x1 + 1):
        b.put(x, y, z, PIPE(facing))
        if x % 8 == 0:
            b.put(x, y - 1, z, PCLAMP(facing))
    b.put(x0, y, z, ELBOW("west"))
    b.put(x1, y, z, ELBOW("east"))


def room_cluster(x0, z0, x1, z1, y, door_side="south"):
    # Compact occupied hab room, intentionally varied and not repeated as a perfect grid.
    rect(x0, y, z0, x1, y + 4, z0, CATHW)
    rect(x0, y, z1, x1, y + 4, z1, CATHW)
    rect(x0, y, z0, x0, y + 4, z1, CATHW)
    rect(x1, y, z0, x1, y + 4, z1, CATHW)
    rect(x0 + 1, y, z0 + 1, x1 - 1, y + 3, z1 - 1, AIR)
    if door_side == "south":
        rect((x0+x1)//2, y, z1, (x0+x1)//2 + 1, y + 2, z1, AIR)
    elif door_side == "north":
        rect((x0+x1)//2, y, z0, (x0+x1)//2 + 1, y + 2, z0, AIR)
    elif door_side == "east":
        rect(x1, y, (z0+z1)//2, x1, y + 2, (z0+z1)//2 + 1, AIR)
    else:
        rect(x0, y, (z0+z1)//2, x0, y + 2, (z0+z1)//2 + 1, AIR)
    b.put(x0 + 2, y, z0 + 2, TABLE)
    b.put(x0 + 2, y, z0 + 3, CHAIR("north"))
    b.put(x1 - 2, y, z1 - 2, SHELF("west"))
    b.put(x1 - 3, y, z0 + 2, RUG)
    b.put(x1 - 2, y + 4, z0 + 2, LAMP)
    b.put(x0 + 1, y + 2, z1 - 2, TERMINAL("north"))


# ---------------------------------------------------------------- Common foundation and streets
rect(0, 0, 0, SX - 1, 0, SZ - 1, ASH_CR)
for cx in (32, 96, 160):
    street_axis(cx)
# Cross-corridors connect all three canyons at ground and gallery levels.
for z, y in ((17, 1), (32, 1), (48, 1)):
    rect(4, y, z - 2, 187, y, z + 2, METALF)
    for x in range(4, 188, 9):
        b.put(x, y, z - 2, HAZ)
        b.put(x, y, z + 2, FLOORGRATE)


# ================================================================= HAB STACKS (0..63)
# West and east towers flank a street canyon; footprints and heights are deliberately unequal.
chamfered_shell(2, 2, 4, 23, 45, 58, 4, ASH, METALF, STEEL)
chamfered_shell(40, 2, 8, 62, 39, 57, 3, ASH, METALF, STEEL)
chamfered_shell(5, 19, 12, 28, 56, 43, 4, ASH, METALF, STEEL)
chamfered_shell(37, 14, 25, 59, 51, 62, 4, ASH, METALF, STEEL)
chamfered_shell(10, 10, 42, 38, 38, 63, 5, ASH, METALF, STEEL)

# Rebuild visible facades after overlapping shells.
facade_x(8, 54, 3, 43, 2, "east", 7)
facade_x(12, 54, 3, 37, 62, "west", 7)
facade_z(5, 22, 3, 43, 4, "south", 6)
facade_z(41, 61, 3, 37, 8, "south", 6)
facade_z(7, 27, 20, 54, 12, "south", 6)
facade_z(38, 58, 15, 49, 62, "north", 6)

# Thick stepped crowns, roof plant and clustered spires.
crown_gable_ew(5, 27, 14, 41, 47, 5)
crown_gable_ns(39, 60, 27, 59, 52, 5)
for x, z, h in ((7,8,48),(19,9,51),(42,12,43),(57,13,46),(11,48,42),(48,52,54)):
    rect(x, h - 5, z, x + 2, h, z + 2, PILLAR)
    b.put(x + 1, h + 1, z + 1, SPIRECAP)

# Balconies and overhangs break the façades at irregular heights.
balcony_x(10, 32, 13, 23, "west", 1)
balcony_x(35, 54, 29, 23, "west", 1)
balcony_x(14, 38, 18, 40, "east", -1)
balcony_x(42, 58, 34, 40, "east", -1)
balcony_z(7, 20, 24, 4, "south", -1)
balcony_z(43, 60, 27, 8, "south", -1)

# Three occupied skybridges over the canyon.
skybridge_x(20, 43, 15, 28, 35)
skybridge_x(17, 46, 30, 29, 34)
skybridge_x(23, 40, 43, 30, 33)
for x in range(25, 39):
    b.put(x, 15, 31, GLOWWIN("south"))
    if x % 4 == 0:
        b.put(x, 16, 31, SCONCE("south"))

# Hab interiors at several elevations, leaving large shafts and galleries.
for y, clusters in (
    (3, [(5,8,18,17,"south"),(43,12,58,21,"south"),(6,47,20,56,"north"),(43,44,58,54,"north")]),
    (16, [(7,16,19,25,"south"),(44,18,57,27,"south"),(8,45,21,54,"north")]),
    (31, [(9,18,21,27,"south"),(42,36,56,45,"north")]),
):
    for x0,z0,x1,z1,door in clusters:
        room_cluster(x0,z0,x1,z1,y,door)

# Service shafts, ducts and improvised additions.
for x, z, top in ((4,28,43),(21,51,53),(60,32,38),(39,48,49)):
    for y in range(3, top):
        b.put(x, y, z, LIFT("east" if x < 32 else "west"))
    b.put(x, min(top, 58), z, BEACON)
for x in (1, 24, 39, 63):
    exterior_pipe_z(x, 11 + (x % 3) * 6, 8, 56, "east" if x < 32 else "west")
for _ in range(75):
    x = b.rng.choice(list(range(3,23)) + list(range(41,62)))
    z = b.rng.randrange(7,59)
    y = b.rng.choice((2,3,16,17,30,31))
    b.put(x, y, z, SUPPLY(["north","south","east","west"][b.rng.randrange(4)]) if b.rng.random() < .45 else CRATE())

# Small street shrines and lower-market clutter.
for z in (11, 26, 44, 57):
    b.put(24, 4, z, SHRINE("east"))
    b.put(39, 4, z, CANDLE("west"))
    b.put(24, 3, z + 1 if z < 60 else z - 1, BRAZIER)


# ================================================================= TRANSIT NEXUS (64..127)
# A basilica-like station hall with side concourses and offset towers.
chamfered_shell(69, 2, 7, 123, 34, 57, 6, ASH, METALF, STEEL)
# Open the interior and street canyon after shell construction.
rect(75, 3, 12, 117, 33, 52, AIR)
rect(89, 2, 0, 102, 10, 63, AIR)
facade_z(75, 117, 3, 32, 7, "south", 7)
facade_z(75, 117, 3, 32, 57, "north", 7)
facade_x(13, 51, 3, 32, 69, "east", 7)
facade_x(13, 51, 3, 32, 123, "west", 7)

# Side lift towers are offset and unequal.
for x0,z0,x1,z1,h in ((66,4,78,18,46),(115,42,127,61,43),(67,46,80,63,38),(116,3,127,19,50)):
    chamfered_shell(x0,2,z0,x1,h,z1,2,ASH,METALF,STEEL)
    crown_gable_ew(x0+1,x1-1,z0+1,z1-1,h+1,4)

# Broad station roof, clerestory and thick lantern.
crown_gable_ns(72, 120, 10, 54, 35, 9)
chamfered_shell(85, 42, 21, 107, 54, 43, 4, STEEL, STEEL, STEEL)
crown_gable_ns(87, 105, 23, 41, 55, 4)
for z in range(15, 53, 6):
    b.put(96, 40, z, STAINED("east" if (z // 6) % 2 else "west"))

# Platforms and tracks; one line cuts under the street, another is elevated.
for z in (17, 47):
    for x in range(72, 121):
        b.put(x, 2, z, RAILTRACK_X)
        b.put(x, 1, z, STEEL)
    plat_z = z + 3 if z < 32 else z - 4
    rect(72, 2, plat_z, 120, 2, plat_z + 2, SLAB)
    for x in range(72, 121):
        b.put(x, 3, plat_z if z < 32 else plat_z + 2, RAIL_RAW)
    for x in range(76, 119, 8):
        rect(x, 3, plat_z + 1, x, 10, plat_z + 1, PILLAR)
        b.put(x, 11, plat_z + 1, GARCH)
        b.put(x, 9, plat_z + 1, LAMP)
# Elevated line across the nave.
for z in range(9, 56):
    b.put(110, 20, z, RAILTRACK_Z)
    b.put(109, 20, z, CATW)
    b.put(111, 20, z, CATW)
    b.put(109, 21, z, RAIL_RAW)
    b.put(111, 21, z, RAIL_RAW)
for z in range(12, 55, 9):
    rect(108, 3, z, 112, 19, z, FRAME)

# Central concourse balconies and bridges into neighboring modules.
for x in range(75, 118):
    b.put(x, 13, 28, CATW)
    b.put(x, 13, 36, CATW)
    b.put(x, 14, 28, RAIL_RAW)
    b.put(x, 14, 36, RAIL_RAW)
    if x % 7 == 0:
        b.put(x, 15, 28, LYEL)
skybridge_x(58, 76, 18, 29, 34)
skybridge_x(118, 136, 24, 29, 34)
skybridge_x(61, 81, 36, 44, 49)
skybridge_x(113, 140, 39, 13, 18)

# Lift cores and vertical circulation.
for ex,ez in ((72,12),(116,12),(72,48),(116,48)):
    rect(ex,2,ez,ex+4,36,ez+4,ARMOR)
    rect(ex+1,2,ez+1,ex+3,35,ez+3,AIR)
    for y in range(2,36):
        b.put(ex+1,y,ez+1,LIFT("east"))
        b.put(ex+3,y,ez+3,LADDER_W)
    for yy in (2,13,20,32):
        rect(ex+1,yy,ez+1,ex+3,yy,ez+3,GRATE)
    b.put(ex+2,35,ez+2,LYEL)

# Monument, ticketing, terminals and suspended lights.
rect(91,2,29,101,4,35,CATHF)
for p in range(3):
    b.put(96,5+p,32,GUARDIAN("south",p))
b.put(92,3,31,BRAZIER); b.put(100,3,31,BRAZIER)
for x in (79,86,105,112):
    b.put(x,3,10,COG("south")); b.put(x+1,3,10,CTRL("south"))
for x in range(78,116,9):
    for y in range(28,32): b.put(x,y,32,CHAIN)
    b.put(x,27,32,LAMP)

# Station frontage pipes and roof machinery.
exterior_pipe_x(59, 15, 66, 126, "east")
exterior_pipe_z(125, 27, 9, 55, "west")
for x,z in ((70,8),(121,9),(68,54),(119,55)):
    b.put(x,8,z,VENT("east" if x < 96 else "west"))
    b.put(x,9,z,VCON)
for x,z in ((81,18),(111,46),(104,15)):
    rect(x,36,z,x+2,41,z+2,CASING)
    b.put(x+1,42,z+1,VENT("north"))


# ================================================================= MARKET + CHAPEL (128..191)
# Market podium consists of offset low halls around an open plaza.
chamfered_shell(130,2,5,151,22,58,4,ASH,METALF,STEEL)
chamfered_shell(168,2,9,190,26,56,4,ASH,METALF,STEEL)
chamfered_shell(145,2,38,178,18,63,5,ASH,METALF,STEEL)
# Open plaza and street canyon.
rect(152,2,8,167,17,55,AIR)
rect(153,2,0,166,9,63,AIR)
facade_x(8,55,3,20,130,"east",7)
facade_x(12,53,3,24,190,"west",7)
facade_z(132,149,3,20,5,"south",6)
facade_z(170,188,3,24,9,"south",6)

# Market arcades and irregular stalls around the plaza.
for z in (13,22,41,50):
    for x in list(range(133,151,5)) + list(range(170,189,5)):
        b.put(x,2,z,TABLE)
        b.put(x+1 if x < 160 else x-1,2,z,SUPPLY("south"))
        b.put(x,5,z,HAZP if (x+z)%2 else STAINED("south"))
        b.put(x,6,z,LAMP)
for x,z in ((137,16),(146,45),(174,18),(184,44),(148,56),(177,56)):
    b.put(x,2,z,CRATE())
    b.put(x+1,2,z,BLOOD if (x+z)%3==0 else RUG)
# Arcaded plaza walls.
for z in range(11,54,7):
    rect(150,2,z,150,11,z,PILLAR); b.put(150,12,z,GARCH)
    rect(169,2,z,169,11,z,PILLAR); b.put(169,12,z,GARCH)
    b.put(151,7,z,CANDLE("east")); b.put(168,7,z,CANDLE("west"))

# Upper chapel: broad cruciform mass, offset transept and twin towers.
chamfered_shell(139,20,14,181,46,54,6,CATHW,CATHF,STEEL)
rect(151,20,5,169,43,62,CATHW)
rect(132,24,26,188,40,42,CATHW)
# Carve nave and transept.
rect(146,21,19,174,45,49,AIR)
rect(153,21,8,167,42,60,AIR)
rect(136,25,29,184,39,39,AIR)
# Visible façades and thick roof masses.
facade_z(144,176,21,44,14,"south",6)
facade_z(144,176,21,44,54,"north",6)
facade_x(18,50,21,44,139,"east",6)
facade_x(18,50,21,44,181,"west",6)
crown_gable_ns(141,179,16,52,47,8)
crown_gable_ew(133,187,27,41,42,6)

# Twin bell towers and rear chapter house, all with different heights.
for x0,z0,x1,z1,h in ((131,18,143,31,53),(177,18,190,32,58),(143,49,155,63,50)):
    chamfered_shell(x0,20,z0,x1,h,z1,2,CATHW,CATHF,STEEL)
    crown_gable_ew(x0+1,x1-1,z0+1,z1-1,h+1,4)
    for yy in range(26,min(h,56),8):
        b.put((x0+x1)//2,yy,z0,GLOWWIN("south"))
        b.put((x0+x1)//2,yy,z1,STAINED("north"))

# Nave columns, galleries, altar and seating.
for z in range(20,51,6):
    for x in (148,172):
        rect(x,21,z,x,40,z,PILLAR)
        b.put(x,41,z,GARCH)
    rect(149,31,z,171,31,z,SLAB)
    b.put(149,32,z,RAIL("east")); b.put(171,32,z,RAIL("west"))
for z in range(22,47,4):
    rect(153,21,z,157,21,z,BENCH("north"))
    rect(163,21,z,167,21,z,BENCH("north"))
for z in range(15,51):
    b.put(159,21,z,RUG); b.put(160,21,z,RUG)
# Altar at north end.
rect(151,21,49,168,25,53,CATHF)
b.put(159,26,51,AQUILA_ST("south")); b.put(160,26,51,AQUILA_ST("south"))
for p in range(3):
    b.put(149,22+p,50,SAINT("east",p)); b.put(170,22+p,50,SAINT("west",p))
b.put(155,22,48,BRAZIER); b.put(165,22,48,BRAZIER)
b.put(159,29,53,SHRINE("north")); b.put(160,29,53,SKULL("north"))
# Hanging central chandelier and banners.
for y in range(37,44): b.put(160,y,33,CHAIN)
b.put(160,36,33,BRAZIER)
for x in (145,176):
    b.put(x,35,33,BANNER("south",0)); b.put(x,36,33,BANNER("south",1))

# Terraces, side chapels and market/chapel stairs.
balcony_z(142,178,27,14,"south",-1)
balcony_x(22,47,34,181,"west",1)
for i in range(18):
    b.put(133+i,3+i,20,SST("east"))
    b.put(134+i,3+i,20,SST("east"))
for i in range(17):
    b.put(187-i,3+i,45,SST("west"))
    b.put(186-i,3+i,45,SST("west"))
# Street shrines and market lights.
for z in (10,25,40,55):
    b.put(152,4,z,SHRINE("east")); b.put(167,4,z,CANDLE("west"))
    b.put(152,3,z+1 if z<58 else z-1,BRAZIER)

# Roof clutter, pipes and flying details.
exterior_pipe_z(129,13,7,58,"east")
exterior_pipe_z(191,17,8,56,"west")
exterior_pipe_x(60,29,132,189,"east")
for x,z,h in ((134,8,29),(186,12,34),(146,58,25),(176,57,30)):
    rect(x,h-5,z,x+2,h,z+2,VCON)
    b.put(x+1,h+1,z+1,BEACON)
for x,z in ((134,14),(187,14),(136,54),(184,52)):
    b.put(x,22,z,GARGOYLE("south" if z<32 else "north"))

# ---------------------------------------------------------------- Shared microdetail and markers
for _ in range(95):
    x = b.rng.randrange(2,190); z = b.rng.randrange(3,61)
    if any(abs(x-cx)<8 for cx in (32,96,160)):
        continue
    y = b.rng.choice((1,2,13,20,30))
    if b.get(x,y,z) == AIR:
        b.put(x,y,z,CRATE(["north","south","east","west"][b.rng.randrange(4)]) if b.rng.random()<.55 else SUPPLY())
for _ in range(70):
    x = b.rng.randrange(3,189); z = b.rng.randrange(3,61)
    if b.get(x,1,z) != AIR:
        b.put(x,1,z,BLOOD if b.rng.random()<.10 else (FVENT if b.rng.random()<.48 else FLOORGRATE))

markers = [
    (10,3,12,"marker_civil_spawn"),(18,17,50,"marker_civil_spawn"),(49,3,18,"marker_civil_spawn"),
    (45,31,44,"marker_civil_spawn"),(31,2,8,"marker_guardsman_spawn"),(31,30,31,"marker_patrol_point"),
    (76,3,20,"marker_civil_spawn"),(116,3,45,"marker_civil_spawn"),(96,3,32,"marker_commander_point"),
    (110,21,31,"marker_vehicle_point"),(72,13,31,"marker_trade_point"),(120,13,31,"marker_trade_point"),
    (138,3,18,"marker_trade_point"),(181,3,43,"marker_trade_point"),(160,22,46,"marker_commander_point"),
    (144,32,33,"marker_patrol_point"),(180,32,33,"marker_patrol_point"),(160,3,8,"marker_guardsman_spawn"),
    (17,3,54,"marker_loot_point"),(105,3,12,"marker_loot_point"),(186,3,52,"marker_loot_point"),
]
for x,y,z,m in markers:
    b.put(x,y,z,MK(m))

b.resolve()


# ---------------------------------------------------------------- Slice into existing module IDs
def slice_module(x0, sx=64, sy=64, sz=64):
    sub = ModuleBuilder(sx, sy, sz, seed=95005+x0)
    for (x,y,z), state in b.grid.items():
        if x0 <= x < x0+sx and 0 <= y < sy and 0 <= z < sz:
            sub.put(x-x0,y,z,state)
    return sub

modules = [
    ("hab/hab_block_01",0),
    ("hab/transit_nexus_01",64),
    ("hab/market_chapel_01",128),
]
results=[]
for rel,x0 in modules:
    sub=slice_module(x0)
    nonair,pal,size=sub.write_nbt(str(OUT/f"{rel}.nbt"))
    sub.previews(str(PREV/rel.replace('/','_')),
                 plans=[(63,"plan_roof"),(40,"plan_upper"),(24,"plan_gallery"),(8,"plan_ground")],
                 sections_x=[(31,"section_x31")],sections_z=[(31,"section_z31")])
    results.append((rel,nonair,pal,size))

for rel,_ in modules:
    meta=ROOT/"src/main/resources/data/firstcrusade/hive_modules"/f"{rel}.json"
    data=json.loads(meta.read_text(encoding="utf-8"))
    data["size"]=[64,64,64]
    data["description"]="Hab Stacks rebuild v2: staggered vertical city, occupied skybridges, transit basilica and broad market-chapel complex."
    meta.write_text(json.dumps(data,indent=2,ensure_ascii=False)+"\n",encoding="utf-8")

district=ROOT/"src/main/resources/data/firstcrusade/hive_districts/hab_stacks.json"
ddata=json.loads(district.read_text(encoding="utf-8"))
ddata["description"]="Hab Stacks v2 (192x64x64): vertical canyon city with irregular towers, transit basilica, market podium and broad chapel crown."
district.write_text(json.dumps(ddata,indent=2,ensure_ascii=False)+"\n",encoding="utf-8")

b.previews(str(PREV/"hab_stacks_district_v2"),
           plans=[(63,"plan_roof"),(45,"plan_crown"),(30,"plan_gallery"),(16,"plan_mid"),(8,"plan_ground")],
           sections_x=[(31,"section_hab_x31"),(95,"section_transit_x95"),(159,"section_chapel_x159")],
           sections_z=[(17,"section_front_z17"),(32,"section_spine_z32"),(48,"section_rear_z48")])

print("Hab Stacks v2")
for rel,nonair,pal,size in results:
    print(f"{rel:32s} blocks={nonair:7d} palette={pal:3d} bytes={size:8d}")
print(f"combined visible states: {sum(1 for v in b.grid.values() if v != AIR)}")
print(PREV)
