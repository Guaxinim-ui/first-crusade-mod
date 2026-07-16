#!/usr/bin/env python3
"""Biblioteca compartilhada dos geradores de módulo da Hive City.

Contém: constantes de bloco, o ModuleBuilder (grid 3D + primitivas), a resolução de
estados conectáveis (canos/corrimãos/muretas), o escritor NBT (structure template
1.20.1, DataVersion 3465) e prévias PNG de QA.

CONVENÇÕES DE SOCKET (todas em coordenadas LOCAIS do módulo, chão em y=1):
  street       — rua nas faces N/S: pista x25..38 (aço x30..33, hazard x29/34,
                 canaletas gradeadas x26/37 com lúmen verde no y0)
  hive_wall    — corpo de muralha nas faces E/W: massa z16..47, altura até y48;
                 corredor interno y2..5 z29..32; galeria y24..27 z29..32 (piso y23);
                 passadiço no topo (laje y48, andar y49, parapeitos z16 e z46..47)
  cargo_bay    — arco de veículos nas faces E/W: vão z16..27 y2..7 (trilhos z20 e z23)
  cargo_ring   — continuação futura do anel (porta cega detalhada por enquanto)
  ash_wastes   — face externa aberta para o deserto de cinzas
  corridor_l2  — abertura z30..32 y16..19 (usada pelo industrial_street_01)
  underhive_shaft (down) — poço em x48..53, z48..53
  foundation / sealed / canopy — autoexplicativos

gen_hive_module.py (FASE 3) antecede esta lib e mantém uma cópia própria do
encanamento; portar aquele gerador para cá é limpeza futura já anotada no HIVE_CITY.md.
"""
import gzip, struct, io, os, random

AIR = "minecraft:air"

# ---------------------------------------------------------------- chaves de estado
def S(name, **props):
    if not props:
        return f"firstcrusade:{name}"
    p = ";".join(f"{k}={v}" for k, v in sorted(props.items()))
    return f"firstcrusade:{name}|{p}"

def V(name, **props):
    if not props:
        return f"minecraft:{name}"
    p = ";".join(f"{k}={v}" for k, v in sorted(props.items()))
    return f"minecraft:{name}|{p}"

ASH    = S("reinforced_ashcrete");      ASH_CR = S("cracked_reinforced_ashcrete")
STEEL  = S("riveted_steel_block");      RUSTY  = S("rusted_riveted_steel")
ARMOR  = S("armored_hive_plating");     CASING = S("machine_casing")
GRATE  = S("industrial_grating");       CATW   = S("industrial_catwalk")
HAZ    = S("hazard_stripe_panel");      CATHW  = S("cathedral_wall")
GARCH  = S("gothic_arch");              SKULL  = S("skull_wall_relief")
AQUILA = S("aquila_wall_relief")
LYEL   = S("yellow_industrial_lumen");  LGRN   = S("green_industrial_lumen")
LRED   = S("red_emergency_lumen")
CHAIN  = V("chain")

def COL(axis="y"):    return S("imperial_column", axis=axis)
def LSTRIP(axis="y"): return S("hive_lumen_strip", axis=axis)
def VALVE(axis="y"):  return S("pressure_valve", axis=axis)
def VENT(facing):     return S("industrial_vent", facing=facing)
def CONT(facing):     return S("cargo_container", facing=facing)
def AST(facing, half="bottom"): return S("reinforced_ashcrete_stairs", facing=facing, half=half)
def SST(facing, half="bottom"): return S("riveted_steel_stairs", facing=facing, half=half)
def ASLAB(t="bottom"): return S("reinforced_ashcrete_slab", type=t)
def SSLAB(t="bottom"): return S("riveted_steel_slab", type=t)
def LADDER(facing):    return V("ladder", facing=facing)
def FURNACE(facing):   return S("forge_furnace", facing=facing)
def COGITATOR(facing): return S("cogitator_console", facing=facing)
def CTRLPANEL(facing): return S("control_panel", facing=facing)
def PROPAGANDA(facing):return S("imperial_propaganda_panel", facing=facing)
def CONVEYOR(axis="x"):return S("conveyor_belt", axis=axis)
def TURBINE(axis="y"): return S("industrial_turbine", axis=axis)
def BOILER(axis="y"):  return S("boiler_tank", axis=axis)
def STACK(axis="y"):   return S("smoke_stack", axis=axis)
def VENTDUCT(axis="y"):return S("ventilation_duct", axis=axis)
CRUCIBLE = S("smelter_crucible")
PRESS    = S("industrial_press")
COOLANT  = S("coolant_tank")
BRAZIER  = S("cathedral_brazier")
FLOOD    = lambda facing: S("industrial_floodlight", facing=facing)
HANGLAMP = S("hanging_hive_lamp")
BEACON   = S("warning_beacon")
CHAIN2   = lambda axis="y": S("industrial_chain", axis=axis)
CABLE    = lambda axis="y": S("cable_bundle", axis=axis)
HUGEPIPE = "HUGEPIPE"
TRUNK    = "TRUNK"
def SAINT(facing):   return S("saint_statue", facing=facing, part=0)
def SAINT_P(facing,part): return S("saint_statue", facing=facing, part=part)
def GUARDIAN(facing,part=0): return S("imperial_guardian_statue", facing=facing, part=part)
def BANNER(facing,part=0):   return S("aquila_banner", facing=facing, part=part)
def BUST(facing):    return S("saint_bust", facing=facing)
def AQUILA_ST(facing): return S("aquila_statue", facing=facing)
RUG      = S("hive_rug")
def TABLE():  return S("hive_table")
def CHAIR(facing): return S("hive_chair", facing=facing)
def BENCH(facing): return S("hive_bench", facing=facing)
def RAIL_TRACK(shape="east_west"): return V("rail", shape=shape)
def MK(name):          return S(name)

PIPE_RAW, JUNC_RAW, WALL_RAW, RAIL_RAW = "PIPE", "JUNC", "WALL", "RAIL"

CONNECTABLE = {"large_hive_pipe", "pipe_junction", "pressure_valve", "machine_casing",
               "industrial_vent"}
FULL_SOLID = {"reinforced_ashcrete", "cracked_reinforced_ashcrete", "riveted_steel_block",
              "rusted_riveted_steel", "armored_hive_plating", "machine_casing",
              "cathedral_wall", "gothic_arch", "skull_wall_relief", "aquila_wall_relief",
              "hazard_stripe_panel", "yellow_industrial_lumen", "green_industrial_lumen",
              "red_emergency_lumen", "hive_lumen_strip", "imperial_column", "cargo_container",
              "industrial_grating", "forge_furnace", "smelter_crucible", "industrial_turbine",
              "boiler_tank", "smoke_stack", "industrial_press", "conveyor_belt",
              "cogitator_console", "control_panel", "ventilation_duct", "imperial_propaganda_panel"}
DIRS = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0),
        "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}


class ModuleBuilder:
    def __init__(self, sx, sy, sz, seed=40001):
        self.sx, self.sy, self.sz = sx, sy, sz
        self.grid = {}
        self.rng = random.Random(seed)

    # ------------------------------------------------------------ primitivas
    def put(self, x, y, z, b):
        if 0 <= x < self.sx and 0 <= y < self.sy and 0 <= z < self.sz:
            self.grid[(x, y, z)] = b

    def fill(self, x0, y0, z0, x1, y1, z1, b):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.put(x, y, z, b)

    def get(self, x, y, z):
        return self.grid.get((x, y, z), AIR)

    @staticmethod
    def basekey(nb):
        core = nb.split("|")[0]
        return core.split(":")[1] if ":" in core else core

    # ------------------------------------------------------------ padrões reutilizáveis
    def pave_street(self, z0, z1):
        """Pista padrão do socket 'street' (x25..38) entre z0..z1, chão y1/y0."""
        for z in range(z0, z1 + 1):
            for x in range(25, 39):
                self.put(x, 1, z, ASH)
            for x in (30, 31, 32, 33):
                self.put(x, 1, z, STEEL)
            for x in (29, 34):
                self.put(x, 1, z, HAZ if z % 4 < 2 else ASH)
            for x in (26, 37):
                self.put(x, 1, z, GRATE)
                self.put(x, 0, z, LGRN if z % 5 == 0 else ASH_CR)

    def digit(self, d, face_x=None, face_z=None, x0=0, y0=0, z0=0, color=None):
        """Pinta um dígito 5x7 num plano de parede (x fixo OU z fixo), origem no canto
        inferior-esquerdo visto de frente. Substitui os blocos da parede."""
        color = color or HAZ
        FONT = {
            "0": ["01110","10001","10011","10101","11001","10001","01110"],
            "1": ["00100","01100","00100","00100","00100","00100","01110"],
            "2": ["01110","10001","00001","00110","01000","10000","11111"],
            "3": ["11110","00001","00001","01110","00001","00001","11110"],
            "4": ["00010","00110","01010","10010","11111","00010","00010"],
            "5": ["11111","10000","11110","00001","00001","10001","01110"],
            "6": ["00110","01000","10000","11110","10001","10001","01110"],
            "7": ["11111","00001","00010","00100","01000","01000","01000"],
        }
        rows = FONT[str(d)]
        for r, row in enumerate(rows):
            for c, bit in enumerate(row):
                if bit != "1":
                    continue
                y = y0 + (6 - r)
                if face_x is not None:
                    self.put(face_x, y, z0 + c, color)
                else:
                    self.put(x0 + c, y, face_z, color)

    def lamp_mast(self, x, z, h=11):
        """Mastro de holofote do pátio: base hazard, poste, cabeça 2 lúmens amarelos."""
        self.put(x, 1, z, HAZ)
        self.fill(x, 2, z, x, h, z, STEEL)
        self.put(x, h + 1, z, LYEL)
        self.put(x, h + 2, z, LYEL)

    # ------------------------------------------------------------ resolução de estados
    def resolve(self):
        for (x, y, z), b in list(self.grid.items()):
            if b in (PIPE_RAW, JUNC_RAW, HUGEPIPE, TRUNK):
                name = {"PIPE": "large_hive_pipe", "JUNC": "pipe_junction",
                        "HUGEPIPE": "huge_hive_pipe", "TRUNK": "main_pipe_trunk"}[b]
                props = {}
                for d, (dx, dy, dz) in DIRS.items():
                    nb = self.grid.get((x + dx, y + dy, z + dz), AIR)
                    if nb in (PIPE_RAW, JUNC_RAW, HUGEPIPE, TRUNK) or self.basekey(nb) in CONNECTABLE:
                        props[d] = "true"
                self.grid[(x, y, z)] = S(name, **props) if props else S(name)
            elif b == RAIL_RAW:
                props = {}
                for d in ("north", "south", "east", "west"):
                    dx, dy, dz = DIRS[d]
                    nb = self.grid.get((x + dx, y, z + dz), AIR)
                    if nb == RAIL_RAW or self.basekey(nb) in FULL_SOLID:
                        props[d] = "true"
                self.grid[(x, y, z)] = S("industrial_railing", **props) if props else S("industrial_railing")
            elif b == WALL_RAW:
                props = {"up": "true"}
                low = []
                for d in ("north", "south", "east", "west"):
                    dx, dy, dz = DIRS[d]
                    nb = self.grid.get((x + dx, y, z + dz), AIR)
                    if nb == WALL_RAW or self.basekey(nb) in FULL_SOLID:
                        props[d] = "low"
                        low.append(d)
                if len(low) == 2 and tuple(sorted(low)) in (("east", "west"), ("north", "south")):
                    props["up"] = "false" if self.grid.get((x, y + 1, z), AIR) == AIR else "true"
                self.grid[(x, y, z)] = S("reinforced_ashcrete_wall", **props)

    # ------------------------------------------------------------ NBT
    def write_nbt(self, out_path):
        def tag_str(s):
            b = s.encode("utf-8")
            return struct.pack(">H", len(b)) + b

        def write_compound(out, d):
            for k, (t, v) in d.items():
                out.write(bytes([t]))
                out.write(tag_str(k))
                write_payload(out, t, v)
            out.write(b"\x00")

        def write_payload(out, t, v):
            if t == 3:
                out.write(struct.pack(">i", v))
            elif t == 8:
                out.write(tag_str(v))
            elif t == 9:
                et, items = v
                out.write(bytes([et]))
                out.write(struct.pack(">i", len(items)))
                for it in items:
                    write_payload(out, et, it)
            elif t == 10:
                write_compound(out, v)
            else:
                raise TypeError(t)

        def state_to_nbt(key):
            ns_name, *props = key.split("|")
            d = {"Name": (8, ns_name)}
            if props:
                pd = {}
                for kv in props[0].split(";"):
                    k, v = kv.split("=")
                    pd[k] = (8, v)
                d["Properties"] = (10, pd)
            return d

        palette, pindex = [], {}

        def pid(key):
            if key not in pindex:
                pindex[key] = len(palette)
                palette.append(key)
            return pindex[key]

        blocks = []
        for x in range(self.sx):
            for y in range(self.sy):
                for z in range(self.sz):
                    key = self.grid.get((x, y, z), AIR)
                    blocks.append({"pos": (9, (3, [x, y, z])), "state": (3, pid(key))})

        root = {
            "size": (9, (3, [self.sx, self.sy, self.sz])),
            "entities": (9, (0, [])),
            "blocks": (9, (10, blocks)),
            "palette": (9, (10, [state_to_nbt(k) for k in palette])),
            "DataVersion": (3, 3465),
        }
        buf = io.BytesIO()
        buf.write(b"\x0a")
        buf.write(tag_str(""))
        write_compound(buf, root)
        os.makedirs(os.path.dirname(out_path), exist_ok=True)
        with gzip.open(out_path, "wb") as f:
            f.write(buf.getvalue())
        nonair = sum(1 for v in self.grid.values() if v != AIR)
        return nonair, len(palette), os.path.getsize(out_path)

    # ------------------------------------------------------------ prévias
    def previews(self, prefix, plans=(), sections_x=(), sections_z=()):
        try:
            from PIL import Image
        except ImportError:
            return
        COLORS = {
            "air": (18, 18, 22), "reinforced_ashcrete": (70, 74, 78),
            "cracked_reinforced_ashcrete": (58, 60, 64), "riveted_steel_block": (86, 94, 102),
            "rusted_riveted_steel": (140, 84, 38), "armored_hive_plating": (40, 44, 48),
            "machine_casing": (96, 104, 96), "industrial_grating": (120, 126, 132),
            "industrial_catwalk": (150, 156, 162), "industrial_railing": (180, 186, 192),
            "large_hive_pipe": (98, 118, 96), "pipe_junction": (130, 108, 60),
            "pressure_valve": (168, 137, 63), "industrial_vent": (110, 118, 110),
            "gothic_arch": (150, 140, 116), "imperial_column": (128, 122, 104),
            "cathedral_wall": (84, 80, 88), "skull_wall_relief": (199, 189, 156),
            "aquila_wall_relief": (168, 137, 63), "hive_lumen_strip": (255, 220, 130),
            "yellow_industrial_lumen": (240, 190, 74), "green_industrial_lumen": (127, 214, 154),
            "red_emergency_lumen": (214, 84, 56), "hazard_stripe_panel": (216, 165, 22),
            "cargo_container": (74, 110, 80), "reinforced_ashcrete_stairs": (78, 82, 86),
            "riveted_steel_stairs": (92, 100, 108), "reinforced_ashcrete_slab": (74, 78, 82),
            "riveted_steel_slab": (90, 98, 106), "reinforced_ashcrete_wall": (66, 70, 74),
            "chain": (200, 200, 210), "ladder": (160, 120, 70), "rail": (200, 170, 90),
            "forge_furnace": (230, 130, 50), "smelter_crucible": (240, 150, 60),
            "conveyor_belt": (120, 120, 128), "industrial_turbine": (100, 108, 116),
            "boiler_tank": (150, 90, 46), "smoke_stack": (36, 32, 28),
            "cogitator_console": (60, 140, 90), "control_panel": (110, 118, 126),
            "ventilation_duct": (110, 116, 122), "industrial_press": (80, 86, 92),
            "coolant_tank": (70, 150, 96), "imperial_propaganda_panel": (150, 50, 40),
            "cathedral_brazier": (240, 150, 60), "industrial_floodlight": (255, 250, 200),
            "hanging_hive_lamp": (240, 200, 110), "warning_beacon": (220, 90, 60),
            "industrial_chain": (200, 200, 210), "cable_bundle": (60, 60, 66),
            "huge_hive_pipe": (110, 130, 108), "main_pipe_trunk": (150, 100, 55),
            "saint_statue": (180, 172, 150), "imperial_guardian_statue": (180, 172, 150),
            "aquila_banner": (150, 50, 40), "saint_bust": (150, 140, 116),
            "aquila_statue": (168, 137, 63), "hive_rug": (140, 40, 35),
            "hive_table": (110, 118, 126), "hive_chair": (100, 108, 116),
        }
        def col(key):
            core = key.split("|")[0]
            b = core.split(":")[1] if ":" in core else core
            if b.startswith("marker_"):
                return (255, 0, 255)
            return COLORS.get(b, (255, 0, 255))
        SC = 5
        def plan(ymax, fname):
            im = Image.new("RGB", (self.sx * SC, self.sz * SC))
            for x in range(self.sx):
                for z in range(self.sz):
                    c = COLORS["air"]
                    for yy in range(ymax, -1, -1):
                        k = self.grid.get((x, yy, z), AIR)
                        if k != AIR:
                            c = col(k)
                            break
                    for a in range(SC):
                        for b2 in range(SC):
                            im.putpixel((x * SC + a, z * SC + b2), c)
            im.save(fname)
        def section(axis, v, fname):
            w = self.sz if axis == "x" else self.sx
            im = Image.new("RGB", (w * SC, self.sy * SC))
            for i in range(w):
                for y in range(self.sy):
                    k = self.grid.get((v, y, i) if axis == "x" else (i, y, v), AIR)
                    c = col(k) if k != AIR else COLORS["air"]
                    for a in range(SC):
                        for b2 in range(SC):
                            im.putpixel((i * SC + a, (self.sy - 1 - y) * SC + b2), c)
            im.save(fname)
        for ymax, name in plans:
            plan(ymax, f"{prefix}_{name}.png")
        for v, name in sections_x:
            section("x", v, f"{prefix}_{name}.png")
        for v, name in sections_z:
            section("z", v, f"{prefix}_{name}.png")
