#!/usr/bin/env python3
"""Gera todos os JSONs da FASE 2 (blockstates, modelos, item models, loot tables, tags).
Executado uma vez; os JSONs resultantes são os artefatos versionados no repositório."""
import json, os

ROOT = "src/main/resources"
NS = "firstcrusade"
A = f"{ROOT}/assets/{NS}"
D = f"{ROOT}/data"

def w(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")

def bs(name, obj):    w(f"{A}/blockstates/{name}.json", obj)
def bm(name, obj):    w(f"{A}/models/block/{name}.json", obj)
def im(name, obj):    w(f"{A}/models/item/{name}.json", obj)
def loot(name, obj):  w(f"{D}/{NS}/loot_tables/blocks/{name}.json", obj)

def T(n): return f"{NS}:block/{n}"
def M(n): return f"{NS}:block/{n}"

# ------------------------------------------------------------------ helpers
def simple_loot(name):
    return {"type": "minecraft:block", "pools": [{
        "rolls": 1.0, "bonus_rolls": 0.0,
        "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
        "conditions": [{"condition": "minecraft:survives_explosion"}]}]}

def slab_loot(name):
    return {"type": "minecraft:block", "pools": [{
        "rolls": 1.0, "bonus_rolls": 0.0,
        "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}", "functions": [
            {"function": "minecraft:set_count",
             "conditions": [{"condition": "minecraft:block_state_property",
                             "block": f"{NS}:{name}", "properties": {"type": "double"}}],
             "count": 2.0, "add": False},
            {"function": "minecraft:explosion_decay"}]}]}]}

def cube_all(name, tex=None, cutout=False):
    m = {"parent": "minecraft:block/cube_all", "textures": {"all": T(tex or name)}}
    if cutout:
        m = {"parent": "minecraft:block/cube_all", "render_type": "minecraft:cutout",
             "textures": {"all": T(tex or name)}}
    bm(name, m)
    bs(name, {"variants": {"": {"model": M(name)}}})
    im(name, {"parent": M(name)})
    loot(name, simple_loot(name))

def pillar(name, end, side):
    bm(name, {"parent": "minecraft:block/cube_column", "textures": {"end": T(end), "side": T(side)}})
    bs(name, {"variants": {
        "axis=y": {"model": M(name)},
        "axis=z": {"model": M(name), "x": 90},
        "axis=x": {"model": M(name), "x": 90, "y": 90}}})
    im(name, {"parent": M(name)})
    loot(name, simple_loot(name))

def facing(name, front, side, top):
    bm(name, {"parent": "minecraft:block/orientable",
              "textures": {"front": T(front), "side": T(side), "top": T(top)}})
    bs(name, {"variants": {
        "facing=north": {"model": M(name)},
        "facing=east":  {"model": M(name), "y": 90},
        "facing=south": {"model": M(name), "y": 180},
        "facing=west":  {"model": M(name), "y": 270}}})
    im(name, {"parent": M(name)})
    loot(name, simple_loot(name))

# ================================================================== simples (cube_all)
for n in ["reinforced_ashcrete", "cracked_reinforced_ashcrete", "riveted_steel_block",
          "rusted_riveted_steel", "armored_hive_plating", "machine_casing", "cathedral_wall",
          "gothic_arch", "skull_wall_relief", "aquila_wall_relief", "hazard_stripe_panel",
          "yellow_industrial_lumen", "green_industrial_lumen", "red_emergency_lumen"]:
    cube_all(n)
cube_all("industrial_grating", cutout=True)

# ================================================================== pilares
pillar("imperial_column", "imperial_column_end", "imperial_column_side")
pillar("hive_lumen_strip", "hive_lumen_strip_end", "hive_lumen_strip_side")

# ================================================================== com face frontal
facing("industrial_vent", "industrial_vent", "riveted_steel_block", "riveted_steel_block")
facing("cargo_container", "cargo_container_front", "cargo_container_side", "cargo_container_top")

# ================================================================== catwalk
bm("industrial_catwalk", {
    "parent": "minecraft:block/block",
    "textures": {"particle": T("industrial_catwalk"),
                 "top": T("industrial_catwalk"),
                 "steel": T("riveted_steel_block")},
    "elements": [
        {"from": [0, 13, 0], "to": [16, 16, 16], "faces": {
            "up":    {"texture": "#top"},
            "down":  {"texture": "#top"},
            "north": {"texture": "#steel", "cullface": "north"},
            "south": {"texture": "#steel", "cullface": "south"},
            "east":  {"texture": "#steel", "cullface": "east"},
            "west":  {"texture": "#steel", "cullface": "west"}}},
        {"from": [0, 8, 0],  "to": [2, 13, 2],  "faces": {f: {"texture": "#steel"} for f in ["north","south","east","west","down"]}},
        {"from": [14, 8, 0], "to": [16, 13, 2], "faces": {f: {"texture": "#steel"} for f in ["north","south","east","west","down"]}},
        {"from": [0, 8, 14], "to": [2, 13, 16], "faces": {f: {"texture": "#steel"} for f in ["north","south","east","west","down"]}},
        {"from": [14, 8, 14],"to": [16, 13, 16],"faces": {f: {"texture": "#steel"} for f in ["north","south","east","west","down"]}}]})
bs("industrial_catwalk", {"variants": {"": {"model": M("industrial_catwalk")}}})
im("industrial_catwalk", {"parent": M("industrial_catwalk")})
loot("industrial_catwalk", simple_loot("industrial_catwalk"))

# ================================================================== canos
def pipe_arm_faces():
    return {
        "north": {"texture": "#end", "cullface": "north"},
        "up":    {"texture": "#pipe"},
        "down":  {"texture": "#pipe"},
        "east":  {"texture": "#pipe", "rotation": 90},
        "west":  {"texture": "#pipe", "rotation": 90}}

bm("large_hive_pipe_core", {
    "parent": "minecraft:block/block",
    "textures": {"particle": T("large_hive_pipe"), "end": T("large_hive_pipe_end")},
    "elements": [{"from": [5, 5, 5], "to": [11, 11, 11],
                  "faces": {f: {"texture": "#end"} for f in ["north","south","east","west","up","down"]}}]})

bm("large_hive_pipe_arm", {
    "parent": "minecraft:block/block",
    "textures": {"particle": T("large_hive_pipe"), "pipe": T("large_hive_pipe"),
                 "end": T("large_hive_pipe_end")},
    "elements": [{"from": [5, 5, 0], "to": [11, 11, 5], "faces": pipe_arm_faces()}]})

bm("large_hive_pipe_inventory", {
    "parent": "minecraft:block/block",
    "textures": {"particle": T("large_hive_pipe"), "pipe": T("large_hive_pipe"),
                 "end": T("large_hive_pipe_end")},
    "elements": [{"from": [5, 0, 5], "to": [11, 16, 11], "faces": {
        "up": {"texture": "#end"}, "down": {"texture": "#end"},
        "north": {"texture": "#pipe"}, "south": {"texture": "#pipe"},
        "east": {"texture": "#pipe"}, "west": {"texture": "#pipe"}}}]})

def pipe_multipart(core_model):
    return {"multipart": [
        {"apply": {"model": core_model}},
        {"when": {"north": "true"}, "apply": {"model": M("large_hive_pipe_arm")}},
        {"when": {"east": "true"},  "apply": {"model": M("large_hive_pipe_arm"), "y": 90}},
        {"when": {"south": "true"}, "apply": {"model": M("large_hive_pipe_arm"), "y": 180}},
        {"when": {"west": "true"},  "apply": {"model": M("large_hive_pipe_arm"), "y": 270}},
        {"when": {"up": "true"},    "apply": {"model": M("large_hive_pipe_arm"), "x": 270}},
        {"when": {"down": "true"},  "apply": {"model": M("large_hive_pipe_arm"), "x": 90}}]}

bs("large_hive_pipe", pipe_multipart(M("large_hive_pipe_core")))
im("large_hive_pipe", {"parent": M("large_hive_pipe_inventory")})
loot("large_hive_pipe", simple_loot("large_hive_pipe"))

bm("pipe_junction_core", {
    "parent": "minecraft:block/block",
    "textures": {"particle": T("pipe_junction_core"), "core": T("pipe_junction_core")},
    "elements": [{"from": [2, 2, 2], "to": [14, 14, 14],
                  "faces": {f: {"texture": "#core"} for f in ["north","south","east","west","up","down"]}}]})
bm("pipe_junction_inventory", {"parent": M("pipe_junction_core")})
bs("pipe_junction", pipe_multipart(M("pipe_junction_core")))
im("pipe_junction", {"parent": M("pipe_junction_inventory")})
loot("pipe_junction", simple_loot("pipe_junction"))

# ---- válvula (coluna de cano + volante de latão; sem uv explícito = uv inferido do elemento)
def solid(tex):
    return {f: {"texture": tex} for f in ["north","south","east","west","up","down"]}

bm("pressure_valve", {
    "parent": "minecraft:block/block",
    "textures": {"particle": T("large_hive_pipe"), "pipe": T("large_hive_pipe"),
                 "end": T("large_hive_pipe_end"), "trim": T("brass_trim")},
    "elements": [
        {"from": [5, 0, 5], "to": [11, 16, 11], "faces": {
            "up": {"texture": "#end", "cullface": "up"},
            "down": {"texture": "#end", "cullface": "down"},
            "north": {"texture": "#pipe"}, "south": {"texture": "#pipe"},
            "east": {"texture": "#pipe"}, "west": {"texture": "#pipe"}}},
        {"from": [2, 8, 2],  "to": [14, 10, 4],  "faces": solid("#trim")},
        {"from": [2, 8, 12], "to": [14, 10, 14], "faces": solid("#trim")},
        {"from": [2, 8, 4],  "to": [4, 10, 12],  "faces": solid("#trim")},
        {"from": [12, 8, 4], "to": [14, 10, 12], "faces": solid("#trim")},
        {"from": [4, 8, 7],  "to": [12, 10, 9],  "faces": solid("#trim")},
        {"from": [7, 8, 4],  "to": [9, 10, 12],  "faces": solid("#trim")},
        {"from": [6, 7, 6],  "to": [10, 11, 10], "faces": solid("#trim")}]})
bs("pressure_valve", {"variants": {
    "axis=y": {"model": M("pressure_valve")},
    "axis=z": {"model": M("pressure_valve"), "x": 90},
    "axis=x": {"model": M("pressure_valve"), "x": 90, "y": 90}}})
im("pressure_valve", {"parent": M("pressure_valve")})
loot("pressure_valve", simple_loot("pressure_valve"))

# ================================================================== corrimão (pane-style)
bm("industrial_railing_post", {
    "parent": "minecraft:block/block",
    "textures": {"particle": T("riveted_steel_block"), "steel": T("riveted_steel_block")},
    "elements": [{"from": [7, 0, 7], "to": [9, 16, 9],
                  "faces": {f: {"texture": "#steel"} for f in ["north","south","east","west","up","down"]}}]})
bm("industrial_railing_side", {
    "parent": "minecraft:block/block",
    "render_type": "minecraft:cutout",
    "textures": {"particle": T("riveted_steel_block"), "rail": T("industrial_railing"),
                 "steel": T("riveted_steel_block")},
    "elements": [{"from": [7, 0, 0], "to": [9, 16, 7], "faces": {
        "east": {"texture": "#rail", "uv": [0, 0, 7, 16]},
        "west": {"texture": "#rail", "uv": [9, 0, 16, 16]},
        "north": {"texture": "#steel", "uv": [7, 0, 9, 16], "cullface": "north"},
        "up": {"texture": "#steel", "uv": [7, 0, 9, 7]}}}]})
bs("industrial_railing", {"multipart": [
    {"apply": {"model": M("industrial_railing_post")}},
    {"when": {"north": "true"}, "apply": {"model": M("industrial_railing_side")}},
    {"when": {"east": "true"},  "apply": {"model": M("industrial_railing_side"), "y": 90}},
    {"when": {"south": "true"}, "apply": {"model": M("industrial_railing_side"), "y": 180}},
    {"when": {"west": "true"},  "apply": {"model": M("industrial_railing_side"), "y": 270}}]})
im("industrial_railing", {"parent": "minecraft:item/generated",
                          "textures": {"layer0": T("industrial_railing")}})
loot("industrial_railing", simple_loot("industrial_railing"))

# ================================================================== escadas / lajes / mureta
def stairs(name, tex):
    for suffix, parent in [("", "stairs"), ("_inner", "inner_stairs"), ("_outer", "outer_stairs")]:
        bm(f"{name}{suffix}", {"parent": f"minecraft:block/{parent}",
                               "textures": {"bottom": T(tex), "top": T(tex), "side": T(tex)}})
    base_y = {"east": 0, "south": 90, "west": 180, "north": 270}
    variants = {}
    for f, by in base_y.items():
        for half in ["bottom", "top"]:
            for shape, model in [("straight", name), ("inner_left", f"{name}_inner"),
                                 ("inner_right", f"{name}_inner"), ("outer_left", f"{name}_outer"),
                                 ("outer_right", f"{name}_outer")]:
                if half == "bottom":
                    y = by - 90 if shape.endswith("left") else by
                    x = 0
                else:
                    y = by + 90 if shape.endswith("right") else by
                    x = 180
                y %= 360
                v = {"model": M(model)}
                if x: v["x"] = x
                if y: v["y"] = y
                if x or y: v["uvlock"] = True
                variants[f"facing={f},half={half},shape={shape}"] = v
    bs(name, {"variants": variants})
    im(name, {"parent": M(name)})
    loot(name, simple_loot(name))

def slab(name, tex, double_model):
    bm(f"{name}", {"parent": "minecraft:block/slab",
                   "textures": {"bottom": T(tex), "top": T(tex), "side": T(tex)}})
    bm(f"{name}_top", {"parent": "minecraft:block/slab_top",
                       "textures": {"bottom": T(tex), "top": T(tex), "side": T(tex)}})
    bs(name, {"variants": {
        "type=bottom": {"model": M(name)},
        "type=double": {"model": M(double_model)},
        "type=top": {"model": M(f"{name}_top")}}})
    im(name, {"parent": M(name)})
    loot(name, slab_loot(name))

def wall(name, tex):
    bm(f"{name}_post", {"parent": "minecraft:block/template_wall_post", "textures": {"wall": T(tex)}})
    bm(f"{name}_side", {"parent": "minecraft:block/template_wall_side", "textures": {"wall": T(tex)}})
    bm(f"{name}_side_tall", {"parent": "minecraft:block/template_wall_side_tall", "textures": {"wall": T(tex)}})
    bm(f"{name}_inventory", {"parent": "minecraft:block/wall_inventory", "textures": {"wall": T(tex)}})
    mp = [{"when": {"up": "true"}, "apply": {"model": M(f"{name}_post")}}]
    for d, y in [("north", 0), ("east", 90), ("south", 180), ("west", 270)]:
        a = {"model": M(f"{name}_side"), "uvlock": True}
        if y: a["y"] = y
        mp.append({"when": {d: "low"}, "apply": a})
    for d, y in [("north", 0), ("east", 90), ("south", 180), ("west", 270)]:
        a = {"model": M(f"{name}_side_tall"), "uvlock": True}
        if y: a["y"] = y
        mp.append({"when": {d: "tall"}, "apply": a})
    bs(name, {"multipart": mp})
    im(name, {"parent": M(f"{name}_inventory")})
    loot(name, simple_loot(name))

stairs("reinforced_ashcrete_stairs", "reinforced_ashcrete")
slab("reinforced_ashcrete_slab", "reinforced_ashcrete", "reinforced_ashcrete")
wall("reinforced_ashcrete_wall", "reinforced_ashcrete")
stairs("riveted_steel_stairs", "riveted_steel_block")
slab("riveted_steel_slab", "riveted_steel_block", "riveted_steel_block")

# ================================================================== marcadores (FASE 4)
MARKERS = ["marker_civil_spawn", "marker_worker_spawn", "marker_guardsman_spawn",
           "marker_enemy_spawn", "marker_patrol_point", "marker_cover_point",
           "marker_defense_point", "marker_trade_point", "marker_loot_point",
           "marker_commander_point", "marker_vehicle_point", "marker_construction_point"]
for n in MARKERS:
    cube_all(n, cutout=True)   # instabreak: fora das tags de picareta de propósito

# ================================================================== tags
ALL = ["reinforced_ashcrete", "cracked_reinforced_ashcrete", "reinforced_ashcrete_stairs",
       "reinforced_ashcrete_slab", "reinforced_ashcrete_wall", "riveted_steel_block",
       "rusted_riveted_steel", "riveted_steel_stairs", "riveted_steel_slab",
       "armored_hive_plating", "industrial_grating", "industrial_catwalk", "industrial_railing",
       "large_hive_pipe", "pipe_junction", "pressure_valve", "machine_casing", "industrial_vent",
       "gothic_arch", "imperial_column", "cathedral_wall", "skull_wall_relief",
       "aquila_wall_relief", "hive_lumen_strip", "yellow_industrial_lumen",
       "green_industrial_lumen", "red_emergency_lumen", "hazard_stripe_panel", "cargo_container"]

def tag(path, values):
    w(path, {"replace": False, "values": [f"{NS}:{v}" for v in values]})

tag(f"{D}/minecraft/tags/blocks/mineable/pickaxe.json", ALL)
tag(f"{D}/minecraft/tags/blocks/needs_stone_tool.json",
    ["reinforced_ashcrete", "cracked_reinforced_ashcrete", "reinforced_ashcrete_stairs",
     "reinforced_ashcrete_slab", "reinforced_ashcrete_wall", "riveted_steel_block",
     "rusted_riveted_steel", "riveted_steel_stairs", "riveted_steel_slab", "machine_casing",
     "cathedral_wall", "gothic_arch", "imperial_column", "skull_wall_relief", "aquila_wall_relief"])
tag(f"{D}/minecraft/tags/blocks/needs_iron_tool.json", ["armored_hive_plating"])
tag(f"{D}/minecraft/tags/blocks/walls.json", ["reinforced_ashcrete_wall"])
tag(f"{D}/{NS}/tags/blocks/pipe_connectable.json",
    ["large_hive_pipe", "pipe_junction", "pressure_valve", "machine_casing", "industrial_vent"])

print(f"OK — {len(ALL)} blocos cobertos")
