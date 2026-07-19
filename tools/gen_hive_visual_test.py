#!/usr/bin/env python3
"""Generate the first visual test sector for the rebuilt Hive City.

This 64x64x64 module is deliberately a vertical slice of the target atmosphere:
- monumental gothic-industrial nave;
- two walkable levels;
- central gate and shrine axis;
- connected service pipes, lift rails and maintenance machinery;
- side galleries, stairs, railings, windows, candle niches and braziers;
- marker-safe open routes for civilians, guards and large mobs.

Output:
  data/firstcrusade/structures/hive/test/hive_visual_test_01.nbt
  tools/previews_visual_test/*.png
"""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(Path(__file__).resolve().parent))
from hive_module_lib import ModuleBuilder, S, V, AIR, MK  # noqa: E402

OUT = ROOT / "src/main/resources/data/firstcrusade/structures/hive/test/hive_visual_test_01.nbt"
PREV = ROOT / "tools/previews_visual_test"
PREV.mkdir(parents=True, exist_ok=True)


def H(name, facing="north"):
    return S(name, facing=facing)

# New concept blocks — Set I: structure
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

# Set II: industrial systems
PIPE = lambda f="north": H("straight_pipe", f)
ELBOW = lambda f="north": H("elbow_pipe", f)
TPIPE = lambda f="north": H("t_pipe_junction", f)
XPIPE = S("cross_pipe_junction")
CLAMP = lambda f="north": H("pipe_support_clamp", f)
VCON = S("vertical_service_conduit")
CABLE = lambda f="north": H("cable_bundle_block", f)
VENT = lambda f="north": H("vent_outlet", f)
FVENT = S("floor_vent")
LIFT = lambda f="north": H("lift_rail", f)
GANTRY = lambda f="north": H("gantry_beam", f)
ANCHOR = lambda f="north": H("suspended_track_anchor", f)
HATCH = lambda f="north": H("maintenance_hatch", f)
MACHINE = lambda f="north": H("machine_casing_block", f)
HGRATE = S("hazard_grated_floor")
PLATFORM = lambda f="north": H("reinforced_platform_edge", f)

# Set III: floors, lights and details
GLOWWIN = lambda f="north": H("glowing_shrine_window", f)
STAINED = lambda f="north": H("stained_window_variant", f)
CANDLE = lambda f="north": H("candle_alcove", f)
SCONCE = lambda f="north": H("wall_sconce", f)
SHRINE = lambda f="north": H("shrine_recess", f)
BLOOD = S("bloodstained_floor_tile")
CATHF = S("cathedral_floor_tile")
METALF = S("metal_floor_plate")
FGRATE = S("floor_grate")
STAIR = lambda f="north": H("cathedral_stair_block", f)
SLAB = S("landing_slab")
RAIL = lambda f="north": H("balustrade_railing", f)
SKULL = lambda f="north": H("skull_relief_panel", f)
GARGOYLE = lambda f="north": H("gargoyle_pedestal", f)
CRATE = lambda f="north": H("industrial_crate", f)
BRAZIER = S("brazier_block")

# Existing structural fillers, used sparingly behind the new visible blocks.
ASH = S("reinforced_ashcrete")
ASH_CR = S("cracked_reinforced_ashcrete")
STEEL = S("riveted_steel_block")
RUST = S("rusted_riveted_steel")
CHAIN = V("chain")

b = ModuleBuilder(64, 64, 64, seed=71001)

# ---------------------------------------------------------------- foundations and floor
b.fill(0, 0, 0, 63, 0, 63, ASH)
for x in range(2, 62):
    for z in range(2, 62):
        # Main nave is stone; service aisles are steel/grating.
        if 18 <= x <= 45:
            tile = CATHF if (x + z) % 4 else METALF
        elif x < 18:
            tile = HGRATE if (x + z) % 5 == 0 else METALF
        else:
            tile = FGRATE if (x + z) % 5 == 0 else METALF
        b.put(x, 1, z, tile)

# Drainage channels and illuminated service trench.
for z in range(3, 61):
    b.put(16, 1, z, FGRATE)
    b.put(47, 1, z, FVENT if z % 4 == 0 else FGRATE)

# ---------------------------------------------------------------- outer shell / monumental rhythm
# Thick backer walls at both sides, visible faces clad with concept blocks.
b.fill(0, 1, 0, 3, 45, 63, ASH)
b.fill(60, 1, 0, 63, 45, 63, ASH)
for z in range(1, 63):
    for y in range(2, 42):
        # Every fourth course switches panel language, creating vertical scale.
        if y % 8 in (0, 1):
            west, east = SEAM("east"), SEAM("west")
        elif z % 12 in (0, 1, 2):
            west, east = ABW("east"), ABW("west")
        else:
            west, east = RSP("east"), RSP("west")
        b.put(3, y, z, west)
        b.put(60, y, z, east)

# Tall piers, buttresses, cornices and spired crowns.
for z in (4, 16, 28, 40, 52, 60):
    for y in range(2, 43):
        b.put(5, y, z, PILLAR)
        b.put(58, y, z, PILLAR)
    for y in range(2, 18):
        b.put(6, y, z, BUTT("east"))
        b.put(57, y, z, BUTT("west"))
    b.put(5, 43, z, SPIRECAP)
    b.put(58, 43, z, SPIRECAP)

for z in range(1, 63):
    b.put(4, 18, z, CORNICE("east"))
    b.put(59, 18, z, CORNICE("west"))
    b.put(4, 31, z, MOLDING("east"))
    b.put(59, 31, z, MOLDING("west"))

# ---------------------------------------------------------------- south gate façade
# Build a thick façade and carve a 12x15 central opening.
b.fill(4, 1, 0, 59, 28, 4, ABW("south"))
b.fill(25, 2, 0, 38, 16, 4, AIR)
# Gate jambs and segmented overhead door/arch.
for y in range(2, 20):
    for x in (23, 24, 39, 40):
        b.put(x, y, 4, PILLAR)
for x in range(22, 42):
    b.put(x, 20, 4, DOOR("south"))
    if x % 3 == 0:
        b.put(x, 21, 4, TRI("south"))
for x in range(25, 39):
    b.put(x, 17, 4, GAW("south"))
# Door wings are recessed, leaving the central route open.
for x in range(17, 23):
    for y in range(3, 16):
        b.put(x, y, 4, DOOR("south"))
for x in range(41, 47):
    for y in range(3, 16):
        b.put(x, y, 4, DOOR("south"))
# Gargoyles and braziers mark the entrance.
for x in (13, 50):
    b.put(x, 2, 6, GARGOYLE("south"))
    b.put(x, 3, 9, BRAZIER)

# ---------------------------------------------------------------- north shrine / apse
# Raised sanctuary and grand rear wall.
for step in range(7):
    y = 2 + step
    z0 = 50 + step
    for x in range(20 + step, 44 - step):
        b.put(x, y, z0, STAIR("south"))
for x in range(19, 45):
    for z in range(57, 63):
        b.put(x, 8, z, CATHF)

b.fill(16, 2, 62, 47, 34, 63, ASH)
# Central shrine windows and recessed iconography.
for x in (23, 27, 31, 35, 39):
    for y in range(12, 25):
        b.put(x, y, 62, GLOWWIN("south") if x in (27, 31, 35) else STAINED("south"))
    b.put(x, 9, 62, SHRINE("south"))
    b.put(x, 10, 61, BRAZIER)
# Vertical arch composition and relief band.
for x in range(18, 46):
    b.put(x, 27, 62, GAW("south"))
    b.put(x, 28, 62, CORNICE("south"))
for x in (18, 45):
    for y in range(8, 33):
        b.put(x, y, 61, PILLAR)
for x in range(21, 43, 4):
    b.put(x, 30, 62, TRI("south"))
    b.put(x, 31, 62, SKULL("south"))

# ---------------------------------------------------------------- side galleries at y=16
for z in range(8, 57):
    for x in range(7, 17):
        b.put(x, 15, z, SLAB if x not in (7, 16) else BRIDGE("east"))
    for x in range(48, 57):
        b.put(x, 15, z, SLAB if x not in (48, 56) else BRIDGE("west"))
    b.put(17, 16, z, RAIL("east"))
    b.put(46, 16, z, RAIL("west"))
    if z % 4 == 0:
        b.put(17, 15, z, BALCONY("east"))
        b.put(46, 15, z, BALCONY("west"))

# Two safe stair runs to galleries.
for i in range(14):
    b.put(8 + (i // 7), 2 + i, 12 + i, STAIR("south"))
    b.put(55 - (i // 7), 2 + i, 12 + i, STAIR("south"))
# Landings.
b.fill(7, 15, 26, 12, 15, 31, SLAB)
b.fill(51, 15, 26, 56, 15, 31, SLAB)

# ---------------------------------------------------------------- overhead gothic/industrial frames
for z in (10, 22, 34, 46, 56):
    for x in range(8, 56):
        b.put(x, 34, z, GANTRY("east"))
    for x in (8, 12, 51, 55):
        for y in range(18, 35):
            b.put(x, y, z, FRAME)
    for x in (20, 32, 44):
        b.put(x, 33, z, ANCHOR("south"))
        for y in range(29, 33):
            b.put(x, y, z, CHAIN)
        b.put(x, 28, z, SCONCE("south"))

# ---------------------------------------------------------------- west industrial service wall
# Lift rails and maintenance platforms.
for x in (9, 12):
    for y in range(2, 31):
        b.put(x, y, 20, LIFT("south"))
for y in (8, 16, 24):
    for x in range(7, 15):
        b.put(x, y, 21, PLATFORM("south"))

# Connected pipe composition climbing then turning along the gallery.
for y in range(3, 28):
    b.put(7, y, 37, PIPE("east"))
    if y % 5 == 0:
        b.put(8, y, 37, CLAMP("east"))
b.put(7, 28, 37, ELBOW("east"))
for z in range(38, 54):
    b.put(7, 28, z, PIPE("south"))
b.put(7, 28, 54, TPIPE("south"))
b.put(8, 28, 54, XPIPE)
for y in range(5, 25):
    b.put(10, y, 54, VCON)

# Maintenance machinery and cable bank.
for z in (38, 42, 46, 50):
    b.put(10, 2, z, MACHINE("east"))
    b.put(11, 2, z, VENT("east"))
    b.put(12, 2, z, HATCH("east"))
    b.put(13, 2, z, CABLE("east"))
for z in (39, 47, 55):
    b.put(14, 3, z, CRATE("east"))

# ---------------------------------------------------------------- east devotional wall
for z in range(10, 56, 6):
    b.put(56, 4, z, CANDLE("west"))
    b.put(56, 9, z, LANCET("west"))
    b.put(56, 13, z, WIN("west") if z % 12 else STAINED("west"))
    b.put(55, 2, z, BRAZIER if z % 12 == 4 else SHRINE("west"))
    b.put(56, 20, z, TRI("west"))

# ---------------------------------------------------------------- floor storytelling and safe combat lanes
for x, z in ((29, 30), (30, 30), (31, 31), (32, 31), (33, 32), (35, 33)):
    b.put(x, 1, z, BLOOD)
# Cover props stay out of the central 14-block-wide firing lane.
for x, z, f in ((12, 10, "south"), (51, 10, "south"), (13, 32, "east"),
                (50, 32, "west"), (12, 58, "north"), (51, 58, "north")):
    b.put(x, 2, z, CRATE(f))

# ---------------------------------------------------------------- markers (converted to air by HiveMarkerProcessor)
for pos in ((30, 2, 8), (33, 2, 8), (29, 2, 24), (34, 2, 24), (29, 2, 42), (34, 2, 42)):
    b.put(*pos, MK("marker_patrol_point"))
for pos in ((26, 2, 12), (37, 2, 12), (26, 2, 37), (37, 2, 37)):
    b.put(*pos, MK("marker_cover_point"))
for pos in ((28, 2, 18), (35, 2, 18), (28, 2, 47), (35, 2, 47)):
    b.put(*pos, MK("marker_civil_spawn"))
b.put(31, 9, 58, MK("marker_commander_point"))
b.put(11, 2, 44, MK("marker_worker_spawn"))
b.put(52, 2, 44, MK("marker_worker_spawn"))
b.put(31, 2, 34, MK("marker_loot_point"))

# A few worn blocks around service areas, but no path obstruction.
for x, y, z in ((6, 2, 48), (7, 2, 49), (14, 2, 53), (49, 2, 14), (54, 2, 18)):
    b.put(x, y, z, RUST if (x + z) % 2 else ASH_CR)

nonair, palette, size = b.write_nbt(str(OUT))
b.previews(
    str(PREV / "hive_visual_test_01"),
    plans=[(63, "plan_roof"), (18, "plan_gallery"), (6, "plan_ground")],
    sections_x=[(31, "section_central_x31"), (10, "section_industrial_x10")],
    sections_z=[(32, "section_mid_z32"), (60, "section_shrine_z60")],
)
print(f"generated {OUT.relative_to(ROOT)}")
print(f"non-air={nonair} palette={palette} bytes={size}")
