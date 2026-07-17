#!/usr/bin/env python3
"""FASE 8 — ADMINISTRATUM + CATEDRAL (192×128), o coroamento da colmeia. Empilha sobre o Hab
Stacks. Fileira de 3 módulos 64×64×64: arquivo/scriptorium, a grande catedral (peça central,
nave monumental), e as câmaras administrativas/tribunal.

Layout (data/firstcrusade/hive_districts/administratum.json):
  z 0..63 (frente): scriptorium_01 | cathedral_nave_01 | tribunal_01
Rua (socket street x25..38) atravessa; encaixa no Hab Stacks abaixo.

Sockets: street (N/S) | admin_hall (E/W) | down=hab_roof (assenta no Hab) | up=spire_base.

Usa estátuas, braseiros, órgãos de cano, arquitetura gótica das fases anteriores.

Uso: python3 tools/gen_hive_administratum.py
"""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from hive_module_lib import *  # noqa

OUT = "src/main/resources/data/firstcrusade/structures/hive/admin"
PREV = "/tmp/fase8"
os.makedirs(PREV, exist_ok=True)
results = []

# helpers locais (estátuas + móveis + luzes)
def SAINT(f, p):  return S("saint_statue", facing=f, part=p)
def GUARD(f, p):  return S("imperial_guardian_statue", facing=f, part=p)
def BANNER(f, p): return S("aquila_banner", facing=f, part=p)
def BUST(f):      return S("saint_bust", facing=f)
def AQ_ST(f):     return S("aquila_statue", facing=f)
def BRAZ():       return S("cathedral_brazier")
def LAMP():       return S("hanging_hive_lamp")
def FLOOD(f):     return S("industrial_floodlight", facing=f)
def TERM(f):      return S("wall_terminal", facing=f)
def COGI(f):      return S("cogitator_console", facing=f)
def CTRL(f):      return S("control_panel", facing=f)
def SHELF(f):     return S("shelf_unit", facing=f)
def TABLE():      return S("hive_table")
def CHAIR(f):     return S("hive_chair", facing=f)
def BENCH(f):     return S("hive_bench", facing=f)
def RUG():        return S("hive_rug")
def PROP(f):      return S("imperial_propaganda_panel", facing=f)
def TRUNKP():     return "TRUNK"

def emit(b, rel, plans=(), sx=(), sz=()):
    nonair, pal, size = b.write_nbt(f"{OUT}/{rel}.nbt")
    b.previews(f"{PREV}/{rel}", plans, sx, sz)
    results.append((rel, b.sx, b.sy, b.sz, nonair, pal, size))

def street_tunnel(b, z0, z1):
    b.fill(24, 2, z0, 39, 6, z1, AIR)
    b.pave_street(z0, z1)
    b.fill(24, 7, z0, 39, 7, z1, STEEL)

def perimeter(b, top_y, north_open=True):
    b.fill(0, 0, 0, 63, 0, 63, ASH)
    b.fill(0, 1, 0, 63, 1, 63, ASH)
    b.fill(0, 1, 0, 63, top_y, 0, ASH)
    b.fill(0, 1, 63, 63, top_y, 63, ASH)
    b.fill(0, 1, 0, 0, top_y, 63, ASH)
    b.fill(63, 1, 0, 63, top_y, 63, ASH)

def gothic_windows(b, wall, top_y, coords):
    """Janelas ogivais com vitrais coloridos numa parede (x fixo). coords=lista de (z,base_y)."""
    for (z, by) in coords:
        b.fill(wall, by, z, wall, by + 5, z, LGRN if z % 3 == 0 else LYEL)
        b.fill(wall, by, z + 1, wall, by + 5, z + 1, LRED if z % 2 else LYEL)
        b.put(wall, by + 6, z, GARCH); b.put(wall, by + 6, z + 1, GARCH)

# ======================================================================================
# MÓDULO: cathedral_nave_01 (PEÇA CENTRAL — nave monumental de teto altíssimo)
# ======================================================================================
b = ModuleBuilder(64, 64, 64, seed=8102)
perimeter(b, 62)
street_tunnel(b, 0, 63)                                              # nave é atravessada pela via processional
# piso nobre: tapete vermelho na via central
for z in range(2, 62):
    b.put(30, 1, z, RUG()); b.put(31, 1, z, RUG()); b.put(32, 1, z, RUG()); b.put(33, 1, z, RUG())
# arcada dupla de colunas altíssimas (y2..40) + arcobotantes
for z in range(6, 60, 6):
    for cx in (10, 22, 41, 53):
        b.fill(cx, 2, z, cx + 1, 44, z, COL("y"))
        b.put(cx, 45, z, GARCH); b.put(cx + 1, 45, z, GARCH)
    # arcos cruzando a nave
    b.fill(11, 44, z, 22, 44, z, STEEL)
    b.fill(42, 44, z, 53, 44, z, STEEL)
    # arcobotante para as paredes externas
    b.fill(2, 30, z, 9, 30, z, AST("east"))
    b.fill(54, 30, z, 61, 30, z, AST("west"))
# teto abobadado (nervuras) muito alto
for z in range(4, 60, 4):
    for x in range(11, 53):
        if abs(x - 32) + (z % 8) < 3 or x in (11, 32, 53):
            b.put(x, 52, z, STEEL)
b.fill(24, 54, 4, 39, 54, 59, ARMOR)                                # espinha do telhado
# vitrais gigantes nas paredes laterais
gothic_windows(b, 0, 62, [(z, 20) for z in range(8, 58, 8)])
gothic_windows(b, 63, 62, [(z, 20) for z in range(8, 58, 8)])
# clerestório (janelas altas)
for z in range(6, 60, 5):
    b.put(0, 40, z, LYEL); b.put(63, 40, z, LYEL)
# ÓRGÃO DE CANOS na parede norte (sobre a entrada)
for i, x in enumerate(range(10, 54, 2)):
    h = 12 + (abs(x - 32) % 8)
    b.fill(x, 34, 2, x, 34 + h, 2, TRUNKP() if i % 3 == 0 else PIPE_RAW)
b.fill(8, 32, 2, 55, 33, 2, STEEL)                                  # console do órgão
# ALTAR-MOR ao fundo sul: plataforma elevada, águia colossal, santos, braseiros
b.fill(20, 1, 54, 43, 4, 60, ASH)                                   # plataforma em degraus
b.fill(22, 1, 55, 41, 1, 59, GRATE)
for step in range(3): 
    b.fill(20 + step, 1 + step, 54, 43 - step, 1 + step, 54, AST("north"))
# águia colossal na parede
b.fill(26, 20, 62, 37, 32, 62, AQUILA)
b.fill(28, 33, 62, 35, 38, 62, SKULL)
# santos monumentais ladeando o altar (3 de altura)
b.put(23, 4, 57, SAINT("east", 0)); b.put(23, 5, 57, SAINT("east", 1)); b.put(23, 6, 57, SAINT("east", 2))
b.put(40, 4, 57, SAINT("west", 0)); b.put(40, 5, 57, SAINT("west", 1)); b.put(40, 6, 57, SAINT("west", 2))
b.put(27, 4, 58, GUARD("north", 0)); b.put(27, 5, 58, GUARD("north", 1)); b.put(27, 6, 58, GUARD("north", 2))
b.put(36, 4, 58, GUARD("north", 0)); b.put(36, 5, 58, GUARD("north", 1)); b.put(36, 6, 58, GUARD("north", 2))
# altar central + braseiros
b.fill(30, 4, 57, 33, 5, 58, CATHW)
b.put(29, 4, 57, BRAZ()); b.put(34, 4, 57, BRAZ())
b.put(31, 6, 57, AQ_ST("north"))
# bancos da congregação nos lados da via
for z in range(10, 52, 4):
    b.fill(24, 1, z, 28, 1, z, BENCH("north"))
    b.fill(35, 1, z, 39, 1, z, BENCH("north"))
# grande candelabro pendente (braseiros em corrente) ao longo da nave
for z in range(12, 54, 12):
    b.fill(31, 45, z, 31, 51, z, CHAIN); b.put(31, 44, z, BRAZ())
    b.fill(32, 45, z, 32, 51, z, CHAIN); b.put(32, 44, z, BRAZ())
# estandartes entre colunas
for z in range(9, 58, 12):
    b.put(10, 20, z, BANNER("east", 0)); b.put(10, 21, z, BANNER("east", 1))
    b.put(53, 20, z, BANNER("west", 0)); b.put(53, 21, z, BANNER("west", 1))
# marcadores
b.put(31, 4, 57, MK("marker_commander_point"))
for z in (16, 30, 44): b.put(26, 1, z, MK("marker_civil_spawn")); b.put(37, 1, z, MK("marker_civil_spawn"))
b.put(31, 1, 8, MK("marker_guardsman_spawn")); b.put(32, 1, 8, MK("marker_guardsman_spawn"))
b.put(23, 4, 55, MK("marker_defense_point")); b.put(40, 4, 55, MK("marker_defense_point"))
b.put(31, 1, 30, MK("marker_patrol_point"))
b.put(15, 1, 30, MK("marker_loot_point")); b.put(48, 1, 30, MK("marker_loot_point"))
b.resolve()
emit(b, "cathedral_nave_01", plans=[(62, "plan"), (3, "plan_piso")], sx=[(31, "corte_nave_x31")], sz=[(57, "altar_z57")])

# ======================================================================================
# CASCO ADMINISTRATIVO (torres de escritórios, arquivos, iluminação de trabalho)
# ======================================================================================
ADMIN_FLOORS = [1, 13, 25, 37]

def admin_shell(b, digits):
    perimeter(b, 48)
    for fy in ADMIN_FLOORS[1:]:
        b.fill(1, fy - 1, 1, 62, fy - 1, 62, STEEL)
    b.fill(1, 48, 1, 62, 48, 62, ASH)
    for (cx, cz) in ((6, 6), (56, 6), (6, 56), (56, 56)):
        b.fill(cx, 1, cz, cx + 1, 47, cz + 1, COL("y"))
    # corredores E-W (socket admin_hall) nos andares 2 e 4
    for fy in (ADMIN_FLOORS[1], ADMIN_FLOORS[3]):
        b.fill(0, fy, 29, 0, fy + 3, 32, AIR)
        b.fill(63, fy, 29, 63, fy + 3, 32, AIR)
    # rua no térreo
    street_tunnel(b, 0, 6)
    if digits:
        b.digit(digits[0], face_z=0, x0=44, y0=20); b.digit(digits[1], face_z=0, x0=52, y0=20)
        b.fill(42, 18, 0, 60, 18, 0, HAZ); b.fill(42, 28, 0, 60, 28, 0, HAZ)
    # escadaria lateral (fora da faixa da rua x24..39)
    for i in range(len(ADMIN_FLOORS) - 1):
        fy = ADMIN_FLOORS[i]
        for step, z in enumerate(range(10, 18)):
            yy = fy + step
            if yy < fy + 11:
                b.put(4, yy, z, SST("south")); b.put(5, yy, z, SST("south"))
                b.fill(4, fy, z, 5, yy - 1, z, STEEL)
        b.fill(3, fy + 11, 10, 6, fy + 11, 17, STEEL)
        b.put(4, fy + 3, 11, LYEL)

def office(b, x0, z0, fy):
    """Sala de escritório 12x12: mesa, cadeira, estantes de arquivo, terminal, lâmpada."""
    x1, z1 = x0 + 11, z0 + 11
    for wx, wz0, wz1 in [(x0, z0, z1), (x1, z0, z1)]:
        b.fill(wx, fy, wz0, wx, fy + 4, wz1, CATHW)
    b.fill(x0, fy, z0, x1, fy + 4, z0, CATHW)
    b.fill(x0 + 5, fy, z0, x0 + 6, fy + 2, z0, AIR)                  # porta
    # mobília
    b.put(x0 + 3, fy, z0 + 3, TABLE()); b.put(x0 + 3, fy, z0 + 4, CHAIR("north"))
    b.put(x0 + 2, fy + 1, z0 + 2, TERM("east"))
    for zz in range(z0 + 6, z1): b.put(x0 + 1, fy, zz, SHELF("east")); b.put(x1 - 1, fy, zz, SHELF("west"))
    b.put(x0 + 6, fy + 4, z0 + 6, LAMP())
    # janela
    if x0 <= 2: b.fill(x0, fy + 1, z0 + 5, x0, fy + 2, z0 + 6, GRATE)

# ======================================================================================
# MÓDULO: scriptorium_01 (arquivo/escritório — torres de estantes, cogitadores)
# ======================================================================================
b = ModuleBuilder(64, 64, 64, seed=8101)
admin_shell(b, digits=(0, 7))
for fy in ADMIN_FLOORS:
    office(b, 8, 8, fy)
    office(b, 44, 8, fy)
    office(b, 8, 44, fy)
    office(b, 44, 44, fy)
    for z in range(20, 44): b.put(31, fy + 4, z, LSTRIP("z"))
# grande sala de cogitadores no térreo central (banco de servidores + telas)
for x in range(22, 42, 3):
    b.fill(x, 1, 24, x, 5, 24, CASING)
    b.put(x, 2, 25, COGI("south")); b.put(x, 4, 25, CTRL("south"))
    b.fill(x, 1, 39, x, 5, 39, CASING)
    b.put(x, 2, 38, COGI("north"))
for x in range(24, 40, 4): b.put(x, 6, 31, LGRN)
# arquivo vertical (estantes altas) num átrio
b.fill(14, 1, 28, 14, 44, 35, SHELF("east"))
b.fill(49, 1, 28, 49, 44, 35, SHELF("west"))
# estátua da águia no saguão + propaganda
b.put(31, 1, 14, AQ_ST("south")); b.put(29, 1, 14, BRAZ()); b.put(33, 1, 14, BRAZ())
b.put(20, 3, 0, PROP("south")); b.put(43, 3, 0, PROP("south"))
# telhado: antenas, dutos, parapeito
for x in range(1, 63): b.put(x, 49, 1, WALL_RAW); b.put(x, 49, 62, WALL_RAW)
for (ax, az) in ((12, 12), (50, 50)):
    b.fill(ax, 49, az, ax, 58, az, TRUNKP()); b.put(ax, 59, az, BEACON := S("warning_beacon"))
# marcadores
for fy in ADMIN_FLOORS:
    b.put(12, fy, 12, MK("marker_worker_spawn")); b.put(48, fy, 48, MK("marker_worker_spawn"))
b.put(31, 1, 31, MK("marker_commander_point"))
b.put(31, 1, 25, MK("marker_loot_point"))
b.put(31, 1, 3, MK("marker_guardsman_spawn"))
b.put(14, 13, 31, MK("marker_patrol_point")); b.put(49, 25, 31, MK("marker_patrol_point"))
b.put(31, 1, 14, MK("marker_trade_point"))
b.resolve()
emit(b, "scriptorium_01", plans=[(48, "plan"), (12, "plan_a1")], sx=[(31, "corte_x31")])

# ======================================================================================
# MÓDULO: tribunal_01 (tribunal/câmaras — grande salão de justiça + celas)
# ======================================================================================
b = ModuleBuilder(64, 64, 64, seed=8103)
perimeter(b, 48)
street_tunnel(b, 0, 6)
b.fill(1, 30, 1, 62, 30, 62, STEEL)                                 # laje entre salão(baixo) e câmaras(alto)
b.fill(1, 48, 1, 62, 48, 62, ASH)
# --- GRANDE SALÃO DE JUSTIÇA (y1..29) ---
# colunas majestosas
for z in range(8, 58, 10):
    for cx in (8, 55): b.fill(cx, 1, z, cx, 28, z, COL("y")); b.put(cx, 29, z, GARCH)
# estrado do juiz ao fundo sul, elevado, com guardião atrás
b.fill(24, 1, 52, 39, 6, 60, ASH)
for step in range(3): b.fill(24 + step, 1 + step, 52 - step, 39 - step, 1 + step, 52 - step, AST("north"))
b.fill(28, 6, 55, 35, 7, 55, CATHW)                                 # bancada
b.put(31, 7, 56, CHAIR("north")); b.put(32, 7, 56, CHAIR("north"))
b.put(28, 6, 55, BRAZ()); b.put(35, 6, 55, BRAZ())
b.put(29, 7, 59, GUARD("north", 0)); b.put(29, 8, 59, GUARD("north", 1)); b.put(29, 9, 59, GUARD("north", 2))
b.put(34, 7, 59, GUARD("north", 0)); b.put(34, 8, 59, GUARD("north", 1)); b.put(34, 9, 59, GUARD("north", 2))
b.fill(30, 10, 59, 33, 15, 59, AQUILA)                              # águia atrás do estrado
# bancos do público
for z in range(14, 48, 4):
    b.fill(20, 1, z, 27, 1, z, BENCH("north"))
    b.fill(36, 1, z, 43, 1, z, BENCH("north"))
# balaustrada separando público do estrado
for x in range(18, 46): b.put(x, 1, 50, RAIL_RAW)
b.fill(30, 1, 50, 33, 1, 50, AIR)
# vitrais laterais + candelabros
gothic_windows(b, 0, 48, [(z, 14) for z in range(10, 52, 10)])
gothic_windows(b, 63, 48, [(z, 14) for z in range(10, 52, 10)])
for z in range(14, 50, 12):
    b.fill(31, 24, z, 31, 28, z, CHAIN); b.put(31, 23, z, BRAZ())
# --- CÂMARAS/CELAS no alto (y30..47) ---
b.fill(24, 31, 0, 39, 35, 6, AIR); b.pave_street(0, 6)              # (rua já feita; só garante piso alto? não) 
# corredor de celas
for cell_z in range(6, 58, 6):
    for side_x in (6, 48):
        b.fill(side_x, 31, cell_z, side_x + 9, 36, cell_z + 4, ASH)
        b.fill(side_x + 1, 31, cell_z + 1, side_x + 8, 35, cell_z + 3, AIR)
        # porta gradeada
        gx = side_x + 9 if side_x == 6 else side_x
        b.fill(gx, 31, cell_z + 1, gx, 34, cell_z + 3, GRATE)
        # catre + balde
        b.put(side_x + 2, 31, cell_z + 1, SSLAB("top"))
        b.put(side_x + 7, 31, cell_z + 3, S("supply_crate", facing="north"))
        b.put(side_x + 4, 35, cell_z + 2, LRED)
# corredor central de vigília
for z in range(4, 60): b.put(31, 36, z, LSTRIP("z"))
for x in range(20, 44, 6): b.put(x, 31, 31, LRED)
b.put(31, 31, 6, TERM("south"))
# marcadores
b.put(31, 7, 56, MK("marker_commander_point"))
b.put(29, 7, 58, MK("marker_defense_point")); b.put(34, 7, 58, MK("marker_defense_point"))
for z in (18, 30, 42): b.put(23, 1, z, MK("marker_civil_spawn")); b.put(40, 1, z, MK("marker_civil_spawn"))
b.put(31, 1, 8, MK("marker_guardsman_spawn"))
for cell_z in (12, 30, 48): b.put(10, 31, cell_z + 2, MK("marker_enemy_spawn"))
b.put(31, 31, 31, MK("marker_patrol_point"))
b.put(31, 1, 30, MK("marker_patrol_point"))
b.resolve()
emit(b, "tribunal_01", plans=[(48, "plan"), (3, "plan_salao"), (33, "plan_celas")], sz=[(56, "estrado_z56")])

# ======================================================================================
print(f"{'módulo':22s} {'dim':>12s} {'não-ar':>8s} {'palette':>7s} {'bytes':>8s}")
for rel, sx, sy, sz, nonair, pal, size in results:
    print(f"{rel:22s} {sx}x{sy}x{sz:>4} {nonair:8d} {pal:7d} {size:8d}")
print(f"prévias em {PREV}/")
