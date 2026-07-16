#!/usr/bin/env python3
"""FASE 7 — Gera os 3 módulos do distrito HAB STACKS + TRANSIT (192×128 total, empilha sobre
a Manufactorum). Fileira de 3 módulos 64×64×64: habitação vertical, nexo de transporte, e um
setor cívico com mercado + capela.

Layout (data/firstcrusade/hive_districts/hab_stacks.json):
  z 0..63 (frente): hab_block_01 | transit_nexus_01 | market_chapel_01
Rua (socket street x25..38) atravessa os 3, encaixando na Manufactorum abaixo e na rua ao norte.

Sockets: street (N/S) | hab_corridor (E/W: corredores nos níveis 2 e 4) | down=canopy (assenta
no dossel da Manufactorum) | up=hab_roof.

Usa móveis (cadeira/mesa/cama-beliche/tapete/estante), luzes fortes (braseiro/holofote/lâmpada),
estátuas (santo/guardião/águia) e blocos industriais das fases anteriores.

Uso: python3 tools/gen_hive_hab.py
"""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from hive_module_lib import *  # noqa

OUT = "src/main/resources/data/firstcrusade/structures/hive/hab"
PREV = "/tmp/fase7"
os.makedirs(PREV, exist_ok=True)
results = []

# helpers de móveis (definidos na lib da 6.5B; garantimos fallback se algum faltar)
def _seat(facing): return S("hive_chair", facing=facing)
def _bench(facing): return S("hive_bench", facing=facing)
def _table(): return S("hive_table")
def _rug(): return S("hive_rug")
def _shelf(facing): return S("shelf_unit", facing=facing)
def _crate(facing): return S("supply_crate", facing=facing)
def _flood(facing): return S("industrial_floodlight", facing=facing)
def _lamp(): return S("hanging_hive_lamp")
def _brazier(): return S("cathedral_brazier")
def _beacon(): return S("warning_beacon")
def _terminal(facing): return S("wall_terminal", facing=facing)
def _bust(facing): return S("saint_bust", facing=facing)
def _aquila(facing): return S("aquila_statue", facing=facing)
def SAINT(facing, part): return S("saint_statue", facing=facing, part=part)
def GUARD(facing, part): return S("imperial_guardian_statue", facing=facing, part=part)
def BANNER(facing, part): return S("aquila_banner", facing=facing, part=part)

def emit(b, rel, plans=(), sx=(), sz=()):
    nonair, pal, size = b.write_nbt(f"{OUT}/{rel}.nbt")
    b.previews(f"{PREV}/{rel}", plans, sx, sz)
    results.append((rel, b.sx, b.sy, b.sz, nonair, pal, size))

# ======================================================================================
# CASCO HAB COMPARTILHADO — 4 andares de 12 (y1..48) + laje de teto, corredores E-W
# ======================================================================================
FLOOR_Y = [1, 13, 25, 37]        # pisos dos 4 andares
FLOOR_TOP = 48

def hab_shell(b, digits=None):
    b.fill(0, 0, 0, 63, 0, 63, ASH)                                    # base (assenta no dossel)
    # paredes N (rua) e S; E/W abertas nos corredores (socket hab_corridor)
    b.fill(0, 1, 0, 63, FLOOR_TOP, 0, ASH)
    b.fill(0, 1, 63, 63, FLOOR_TOP, 63, ASH)
    b.fill(0, 1, 0, 0, FLOOR_TOP, 63, ASH)                             # oeste (fechado exceto corredor)
    b.fill(63, 1, 0, 63, FLOOR_TOP, 63, ASH)                           # leste
    # lajes de andar
    for fy in FLOOR_Y[1:]:
        b.fill(1, fy - 1, 1, 62, fy - 1, 62, STEEL)
    b.fill(1, FLOOR_TOP, 1, 62, FLOOR_TOP, 62, ASH)                    # teto
    # pilares estruturais 2x2
    for (cx, cz) in ((6, 6), (56, 6), (6, 56), (56, 56), (30, 30)):
        b.fill(cx, 1, cz, cx + 1, FLOOR_TOP - 1, cz + 1, COL("y"))
    # corredores E-W nos andares 2 e 4 (socket hab_corridor): abre as paredes E/W
    for fy in (FLOOR_Y[1], FLOOR_Y[3]):
        b.fill(0, fy, 29, 0, fy + 3, 32, AIR)
        b.fill(63, fy, 29, 63, fy + 3, 32, AIR)
        for z in (29, 30, 31, 32):
            b.put(0, fy + 4, z, GARCH); b.put(63, fy + 4, z, GARCH)
    # rua atravessa a parede norte no térreo (túnel)
    b.fill(24, 2, 0, 39, 6, 6, AIR)
    b.pave_street(0, 6)
    b.fill(24, 7, 0, 39, 7, 6, STEEL)
    # números de setor
    if digits:
        b.digit(digits[0], face_z=0, x0=44, y0=20)
        b.digit(digits[1], face_z=0, x0=52, y0=20)
        b.fill(42, 18, 0, 60, 18, 0, HAZ); b.fill(42, 28, 0, 60, 28, 0, HAZ)
    # escada central em espiral ligando os 4 andares (canto NW) + poço de elevador
    for i in range(len(FLOOR_Y) - 1):
        fy = FLOOR_Y[i]
        # lance de escada de aço
        for step, z in enumerate(range(4, 12)):
            yy = fy + step * 1
            if yy < fy + 11:
                b.put(3, yy, z, SST("south")); b.put(4, yy, z, SST("south"))
                b.fill(3, fy, z, 4, yy - 1, z, STEEL)
        b.fill(3, fy + 11, 4, 4, fy + 11, 11, STEEL)                   # patamar
    # lumens em cada patamar da escada
    for fy in FLOOR_Y:
        b.put(3, fy + 3, 3, LYEL)

def hab_unit(b, x0, z0, fy, facing_door):
    """Uma habitação mobiliada 10x10 no andar fy, canto em (x0,z0)."""
    x1, z1 = x0 + 9, z0 + 9
    # paredes internas (finas)
    b.fill(x0, fy, z0, x1, fy + 4, z0, CATHW)
    b.fill(x0, fy, z1, x1, fy + 4, z1, CATHW)
    b.fill(x0, fy, z0, x0, fy + 4, z1, CATHW)
    b.fill(x1, fy, z0, x1, fy + 4, z1, CATHW)
    # porta
    if facing_door == "south":
        b.fill(x0 + 4, fy, z1, x0 + 5, fy + 2, z1, AIR)
    else:
        b.fill(x0 + 4, fy, z0, x0 + 5, fy + 2, z0, AIR)
    # interior: tapete, mesa+cadeiras, beliche, estante, terminal, lâmpada
    b.put(x0 + 4, fy, z0 + 4, _rug()); b.put(x0 + 5, fy, z0 + 4, _rug())
    b.put(x0 + 4, fy, z0 + 5, _rug()); b.put(x0 + 5, fy, z0 + 5, _rug())
    b.put(x0 + 2, fy, z0 + 2, _table())
    b.put(x0 + 2, fy, z0 + 3, _seat("north")); b.put(x0 + 3, fy, z0 + 2, _seat("west"))
    # beliche (2 andares de laje de aço + tapete)
    b.fill(x0 + 7, fy, z0 + 6, x0 + 8, fy, z0 + 8, SSLAB("top"))
    b.fill(x0 + 7, fy + 2, z0 + 6, x0 + 8, fy + 2, z0 + 8, SSLAB("top"))
    b.put(x0 + 7, fy + 1, z0 + 6, STEEL); b.put(x0 + 8, fy + 1, z0 + 8, STEEL)
    b.put(x0 + 1, fy, z0 + 7, _shelf("east"))
    b.put(x0 + 6, fy + 2, z0 + 1, _terminal("south"))
    b.put(x0 + 5, fy + 4, z0 + 5, _lamp())
    # janela gradeada para fora
    if x0 <= 2:
        b.fill(x0, fy + 1, z0 + 4, x0, fy + 2, z0 + 5, GRATE)

# ======================================================================================
# MÓDULO: hab_block_01 (blocos de habitação — 4 andares de apês mobiliados)
# ======================================================================================
b = ModuleBuilder(64, 64, 64, seed=7101)
hab_shell(b, digits=(0, 6))
# 4 andares × 4 unidades cada (grade de apês nos cantos, deixando corredor central)
for fy in FLOOR_Y:
    hab_unit(b, 8, 8, fy, "south")
    hab_unit(b, 46, 8, fy, "south")
    hab_unit(b, 8, 46, fy, "north")
    hab_unit(b, 46, 46, fy, "north")
    # corredor central iluminado (lumen no teto de cada andar)
    for z in range(20, 44):
        b.put(31, fy + 4, z, LSTRIP("z"))
    for x in range(20, 44, 6):
        b.put(x, fy, 31, LYEL if fy != FLOOR_Y[0] else GRATE)
# átrio central vertical: buraco no meio ligando visualmente os andares
for fy in FLOOR_Y[1:]:
    b.fill(28, fy - 1, 28, 35, fy - 1, 35, AIR)
    for x in range(28, 36):
        b.put(x, fy - 1, 27, RAIL_RAW); b.put(x, fy - 1, 36, RAIL_RAW)
# estandarte da águia descendo pelo átrio
b.put(31, 45, 31, BANNER("south", 0)); b.put(31, 46, 31, BANNER("south", 1))
b.put(32, 13, 31, BANNER("north", 0)); b.put(32, 14, 31, BANNER("north", 1))
# telhado: tanques de água, dutos, parapeito
for x in range(1, 63): b.put(x, FLOOR_TOP + 1, 1, WALL_RAW); b.put(x, FLOOR_TOP + 1, 62, WALL_RAW)
for (tx, tz) in ((10, 10), (48, 48)):
    b.fill(tx, FLOOR_TOP + 1, tz, tx + 3, FLOOR_TOP + 4, tz + 3, BOILER("y"))
b.put(31, FLOOR_TOP + 1, 31, _beacon())
# marcadores
for fy in FLOOR_Y:
    b.put(12, fy, 12, MK("marker_civil_spawn")); b.put(50, fy, 50, MK("marker_civil_spawn"))
b.put(31, FLOOR_TOP + 2, 31, MK("marker_defense_point"))
b.put(31, 2, 31, MK("marker_patrol_point"))
b.put(50, 13, 12, MK("marker_trade_point"))
b.put(12, 25, 50, MK("marker_loot_point"))
b.put(31, 2, 3, MK("marker_guardsman_spawn"))
b.resolve()
emit(b, "hab_block_01", plans=[(FLOOR_TOP, "plan"), (12, "plan_a1"), (24, "plan_a2")], sx=[(31, "corte_x31")])

# ======================================================================================
# MÓDULO: transit_nexus_01 (nexo de transporte — trilhos, plataformas, elevadores)
# ======================================================================================
b = ModuleBuilder(64, 64, 64, seed=7102)
b.fill(0, 0, 0, 63, 0, 63, ASH)
b.fill(0, 1, 0, 63, 1, 63, ASH)
# paredes perimetrais com grandes arcos
b.fill(0, 1, 0, 63, 40, 0, ASH); b.fill(0, 1, 63, 63, 40, 63, ASH)
b.fill(0, 1, 0, 0, 40, 63, ASH); b.fill(63, 1, 0, 63, 40, 63, ASH)
b.fill(1, 40, 1, 62, 40, 62, STEEL)                                    # teto do átrio
# rua atravessa
b.fill(24, 2, 0, 39, 6, 63, AIR)
b.pave_street(0, 63)
b.fill(24, 7, 0, 39, 7, 63, STEEL)
# ESTAÇÃO DE TRILHOS: duas plataformas E-W ladeando a rua, com trilhos no térreo (z10, z53)
for pz in (10, 53):
    for x in range(2, 62):
        b.put(x, 1, pz, STEEL); b.put(x, 2, pz, RAIL_TRACK())         # trilho
        b.put(x, 1, pz - 1, ASH); b.put(x, 1, pz + 1, ASH)
    # plataforma elevada ao lado do trilho
    plat_z = pz + 2 if pz < 32 else pz - 3
    b.fill(2, 2, plat_z, 61, 2, plat_z + 2, ASH)
    for x in range(2, 62, 2): b.put(x, 2, plat_z if pz < 32 else plat_z + 2, HAZ)
    # cobertura da plataforma em arco
    for x in range(6, 58, 8):
        b.fill(x, 3, plat_z, x, 8, plat_z, STEEL)
        b.put(x, 9, plat_z, GARCH)
    for x in range(4, 60, 6): b.put(x, 8, plat_z, _lamp())
# vagão parado numa plataforma (feito de contêiner + rodas)
b.fill(20, 2, 10, 30, 5, 10, CONT("south"))
b.fill(20, 3, 10, 30, 4, 10, AIR)                                     # janelas
b.put(19, 1, 10, STEEL); b.put(31, 1, 10, STEEL)
# QUADRO DE HORÁRIOS / painéis
for x in (14, 44):
    b.put(x, 3, 8, COGITATOR("south")); b.put(x + 1, 3, 8, CTRLPANEL("south"))
    b.put(x, 5, 8, _terminal("south"))
# 4 ELEVADORES (poços blindados com plataforma e ladder) nos cantos, sobem além do teto
for (ex, ez) in ((4, 4), (56, 4), (4, 56), (56, 56)):
    b.fill(ex, 1, ez, ex + 4, 40, ez + 4, ASH)
    b.fill(ex + 1, 1, ez + 1, ex + 3, 40, ez + 3, AIR)
    for (cx, cz) in ((ex, ez), (ex + 4, ez), (ex, ez + 4), (ex + 4, ez + 4)):
        b.fill(cx, 1, cz, cx, 40, cz, ARMOR)
    b.fill(ex + 1, 1, ez + 1, ex + 3, 1, ez + 3, GRATE)              # cabine base
    for y in range(2, 40): b.put(ex + 1, y, ez + 1, LADDER("east"))
    b.put(ex + 2, 39, ez + 2, LYEL)
    b.fill(ex + 1, 20, ez + 1, ex + 3, 20, ez + 3, GRATE)           # patamar intermediário
    b.fill(ex, 6, ez, ex, 8, ez, AIR)                                # porta térrea
    b.put(ex + 2, 5, ez, HAZ)
# átrio central alto com estátua monumental do guardião (na ilha entre as ruas)
b.fill(29, 1, 30, 34, 1, 33, ARMOR)
b.put(31, 2, 31, GUARD("south", 0)); b.put(31, 3, 31, GUARD("south", 1)); b.put(31, 4, 31, GUARD("south", 2))
b.put(30, 2, 31, _brazier()); b.put(33, 2, 31, _brazier())
# grandes lumens pendurados do teto do átrio
for x in range(10, 56, 12):
    for z in range(10, 56, 14):
        b.fill(x, 37, z, x, 39, z, CHAIN); b.put(x, 36, z, _lamp())
# marcadores
for pz in (10, 53):
    b.put(25, 2, pz, MK("marker_civil_spawn")); b.put(45, 2, pz, MK("marker_civil_spawn"))
b.put(31, 2, 31, MK("marker_commander_point"))
b.put(14, 2, 12, MK("marker_trade_point")); b.put(44, 2, 51, MK("marker_trade_point"))
for (ex, ez) in ((4, 4), (56, 56)): b.put(ex + 2, 2, ez + 2, MK("marker_vehicle_point"))
b.put(31, 2, 6, MK("marker_guardsman_spawn"))
b.put(6, 2, 31, MK("marker_patrol_point")); b.put(57, 2, 31, MK("marker_patrol_point"))
b.resolve()
emit(b, "transit_nexus_01", plans=[(40, "plan"), (5, "plan_terreo")], sz=[(10, "corte_plataforma_z10")])

# ======================================================================================
# MÓDULO: market_chapel_01 (mercado no térreo + capela no alto)
# ======================================================================================
b = ModuleBuilder(64, 64, 64, seed=7103)
b.fill(0, 0, 0, 63, 0, 63, ASH)
b.fill(0, 1, 0, 63, 1, 63, ASH)
b.fill(0, 1, 0, 63, 47, 0, ASH); b.fill(0, 1, 63, 63, 47, 63, ASH)
b.fill(0, 1, 0, 0, 47, 63, ASH); b.fill(63, 1, 0, 63, 47, 63, ASH)
b.fill(1, 24, 1, 62, 24, 62, STEEL)                                    # laje entre mercado(baixo) e capela(alto)
b.fill(1, 47, 1, 62, 47, 62, ASH)                                     # teto
# rua atravessa térreo
b.fill(24, 2, 0, 39, 6, 6, AIR); b.pave_street(0, 6); b.fill(24, 7, 0, 39, 7, 6, STEEL)
# --- MERCADO (y1..23): fileiras de bancas ---
for row_z in (10, 20, 44, 54):
    for x in range(6, 58, 6):
        b.put(x, 1, row_z, _table()); b.put(x + 1, 1, row_z, _table())
        b.put(x, 2, row_z, _crate("south"))
        b.put(x + 1, 1, row_z - 1, _seat("south"))
        # toldo
        b.fill(x, 4, row_z, x + 2, 4, row_z, HAZARD_YD := S("hazard_stripe_panel"))
    # lumen sobre a fileira
    for x in range(8, 56, 8): b.put(x, 6, row_z, _lamp())
# pilares do mercado
for (cx, cz) in ((16, 16), (48, 16), (16, 48), (48, 48)):
    b.fill(cx, 1, cz, cx, 23, cz, IMPERIAL_COL := COL("y"))
# banca de comida quente (fornalha + cadinho)
b.put(30, 1, 32, FURNACE("south")); b.put(32, 1, 32, CRUCIBLE)
b.put(31, 1, 34, _bench("north"))
# escada do mercado para a capela (canto)
for step, y in enumerate(range(2, 24)):
    z = 4 + step
    if z < 20:
        b.put(58, y, z, SST("south")); b.put(59, y, z, SST("south"))
        b.fill(58, 1, z, 59, y - 1, z, STEEL)
# --- CAPELA (y24..47): nave com colunas, estátuas, altar, vitrais ---
# piso nobre com tapete central
for z in range(6, 58):
    b.put(30, 25, z, _rug()); b.put(31, 25, z, _rug()); b.put(32, 25, z, _rug())
# colunas da nave + arcos
for z in range(8, 58, 8):
    for cx in (12, 51):
        b.fill(cx, 25, z, cx, 40, z, COL("y"))
        b.put(cx, 41, z, GARCH)
    b.fill(12, 42, z, 51, 42, z, STEEL)                               # viga
# vitrais (lumens coloridos nas paredes altas)
for z in range(10, 56, 6):
    b.put(0, 32, z, LGRN); b.put(0, 33, z, LYEL)
    b.put(63, 32, z, LRED); b.put(63, 33, z, LYEL)
# ALTAR ao fundo (sul) com águia e estátuas de santo ladeando
b.fill(26, 25, 56, 37, 28, 58, ASH)
b.fill(28, 29, 57, 35, 33, 57, AQUILA)                                # grande águia na parede
b.put(31, 26, 56, _brazier()); b.put(32, 26, 56, _brazier())
b.put(24, 25, 55, SAINT("east", 0)); b.put(24, 26, 55, SAINT("east", 1)); b.put(24, 27, 55, SAINT("east", 2))
b.put(39, 25, 55, SAINT("west", 0)); b.put(39, 26, 55, SAINT("west", 1)); b.put(39, 27, 55, SAINT("west", 2))
# bancos da congregação
for z in range(14, 52, 4):
    b.fill(24, 25, z, 28, 25, z, _bench("south"))
    b.fill(34, 25, z, 38, 25, z, _bench("south"))
# grande braseiro central pendente
b.fill(31, 42, 31, 31, 44, 31, CHAIN); b.put(31, 41, 31, _brazier())
# campanário: torre subindo do teto
b.fill(28, 47, 28, 35, 58, 35, ASH)
b.fill(30, 48, 30, 33, 57, 33, AIR)
for z in (30, 33): 
    b.fill(30, 50, z, 33, 55, z, GRATE)
b.put(31, 56, 31, _beacon()); b.put(32, 56, 32, _beacon())
b.put(31, 47, 31, BANNER("south", 0)); b.put(31, 48, 31, BANNER("south", 1))
# marcadores
for row_z in (10, 20, 44, 54): b.put(10, 1, row_z, MK("marker_trade_point"))
for row_z in (14, 48): b.put(31, 1, row_z, MK("marker_civil_spawn"))
b.put(31, 25, 40, MK("marker_civil_spawn")); b.put(31, 25, 20, MK("marker_civil_spawn"))
b.put(31, 25, 54, MK("marker_commander_point"))
b.put(31, 1, 32, MK("marker_trade_point"))
b.put(31, 47, 31, MK("marker_defense_point"))
b.put(31, 2, 3, MK("marker_guardsman_spawn"))
b.put(12, 25, 30, MK("marker_patrol_point"))
b.resolve()
emit(b, "market_chapel_01", plans=[(47, "plan"), (5, "plan_mercado"), (30, "plan_capela")], sz=[(31, "corte_z31")])

# ======================================================================================
print(f"{'módulo':24s} {'dim':>12s} {'não-ar':>8s} {'palette':>7s} {'bytes':>8s}")
for rel, sx, sy, sz, nonair, pal, size in results:
    print(f"{rel:24s} {sx}x{sy}x{sz:>4} {nonair:8d} {pal:7d} {size:8d}")
print(f"prévias em {PREV}/")
