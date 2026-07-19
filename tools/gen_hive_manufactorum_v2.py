#!/usr/bin/env python3
"""Manufactorum v2 — distrito contínuo 192x64x64.

Substitui os três salões industriais antigos por uma composição única e escalonada:
fundição monumental, nave de montagem e salão de geração. O distrito é criado como
uma única estrutura para que passarelas, tubulações, galerias e volumes atravessem
as emendas dos módulos. No final ele é dividido nos IDs existentes.
"""
from pathlib import Path
import json
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(Path(__file__).resolve().parent))
from hive_module_lib import ModuleBuilder, S, V, AIR, MK  # noqa: E402

OUT = ROOT / "src/main/resources/data/firstcrusade/structures/hive"
PREV = ROOT / "tools/previews_manufactorum_v2"
PREV.mkdir(parents=True, exist_ok=True)


def H(name, facing="north"):
    return S(name, facing=facing)


# Hive City block set I — structure
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

# Hive City block set II — industrial
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

# Hive City block set III — details
GLOWWIN = lambda f="north": H("glowing_shrine_window", f)
STAINED = lambda f="north": H("stained_window_variant", f)
CANDLE = lambda f="north": H("candle_alcove", f)
SCONCE = lambda f="north": H("wall_sconce", f)
SHRINE = lambda f="north": H("shrine_recess", f)
BLOOD = S("bloodstained_floor_tile")
CATHF = S("cathedral_floor_tile")
METALF = S("metal_floor_plate")
FLOORGRATE = S("floor_grate")
STAIR = lambda f="north": H("cathedral_stair_block", f)
SLAB = S("landing_slab")
RAIL = lambda f="north": H("balustrade_railing", f)
SKULL = lambda f="north": H("skull_relief_panel", f)
GARGOYLE = lambda f="north": H("gargoyle_pedestal", f)
CRATE = lambda f="north": H("industrial_crate", f)
BRAZIER = S("brazier_block")

# Existing mass and machine blocks
ASH = S("reinforced_ashcrete")
ASH_CR = S("cracked_reinforced_ashcrete")
STEEL = S("riveted_steel_block")
RUST = S("rusted_riveted_steel")
ARMOR = S("armored_hive_plating")
CASING = S("machine_casing")
FURNACE = lambda f="north": H("forge_furnace", f)
CRUCIBLE = S("smelter_crucible")
PRESS = S("industrial_press")
CONVEYOR_X = S("conveyor_belt", axis="x")
CONVEYOR_Z = S("conveyor_belt", axis="z")
TURBINE = S("industrial_turbine", axis="y")
BOILER = S("boiler_tank", axis="y")
COOLANT = S("coolant_tank")
STACK = S("smoke_stack", axis="y")
DUCT = S("ventilation_duct", axis="y")
COG = lambda f="north": H("cogitator_console", f)
CTRL = lambda f="north": H("control_panel", f)
PROP = lambda f="north": H("imperial_propaganda_panel", f)
LRED = S("red_emergency_lumen")
LYEL = S("yellow_industrial_lumen")
LGRN = S("green_industrial_lumen")
CHAIN = V("chain")
LADDER_E = V("ladder", facing="east")
LADDER_W = V("ladder", facing="west")

SX, SY, SZ = 192, 64, 64
b = ModuleBuilder(SX, SY, SZ, seed=94004)


def rect(x0, y0, z0, x1, y1, z1, block):
    b.fill(x0, y0, z0, x1, y1, z1, block)


def hollow_box(x0, y0, z0, x1, y1, z1, wall, floor=METALF, roof=STEEL, thickness=1):
    rect(x0, y0, z0, x1, y0, z1, floor)
    rect(x0, y1, z0, x1, y1, z1, roof)
    for t in range(thickness):
        rect(x0+t, y0+1, z0+t, x1-t, y1-1, z0+t, wall)
        rect(x0+t, y0+1, z1-t, x1-t, y1-1, z1-t, wall)
        rect(x0+t, y0+1, z0+t, x0+t, y1-1, z1-t, wall)
        rect(x1-t, y0+1, z0+t, x1-t, y1-1, z1-t, wall)
    if x1-x0 > thickness*2 and z1-z0 > thickness*2 and y1-y0 > 2:
        rect(x0+thickness, y0+1, z0+thickness, x1-thickness, y1-1, z1-thickness, AIR)


def chamfered_mass(x0, y0, z0, x1, y1, z1, cut, block):
    for x in range(x0, x1+1):
        for z in range(z0, z1+1):
            if ((x-x0)+(z-z0) < cut or (x1-x)+(z-z0) < cut or
                    (x-x0)+(z1-z) < cut or (x1-x)+(z1-z) < cut):
                continue
            rect(x, y0, z, x, y1, z, block)


def facade_z(x0, x1, y0, y1, z, facing, window_gap=8, relief_gap=15):
    for x in range(x0, x1+1):
        for y in range(y0, y1+1):
            if y in (y0, y1) or (y-y0) % 10 == 0:
                block = CORNICE(facing) if (y-y0) % 20 == 0 else MOLD(facing)
            elif x in (x0, x1) or (x-x0) % 9 == 0:
                block = PILLAR
            else:
                block = ABW(facing) if (x+y) % 3 else RSP(facing)
            b.put(x, y, z, block)
    for x in range(x0+4, x1-3, window_gap):
        if y1-y0 >= 10:
            b.put(x, y0+5, z, WIN(facing))
            b.put(x, y0+6, z, GLOWWIN(facing) if (x // max(1, window_gap)) % 2 else LANCET(facing))
    for x in range(x0+6, x1-5, relief_gap):
        b.put(x, min(y1-3, y0+13), z, TRI(facing))


def facade_x(z0, z1, y0, y1, x, facing, window_gap=8):
    for z in range(z0, z1+1):
        for y in range(y0, y1+1):
            if y in (y0, y1) or (y-y0) % 10 == 0:
                block = CORNICE(facing) if (y-y0) % 20 == 0 else MOLD(facing)
            elif z in (z0, z1) or (z-z0) % 9 == 0:
                block = PILLAR
            else:
                block = ABW(facing) if (z+y) % 3 else RSP(facing)
            b.put(x, y, z, block)
    for z in range(z0+4, z1-3, window_gap):
        b.put(x, y0+5, z, WIN(facing))
        b.put(x, y0+6, z, LANCET(facing))


def gable_ns(x0, x1, z0, z1, y0, rise=8):
    r = min(rise, max(1, (x1-x0)//2))
    for s in range(r+1):
        xa, xb, y = x0+s, x1-s, y0+s
        if xa > xb:
            break
        for z in range(z0, z1+1):
            b.put(xa, y, z, STAIR("east"))
            b.put(xb, y, z, STAIR("west"))
            if s % 3 == 0:
                b.put(xa, y+1, z, CORNICE("east"))
                b.put(xb, y+1, z, CORNICE("west"))
    for z in range(z0, z1+1):
        b.put((x0+x1)//2, y0+r+1, z, SPIRECAP if z % 4 == 0 else STEEL)


def gable_ew(x0, x1, z0, z1, y0, rise=8):
    r = min(rise, max(1, (z1-z0)//2))
    for s in range(r+1):
        za, zb, y = z0+s, z1-s, y0+s
        if za > zb:
            break
        for x in range(x0, x1+1):
            b.put(x, y, za, STAIR("south"))
            b.put(x, y, zb, STAIR("north"))
            if s % 3 == 0:
                b.put(x, y+1, za, CORNICE("south"))
                b.put(x, y+1, zb, CORNICE("north"))
    for x in range(x0, x1+1):
        b.put(x, y0+r+1, (z0+z1)//2, SPIRECAP if x % 4 == 0 else STEEL)


def buttress_z(x, z, y0, y1, outward, facing):
    depth = 2
    for y in range(y0, y1+1):
        depth = 5 if y < y0+(y1-y0)//3 else 4 if y < y0+2*(y1-y0)//3 else 2
        for d in range(depth):
            b.put(x, y, z+outward*d, PILLAR if d == 0 else BUTT(facing))
    b.put(x, y1+1, z+outward*(depth-1), GARGOYLE(facing))


def buttress_x(z, x, y0, y1, outward, facing):
    depth = 2
    for y in range(y0, y1+1):
        depth = 5 if y < y0+(y1-y0)//3 else 4 if y < y0+2*(y1-y0)//3 else 2
        for d in range(depth):
            b.put(x+outward*d, y, z, PILLAR if d == 0 else BUTT(facing))
    b.put(x+outward*(depth-1), y1+1, z, GARGOYLE(facing))


def pipe_x(x0, x1, y, z, facing="east"):
    for x in range(x0, x1+1):
        b.put(x, y, z, PIPE(facing))
        if x % 8 == 0:
            b.put(x, y-1, z, PCLAMP(facing))
    b.put(x0, y, z, ELBOW("west")); b.put(x1, y, z, ELBOW("east"))


def pipe_z(z0, z1, y, x, facing="south"):
    for z in range(z0, z1+1):
        b.put(x, y, z, PIPE(facing))
        if z % 8 == 0:
            b.put(x, y-1, z, PCLAMP(facing))
    b.put(x, y, z0, ELBOW("north")); b.put(x, y, z1, ELBOW("south"))


def stair_run_x(x0, x1, y0, z, facing):
    step = 1 if x1 >= x0 else -1
    for i, x in enumerate(range(x0, x1+step, step)):
        b.put(x, y0+i, z, STAIR(facing))


def street_tunnel(cx):
    # North and south entrances with a clear 14-wide route.
    for z in range(0, SZ):
        for x in range(cx-7, cx+7):
            b.put(x, 1, z, METALF if x not in (cx-6, cx+5) else HAZ)
        for x in (cx-4, cx+3):
            b.put(x, 1, z, FLOORGRATE)
    for z in list(range(0, 9)) + list(range(55, 64)):
        rect(cx-8, 2, z, cx+7, 9, z, AIR)
    for x in range(cx-8, cx+8):
        b.put(x, 10, 1, CORNICE("south")); b.put(x, 10, 62, CORNICE("north"))
    for x in (cx-8, cx+7):
        rect(x, 2, 0, x, 18, 6, PILLAR)
        rect(x, 2, 57, x, 18, 63, PILLAR)
    b.put(cx-10, 5, 1, SHRINE("south")); b.put(cx+9, 5, 1, SHRINE("south"))
    b.put(cx-10, 4, 62, SCONCE("north")); b.put(cx+9, 4, 62, SCONCE("north"))


# ---------------------------------------------------------------- foundation and common circulation
rect(0, 0, 0, SX-1, 0, SZ-1, ASH_CR)
for x in range(SX):
    for z in range(SZ):
        if (x*11 + z*7) % 31 == 0:
            b.put(x, 1, z, FVENT)
        elif (x+z) % 17 == 0:
            b.put(x, 1, z, HAZ)
        else:
            b.put(x, 1, z, METALF if z % 9 else CATHF)

for cx in (32, 96, 160):
    street_tunnel(cx)

# East-west central process spine and two elevated galleries.
for x in range(4, 188):
    b.put(x, 2, 30, HAZ)
    b.put(x, 2, 31, FLOORGRATE)
    b.put(x, 2, 32, FLOORGRATE)
    b.put(x, 2, 33, HAZ)
    if x % 12 == 0:
        b.put(x, 3, 29, VCON); b.put(x, 3, 34, VCON)
for y, z0, z1 in ((16, 22, 25), (29, 38, 41)):
    for x in range(2, 190):
        for z in range(z0, z1+1):
            b.put(x, y, z, SLAB if (x+z) % 4 else FLOORGRATE)
        b.put(x, y+1, z0, PEDGE("south")); b.put(x, y+1, z1, PEDGE("north"))
    for x in range(8, 188, 16):
        b.put(x, y-1, z0, BRIDGE("south")); b.put(x, y-1, z1, BRIDGE("north"))

# Inter-module arch openings so the three halls read as one complex.
for seam in (63, 64, 127, 128):
    rect(seam, 2, 20, seam, 13, 44, AIR)
    for z in range(18, 47):
        b.put(seam, 14, z, GAW("east" if seam in (63,127) else "west"))

# ---------------------------------------------------------------- FOUNDry (west 0..63)
# Main cruciform/chamfered mass with uneven annexes.
chamfered_mass(5, 2, 8, 58, 36, 56, 6, ASH)
rect(5, 3, 8, 58, 35, 56, AIR)
hollow_box(0, 2, 15, 18, 23, 48, ASH, floor=METALF, roof=STEEL)
hollow_box(45, 2, 2, 63, 27, 22, ASH, floor=METALF, roof=STEEL)
# Exterior facades and staggered buttresses.
facade_z(8, 55, 3, 34, 8, "south", window_gap=7, relief_gap=12)
facade_z(8, 55, 3, 34, 56, "north", window_gap=9, relief_gap=13)
facade_x(11, 53, 3, 34, 5, "east", window_gap=8)
facade_x(11, 53, 3, 34, 58, "west", window_gap=8)
for x in (10, 24, 43, 55):
    buttress_z(x, 8, 2, 33, -1, "south")
for x in (16, 36, 52):
    buttress_z(x, 56, 2, 33, 1, "north")
# Thick layered roof and twin furnace towers.
gable_ns(7, 56, 10, 54, 37, rise=9)
for x0, x1, z0, z1, h in ((10, 24, 18, 34, 52), (36, 52, 28, 47, 55)):
    chamfered_mass(x0, 32, z0, x1, h, z1, 3, STEEL)
    facade_z(x0+2, x1-2, 34, h-1, z0, "south", window_gap=6, relief_gap=9)
    gable_ns(x0+1, x1-1, z0+1, z1-1, h+1, rise=4)
# Chimney clusters, varied height and thickness.
for x, z, top in ((8, 13, 58), (14, 11, 61), (25, 49, 55), (44, 51, 62), (55, 14, 57)):
    rect(x, 20, z, x+1, top, z+1, STACK)
    b.put(x, min(63, top+1), z, SPIRECAP)
# Furnaces, crucibles and molten process line.
for x in range(10, 55, 9):
    rect(x, 2, 49, x+5, 9, 54, CASING)
    b.put(x+1, 3, 48, FURNACE("north")); b.put(x+3, 3, 48, FURNACE("north"))
    b.put(x+2, 7, 48, LRED)
for x, z in ((14, 20), (25, 20), (36, 20), (47, 20), (20, 42), (42, 42)):
    rect(x-1, 2, z-1, x+1, 4, z+1, CASING)
    b.put(x, 5, z, CRUCIBLE)
for x in range(9, 56):
    b.put(x, 2, 36, CRUCIBLE if x % 3 else LRED)
    b.put(x, 1, 36, ARMOR)
# Foundry crane and control chapel.
rect(8, 26, 15, 56, 28, 17, GANTRY("east"))
for x in (18, 34, 50):
    b.put(x, 25, 16, ANCHOR("south"))
    for y in range(20, 25): b.put(x, y, 16, CHAIN)
hollow_box(43, 17, 8, 57, 25, 20, ASH, floor=METALF, roof=STEEL)
facade_z(44, 56, 18, 24, 8, "south", window_gap=5, relief_gap=7)
b.put(49, 18, 9, COG("south")); b.put(53, 18, 9, CTRL("south")); b.put(55, 21, 11, SHRINE("south"))
# Pipe spine and ducts.
pipe_x(3, 60, 12, 58, "east"); pipe_x(4, 57, 31, 5, "east")
pipe_z(10, 54, 23, 60, "south")
for z in range(12, 54, 8): b.put(6, 12, z, VENT("east"))

# ---------------------------------------------------------------- ASSEMBLY HALL (64..127)
# Long nave with three offset bays instead of one box.
hollow_box(68, 2, 7, 123, 33, 56, ASH, floor=METALF, roof=STEEL, thickness=2)
hollow_box(64, 2, 18, 78, 22, 48, ASH, floor=METALF, roof=STEEL)
hollow_box(111, 2, 2, 127, 27, 25, ASH, floor=METALF, roof=STEEL)
# Carve full interior after overlapping shells.
rect(70, 3, 9, 121, 32, 54, AIR)
# Facades and vertical rhythm.
facade_z(70, 121, 3, 31, 7, "south", window_gap=8, relief_gap=13)
facade_z(70, 121, 3, 31, 56, "north", window_gap=7, relief_gap=12)
facade_x(10, 53, 3, 31, 68, "east", window_gap=8)
facade_x(10, 53, 3, 31, 123, "west", window_gap=8)
for x in (73, 88, 104, 119):
    buttress_z(x, 7, 2, 31, -1, "south")
for x in (80, 98, 116):
    buttress_z(x, 56, 2, 31, 1, "north")
# Saw-tooth/gabled roof in three heavy bays with glowing clerestories.
for x0, x1, h in ((69, 85, 34), (86, 104, 38), (105, 122, 35)):
    gable_ew(x0, x1, 9, 54, h, rise=7)
    for z in range(16, 51, 7):
        b.put((x0+x1)//2, h+4, z, STAINED("east" if (z//7)%2 else "west"))
# Two conveyor lines and a transverse lift line.
for z in (18, 46):
    for x in range(72, 120):
        b.put(x, 2, z, CONVEYOR_X)
        b.put(x, 1, z, STEEL)
    for x in range(76, 119, 10):
        rect(x, 2, z-2, x, 9, z-2, FRAME); rect(x, 2, z+2, x, 9, z+2, FRAME)
        rect(x-1, 9, z-2, x+1, 9, z+2, GANTRY("north"))
        b.put(x, 6, z, PRESS); b.put(x-1, 8, z, LYEL)
for z in range(18, 47):
    b.put(96, 2, z, CONVEYOR_Z); b.put(96, 1, z, STEEL)
# Suspended bridge crane and hanging assemblies.
rect(71, 27, 12, 121, 29, 14, GANTRY("east"))
rect(71, 27, 49, 121, 29, 51, GANTRY("east"))
for x in range(76, 119, 8):
    b.put(x, 26, 32, ANCHOR("south"))
    for y in range(18, 26): b.put(x, y, 32, CHAIN)
    b.put(x, 17, 32, MACHINE("north"))
# Elevated supervisors' gallery and stair/lift access.
rect(72, 15, 10, 118, 15, 14, METALF)
for x in range(72, 119):
    b.put(x, 16, 14, PEDGE("north"))
    if x % 6 == 0: b.put(x, 16, 10, COG("south"))
stair_run_x(72, 84, 3, 15, "east")
for y in range(2, 30):
    b.put(121, y, 20, LIFT("west")); b.put(120, y, 20, LADDER_E)
# Damaged southeast annex, irregular void and rubble.
for dx in range(-9, 10):
    for dy in range(-8, 9):
        if dx*dx + dy*dy <= 70:
            for dz in range(0, 5):
                b.put(116+dx, 18+dy, 56-dz, AIR)
for _ in range(90):
    x = 105 + b.rng.randrange(21); z = 47 + b.rng.randrange(15)
    b.put(x, 2+b.rng.randrange(4), z, RUST if b.rng.random() < .65 else ASH_CR)
for x, z in ((108, 51), (117, 48), (121, 55)):
    b.put(x, 4, z, LRED); b.put(x, 5, z, VENT("north"))
# Service pipe/cable runs.
pipe_x(66, 126, 12, 59, "east")
pipe_z(8, 55, 24, 125, "south")
for x in range(72, 121, 9): b.put(x, 30, 54, CABLE("north"))

# ---------------------------------------------------------------- GENERATOR HALL (128..191)
# Large chamfered reactor basilica with asymmetric transformer annexes.
chamfered_mass(134, 2, 9, 187, 40, 56, 7, ASH)
rect(138, 3, 12, 183, 39, 53, AIR)
hollow_box(128, 2, 18, 143, 26, 47, ASH, floor=METALF, roof=STEEL)
hollow_box(177, 2, 2, 191, 31, 28, ASH, floor=METALF, roof=STEEL)
facade_z(138, 183, 3, 38, 9, "south", window_gap=7, relief_gap=11)
facade_z(138, 183, 3, 38, 56, "north", window_gap=8, relief_gap=13)
facade_x(13, 52, 3, 38, 134, "east", window_gap=7)
facade_x(13, 52, 3, 38, 187, "west", window_gap=7)
for x in (140, 153, 169, 182):
    buttress_z(x, 9, 2, 37, -1, "south")
for x in (146, 161, 177):
    buttress_z(x, 56, 2, 37, 1, "north")
# Broad two-stage crown and four short reactor pinnacles.
gable_ew(136, 185, 11, 54, 41, rise=8)
chamfered_mass(146, 47, 20, 175, 55, 45, 5, STEEL)
gable_ns(147, 174, 21, 44, 56, rise=5)
for x, z in ((143, 17), (180, 17), (143, 49), (180, 49)):
    rect(x, 36, z, x+1, 58, z+1, PILLAR)
    b.put(x, 59, z, SPIRECAP)
# Central reactor core — thick, ringed and readable from multiple levels.
chamfered_mass(151, 2, 23, 171, 34, 43, 4, CASING)
rect(155, 3, 27, 167, 33, 39, AIR)
for y in range(3, 34):
    rect(159, y, 31, 163, y, 35, TURBINE)
for ring_y in (4, 12, 20, 28):
    for x in range(151, 172):
        b.put(x, ring_y, 23, HAZ); b.put(x, ring_y, 43, HAZ)
    for z in range(23, 44):
        b.put(151, ring_y, z, HAZ); b.put(171, ring_y, z, HAZ)
for x, z in ((153,25),(169,25),(153,41),(169,41)):
    b.put(x, 4, z, LRED); b.put(x, 20, z, LGRN)
# Boiler banks and coolant towers.
for x in (139, 146, 176, 183):
    for z in (16, 48):
        rect(x, 2, z, x+1, 10, z+1, BOILER)
        b.put(x, 11, z, TJUNC("north"))
for x, z in ((142,31),(181,31),(160,14),(160,52)):
    rect(x, 2, z, x+1, 8, z+1, COOLANT)
    b.put(x, 1, z, LGRN)
# Control sanctuary, transformer yard and cable trunks.
hollow_box(136, 17, 11, 149, 26, 23, ASH, floor=METALF, roof=STEEL)
facade_z(137, 148, 18, 25, 11, "south", window_gap=5, relief_gap=6)
b.put(139, 18, 12, COG("south")); b.put(143, 18, 12, CTRL("south")); b.put(147, 20, 12, SHRINE("south"))
for x in range(178, 189, 4):
    rect(x, 2, 5, x+2, 12, 11, MACHINE("south"))
    b.put(x+1, 13, 8, VCON)
for z in (29, 34, 39):
    pipe_x(130, 151, 10, z, "east")
    pipe_x(171, 190, 10, z, "east")
for y in (18, 28, 38):
    pipe_z(12, 54, y, 188, "south")
# Upper reactor ring gallery.
for x in range(145, 179):
    for z in (18, 48):
        b.put(x, 29, z, SLAB)
        b.put(x, 30, z, PEDGE("north" if z == 48 else "south"))
for z in range(18, 49):
    for x in (145, 178):
        b.put(x, 29, z, SLAB)
        b.put(x, 30, z, PEDGE("east" if x == 145 else "west"))

# ---------------------------------------------------------------- Shared roof infrastructure and microdetail
# Heavy service canopy linking all three sectors.
for y, z in ((48, 6), (51, 31), (49, 58)):
    for x in range(5, 188):
        if x % 2 == 0:
            b.put(x, y, z, GANTRY("east"))
        else:
            b.put(x, y, z, PIPE("east"))
        if x % 12 == 0:
            b.put(x, y-1, z, PCLAMP("east")); b.put(x, y+1, z, VCON)
# Suspended lamps and shrines make the huge interiors readable.
for x in range(10, 188, 12):
    for z in (14, 32, 50):
        for y in range(36, 40): b.put(x, y, z, CHAIN)
        b.put(x, 35, z, LYEL if x % 24 else LRED)
for x, z, f in ((4,8,"east"),(60,54,"west"),(67,8,"east"),(124,54,"west"),(131,8,"east"),(188,54,"west")):
    b.put(x, 5, z, SHRINE(f)); b.put(x, 4, z+1 if z < 32 else z-1, BRAZIER)
# Clutter, hatches, floor damage and markers.
for _ in range(95):
    x = b.rng.randrange(4, 188); z = b.rng.randrange(4, 60)
    if any(abs(x-cx) < 8 for cx in (32,96,160)):
        continue
    b.put(x, 2, z, CRATE(["north","south","east","west"][b.rng.randrange(4)]))
for _ in range(75):
    x = b.rng.randrange(3, 189); z = b.rng.randrange(3, 61)
    if b.get(x, 1, z) != AIR:
        b.put(x, 1, z, BLOOD if b.rng.random() < .13 else (FVENT if b.rng.random() < .5 else HAZ))
for x, z, f in ((8,25,"east"),(52,39,"west"),(73,52,"north"),(116,13,"south"),(137,46,"east"),(184,22,"west")):
    b.put(x, 5, z, HATCH(f))

markers = [
    (20,2,18,"marker_worker_spawn"),(42,2,45,"marker_worker_spawn"),(50,18,12,"marker_commander_point"),
    (78,2,20,"marker_worker_spawn"),(108,2,44,"marker_worker_spawn"),(117,2,53,"marker_enemy_spawn"),
    (145,2,20,"marker_worker_spawn"),(178,2,46,"marker_worker_spawn"),(161,29,18,"marker_patrol_point"),
    (32,2,5,"marker_civil_spawn"),(96,2,5,"marker_civil_spawn"),(160,2,5,"marker_civil_spawn"),
    (30,16,24,"marker_patrol_point"),(95,29,40,"marker_patrol_point"),(160,20,24,"marker_defense_point"),
    (20,2,38,"marker_loot_point"),(98,2,32,"marker_loot_point"),(162,32,34,"marker_loot_point"),
]
for x,y,z,m in markers:
    b.put(x,y,z,MK(m))


# ---------------------------------------------------------------- slice and metadata
def slice_module(x0, sx=64, sy=64, sz=64):
    sub = ModuleBuilder(sx, sy, sz, seed=94004+x0)
    for (x,y,z), state in b.grid.items():
        if x0 <= x < x0+sx and 0 <= y < sy and 0 <= z < sz:
            sub.put(x-x0, y, z, state)
    return sub

modules = [
    ("industrial/foundry_01", 0),
    ("industrial/assembly_hall_01", 64),
    ("industrial/generator_hall_01", 128),
]
results = []
for rel, x0 in modules:
    sub = slice_module(x0)
    out = OUT / f"{rel}.nbt"
    nonair, pal, size = sub.write_nbt(str(out))
    sub.previews(str(PREV / rel.replace('/', '_')), plans=[(63,"plan_roof"),(32,"plan_mid"),(8,"plan_ground")], sections_x=[(31,"section_x31")], sections_z=[(31,"section_z31")])
    results.append((rel, nonair, pal, size))

for rel, _ in modules:
    meta = ROOT / "src/main/resources/data/firstcrusade/hive_modules" / f"{rel}.json"
    data = json.loads(meta.read_text(encoding="utf-8"))
    data["size"] = [64,64,64]
    data["description"] = "Manufactorum rebuild v2: layered gothic-industrial hall, continuous galleries, thick roofs and irregular massing."
    meta.write_text(json.dumps(data, indent=2, ensure_ascii=False)+"\n", encoding="utf-8")

# Full-district previews.
b.previews(str(PREV / "manufactorum_district_v2"), plans=[(63,"plan_roof"),(40,"plan_upper"),(24,"plan_gallery"),(8,"plan_ground")], sections_x=[(31,"section_foundry_x31"),(95,"section_assembly_x95"),(159,"section_generator_x159")], sections_z=[(31,"section_process_spine_z31"),(12,"section_front_z12")])

print("Manufactorum v2")
for rel, nonair, pal, size in results:
    print(f"{rel:34s} blocks={nonair:7d} palette={pal:3d} bytes={size:8d}")
print(f"combined visible states: {sum(1 for v in b.grid.values() if v != AIR)}")
print(PREV)
