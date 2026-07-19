#!/usr/bin/env python3
"""Administratum V2 — distrito contínuo 192x64x64.

Reconstrói scriptorium_01, cathedral_nave_01 e tribunal_01 como um único coroamento
urbano gótico-industrial. As três peças compartilham fundações, avenidas processionais,
galerias, arcobotantes, telhados grossos, torres agrupadas e passarelas para evitar o
aspecto de três caixas isoladas.
"""
from pathlib import Path
import json
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(Path(__file__).resolve().parent))
from hive_module_lib import ModuleBuilder, S, V, AIR, MK, RAIL_RAW  # noqa: E402

OUT = ROOT / "src/main/resources/data/firstcrusade/structures/hive"
PREV = ROOT / "tools/previews_administratum_v2"
PREV.mkdir(parents=True, exist_ok=True)


def H(name, facing="north"):
    return S(name, facing=facing)


# New Hive City blocks — structure
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

# New Hive City blocks — industrial
PIPE = lambda f="north": H("straight_pipe", f)
ELBOW = lambda f="north": H("elbow_pipe", f)
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

# New Hive City blocks — floors/light/details
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

# Existing supporting set
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
SST = lambda f="north", half="bottom": S("riveted_steel_stairs", facing=f, half=half)
AST = lambda f="north", half="bottom": S("reinforced_ashcrete_stairs", facing=f, half=half)
SSL = lambda t="bottom": S("riveted_steel_slab", type=t)
LADDER_E = V("ladder", facing="east")
LADDER_W = V("ladder", facing="west")

SX, SY, SZ = 192, 64, 64
b = ModuleBuilder(SX, SY, SZ, seed=96006)


def rect(x0, y0, z0, x1, y1, z1, block):
    b.fill(x0, y0, z0, x1, y1, z1, block)


def footprint(x0, z0, x1, z1, cut):
    pts = set()
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if ((x - x0) + (z - z0) < cut or (x1 - x) + (z - z0) < cut or
                    (x - x0) + (z1 - z) < cut or (x1 - x) + (z1 - z) < cut):
                continue
            pts.add((x, z))
    return pts


def chamfered_shell(x0, y0, z0, x1, y1, z1, cut, wall=ASH, floor=CATHF, roof=STEEL):
    pts = footprint(x0, z0, x1, z1, cut)
    for x, z in pts:
        b.put(x, y0, z, floor)
        b.put(x, y1, z, roof)
        boundary = any((x + dx, z + dz) not in pts for dx, dz in ((1,0),(-1,0),(0,1),(0,-1)))
        if boundary:
            rect(x, y0 + 1, z, x, y1 - 1, z, wall)
        else:
            rect(x, y0 + 1, z, x, y1 - 1, z, AIR)


def stepped_plinth(x0, z0, x1, z1, y0=1, levels=4, block=ASH):
    for i in range(levels):
        xa, za, xb, zb = x0 + i, z0 + i, x1 - i, z1 - i
        if xa > xb or za > zb:
            break
        rect(xa, y0 + i, za, xb, y0 + i, zb, block if i < levels - 1 else CATHF)


def facade_z(x0, x1, y0, y1, z, facing, window_every=7, lit_every=3):
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            if y in (y0, y1) or (y - y0) % 10 == 0:
                block = CORNICE(facing) if (y - y0) % 20 == 0 else MOLD(facing)
            elif x in (x0, x1) or (x - x0) % 8 == 0:
                block = PILLAR
            elif (x + y) % 5 == 0:
                block = SEAM(facing)
            else:
                block = ABW(facing) if (x + y) % 3 else RSP(facing)
            b.put(x, y, z, block)
    for i, x in enumerate(range(x0 + 3, x1 - 2, window_every)):
        for y in range(y0 + 4, y1 - 2, 9):
            b.put(x, y, z, GLOWWIN(facing) if i % lit_every == 0 else WIN(facing))
            if y + 1 <= y1 - 1:
                b.put(x, y + 1, z, LANCET(facing))
    for x in range(x0 + 5, x1 - 4, 14):
        b.put(x, min(y1 - 2, y0 + 15), z, TRI(facing))


def facade_x(z0, z1, y0, y1, x, facing, window_every=7):
    for z in range(z0, z1 + 1):
        for y in range(y0, y1 + 1):
            if y in (y0, y1) or (y - y0) % 10 == 0:
                block = CORNICE(facing) if (y - y0) % 20 == 0 else MOLD(facing)
            elif z in (z0, z1) or (z - z0) % 8 == 0:
                block = PILLAR
            elif (z + y) % 5 == 0:
                block = SEAM(facing)
            else:
                block = ABW(facing) if (z + y) % 3 else RSP(facing)
            b.put(x, y, z, block)
    for i, z in enumerate(range(z0 + 3, z1 - 2, window_every)):
        for y in range(y0 + 4, y1 - 2, 9):
            b.put(x, y, z, GLOWWIN(facing) if i % 3 == 0 else WIN(facing))
            if y + 1 <= y1 - 1:
                b.put(x, y + 1, z, LANCET(facing))


def gabled_roof_ns(x0, x1, z0, z1, y0, rise=8, thick=2):
    r = min(rise, max(1, (x1 - x0) // 2))
    for s in range(r + 1):
        xa, xb, yy = x0 + s, x1 - s, y0 + s
        if xa > xb:
            break
        for z in range(z0, z1 + 1):
            for t in range(thick):
                b.put(xa, yy + t, z, CATHSTAIR("east"))
                b.put(xb, yy + t, z, CATHSTAIR("west"))
    ridge = (x0 + x1) // 2
    rect(ridge - 1, y0 + r, z0, ridge + 1, y0 + r + thick, z1, STEEL)
    for z in range(z0 + 2, z1, 6):
        b.put(ridge, y0 + r + thick + 1, z, SPIRECAP)


def gabled_roof_ew(x0, x1, z0, z1, y0, rise=8, thick=2):
    r = min(rise, max(1, (z1 - z0) // 2))
    for s in range(r + 1):
        za, zb, yy = z0 + s, z1 - s, y0 + s
        if za > zb:
            break
        for x in range(x0, x1 + 1):
            for t in range(thick):
                b.put(x, yy + t, za, CATHSTAIR("south"))
                b.put(x, yy + t, zb, CATHSTAIR("north"))
    ridge = (z0 + z1) // 2
    rect(x0, y0 + r, ridge - 1, x1, y0 + r + thick, ridge + 1, STEEL)
    for x in range(x0 + 2, x1, 6):
        b.put(x, y0 + r + thick + 1, ridge, SPIRECAP)


def tower(x0, z0, x1, z1, h, cut=2, front="south", crown=5):
    chamfered_shell(x0, 2, z0, x1, h, z1, cut, ASH, CATHF, STEEL)
    if front in ("south", "north"):
        z = z0 if front == "south" else z1
        facade_z(x0 + 1, x1 - 1, 3, h - 1, z, front, 6)
        gabled_roof_ew(x0 + 1, x1 - 1, z0 + 1, z1 - 1, h + 1, crown, 2)
    else:
        x = x0 if front == "east" else x1
        facade_x(z0 + 1, z1 - 1, 3, h - 1, x, front, 6)
        gabled_roof_ns(x0 + 1, x1 - 1, z0 + 1, z1 - 1, h + 1, crown, 2)
    for cx, cz in ((x0 + cut, z0 + cut), (x1 - cut, z0 + cut), (x0 + cut, z1 - cut), (x1 - cut, z1 - cut)):
        rect(cx, h - 4, cz, cx + 1, h + crown + 2, cz + 1, PILLAR)
        b.put(cx, h + crown + 3, cz, SPIRECAP)


def buttress_z(x, z, y0, y1, facing, depth_sign):
    for y in range(y0, y1 + 1):
        depth = max(1, 4 - (y - y0) // 8)
        z2 = z + depth_sign * depth
        rect(x, y, min(z, z2), x + 1, y, max(z, z2), BUTT(facing) if y % 4 else BRIDGE(facing))
    b.put(x, y1 + 1, z, GARGOYLE(facing))


def buttress_x(x, z, y0, y1, facing, depth_sign):
    for y in range(y0, y1 + 1):
        depth = max(1, 4 - (y - y0) // 8)
        x2 = x + depth_sign * depth
        rect(min(x, x2), y, z, max(x, x2), y, z + 1, BUTT(facing) if y % 4 else BRIDGE(facing))
    b.put(x, y1 + 1, z, GARGOYLE(facing))


def skybridge_x(x0, x1, y, z0, z1, ornate=True):
    rect(x0, y, z0, x1, y, z1, CATW)
    for x in range(x0, x1 + 1):
        b.put(x, y + 1, z0, RAIL_RAW)
        b.put(x, y + 1, z1, RAIL_RAW)
        if ornate and x % 4 == 0:
            b.put(x, y + 2, z0, BALCONY("south"))
            b.put(x, y + 2, z1, BALCONY("north"))
    for x in range(x0 + 2, x1, 8):
        rect(x, y - 5, z0, x, y - 1, z1, BRIDGE("south"))
        b.put(x, y + 3, (z0 + z1) // 2, LYEL)


def skybridge_z(z0, z1, y, x0, x1, ornate=True):
    rect(x0, y, z0, x1, y, z1, CATW)
    for z in range(z0, z1 + 1):
        b.put(x0, y + 1, z, RAIL_RAW)
        b.put(x1, y + 1, z, RAIL_RAW)
        if ornate and z % 4 == 0:
            b.put(x0, y + 2, z, BALCONY("east"))
            b.put(x1, y + 2, z, BALCONY("west"))
    for z in range(z0 + 2, z1, 8):
        rect(x0, y - 5, z, x1, y - 1, z, BRIDGE("east"))
        b.put((x0 + x1) // 2, y + 3, z, LYEL)


def grand_stair_ns(x0, x1, z0, length, y0=2, north_to_south=True):
    for s in range(length):
        z = z0 + s if north_to_south else z0 - s
        y = y0 + s // 2
        rect(x0, y, z, x1, y, z, CATHSTAIR("south" if north_to_south else "north"))
        if s % 2 == 0:
            b.put(x0 - 1, y + 1, z, RAIL("east"))
            b.put(x1 + 1, y + 1, z, RAIL("west"))


def processional_axis(cx):
    # A broad, clear vertical route aligned with the city sockets.
    for z in range(SZ):
        for x in range(cx - 7, cx + 7):
            b.put(x, 1, z, CATHF if x not in (cx - 6, cx + 5) else METALF)
        for x in (cx - 5, cx + 4):
            b.put(x, 1, z, FLOORGRATE)
            b.put(x, 0, z, LGRN if z % 8 == 0 else ASH_CR)
        rect(cx - 7, 2, z, cx + 6, 10, z, AIR)
    for z in range(7, SZ, 13):
        b.put(cx - 8, 5, z, BRAZIER)
        b.put(cx + 7, 5, z, BRAZIER)


def archive_room(x0, z0, x1, z1, y):
    rect(x0, y, z0, x1, y + 5, z0, CATHW)
    rect(x0, y, z1, x1, y + 5, z1, CATHW)
    rect(x0, y, z0, x0, y + 5, z1, CATHW)
    rect(x1, y, z0, x1, y + 5, z1, CATHW)
    rect(x0 + 1, y, z0 + 1, x1 - 1, y + 4, z1 - 1, AIR)
    rect((x0 + x1) // 2, y, z0, (x0 + x1) // 2 + 1, y + 2, z0, AIR)
    for z in range(z0 + 2, z1 - 1, 2):
        b.put(x0 + 1, y, z, SHELF("east"))
        b.put(x1 - 1, y, z, SHELF("west"))
    b.put((x0 + x1) // 2, y, (z0 + z1) // 2, TABLE)
    b.put((x0 + x1) // 2, y, (z0 + z1) // 2 + 1, CHAIR("north"))
    b.put((x0 + x1) // 2 + 2, y + 1, z1 - 1, TERMINAL("north"))
    b.put((x0 + x1) // 2, y + 5, (z0 + z1) // 2, LAMP)


# ---------------------------------------------------------------- Common base and connective fabric
rect(0, 0, 0, SX - 1, 0, SZ - 1, ASH_CR)
for cx in (32, 96, 160):
    processional_axis(cx)
for z, y in ((17, 1), (32, 1), (48, 1), (32, 21)):
    rect(4, y, z - 2, 187, y, z + 2, CATHF if y == 1 else CATW)
    for x in range(4, 188):
        if x % 9 == 0:
            b.put(x, y, z - 2, FLOORGRATE)
            b.put(x, y, z + 2, HAZ if y > 1 else METALF)


# ================================================================= SCRIPTORIUM (0..63)
# A stepped archive fortress with two unequal book towers and a central record hall.
stepped_plinth(2, 3, 61, 60, 1, 4, ASH)
chamfered_shell(4, 5, 6, 59, 34, 57, 5, ASH, CATHF, STEEL)
rect(12, 6, 11, 51, 33, 52, AIR)
facade_z(8, 55, 6, 32, 6, "south", 7)
facade_z(8, 55, 6, 32, 57, "north", 7)
facade_x(10, 53, 6, 32, 4, "east", 7)
facade_x(10, 53, 6, 32, 59, "west", 7)

# Unequal archive towers and clustered crown.
tower(2, 4, 19, 25, 49, 3, "south", 5)
tower(43, 8, 62, 31, 43, 3, "south", 6)
tower(7, 40, 27, 63, 40, 3, "north", 5)
tower(39, 37, 59, 61, 53, 3, "north", 6)
gabled_roof_ns(11, 52, 12, 52, 35, 9, 3)

# Deep exterior buttresses, asymmetrical balconies, and pipe trunks.
for x in (8, 20, 44, 56):
    buttress_z(x, 6, 5, 28, "south", -1)
for z in (13, 27, 42, 53):
    buttress_x(4, z, 5, 27, "east", -1)
    buttress_x(59, z, 5, 27, "west", 1)
skybridge_x(18, 45, 24, 28, 35)
skybridge_x(24, 39, 38, 29, 34)
for z in range(9, 56):
    b.put(1, 17, z, PIPE("east"))
    if z % 8 == 0:
        b.put(2, 16, z, PCLAMP("east"))
for z in range(12, 55, 5):
    b.put(62, 25, z, CABLE("west"))

# Internal archive galleries and chambers.
for y in (6, 18, 30):
    rect(10, y, 11, 53, y, 52, CATHF)
    rect(15, y + 1, 16, 48, y + 5, 47, AIR)
    for x in (12, 23, 40, 51):
        rect(x, y + 1, 13, x, y + 9, 50, PILLAR)
    for z in (15, 28, 41, 50):
        skybridge_x(12, 52, y + 7, z, z + 1, ornate=False)
for y, rooms in (
    (6, [(7,10,18,20),(42,11,55,21),(7,42,19,54),(43,41,56,53)]),
    (18, [(8,12,20,23),(40,12,55,23),(10,40,22,52),(41,39,54,51)]),
    (30, [(9,13,22,25),(39,13,54,25),(10,38,23,50),(40,38,54,50)]),
):
    for x0,z0,x1,z1 in rooms:
        archive_room(x0,z0,x1,z1,y)

# Central data shrine / cogitator well.
rect(25, 5, 24, 38, 9, 39, CATHF)
rect(28, 10, 27, 35, 21, 36, FRAME)
for x,z in ((28,27),(35,27),(28,36),(35,36)):
    rect(x, 10, z, x, 22, z, VCON)
for z in range(28, 36, 2):
    b.put(29, 10, z, COG("east")); b.put(34, 10, z, CTRL("west"))
for y in range(22, 31):
    b.put(31, y, 31, CHAIN); b.put(32, y, 32, CHAIN)
b.put(31, 21, 31, BRAZIER); b.put(32, 21, 32, BRAZIER)
for x,z in ((6,8),(57,10),(8,55),(54,54)):
    b.put(x,35,z,GARGOYLE("south" if z < 32 else "north"))


# ================================================================= CATHEDRAL NAVE (64..127)
# Cross-shaped cathedral mass with thick lower body, broad transepts, twin front towers and a central lantern.
stepped_plinth(66, 2, 125, 61, 1, 5, ASH)
# Nave and transepts are separate but overlapping masses for a natural silhouette.
chamfered_shell(79, 6, 3, 112, 43, 60, 5, CATHW, CATHF, STEEL)
chamfered_shell(67, 8, 20, 124, 36, 45, 5, CATHW, CATHF, STEEL)
rect(84, 7, 8, 107, 42, 56, AIR)
rect(72, 9, 25, 119, 35, 40, AIR)

# Monumental façades.
facade_z(80, 111, 7, 41, 3, "south", 6, 2)
facade_z(80, 111, 7, 41, 60, "north", 6, 2)
facade_x(23, 42, 9, 34, 67, "east", 6)
facade_x(23, 42, 9, 34, 124, "west", 6)
# Giant portal and rose-window-like relief cluster.
rect(91, 7, 3, 100, 18, 3, DOOR("south"))
for x in range(87, 105):
    for y in range(21, 34):
        if abs(x - 96) + abs(y - 27) <= 8:
            b.put(x, y, 3, STAINED("south") if (x + y) % 3 else GLOWWIN("south"))
for x in (85, 107):
    rect(x, 8, 3, x + 1, 38, 3, PILLAR)
    b.put(x, 39, 3, GARGOYLE("south"))

# Twin front towers are large and unequal; rear chapel towers and central crossing lantern.
tower(66, 2, 84, 22, 50, 3, "south", 6)
tower(108, 4, 126, 24, 55, 3, "south", 6)
tower(68, 43, 84, 62, 41, 3, "north", 5)
tower(108, 42, 124, 61, 46, 3, "north", 5)
gabled_roof_ns(80, 111, 7, 57, 44, 10, 3)
gabled_roof_ew(70, 121, 22, 43, 37, 7, 3)
chamfered_shell(87, 46, 23, 104, 56, 42, 4, STEEL, STEEL, STEEL)
gabled_roof_ns(89, 102, 25, 40, 57, 4, 2)
for x,z in ((88,24),(102,24),(88,41),(102,41)):
    rect(x, 52, z, x + 1, 61, z + 1, PILLAR)
    b.put(x, 62, z, SPIRECAP)

# Flying buttresses and galleries.
for z in range(12, 57, 8):
    buttress_x(79, z, 11, 36, "east", -1)
    buttress_x(112, z, 11, 36, "west", 1)
for x in range(72, 121, 10):
    buttress_z(x, 20, 10, 29, "south", -1)
    buttress_z(x, 45, 10, 29, "north", 1)
skybridge_x(58, 80, 22, 29, 34)
skybridge_x(111, 135, 25, 29, 34)

# Nave interior: columns, arcades, galleries, chandeliers and benches.
for z in range(11, 56, 7):
    for x in (83, 87, 104, 108):
        rect(x, 7, z, x + 1, 37, z, PILLAR)
        b.put(x, 38, z, GARCH)
    rect(88, 34, z, 103, 34, z, SLAB)
    for xx in (88,103):
        b.put(xx, 35, z, RAIL("east" if xx == 88 else "west"))
for z in range(12, 49, 5):
    rect(89, 7, z, 93, 7, z, BENCH("north"))
    rect(99, 7, z, 103, 7, z, BENCH("north"))
for z in range(9, 57):
    b.put(95, 7, z, RUG); b.put(96, 7, z, RUG)
for z in (16, 29, 42, 54):
    for y in range(36, 44):
        b.put(95, y, z, CHAIN); b.put(96, y, z, CHAIN)
    b.put(95, 35, z, BRAZIER); b.put(96, 35, z, BRAZIER)
# Clerestory and stained transept windows.
for z in range(12, 57, 6):
    b.put(79, 30, z, STAINED("east")); b.put(112, 30, z, STAINED("west"))
for x in range(73, 120, 5):
    b.put(x, 27, 20, GLOWWIN("south")); b.put(x, 27, 45, STAINED("north"))

# High altar and monumental statuary at the north end.
stepped_plinth(86, 49, 106, 58, 7, 4, CATHF)
rect(91, 11, 53, 101, 14, 57, CATHF)
for p in range(3):
    b.put(88, 11 + p, 54, SAINT("east", p)); b.put(104, 11 + p, 54, SAINT("west", p))
    b.put(90, 11 + p, 56, GUARDIAN("north", p)); b.put(102, 11 + p, 56, GUARDIAN("north", p))
b.put(95, 15, 56, AQUILA_ST("south")); b.put(96, 15, 56, AQUILA_ST("south"))
b.put(91, 11, 51, BRAZIER); b.put(101, 11, 51, BRAZIER)
rect(88, 24, 60, 103, 37, 60, AQUILA)
for x in (68,75,117,124):
    b.put(x,20,32,BANNER("east" if x < 96 else "west",0))
    b.put(x,21,32,BANNER("east" if x < 96 else "west",1))


# ================================================================= TRIBUNAL (128..191)
# Broad stepped palace with central judgment hall and offset administrative wings.
stepped_plinth(130, 3, 189, 60, 1, 5, ASH)
chamfered_shell(132, 6, 7, 187, 36, 57, 5, ASH, CATHF, STEEL)
rect(141, 7, 12, 178, 35, 52, AIR)
facade_z(136, 183, 7, 34, 7, "south", 7)
facade_z(136, 183, 7, 34, 57, "north", 7)
facade_x(12, 52, 7, 34, 132, "east", 7)
facade_x(12, 52, 7, 34, 187, "west", 7)
# Central court block and asymmetric towers.
tower(129, 5, 146, 24, 43, 3, "south", 5)
tower(174, 3, 191, 27, 50, 3, "south", 6)
tower(132, 42, 151, 63, 47, 3, "north", 5)
tower(169, 40, 187, 61, 42, 3, "north", 5)
chamfered_shell(146, 22, 15, 175, 49, 50, 5, CATHW, CATHF, STEEL)
rect(151, 23, 20, 170, 48, 45, AIR)
gabled_roof_ns(147, 174, 17, 48, 50, 8, 3)

# Grand stair and tribunal entrance.
grand_stair_ns(152, 168, 3, 18, 2, True)
rect(154, 11, 21, 166, 20, 21, DOOR("south"))
for x in (148,172):
    rect(x, 7, 7, x + 1, 32, 7, PILLAR)
    b.put(x,33,7,GARGOYLE("south"))
for x in range(138,184,8):
    buttress_z(x, 7, 6, 28, "south", -1)
for z in range(14, 54, 8):
    buttress_x(132, z, 6, 27, "east", -1)
    buttress_x(187, z, 6, 27, "west", 1)

# Main judgment chamber: tiered seating, central dais, judge wall and galleries.
rect(146, 22, 16, 175, 22, 50, CATHF)
for i in range(5):
    y = 23 + i
    rect(148 + i, y, 20 + i * 2, 173 - i, y, 21 + i * 2, CATHSTAIR("north"))
    rect(148 + i, y, 45 - i * 2, 173 - i, y, 46 - i * 2, CATHSTAIR("south"))
for z in range(18, 49, 6):
    rect(148, 31, z, 173, 31, z, SLAB)
    b.put(148,32,z,RAIL("east")); b.put(173,32,z,RAIL("west"))
for x in (150,156,164,170):
    rect(x, 23, 18, x, 43, 48, PILLAR)
# Judgment dais at the north end.
stepped_plinth(153, 40, 168, 49, 23, 4, CATHF)
rect(156, 27, 44, 165, 29, 48, CATHF)
for p in range(3):
    b.put(153,27+p,46,GUARDIAN("east",p)); b.put(168,27+p,46,GUARDIAN("west",p))
b.put(160,30,47,AQUILA_ST("south")); b.put(158,27,43,BRAZIER); b.put(163,27,43,BRAZIER)
rect(153, 35, 50, 168, 44, 50, AQUILA)
b.put(160,36,50,SKULL("north"))
# Counsel tables, witness platform, and public benches.
for x,z,f in ((152,31,"east"),(169,31,"west"),(160,35,"north")):
    b.put(x,23,z,TABLE); b.put(x,23,z+1,CHAIR(f))
for z in range(25,39,4):
    rect(148,23,z,153,23,z,BENCH("north")); rect(167,23,z,172,23,z,BENCH("north"))

# Administrative wings and roof terrace.
for y, rooms in (
    (7, [(135,12,147,22),(174,13,185,23),(135,42,148,52),(173,41,185,51)]),
    (19, [(136,13,148,24),(173,14,184,25),(136,39,149,50),(172,39,184,50)]),
):
    for x0,z0,x1,z1 in rooms:
        archive_room(x0,z0,x1,z1,y)
rect(139, 36, 12, 184, 36, 54, SLAB)
for x in range(139,185):
    b.put(x,37,12,RAIL("south")); b.put(x,37,54,RAIL("north"))
for z in range(12,55):
    b.put(139,37,z,RAIL("east")); b.put(184,37,z,RAIL("west"))
for x,z in ((142,15),(181,16),(144,51),(179,50)):
    b.put(x,38,z,BRAZIER)

# Service infrastructure and bridges to the cathedral.
skybridge_x(121, 141, 26, 29, 34)
skybridge_x(116, 147, 39, 44, 49)
for z in range(10, 56):
    b.put(190, 16, z, PIPE("west"))
    if z % 8 == 0:
        b.put(189, 15, z, PCLAMP("west"))
for x in range(135, 186, 6):
    b.put(x, 39, 59, CABLE("north"))
for x,z in ((132,9),(187,10),(134,56),(185,55)):
    b.put(x,28,z,GARGOYLE("south" if z < 32 else "north"))


# ---------------------------------------------------------------- Shared microdetail, lighting and gameplay markers
for x in range(7, 186, 11):
    b.put(x, 4, 17, SCONCE("south"))
    b.put(x, 4, 48, SCONCE("north"))
for _ in range(115):
    x = b.rng.randrange(3,189); z = b.rng.randrange(4,60)
    if any(abs(x - cx) < 8 for cx in (32,96,160)):
        continue
    y = b.rng.choice((1,6,18,22,31,36))
    if b.get(x,y,z) == AIR:
        b.put(x,y,z,CRATE(["north","south","east","west"][b.rng.randrange(4)]) if b.rng.random() < .42 else SUPPLY())
for _ in range(80):
    x = b.rng.randrange(3,189); z = b.rng.randrange(3,61)
    if b.get(x,1,z) != AIR:
        r = b.rng.random()
        b.put(x,1,z,BLOOD if r < .05 else (FVENT if r < .48 else FLOORGRATE))

markers = [
    (15,6,16,"marker_civil_spawn"),(49,7,18,"marker_civil_spawn"),(31,6,31,"marker_commander_point"),
    (23,19,45,"marker_patrol_point"),(45,31,43,"marker_loot_point"),(31,2,8,"marker_guardsman_spawn"),
    (86,7,18,"marker_civil_spawn"),(106,7,36,"marker_civil_spawn"),(96,8,52,"marker_commander_point"),
    (82,22,31,"marker_patrol_point"),(110,22,31,"marker_patrol_point"),(96,7,8,"marker_guardsman_spawn"),
    (143,7,17,"marker_civil_spawn"),(181,8,18,"marker_civil_spawn"),(160,27,43,"marker_commander_point"),
    (151,23,31,"marker_defense_point"),(170,23,31,"marker_defense_point"),(160,2,8,"marker_guardsman_spawn"),
    (12,7,52,"marker_loot_point"),(117,9,54,"marker_loot_point"),(184,7,51,"marker_loot_point"),
]
for x,y,z,m in markers:
    b.put(x,y,z,MK(m))

b.resolve()


# ---------------------------------------------------------------- Slice into production module IDs

def slice_module(x0, sx=64, sy=64, sz=64):
    sub = ModuleBuilder(sx, sy, sz, seed=96006 + x0)
    for (x,y,z), state in b.grid.items():
        if x0 <= x < x0 + sx and 0 <= y < sy and 0 <= z < sz:
            sub.put(x - x0, y, z, state)
    return sub


modules = [
    ("admin/scriptorium_01", 0),
    ("admin/cathedral_nave_01", 64),
    ("admin/tribunal_01", 128),
]
results = []
for rel, x0 in modules:
    sub = slice_module(x0)
    nonair, pal, size = sub.write_nbt(str(OUT / f"{rel}.nbt"))
    sub.previews(str(PREV / rel.replace('/', '_')),
                 plans=[(63,"plan_roof"),(48,"plan_crown"),(32,"plan_gallery"),(20,"plan_upper"),(8,"plan_ground")],
                 sections_x=[(31,"section_x31")], sections_z=[(31,"section_z31")])
    results.append((rel, nonair, pal, size))

for rel, _ in modules:
    meta = ROOT / "src/main/resources/data/firstcrusade/hive_modules" / f"{rel}.json"
    data = json.loads(meta.read_text(encoding="utf-8"))
    data["size"] = [64,64,64]
    data["description"] = "Administratum rebuild v2: continuous gothic civic crown with thick stepped masses, clustered towers, flying buttresses, galleries and processional routes."
    meta.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

district = ROOT / "src/main/resources/data/firstcrusade/hive_districts/administratum.json"
ddata = json.loads(district.read_text(encoding="utf-8"))
ddata["description"] = "Administratum v2 (192x64x64): archive fortress, monumental cathedral crossing and broad tribunal palace joined by processional axes and elevated galleries."
district.write_text(json.dumps(ddata, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

b.previews(str(PREV / "administratum_district_v2"),
           plans=[(63,"plan_roof"),(51,"plan_crown"),(36,"plan_gallery"),(22,"plan_upper"),(8,"plan_ground")],
           sections_x=[(31,"section_scriptorium_x31"),(95,"section_cathedral_x95"),(159,"section_tribunal_x159")],
           sections_z=[(17,"section_front_z17"),(32,"section_spine_z32"),(48,"section_rear_z48")])

print("Administratum v2")
for rel, nonair, pal, size in results:
    print(f"{rel:32s} blocks={nonair:7d} palette={pal:3d} bytes={size:8d}")
print(f"combined visible states: {sum(1 for v in b.grid.values() if v != AIR)}")
print(PREV)
