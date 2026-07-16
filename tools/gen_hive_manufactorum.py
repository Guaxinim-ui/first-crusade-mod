#!/usr/bin/env python3
"""FASE 6 — Gera os 3 módulos do distrito MANUFACTORUM (192×128 total, empilha sobre o
Cargo Ring da FASE 5). Fileira de 3 módulos de 64×64, altura 64 (pé-direito industrial
alto com passarelas suspensas sobre as máquinas — spec §5.4).

Layout (data/firstcrusade/hive_districts/manufactorum.json):
  z 0..63  (frente, lado cidade):  foundry_01 | assembly_hall_01 | generator_hall_01
Rua (socket street x25..38) atravessa os 3, encaixando no industrial_street_01 ao norte.

Sockets desta fileira (locais):
  street (N/S): x25..38   |  manufactorum_hall (E/W): planta baixa aberta y2..38, z8..55
  down = cargo_ring (assenta sobre o teto do Cargo Ring)  |  up = canopy (dutos no topo)

Uso: python3 tools/gen_hive_manufactorum.py
"""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from hive_module_lib import *  # noqa

OUT = "src/main/resources/data/firstcrusade/structures/hive/industrial"
PREV = "/tmp/fase6"
os.makedirs(PREV, exist_ok=True)
results = []

def emit(b, rel, plans=(), sx=(), sz=()):
    nonair, pal, size = b.write_nbt(f"{OUT}/{rel}.nbt")
    b.previews(f"{PREV}/{rel}", plans, sx, sz)
    results.append((rel, b.sx, b.sy, b.sz, nonair, pal, size))


def dress(b, saints=True):
    """Passada de detalhamento/iluminação (retrabalho pedido: interiores escuros + corredores
    góticos como na arte da passarela). Adiciona braseiros nos pilares-mestres, uma galeria
    gótica iluminada rente à parede sul, estátuas ladeando o túnel da rua, e lumens no piso."""
    # braseiros no topo dos 5 pilares-mestres (luz forte 15)
    for (cx, cz) in ((6, 6), (56, 6), (6, 56), (56, 56), (30, 30)):
        b.put(cx, 41, cz, BRAZIER)
        b.put(cx + 1, 41, cz + 1, BRAZIER)
    # lâmpadas penduradas do teto alto sobre o salão central (não na cabeça das passarelas)
    for x in range(12, 54, 10):
        for z in (16, 32, 48):
            b.put(x, 40, z, CHAIN2("y")); b.put(x, 39, z, HANGLAMP)
    # GALERIA GÓTICA rente à parede oeste (x1..3): arcada iluminada com balaustrada,
    # inspirada na passarela da arte de referência
    for z in range(6, 58):
        b.put(1, 2, z, ASH); b.put(1, 3, z, ASH)                      # piso elevado da galeria
        if z % 4 == 0:
            b.fill(1, 4, z, 1, 9, z, COL("y"))                        # colunas da arcada
            b.put(1, 10, z, GARCH)
        b.put(2, 4, z, RAIL_RAW)                                      # balaustrada
        if z % 6 == 0:
            b.put(2, 5, z, BRAZIER)                                   # braseiros na balaustrada
    for z in range(8, 56, 12):
        b.put(1, 11, z, LYEL)                                         # lumens altos
    # estátuas ladeando o túnel da rua (entrada nobre)
    if saints:
        b.put(22, 2, 3, GUARDIAN("east", 0)); b.put(22, 3, 3, GUARDIAN("east", 1)); b.put(22, 4, 3, GUARDIAN("east", 2))
        b.put(41, 2, 3, GUARDIAN("west", 0)); b.put(41, 3, 3, GUARDIAN("west", 1)); b.put(41, 4, 3, GUARDIAN("west", 2))
        b.put(20, 2, 5, BUST("east")); b.put(43, 2, 5, BUST("west"))
    # estandartes da águia entre os pilares
    b.put(8, 2, 8, BANNER("east", 0)); b.put(8, 3, 8, BANNER("east", 1))
    b.put(55, 2, 55, BANNER("west", 0)); b.put(55, 3, 55, BANNER("west", 1))
    # mais lumens no piso térreo (corrige escuridão)
    for x in range(8, 58, 6):
        for z in range(8, 58, 10):
            if b.get(x, 1, z) == ASH and b.get(x, 2, z) == AIR:
                b.put(x, 1, z, LYEL if (x + z) % 3 else LGRN)
    # troncos de canos grossos descendo pelos cantos (detalhe vertical)
    for (x, z) in ((3, 3), (60, 3), (3, 60), (60, 60)):
        b.fill(x, 2, z, x, 40, z, TRUNK)


# ======================================================================================
# CASCO INDUSTRIAL COMPARTILHADO (pé-direito alto, mezaninos, passadiços, dossel)
# ======================================================================================
def hall_shell(b, digits=None):
    b.fill(0, 0, 0, 63, 0, 63, ASH)                                    # piso térreo (assenta no cargo ring)
    b.fill(0, 1, 0, 63, 1, 63, ASH)
    # paredes perimetrais N e S (E/W ficam abertas = socket manufactorum_hall)
    b.fill(0, 1, 0, 63, 47, 0, ASH)                                    # parede norte (rua)
    b.fill(0, 1, 63, 63, 47, 63, ASH)                                  # parede sul
    # pilares-mestres 2x2 nas 4 quinas do salão + fileira central
    for (cx, cz) in ((6, 6), (56, 6), (6, 56), (56, 56), (30, 30)):
        b.fill(cx, 1, cz, cx + 1, 1, cz + 1, ARMOR)
        b.fill(cx, 2, cz, cx + 1, 39, cz + 1, COL("y"))
        b.fill(cx, 40, cz, cx + 1, 40, cz + 1, CATHW)
    # mezanino de passarela em y14 (perímetro interno) e y26 (galeria de máquinas)
    for y in (14, 26):
        for x in range(2, 62):
            b.put(x, y, 4, CATW); b.put(x, y, 59, CATW)
        for z in range(4, 60):
            b.put(2, y, z, CATW); b.put(61, y, z, CATW)
        # corrimãos internos
        for x in range(2, 62):
            b.put(x, y + 1, 5, RAIL_RAW); b.put(x, y + 1, 58, RAIL_RAW)
        for z in range(5, 59):
            b.put(3, y + 1, z, RAIL_RAW); b.put(60, y + 1, z, RAIL_RAW)
    # torre de acesso térreo->y14->y26 (canto NW): pilar sólido + ladder colada (suporte a oeste)
    for y in range(1, 27):
        b.put(3, y, 8, STEEL)                                          # coluna-suporte
    for y in range(2, 27):
        b.put(4, y, 8, LADDER("east"))                                # face leste = apoio em x3
    # aberturas nas passarelas para sair da ladder
    b.put(4, 14, 8, AIR); b.put(5, 14, 8, AIR)
    b.put(4, 26, 8, AIR); b.put(5, 26, 8, AIR)
    b.put(4, 14, 8, LADDER("east")); b.put(4, 26, 8, LADDER("east"))
    # dossel de dutos no topo (y41..46) — socket canopy
    for zc in (16, 32, 48):
        for x in range(64):
            for y in (42, 43, 44):
                b.put(x, y, zc, RUSTY if (x + y) % 3 == 0 else STEEL)
        for xc in (8, 30, 56):
            b.fill(xc, 42, zc, xc, 44, zc, ARMOR)
            b.put(xc, 45, zc, VALVE("x"))
    b.fill(0, 47, 1, 63, 47, 62, STEEL)                                # laje de teto (com furos dos dutos)
    for zc in (16, 32, 48):
        for x in range(64):
            b.put(x, 47, zc, AIR)                                      # fenda dos dutos sobe ao canopy
    # iluminação alta pendurada
    for x in range(10, 58, 12):
        for z in range(8, 58, 16):
            for y in (40, 39, 38): b.put(x, y, z, CHAIN)
            b.put(x, 37, z, LYEL)
    # rua atravessa a parede norte (túnel z0..8 no piso)
    b.fill(24, 2, 0, 39, 6, 8, AIR)
    b.pave_street(0, 8)
    b.fill(24, 7, 0, 39, 7, 8, STEEL)                                  # verga do túnel
    for x in (27, 36):
        b.put(x, 6, 2, LSTRIP("z")); b.put(x, 6, 6, LSTRIP("z"))
    # números de setor na parede norte
    if digits:
        b.digit(digits[0], face_z=0, x0=44, y0=20)
        b.digit(digits[1], face_z=0, x0=52, y0=20)
        b.fill(42, 18, 0, 60, 18, 0, HAZ); b.fill(42, 28, 0, 60, 28, 0, HAZ)

def wall_pipes(b, wall_z):
    """Coluna de canos subindo pela parede (norte ou sul). Pula a faixa da rua."""
    for x in range(6, 58, 10):
        if wall_z <= 1 and 24 <= x <= 39:
            continue                                                   # não cruza o túnel da rua
        b.fill(x, 2, wall_z, x, 40, wall_z, PIPE_RAW)
        b.put(x, 8, wall_z, VALVE("y")); b.put(x, 24, wall_z, VALVE("y"))
        b.put(x, 41, wall_z, JUNC_RAW)

# ======================================================================================
# MÓDULO: foundry_01 (fundição — fornalhas, cadinhos, calhas de metal)
# ======================================================================================
b = ModuleBuilder(64, 64, 64, seed=6101)
hall_shell(b, digits=(0, 3))
wall_pipes(b, 1)
# bateria de fornalhas na parede sul, viradas para o salão (norte)
for i, x in enumerate(range(8, 56, 8)):
    b.fill(x, 1, 58, x + 5, 8, 62, CASING)
    b.put(x + 1, 2, 57, FURNACE("north")); b.put(x + 3, 2, 57, FURNACE("north"))
    b.fill(x + 1, 3, 62, x + 4, 6, 62, SMOKE_STACK := STACK("y")) if False else None
    # chaminé subindo até o dossel
    b.fill(x + 2, 9, 60, x + 3, 46, 61, STACK("y"))
    b.put(x + 1, 5, 57, LRED)
# cadinhos de fundição (crucibles) numa fileira central com calha
for x in range(12, 52, 8):
    b.fill(x, 2, 28, x + 2, 4, 30, CASING)
    b.put(x + 1, 5, 29, CRUCIBLE)
    b.put(x + 1, 2, 27, VENT("north"))
# calha de metal derretido (esteira quente) do centro às fornalhas
for x in range(8, 56):
    b.put(x, 2, 40, S("smelter_crucible")); b.put(x, 1, 40, ARMOR)
for x in range(8, 56, 4): b.put(x, 3, 39, HAZ)
# ponte rolante (viga com gancho) atravessando o salão em y26
b.fill(4, 26, 20, 60, 26, 20, STEEL)
b.fill(30, 22, 20, 31, 25, 20, CASING)
for y in (21, 20, 19): b.put(30, y, 20, CHAIN)
# sala de controle elevada (canto NE, y14)
b.fill(46, 14, 4, 60, 20, 14, ASH)
b.fill(47, 15, 5, 59, 19, 13, AIR)
b.fill(47, 15, 14, 58, 18, 14, AIR)                                    # janela para o salão
for z in range(5, 14, 2): b.put(46, 16, z, GRATE)
b.put(48, 15, 6, COGITATOR("south")); b.put(52, 15, 6, CTRLPANEL("south"))
b.put(56, 15, 6, COGITATOR("south")); b.put(50, 18, 8, LYEL)
b.put(45, 10, 8, PROPAGANDA("west"))                                   # cartaz na parede
# marcadores
for pos in ((14, 2, 34), (30, 2, 34), (46, 2, 34)):  b.put(*pos, MK("marker_worker_spawn"))
for pos in ((10, 2, 55), (50, 2, 55)):               b.put(*pos, MK("marker_worker_spawn"))
b.put(52, 15, 8, MK("marker_commander_point"))
for pos in ((8, 14, 30), (56, 14, 30)):              b.put(*pos, MK("marker_patrol_point"))
b.put(30, 2, 45, MK("marker_loot_point"))
for pos in ((6, 2, 20), (56, 2, 44)):                b.put(*pos, MK("marker_cover_point"))
b.put(30, 2, 6, MK("marker_civil_spawn"))
dress(b)
b.resolve()
emit(b, "foundry_01", plans=[(46, "plan"), (10, "plan_terreo")], sx=[(30, "corte_x30")])

# ======================================================================================
# MÓDULO: assembly_hall_01 (montagem — esteiras, prensas, braços; setor danificado)
# ======================================================================================
b = ModuleBuilder(64, 64, 64, seed=6102)
hall_shell(b, digits=(0, 4))
wall_pipes(b, 62)
# duas linhas de esteira longitudinais (E-W) no térreo
for lane_z in (18, 44):
    for x in range(6, 58):
        b.put(x, 2, lane_z, CONVEYOR("x")); b.put(x, 1, lane_z, STEEL)
    # prensas periódicas sobre a linha
    for x in range(10, 56, 12):
        b.fill(x, 2, lane_z - 1, x, 7, lane_z - 1, STEEL)              # pórtico
        b.fill(x, 2, lane_z + 1, x, 7, lane_z + 1, STEEL)
        b.fill(x - 1, 7, lane_z - 1, x + 1, 7, lane_z + 1, STEEL)
        b.put(x, 4, lane_z, PRESS)                                     # martelo suspenso
        b.put(x - 1, 6, lane_z, LYEL)
    # peças em processamento (contêineres pequenos)
    for x in range(8, 56, 6):
        b.put(x, 3, lane_z, CONT("north"))
# esteira transversal ligando as duas linhas (elevador de rolos)
for z in range(18, 45):
    b.put(30, 2, z, CONVEYOR("y")); b.put(30, 1, z, STEEL)
# fileira de consoles de supervisão no mezanino y14 (parede norte interna)
for x in range(8, 56, 6):
    b.put(x, 15, 3, COGITATOR("south")); b.put(x + 2, 15, 3, CTRLPANEL("south"))
# braços/dutos de ventilação descendo do dossel
for (x, z) in ((16, 32), (48, 32)):
    b.fill(x, 27, z, x, 41, z, VENTDUCT("y"))
    b.put(x, 26, z, JUNC_RAW)
# SETOR DANIFICADO (canto SE do térreo): linha destruída, entulho, faíscas, alerta
b.fill(44, 2, 46, 58, 7, 58, AIR)                                      # abre o volume
for k in range(24):
    x = b.rng.randrange(44, 59); z = b.rng.randrange(46, 59)
    b.put(x, 2, z, RUSTY if k % 2 else ASH_CR)
    if k % 3 == 0: b.put(x, 3, z, ASH_CR)
b.fill(48, 2, 52, 54, 2, 52, CONVEYOR("x"))                            # esteira torta parada
b.put(50, 2, 52, AIR); b.put(52, 2, 52, RUSTY)                         # segmentos faltando
for (x, z) in ((46, 48), (56, 56), (50, 50)):
    b.fill(x, 4, z, x, 8, z, PIPE_RAW); b.put(x, 9, z, JUNC_RAW)       # canos rompidos
b.put(46, 4, 48, LRED); b.put(56, 4, 56, LRED); b.put(50, 5, 50, LRED)
b.put(52, 3, 54, PROPAGANDA("west"))                                   # cartaz rasgado (só decor)
# marcadores
for pos in ((14, 2, 22), (26, 2, 40), (40, 2, 22), (44, 2, 40)): b.put(*pos, MK("marker_worker_spawn"))
for pos in ((8, 15, 4), (44, 15, 4)):               b.put(*pos, MK("marker_patrol_point"))
b.put(50, 2, 55, MK("marker_enemy_spawn")); b.put(56, 2, 50, MK("marker_enemy_spawn"))
b.put(46, 2, 47, MK("marker_construction_point"))
b.put(30, 2, 30, MK("marker_loot_point"))
for pos in ((6, 2, 30), (56, 2, 30)):               b.put(*pos, MK("marker_cover_point"))
b.put(30, 2, 6, MK("marker_civil_spawn"))
dress(b)
b.resolve()
emit(b, "assembly_hall_01", plans=[(46, "plan"), (10, "plan_terreo")], sz=[(18, "corte_esteira_z18")])

# ======================================================================================
# MÓDULO: generator_hall_01 (energia — turbinas, caldeiras, reator; risco elétrico)
# ======================================================================================
b = ModuleBuilder(64, 64, 64, seed=6103)
hall_shell(b, digits=(0, 5))
wall_pipes(b, 1)
wall_pipes(b, 62)
# reator central: pilar de turbinas empilhadas (y2..38) cercado por anel de segurança
b.fill(28, 1, 28, 35, 1, 35, ARMOR)
for y in range(2, 39):
    b.fill(30, y, 30, 33, y, 33, TURBINE("y"))
b.fill(29, 39, 29, 34, 40, 34, CASING)                                 # topo do reator
b.put(31, 41, 31, VALVE("y")); b.put(32, 41, 32, VALVE("y"))
# anel de corrimão + luzes vermelhas
for x in range(26, 38):
    b.put(x, 2, 26, RAIL_RAW); b.put(x, 2, 37, RAIL_RAW)
for z in range(26, 38):
    b.put(26, 2, z, RAIL_RAW); b.put(37, 2, z, RAIL_RAW)
b.fill(31, 2, 26, 32, 2, 26, AIR)                                      # entrada do anel
for (x, z) in ((26, 26), (37, 26), (26, 37), (37, 37)): b.put(x, 3, z, LRED)
# bancos de caldeiras nas paredes N e S
for x in range(8, 56, 12):
    if not (24 <= x <= 39 or 24 <= x + 1 <= 39):                       # não bloquear a rua
        b.fill(x, 2, 4, x + 1, 9, 5, BOILER("y"))
        b.put(x, 10, 4, JUNC_RAW); b.put(x, 2, 3, VALVE("y"))
    b.fill(x, 2, 58, x + 1, 9, 59, BOILER("y"))
    b.put(x, 10, 58, JUNC_RAW)
# tanques de refrigerante (coolant) em coluna, brilho verde
for (x, z) in ((10, 30), (54, 30), (30, 10), (30, 54)):
    b.fill(x, 2, z, x, 6, z, COOLANT)
    b.put(x, 1, z, LGRN)
# transformadores/cabos grossos ligando reator às paredes
for z in (30, 33):
    for x in range(14, 28): b.put(x, 3, z, PIPE_RAW)
    for x in range(36, 50): b.put(x, 3, z, PIPE_RAW)
    b.put(13, 3, z, JUNC_RAW); b.put(50, 3, z, JUNC_RAW)
# sala de controle do reator (mezanino y14, lado norte) com muitos consoles
b.fill(4, 14, 4, 24, 20, 12, ASH)
b.fill(5, 15, 5, 23, 19, 11, AIR)
b.fill(5, 15, 12, 22, 18, 12, AIR)                                     # janela p/ reator
for x in range(6, 22, 3):
    b.put(x, 15, 5, COGITATOR("south")); b.put(x + 1, 15, 5, CTRLPANEL("south"))
b.put(8, 18, 8, LGRN); b.put(16, 18, 8, LYEL)
b.put(3, 10, 8, PROPAGANDA("west"))
# marcadores
for pos in ((10, 2, 20), (50, 2, 20), (10, 2, 44), (50, 2, 44)): b.put(*pos, MK("marker_worker_spawn"))
b.put(31, 2, 27, MK("marker_defense_point"))
b.put(12, 15, 8, MK("marker_commander_point"))
for pos in ((8, 14, 30), (56, 14, 30)):             b.put(*pos, MK("marker_patrol_point"))
b.put(31, 39, 31, MK("marker_loot_point"))
for pos in ((6, 2, 30), (56, 2, 30)):               b.put(*pos, MK("marker_cover_point"))
b.put(30, 2, 6, MK("marker_civil_spawn"))
dress(b, saints=False)
b.resolve()
emit(b, "generator_hall_01", plans=[(46, "plan"), (10, "plan_terreo")], sx=[(31, "corte_reator_x31")])

# ======================================================================================
print(f"{'módulo':24s} {'dim':>12s} {'não-ar':>8s} {'palette':>7s} {'bytes':>8s}")
for rel, sx, sy, sz, nonair, pal, size in results:
    print(f"{rel:24s} {sx}x{sy}x{sz:>4} {nonair:8d} {pal:7d} {size:8d}")
print(f"prévias em {PREV}/")
