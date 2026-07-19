#!/usr/bin/env python3
"""Perimeter completion v2: straight wall sectors and corner bastions.

Adds wall-only and corner districts so HiveCityLayout no longer places a gate in every
perimeter cell. The straight wall reuses the cargo support row and adds a continuous
fortified rear row. Corner bastions are full 192×128×64 L-shaped fortresses.
"""
from pathlib import Path
import json
import sys

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(Path(__file__).resolve().parent))
from hive_module_lib import ModuleBuilder,S,V,AIR,MK,RAIL_RAW  # noqa: E402

OUT=ROOT/"src/main/resources/data/firstcrusade/structures/hive"
PREV=ROOT/"tools/previews_perimeter_v2"; PREV.mkdir(parents=True,exist_ok=True)

def H(n,f="north"): return S(n,facing=f)
ABW=lambda f="north":H("armored_bulkhead_wall",f); RSP=lambda f="north":H("recessed_steel_wall_panel",f)
GAW=lambda f="north":H("gothic_arch_wall",f); PILLAR=S("tall_ribbed_pillar")
BUTT=lambda f="north":H("buttress_column",f); CORNICE=lambda f="north":H("cathedral_cornice",f)
MOLD=lambda f="north":H("lower_wall_molding",f); SPIRECAP=S("spire_cap_block")
BALCONY=lambda f="north":H("balcony_edge_trim",f); BRIDGE=lambda f="north":H("bridge_support_block",f)
DOOR=lambda f="north":H("giant_door_segment",f); LANCET=lambda f="north":H("narrow_lancet_recess",f)
TRI=lambda f="north":H("triangular_relief_panel",f); WIN=lambda f="north":H("window_slot_frame",f)
FRAME=S("heavy_structural_frame"); SEAM=lambda f="north":H("vertical_seam_strip",f)
PIPE=lambda f="north":H("straight_pipe",f); ELBOW=lambda f="north":H("elbow_pipe",f)
PCLAMP=lambda f="north":H("pipe_support_clamp",f); VCON=S("vertical_service_conduit")
VENT=lambda f="north":H("vent_outlet",f); GANTRY=lambda f="north":H("gantry_beam",f)
MACHINE=lambda f="north":H("machine_casing_block",f); HATCH=lambda f="north":H("maintenance_hatch",f)
GLOWWIN=lambda f="north":H("glowing_shrine_window",f); CANDLE=lambda f="north":H("candle_alcove",f)
SHRINE=lambda f="north":H("shrine_recess",f); CATHF=S("cathedral_floor_tile")
METALF=S("metal_floor_plate"); FLOORGRATE=S("floor_grate"); RAIL=lambda f="north":H("balustrade_railing",f)
SKULLP=lambda f="north":H("skull_relief_panel",f); GARGOYLE=lambda f="north":H("gargoyle_pedestal",f)
CRATE=lambda f="north":H("industrial_crate",f); BRAZIER=S("brazier_block")
PEDGE=lambda f="north":H("reinforced_platform_edge",f); HAZ=S("hazard_grated_floor")
ASH=S("reinforced_ashcrete"); ASH_CR=S("cracked_reinforced_ashcrete"); STEEL=S("riveted_steel_block")
RUST=S("rusted_riveted_steel"); CATW=S("industrial_catwalk"); LYEL=S("yellow_industrial_lumen")
LRED=S("red_emergency_lumen"); CHAIN=V("chain"); SST=lambda f="north":S("riveted_steel_stairs",facing=f,half="bottom")
SUPPLY=lambda f="north":H("supply_crate",f); CTRL=lambda f="north":H("control_panel",f)
FLOOD=lambda f="north":H("industrial_floodlight",f); BEACON=S("warning_beacon")


def rect(b,x0,y0,z0,x1,y1,z1,block): b.fill(x0,y0,z0,x1,y1,z1,block)

def wall_face_z(b,x0,x1,z,y0,y1,facing):
    for x in range(x0,x1+1):
        for y in range(y0,y1+1):
            if y in (y0,y1) or y%10==0: block=CORNICE(facing) if y%20==0 else MOLD(facing)
            elif x%8==0: block=PILLAR
            elif (x+y)%7==0: block=SEAM(facing)
            else: block=ABW(facing) if (x+y)%3 else RSP(facing)
            b.put(x,y,z,block)
    for x in range(x0+4,x1-3,8):
        for y in (10,22,34):
            if y<y1-2: b.put(x,y,z,WIN(facing)); b.put(x,y+1,z,LANCET(facing))
    for x in range(x0+9,x1-8,18): b.put(x,min(y1-3,28),z,TRI(facing))

def wall_face_x(b,z0,z1,x,y0,y1,facing):
    for z in range(z0,z1+1):
        for y in range(y0,y1+1):
            if y in (y0,y1) or y%10==0: block=CORNICE(facing) if y%20==0 else MOLD(facing)
            elif z%8==0: block=PILLAR
            elif (z+y)%7==0: block=SEAM(facing)
            else: block=ABW(facing) if (z+y)%3 else RSP(facing)
            b.put(x,y,z,block)
    for z in range(z0+4,z1-3,8):
        for y in (10,22,34):
            if y<y1-2: b.put(x,y,z,WIN(facing)); b.put(x,y+1,z,LANCET(facing))

def tower(b,cx,cz,r,h,facings=("south","north","east","west")):
    # octagonal stepped bastion
    for y in range(2,h+1):
        cut=3+max(0,(y-(h-14))//4)
        for x in range(cx-r,cx+r+1):
            for z in range(cz-r,cz+r+1):
                if abs(x-cx)+abs(z-cz)>2*r-cut: continue
                boundary=(abs(x-cx)+abs(z-cz)>=2*r-cut-1 or abs(x-cx)==r or abs(z-cz)==r)
                if boundary: b.put(x,y,z,ABW("south") if (x+z+y)%4 else PILLAR)
    for x in range(cx-r+2,cx+r-1): b.put(x,h+1,cz-r+1,CORNICE("south")); b.put(x,h+1,cz+r-1,CORNICE("north"))
    for z in range(cz-r+2,cz+r-1): b.put(cx-r+1,h+1,z,CORNICE("east")); b.put(cx+r-1,h+1,z,CORNICE("west"))
    for x,z in ((cx-r+2,cz-r+2),(cx+r-2,cz-r+2),(cx-r+2,cz+r-2),(cx+r-2,cz+r-2)):
        b.put(x,h+2,z,SPIRECAP)
    for f,(x,z) in zip(facings,((cx,cz-r),(cx,cz+r),(cx-r,cz),(cx+r,cz))):
        b.put(x,12,z,GLOWWIN(f)); b.put(x,23,z,SKULLP(f)); b.put(x,35,z,FLOOD(f))

def straight_wall_builder():
    b=ModuleBuilder(192,64,64,seed=99021)
    rect(b,0,0,0,191,1,63,ASH_CR)
    # wall mass centered at z38, broad enough to feel monumental
    rect(b,0,2,24,191,43,52,ASH)
    rect(b,2,3,27,189,41,49,AIR)
    wall_face_z(b,0,191,24,2,46,"south"); wall_face_z(b,0,191,52,2,46,"north")
    # internal corridors and top wall-walk
    rect(b,0,4,35,191,8,41,METALF); rect(b,0,25,34,191,29,42,CATW)
    rect(b,0,47,27,191,47,49,CATHF)
    for x in range(192):
        b.put(x,48,27,RAIL("south")); b.put(x,48,49,RAIL("north"))
    # unequal towers and buttresses
    for cx,r,h in ((13,9,53),(45,7,47),(79,10,58),(112,7,49),(145,9,55),(179,8,51)):
        tower(b,cx,38,r,h)
    for x in range(4,189,8):
        rect(b,x,2,22,x,37,24,BUTT("south")); rect(b,x,2,52,x,37,55,BUTT("north"))
        if x%16==4: b.put(x,38,21,GARGOYLE("south")); b.put(x,38,56,GARGOYLE("north"))
    # maintenance entrances and pipe systems
    for cx in (31,95,159):
        rect(b,cx-5,4,24,cx+5,12,28,AIR); b.put(cx,4,24,DOOR("south"))
        b.put(cx-3,5,27,CTRL("north")); b.put(cx+3,5,27,HATCH("north"))
        for y in range(14,39,8): b.put(cx,y,24,SHRINE("south"))
    for y,z in ((18,20),(31,57),(42,19)):
        for x in range(2,190): b.put(x,y,z,PIPE("north"))
        for x in range(8,190,16): b.put(x,y-1,z,PCLAMP("north"))
    for x in range(8,188,24):
        for y in range(49,60): b.put(x,y,38,VCON)
        b.put(x,60,38,BEACON)
    for x,y,z,m in ((31,6,38,"marker_patrol_point"),(95,27,38,"marker_defense_point"),(159,48,38,"marker_patrol_point"),(13,6,38,"marker_guardsman_spawn"),(179,6,38,"marker_guardsman_spawn")):
        b.put(x,y,z,MK(m))
    b.resolve(); return b

def corner_builder():
    b=ModuleBuilder(192,64,128,seed=99022)
    rect(b,0,0,0,191,1,127,ASH_CR)
    # Support yards inside the L, varied masses and open roads.
    for x0,z0,x1,z1,h in ((38,7,69,35,19),(79,10,111,42,27),(122,5,153,34,22),(157,19,187,55,30),
                           (38,72,68,112,24),(78,75,112,119,18),(122,70,155,113,28)):
        rect(b,x0,2,z0,x1,h,z1,ASH); rect(b,x0+2,3,z0+2,x1-2,h-1,z1-2,AIR)
        wall_face_z(b,x0,x1,z0,3,h,"south"); wall_face_z(b,x0,x1,z1,3,h,"north")
        b.put((x0+x1)//2,4,z0,DOOR("south"))
    # South wall arm.
    rect(b,0,2,92,191,45,123,ASH); rect(b,3,3,96,188,42,119,AIR)
    wall_face_z(b,0,191,92,2,48,"south"); wall_face_z(b,0,191,123,2,48,"north")
    # West wall arm through the full 128 depth.
    rect(b,0,2,0,31,49,127,ASH); rect(b,4,3,3,27,45,124,AIR)
    wall_face_x(b,0,127,31,2,51,"west"); wall_face_x(b,0,127,0,2,51,"east")
    # Huge corner bastion plus subsidiary towers.
    tower(b,23,104,20,61)
    for cx,cz,r,h in ((15,18,10,49),(15,61,8,45),(64,104,9,53),(111,104,7,47),(159,104,10,56)):
        tower(b,cx,cz,r,h)
    # Walks on both arms and internal bridges.
    rect(b,2,50,7,27,50,120,CATHF); rect(b,5,49,95,187,49,120,CATHF)
    for z in range(7,121): b.put(3,51,z,RAIL("east")); b.put(27,51,z,RAIL("west"))
    for x in range(5,188): b.put(x,50,95,RAIL("south")); b.put(x,50,120,RAIL("north"))
    for y,z in ((20,88),(34,125)):
        for x in range(2,190): b.put(x,y,z,PIPE("north"))
    for y,x in ((25,34),(39,1)):
        for z in range(2,126): b.put(x,y,z,PIPE("east"))
    # Inner corner shrine and command chamber.
    rect(b,34,4,82,58,20,108,CATHF); rect(b,36,5,84,56,19,106,AIR)
    b.put(46,5,84,DOOR("south")); b.put(46,13,84,GLOWWIN("south")); b.put(46,6,104,SHRINE("north"))
    b.put(40,6,100,BRAZIER); b.put(52,6,100,BRAZIER)
    # roads and sockets
    rect(b,25,2,0,38,2,91,METALF); rect(b,32,2,60,191,2,73,METALF)
    for x,y,z,m in ((23,52,104,"marker_commander_point"),(64,50,104,"marker_patrol_point"),(159,50,104,"marker_patrol_point"),(46,6,96,"marker_guardsman_spawn"),(95,3,66,"marker_vehicle_point")):
        b.put(x,y,z,MK(m))
    b.resolve(); return b

wall=straight_wall_builder(); corner=corner_builder()


def slice_x(src,x0,z0=0):
    sub=ModuleBuilder(64,64,64,seed=99100+x0+z0)
    for (x,y,z),state in src.grid.items():
        if x0<=x<x0+64 and z0<=z<z0+64: sub.put(x-x0,y,z-z0,state)
    return sub

results=[]
# Straight wall rear row modules.
wall_rels=[("gates/hive_wall_line_w_01",0),("gates/hive_wall_line_c_01",64),("gates/hive_wall_line_e_01",128)]
for rel,x0 in wall_rels:
    sub=slice_x(wall,x0)
    nonair,pal,size=sub.write_nbt(str(OUT/f"{rel}.nbt")); results.append((rel,nonair,pal,size))
# Corner full six modules.
corner_rels=[]
for z0,tag in ((0,"front"),(64,"rear")):
    for x0,side in ((0,"w"),(64,"c"),(128,"e")):
        rel=f"gates/hive_corner_{tag}_{side}_01"; corner_rels.append((rel,x0,z0))
        sub=slice_x(corner,x0,z0)
        nonair,pal,size=sub.write_nbt(str(OUT/f"{rel}.nbt")); results.append((rel,nonair,pal,size))

module_root=ROOT/"src/main/resources/data/firstcrusade/hive_modules"
for rel,_,*rest in [(r,x) for r,x in wall_rels]+corner_rels:
    meta={"template":f"firstcrusade:hive/{rel}","category":"gate","size":[64,64,64],"weight":10,
          "sockets":{"north":"hive_wall","south":"hive_wall","west":"hive_wall","east":"hive_wall","up":"wall_walk","down":"foundation"},
          "description":"Hive perimeter v2: gate-free wall or corner bastion with layered towers and internal circulation."}
    p=module_root/f"{rel}.json"; p.parent.mkdir(parents=True,exist_ok=True); p.write_text(json.dumps(meta,indent=2)+"\n")

# Districts. Wall line reuses the established cargo support row.
wall_d={"description":"Straight hive wall v2 (192x128x64): support yards behind a continuous layered wall without a gate.","modules":[
    {"module":"firstcrusade:cargo/warehouse_01","offset":[0,0,0]},
    {"module":"firstcrusade:cargo/cargo_yard_01","offset":[64,0,0]},
    {"module":"firstcrusade:cargo/military_depot_01","offset":[128,0,0]},
    {"module":"firstcrusade:gates/hive_wall_line_w_01","offset":[0,0,64]},
    {"module":"firstcrusade:gates/hive_wall_line_c_01","offset":[64,0,64]},
    {"module":"firstcrusade:gates/hive_wall_line_e_01","offset":[128,0,64]}]}
corner_d={"description":"Corner bastion v2 (192x128x64): L-shaped perimeter fortress with a massive corner tower and support compounds.","modules":[]}
for rel,x0,z0 in corner_rels: corner_d["modules"].append({"module":f"firstcrusade:{rel}","offset":[x0,0,z0]})
droot=ROOT/"src/main/resources/data/firstcrusade/hive_districts"
(droot/"hive_wall_line.json").write_text(json.dumps(wall_d,indent=2,ensure_ascii=False)+"\n",encoding="utf-8")
(droot/"hive_corner_bastion.json").write_text(json.dumps(corner_d,indent=2,ensure_ascii=False)+"\n",encoding="utf-8")

wall.previews(str(PREV/"hive_wall_line_v2"),plans=[(61,"plan_top"),(48,"plan_walk"),(28,"plan_gallery"),(6,"plan_ground")],sections_x=[(31,"section_w"),(95,"section_c"),(159,"section_e")],sections_z=[(38,"section_wall")])
corner.previews(str(PREV/"hive_corner_bastion_v2"),plans=[(61,"plan_top"),(50,"plan_walk"),(30,"plan_gallery"),(6,"plan_ground")],sections_x=[(23,"section_corner"),(95,"section_inner")],sections_z=[(104,"section_wall"),(50,"section_arm")])

b=corner
print("Perimeter v2")
for rel,nonair,pal,size in results: print(f"{rel:42s} blocks={nonair:7d} palette={pal:3d} bytes={size:8d}")
print(f"wall visible={sum(1 for v in wall.grid.values() if v!=AIR)} corner visible={sum(1 for v in corner.grid.values() if v!=AIR)}")
print(PREV)
