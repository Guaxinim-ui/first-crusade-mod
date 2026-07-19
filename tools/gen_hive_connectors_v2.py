#!/usr/bin/env python3
"""Phase 8 — rear transition belts and aligned vertical transit.

Expands Manufactorum, Hab Stacks and Administratum from 192×64×64 to the full
192×64×128 footprint expected by HiveCityLayout. Each level receives a unique,
continuous 192×64×64 rear belt split into three modules. Lift/stair towers use the
same X/Z coordinates at every level, so the three stacked districts connect cleanly.
"""
from pathlib import Path
import json
import math
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(Path(__file__).resolve().parent))
from hive_module_lib import ModuleBuilder, S, V, AIR, MK, RAIL_RAW  # noqa: E402

OUT = ROOT / "src/main/resources/data/firstcrusade/structures/hive"
PREV = ROOT / "tools/previews_connectors_v2"
PREV.mkdir(parents=True, exist_ok=True)


def H(name, facing="north"):
    return S(name, facing=facing)

ABW=lambda f="north":H("armored_bulkhead_wall",f)
RSP=lambda f="north":H("recessed_steel_wall_panel",f)
GAW=lambda f="north":H("gothic_arch_wall",f)
PILLAR=S("tall_ribbed_pillar")
BUTT=lambda f="north":H("buttress_column",f)
CORNICE=lambda f="north":H("cathedral_cornice",f)
MOLD=lambda f="north":H("lower_wall_molding",f)
SPIRECAP=S("spire_cap_block")
BALCONY=lambda f="north":H("balcony_edge_trim",f)
BRIDGE=lambda f="north":H("bridge_support_block",f)
DOOR=lambda f="north":H("giant_door_segment",f)
LANCET=lambda f="north":H("narrow_lancet_recess",f)
TRI=lambda f="north":H("triangular_relief_panel",f)
WIN=lambda f="north":H("window_slot_frame",f)
FRAME=S("heavy_structural_frame")
SEAM=lambda f="north":H("vertical_seam_strip",f)

PIPE=lambda f="north":H("straight_pipe",f)
ELBOW=lambda f="north":H("elbow_pipe",f)
PCLAMP=lambda f="north":H("pipe_support_clamp",f)
VCON=S("vertical_service_conduit")
CABLE=lambda f="north":H("cable_bundle_block",f)
VENT=lambda f="north":H("vent_outlet",f)
FVENT=S("floor_vent")
LIFT=lambda f="north":H("lift_rail",f)
GANTRY=lambda f="north":H("gantry_beam",f)
ANCHOR=lambda f="north":H("suspended_track_anchor",f)
HATCH=lambda f="north":H("maintenance_hatch",f)
MACHINE=lambda f="north":H("machine_casing_block",f)
HAZGRATE=S("hazard_grated_floor")
PEDGE=lambda f="north":H("reinforced_platform_edge",f)

GLOWWIN=lambda f="north":H("glowing_shrine_window",f)
STAINED=lambda f="north":H("stained_window_variant",f)
CANDLE=lambda f="north":H("candle_alcove",f)
SCONCE=lambda f="north":H("wall_sconce",f)
SHRINE=lambda f="north":H("shrine_recess",f)
BLOOD=S("bloodstained_floor_tile")
CATHF=S("cathedral_floor_tile")
METALF=S("metal_floor_plate")
FLOORGRATE=S("floor_grate")
CATHSTAIR=lambda f="north":H("cathedral_stair_block",f)
SLAB=S("landing_slab")
RAIL=lambda f="north":H("balustrade_railing",f)
SKULLP=lambda f="north":H("skull_relief_panel",f)
GARGOYLE=lambda f="north":H("gargoyle_pedestal",f)
CRATE=lambda f="north":H("industrial_crate",f)
BRAZIER=S("brazier_block")

ASH=S("reinforced_ashcrete"); ASH_CR=S("cracked_reinforced_ashcrete")
STEEL=S("riveted_steel_block"); RUST=S("rusted_riveted_steel")
ARMOR=S("armored_hive_plating"); GRATE=S("industrial_grating"); CATW=S("industrial_catwalk")
LYEL=S("yellow_industrial_lumen"); LGRN=S("green_industrial_lumen"); LRED=S("red_emergency_lumen")
CHAIN=V("chain"); LADDER_E=V("ladder",facing="east")
SST=lambda f="north",half="bottom":S("riveted_steel_stairs",facing=f,half=half)
AST=lambda f="north",half="bottom":S("reinforced_ashcrete_stairs",facing=f,half=half)
BENCH=lambda f="north":H("hive_bench",f); CHAIR=lambda f="north":H("hive_chair",f)
TABLE=S("hive_table"); SHELF=lambda f="north":H("shelf_unit",f)
SUPPLY=lambda f="north":H("supply_crate",f); PROP=lambda f="north":H("imperial_propaganda_panel",f)
FLOOD=lambda f="north":H("industrial_floodlight",f); CTRL=lambda f="north":H("control_panel",f)
COG=lambda f="north":H("cogitator_console",f); BANNER=lambda f="north",p=0:S("aquila_banner",facing=f,part=p)
SAINT=lambda f="north",p=0:S("saint_statue",facing=f,part=p)
AQUILA_ST=lambda f="north":H("aquila_statue",f)
BEACON=S("warning_beacon")
CORRUG=lambda axis="y":S("corrugated_wall",axis=axis)

SX,SY,SZ=192,64,64


def rect(b,x0,y0,z0,x1,y1,z1,block):
    b.fill(x0,y0,z0,x1,y1,z1,block)


def bridge_x(b,x0,x1,y,z,width=5,ornate=False):
    rect(b,x0,y,z,x1,y,z+width-1,CATHF if ornate else CATW)
    edge=RAIL if ornate else PEDGE
    for x in range(x0,x1+1):
        b.put(x,y+1,z,edge("south")); b.put(x,y+1,z+width-1,edge("north"))
    for x in range(x0,x1+1,8):
        rect(b,x,y-6,z+1,x,y-1,z+width-2,BRIDGE("south"))


def bridge_z(b,z0,z1,y,x,width=5,ornate=False):
    rect(b,x,y,z0,x+width-1,y,z1,CATHF if ornate else CATW)
    edge=RAIL if ornate else PEDGE
    for z in range(z0,z1+1):
        b.put(x,y+1,z,edge("east")); b.put(x+width-1,y+1,z,edge("west"))
    for z in range(z0,z1+1,8):
        rect(b,x+1,y-6,z,x+width-2,y-1,z,BRIDGE("east"))


def lift_tower(b,cx,cz,style="industrial"):
    x0,z0=cx-4,cz-4
    rect(b,x0,0,z0,x0+8,63,z0+8,AIR)
    for x,z in ((x0,z0),(x0+8,z0),(x0,z0+8),(x0+8,z0+8)):
        rect(b,x,0,z,x,63,z,FRAME if style!="admin" else PILLAR)
    for y in range(1,63):
        b.put(x0+1,y,z0+1,LIFT("east")); b.put(x0+2,y,z0+1,LADDER_E)
        if y%8==0: b.put(x0+7,y,z0+1,LGRN if style=="industrial" else LYEL)
    floors=(2,15,28,41,54,63)
    for y in floors:
        rect(b,x0,y,z0,x0+8,y,z0+8,FLOORGRATE if style!="admin" else CATHF)
        for x in range(x0,x0+9):
            b.put(x,y+1,z0,RAIL_RAW); b.put(x,y+1,z0+8,RAIL_RAW)
    # Broad helical stair, navigable in-game.
    for y in range(2,63):
        p=(y-2)%24
        if p<6: x=x0+1+p; z=z0+7; f="east"
        elif p<12: x=x0+7; z=z0+7-(p-6); f="north"
        elif p<18: x=x0+7-(p-12); z=z0+1; f="west"
        else: x=x0+1; z=z0+1+(p-18); f="south"
        b.put(x,y,z,CATHSTAIR(f) if style=="admin" else SST(f))
    # Top/bottom sockets remain open.
    rect(b,cx-2,0,cz-2,cx+2,1,cz+2,AIR)
    rect(b,cx-2,62,cz-2,cx+2,63,cz+2,AIR)


def common_ground(b,style):
    # Full foundation, but with three deep canyon courts so the belt is not a flat slab.
    rect(b,0,0,0,191,1,63,ASH_CR if style=="industrial" else ASH)
    for x in range(0,192):
        for z in range(64):
            if (x*13+z*7)%31==0: b.put(x,2,z,ASH_CR)
    # North/south through-road aligned with each existing 64-block module socket.
    for mx in (0,64,128):
        rect(b,mx+25,2,0,mx+38,2,63,METALF if style!="admin" else CATHF)
        for z in range(64):
            b.put(mx+29,2,z,HAZGRATE if style=="industrial" else SLAB)
            b.put(mx+34,2,z,HAZGRATE if style=="industrial" else SLAB)
        rect(b,mx+25,3,0,mx+38,10,3,AIR)
        rect(b,mx+25,3,60,mx+38,10,63,AIR)
    # East-west boulevard unifies the three slices.
    rect(b,0,3,27,191,3,36,METALF if style!="admin" else CATHF)
    for x in range(192):
        if x%4==0: b.put(x,3,31,FVENT if style=="industrial" else SLAB)
    # Three aligned vertical cores.
    for cx in (31,95,159): lift_tower(b,cx,32,style)


def industrial_belt():
    b=ModuleBuilder(SX,SY,SZ,seed=98011)
    common_ground(b,"industrial")
    # Deep service pits between road and side halls.
    for x0,x1 in ((4,21),(42,58),(68,84),(106,123),(132,148),(170,187)):
        rect(b,x0,2,8,x1,20,22,AIR)
        rect(b,x0,2,42,x1,18,57,AIR)
        for x in range(x0,x1+1):
            b.put(x,2,8,PEDGE("south")); b.put(x,2,22,PEDGE("north"))
            b.put(x,2,42,PEDGE("south")); b.put(x,2,57,PEDGE("north"))
    # Unequal machine halls and gantries.
    halls=[(3,5,22,24,25),(41,39,60,59,18),(67,5,88,22,34),(104,40,125,59,25),
           (131,4,151,23,20),(168,39,190,59,31)]
    for x0,z0,x1,z1,h in halls:
        for y in range(3,h):
            for x in range(x0,x1+1):
                b.put(x,y,z0,ABW("south") if (x+y)%4 else SEAM("south"))
                b.put(x,y,z1,RSP("north") if (x+y)%4 else VENT("north"))
            for z in range(z0,z1+1):
                b.put(x0,y,z,ABW("east")); b.put(x1,y,z,RSP("west"))
        rect(b,x0+2,3,z0+2,x1-2,3,z1-2,METALF)
        for x in range(x0+3,x1-2,6): b.put(x,h,z0+2,VCON)
        b.put((x0+x1)//2,4,z0,DOOR("south"))
    # Pipe forest, suspended tracks, crane bridges.
    for y,z in ((18,12),(25,19),(34,48),(43,55)):
        for x in range(2,190):
            if x%16==0: b.put(x,y-1,z,PCLAMP("north"))
            b.put(x,y,z,PIPE("north"))
    for x in (13,51,77,116,140,180):
        for z in range(4,60): b.put(x,38,z,GANTRY("east"))
        for z in range(8,60,12):
            b.put(x,37,z,ANCHOR("east"));
            for y in range(31,37): b.put(x,y,z,CHAIN)
    bridge_x(b,2,189,14,29,5); bridge_x(b,6,185,31,47,4)
    bridge_z(b,4,59,22,21,4); bridge_z(b,4,59,28,167,4)
    # Transformer yards and service detail.
    for cx in (15,47,79,111,143,175):
        for dx,dz in ((-4,-3),(4,-3),(-4,3),(4,3)):
            b.put(cx+dx,4,32+dz,MACHINE("south")); b.put(cx+dx,5,32+dz,CTRL("south"))
        b.put(cx,4,32,HATCH("south"))
    for _ in range(90):
        x=b.rng.randrange(2,190); z=b.rng.randrange(3,61); y=b.rng.choice((3,4,15,23,32))
        if b.get(x,y,z)==AIR:
            b.put(x,y,z,CRATE(["north","south","east","west"][b.rng.randrange(4)]) if b.rng.random()<.55 else CABLE("south"))
    markers=[(15,4,31,"marker_worker_spawn"),(48,4,31,"marker_worker_spawn"),(80,4,31,"marker_vehicle_point"),
             (112,4,31,"marker_worker_spawn"),(144,4,31,"marker_patrol_point"),(176,4,31,"marker_vehicle_point"),
             (31,15,32,"marker_patrol_point"),(95,28,32,"marker_patrol_point"),(159,41,32,"marker_loot_point")]
    for x,y,z,m in markers: b.put(x,y,z,MK(m))
    b.resolve(); return b


def hab_belt():
    b=ModuleBuilder(SX,SY,SZ,seed=98012)
    common_ground(b,"hab")
    # Canyon edges with inhabited stacks, offset and linked by occupied bridges.
    pods=[(2,4,20,20,20),(42,5,61,23,31),(67,41,87,59,24),(105,4,124,22,37),
          (130,39,151,59,28),(169,4,190,25,33)]
    for x0,z0,x1,z1,h in pods:
        for y in range(3,h):
            inset=max(0,(y-18)//7)
            xa,xb=x0+inset,x1-inset; za,zb=z0+inset,z1-inset
            if xa>=xb or za>=zb: break
            for x in range(xa,xb+1):
                b.put(x,y,za,ABW("south") if (x+y)%5 else GAW("south"))
                b.put(x,y,zb,RSP("north") if (x+y)%5 else WIN("north"))
            for z in range(za,zb+1):
                b.put(xa,y,z,ABW("east")); b.put(xb,y,z,RSP("west"))
        rect(b,x0+2,3,z0+2,x1-2,3,z1-2,METALF)
        b.put((x0+x1)//2,4,z0,DOOR("south"))
        for y in range(9,h,7): b.put((x0+x1)//2,y,z0,GLOWWIN("south"))
        for x in range(x0+3,x1-2,5): b.put(x,h,z0+2,SPIRECAP)
    # Market boulevard and transit platforms.
    for x in range(4,188,10):
        rect(b,x,4,24,x+5,9,40,CORRUG("x")); rect(b,x+1,4,25,x+4,8,39,AIR)
        b.put(x+2,4,27,TABLE); b.put(x+1,4,27,CHAIR("east")); b.put(x+4,4,27,CRATE("west"))
        b.put(x+2,8,24,SCONCE("south"))
    rect(b,0,11,27,191,11,36,HAZGRATE)
    for x in range(0,192):
        if x%6==0: b.put(x,12,27,PEDGE("south")); b.put(x,12,36,PEDGE("north"))
    # Inhabited skybridges and balconies.
    bridge_x(b,3,61,19,15,5); bridge_x(b,67,125,26,47,5); bridge_x(b,130,190,22,14,5)
    bridge_x(b,6,187,38,29,5)
    bridge_z(b,5,58,16,18,4); bridge_z(b,4,59,31,113,4); bridge_z(b,5,58,28,176,4)
    for x in range(8,188,12):
        b.put(x,39,29,BALCONY("south")); b.put(x,39,35,BALCONY("north"))
        if x%24==8: b.put(x,40,32,SHRINE("south"))
    # Transit rail and elevator waiting halls.
    for x in range(192):
        b.put(x,6,30,S("rail",shape="east_west") if False else METALF)
    for cx in (31,95,159):
        rect(b,cx-9,3,20,cx+9,12,25,RSP("south")); rect(b,cx-7,4,21,cx+7,11,25,AIR)
        b.put(cx,4,20,DOOR("south")); b.put(cx-4,5,24,BENCH("north")); b.put(cx+4,5,24,BENCH("north"))
        b.put(cx,9,20,STAINED("south"))
    for _ in range(120):
        x=b.rng.randrange(2,190); z=b.rng.randrange(3,61); y=b.rng.choice((3,4,12,20,28,39))
        if b.get(x,y,z)==AIR:
            r=b.rng.random()
            b.put(x,y,z,CRATE("south") if r<.35 else (SUPPLY("south") if r<.65 else CABLE("south")))
    markers=[(12,4,31,"marker_civil_spawn"),(50,4,31,"marker_trade_point"),(78,4,31,"marker_civil_spawn"),
             (113,4,31,"marker_trade_point"),(142,4,31,"marker_civil_spawn"),(179,4,31,"marker_trade_point"),
             (31,12,32,"marker_patrol_point"),(95,29,32,"marker_civil_spawn"),(159,42,32,"marker_loot_point")]
    for x,y,z,m in markers: b.put(x,y,z,MK(m))
    b.resolve(); return b


def admin_belt():
    b=ModuleBuilder(SX,SY,SZ,seed=98013)
    common_ground(b,"admin")
    # Monumental processional canyon with irregular chapel towers.
    towers=[(4,5,22,25,32),(43,38,61,59,26),(67,5,87,24,39),(105,39,124,59,34),
            (131,4,151,23,28),(169,37,190,59,42)]
    for x0,z0,x1,z1,h in towers:
        for y in range(3,h):
            inset=max(0,(y-24)//6)
            xa,xb=x0+inset,x1-inset; za,zb=z0+inset,z1-inset
            if xa>=xb or za>=zb: break
            for x in range(xa,xb+1):
                b.put(x,y,za,GAW("south") if (x+y)%5==0 else ABW("south"))
                b.put(x,y,zb,WIN("north") if (x+y)%7==0 else RSP("north"))
            for z in range(za,zb+1):
                b.put(xa,y,z,PILLAR if z%6==0 else ABW("east")); b.put(xb,y,z,PILLAR if z%6==0 else RSP("west"))
        rect(b,x0+2,3,z0+2,x1-2,3,z1-2,CATHF)
        for x in range(x0+2,x1-1):
            b.put(x,h,z0+1,CORNICE("south")); b.put(x,h,z1-1,CORNICE("north"))
        b.put((x0+x1)//2,4,z0,DOOR("south")); b.put((x0+x1)//2,h+1,z0+2,SPIRECAP)
    # Grand processional bridge and elevated courts.
    bridge_x(b,2,189,17,29,7,True); bridge_x(b,8,184,35,45,5,True)
    bridge_z(b,4,59,24,20,5,True); bridge_z(b,4,59,29,83,5,True); bridge_z(b,4,59,33,171,5,True)
    # Arched colonnades along the boulevard.
    for x in range(3,190,6):
        rect(b,x,4,23,x,16,23,PILLAR); rect(b,x,4,41,x,16,41,PILLAR)
        for dx in range(0,6):
            y=16-int(((dx-3)**2)/3)
            b.put(min(191,x+dx),y,23,GAW("south")); b.put(min(191,x+dx),y,41,GAW("north"))
    # Shrine courts around aligned lift towers.
    for cx in (31,95,159):
        rect(b,cx-12,3,9,cx+12,7,20,CATHF)
        for x in range(cx-12,cx+13):
            b.put(x,8,9,BALCONY("south")); b.put(x,8,20,BALCONY("north"))
        b.put(cx,4,10,SHRINE("south")); b.put(cx-7,4,15,BRAZIER); b.put(cx+7,4,15,BRAZIER)
        b.put(cx,10,9,STAINED("south"))
        for p in range(3):
            b.put(cx-4,4+p,18,SAINT("north",p)); b.put(cx+4,4+p,18,SAINT("north",p))
    # Flying buttresses and banners.
    for x in range(8,188,12):
        rect(b,x,18,7,x,33,7,BUTT("south")); rect(b,x,18,56,x,33,56,BUTT("north"))
        bridge_z(b,8,22,30,x,3,True); bridge_z(b,42,56,30,x,3,True)
        if x%24==8:
            b.put(x,34,31,BANNER("south",0)); b.put(x,35,31,BANNER("south",1))
    for x,z in ((7,7),(57,55),(70,7),(122,55),(134,7),(188,55)):
        b.put(x,34,z,GARGOYLE("south" if z<32 else "north"))
    for _ in range(70):
        x=b.rng.randrange(3,189); z=b.rng.randrange(3,61); y=b.rng.choice((3,4,18,25,36))
        if b.get(x,y,z)==AIR:
            b.put(x,y,z,CANDLE("south") if b.rng.random()<.55 else PROP("south"))
    markers=[(15,4,31,"marker_civil_spawn"),(48,4,31,"marker_patrol_point"),(80,4,31,"marker_civil_spawn"),
             (112,4,31,"marker_patrol_point"),(144,4,31,"marker_civil_spawn"),(176,4,31,"marker_commander_point"),
             (31,18,32,"marker_patrol_point"),(95,36,32,"marker_loot_point"),(159,36,32,"marker_patrol_point")]
    for x,y,z,m in markers: b.put(x,y,z,MK(m))
    b.resolve(); return b


belts={"manufactorum":industrial_belt(),"hab_stacks":hab_belt(),"administratum":admin_belt()}
module_specs={
    "manufactorum":[("connectors/manufactorum_service_w_01",0),("connectors/manufactorum_service_c_01",64),("connectors/manufactorum_service_e_01",128)],
    "hab_stacks":[("connectors/hab_transit_w_01",0),("connectors/hab_transit_c_01",64),("connectors/hab_transit_e_01",128)],
    "administratum":[("connectors/admin_processional_w_01",0),("connectors/admin_processional_c_01",64),("connectors/admin_processional_e_01",128)],
}


def slice_module(src,x0):
    sub=ModuleBuilder(64,64,64,seed=98100+x0)
    for (x,y,z),state in src.grid.items():
        if x0<=x<x0+64:
            sub.put(x-x0,y,z,state)
    return sub

results=[]
module_root=ROOT/"src/main/resources/data/firstcrusade/hive_modules"
for district_id,src in belts.items():
    for rel,x0 in module_specs[district_id]:
        sub=slice_module(src,x0)
        nonair,pal,size=sub.write_nbt(str(OUT/f"{rel}.nbt"))
        sub.previews(str(PREV/rel.replace('/','_')),
                     plans=[(62,"plan_top"),(42,"plan_upper"),(28,"plan_gallery"),(16,"plan_mid"),(4,"plan_ground")],
                     sections_x=[(31,"section_x31")],sections_z=[(31,"section_z31")])
        results.append((rel,nonair,pal,size))
        meta={
            "template":f"firstcrusade:hive/{rel}","category":"connector","size":[64,64,64],"weight":10,
            "sockets":{"north":"street","south":"street","west":"corridor_l2","east":"corridor_l2","up":"vertical_transit","down":"vertical_transit"},
            "description":"Hive City v2 transition module with aligned lift/stair core, bridges and level-specific architecture."
        }
        path=module_root/f"{rel}.json"; path.parent.mkdir(parents=True,exist_ok=True)
        path.write_text(json.dumps(meta,indent=2,ensure_ascii=False)+"\n",encoding="utf-8")

# Append rear rows to the three production districts.
for district_id in ("manufactorum","hab_stacks","administratum"):
    path=ROOT/"src/main/resources/data/firstcrusade/hive_districts"/f"{district_id}.json"
    data=json.loads(path.read_text(encoding="utf-8"))
    base=[m for m in data["modules"] if m["offset"][2] < 64]
    rear=[]
    for idx,(rel,_) in enumerate(module_specs[district_id]):
        rear.append({"module":f"firstcrusade:{rel}","offset":[idx*64,0,64]})
    data["modules"]=base+rear
    label={"manufactorum":"industrial service canyon","hab_stacks":"inhabited transit canyon","administratum":"processional bridge court"}[district_id]
    data["description"]=f"{district_id.replace('_',' ').title()} v2 complete (192x128x64): original monumental front row plus a connected {label} with aligned vertical transit."
    path.write_text(json.dumps(data,indent=2,ensure_ascii=False)+"\n",encoding="utf-8")

# Expose the three builders for preview wrappers; default b is the Hab belt.
b_manufactorum=belts["manufactorum"]
b_hab=belts["hab_stacks"]
b_administratum=belts["administratum"]
b=b_hab

for name,src in belts.items():
    src.previews(str(PREV/f"{name}_rear_belt_v2"),
                 plans=[(62,"plan_top"),(43,"plan_upper"),(29,"plan_gallery"),(17,"plan_mid"),(4,"plan_ground")],
                 sections_x=[(31,"section_w_x31"),(95,"section_c_x95"),(159,"section_e_x159")],
                 sections_z=[(15,"section_front_z15"),(32,"section_spine_z32"),(50,"section_rear_z50")])

print("Connector belts v2")
for rel,nonair,pal,size in results:
    print(f"{rel:44s} blocks={nonair:7d} palette={pal:3d} bytes={size:8d}")
for name,src in belts.items():
    print(f"{name:16s} combined visible={sum(1 for v in src.grid.values() if v != AIR)}")
print(PREV)
