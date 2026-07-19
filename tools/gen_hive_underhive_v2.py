#!/usr/bin/env python3
"""Underhive V2 — distrito contínuo 192×64×128.

Reconstrói a camada inferior como uma subcidade irregular em seis módulos conectados:
coletores monumentais, basílica colapsada, assentamento de gangue, catacumbas,
mercado do sump e abismo do reator. O conjunto usa os blocos novos da Hive City,
formas chanfradas, ruínas diagonais, passarelas e massas em alturas variadas.
"""
from pathlib import Path
import json
import math
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(Path(__file__).resolve().parent))
from hive_module_lib import ModuleBuilder, S, V, AIR, MK, RAIL_RAW  # noqa: E402

OUT = ROOT / "src/main/resources/data/firstcrusade/structures/hive"
PREV = ROOT / "tools/previews_underhive_v2"
PREV.mkdir(parents=True, exist_ok=True)


def H(name, facing="north"):
    return S(name, facing=facing)

# New Hive City set I
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

# Set II
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
HAZGRATE = S("hazard_grated_floor")
PEDGE = lambda f="north": H("reinforced_platform_edge", f)

# Set III
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
SKULLP = lambda f="north": H("skull_relief_panel", f)
GARGOYLE = lambda f="north": H("gargoyle_pedestal", f)
CRATE = lambda f="north": H("industrial_crate", f)
BRAZIER = S("brazier_block")

# Existing set
ASH = S("reinforced_ashcrete")
ASH_CR = S("cracked_reinforced_ashcrete")
STEEL = S("riveted_steel_block")
RUST = S("rusted_riveted_steel")
ARMOR = S("armored_hive_plating")
GRATE = S("industrial_grating")
CATW = S("industrial_catwalk")
CATHW = S("cathedral_wall")
UHCON = S("underhive_concrete")
RUBBLE = S("rubble")
GFUNGUS = S("glow_fungus")
SLUDGE = S("toxic_sludge")
SLUDGE_S = S("solid_toxic_sludge")
GANGFIRE = S("gang_fire")
LYEL = S("yellow_industrial_lumen")
LGRN = S("green_industrial_lumen")
LRED = S("red_emergency_lumen")
CHAIN = V("chain")
LADDER_E = V("ladder", facing="east")
LADDER_W = V("ladder", facing="west")
SST = lambda f="north", half="bottom": S("riveted_steel_stairs", facing=f, half=half)
AST = lambda f="north", half="bottom": S("reinforced_ashcrete_stairs", facing=f, half=half)
BENCH = lambda f="north": H("hive_bench", f)
CHAIR = lambda f="north": H("hive_chair", f)
SHELF = lambda f="north": H("shelf_unit", f)
SUPPLY = lambda f="north": H("supply_crate", f)
TBARREL = lambda f="north": H("toxic_barrel", f)
SCRAP = lambda f="north": H("scrap_pile", f)
GANGMARK = lambda f="north": H("gang_marking", f)
CORRUG = lambda axis="y": S("corrugated_wall", axis=axis)
FLOOD = lambda f="north": H("industrial_floodlight", f)
CTRL = lambda f="north": H("control_panel", f)
COG = lambda f="north": H("cogitator_console", f)
AQUILA = S("aquila_wall_relief")
SAINT = lambda f="north", p=0: S("saint_statue", facing=f, part=p)
GUARDIAN = lambda f="north", p=0: S("imperial_guardian_statue", facing=f, part=p)
BANNER = lambda f="north", p=0: S("aquila_banner", facing=f, part=p)

SX, SY, SZ = 192, 64, 128
b = ModuleBuilder(SX, SY, SZ, seed=97007)


def rect(x0, y0, z0, x1, y1, z1, block):
    b.fill(x0, y0, z0, x1, y1, z1, block)


def disk(cx, cz, r, y, block, thickness=1):
    rr = r * r
    for x in range(cx-r, cx+r+1):
        for z in range(cz-r, cz+r+1):
            if (x-cx)**2 + (z-cz)**2 <= rr:
                rect(x, y, z, x, y+thickness-1, z, block)


def ring(cx, cz, r0, r1, y0, y1, block):
    a, d = r0*r0, r1*r1
    for x in range(cx-r1, cx+r1+1):
        for z in range(cz-r1, cz+r1+1):
            q = (x-cx)**2 + (z-cz)**2
            if a <= q <= d:
                rect(x, y0, z, x, y1, z, block)


def irregular_floor(x0, z0, x1, z1, base=1):
    for x in range(x0, x1+1):
        for z in range(z0, z1+1):
            h = base + ((x*17 + z*23 + (x*z)%11) % 5 == 0) + ((x*5 + z*3) % 17 == 0)
            for y in range(0, h+1):
                b.put(x, y, z, UHCON if (x+y+z)%5 else ASH_CR)
            if (x*13+z*7)%29 == 0:
                b.put(x, h+1, z, RUBBLE)


def cavern_roof(x0, z0, x1, z1, base_y=52, amp=7):
    # Thick, uneven roof with hanging ribs; deliberately leaves a few abyss apertures.
    for x in range(x0, x1+1):
        for z in range(z0, z1+1):
            wave = int((math.sin(x*0.17)+math.cos(z*0.13))*1.8)
            y = max(43, min(60, base_y + wave + ((x*11+z*5)%amp)//3))
            for yy in range(y, min(SY, y+4)):
                b.put(x, yy, z, UHCON if (x+z+yy)%4 else ASH_CR)
    for x in range(x0+4, x1-3, 12):
        for z in range(z0+4, z1-3, 16):
            drop = 4 + ((x+z)//4)%8
            rect(x, base_y-drop, z, x+1, base_y, z+1, RUST)
            b.put(x, base_y-drop-1, z, GFUNGUS)


def arch_rib_x(x, z0, z1, floor_y, top_y, block=RUST):
    rect(x, floor_y, z0, x, top_y-3, z0, block)
    rect(x, floor_y, z1, x, top_y-3, z1, block)
    span = z1-z0
    mid = (z0+z1)//2
    for z in range(z0, z1+1):
        q = abs(z-mid)/(span/2 if span else 1)
        y = int(top_y - (q*q)*7)
        rect(x, y, z, x, top_y, z, block if (z+ x)%3 else FRAME)


def arch_rib_z(z, x0, x1, floor_y, top_y, block=RUST):
    rect(x0, floor_y, z, x0, top_y-3, z, block)
    rect(x1, floor_y, z, x1, top_y-3, z, block)
    span = x1-x0
    mid = (x0+x1)//2
    for x in range(x0, x1+1):
        q = abs(x-mid)/(span/2 if span else 1)
        y = int(top_y - (q*q)*7)
        rect(x, y, z, x, top_y, z, block if (x+z)%3 else FRAME)


def catwalk_x(x0, x1, y, z, width=3):
    rect(x0, y, z, x1, y, z+width-1, CATW)
    for x in range(x0, x1+1):
        b.put(x, y+1, z, RAIL_RAW)
        b.put(x, y+1, z+width-1, RAIL_RAW)
    for x in range(x0, x1+1, 8):
        rect(x, y-5, z+1, x, y-1, z+1, BRIDGE("north"))


def catwalk_z(z0, z1, y, x, width=3):
    rect(x, y, z0, x+width-1, y, z1, CATW)
    for z in range(z0, z1+1):
        b.put(x, y+1, z, RAIL_RAW)
        b.put(x+width-1, y+1, z, RAIL_RAW)
    for z in range(z0, z1+1, 8):
        rect(x+1, y-5, z, x+1, y-1, z, BRIDGE("east"))


def pipe_run_x(x0, x1, y, z, facing="north"):
    for x in range(x0, x1+1):
        b.put(x, y, z, PIPE(facing))
        if (x-x0)%8 == 0:
            b.put(x, y-1, z, PCLAMP(facing))


def pipe_run_z(z0, z1, y, x, facing="east"):
    for z in range(z0, z1+1):
        b.put(x, y, z, PIPE(facing))
        if (z-z0)%8 == 0:
            b.put(x, y-1, z, PCLAMP(facing))


def stair_tower(x0, z0, floors=(3,16,29,42), clockwise=True):
    # 7x7 open stair/lift cage. Same footprint is used by the connector belts above.
    rect(x0, 1, z0, x0+6, 62, z0+6, AIR)
    for x,z in ((x0,z0),(x0+6,z0),(x0,z0+6),(x0+6,z0+6)):
        rect(x, 1, z, x, 63, z, FRAME)
    for y in range(2,63):
        b.put(x0+1, y, z0+1, LIFT("east"))
        b.put(x0+2, y, z0+1, LADDER_E)
    for fi in range(len(floors)-1):
        y0,y1=floors[fi],floors[fi+1]
        for y in range(y0,y1):
            p=(y-y0)%20
            if p<5: x=x0+1+p; z=z0+5
            elif p<10: x=x0+5; z=z0+5-(p-5)
            elif p<15: x=x0+5-(p-10); z=z0+1
            else: x=x0+1; z=z0+1+(p-15)
            b.put(x,y,z,SST("south" if clockwise else "north"))
    for y in floors:
        rect(x0, y, z0, x0+6, y, z0+6, FLOORGRATE)
        for x in range(x0,x0+7):
            b.put(x,y+1,z0,RAIL_RAW); b.put(x,y+1,z0+6,RAIL_RAW)


# Base cavern
irregular_floor(0,0,191,127,1)
cavern_roof(0,0,191,127,54,9)
# Perimeter rock/old hive retaining walls, with deliberate breaches.
for x in range(SX):
    if not (26 <= x <= 39 or 90 <= x <= 103 or 154 <= x <= 167):
        rect(x,2,0,x,49,1,UHCON)
        rect(x,2,126,x,49,127,UHCON)
for z in range(SZ):
    if not (25 <= z <= 39 or 88 <= z <= 103):
        rect(0,2,z,1,49,z,UHCON)
        rect(190,2,z,191,49,z,UHCON)

# Large load-bearing ribs make the cavern read as buried city infrastructure.
for x in range(8,192,16):
    arch_rib_x(x,4,123,2,50,RUST if x%32 else FRAME)
for z in (8,32,56,72,96,120):
    arch_rib_z(z,3,188,2,47,RUST if z%24 else FRAME)

# Shared road/canal spine at z60..67 and cross axes.
rect(0,2,60,191,2,67,METALF)
for x in range(0,192):
    b.put(x,3,60,PEDGE("south")); b.put(x,3,67,PEDGE("north"))
    if x%6==0: b.put(x,3,63,FVENT)
for cx in (31,95,159):
    rect(cx-5,2,0,cx+5,2,127,METALF)
    for z in range(0,128,8):
        b.put(cx-4,3,z,PEDGE("east")); b.put(cx+4,3,z,PEDGE("west"))

# ====================================================================================
# 1) SUMP CATHEDRAL — x0..63,z0..63
# ====================================================================================
# Three deep channels with organic edges.
for zc,w in ((16,7),(31,9),(47,6)):
    for x in range(3,61):
        for z in range(zc-w//2,zc+w//2+1):
            depth=1+((x*7+z*11)%3)
            rect(x,1,z,x,2+depth,z,AIR)
            b.put(x,1,z,SLUDGE)
    catwalk_x(4,59,7,zc-1,3)
# Pump basilica, broad and chamfered.
for y in range(3,29):
    inset=max(0,(y-19)//4)
    x0,x1=9+inset,54-inset; z0,z1=7+inset,56-inset
    for x in range(x0,x1+1):
        if x in (x0,x1) or (x-x0)%7==0:
            b.put(x,y,z0,PILLAR); b.put(x,y,z1,PILLAR)
        else:
            b.put(x,y,z0,ABW("south")); b.put(x,y,z1,ABW("north"))
    for z in range(z0,z1+1):
        if z in (z0,z1) or (z-z0)%7==0:
            b.put(x0,y,z,PILLAR); b.put(x1,y,z,PILLAR)
        else:
            b.put(x0,y,z,ABW("east")); b.put(x1,y,z,ABW("west"))
# Open portals and rose of pipes.
rect(26,3,7,38,17,9,AIR); rect(26,3,54,38,17,56,AIR)
for x in range(27,38):
    b.put(x,18,8,GAW("south")); b.put(x,18,55,GAW("north"))
ring(31,31,5,9,5,17,MACHINE("south"))
disk(31,31,4,5,FLOORGRATE)
for a in range(0,360,45):
    x=31+int(math.cos(math.radians(a))*8); z=31+int(math.sin(math.radians(a))*8)
    rect(x,6,z,x,20,z,VCON); b.put(x,21,z,ELBOW("north"))
pipe_run_x(6,58,24,11); pipe_run_x(6,58,28,52)
catwalk_z(8,55,17,16,3); catwalk_z(8,55,21,45,3)
for x,z in ((12,12),(50,12),(12,50),(50,50)):
    rect(x,3,z,x+2,22,z+2,BUTT("south")); b.put(x+1,23,z+1,GARGOYLE("south"))
for pos in ((7,4,7),(56,4,7),(7,4,56),(56,4,56)):
    b.put(*pos,BRAZIER)

# ====================================================================================
# 2) COLLAPSED BASILICA — x64..127,z0..63
# ====================================================================================
# Diagonal nave ruins and broken transept.
for t in range(-8,9):
    for s in range(5,58):
        x=95+s//2+t; z=5+s
        if 64<=x<128 and 0<=z<64:
            y=3+max(0,8-abs(t))//2
            b.put(x,y,z,CATHF if abs(t)<5 else RUBBLE)
# Broken walls as stepped diagonals.
for s in range(5,56):
    x=72+s; z=6+s
    if x>=126 or z>=62: break
    h=8+((s*7)%18)
    for y in range(3,h):
        if (s+y)%6:
            b.put(x,y,z,CATHW if y%5 else GAW("south"))
            if s%4==0: b.put(x+1,y,z,BUTT("west"))
# Fallen vault segments.
for cx,cz,r,tilt in ((85,22,10,0),(104,40,13,1),(77,49,8,2)):
    for q in range(-r,r+1):
        h=int(math.sqrt(max(0,r*r-q*q)))
        x=cx+q; z=cz+q//2 if tilt else cz
        for yy in range(4,4+h):
            if (x+yy+z)%5:
                b.put(x,yy,z,RUBBLE if yy<7 else ASH_CR)
# Surviving chapel tower, cracked and asymmetric.
for y in range(3,39):
    inset=max(0,(y-29)//3)
    x0,x1=102+inset,121-inset; z0,z1=7+inset,24-inset
    for x in range(x0,x1+1):
        if (x+y)%4: b.put(x,y,z0,RSP("south")); b.put(x,y,z1,RSP("north"))
    for z in range(z0,z1+1):
        if (z+y)%4: b.put(x0,y,z,RSP("east")); b.put(x1,y,z,RSP("west"))
for y in range(10,34,7):
    b.put(111,y,7,GLOWWIN("south")); b.put(112,y,7,LANCET("south"))
for x,z in ((103,8),(120,8),(103,23),(120,23)):
    b.put(x,39,z,SPIRECAP)
# Excavated route through rubble and hanging relic bridge.
catwalk_x(68,124,19,46,4)
for x in range(72,124,12):
    for y in range(20,45): b.put(x,y,47,CHAIN)
for p in range(3):
    b.put(94,7+p,30,SAINT("east",p))
for x,z in ((70,9),(78,54),(118,53),(122,30)):
    b.put(x,3,z,GFUNGUS)
    b.put(x+1,3,z,GFUNGUS)

# ====================================================================================
# 3) GANG CITADEL — x128..191,z0..63
# ====================================================================================
# Irregular stepped scrap settlement around a central arena.
for x0,z0,w,d,h in ((131,5,17,15,13),(151,7,13,20,18),(170,4,17,14,11),
                    (134,37,20,20,16),(161,34,26,23,22)):
    for y in range(3,h+3):
        inset=max(0,(y-10)//6)
        xa,xb=x0+inset,min(190,x0+w-inset); za,zb=z0+inset,min(62,z0+d-inset)
        if xa>xb or za>zb: break
        for x in range(xa,xb+1):
            b.put(x,y,za,CORRUG("y") if (x+y)%3 else ABW("south"))
            b.put(x,y,zb,CORRUG("y") if (x+y)%3 else RSP("north"))
        for z in range(za,zb+1):
            b.put(xa,y,z,CORRUG("y")); b.put(xb,y,z,CORRUG("y"))
    rect(x0+2,3,z0+2,min(190,x0+w-2),3,min(62,z0+d-2),METALF)
    for x in range(x0+2,min(190,x0+w-1),4):
        b.put(x,h+3,z0+2,CRATE("south"))
# Arena bowl.
cx,cz=159,31
for x in range(cx-14,cx+15):
    for z in range(cz-12,cz+13):
        q=((x-cx)/14)**2+((z-cz)/12)**2
        if q<=1:
            b.put(x,2,z,BLOOD if (x+z)%11==0 else RUBBLE)
            rect(x,3,z,x,6,z,AIR)
        elif q<=1.28:
            b.put(x,3,z,CORRUG("y")); b.put(x,4,z,BENCH("north"))
for x,z in ((146,22),(172,22),(146,41),(172,41)):
    b.put(x,4,z,GANGFIRE)
# Throne and gantries.
rect(154,5,8,164,9,14,RUST); rect(156,6,10,162,8,13,AIR)
b.put(159,6,11,CHAIR("south")); b.put(159,9,9,GANGMARK("south"))
catwalk_x(130,190,25,16,3); catwalk_x(130,190,31,49,3)
catwalk_z(5,58,20,145,3); catwalk_z(5,58,27,178,3)
# Pipes/cables and graffiti.
pipe_run_z(4,59,35,132,"east"); pipe_run_z(4,59,39,188,"west")
for z in range(8,60,7):
    b.put(129,7+(z%4),z,GANGMARK("east")); b.put(190,9+(z%5),z,GANGMARK("west"))
for _ in range(55):
    x=b.rng.randrange(130,190); z=b.rng.randrange(3,61)
    y=b.rng.randrange(3,12)
    if b.get(x,y,z)==AIR:
        b.put(x,y,z,SCRAP(["north","south","east","west"][b.rng.randrange(4)]))

# ====================================================================================
# 4) FORGOTTEN CATACOMBS — x0..63,z64..127
# ====================================================================================
# Circular crypt galleries with four radial aisles.
cx,cz=31,95
ring(cx,cz,10,22,3,16,CATHW)
ring(cx,cz,23,25,3,9,ASH_CR)
disk(cx,cz,9,3,CATHF)
for a in range(0,360,30):
    x=cx+int(math.cos(math.radians(a))*18); z=cz+int(math.sin(math.radians(a))*18)
    rect(x,4,z,x,18,z,PILLAR)
    b.put(x,19,z,GARGOYLE("south"))
for a in (0,90,180,270):
    dx=int(math.cos(math.radians(a))); dz=int(math.sin(math.radians(a)))
    for r in range(0,31):
        x=cx+dx*r; z=cz+dz*r
        rect(x,3,z,x,9,z,AIR); b.put(x,2,z,CATHF)
# Central ossuary shrine.
rect(25,4,89,37,10,101,RSP("south")); rect(27,5,91,35,9,99,AIR)
b.put(31,5,94,SHRINE("south")); b.put(31,6,99,SKULLP("north"))
for p in range(3):
    b.put(27,5+p,95,GUARDIAN("east",p)); b.put(35,5+p,95,GUARDIAN("west",p))
for x,z in ((25,89),(37,89),(25,101),(37,101)): b.put(x,11,z,BRAZIER)
# Rows of crypt niches in irregular outer walls.
for x in range(5,59,5):
    for y in (5,10,15,20):
        b.put(x,y,68,CANDLE("south")); b.put(x,y,123,CANDLE("north"))
for z in range(72,120,6):
    b.put(4,7+(z%3)*5,z,SKULLP("east")); b.put(59,7+((z+1)%3)*5,z,SKULLP("west"))
# Broken transept exposing sump.
for x in range(42,62):
    for z in range(104,125):
        if (x-52)**2+(z-114)**2<75:
            b.put(x,2,z,SLUDGE if (x+z)%3 else SLUDGE_S)
catwalk_x(36,62,13,110,3)

# ====================================================================================
# 5) SUMP MARKET — x64..127,z64..127
# ====================================================================================
# Main bazaar street curves through slum pods.
for t in range(0,60):
    x=66+t; z=93+int(math.sin(t/8)*9)
    rect(x-2,3,z-3,x+2,3,z+3,METALF)
    if t%7==0:
        b.put(x-3,4,z,PEDGE("east")); b.put(x+3,4,z,PEDGE("west"))
# Stacked pods, intentionally uneven and interconnected.
pods=[(67,69,14,13,11),(84,67,18,15,17),(105,70,17,13,12),
      (68,104,20,18,15),(94,101,14,20,21),(111,102,14,18,16)]
for x0,z0,w,d,h in pods:
    cut=2+(x0+z0)%4
    for y in range(4,h+4):
        inset=max(0,(y-13)//5)
        xa,xb=x0+inset,x0+w-inset; za,zb=z0+inset,z0+d-inset
        for x in range(xa,xb+1):
            if x-xa<cut or xb-x<cut:
                continue
            b.put(x,y,za,ABW("south") if (x+y)%4 else CORRUG("y"))
            b.put(x,y,zb,RSP("north") if (x+y)%4 else CORRUG("y"))
        for z in range(za,zb+1):
            if z-za<cut or zb-z<cut:
                continue
            b.put(xa,y,z,CORRUG("y")); b.put(xb,y,z,CORRUG("y"))
    rect(x0+2,4,z0+2,x0+w-2,4,z0+d-2,METALF)
    b.put(x0+w//2,5,z0,DOOR("south"))
    for y in range(8,h+3,6): b.put(x0+w//2,y,z0,GLOWWIN("south"))
# Bridges and market awnings.
catwalk_x(66,126,18,86,3); catwalk_x(69,124,28,112,3)
catwalk_z(68,124,23,80,3); catwalk_z(67,123,32,114,3)
for x in range(70,124,8):
    rect(x,5,89,x+4,9,95,CORRUG("x")); rect(x+1,5,90,x+3,8,94,AIR)
    b.put(x+2,5,92,CRATE("south")); b.put(x+2,6,90,SCONCE("south"))
for x,z in ((71,84),(92,87),(115,85),(74,113),(102,117),(121,109)):
    b.put(x,4,z,GANGFIRE if (x+z)%2 else BRAZIER)
# Toxic well and improvised chapel.
ring(97,95,4,7,3,7,RUST); disk(97,95,3,3,SLUDGE)
rect(116,4,74,126,17,90,CATHW); rect(119,5,74,123,12,76,AIR)
b.put(121,6,76,SHRINE("south")); b.put(121,14,74,STAINED("south"))

# ====================================================================================
# 6) REACTOR ABYSS / VERTICAL TRANSIT — x128..191,z64..127
# ====================================================================================
# Deep central void with ring galleries and aligned shaft at x159,z96.
cx,cz=159,96
for x in range(132,188):
    for z in range(69,124):
        q=((x-cx)/27)**2+((z-cz)/27)**2
        if q<0.58:
            rect(x,0,z,x,47,z,AIR)
        elif q<0.80:
            b.put(x,3,z,HAZGRATE)
            if q>0.73: b.put(x,4,z,RAIL_RAW)
# Reactor core hanging into the abyss.
for y in range(8,49):
    r=4+(y%12<6)
    ring(cx,cz,max(0,r-2),r,y,y,MACHINE("south") if y%4 else VCON)
    if y%8==0:
        for a in range(0,360,45):
            x=cx+int(math.cos(math.radians(a))*9); z=cz+int(math.sin(math.radians(a))*9)
            b.put(x,y,z,ANCHOR("north"))
            for yy in range(y-5,y): b.put(x,yy,z,CHAIN)
# Three ring galleries.
for r,y in ((15,12),(21,27),(26,42)):
    ring(cx,cz,r-1,r+1,y,y,CATW)
    for a in range(0,360,15):
        x=cx+int(math.cos(math.radians(a))*r); z=cz+int(math.sin(math.radians(a))*r)
        b.put(x,y+1,z,RAIL_RAW)
# Radial bridges.
for a in (0,90,180,270):
    dx=int(math.cos(math.radians(a))); dz=int(math.sin(math.radians(a)))
    for r in range(6,29):
        x=cx+dx*r; z=cz+dz*r
        b.put(x,27,z,CATW); b.put(x,28,z+dx if dx else z,RAIL_RAW)
# Aligned lift/stair tower through all city levels.
stair_tower(156,93,(3,16,29,42,63),True)
# Three principal shafts align exactly with the rear connector belts above (x31/95/159, global z96).
stair_tower(28,93,(3,16,29,42,63),False)
stair_tower(92,93,(3,16,29,42,63),True)
# Secondary local lift towers diversify the underhive circulation.
stair_tower(132,72,(3,16,29,42,63),False)
stair_tower(180,113,(3,16,29,42,63),True)
# Control chapels, transformer blocks and giant pipe forest.
for x0,z0 in ((131,105),(174,68),(174,105)):
    rect(x0,3,z0,x0+12,16,z0+14,ABW("south")); rect(x0+2,4,z0+2,x0+10,15,z0+12,AIR)
    b.put(x0+6,5,z0,DOOR("south")); b.put(x0+6,12,z0,GLOWWIN("south"))
    b.put(x0+4,5,z0+8,CTRL("north")); b.put(x0+8,5,z0+8,COG("north"))
for x in (134,142,176,184):
    pipe_run_z(68,123,18+(x%3)*5,x,"east" if x<159 else "west")
for x,z in ((134,70),(185,70),(134,122),(185,122)):
    rect(x,3,z,x+2,45,z+2,VCON); b.put(x+1,46,z+1,LRED)
# Underhive ceiling socket around the main lift.
rect(154,59,91,164,63,101,FRAME)
rect(157,59,94,161,63,98,AIR)

# Shared underhive micro-detail, lighting and markers.
for _ in range(180):
    x=b.rng.randrange(3,189); z=b.rng.randrange(3,125)
    y=b.rng.randrange(3,34)
    if b.get(x,y,z)==AIR:
        r=b.rng.random()
        if r<.45: b.put(x,y,z,GFUNGUS)
        elif r<.67: b.put(x,y,z,CABLE(["north","south","east","west"][b.rng.randrange(4)]))
        elif r<.82: b.put(x,y,z,SCRAP(["north","south","east","west"][b.rng.randrange(4)]))
        else: b.put(x,y,z,CRATE(["north","south","east","west"][b.rng.randrange(4)]))
for _ in range(80):
    x=b.rng.randrange(3,189); z=b.rng.randrange(3,125)
    if b.get(x,2,z)!=AIR and b.get(x,3,z)==AIR:
        b.put(x,3,z,GFUNGUS if b.rng.random()<.65 else SCONCE("south"))

markers=[
    (11,8,16,"marker_enemy_spawn"),(52,8,48,"marker_patrol_point"),(31,8,31,"marker_loot_point"),
    (75,5,12,"marker_enemy_spawn"),(113,20,46,"marker_loot_point"),(95,20,47,"marker_cover_point"),
    (142,5,12,"marker_enemy_spawn"),(159,3,31,"marker_defense_point"),(159,7,11,"marker_commander_point"),
    (31,5,95,"marker_loot_point"),(10,5,95,"marker_patrol_point"),(52,5,95,"marker_enemy_spawn"),
    (72,5,92,"marker_trade_point"),(96,5,91,"marker_trade_point"),(121,5,91,"marker_trade_point"),
    (159,28,96,"marker_commander_point"),(140,28,96,"marker_patrol_point"),(180,28,96,"marker_patrol_point"),
    (159,4,120,"marker_construction_point"),(159,43,96,"marker_loot_point"),
]
for x,y,z,m in markers:
    b.put(x,y,z,MK(m))

b.resolve()


def slice_module(x0,z0,sx=64,sy=64,sz=64):
    sub=ModuleBuilder(sx,sy,sz,seed=97007+x0+z0)
    for (x,y,z),state in b.grid.items():
        if x0<=x<x0+sx and z0<=z<z0+sz and 0<=y<sy:
            sub.put(x-x0,y,z-z0,state)
    return sub

modules=[
    ("underhive/sump_tunnels_01",0,0),
    ("underhive/collapsed_ruins_01",64,0),
    ("underhive/gang_territory_01",128,0),
    ("underhive/forgotten_catacombs_01",0,64),
    ("underhive/sump_market_01",64,64),
    ("underhive/reactor_abyss_01",128,64),
]
results=[]
for rel,x0,z0 in modules:
    sub=slice_module(x0,z0)
    nonair,pal,size=sub.write_nbt(str(OUT/f"{rel}.nbt"))
    sub.previews(str(PREV/rel.replace('/','_')),
                 plans=[(60,"plan_ceiling"),(44,"plan_upper"),(28,"plan_gallery"),(12,"plan_mid"),(4,"plan_ground")],
                 sections_x=[(31,"section_x31")],sections_z=[(31,"section_z31")])
    results.append((rel,nonair,pal,size))

# Create/update module metadata.
module_root=ROOT/"src/main/resources/data/firstcrusade/hive_modules"
for rel,_,_ in modules:
    meta=module_root/f"{rel}.json"
    data={
        "template":f"firstcrusade:hive/{rel}",
        "category":"underhive",
        "size":[64,64,64],
        "weight":10,
        "sockets":{"north":"underhive_corridor","south":"underhive_corridor","west":"underhive_corridor","east":"underhive_corridor","up":"underhive_ceiling","down":"foundation"},
        "description":"Underhive v2: irregular buried city with monumental sewers, ruins, slums, catacombs and aligned vertical transit."
    }
    meta.parent.mkdir(parents=True,exist_ok=True)
    meta.write_text(json.dumps(data,indent=2,ensure_ascii=False)+"\n",encoding="utf-8")

district=ROOT/"src/main/resources/data/firstcrusade/hive_districts/underhive.json"
ddata={
    "description":"Underhive v2 (192x128x64): six connected subdistricts forming a natural buried city beneath the center of the hive.",
    "modules":[
        {"module":"firstcrusade:underhive/sump_tunnels_01","offset":[0,0,0]},
        {"module":"firstcrusade:underhive/collapsed_ruins_01","offset":[64,0,0]},
        {"module":"firstcrusade:underhive/gang_territory_01","offset":[128,0,0]},
        {"module":"firstcrusade:underhive/forgotten_catacombs_01","offset":[0,0,64]},
        {"module":"firstcrusade:underhive/sump_market_01","offset":[64,0,64]},
        {"module":"firstcrusade:underhive/reactor_abyss_01","offset":[128,0,64]},
    ]
}
district.write_text(json.dumps(ddata,indent=2,ensure_ascii=False)+"\n",encoding="utf-8")

# Full QA previews.
b.previews(str(PREV/"underhive_district_v2"),
           plans=[(60,"plan_ceiling"),(46,"plan_crown"),(31,"plan_galleries"),(16,"plan_mid"),(4,"plan_ground")],
           sections_x=[(31,"section_sump_x31"),(95,"section_ruins_market_x95"),(159,"section_gang_reactor_x159")],
           sections_z=[(31,"section_north_z31"),(63,"section_spine_z63"),(96,"section_south_z96")])

print("Underhive v2")
for rel,nonair,pal,size in results:
    print(f"{rel:40s} blocks={nonair:7d} palette={pal:3d} bytes={size:8d}")
print(f"combined visible states: {sum(1 for v in b.grid.values() if v != AIR)}")
print(PREV)
