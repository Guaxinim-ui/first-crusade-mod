#!/usr/bin/env python3
"""South Ash Gate + Cargo Ring v2.

Creates a unified 192x64x128 district and slices it into the six existing
64x64x64 modules. The silhouette is intentionally irregular and layered:
stepped walls, broad gatehouse, offset towers, deep buttresses, gabled roofs,
service gantries, cargo platforms and industrial pipe runs.
"""
from pathlib import Path
import json
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(Path(__file__).resolve().parent))
from hive_module_lib import ModuleBuilder, S, V, AIR, MK  # noqa: E402

OUT = ROOT / "src/main/resources/data/firstcrusade/structures/hive"
PREV = ROOT / "tools/previews_south_gate_v2"
PREV.mkdir(parents=True, exist_ok=True)


def H(name, facing="north"):
    return S(name, facing=facing)


# New Hive City block set
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

# Existing mass blocks remain useful behind detailed facades.
ASH = S("reinforced_ashcrete")
ASH_CR = S("cracked_reinforced_ashcrete")
STEEL = S("riveted_steel_block")
RUST = S("rusted_riveted_steel")
CHAIN = V("chain")
LADDER_N = V("ladder", facing="north")
LADDER_S = V("ladder", facing="south")
RAIL_TRACK_EW = V("rail", shape="east_west")
RAIL_TRACK_NS = V("rail", shape="north_south")

SX, SY, SZ = 192, 64, 128
b = ModuleBuilder(SX, SY, SZ, seed=83003)


def rect(x0, y0, z0, x1, y1, z1, block):
    b.fill(x0, y0, z0, x1, y1, z1, block)


def hollow_box(x0, y0, z0, x1, y1, z1, wall, floor=CATHF, roof=STEEL, thickness=1):
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
            if (x-x0)+(z-z0) < cut or (x1-x)+(z-z0) < cut or (x-x0)+(z1-z) < cut or (x1-x)+(z1-z) < cut:
                continue
            rect(x, y0, z, x, y1, z, block)


def facade_panel_z(x0, x1, y0, y1, z, facing, window_every=7, relief_every=13):
    for x in range(x0, x1+1):
        for y in range(y0, y1+1):
            if y in (y0, y1) or (y-y0) % 9 == 0:
                block = CORNICE(facing) if y % 2 else MOLD(facing)
            elif x in (x0, x1) or (x-x0) % 8 == 0:
                block = PILLAR
            else:
                block = ABW(facing) if (x+y) % 4 else RSP(facing)
            b.put(x, y, z, block)
    for x in range(x0+3, x1-2, window_every):
        if y1-y0 >= 12:
            b.put(x, y0+5, z, WIN(facing))
            b.put(x, y0+6, z, GLOWWIN(facing) if (x//window_every) % 2 else LANCET(facing))
    for x in range(x0+5, x1-4, relief_every):
        b.put(x, min(y1-3, y0+10), z, TRI(facing))


def facade_panel_x(z0, z1, y0, y1, x, facing, window_every=7):
    for z in range(z0, z1+1):
        for y in range(y0, y1+1):
            if y in (y0, y1) or (y-y0) % 9 == 0:
                block = CORNICE(facing) if y % 2 else MOLD(facing)
            elif z in (z0, z1) or (z-z0) % 8 == 0:
                block = PILLAR
            else:
                block = ABW(facing) if (z+y) % 4 else RSP(facing)
            b.put(x, y, z, block)
    for z in range(z0+3, z1-2, window_every):
        b.put(x, y0+5, z, WIN(facing))
        b.put(x, y0+6, z, LANCET(facing))


def gable_roof_ns(x0, x1, z0, z1, y0, rise=8):
    # Ridge runs north/south, slopes in X.
    max_rise = min(rise, max(1, (x1-x0)//2))
    for s in range(max_rise+1):
        xa, xb, y = x0+s, x1-s, y0+s
        if xa > xb:
            break
        for z in range(z0, z1+1):
            b.put(xa, y, z, STAIR("east"))
            b.put(xb, y, z, STAIR("west"))
            if s % 3 == 0:
                b.put(xa, y+1, z, CORNICE("east"))
                b.put(xb, y+1, z, CORNICE("west"))
    for x in range(x0+max_rise, x1-max_rise+1):
        for z in range(z0, z1+1):
            b.put(x, y0+max_rise+1, z, SPIRECAP if z % 3 == 0 else STEEL)


def gable_roof_ew(x0, x1, z0, z1, y0, rise=8):
    max_rise = min(rise, max(1, (z1-z0)//2))
    for s in range(max_rise+1):
        za, zb, y = z0+s, z1-s, y0+s
        if za > zb:
            break
        for x in range(x0, x1+1):
            b.put(x, y, za, STAIR("south"))
            b.put(x, y, zb, STAIR("north"))
            if s % 3 == 0:
                b.put(x, y+1, za, CORNICE("south"))
                b.put(x, y+1, zb, CORNICE("north"))
    for z in range(z0+max_rise, z1-max_rise+1):
        for x in range(x0, x1+1):
            b.put(x, y0+max_rise+1, z, SPIRECAP if x % 3 == 0 else STEEL)


def buttress_z(x, z, y0, y1, outward, facing):
    for y in range(y0, y1+1):
        depth = 4 if y < y0 + (y1-y0)//3 else 3 if y < y0 + 2*(y1-y0)//3 else 2
        for d in range(depth):
            b.put(x, y, z + outward*d, PILLAR if d == 0 else BUTT(facing))
    b.put(x, y1+1, z + outward*(depth-1), GARGOYLE(facing))


def buttress_x(z, x, y0, y1, outward, facing):
    for y in range(y0, y1+1):
        depth = 4 if y < y0 + (y1-y0)//3 else 3 if y < y0 + 2*(y1-y0)//3 else 2
        for d in range(depth):
            b.put(x + outward*d, y, z, PILLAR if d == 0 else BUTT(facing))
    b.put(x + outward*(depth-1), y1+1, z, GARGOYLE(facing))


def pipe_run_x(x0, x1, y, z, facing="east"):
    for x in range(x0, x1+1):
        b.put(x, y, z, PIPE(facing))
        if x % 8 == 0:
            b.put(x, y-1, z, PCLAMP(facing))
    b.put(x0, y, z, ELBOW("west"))
    b.put(x1, y, z, ELBOW("east"))


def pipe_run_z(z0, z1, y, x, facing="south"):
    for z in range(z0, z1+1):
        b.put(x, y, z, PIPE(facing))
        if z % 8 == 0:
            b.put(x, y-1, z, PCLAMP(facing))
    b.put(x, y, z0, ELBOW("north"))
    b.put(x, y, z1, ELBOW("south"))


# ---------------------------------------------------------------- Ground and shared roads
rect(0, 0, 0, SX-1, 0, SZ-1, ASH_CR)
for x in range(SX):
    for z in range(SZ):
        if (x*7 + z*11) % 29 == 0:
            b.put(x, 1, z, METALF)
        else:
            b.put(x, 1, z, CATHF if z < 80 else ASH)

# Main north-south road aligns with central 64-wide module local x=25..38.
for z in range(SZ):
    for x in range(89, 103):
        b.put(x, 1, z, METALF)
    for x in (92, 93, 98, 99):
        b.put(x, 1, z, FLOORGRATE)
    for x in (88, 103):
        b.put(x, 1, z, HAZ)

# Cargo rail line, east-west.
for x in range(4, 188):
    b.put(x, 1, 24, METALF); b.put(x, 2, 24, RAIL_TRACK_EW)
    b.put(x, 1, 29, METALF); b.put(x, 2, 29, RAIL_TRACK_EW)
    if x % 10 == 0:
        b.put(x, 1, 23, FVENT); b.put(x, 1, 30, FVENT)

# ---------------------------------------------------------------- Perimeter wall, irregular stepped profile
# Mass and inner corridors.
rect(0, 2, 82, 191, 38, 111, ASH)
rect(0, 39, 84, 191, 44, 109, STEEL)
rect(0, 7, 91, 191, 13, 101, AIR)   # lower service corridor
rect(0, 25, 91, 191, 30, 101, AIR)  # upper gallery
for x in range(0, 192):
    b.put(x, 6, 96, FLOORGRATE)
    b.put(x, 24, 96, FLOORGRATE)
    if x % 4 == 0:
        b.put(x, 29, 101, WIN("north"))

# Exterior and interior facades.
facade_panel_z(0, 191, 2, 43, 111, "north", window_every=9, relief_every=17)
facade_panel_z(0, 191, 2, 38, 82, "south", window_every=11, relief_every=19)

# Top walkways vary in depth and height.
for x in range(0, 192):
    for z in range(84, 110):
        if (x//16) % 2 == 0 or 88 <= x <= 104:
            b.put(x, 45, z, SLAB)
    b.put(x, 46, 84, BALCONY("south"))
    b.put(x, 46, 109, BALCONY("north"))
    if x % 3 == 0:
        b.put(x, 44, 84, BRIDGE("south"))
        b.put(x, 44, 109, BRIDGE("north"))

# Wall buttresses and small towers, staggered rather than perfectly uniform.
for x in (8, 24, 47, 144, 166, 184):
    buttress_z(x, 111, 2, 39, 1, "north")
for x in (15, 37, 55, 136, 154, 176):
    buttress_z(x, 82, 2, 34, -1, "south")

for x0, x1, h in ((3, 20, 54), (29, 48, 49), (143, 162, 52), (171, 188, 56)):
    chamfered_mass(x0, 39, 86, x1, h, 108, 4, ASH)
    facade_panel_z(x0+2, x1-2, 40, h-1, 109, "north", window_every=6, relief_every=10)
    facade_panel_z(x0+2, x1-2, 40, h-1, 85, "south", window_every=7, relief_every=11)
    gable_roof_ns(x0+1, x1-1, 87, 107, h+1, rise=6)

# A damaged, asymmetrical wall sector on the far east.
for dx in range(-7, 8):
    for dy in range(-6, 7):
        if dx*dx + dy*dy <= 45:
            for dz in range(0, 4):
                b.put(166+dx, 18+dy, 111-dz, AIR)
for k in range(70):
    x = 156 + b.rng.randrange(25); z = 112 + b.rng.randrange(14)
    b.put(x, 2 + b.rng.randrange(3), z, RUST if k % 3 else ASH_CR)

# ---------------------------------------------------------------- Monumental central gatehouse
# Broad flanking towers and layered central mass.
chamfered_mass(58, 2, 75, 82, 59, 116, 5, ASH)
chamfered_mass(109, 2, 75, 133, 61, 116, 5, ASH)
chamfered_mass(77, 2, 78, 114, 48, 116, 6, ASH)
chamfered_mass(84, 43, 83, 107, 57, 111, 5, STEEL)

# Carve the road tunnel and gate chamber.
rect(83, 2, 78, 108, 27, 118, AIR)
for x in range(83, 109):
    b.put(x, 1, 78, METALF)
    for z in range(78, 119):
        b.put(x, 1, z, METALF if x not in (88, 103) else HAZ)
# Broad pointed arch stepped in from both sides.
for y in range(2, 30):
    inset = max(0, (y-18)//2)
    lx, rx = 80+inset, 111-inset
    if lx < 83:
        rect(lx, y, 111, 82, y, 115, GAW("north"))
    if rx > 108:
        rect(109, y, 111, rx, y, 115, GAW("north"))
for x in range(80, 112):
    b.put(x, 30 + abs(96-x)//4, 114, CORNICE("north"))

# Exterior gate facade details.
facade_panel_z(61, 79, 4, 54, 116, "north", window_every=6, relief_every=9)
facade_panel_z(112, 130, 4, 56, 116, "north", window_every=6, relief_every=9)
facade_panel_z(79, 112, 31, 46, 116, "north", window_every=7, relief_every=11)
for x in range(86, 106, 4):
    b.put(x, 38, 116, TRI("north"))
for x in (62, 78, 113, 129):
    buttress_z(x, 116, 2, 52, 1, "north")

# Gate leaves recessed in the chamber; opening remains traversable.
for x in range(83, 109):
    if x in (83, 84, 107, 108):
        for y in range(2, 24):
            b.put(x, y, 104, DOOR("north"))
for x in range(87, 105):
    b.put(x, 25, 104, CHAIN)
    if x % 4 == 0:
        b.put(x, 26, 104, ANCHOR("north"))

# Murder-hole gallery and command shrine above gate.
rect(82, 31, 90, 109, 40, 108, ASH)
rect(84, 32, 92, 107, 39, 106, AIR)
for x in range(85, 107):
    b.put(x, 31, 95, FLOORGRATE)
    b.put(x, 31, 102, FLOORGRATE)
for x in (86, 91, 100, 105):
    b.put(x, 35, 107, SCONCE("north"))
rect(92, 32, 104, 99, 36, 107, SHRINE("north"))
b.put(91, 32, 105, BRAZIER); b.put(100, 32, 105, BRAZIER)

# Integrated roofs and crown; broad multiple pinnacles instead of one needle.
gable_roof_ns(59, 81, 76, 115, 55, rise=8)
gable_roof_ns(110, 132, 76, 115, 57, rise=8)
gable_roof_ew(80, 111, 82, 114, 49, rise=8)
for x, z, base in ((65, 83, 58), (76, 104, 58), (115, 104, 60), (126, 83, 60), (88, 87, 58), (103, 87, 58)):
    for y in range(base, min(63, base+5)):
        b.put(x, y, z, PILLAR)
    b.put(x, min(63, base+5), z, SPIRECAP)

# Inner gatehouse face and balconies.
facade_panel_z(62, 129, 4, 38, 75, "south", window_every=8, relief_every=13)
for x in range(63, 129):
    b.put(x, 39, 76, SLAB)
    b.put(x, 40, 76, BALCONY("south"))
    if x % 4 == 0:
        b.put(x, 38, 76, BRIDGE("south"))

# ---------------------------------------------------------------- Warehouse — west cargo block
hollow_box(4, 2, 5, 59, 29, 58, ASH, floor=METALF, roof=STEEL, thickness=2)
# Uneven annexes create a less cubic footprint.
hollow_box(0, 2, 14, 15, 18, 46, ASH, floor=METALF, roof=STEEL)
hollow_box(46, 2, 0, 63, 22, 22, ASH, floor=METALF, roof=STEEL)
# Cargo doors and side arcade.
rect(59, 3, 18, 59, 12, 34, AIR)
for y in range(3, 13):
    b.put(58, y, 17, DOOR("east")); b.put(58, y, 35, DOOR("east"))
for z in range(9, 56, 8):
    b.put(4, 8, z, GAW("east")); b.put(4, 9, z, WIN("east"))
# Facades.
facade_panel_z(5, 58, 3, 27, 5, "south", window_every=8, relief_every=13)
facade_panel_z(5, 58, 3, 27, 58, "north", window_every=8, relief_every=13)
facade_panel_x(6, 57, 3, 27, 4, "east", window_every=9)
facade_panel_x(6, 57, 3, 27, 59, "west", window_every=9)
gable_roof_ns(5, 58, 6, 57, 30, rise=10)
# Interior racks and office mezzanine.
for x in (12, 24, 36, 48):
    for z in range(10, 53, 6):
        rect(x, 2, z, x, 9, z, FRAME)
        b.put(x+1, 3, z, CRATE("south"))
        if z % 12 == 0:
            b.put(x+1, 6, z, CRATE("south"))
rect(7, 14, 39, 25, 14, 55, METALF)
for x in range(7, 26):
    b.put(x, 15, 39, PEDGE("south"))
for i in range(11):
    b.put(26-i, 3+i, 38, STAIR("west"))
# Pipes, vents and varied lighting.
pipe_run_z(8, 54, 12, 52, "south")
pipe_run_x(8, 50, 18, 10, "east")
for z in range(10, 56, 10):
    b.put(58, 14, z, VENT("east"))
    b.put(6, 5, z, SCONCE("west"))

# ---------------------------------------------------------------- Open cargo yard — center
# Platforms around rails and main road.
rect(67, 2, 8, 86, 4, 20, METALF)
rect(105, 2, 8, 124, 4, 20, METALF)
rect(67, 2, 33, 86, 4, 54, METALF)
rect(105, 2, 33, 124, 4, 54, METALF)
for x in list(range(67, 87)) + list(range(105, 125)):
    b.put(x, 5, 20, PEDGE("north")); b.put(x, 5, 33, PEDGE("south"))
# Large offset gantry crane, with varied leg positions.
for x, z in ((70, 16), (70, 39), (118, 14), (118, 42)):
    rect(x, 2, z, x+2, 22, z+2, FRAME)
rect(70, 22, 16, 120, 24, 18, GANTRY("east"))
rect(70, 22, 39, 120, 24, 41, GANTRY("east"))
for x in range(76, 116, 8):
    b.put(x, 21, 28, ANCHOR("south"))
    for y in range(12, 21):
        b.put(x, y, 28, CHAIN)
# Suspended cargo pod.
rect(92, 8, 25, 99, 12, 31, CRATE("north"))
# Customs scanner arch over road.
rect(86, 2, 46, 88, 17, 48, FRAME)
rect(103, 2, 46, 105, 17, 48, FRAME)
rect(86, 17, 46, 105, 19, 48, GANTRY("east"))
for x in range(90, 102, 3):
    b.put(x, 16, 47, GLOWWIN("north"))
# Container stacks arranged in clusters, not a strict grid.
clusters = [
    (69, 37, 3, "east"), (74, 43, 2, "south"), (80, 49, 1, "west"),
    (108, 7, 2, "north"), (114, 12, 3, "west"), (120, 7, 1, "south"),
]
for x0, z0, h, f in clusters:
    for yy in range(h):
        rect(x0 + (yy % 2), 2+yy, z0, x0+3+(yy % 2), 2+yy, z0+2, CRATE(f))
# Yard control chapel/office.
hollow_box(108, 2, 44, 124, 16, 60, ASH, floor=CATHF, roof=STEEL)
facade_panel_z(109, 123, 3, 15, 44, "south", window_every=5, relief_every=7)
gable_roof_ns(109, 123, 45, 59, 17, rise=5)
b.put(116, 4, 44, SHRINE("south")); b.put(113, 3, 45, BRAZIER); b.put(119, 3, 45, BRAZIER)
# Yard pipe rack and floodlight masts.
pipe_run_x(65, 126, 28, 59, "east")
for x, z in ((68, 7), (83, 57), (108, 7), (123, 57)):
    rect(x, 2, z, x, 13, z, VCON)
    b.put(x, 14, z, GLOWWIN("north"))

# ---------------------------------------------------------------- Military depot — east cargo block
hollow_box(132, 2, 7, 187, 31, 58, ASH, floor=METALF, roof=STEEL, thickness=2)
# Corner arsenal tower and chapel-like roof.
chamfered_mass(156, 2, 31, 189, 43, 61, 5, ASH)
facade_panel_z(159, 186, 3, 40, 61, "north", window_every=7, relief_every=9)
facade_panel_x(34, 58, 3, 40, 189, "west", window_every=7)
gable_roof_ns(158, 187, 32, 60, 44, rise=9)
# Main depot facades and loading bay.
facade_panel_z(133, 186, 3, 29, 7, "south", window_every=8, relief_every=13)
facade_panel_z(133, 155, 3, 29, 58, "north", window_every=7, relief_every=11)
rect(132, 3, 17, 132, 13, 34, AIR)
for y in range(3, 14):
    b.put(133, y, 16, DOOR("west")); b.put(133, y, 35, DOOR("west"))
# Interior armored vault and underhive shaft.
rect(160, 2, 34, 184, 17, 56, ABW("north"))
rect(163, 3, 37, 181, 15, 53, AIR)
rect(171, 1, 44, 178, 1, 51, FLOORGRATE)
rect(173, 0, 46, 176, 0, 49, AIR)
for x, z in ((171, 44), (178, 44), (171, 51), (178, 51)):
    rect(x, 2, z, x, 14, z, LIFT("north"))
for y in range(2, 15):
    b.put(172, y, 45, LADDER_S)
# Weapon/storage cages and guard balcony.
for x0, z0 in ((138, 12), (150, 12), (138, 38)):
    for x in range(x0, x0+9):
        b.put(x, 2, z0, RAIL("south")); b.put(x, 2, z0+8, RAIL("north"))
    for z in range(z0, z0+9):
        b.put(x0, 2, z, RAIL("east")); b.put(x0+8, 2, z, RAIL("west"))
    b.put(x0+4, 2, z0, AIR)
    b.put(x0+2, 2, z0+3, CRATE("south")); b.put(x0+5, 2, z0+5, MACHINE("north"))
rect(135, 19, 10, 181, 19, 14, METALF)
for x in range(135, 182):
    b.put(x, 20, 14, PEDGE("north"))
for i in range(14):
    b.put(181-i, 4+i, 15, STAIR("west"))
# Industrial systems and warning details.
pipe_run_z(10, 55, 24, 184, "south")
for z in range(12, 55, 8):
    b.put(133, 16, z, VENT("west"))
for x, z in ((140, 5), (180, 5), (140, 56), (180, 56)):
    b.put(x, 3, z, BRAZIER)

# ---------------------------------------------------------------- Street clutter, bridges and micro-detail
# Two overhead links between cargo buildings, different heights/widths.
for x0, x1, y, z in ((55, 70, 14, 12), (121, 136, 20, 47)):
    rect(x0, y, z, x1, y+1, z+3, GANTRY("east"))
    for x in range(x0, x1+1):
        b.put(x, y+2, z, PEDGE("south")); b.put(x, y+2, z+3, PEDGE("north"))
# Randomized crates, floor blood, hatches and vents in sensible zones.
for _ in range(55):
    x = b.rng.randrange(6, 186); z = b.rng.randrange(5, 76)
    if 87 <= x <= 104:
        continue
    block = CRATE(["north", "south", "east", "west"][b.rng.randrange(4)])
    b.put(x, 2, z, block)
for _ in range(40):
    x = b.rng.randrange(4, 188); z = b.rng.randrange(3, 78)
    if b.get(x, 1, z) != AIR:
        b.put(x, 1, z, BLOOD if b.rng.random() < 0.22 else (FVENT if b.rng.random() < 0.5 else HAZ))
for x, z, f in ((8, 40, "east"), (52, 21, "west"), (74, 55, "north"), (115, 35, "south"), (148, 20, "east"), (182, 27, "west")):
    b.put(x, 4, z, HATCH(f))

# ---------------------------------------------------------------- Gameplay markers
markers = [
    (94, 2, 5, "marker_patrol_point"), (94, 2, 68, "marker_patrol_point"),
    (88, 2, 105, "marker_guardsman_spawn"), (103, 2, 105, "marker_guardsman_spawn"),
    (67, 47, 96, "marker_defense_point"), (124, 49, 96, "marker_defense_point"),
    (18, 3, 28, "marker_worker_spawn"), (76, 3, 38, "marker_worker_spawn"),
    (112, 3, 17, "marker_worker_spawn"), (147, 3, 27, "marker_guardsman_spawn"),
    (174, 3, 48, "marker_loot_point"), (119, 3, 50, "marker_trade_point"),
    (95, 33, 102, "marker_commander_point"), (166, 3, 118, "marker_enemy_spawn"),
]
for x, y, z, m in markers:
    b.put(x, y, z, MK(m))

# ---------------------------------------------------------------- Slice into the six existing module ids.
def slice_module(x0, z0, sx=64, sy=64, sz=64):
    sub = ModuleBuilder(sx, sy, sz, seed=83003 + x0 + z0)
    for (x, y, z), state in b.grid.items():
        if x0 <= x < x0+sx and z0 <= z < z0+sz and 0 <= y < sy:
            sub.put(x-x0, y, z-z0, state)
    return sub

modules = [
    ("cargo/warehouse_01", 0, 0),
    ("cargo/cargo_yard_01", 64, 0),
    ("cargo/military_depot_01", 128, 0),
    ("gates/hive_wall_w_01", 0, 64),
    ("gates/south_ash_gate_01", 64, 64),
    ("gates/hive_wall_e_01", 128, 64),
]
results = []
for rel, x0, z0 in modules:
    sub = slice_module(x0, z0)
    out = OUT / f"{rel}.nbt"
    nonair, pal, size = sub.write_nbt(str(out))
    sub.previews(str(PREV / rel.replace('/', '_')), plans=[(63, "plan")], sections_x=[(31, "section_x31")], sections_z=[(31, "section_z31")])
    results.append((rel, nonair, pal, size))

# Update module heights to the actually generated 64 blocks.
for rel, _, _ in modules:
    meta = ROOT / "src/main/resources/data/firstcrusade/hive_modules" / f"{rel}.json"
    data = json.loads(meta.read_text(encoding="utf-8"))
    data["size"] = [64, 64, 64]
    data["description"] = "Hive City rebuild v2: layered gothic-industrial module with natural stepped silhouette."
    meta.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

# Full-district technical previews.
b.previews(str(PREV / "south_ash_gate_district_v2"), plans=[(63, "plan_roof"), (30, "plan_gallery"), (8, "plan_ground")], sections_x=[(95, "section_gate_x95"), (31, "section_warehouse_x31"), (159, "section_depot_x159")], sections_z=[(96, "section_wall_z96"), (24, "section_rail_z24")])

print("South Ash Gate + Cargo Ring v2")
for rel, nonair, pal, size in results:
    print(f"{rel:32s} blocks={nonair:7d} palette={pal:3d} bytes={size:8d}")
print(f"combined visible states: {sum(1 for v in b.grid.values() if v != AIR)}")
print(PREV)
