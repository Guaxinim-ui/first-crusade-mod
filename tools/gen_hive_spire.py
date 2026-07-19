#!/usr/bin/env python3
"""Generate Hive City Spire v2 — broad, terraced, cathedral-industrial silhouette.

The v1 spire read as a square box with a needle on top.  This revision uses a
96x128x96 cruciform/octagonal footprint, several stepped masses, integrated
side towers, deep buttresses, transepts, chapels, roofs and a broad crown.
"""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(Path(__file__).resolve().parent))
from hive_module_lib import ModuleBuilder, S, V, AIR, MK  # noqa: E402

OUT = ROOT / "src/main/resources/data/firstcrusade/structures/hive/spire/spire_crown_01.nbt"
PREV = ROOT / "tools/previews_spire_v2"
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
MOLDING = lambda f="north": H("lower_wall_molding", f)
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
CROSS = lambda f="north": H("cross_pipe_junction", f)
PCLAMP = lambda f="north": H("pipe_support_clamp", f)
VCON = S("vertical_service_conduit")
CABLE = lambda f="north": H("cable_bundle_block", f)
VENT = lambda f="north": H("vent_outlet", f)
FVENT = S("floor_vent")
LIFT = lambda f="north": H("lift_rail", f)
GANTRY = lambda f="north": H("gantry_beam", f)
ANCHOR = lambda f="north": H("suspended_track_anchor", f)
HATCH = lambda f="north": H("maintenance_hatch", f)
MACHINE = lambda f="north": H("machine_casing", f)
HAZ = S("hazard_grated_floor")
PEDGE = lambda f="north": H("reinforced_platform_edge", f)

# Detail set
GLOWWIN = lambda f="north": H("glowing_shrine_window", f)
STAINED = lambda f="north": H("stained_window_variant", f)
CANDLE = lambda f="north": H("candle_alcove", f)
SCONCE = lambda f="north": H("wall_sconce", f)
SHRINE = lambda f="north": H("shrine_recess", f)
CATHF = S("cathedral_floor_tile")
METALF = S("metal_floor_plate")
FLOORGRATE = S("floor_grate")
STAIR = lambda f="north": H("cathedral_stair_block", f)
SLAB = S("landing_slab")
RAIL = lambda f="north": H("balustrade_railing", f)
SKULL = lambda f="north": H("skull_relief_panel", f)
GARGOYLE = lambda f="north": H("gargoyle_pedestal", f)
CRATE = S("industrial_crate")
BRAZIER = S("brazier_block")

CHAIN = V("chain")
ASH = S("reinforced_ashcrete")
STEEL = S("riveted_steel_block")

SX, SY, SZ = 96, 128, 96
b = ModuleBuilder(SX, SY, SZ, seed=72002)


def rect_contains(x, z, x0, z0, x1, z1):
    return x0 <= x <= x1 and z0 <= z <= z1


def oct_contains(x, z, x0, z0, x1, z1, cut):
    if not rect_contains(x, z, x0, z0, x1, z1):
        return False
    # Distance from each rectangular corner.  The sum test creates 45-degree chamfers.
    if (x - x0) + (z - z0) < cut:
        return False
    if (x1 - x) + (z - z0) < cut:
        return False
    if (x - x0) + (z1 - z) < cut:
        return False
    if (x1 - x) + (z1 - z) < cut:
        return False
    return True


def lower_shape(x, z):
    core = oct_contains(x, z, 8, 8, 87, 87, 13)
    south = rect_contains(x, z, 29, 2, 66, 18)
    north = rect_contains(x, z, 27, 77, 68, 93)
    west = rect_contains(x, z, 2, 29, 18, 66)
    east = rect_contains(x, z, 77, 27, 93, 68)
    return core or south or north or west or east


def middle_shape(x, z):
    # A concave cathedral cross.  Deliberately no large square/octagonal core:
    # the missing corner volumes keep this level from reading as another box.
    nave = rect_contains(x, z, 30, 10, 65, 85)
    transept = rect_contains(x, z, 10, 31, 85, 64)
    north_apse = oct_contains(x, z, 25, 67, 70, 89, 7)
    south_narthex = oct_contains(x, z, 27, 6, 68, 27, 6)
    return nave or transept or north_apse or south_narthex


def upper_shape(x, z):
    # Narrower cruciform citadel above the roofline, with pronounced recesses.
    crossing = oct_contains(x, z, 29, 28, 66, 67, 6)
    nave = rect_contains(x, z, 37, 17, 58, 78)
    transept = rect_contains(x, z, 18, 38, 77, 57)
    return crossing or nave or transept


def crown_shape(x, z):
    return oct_contains(x, z, 30, 29, 65, 66, 6)


def lantern_shape(x, z, inset=0):
    return oct_contains(x, z, 38 + inset, 37 + inset, 57 - inset, 58 - inset, max(2, 4 - inset // 2))


def cardinal_facing(x, z, shape_fn):
    """Facing of the nearest exposed side, suitable for a wall-mounted model."""
    sides = []
    if not shape_fn(x, z - 1):
        sides.append("south")
    if not shape_fn(x, z + 1):
        sides.append("north")
    if not shape_fn(x - 1, z):
        sides.append("east")
    if not shape_fn(x + 1, z):
        sides.append("west")
    if sides:
        return sides[0]
    return "north"


def boundary(shape_fn, x, z, thickness=1):
    if not shape_fn(x, z):
        return False
    for d in range(1, thickness + 1):
        if (not shape_fn(x + d, z) or not shape_fn(x - d, z) or
                not shape_fn(x, z + d) or not shape_fn(x, z - d)):
            return True
    return False


def fill_floor(y, shape_fn, inset_fn=None, grate_period=0):
    for x in range(SX):
        for z in range(SZ):
            if shape_fn(x, z) and (inset_fn is None or inset_fn(x, z)):
                if grate_period and (x * 3 + z * 5) % grate_period == 0:
                    b.put(x, y, z, FLOORGRATE)
                else:
                    b.put(x, y, z, CATHF if (x + z) % 4 else METALF)


def shell(shape_fn, y0, y1, band_levels=(), window_levels=(), buttress_period=9,
          base_block=ABW, alt_block=RSP):
    for y in range(y0, y1 + 1):
        for x in range(SX):
            for z in range(SZ):
                if not boundary(shape_fn, x, z, 2):
                    continue
                f = cardinal_facing(x, z, shape_fn)
                # Heavy horizontal bands break the tower into readable storeys.
                if y in band_levels:
                    block = CORNICE(f) if y % 2 else MOLDING(f)
                elif y % 10 == 0:
                    block = SEAM(f)
                else:
                    block = base_block(f) if (x + z + y) % 5 else alt_block(f)
                b.put(x, y, z, block)

        # Deep exterior ribs / buttresses at changing intervals.
        if buttress_period and y % 2 == 0:
            for x in range(8, 88, buttress_period):
                for z, outward, f in ((2, -1, "south"), (93, 1, "north")):
                    if shape_fn(x, z):
                        for dz in range(0, 4):
                            zz = z + outward * dz
                            b.put(x, y, zz, PILLAR if dz == 0 else BUTT(f))
            for z in range(8, 88, buttress_period):
                for x, outward, f in ((2, -1, "east"), (93, 1, "west")):
                    if shape_fn(x, z):
                        for dx in range(0, 4):
                            xx = x + outward * dx
                            b.put(xx, y, z, PILLAR if dx == 0 else BUTT(f))


def ring_balcony(y, shape_fn, outer_shape_fn=None):
    for x in range(SX):
        for z in range(SZ):
            if not boundary(shape_fn, x, z, 1):
                continue
            f = cardinal_facing(x, z, shape_fn)
            b.put(x, y, z, SLAB)
            b.put(x, y + 1, z, BALCONY(f))
            # Projecting support under the ledge.
            if (x + z) % 3 == 0:
                b.put(x, y - 1, z, BRIDGE(f))


def roof_terrace(y, lower_fn, upper_fn, edge_block=PEDGE):
    """Fill exposed roof between two footprints and decorate the outer edge."""
    for x in range(SX):
        for z in range(SZ):
            if lower_fn(x, z) and not upper_fn(x, z):
                b.put(x, y, z, SLAB if (x + z) % 5 else METALF)
                if boundary(lower_fn, x, z, 1):
                    b.put(x, y + 1, z, edge_block(cardinal_facing(x, z, lower_fn)))




def gabled_roof_ns(y0, x0, x1, z0, z1, max_rise=12):
    """Stepped Gothic roof whose ridge runs north/south."""
    half = max(1, (x1 - x0) // 2)
    rise = min(max_rise, half)
    for step in range(rise + 1):
        xa, xb = x0 + step, x1 - step
        y = y0 + step
        if xa > xb:
            break
        for z in range(z0, z1 + 1):
            b.put(xa, y, z, STAIR("east"))
            b.put(xb, y, z, STAIR("west"))
            if step % 3 == 0:
                b.put(xa, y + 1, z, SPIRECAP)
                b.put(xb, y + 1, z, SPIRECAP)
    ridge_x0 = x0 + rise
    ridge_x1 = x1 - rise
    for x in range(ridge_x0, ridge_x1 + 1):
        for z in range(z0, z1 + 1):
            b.put(x, y0 + rise + 1, z, CORNICE("north" if z > (z0 + z1)//2 else "south"))


def gabled_roof_ew(y0, x0, x1, z0, z1, max_rise=12):
    """Stepped Gothic roof whose ridge runs east/west."""
    half = max(1, (z1 - z0) // 2)
    rise = min(max_rise, half)
    for step in range(rise + 1):
        za, zb = z0 + step, z1 - step
        y = y0 + step
        if za > zb:
            break
        for x in range(x0, x1 + 1):
            b.put(x, y, za, STAIR("south"))
            b.put(x, y, zb, STAIR("north"))
            if step % 3 == 0:
                b.put(x, y + 1, za, SPIRECAP)
                b.put(x, y + 1, zb, SPIRECAP)
    ridge_z0 = z0 + rise
    ridge_z1 = z1 - rise
    for z in range(ridge_z0, ridge_z1 + 1):
        for x in range(x0, x1 + 1):
            b.put(x, y0 + rise + 1, z, CORNICE("east" if x > (x0 + x1)//2 else "west"))


def pointed_roof(y0, y1, x0, z0, x1, z1):
    """Broad stepped roof.  It narrows slowly so the crown never reads as a needle."""
    span = max(x1 - x0, z1 - z0)
    steps = max(1, (y1 - y0 + 1) // 3)
    for step in range(steps):
        y = y0 + step * 3
        inset = min(step, span // 3)
        xa, za, xb, zb = x0 + inset, z0 + inset, x1 - inset, z1 - inset
        if xa > xb or za > zb:
            break
        for x in range(xa, xb + 1):
            for z in range(za, zb + 1):
                if x in (xa, xb) or z in (za, zb):
                    # Cathedral stairs create stepped/sloped edges; caps fill the corners.
                    if x == xa:
                        blk = STAIR("east")
                    elif x == xb:
                        blk = STAIR("west")
                    elif z == za:
                        blk = STAIR("south")
                    else:
                        blk = STAIR("north")
                    b.put(x, y, z, blk)
                    b.put(x, y + 1, z, SPIRECAP if (x in (xa, xb) and z in (za, zb)) else blk)
                    b.put(x, y + 2, z, SPIRECAP if (x + z) % 4 == 0 else blk)


# ---------------------------------------------------------------------------
# 1) Broad stepped foundation — three nested plinths with irregular cardinal arms.
# ---------------------------------------------------------------------------
for y, margin in ((0, 0), (1, 1), (2, 2), (3, 4), (4, 5), (5, 6)):
    for x in range(margin, SX - margin):
        for z in range(margin, SZ - margin):
            if lower_shape(x, z) or (8 + margin <= x <= 87 - margin and 8 + margin <= z <= 87 - margin):
                b.put(x, y, z, ASH if y < 3 else STEEL)

# Main accessible ground floor.
fill_floor(6, lower_shape, grate_period=29)

# Lower fortress shell with three strong horizontal storeys.
shell(lower_shape, 7, 34, band_levels=(13, 14, 23, 24, 33, 34), buttress_period=10)
ring_balcony(24, lower_shape)

# Four grand entrances, with recessed door masses rather than flat holes.
entrances = [
    (range(38, 58), range(2, 7), "south"),
    (range(38, 58), range(89, 94), "north"),
]
for xs, zs, f in entrances:
    for x in xs:
        for z in zs:
            for y in range(7, 22):
                b.put(x, y, z, AIR)
    for x in (36, 37, 58, 59):
        for z in zs:
            for y in range(7, 27):
                b.put(x, y, z, PILLAR if y % 5 else BUTT(f))
    for x in range(36, 60):
        for z in zs:
            b.put(x, 27, z, GAW(f))
            b.put(x, 28, z, CORNICE(f))
for zs, xs, f in ((range(38, 58), range(2, 7), "east"), (range(38, 58), range(89, 94), "west")):
    for z in zs:
        for x in xs:
            for y in range(7, 22):
                b.put(x, y, z, AIR)
    for z in (36, 37, 58, 59):
        for x in xs:
            for y in range(7, 27):
                b.put(x, y, z, PILLAR if y % 5 else BUTT(f))
    for z in range(36, 60):
        for x in xs:
            b.put(x, 27, z, GAW(f))
            b.put(x, 28, z, CORNICE(f))

# Giant door surfaces inside entrance throats.
for x in range(40, 56):
    for y in range(7, 22):
        b.put(x, y, 11, DOOR("south"))
        b.put(x, y, 84, DOOR("north"))
for z in range(40, 56):
    for y in range(7, 22):
        b.put(11, y, z, DOOR("east"))
        b.put(84, y, z, DOOR("west"))

# Lower nave: columns and side chapels create interior depth.
for x in (25, 34, 61, 70):
    for z in range(20, 77, 8):
        for y in range(7, 29):
            b.put(x, y, z, PILLAR)
        b.put(x, 29, z, SPIRECAP)
for z in (25, 34, 61, 70):
    for x in range(20, 77, 8):
        for y in range(7, 29):
            b.put(x, y, z, PILLAR)
        b.put(x, 29, z, SPIRECAP)

# Central crossing dais and broad stairs on all sides.
for x in range(37, 59):
    for z in range(37, 59):
        b.put(x, 8, z, CATHF)
for i in range(10):
    for x in range(38 - i // 3, 58 + i // 3):
        b.put(x, 7 + i, 27 + i, STAIR("south"))
        b.put(x, 7 + i, 68 - i, STAIR("north"))
    for z in range(38 - i // 3, 58 + i // 3):
        b.put(27 + i, 7 + i, z, STAIR("east"))
        b.put(68 - i, 7 + i, z, STAIR("west"))

# Windows and shrine niches on the lower mass.
for x in range(18, 79, 10):
    for y in range(16, 23):
        b.put(x, y, 8, GLOWWIN("south") if x % 20 else STAINED("south"))
        b.put(x, y, 87, GLOWWIN("north") if x % 20 else STAINED("north"))
for z in range(18, 79, 10):
    for y in range(16, 23):
        b.put(8, y, z, GLOWWIN("east") if z % 20 else STAINED("east"))
        b.put(87, y, z, GLOWWIN("west") if z % 20 else STAINED("west"))

# ---------------------------------------------------------------------------
# 2) Middle cathedral mass — smaller footprint, transepts and deep terraces.
# ---------------------------------------------------------------------------
roof_terrace(35, lower_shape, middle_shape)
fill_floor(36, middle_shape, grate_period=37)
shell(middle_shape, 37, 65, band_levels=(44, 45, 55, 56, 64, 65), buttress_period=8,
      base_block=RSP, alt_block=GAW)
ring_balcony(55, middle_shape)

# Intersecting cathedral roofs replace the previous flat, box-like middle top.
gabled_roof_ns(64, 30, 65, 12, 83, max_rise=14)
gabled_roof_ew(64, 12, 83, 31, 64, max_rise=13)

# Flying-buttress bridges from lower outer ribs to the middle mass.
for x in (20, 30, 66, 76):
    for z, f in ((17, "south"), (78, "north")):
        for k in range(8):
            b.put(x, 30 + k // 2, z + (k if z < 48 else -k), BRIDGE(f))
            if k in (0, 4, 7):
                b.put(x, 31 + k // 2, z + (k if z < 48 else -k), TRI(f))
for z in (20, 30, 66, 76):
    for x, f in ((17, "east"), (78, "west")):
        for k in range(8):
            b.put(x + (k if x < 48 else -k), 30 + k // 2, z, BRIDGE(f))
            if k in (0, 4, 7):
                b.put(x + (k if x < 48 else -k), 31 + k // 2, z, TRI(f))

# Tall paired windows, not uniform wallpaper.
for x in (25, 35, 48, 60, 70):
    for y in range(43, 54):
        b.put(x, y, 15, STAINED("south") if x in (35, 60) else GLOWWIN("south"))
        b.put(x, y, 80, STAINED("north") if x in (35, 60) else GLOWWIN("north"))
for z in (25, 35, 48, 60, 70):
    for y in range(43, 54):
        b.put(16, y, z, STAINED("east") if z in (35, 60) else GLOWWIN("east"))
        b.put(79, y, z, STAINED("west") if z in (35, 60) else GLOWWIN("west"))

# Side chapel/tower clusters.  Heights intentionally differ to break the perfect-box silhouette.
towers = [
    (18, 18, 18, 72), (77, 18, 15, 68), (18, 77, 15, 66), (77, 77, 18, 74),
    (47, 13, 12, 78), (82, 47, 11, 70), (47, 82, 13, 76), (13, 47, 10, 69),
]
for cx, cz, r, top in towers:
    x0, x1, z0, z1 = cx - r // 2, cx + r // 2, cz - r // 2, cz + r // 2
    for y in range(36, top + 1):
        for x in range(max(0, x0), min(SX, x1 + 1)):
            for z in range(max(0, z0), min(SZ, z1 + 1)):
                if x in (x0, x1) or z in (z0, z1):
                    if (x + z) % 4 == 0:
                        b.put(x, y, z, PILLAR)
                    else:
                        f = "south" if z == z0 else "north" if z == z1 else "east" if x == x0 else "west"
                        b.put(x, y, z, GAW(f) if y % 7 == 0 else RSP(f))
    pointed_roof(top + 1, min(SY - 2, top + 14), x0, z0, x1, z1)
    # Gargoyles at the four roof corners.
    for x, z, f in ((x0, z0, "south"), (x1, z0, "south"), (x0, z1, "north"), (x1, z1, "north")):
        b.put(x, top, z, GARGOYLE(f))

# ---------------------------------------------------------------------------
# 3) Upper citadel — broad crown base and thick central mass.
# ---------------------------------------------------------------------------
roof_terrace(66, middle_shape, upper_shape)
fill_floor(67, upper_shape, grate_period=31)
shell(upper_shape, 68, 84, band_levels=(75, 76, 83, 84), buttress_period=7,
      base_block=GAW, alt_block=RSP)
ring_balcony(83, upper_shape)
# Smaller crossed roofs leave the corner towers visible around the central citadel.
gabled_roof_ns(84, 37, 58, 18, 77, max_rise=9)
gabled_roof_ew(84, 19, 76, 38, 57, max_rise=9)

# Upper command chapel.
for x in range(37, 59):
    for z in range(38, 62):
        b.put(x, 69, z, CATHF)
for step in range(8):
    for x in range(39 - step // 3, 57 + step // 3):
        b.put(x, 69 + step, 30 + step, STAIR("south"))
for x in (39, 43, 47, 51, 55):
    b.put(x, 78, 59, SHRINE("north"))
    b.put(x, 79, 59, CANDLE("north"))
    b.put(x, 81, 59, TRI("north") if x != 47 else SKULL("north"))
for x in (38, 57):
    b.put(x, 78, 57, BRAZIER)
b.put(47, 78, 60, MK("marker_commander_point"))

# Industrial service spine woven into the cathedral rather than isolated boxes.
for y in range(69, 87):
    b.put(25, y, 47, VCON)
    b.put(70, y, 48, VCON)
for x in range(25, 71):
    if x % 3 == 0:
        b.put(x, 72, 47, PIPE("east"))
        b.put(x, 73, 47, PCLAMP("south"))
b.put(47, 72, 47, CROSS("south"))
b.put(25, 72, 47, ELBOW("east"))
b.put(70, 72, 47, ELBOW("west"))

# ---------------------------------------------------------------------------
# 4) Broad crown and lantern — reduced taper; top remains massive.
# ---------------------------------------------------------------------------
roof_terrace(94, upper_shape, crown_shape)
fill_floor(95, crown_shape)
shell(crown_shape, 96, 108, band_levels=(101, 102, 107, 108), buttress_period=0,
      base_block=GAW, alt_block=WIN)
ring_balcony(107, crown_shape)

# Four crown turrets, integrated into the main crown.
for cx, cz, top in ((32, 31, 113), (63, 31, 111), (32, 64, 110), (63, 64, 114)):
    for y in range(95, top):
        for dx in range(-4, 5):
            for dz in range(-4, 5):
                if abs(dx) == 4 or abs(dz) == 4:
                    f = "south" if dz == -4 else "north" if dz == 4 else "east" if dx == -4 else "west"
                    b.put(cx + dx, y, cz + dz, WIN(f) if y % 5 else PILLAR)
    pointed_roof(top, min(126, top + 10), cx - 4, cz - 4, cx + 4, cz + 4)

# Broad lantern: 20x22 footprint, only mild taper near the very top.
for y in range(109, 120):
    inset = 0 if y < 115 else 2
    fn = lambda x, z, i=inset: lantern_shape(x, z, i)
    for x in range(SX):
        for z in range(SZ):
            if boundary(fn, x, z, 2):
                f = cardinal_facing(x, z, fn)
                block = STAINED(f) if (x + z + y) % 5 == 0 else GAW(f)
                b.put(x, y, z, block)
    if y in (113, 114, 118, 119):
        for x in range(SX):
            for z in range(SZ):
                if boundary(fn, x, z, 1):
                    b.put(x, y, z, CORNICE(cardinal_facing(x, z, fn)))

# Thick stepped roof and clustered finials instead of one thin needle.
pointed_roof(120, 126, 38, 37, 57, 58)
for x, z in ((42, 42), (53, 42), (42, 53), (53, 53), (47, 47), (48, 48)):
    b.put(x, 126, z, SPIRECAP)
    b.put(x, 127, z, SPIRECAP)

# Exterior detail passes: reliefs, windows, gargoyles and local asymmetry.
for x in range(14, 82, 8):
    b.put(x, 31, 8, TRI("south"))
    b.put(x, 31, 87, TRI("north"))
for z in range(14, 82, 8):
    b.put(8, 31, z, TRI("east"))
    b.put(87, 31, z, TRI("west"))
for x, z, f in ((20, 8, "south"), (75, 8, "south"), (20, 87, "north"), (75, 87, "north"),
                (8, 20, "east"), (8, 75, "east"), (87, 20, "west"), (87, 75, "west")):
    b.put(x, 35, z, GARGOYLE(f))

# Hanging gantries and chains beneath the upper terraces.
for x0, z0, f in ((22, 47, "east"), (73, 47, "west"), (47, 22, "south"), (47, 73, "north")):
    for k in range(8):
        x = x0 + (k if f == "east" else -k if f == "west" else 0)
        z = z0 + (k if f == "north" else -k if f == "south" else 0)
        b.put(x, 64, z, GANTRY(f))
    b.put(x0, 63, z0, ANCHOR(f))
    for y in range(58, 63):
        b.put(x0, y, z0, CHAIN)
    b.put(x0, 57, z0, SCONCE(f))

# Patrol/civil/loot markers on usable routes.
for pos in ((47, 7, 20), (47, 7, 47), (47, 7, 73), (20, 7, 47), (73, 7, 47),
            (47, 36, 24), (47, 36, 71), (24, 36, 47), (71, 36, 47),
            (47, 67, 47), (47, 90, 47)):
    b.put(*pos, MK("marker_patrol_point"))
for pos in ((40, 7, 31), (55, 7, 31), (40, 7, 64), (55, 7, 64),
            (33, 36, 47), (62, 36, 47)):
    b.put(*pos, MK("marker_civil_spawn"))
b.put(47, 106, 47, MK("marker_loot_point"))

nonair, palette, size = b.write_nbt(str(OUT))
b.previews(
    str(PREV / "spire_crown_01_v2"),
    plans=[(126, "plan_crown"), (104, "plan_upper_crown"), (89, "plan_upper_terrace"),
           (66, "plan_middle_terrace"), (35, "plan_lower_roof"), (7, "plan_ground")],
    sections_x=[(47, "section_x47"), (24, "section_x24")],
    sections_z=[(47, "section_z47"), (31, "section_z31")],
)
print(f"generated {OUT.relative_to(ROOT)}")
print(f"non-air={nonair} palette={palette} bytes={size}")
