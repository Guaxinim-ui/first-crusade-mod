#!/usr/bin/env python3
"""FASE 5 — Gera os 6 módulos do distrito SOUTH ASH GATE + CARGO RING (192×128 total).

Layout do distrito (offsets no JSON data/firstcrusade/hive_districts/south_ash_gate.json):
    z 0..63  (lado cidade):  warehouse_01 | cargo_yard_01 | military_depot_01
    z 64..127 (lado deserto): hive_wall_w_01 | south_ash_gate_01 | hive_wall_e_01
Sul (+z) = Ash Wastes. A rua (socket 'street', x25..38 local) atravessa portão e pátio e
encaixa no industrial_street_01 colocado ao norte.

Costuras E-W compartilhadas (coordenadas locais idênticas em todos os módulos da fileira):
  muralha: massa z16..47 (y1..47, topo y48); corredor y2..5 z29..32; galeria y24..27
  z29..32; passadiço y49 com parapeitos z16 e z46..47.
  carga:   arco de veículos z16..27 y2..7; trilhos z20 e z23 (y2); rack de canos y7 z58.

Uso: python3 tools/gen_hive_gate_district.py
"""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from hive_module_lib import *  # noqa

OUT = "src/main/resources/data/firstcrusade/structures/hive"
PREV = "/tmp/fase5"
os.makedirs(PREV, exist_ok=True)
results = []

def emit(b, rel, plans=(), sx=(), sz=()):
    nonair, pal, size = b.write_nbt(f"{OUT}/{rel}.nbt")
    b.previews(f"{PREV}/{rel.replace('/', '_')}", plans, sx, sz)
    results.append((rel, b.sx, b.sy, b.sz, nonair, pal, size))

# ======================================================================================
# PADRÕES DA FILEIRA DE MURALHA (compartilhados por gate e paredes)
# ======================================================================================
def wall_base(b, breach=False):
    """Fundação, apron interno, massa da muralha z16..47 (y1..47), corredor, galeria,
    topo com passadiço/parapeitos, contrafortes, fosso e faixa externa."""
    b.fill(0, 0, 0, 63, 0, 63, ASH)                                   # fundação
    b.fill(0, 1, 0, 63, 1, 15, ASH)                                   # apron interno
    b.fill(0, 1, 16, 63, 47, 47, ASH)                                 # massa da muralha
    b.fill(0, 48, 16, 63, 48, 47, STEEL)                              # laje do topo
    # faixa externa: chão de cinzas, fosso raso, cavaletes anticarro
    for z in range(48, 64):
        for x in range(64):
            b.put(x, 1, z, ASH_CR if (x + z) % 7 == 0 else ASH)
    b.fill(0, 1, 52, 63, 1, 55, AIR)                                  # fosso (expõe y0)
    for x in range(0, 64, 2):
        b.put(x, 0, 53, LGRN if x % 10 == 0 else ASH_CR)              # lodo luminoso
    for x in range(2, 62, 8):
        if 24 <= x <= 39:
            continue                                                  # nunca na rua
        b.put(x, 1, 58, ARMOR); b.put(x, 2, 58, ARMOR)
        b.put(x + 1, 1, 58, ARMOR)                                    # cavaletes
    # corredor interno E-W (contínuo entre módulos)
    b.fill(0, 2, 29, 63, 5, 32, AIR)
    for x in range(0, 64):
        b.put(x, 5, 30, LSTRIP("x")); b.put(x, 5, 31, LSTRIP("x"))
    # galeria superior E-W + seteiras gradeadas para fora
    b.fill(0, 24, 29, 63, 27, 32, AIR)
    for x in range(0, 64):
        b.put(x, 27, 30, LSTRIP("x"))
    for x in range(3, 62, 6):
        b.put(x, 25, 47, GRATE); b.put(x, 26, 47, GRATE)
        b.fill(x, 25, 33, x, 26, 46, AIR)                             # visada da seteira
        b.put(x, 24, 33, SSLAB())                                     # degrau do atirador
    # parapeitos do passadiço
    for x in range(64):
        b.put(x, 49, 16, WALL_RAW)                                    # guarda interna
        b.put(x, 49, 46, ASH)                                         # mureta externa baixa
        if x % 3 != 2:
            b.fill(x, 49, 47, x, 51, 47, ASH)                         # ameias 2-1
    # contrafortes externos com caveira
    for bx in (6, 18, 42, 54):
        b.fill(bx, 1, 48, bx + 2, 34, 51, ASH)
        b.put(bx + 1, 30, 51, SKULL)
        b.put(bx, 35, 48, AST("north")); b.put(bx + 1, 35, 48, AST("north")); b.put(bx + 2, 35, 48, AST("north"))
        b.fill(bx, 10, 51, bx + 2, 10, 51, HAZ)
    # dano opcional (parede leste): cratera externa + entulho
    if breach:
        for (cx, cy, cz, r) in ((31, 14, 47, 5), (28, 10, 47, 3), (35, 18, 47, 3)):
            for dx in range(-r, r + 1):
                for dy in range(-r, r + 1):
                    if dx * dx + dy * dy <= r * r:
                        depth = 3 if dx * dx + dy * dy <= (r - 1) ** 2 else 1
                        for dz in range(depth):
                            b.put(cx + dx, cy + dy, 47 - dz, AIR)
                        b.put(cx + dx, cy + dy, 47 - depth, ASH_CR if (dx + dy) % 2 else ARMOR)
        for k in range(18):
            x = b.rng.randrange(24, 40); z = b.rng.randrange(48, 56)
            b.put(x, 2, z, RUSTY if k % 2 else ASH_CR)
            if k % 3 == 0: b.put(x, 3, z, ASH_CR)
        b.put(30, 5, 29, LRED); b.put(34, 5, 29, LRED)                # corredor em alerta

def wall_tower(b, x0):
    """Torre defensiva 16 de largura sobre a muralha (x0..x0+15, z20..43, y49..62):
    passadiço atravessa a câmara, poço 1x1 na massa (z34) liga corredor→galeria→topo,
    ladder interna câmara→teto."""
    x1 = x0 + 15
    b.fill(x0, 49, 20, x1, 61, 43, ASH)                               # corpo
    b.fill(x0 + 2, 49, 22, x1 - 2, 60, 41, AIR)                       # câmara
    b.fill(x0, 62, 20, x1, 62, 43, STEEL)                             # teto
    for x in range(x0, x1 + 1):
        if x % 3 != 2:
            b.put(x, 63, 20, ASH); b.put(x, 63, 43, ASH)
    for z in range(20, 44):
        if z % 3 != 2:
            b.put(x0, 63, z, ASH); b.put(x1, 63, z, ASH)
    # passagem do passadiço atravessa as DUAS espessuras de parede
    b.fill(x0, 49, 29, x0 + 1, 52, 32, AIR)
    b.fill(x1 - 1, 49, 29, x1, 52, 32, AIR)
    for z in (24, 33, 39):
        b.put(x0, 53, z, GRATE); b.put(x0, 54, z, GRATE)
        b.put(x1, 53, z, GRATE); b.put(x1, 54, z, GRATE)
    b.put(x0 + 4, 60, 30, LYEL); b.put(x1 - 4, 60, 33, LYEL)
    b.put(x0 + 2, 52, 22, LRED)
    # poço 1x1 na massa da muralha (z=34; ladder apoiada na parede z=35)
    sx = x0 + 8
    b.fill(sx, 2, 34, sx, 47, 34, AIR)
    b.put(sx, 48, 34, AIR)                                            # furo na laje do topo
    b.fill(sx, 2, 33, sx, 5, 33, AIR)                                 # espoleta → corredor
    b.fill(sx, 24, 33, sx, 27, 33, AIR)                               # espoleta → galeria
    for y in range(2, 49):
        b.put(sx, y, 34, LADDER("north"))
    b.put(sx, 1, 34, HAZ)
    # ladder interna da câmara ao teto (apoiada no anel z=21)
    b.fill(x0 + 2, 62, 22, x0 + 2, 62, 22, AIR)
    for y in range(49, 62):
        b.put(x0 + 2, y, 22, LADDER("south"))
    # holofotes externos + guarda do teto
    b.put(x0 + 1, 56, 44, STEEL); b.put(x0 + 1, 56, 45, LYEL)
    b.put(x1 - 1, 56, 44, STEEL); b.put(x1 - 1, 56, 45, LYEL)
    for x in range(x0 + 3, x1 - 2, 3):
        b.put(x, 63, 31, RAIL_RAW)

def apron_bunker(b, x0, z0):
    """Depósito de munição encostado na muralha (lado interno)."""
    b.fill(x0, 1, z0, x0 + 7, 5, z0 + 5, CASING)
    b.fill(x0 + 1, 2, z0 + 1, x0 + 6, 4, z0 + 4, AIR)
    b.fill(x0 + 3, 2, z0, x0 + 4, 3, z0, AIR)                          # porta
    b.put(x0 + 2, 5, z0, VENT("north")); b.put(x0 + 5, 5, z0, VENT("north"))
    b.put(x0 + 1, 4, z0 + 1, LRED)
    b.put(x0 + 1, 2, z0 + 4, CONT("north")); b.put(x0 + 2, 2, z0 + 4, CONT("north"))
    b.fill(x0, 1, z0 - 1, x0 + 7, 1, z0 - 1, HAZ)

# ======================================================================================
# MÓDULO: hive_wall_w_01
# ======================================================================================
b = ModuleBuilder(64, 64, 64, seed=5101)
wall_base(b)
wall_tower(b, 8)
apron_bunker(b, 36, 8)
apron_bunker(b, 50, 8)
# marcadores
for pos in ((16, 50, 25), (23, 63, 30)):            b.put(*pos, MK("marker_defense_point"))
for pos in ((30, 2, 8), (10, 2, 30)):               b.put(*pos, MK("marker_guardsman_spawn"))
for pos in ((40, 50, 30), (55, 50, 31)):            b.put(*pos, MK("marker_patrol_point"))
for pos in ((30, 50, 45), (48, 50, 45)):            b.put(*pos, MK("marker_cover_point"))
b.put(39, 2, 9, MK("marker_loot_point"))
b.put(12, 50, 33, MK("marker_guardsman_spawn"))
b.resolve()
emit(b, "gates/hive_wall_w_01", plans=[(63, "plan")], sz=[(30, "corte_z30")])

# ======================================================================================
# MÓDULO: hive_wall_e_01 (com brecha de cerco)
# ======================================================================================
b = ModuleBuilder(64, 64, 64, seed=5102)
wall_base(b, breach=True)
wall_tower(b, 40)
apron_bunker(b, 6, 8)
for pos in ((48, 50, 25), (55, 63, 30)):            b.put(*pos, MK("marker_defense_point"))
for pos in ((20, 2, 8), (52, 2, 30)):               b.put(*pos, MK("marker_guardsman_spawn"))
for pos in ((10, 50, 30), (30, 50, 31)):            b.put(*pos, MK("marker_patrol_point"))
b.put(14, 50, 45, MK("marker_cover_point"))
b.put(31, 2, 57, MK("marker_enemy_spawn"))
b.put(27, 2, 50, MK("marker_enemy_spawn"))
b.put(30, 2, 12, MK("marker_construction_point"))
b.resolve()
emit(b, "gates/hive_wall_e_01", plans=[(63, "plan")], sz=[(47, "corte_brecha_z47")])

# ======================================================================================
# MÓDULO: south_ash_gate_01
# ======================================================================================
b = ModuleBuilder(64, 64, 64, seed=5103)
wall_base(b)
b.pave_street(0, 63)                                                  # rua atravessa tudo
b.fill(24, 2, 16, 39, 17, 47, AIR)                                    # PASSAGEM 16x16
b.fill(25, 1, 16, 38, 1, 47, ASH)                                     # repõe piso da rua
for z in range(16, 48):
    for x in (30, 31, 32, 33): b.put(x, 1, z, STEEL)
    for x in (29, 34): b.put(x, 1, z, HAZ if z % 4 < 2 else ASH)
    for x in (26, 37):
        b.put(x, 1, z, GRATE); b.put(x, 0, z, LGRN if z % 5 == 0 else ASH_CR)
# torres do portão (x8..23 e x40..55, y49..59)
for x0 in (8, 40):
    x1 = x0 + 15
    b.fill(x0, 49, 18, x1, 58, 45, ASH)
    b.fill(x0 + 2, 49, 20, x1 - 2, 57, 43, AIR)
    b.fill(x0, 59, 18, x1, 59, 45, STEEL)
    b.fill(x0, 49, 29, x0 + 1, 52, 32, AIR)
    b.fill(x1 - 1, 49, 29, x1, 52, 32, AIR)
    for z in (22, 34, 41):
        b.put(x0 if x0 == 8 else x1, 53, z, GRATE)
    b.put(x0 + 3, 56, 44, STEEL); b.put(x0 + 3, 56, 45, LYEL)          # holofote
    b.put(x1 - 3, 56, 44, STEEL); b.put(x1 - 3, 56, 45, LYEL)
    sx = x0 + 7                                                        # poço 1x1 na massa
    b.fill(sx, 2, 34, sx, 47, 34, AIR)
    b.put(sx, 48, 34, AIR)
    b.fill(sx, 2, 33, sx, 5, 33, AIR)                                  # → corredor
    b.fill(sx, 24, 33, sx, 27, 33, AIR)                                # → galeria
    for y in range(2, 49):
        b.put(sx, y, 34, LADDER("north"))
    # acesso à galeria de tiro sobre a passagem
    inner = 23 if x0 == 8 else 40
    step = 1 if x0 == 8 else -1
    b.fill(min(sx + step, inner), 18, 34, max(sx + step, inner), 21, 35, AIR)
    b.fill(sx, 18, 34, sx, 21, 35, AIR)
    # ladder da câmara ao teto
    for y in range(49, 59):
        b.put(x0 + 2, y, 21, LADDER("south"))
    b.put(x0 + 2, 59, 21, AIR)
# galeria de tiro sobre a passagem + buracos assassinos gradeados
b.fill(24, 18, 28, 39, 21, 35, AIR)
for x in range(25, 39):
    b.put(x, 17, 30, GRATE); b.put(x, 17, 33, GRATE)
b.fill(26, 21, 29, 37, 21, 29, LRED)
b.put(25, 19, 34, LRED); b.put(38, 19, 34, LRED)
# portcullis blindada içada (plano z40) + correntes e guinchos
b.fill(25, 12, 40, 38, 17, 40, ARMOR)
for x in range(25, 39): b.put(x, 12, 40, HAZ)
b.fill(26, 18, 40, 37, 25, 40, AIR)                                   # fenda do guincho
for x in (27, 31, 36):
    for y in range(18, 25): b.put(x, y, 40, CHAIN)
    b.put(x, 25, 40, CASING)
# folhas internas recolhidas (bolsões)
for (px, leaf_x) in ((20, 23), (40, 40)):
    b.fill(px, 2, 17, px + 3, 15, 20, AIR)
    b.fill(leaf_x, 2, 18, leaf_x, 15, 19, ARMOR)
# fachada externa monumental
for (x, y) in ((23, 18), (24, 19), (25, 20), (26, 21), (27, 22), (28, 22), (29, 23), (30, 23), (31, 24)):
    b.put(x, y, 47, GARCH); b.put(63 - x, y, 47, GARCH)
b.fill(22, 2, 47, 23, 19, 47, CATHW); b.fill(40, 2, 47, 41, 19, 47, CATHW)
b.fill(31, 26, 47, 32, 27, 47, AQUILA)
b.put(26, 22, 47, SKULL); b.put(37, 22, 47, SKULL)
for x in list(range(8, 22)) + list(range(42, 56)):
    b.put(x, 10, 47, HAZ)
# iluminação da passagem
for z in range(16, 48):
    if z == 40:
        continue                                                      # plano da portcullis
    b.put(27, 17, z, LSTRIP("z")); b.put(36, 17, z, LSTRIP("z"))
# posto de inspeção interno (cabines + braços de barreira levantados)
for (bx, door_x) in ((16, 23), (40, 40)):
    b.fill(bx, 1, 6, bx + 7, 6, 13, ASH)
    b.fill(bx + 1, 2, 7, bx + 6, 5, 12, AIR)
    b.fill(door_x, 2, 9, door_x, 3, 10, AIR)
    b.put(bx + 3, 3, 6, GRATE); b.put(bx + 4, 3, 6, GRATE)
    b.put(bx + 1, 5, 8, LYEL)
b.fill(24, 2, 10, 24, 4, 10, STEEL); b.fill(39, 2, 10, 39, 4, 10, STEEL)
for x in (25, 26, 27, 28): b.put(x, 5, 10, S("hazard_stripe_panel"))
for x in (35, 36, 37, 38): b.put(x, 5, 10, S("hazard_stripe_panel"))
# marcadores
for pos in ((28, 2, 12), (35, 2, 12), (28, 2, 44), (35, 2, 44)): b.put(*pos, MK("marker_guardsman_spawn"))
for pos in ((31, 18, 30), (12, 60, 30), (50, 60, 33)):           b.put(*pos, MK("marker_defense_point"))
b.put(48, 50, 31, MK("marker_commander_point"))
for pos in ((28, 50, 30), (35, 50, 31)):                          b.put(*pos, MK("marker_patrol_point"))
b.put(21, 2, 9, MK("marker_trade_point"))
for pos in ((25, 2, 8), (38, 2, 12)):                             b.put(*pos, MK("marker_cover_point"))
b.put(31, 2, 55, MK("marker_vehicle_point"))
b.put(31, 2, 61, MK("marker_enemy_spawn"))
b.resolve()
emit(b, "gates/south_ash_gate_01", plans=[(63, "plan")], sx=[(31, "corte_rua_x31")], sz=[(47, "fachada_z47")])

# ======================================================================================
# PADRÕES DA FILEIRA DE CARGA
# ======================================================================================
def cargo_rail_bed(b, x0, x1):
    """Leito duplo de trilhos E-W (z20 e z23) com dormentes de aço."""
    for z in (19, 20, 21, 22, 23, 24):
        for x in range(x0, x1 + 1):
            b.put(x, 1, z, STEEL if x % 2 == 0 else ASH)
    for z in (20, 23):
        for x in range(x0, x1 + 1):
            b.put(x, 2, z, RAIL_TRACK())

def pipe_rack(b, x0, x1):
    """Rack elevado de canos ao longo de z58 (contínuo pela fileira)."""
    posts = [x for x in range(x0 + 2, x1, 8) if not 25 <= x <= 38]
    for x in (24, 39):
        if x0 <= x <= x1:
            posts.append(x)
    for x in posts:
        b.fill(x, 2, 58, x, 6, 58, STEEL)
    for x in range(x0, x1 + 1):
        b.put(x, 7, 58, PIPE_RAW)
    b.put((x0 + x1) // 2, 7, 58, VALVE("x"))

# ======================================================================================
# MÓDULO: cargo_yard_01
# ======================================================================================
b = ModuleBuilder(64, 48, 64, seed=5104)
b.fill(0, 0, 0, 63, 0, 63, ASH)
b.fill(0, 1, 0, 63, 1, 63, ASH)
b.pave_street(0, 63)
cargo_rail_bed(b, 0, 63)
pipe_rack(b, 0, 63)
# plataformas de carga (norte e sul dos trilhos) com escadas
b.fill(0, 2, 12, 22, 2, 15, ASH); b.fill(41, 2, 12, 63, 2, 15, ASH)
b.fill(0, 2, 28, 22, 2, 31, ASH); b.fill(41, 2, 28, 63, 2, 31, ASH)
for x in range(2, 22, 8):
    b.put(x, 2, 16, AST("south")); b.put(x + 1, 2, 16, AST("south"))
    b.put(x, 2, 27, AST("north")); b.put(x + 1, 2, 27, AST("north"))
for x in range(43, 63, 8):
    b.put(x, 2, 16, AST("south")); b.put(x + 1, 2, 16, AST("south"))
for z in (12, 31):
    for x in list(range(0, 23)) + list(range(41, 64)):
        if x % 2 == 0: b.put(x, 2, z, HAZ)
# guindaste-pórtico oeste sobre os trilhos (contêiner suspenso)
for (lx, lz) in ((6, 16), (6, 26), (18, 16), (18, 26)):
    b.fill(lx, 2, lz, lx + 1, 13, lz + 1, ARMOR)
for lx in (6, 18):
    b.fill(lx, 14, 16, lx + 1, 15, 27, STEEL)
b.fill(6, 15, 17, 19, 15, 17, STEEL); b.fill(6, 15, 26, 19, 15, 26, STEEL)
b.fill(12, 14, 21, 13, 14, 22, CASING)
for y in (13, 12, 11, 10): b.put(12, y, 21, CHAIN)
b.fill(12, 8, 21, 13, 9, 22, CONT("north"))
# pilhas de contêineres a leste
for (x0, z0, h, f) in ((44, 34, 3, "west"), (44, 38, 2, "south"), (50, 44, 3, "north"),
                       (56, 36, 1, "east"), (50, 52, 2, "west"), (58, 50, 3, "south")):
    for dy in range(h):
        b.fill(x0, 2 + dy, z0, x0 + 1, 2 + dy, z0 + 1, CONT(f))
for z in range(34, 58, 4):
    b.put(42, 1, z, HAZ); b.put(62, 1, z, HAZ)
# pórtico-scanner alfandegário sobre a rua
b.fill(24, 2, 40, 24, 9, 40, STEEL); b.fill(39, 2, 40, 39, 9, 40, STEEL)
b.fill(24, 10, 40, 39, 10, 40, STEEL)
for x in range(26, 38, 2): b.put(x, 9, 40, LGRN)
# guarita de controle do cruzamento
b.fill(2, 1, 34, 8, 6, 40, ASH)
b.fill(3, 2, 35, 7, 5, 39, AIR)
b.fill(5, 2, 34, 6, 3, 34, AIR)
b.put(4, 3, 34, GRATE); b.put(7, 3, 34, GRATE)
b.put(3, 5, 36, LYEL); b.put(8, 4, 36, VENT("east"))
# sinais do cruzamento ferroviário
for (x, z) in ((24, 17), (39, 17), (24, 26), (39, 26)):
    b.fill(x, 2, z, x, 4, z, STEEL); b.put(x, 5, z, LRED)
# mastros de holofote
for (x, z) in ((3, 4), (60, 4), (3, 59), (60, 59)):
    b.lamp_mast(x, z)
# marcadores
for pos in ((10, 3, 13), (46, 3, 29), (14, 2, 21), (52, 2, 40)):  b.put(*pos, MK("marker_worker_spawn"))
for pos in ((31, 2, 8), (16, 2, 21)):                             b.put(*pos, MK("marker_vehicle_point"))
for pos in ((28, 2, 36), (35, 2, 50)):                            b.put(*pos, MK("marker_patrol_point"))
b.put(52, 2, 47, MK("marker_loot_point"))
for pos in ((46, 2, 36), (8, 3, 17)):                             b.put(*pos, MK("marker_cover_point"))
b.put(28, 2, 41, MK("marker_trade_point"))
b.put(4, 2, 37, MK("marker_civil_spawn"))
b.resolve()
emit(b, "cargo/cargo_yard_01", plans=[(47, "plan")], sz=[(21, "corte_trilhos_z21")])

# ======================================================================================
# CASCO DE ARMAZÉM (compartilhado)
# ======================================================================================
def warehouse_shell(b, bay_east=True, digits=(0, 1)):
    b.fill(0, 0, 0, 63, 0, 63, ASH)
    b.fill(0, 1, 1, 63, 1, 62, ASH)
    for wall in ((0, 1, 0, 63, 29, 0), (0, 1, 63, 63, 29, 63),
                 (0, 1, 0, 0, 29, 63), (63, 1, 0, 63, 29, 63)):
        b.fill(*wall, ASH)
    b.fill(1, 30, 1, 62, 30, 62, STEEL)                               # teto
    bay_x = 63 if bay_east else 0
    ring_x = 0 if bay_east else 63
    # arco de veículos (socket cargo_bay) + trilhos entrando
    b.fill(bay_x, 2, 16, bay_x, 7, 27, AIR)
    b.fill(bay_x, 8, 15, bay_x, 8, 28, HAZ)
    b.fill(bay_x, 2, 15, bay_x, 8, 15, ARMOR); b.fill(bay_x, 2, 28, bay_x, 8, 28, ARMOR)
    b.fill(bay_x, 2, 44, bay_x, 4, 46, AIR)                           # porta de pessoal
    inner0, inner1 = (8, 62) if bay_east else (1, 55)
    cargo_rail_bed(b, inner0, inner1)
    stop_x = inner0 if bay_east else inner1
    for z in (20, 23):
        b.put(stop_x, 2, z, ARMOR); b.put(stop_x, 3, z, HAZ)          # para-choques
    pipe_rack(b, 1, 62)
    # portão cego do anel (socket cargo_ring futuro)
    b.fill(ring_x, 2, 24, ring_x, 10, 39, ARMOR)
    b.fill(ring_x, 2, 26, ring_x, 8, 37, RUSTY)
    b.fill(ring_x, 11, 23, ring_x, 11, 40, HAZ)
    # números de setor pintados na face norte (lado cidade)
    b.digit(digits[0], face_z=0, x0=20, y0=10)
    b.digit(digits[1], face_z=0, x0=28, y0=10)
    b.fill(16, 8, 0, 36, 8, 0, HAZ); b.fill(16, 18, 0, 36, 18, 0, HAZ)
    # iluminação do teto
    for x in range(10, 58, 12):
        for z in range(4, 60):
            b.put(x, 29, z, LSTRIP("z"))
    # elevador de carga (poço 4x4 com plataforma e escada-de-mão)
    ex = 4 if bay_east else 56
    b.fill(ex, 1, 4, ex + 3, 29, 7, AIR)
    for (cx, cz) in ((ex, 4), (ex + 3, 4), (ex, 7), (ex + 3, 7)):
        b.fill(cx, 2, cz, cx, 29, cz, ARMOR)
    b.fill(ex + 1, 1, 5, ex + 2, 1, 6, SSLAB())
    b.put(ex, 30, 5, GRATE); b.put(ex, 30, 6, GRATE)
    for y in range(2, 30): b.put(ex + 1, y, 4, LADDER("south"))
    for y in range(2, 12, 3): b.put(ex + 3, y, 5, HAZ)

# ======================================================================================
# MÓDULO: warehouse_01 (oeste — logística civil)
# ======================================================================================
b = ModuleBuilder(64, 32, 64, seed=5105)
warehouse_shell(b, bay_east=True, digits=(0, 1))
# fileiras de prateleiras com contêineres
for z0 in (8, 34, 48):
    for x in range(14, 58, 6):
        b.fill(x, 2, z0, x, 8, z0, STEEL); b.fill(x, 2, z0 + 3, x, 8, z0 + 3, STEEL)
        b.fill(x, 5, z0, x + 4, 5, z0 + 3, GRATE)
        for dy, f in ((2, "north"), (6, "south")):
            if b.rng.random() < 0.8:
                b.fill(x + 1, dy, z0 + 1, x + 2, dy, z0 + 2, CONT(f))
# mezanino de escritório SW com corrimão e escada
b.fill(4, 10, 44, 20, 10, 60, STEEL)
for x in range(4, 21): b.put(x, 11, 44, RAIL_RAW)
for z in range(44, 61): b.put(20, 11, z, RAIL_RAW)
b.fill(20, 11, 45, 20, 11, 46, AIR)                                   # vão de entrada
for i, z in enumerate(range(53, 44, -1)):
    b.put(21, 2 + i, z, SST("north")); b.fill(21, 1, z, 21, 1 + i, z, STEEL)
b.fill(5, 11, 52, 12, 15, 52, CATHW)
b.put(8, 14, 53, LYEL); b.put(6, 11, 56, CONT("east"))
# marcadores
for pos in ((26, 2, 6), (26, 2, 32), (40, 2, 46)):  b.put(*pos, MK("marker_worker_spawn"))
for pos in ((16, 2, 10), (50, 2, 50)):              b.put(*pos, MK("marker_loot_point"))
b.put(8, 11, 48, MK("marker_civil_spawn"))
b.put(31, 2, 42, MK("marker_patrol_point"))
b.put(15, 2, 36, MK("marker_cover_point"))
b.resolve()
emit(b, "cargo/warehouse_01", plans=[(31, "plan")], sz=[(21, "corte_z21")])

# ======================================================================================
# MÓDULO: military_depot_01 (leste — depósito militar + entrada da Underhive)
# ======================================================================================
b = ModuleBuilder(64, 32, 64, seed=5106)
warehouse_shell(b, bay_east=False, digits=(0, 2))
# jaulas de suprimento (corrimão) com caixas
for (x0, z0) in ((14, 6), (30, 6), (14, 32)):
    for x in range(x0, x0 + 11): b.put(x, 2, z0, RAIL_RAW); b.put(x, 2, z0 + 9, RAIL_RAW)
    for z in range(z0, z0 + 10): b.put(x0, 2, z, RAIL_RAW); b.put(x0 + 10, 2, z, RAIL_RAW)
    b.put(x0 + 5, 2, z0, AIR)
    for k in range(4):
        x = x0 + 2 + b.rng.randrange(7); z = z0 + 2 + b.rng.randrange(6)
        b.put(x, 2, z, CASING if k % 2 else CONT("north"))
    b.put(x0 + 1, 6, z0 + 1, LRED)
# cofre blindado do arsenal (SE) — contém a ENTRADA DA UNDERHIVE
b.fill(40, 1, 40, 60, 12, 60, ARMOR)
b.fill(42, 2, 42, 58, 10, 58, AIR)
b.fill(40, 2, 48, 40, 5, 51, AIR)                                     # porta do cofre
b.fill(40, 6, 47, 40, 6, 52, HAZ)
b.fill(42, 1, 42, 58, 1, 58, ASH)
# poço da Underhive (convenção down=underhive_shaft: x48..53, z48..53)
b.fill(47, 1, 47, 54, 1, 54, ARMOR)
b.fill(49, 1, 49, 52, 1, 52, GRATE)
b.fill(49, 0, 49, 52, 0, 52, LGRN)
for (cx, cz) in ((48, 48), (53, 48), (48, 53), (53, 53)):
    for y in range(2, 9): b.put(cx, y, cz, CHAIN)
for (x, z) in ((46, 46), (55, 46), (46, 55), (55, 55)):
    b.put(x, 1, z, HAZ)
b.put(44, 4, 43, SKULL); b.put(57, 4, 43, SKULL)
b.put(43, 3, 50, LRED); b.put(58, 3, 50, LRED)
# racks de armas ao longo da parede norte do cofre
for x in range(43, 58, 3):
    b.fill(x, 2, 43, x, 4, 43, STEEL); b.put(x, 3, 44, GRATE)
# passarela de guarda (mezanino norte) com corrimão
b.fill(4, 9, 4, 53, 9, 7, CATW)
for x in range(4, 54): b.put(x, 10, 4, RAIL_RAW)
for i, z in enumerate(range(14, 6, -1)):
    b.put(4, 2 + i, z, SST("north"))
    if i > 0:
        b.fill(4, 1, z, 4, 1 + i, z, STEEL)
# iluminação: metade sul vermelha
for x in (10, 22, 34, 46, 58):
    for z in range(38, 60):
        b.put(x, 29, z, AIR)
for (x, z) in ((20, 44), (32, 44), (20, 56), (32, 56)):
    b.put(x, 8, z, LRED)
# marcadores
for pos in ((10, 2, 12), (36, 2, 12), (50, 2, 45)): b.put(*pos, MK("marker_guardsman_spawn"))
for pos in ((45, 2, 45), (56, 2, 56)):              b.put(*pos, MK("marker_loot_point"))
b.put(30, 10, 5, MK("marker_defense_point"))
b.put(31, 2, 30, MK("marker_patrol_point"))
b.put(18, 2, 36, MK("marker_cover_point"))
b.resolve()
emit(b, "cargo/military_depot_01", plans=[(31, "plan")], sz=[(50, "corte_cofre_z50")])

# ======================================================================================
print(f"{'módulo':34s} {'dim':>12s} {'não-ar':>8s} {'palette':>7s} {'bytes':>8s}")
for rel, sx, sy, sz, nonair, pal, size in results:
    print(f"{rel:34s} {sx}x{sy}x{sz:>4} {nonair:8d} {pal:7d} {size:8d}")
print(f"prévias em {PREV}/")
