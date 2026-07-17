#!/usr/bin/env python3
"""FASE 9 — UNDERHIVE (192×128), a subcidade escura sob a colmeia. Fica ABAIXO de tudo
(conecta ao poço da underhive no military_depot da Fase 5, socket up=underhive_ceiling).
Fileira de 3 módulos 64×64×64: túneis/esgoto tóxico, ruínas colapsadas, e território de gangue.

Layout (data/firstcrusade/hive_districts/underhive.json):
  z 0..63: sump_tunnels_01 | collapsed_ruins_01 | gang_territory_01

Ambiente: escuro (pouca luz natural), iluminado por fungos, fogueiras e lúmens quebrados.
Água tóxica de verdade (toxic_sludge) nos coletores. Sujo, perigoso, improvisado.

Uso: python3 tools/gen_hive_underhive.py
"""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from hive_module_lib import *  # noqa

OUT = "src/main/resources/data/firstcrusade/structures/hive/underhive"
PREV = "/tmp/fase9"
os.makedirs(PREV, exist_ok=True)
results = []

def emit(b, rel, plans=(), sx=(), sz=()):
    nonair, pal, size = b.write_nbt(f"{OUT}/{rel}.nbt")
    b.previews(f"{PREV}/{rel}", plans, sx, sz)
    results.append((rel, b.sx, b.sy, b.sz, nonair, pal, size))

def cavern_shell(b, ceil_y=40):
    """Casco de caverna: piso irregular de escombros, teto de concreto rachado, paredes rudes."""
    b.fill(0, 0, 0, 63, 0, 63, UHCON)
    # piso irregular
    for x in range(64):
        for z in range(64):
            h = 1 + (abs((x * 7 + z * 13) % 5) // 3)
            for y in range(1, h + 1):
                b.put(x, y, z, RUBBLE if (x + z) % 3 else UHCON)
    # teto
    b.fill(0, ceil_y, 0, 63, ceil_y, 63, UHCON)
    for x in range(0, 64, 2):
        for z in range(0, 64, 2):
            if (x + z) % 7 == 0:
                b.put(x, ceil_y - 1, z, S("cracked_reinforced_ashcrete"))
    # paredes perimetrais rudes
    for wall in ((0, 1, 0, 63, ceil_y, 0), (0, 1, 63, 63, ceil_y, 63),
                 (0, 1, 0, 0, ceil_y, 63), (63, 1, 0, 63, ceil_y, 63)):
        b.fill(*wall, UHCON)
    # vigas de suporte enferrujadas (aleatórias)
    for _ in range(10):
        x = b.rng.randrange(6, 58); z = b.rng.randrange(6, 58)
        b.fill(x, 2, z, x, ceil_y - 1, z, S("rusted_riveted_steel"))
        b.put(x, ceil_y - 2, z, JUNC_RAW)
    # fungos luminosos espalhados (iluminação principal)
    for _ in range(40):
        x = b.rng.randrange(1, 63); z = b.rng.randrange(1, 63)
        wall_y = b.rng.randrange(3, ceil_y - 3)
        # cola em parede ou viga
        b.put(x, wall_y, z, GFUNGUS)
    # lúmens quebrados no teto (piscam vermelho — decor)
    for _ in range(6):
        x = b.rng.randrange(4, 60); z = b.rng.randrange(4, 60)
        b.put(x, ceil_y - 1, z, LRED)

def tunnel_x(b, z0, y0, w=6, h=5):
    """Escava um túnel no eixo X, centrado em z0, base y0."""
    b.fill(0, y0, z0 - w // 2, 63, y0 + h, z0 + w // 2, AIR)
    for x in range(0, 64, 5):
        # arcos de suporte
        for dz in (-w // 2, w // 2):
            b.fill(x, y0, z0 + dz, x, y0 + h, z0 + dz, S("rusted_riveted_steel"))
        b.fill(x, y0 + h, z0 - w // 2, x, y0 + h, z0 + w // 2, S("rusted_riveted_steel"))

# ======================================================================================
# MÓDULO: sump_tunnels_01 (coletores/esgoto — canais de água tóxica, passarelas)
# ======================================================================================
b = ModuleBuilder(64, 48, 64, seed=9101)
cavern_shell(b, ceil_y=40)
# grande canal de água tóxica no eixo X (z28..35), rebaixado
b.fill(0, 1, 28, 63, 4, 35, AIR)
b.fill(0, 1, 28, 63, 1, 35, UHCON)
for x in range(64):
    for z in range(29, 35):
        b.put(x, 2, z, SLUDGE)                                      # água tóxica de verdade
# margens com grade e canos despejando
for x in range(0, 64, 8):
    b.fill(x, 2, 27, x, 5, 27, PIPE_RAW); b.put(x, 5, 28, JUNC_RAW)
    b.put(x, 2, 29, SLUDGE)                                         # jorro
# passarela de grade cruzando o canal
for x in range(64):
    b.put(x, 5, 31, GRATE); b.put(x, 5, 32, GRATE)
for x in range(0, 64, 6):
    b.fill(x, 3, 31, x, 4, 31, S("rusted_riveted_steel"))          # suportes da passarela
    b.put(x, 6, 31, RAIL_RAW); b.put(x, 6, 32, RAIL_RAW)
# túneis laterais de manutenção
tunnel_x(b, 12, 2)
tunnel_x(b, 52, 2)
# tambores tóxicos empilhados e poças
for _ in range(12):
    x = b.rng.randrange(4, 60); z = b.rng.choice([8, 10, 20, 44, 54])
    b.put(x, 2, z, TBARREL(b.rng.choice(["north", "south", "east", "west"])))
    if b.rng.random() < 0.5: b.put(x + 1, 2, z, SLUDGE_S)
# escada-de-mão subindo para o poço da hive (canto — conecta ao military_depot)
b.fill(48, 1, 48, 53, 40, 53, AIR)
# ladder facing=east monta na parede a OESTE (x-1); pomos coluna sólida em x48, ladder em x49
for y in range(2, 40):
    b.put(48, y, 49, S("rusted_riveted_steel"))                     # coluna-suporte
    b.put(49, y, 49, LADDER("east"))                                # apoiada em x48
b.fill(48, 1, 48, 53, 1, 53, GRATE)
b.put(50, 2, 50, GFUNGUS); b.put(51, 2, 51, GFUNGUS)
# marcadores
b.put(12, 6, 31, MK("marker_patrol_point")); b.put(52, 6, 31, MK("marker_patrol_point"))
for z in (12, 52): b.put(20, 3, z, MK("marker_enemy_spawn")); b.put(44, 3, z, MK("marker_enemy_spawn"))
b.put(50, 2, 50, MK("marker_loot_point"))
b.put(31, 6, 31, MK("marker_cover_point"))
b.put(8, 2, 8, MK("marker_construction_point"))
b.resolve()
emit(b, "sump_tunnels_01", plans=[(40, "plan"), (5, "plan_baixo")], sz=[(31, "corte_canal_z31")])

# ======================================================================================
# MÓDULO: collapsed_ruins_01 (ruínas — estruturas da hive que desabaram)
# ======================================================================================
b = ModuleBuilder(64, 48, 64, seed=9102)
cavern_shell(b, ceil_y=42)
# grande pilha de escombros de um teto que caiu (montanha diagonal)
for x in range(8, 44):
    hmax = max(0, 18 - abs(x - 26))
    for z in range(20, 44):
        h = max(0, hmax - abs(z - 32) // 2 + b.rng.randint(-2, 2))
        for y in range(2, 2 + h):
            b.put(x, y, z, RUBBLE if (x + y + z) % 4 else UHCON)
# fragmentos de estrutura antiga espetados no entulho (paredes de catedral inclinadas)
for (fx, fz, fh) in ((16, 24, 10), (34, 38, 12), (24, 30, 14)):
    for y in range(2, 2 + fh):
        b.put(fx, y, fz, CATHW)
        if y % 3 == 0: b.put(fx, y, fz + 1, GARCH)
    b.put(fx, 2 + fh, fz, SKULL)
# uma estátua tombada (partida) entre os escombros
b.put(40, 6, 30, S("saint_statue", facing="east", part=0))
b.put(41, 6, 30, S("saint_statue", facing="east", part=1))          # deitada (partes lado a lado)
# meias-paredes de habitação exposta (mostrando "corte" de apês)
for fy in (2, 8, 14):
    b.fill(48, fy, 6, 60, fy, 18, STEEL)                            # lajes de andares expostos
    for x in range(48, 61, 4): b.put(x, fy + 1, 6, RAIL_RAW)
    b.put(50, fy + 1, 10, S("hive_chair", facing="south"))          # móveis abandonados
    b.put(54, fy + 1, 14, S("shelf_unit", facing="west"))
    b.put(57, fy + 1, 8, GFUNGUS)
# poças de água tóxica infiltrada nas depressões
for (px, pz) in ((10, 50), (54, 50), (30, 54)):
    b.fill(px, 2, pz, px + 3, 2, pz + 3, SLUDGE)
    b.put(px, 2, pz, TBARREL("north"))
# caminho serpenteante transitável pelos escombros (limpo)
path = [(4, 10), (14, 16), (24, 14), (34, 46), (46, 50), (58, 54)]
for i in range(len(path) - 1):
    x0, z0 = path[i]; x1, z1 = path[i + 1]
    steps = max(abs(x1 - x0), abs(z1 - z0))
    for s in range(steps + 1):
        x = x0 + (x1 - x0) * s // steps; z = z0 + (z1 - z0) * s // steps
        b.fill(x, 2, z, x + 1, 6, z + 1, AIR)                       # abre passagem
        b.put(x, 1, z, UHCON)
# marcadores
b.put(26, 20, 32, MK("marker_cover_point")); b.put(34, 14, 32, MK("marker_cover_point"))
for pos in ((14, 2, 16), (46, 2, 50)): b.put(*pos, MK("marker_enemy_spawn"))
b.put(50, 3, 10, MK("marker_loot_point")); b.put(40, 7, 30, MK("marker_loot_point"))
b.put(4, 2, 10, MK("marker_patrol_point")); b.put(58, 2, 54, MK("marker_patrol_point"))
b.put(30, 2, 54, MK("marker_construction_point"))
b.resolve()
emit(b, "collapsed_ruins_01", plans=[(42, "plan"), (8, "plan_baixo")], sx=[(26, "corte_x26")])

# ======================================================================================
# MÓDULO: gang_territory_01 (território de gangue — barricadas, acampamento, arena)
# ======================================================================================
b = ModuleBuilder(64, 48, 64, seed=9103)
cavern_shell(b, ceil_y=38)
# barricadas de sucata dividindo o espaço (parede corrugada com passagens)
for bz in (16, 44):
    for x in range(4, 60):
        if 28 <= x <= 35: continue                                 # passagem central
        b.fill(x, 2, bz, x, 5, bz, CORRUG("y"))
    b.fill(28, 2, bz, 35, 4, bz, AIR)
    for x in range(28, 36, 2): b.put(x, 5, bz, HAZ)
    b.put(27, 5, bz, GANGMARK("south")); b.put(36, 5, bz, GANGMARK("north"))
# ACAMPAMENTO central: fogueiras, assentos improvisados, tendas
for (fx, fz) in ((20, 30), (44, 30), (31, 24)):
    b.put(fx, 2, fz, GANGFIRE)
    for (dx, dz) in ((-2, 0), (2, 0), (0, -2), (0, 2)):
        b.put(fx + dx, 2, fz + dz, S("hive_bench", facing="north"))
    b.fill(fx - 3, 6, fz - 3, fx + 3, 6, fz + 3, CORRUG("x"))       # toldo de sucata
    for (cx, cz) in ((fx - 3, fz - 3), (fx + 3, fz + 3)):
        b.fill(cx, 2, cz, cx, 6, cz, S("rusted_riveted_steel"))
# pilhas de sucata e loot
for _ in range(14):
    x = b.rng.randrange(4, 60); z = b.rng.randrange(4, 60)
    if b.get(x, 2, z) == AIR:
        b.put(x, 2, z, SCRAP(b.rng.choice(["north", "south", "east", "west"])))
# arena de luta (círculo rebaixado com arquibancada de sucata)
cx, cz = 31, 52
for x in range(cx - 6, cx + 7):
    for z in range(cz - 5, cz + 6):
        if (x - cx) ** 2 + (z - cz) ** 2 <= 30:
            b.fill(x, 1, z, x, 4, z, AIR); b.put(x, 1, z, RUBBLE)
for x in range(cx - 7, cx + 8):
    for z in range(cz - 6, cz + 7):
        if 30 < (x - cx) ** 2 + (z - cz) ** 2 <= 48:
            b.put(x, 2, z, CORRUG("y")); b.put(x, 3, z, S("hive_bench", facing="north"))
b.put(cx, 2, cz, GANGMARK("south"))
b.put(cx - 4, 5, cz, GANGFIRE); b.put(cx + 4, 5, cz, GANGFIRE)
# chefe da gangue: trono de sucata numa plataforma
b.fill(4, 2, 30, 8, 5, 34, CORRUG("y"))
b.fill(5, 2, 31, 7, 4, 33, AIR)
b.put(6, 3, 31, S("hive_chair", facing="east"))                    # trono
b.put(5, 3, 31, GANGFIRE); b.put(7, 3, 33, SCRAP("west"))
b.put(4, 5, 32, GANGMARK("east"))
# graffiti pela paredes
for _ in range(8):
    z = b.rng.randrange(6, 58)
    b.put(0, b.rng.randrange(3, 30), z, GANGMARK("west") if False else GFUNGUS)
    b.put(1, 4, z, GANGMARK("east"))
# marcadores
for (fx, fz) in ((20, 30), (44, 30), (31, 24)): b.put(fx, 3, fz, MK("marker_enemy_spawn"))
b.put(6, 4, 32, MK("marker_commander_point"))                       # chefe
b.put(31, 2, 52, MK("marker_enemy_spawn")); b.put(31, 3, 52, MK("marker_defense_point"))
b.put(31, 2, 30, MK("marker_patrol_point"))
for pos in ((12, 2, 8), (52, 2, 56)): b.put(*pos, MK("marker_loot_point"))
b.put(31, 2, 10, MK("marker_cover_point"))
b.resolve()
emit(b, "gang_territory_01", plans=[(38, "plan"), (5, "plan_baixo")], sz=[(30, "corte_z30")])

# ======================================================================================
print(f"{'módulo':22s} {'dim':>12s} {'não-ar':>8s} {'palette':>7s} {'bytes':>8s}")
for rel, sx, sy, sz, nonair, pal, size in results:
    print(f"{rel:22s} {sx}x{sy}x{sz:>4} {nonair:8d} {pal:7d} {size:8d}")
print(f"prévias em {PREV}/")
