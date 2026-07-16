#!/usr/bin/env python3
"""FASE 3 — Gera o módulo protótipo 'industrial_street_01' (64×48×64) como structure
template .nbt do Minecraft 1.20.1 (DataVersion 3465), pronto para o StructureTemplateManager.

Rua-canyon N-S entre dois edifícios assimétricos (Manufactorum a oeste, Hab/Cargo a leste),
3 pontes em alturas diferentes, dossel de tubulações no topo, torre de manutenção vertical,
setor danificado, iluminação por zona (amarelo=habitado, verde=industrial, vermelho=perigo).

Estados de conexão (canos, corrimãos, escadas, pilares, facing) são calculados AQUI e
gravados no template — a colocação não depende de block updates.

Uso: python3 tools/gen_hive_module.py
Saída: src/main/resources/data/firstcrusade/structures/hive/street/industrial_street_01.nbt
       + prévias PNG em /tmp para inspeção.
"""
import gzip, struct, io, os, random

SX, SY, SZ = 64, 48, 64
AIR = "minecraft:air"
grid = {}   # (x,y,z) -> state key string  (ausente = AIR)
rng = random.Random(40001)

# ---------------------------------------------------------------- state keys
def S(name, **props):
    if not props:
        return f"firstcrusade:{name}"
    p = ";".join(f"{k}={v}" for k, v in sorted(props.items()))
    return f"firstcrusade:{name}|{p}"

def V(name, **props):  # vanilla
    if not props:
        return f"minecraft:{name}"
    p = ";".join(f"{k}={v}" for k, v in sorted(props.items()))
    return f"minecraft:{name}|{p}"

ASH   = S("reinforced_ashcrete");      ASH_CR = S("cracked_reinforced_ashcrete")
STEEL = S("riveted_steel_block");      RUSTY  = S("rusted_riveted_steel")
ARMOR = S("armored_hive_plating");     CASING = S("machine_casing")
GRATE = S("industrial_grating");       CATW   = S("industrial_catwalk")
HAZ   = S("hazard_stripe_panel");      CATHW  = S("cathedral_wall")
GARCH = S("gothic_arch");              SKULL  = S("skull_wall_relief")
AQUILA= S("aquila_wall_relief")
LYEL  = S("yellow_industrial_lumen");  LGRN   = S("green_industrial_lumen")
LRED  = S("red_emergency_lumen")
CHAIN = V("chain")                      # axis=y default

def COL(axis="y"):    return S("imperial_column", axis=axis)
def LSTRIP(axis="y"): return S("hive_lumen_strip", axis=axis)
def VALVE(axis="y"):  return S("pressure_valve", axis=axis)
def VENT(facing):     return S("industrial_vent", facing=facing)
def CONT(facing):     return S("cargo_container", facing=facing)
def AST(facing, half="bottom"):  return S("reinforced_ashcrete_stairs", facing=facing, half=half)
def SST(facing, half="bottom"):  return S("riveted_steel_stairs", facing=facing, half=half)
def ASLAB(t="bottom"): return S("reinforced_ashcrete_slab", type=t)
def SSLAB(t="bottom"): return S("riveted_steel_slab", type=t)
def LADDER(facing):    return V("ladder", facing=facing)
PIPE_RAW, JUNC_RAW, WALL_RAW, RAIL_RAW = "PIPE", "JUNC", "WALL", "RAIL"  # resolvidos no fim

# ---------------------------------------------------------------- primitives
def put(x, y, z, b):
    if 0 <= x < SX and 0 <= y < SY and 0 <= z < SZ:
        grid[(x, y, z)] = b

def fill(x0, y0, z0, x1, y1, z1, b):
    for x in range(min(x0,x1), max(x0,x1)+1):
        for y in range(min(y0,y1), max(y0,y1)+1):
            for z in range(min(z0,z1), max(z0,z1)+1):
                put(x, y, z, b)

def get(x, y, z):
    return grid.get((x, y, z), AIR)

def base(x, y, z):
    return get(x, y, z).split("|")[0].split(":")[1] if get(x,y,z) != AIR else "air"

# ================================================================ FUNDAÇÃO
fill(0, 0, 0, 63, 0, 63, ASH)

# ================================================================ RUA (x 25..38)
for z in range(64):
    for x in range(25, 39):
        put(x, 1, z, ASH)
    for x in (30, 31, 32, 33): put(x, 1, z, STEEL)                  # faixa central
    for x in (29, 34): put(x, 1, z, HAZ if z % 4 < 2 else ASH)      # bordas tracejadas
    for x in (26, 37):                                              # canaletas com brilho
        put(x, 1, z, GRATE)
        put(x, 0, z, LGRN if z % 5 == 0 else ASH_CR)

# postes de luz (base hazard, poste, braço, lúmen pendente)
def lamp(xpost, xarm1, xarm2, z):
    put(xpost, 1, z, HAZ)
    fill(xpost, 2, z, xpost, 7, z, STEEL)
    put(xarm1, 7, z, STEEL); put(xarm2, 7, z, STEEL)
    put(xarm2, 6, z, LYEL)
for z in (8, 20, 40, 56):  lamp(25, 26, 27, z)
for z in (5, 17, 33, 53):  lamp(38, 37, 36, z)

# ================================================================ EDIFÍCIO OESTE (x 0..24) — MANUFACTORUM
# casca
fill(0, 1, 0, 24, 30, 0, ASH); fill(0, 1, 63, 24, 30, 63, ASH)      # paredes N/S
fill(0, 1, 0, 0, 30, 63, ASH)                                       # fundo (borda W)
fill(24, 1, 0, 24, 30, 63, ASH)                                     # fachada
fill(1, 14, 1, 23, 14, 62, STEEL)                                   # teto hall / piso N2
fill(1, 22, 1, 23, 22, 62, STEEL)                                   # teto N2 / piso N3
fill(1, 30, 1, 23, 30, 62, ASH)                                     # laje de cobertura
fill(1, 1, 1, 23, 1, 62, ASH)                                       # piso do hall

# hall: pé-direito 12 (y2..13)
for x in (6, 7):                                                    # faixa hazard
    for z in range(2, 62): put(x, 1, z, HAZ if (x+z) % 2 == 0 else ASH)
for x in (12, 13):                                                  # canaletas verdes
    for z in range(2, 62):
        put(x, 1, z, GRATE); put(x, 0, z, LGRN if z % 6 == 0 else ASH_CR)

# colunas internas 2×2 com base blindada e capitel
for cx in (8, 16):
    for cz in (8, 20, 32, 44, 56):
        fill(cx, 1, cz, cx+1, 1, cz+1, ARMOR)
        fill(cx, 2, cz, cx+1, 12, cz+1, STEEL)
        fill(cx, 13, cz, cx+1, 13, cz+1, CATHW)

# banco de máquinas (parede de fundo, como na imagem 2)
fill(1, 2, 4, 4, 12, 60, CASING)
for z in range(5, 60, 3):
    for y in (3, 6, 9): put(4, y, z, VENT("east"))
for z in range(6, 60, 7): put(4, 4, z, LGRN)
for z in range(8, 58, 9):                                           # canos verticais → teto
    fill(5, 2, z, 5, 4, z, PIPE_RAW)
    put(5, 5, z, VALVE("y"))
    fill(5, 6, z, 5, 12, z, PIPE_RAW)
    put(5, 13, z, JUNC_RAW)

# linhas de lúmen no teto do hall
for x in (10, 18):
    for z in range(3, 61): put(x, 13, z, LSTRIP("z"))

# fachada: contrafortes com caveira + arcos monumentais + arcada N2 + seteiras N3
for bz in (3, 13, 33, 53, 61):
    fill(25, 1, bz, 25, 2, bz+1, ARMOR)
    fill(25, 3, bz, 25, 19, bz+1, ASH)
    put(25, 20, bz, SKULL); put(25, 20, bz+1, SKULL)
    fill(25, 21, bz, 25, 23, bz+1, ASH)
    put(25, 24, bz, AST("west")); put(25, 24, bz+1, AST("west"))
for dz in (8, 30, 52):                                              # portais do hall
    fill(24, 2, dz, 24, 6, dz+3, AIR)
    fill(24, 2, dz-1, 24, 7, dz-1, CATHW); fill(24, 2, dz+4, 24, 7, dz+4, CATHW)
    fill(24, 7, dz, 24, 7, dz+3, GARCH)
    put(24, 9, dz+1, AQUILA); put(24, 9, dz+2, AQUILA)
for z in range(2, 62):                                              # arcada aberta N2
    if z % 4 in (2, 3) and not (24 <= z <= 27):
        fill(24, 16, z, 24, 19, z, AIR)
        put(24, 16, z, RAIL_RAW)
    if z % 8 == 0:
        fill(24, 16, z, 24, 19, z, LSTRIP("y"))
for z in range(2, 62, 6):                                           # seteiras gradeadas N3
    put(24, 24, z, GRATE); put(24, 25, z, GRATE)

# N2 interior: colunas continuam, lúmens amarelos, contêineres como mobília
for cx in (8, 16):
    for cz in (8, 20, 32, 44, 56):
        fill(cx, 15, cz, cx+1, 21, cz+1, STEEL)
for x in range(4, 22, 6):
    for z in range(5, 60, 6): put(x, 21, z, LYEL)
for (x, z, f) in ((3,10,"south"),(4,10,"south"),(3,11,"south"),(18,40,"east"),(18,41,"east")):
    put(x, 15, z, CONT(f))

# abertura de conexão OESTE (N2) na borda x=0
fill(0, 16, 30, 0, 19, 32, AIR)
for z in (30, 31, 32): put(0, 16, z, RAIL_RAW)
fill(0, 20, 30, 0, 20, 32, GARCH)

# escada-de-mão interna (térreo → N2 → N3) junto à fachada
for y in range(2, 30): put(23, y, 2, LADDER("west"))                 # atravessa as lajes

# N3: anexo fechado ao sul + terraço aberto ao norte
fill(1, 23, 40, 23, 29, 40, CATHW)                                   # parede divisória
for z in range(41, 62, 7):
    for x in (6, 12, 18): fill(x, 23, z, x, 29, z, COL("y"))
for x in range(3, 22, 6):
    for z in range(43, 61, 6): put(x, 29, z, LYEL)
# terraço: parapeito + chaminés + fazenda de canos
for x in range(1, 24): put(x, 31, 1, WALL_RAW); put(x, 31, 38, WALL_RAW)
put(0, 31, 1, WALL_RAW)
for z in range(1, 39):
    put(24, 31, z, WALL_RAW); put(0, 31, z, WALL_RAW)                # guarda lado rua + borda W
for z in range(1, 30):
    put(39, 31, z, WALL_RAW)                                         # guarda telhado leste
for (cx, cz) in ((5, 10), (11, 24)):                                 # chaminés 2×2
    fill(cx, 23, cz, cx+1, 43, cz+1, RUSTY)
    fill(cx, 30, cz, cx+1, 30, cz+1, HAZ)
    fill(cx, 44, cz, cx+1, 44, cz+1, ASH_CR)
    fill(cx, 41, cz, cx, 44, cz, AIR)                                # boca oca
for z in range(4, 36, 8):                                            # canos do terraço → dossel
    fill(20, 23, z, 20, 39, z, PIPE_RAW)

# ================================================================ EDIFÍCIO LESTE (x 39..63) — HAB/CARGO
fill(39, 1, 0, 63, 30, 0, ASH); fill(39, 1, 63, 63, 30, 63, ASH)
fill(63, 1, 0, 63, 30, 63, ASH)
fill(39, 1, 0, 39, 30, 63, ASH)                                      # fachada leste da rua
fill(40, 14, 1, 62, 14, 62, STEEL)
fill(40, 22, 1, 62, 22, 62, STEEL)
fill(40, 30, 1, 62, 30, 62, ASH)
fill(40, 1, 1, 62, 1, 62, ASH)
for z in range(5, 62, 10): fill(39, 2, z, 39, 12, z, CATHW)          # pilastras (assimetria)

# ---- baia de carga (sul, z30..62): portão 10×8 com moldura hazard
fill(39, 2, 36, 39, 9, 45, AIR)
fill(39, 10, 35, 39, 10, 46, HAZ)
fill(39, 2, 35, 39, 9, 35, HAZ); fill(39, 2, 46, 39, 9, 46, HAZ)
fill(39, 2, 34, 39, 11, 34, ARMOR); fill(39, 2, 47, 39, 11, 47, ARMOR)
# pilhas de contêineres
for (x0, z0, h, f) in ((44, 33, 3, "south"), (44, 37, 2, "east"), (49, 50, 3, "north"),
                       (53, 50, 2, "west"), (57, 44, 1, "south"), (44, 55, 2, "east")):
    for dy in range(h):
        for dx in range(2):
            for dz in range(2):
                put(x0+dx, 2+dy, z0+dz, CONT(f))
# mezanino catwalk (y9) ao longo de x=60..62 com corrimão
fill(60, 9, 31, 62, 9, 61, CATW)
for z in range(31, 62): put(60, 10, z, RAIL_RAW)
fill(60, 2, 42, 61, 9, 50, AIR)                                      # vão da escada
for i, z in enumerate(range(49, 41, -1)):                            # escada de acesso
    y = 2 + i
    put(60, y, z, AST("north")); put(61, y, z, AST("north"))
    fill(60, 1, z, 61, y-1, z, ASH)
# viga com corrente e gancho de carga
fill(41, 12, 45, 59, 12, 45, STEEL)
for y in (11, 10, 9): put(51, y, 45, CHAIN)
# alçapão p/ futura Underhive
fill(58, 1, 34, 59, 1, 35, GRATE)
put(58, 0, 34, LGRN); put(59, 0, 35, LGRN)
for (hx, hz) in ((57,33),(60,33),(57,36),(60,36)): put(hx, 1, hz, HAZ)

# ---- generatorium (norte, z1..29)
fill(48, 2, 10, 53, 9, 15, CASING)
for c in ((48,10),(53,10),(48,15),(53,15)):
    fill(c[0], 2, c[1], c[0], 9, c[1], ARMOR)
for z in range(11, 15): put(47, 5, z, VENT("west")); put(54, 5, z, VENT("east"))
put(50, 10, 12, JUNC_RAW); fill(50, 11, 12, 50, 13, 12, PIPE_RAW)
put(51, 10, 13, JUNC_RAW); fill(51, 11, 13, 51, 13, 13, PIPE_RAW)
fill(50, 13, 12, 50, 13, 12, VALVE("y"))
# anel de segurança
for x in range(46, 56): put(x, 2, 8, RAIL_RAW); put(x, 2, 17, RAIL_RAW)
for z in range(8, 18): put(46, 2, z, RAIL_RAW); put(55, 2, z, RAIL_RAW)
fill(50, 2, 8, 51, 2, 8, AIR); fill(50, 2, 17, 51, 2, 17, AIR)       # entradas do anel
for z in range(4, 28, 8): put(62, 5, z, LRED)                        # luz de risco

# ---- N2 hab stack: corredor central iluminado + celas + janelas gradeadas
fill(50, 15, 1, 52, 21, 62, AIR)
for z in range(2, 62): put(51, 21, z, LSTRIP("z"))
for z in range(5, 62, 5):                                            # divisórias com portas
    fill(40, 15, z, 49, 21, z, CATHW)
    fill(53, 15, z, 62, 21, z, CATHW)
    fill(49, 15, z, 49, 18, z, AIR); fill(53, 15, z, 53, 18, z, AIR)
for z in range(2, 62):
    if z % 5 in (1, 2):
        put(39, 17, z, GRATE); put(39, 18, z, GRATE)
for z in range(3, 62, 5):
    put(41, 21, z, LYEL); put(61, 21, z, LYEL)
# corredor-espora até a conexão LESTE
fill(53, 15, 30, 62, 18, 32, AIR)
fill(63, 16, 30, 63, 19, 32, AIR)
for z in (30, 31, 32): put(63, 16, z, RAIL_RAW)
fill(63, 20, 30, 63, 20, 32, GARCH)

# ---- N3: mesmo esquema, com SETOR DANIFICADO (noroeste do andar)
fill(50, 23, 1, 52, 29, 62, AIR)
for z in range(2, 62):
    put(51, 29, z, LSTRIP("z") if z > 24 else AIR)
for z in range(5, 62, 5):
    if z < 25 and rng.random() < 0.5: continue                       # divisórias destruídas
    fill(40, 23, z, 49, 29, z, CATHW)
    fill(53, 23, z, 62, 29, z, CATHW)
    fill(49, 23, z, 49, 26, z, AIR); fill(53, 23, z, 53, 26, z, AIR)
for z in range(3, 24, 4): put(41, 23, z, ASH_CR)                     # piso rachado
for z in range(4, 24, 7): put(45, 26, z, LRED)                       # só luz vermelha
for k in range(14):                                                  # entulho
    x, z = rng.randrange(41, 49), rng.randrange(2, 24)
    put(x, 23, z, RUSTY if k % 2 else ASH_CR)
    if k % 3 == 0: put(x, 24, z, ASH_CR)
# telhado leste: tanques e parapeito
for x in range(40, 63): put(x, 31, 1, WALL_RAW); put(x, 31, 29, WALL_RAW)
for (tx, tz) in ((43, 6), (43, 20)):
    fill(tx, 31, tz, tx+2, 33, tz+2, CASING)
    put(tx+1, 34, tz+1, VALVE("y"))

# ---- TORRE DE MANUTENÇÃO (x56..62, z50..61) — conexão vertical até o dossel
fill(56, 1, 50, 62, 38, 50, ASH); fill(56, 1, 61, 62, 38, 61, ASH)
fill(56, 1, 50, 56, 38, 61, ASH); fill(62, 1, 50, 62, 38, 61, ASH)
fill(57, 1, 51, 61, 1, 60, ASH)
fill(57, 2, 51, 61, 38, 60, AIR)                                     # oco interno
for y in (14, 22, 30):                                               # pisos gradeados
    fill(57, y, 51, 61, y, 60, GRATE)
fill(57, 38, 51, 61, 38, 60, STEEL)                                  # tampa
put(61, 38, 55, AIR)                                                 # escotilha
for y in range(2, 38): put(61, y, 55, LADDER("west"))
for y in (14, 22, 30): put(61, y, 55, LADDER("west"))
for y in (16, 24, 32): put(56, y, 53, LRED)                          # embutidos na parede
for y0 in (2, 15, 23, 31):                                           # portas torre↔prédio
    fill(56, y0, 52, 56, y0+2, 53, AIR)

# ================================================================ PONTES
# A — catwalk aberta (y14, z24..27) ligando os pisos N2
fill(25, 14, 24, 38, 14, 27, CATW)
for x in range(25, 39): put(x, 15, 24, RAIL_RAW); put(x, 15, 27, RAIL_RAW)
fill(24, 15, 24, 24, 18, 27, AIR); fill(39, 15, 24, 39, 18, 27, AIR)
for x in (29, 34):                                                   # correntes até o dossel
    for y in range(16, 40): put(x, y, 25 if x == 29 else 26, CHAIN)
put(29, 40, 25, STEEL); put(34, 40, 26, STEEL)                       # placas de ancoragem
# B — ponte fechada de tubulação (y22, z43..47) ligando os pisos N3
fill(25, 22, 43, 38, 22, 47, STEEL)
fill(25, 26, 43, 38, 26, 47, STEEL)
for x in range(25, 39):
    for zw in (43, 47):
        put(x, 23, zw, GRATE if x % 3 == 0 else STEEL)
        put(x, 24, zw, GRATE if x % 3 == 1 else STEEL)
        put(x, 25, zw, STEEL)
fill(25, 23, 44, 38, 25, 46, AIR)                                    # interior 3 de largura
fill(23, 23, 45, 40, 23, 45, PIPE_RAW)                               # cano no eixo do piso
put(22, 23, 45, JUNC_RAW); put(41, 23, 45, JUNC_RAW)
fill(24, 23, 43, 24, 26, 47, AIR); fill(39, 23, 43, 39, 26, 47, AIR)
# C — passarela alta estreita (y30, z8..9) ligando os terraços
fill(25, 30, 8, 38, 30, 9, CATW)
for x in range(25, 39): put(x, 31, 8, RAIL_RAW); put(x, 31, 9, RAIL_RAW)
fill(24, 31, 8, 24, 33, 9, AIR); fill(39, 31, 8, 39, 33, 9, AIR)

# ================================================================ DOSSEL (céu de máquinas)
for zc in (16, 32, 48):                                              # mega-dutos E-W 3×3
    for x in range(64):
        for y in (40, 41, 42):
            for z in (zc-1, zc, zc+1):
                put(x, y, z, RUSTY if (x + y + z) % 3 == 0 else STEEL)
    for xc in (8, 24, 40, 56):                                       # colares blindados
        for y in (40, 41, 42):
            for z in (zc-1, zc, zc+1):
                put(xc, y, z, ARMOR)
        put(xc, 43, zc, VALVE("x"))
    for xl in (14, 30, 46):                                          # correntes + lampiões
        for y in (39, 38, 37, 36): put(xl, y, zc, CHAIN)
        put(xl, 35, zc, LYEL)
# pórticos N-S com passarela (folga de 2 sob os dutos)
for xg in (12, 51):
    fill(xg, 36, 1, xg, 36, 62, STEEL)
    fill(xg, 37, 1, xg, 37, 62, CATW)
    for z in range(6, 62, 16):
        fill(xg, 31, z, xg, 35, z, STEEL)                            # apoios nos telhados
# canos finos N-S no topo
for xp in (20, 43):
    fill(xp, 44, 1, xp, 44, 62, PIPE_RAW)
    for zc in (16, 32, 48):
        put(xp, 43, zc, PIPE_RAW); put(xp, 44, zc, JUNC_RAW)
# ligação torre → pórtico leste (salto de 1 bloco para o deque da torre)
fill(52, 37, 55, 55, 37, 55, CATW)

# ================================================================ SETOR DANIFICADO (hall SW)
fill(24, 2, 56, 24, 5, 58, AIR)                                      # brecha na fachada
put(24, 6, 56, ASH_CR); put(24, 6, 58, ASH_CR); put(24, 2, 55, ASH_CR)
for k in range(16):
    x, z = rng.randrange(15, 23), rng.randrange(50, 62)
    put(x, 2, z, RUSTY if k % 2 else ASH_CR)
    if k % 4 == 0: put(x, 3, z, ASH_CR)
fill(18, 6, 52, 23, 6, 52, PIPE_RAW)                                 # cano rompido no ar
put(20, 6, 52, AIR)                                                  # (segmento faltando)
put(2, 5, 57, LRED); put(10, 5, 60, LRED)
for z in range(50, 62):
    if base(2, 4, z) == "machine_casing" and rng.random() < .3:
        put(2, 4, z, RUSTY)

# ================================================================ MARCADORES (FASE 4)
# Convertidos em ar + capturados pelo HiveMarkerProcessor na colocação (/fchive module place).
def MK(n): return S(n)
for pos in ((9,2,10),(9,2,26),(9,2,44)):              put(*pos, MK("marker_worker_spawn"))
for pos in ((51,15,10),(51,15,34),(51,15,55)):        put(*pos, MK("marker_civil_spawn"))
for pos in ((31,2,2),(31,2,61)):                      put(*pos, MK("marker_guardsman_spawn"))
for pos in ((44,23,8),(47,23,18),(18,2,58)):          put(*pos, MK("marker_enemy_spawn"))
for pos in ((28,2,10),(35,2,30),(28,2,50),(31,15,25)):put(*pos, MK("marker_patrol_point"))
for pos in ((47,2,34),(59,2,53)):                     put(*pos, MK("marker_loot_point"))
for pos in ((10,2,20),(47,2,12)):                     put(*pos, MK("marker_cover_point"))
for pos in ((31,31,8),(61,10,40)):                    put(*pos, MK("marker_defense_point"))
put(59,31,55, MK("marker_commander_point"))
put(32,2,52, MK("marker_vehicle_point"))
put(51,15,20, MK("marker_trade_point"))
put(20,2,55, MK("marker_construction_point"))

# ================================================================ RESOLUÇÃO DE ESTADOS
CONNECTABLE = {"large_hive_pipe", "pipe_junction", "pressure_valve", "machine_casing",
               "industrial_vent"}
FULL_SOLID = {"reinforced_ashcrete", "cracked_reinforced_ashcrete", "riveted_steel_block",
              "rusted_riveted_steel", "armored_hive_plating", "machine_casing",
              "cathedral_wall", "gothic_arch", "skull_wall_relief", "aquila_wall_relief",
              "hazard_stripe_panel", "yellow_industrial_lumen", "green_industrial_lumen",
              "red_emergency_lumen", "hive_lumen_strip", "imperial_column", "cargo_container",
              "industrial_grating"}
DIRS = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0),
        "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}

def basekey(nb):
    core = nb.split("|")[0]
    return core.split(":")[1] if ":" in core else core

def resolve():
    for (x, y, z), b in list(grid.items()):
        if b == PIPE_RAW or b == JUNC_RAW:
            name = "large_hive_pipe" if b == PIPE_RAW else "pipe_junction"
            props = {}
            for d, (dx, dy, dz) in DIRS.items():
                nb = grid.get((x+dx, y+dy, z+dz), AIR)
                if nb in (PIPE_RAW, JUNC_RAW) or basekey(nb) in CONNECTABLE:
                    props[d] = "true"
            grid[(x, y, z)] = S(name, **props) if props else S(name)
        elif b == RAIL_RAW:
            props = {}
            for d in ("north", "south", "east", "west"):
                dx, dy, dz = DIRS[d]
                nb = grid.get((x+dx, y, z+dz), AIR)
                if nb == RAIL_RAW or basekey(nb) in FULL_SOLID:
                    props[d] = "true"
            grid[(x, y, z)] = S("industrial_railing", **props) if props else S("industrial_railing")
        elif b == WALL_RAW:
            props = {"up": "true"}
            low = []
            for d in ("north", "south", "east", "west"):
                dx, dy, dz = DIRS[d]
                nb = grid.get((x+dx, y, z+dz), AIR)
                if nb == WALL_RAW or basekey(nb) in FULL_SOLID:
                    props[d] = "low"; low.append(d)
            if len(low) == 2 and tuple(sorted(low)) in (("east","west"),("north","south")):
                props["up"] = "false" if grid.get((x, y+1, z), AIR) == AIR else "true"
            grid[(x, y, z)] = S("reinforced_ashcrete_wall", **props)

resolve()

# ================================================================ NBT WRITER (big-endian)
def tag_str(s):
    b = s.encode("utf-8"); return struct.pack(">H", len(b)) + b

def nbt_payload(v):
    if isinstance(v, int):    return struct.pack(">i", v)
    raise TypeError

def write_compound(out, d):
    for k, (t, v) in d.items():
        out.write(bytes([t])); out.write(tag_str(k))
        write_payload(out, t, v)
    out.write(b"\x00")

def write_payload(out, t, v):
    if t == 3:  out.write(struct.pack(">i", v))
    elif t == 8: out.write(tag_str(v))
    elif t == 9:
        et, items = v
        out.write(bytes([et])); out.write(struct.pack(">i", len(items)))
        for it in items: write_payload(out, et, it)
    elif t == 10: write_compound(out, v)
    else: raise TypeError(t)

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
        pindex[key] = len(palette); palette.append(key)
    return pindex[key]

blocks = []
for x in range(SX):
    for y in range(SY):
        for z in range(SZ):
            key = grid.get((x, y, z), AIR)
            blocks.append({"pos": (9, (3, [x, y, z])), "state": (3, pid(key))})

root = {
    "size": (9, (3, [SX, SY, SZ])),
    "entities": (9, (0, [])),
    "blocks": (9, (10, blocks)),
    "palette": (9, (10, [state_to_nbt(k) for k in palette])),
    "DataVersion": (3, 3465),
}

buf = io.BytesIO()
buf.write(b"\x0a"); buf.write(tag_str(""))                            # root compound sem nome
write_compound(buf, root)

out_path = "src/main/resources/data/firstcrusade/structures/hive/street/industrial_street_01.nbt"
os.makedirs(os.path.dirname(out_path), exist_ok=True)
with gzip.open(out_path, "wb") as f:
    f.write(buf.getvalue())

n_nonair = sum(1 for v in grid.values() if v != AIR)
print(f"NBT: {out_path}")
print(f"  size 64x48x64 | blocos não-ar: {n_nonair} | palette: {len(palette)} estados")
print(f"  bytes (gzip): {os.path.getsize(out_path)}")

# ================================================================ PRÉVIAS PNG
try:
    from PIL import Image
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
        "chain": (200, 200, 210), "ladder": (160, 120, 70),
    }
    def col(key):
        b = key.split("|")[0].split(":")[1]
        return COLORS.get(b, (255, 0, 255))
    SC = 6
    def plan(y, fname):
        im = Image.new("RGB", (SX*SC, SZ*SC))
        for x in range(SX):
            for z in range(SZ):
                for yy in range(y, -1, -1):                           # primeiro bloco visto de cima
                    k = grid.get((x, yy, z), AIR)
                    if k != AIR:
                        c = col(k); break
                else: c = COLORS["air"]
                for a in range(SC):
                    for b2 in range(SC):
                        im.putpixel((x*SC+a, z*SC+b2), c)
        im.save(fname)
    def section_z(z, fname):                                          # corte olhando para o norte
        im = Image.new("RGB", (SX*SC, SY*SC))
        for x in range(SX):
            for y in range(SY):
                k = grid.get((x, y, z), AIR)
                c = col(k) if k != AIR else COLORS["air"]
                for a in range(SC):
                    for b2 in range(SC):
                        im.putpixel((x*SC+a, (SY-1-y)*SC+b2), c)
        im.save(fname)
    def section_x(x, fname):                                          # corte longitudinal
        im = Image.new("RGB", (SZ*SC, SY*SC))
        for z in range(SZ):
            for y in range(SY):
                k = grid.get((x, y, z), AIR)
                c = col(k) if k != AIR else COLORS["air"]
                for a in range(SC):
                    for b2 in range(SC):
                        im.putpixel((z*SC+a, (SY-1-y)*SC+b2), c)
        im.save(fname)
    plan(6,  "/tmp/plan_terreo.png")
    plan(18, "/tmp/plan_n2.png")
    plan(47, "/tmp/plan_topo.png")
    section_z(30, "/tmp/corte_z30.png")
    section_z(45, "/tmp/corte_z45_ponteB.png")
    section_x(31, "/tmp/corte_rua_x31.png")
    print("prévias: /tmp/plan_*.png /tmp/corte_*.png")
except ImportError:
    print("PIL ausente — sem prévias")
