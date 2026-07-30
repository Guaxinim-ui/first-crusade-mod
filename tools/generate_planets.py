#!/usr/bin/env python3
"""
Os planetas do First Crusade: uma dimensao por planeta, com composicao de bioma FIXA.

Mudanca de arquitetura (2026-07-30)
-----------------------------------
O mod nao sobrescreve mais o overworld. O jogador comeca num Minecraft normal; a Cruzada
comeca quando ele usa a nave. Isso separa duas coisas que nunca deviam ter sido a mesma:

  * o overworld e vanilla, com o gerador, os oceanos, as estruturas e o Nether do jogo base;
  * os planetas sao inteiramente do mod, e podem ter a geracao que quiserem sem colidir com
    nada do vanilla nem alterar um save existente.

Foi o que resolveu, de uma vez, o oceano flutuante, o naufragio de ponta-cabeca e o
`sea_level` descasado — todos sintomas de um overworld sequestrado.

Composicao fixa em vez de ruido global
--------------------------------------
No overworld antigo, oito biomas dividiam um unico espaco de clima e o resultado era um
mosaico: o jogador via biomas trocando sem parar e sem logica. Aqui cada planeta declara a
sua propria composicao ("Macragge e 60% montanha rochosa") e so os biomas dessa lista
existem naquele mundo.

A composicao vira uma grade 4x4 de clima (temperatura x umidade). Cada bioma recebe um
numero de celulas proporcional ao seu peso, e as celulas de um mesmo bioma sao SEMPRE
contiguas na grade — e isso que faz uma regiao sair grande e coerente em vez de picotada.

As fronteiras da grade nao sao equidistantes: o ruido de clima do jogo se concentra perto
de zero, entao as faixas centrais sao estreitas e as das pontas largas. Os valores vem da
calibracao medida no overworld antigo (ver docs/FLORA_PHASE2_RUNTIME.md secao 15).

Uso:
    python tools/generate_planets.py
"""

import itertools
import json
import os

RES = os.path.join("src", "main", "resources")
D = os.path.join(RES, "data", "firstcrusade")
BIOME_DIR = os.path.join(D, "worldgen", "biome")
DIM_DIR = os.path.join(D, "dimension")
DIMTYPE_DIR = os.path.join(D, "dimension_type")
NOISE_DIR = os.path.join(D, "worldgen", "noise_settings")

MOD = "firstcrusade"


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


# ============================================================================ os planetas
#
# Cada composicao e (bioma, peso). Os pesos sao percentuais e somam 100.
#
# As escolhas seguem a lore, nao o gosto:
#
#   MACRAGGE   mais de tres quartos da massa de terra sao montanha rochosa quase sem vida; a
#              populacao vive nas terras baixas. (Lexicanum: Macragge)
#   ARMAGEDDON mundo-colmeia cercado de ermos poluidos que matam um homem em um dia; as Sreya
#              Rock Mountains sao vulcanicas ativas. (Lexicanum: Armageddon geography)
#   CATACHAN   mundo-morte quase inteiramente coberto de selva densa que rebrota sozinha.
#   VALHALLA   bola de gelo apos um impacto de cometa; a temperatura nunca sobe de zero.

PLANETS = {
    "macragge": {
        "name": "Macragge",
        "composition": [
            ("rocky_highland", 60),
            ("pale_steppe", 25),
            ("ironwood_forest", 15),
        ],
        "sea_level": 46,
        # Terreno alto e acidentado: e um mundo de montanha.
        "terrain": {"zero_at": 58, "span": 24, "continents": 0.55, "erosion": 0.30},
        "sky": 0x7C96B4,
        "fog": 0xA8B0BC,
    },
    "armageddon": {
        "name": "Armageddon",
        "composition": [
            ("ash_waste", 55),
            ("salt_waste", 30),
            ("volcanic_highland", 15),
        ],
        "sea_level": 0,          # sem agua de superficie: o ermo e o ponto
        "terrain": {"zero_at": 52, "span": 20, "continents": 0.40, "erosion": 0.20},
        "sky": 0x9A8878,
        "fog": 0xB4A490,
    },
    "catachan": {
        "name": "Catachan",
        "composition": [
            ("death_jungle", 75),
            ("sump_marsh", 25),
        ],
        "sea_level": 48,
        # Relevo suave e baixo: a selva e a parede, nao a montanha.
        "terrain": {"zero_at": 54, "span": 16, "continents": 0.30, "erosion": 0.15},
        "sky": 0x6FA88C,
        "fog": 0x7E9478,
    },
    "valhalla": {
        "name": "Valhalla",
        "composition": [
            ("ossuary_tundra", 70),
            ("ironwood_forest", 30),
        ],
        "sea_level": 48,
        "terrain": {"zero_at": 56, "span": 22, "continents": 0.45, "erosion": 0.25},
        "sky": 0x9EC0DC,
        "fog": 0xCEDCE4,
    },
}


# ==================================================================== grade de clima
#
# 4x4 = 16 celulas. As fronteiras sao estreitas no centro porque e la que o ruido passa a
# maior parte do tempo — fronteiras equidistantes dariam duas celulas gigantes nas pontas e
# catorze quase vazias.

T_BANDS = [[-1.0, -0.12], [-0.12, 0.0], [0.0, 0.12], [0.12, 1.0]]
H_BANDS = [[-1.0, -0.10], [-0.10, 0.0], [0.0, 0.10], [0.10, 1.0]]

# A AREA REAL de cada faixa, medida — nao a largura em valor.
#
# As 16 celulas nao tem a mesma area, e nem perto. Distribuir celulas por CONTAGEM deu
# Catachan com 56% de pantano onde a composicao pedia 25%. Os numeros abaixo saem de dois
# planetas descartaveis (--calibrate) que pintam um bioma por faixa, gerados numa amostra de
# 192.000 blocos de lado.
#
# A largura da amostra importa mais do que parece: a primeira tentativa cobriu +-2.400 blocos e
# deu numeros completamente diferentes, porque o ruido de clima tem onda de milhares de blocos
# e a amostra inteira caiu dentro de uma unica regiao fria. Medir clima num quadrado pequeno e
# medir o proprio quadrado.
T_AREA = [41.0, 8.0, 17.6, 33.3]
H_AREA = [30.5, 15.9, 17.4, 36.2]

FULL = [-1.0, 1.0]


def cell_order():
    """A ordem em que as celulas sao distribuidas: serpentina, nao varredura.

    Varrer linha a linha faz a ultima celula de uma linha e a primeira da seguinte ficarem
    em cantos opostos da grade, o que quebra a contiguidade justamente na troca de bioma.
    A serpentina mantem celulas consecutivas sempre vizinhas.
    """
    order = []
    for ti in range(len(T_BANDS)):
        cols = range(len(H_BANDS)) if ti % 2 == 0 else reversed(range(len(H_BANDS)))
        for hi in cols:
            order.append((ti, hi))
    return order


def allocate(composition):
    """Distribui as 16 celulas entre os biomas para bater a AREA pedida, nao a contagem.

    As celulas de um bioma sao sempre um trecho CONTIGUO da serpentina — e isso que faz a
    regiao sair grande e coerente em vez de picotada. Com essa restricao, achar a melhor
    divisao e escolher os pontos de corte, e com 16 celulas e no maximo 4 biomas da para
    testar todas as combinacoes e ficar com a de menor erro absoluto. Nada de heuristica: o
    resultado e o otimo exato dentro da restricao de contiguidade.
    """
    order = cell_order()
    areas = [T_AREA[ti] * H_AREA[hi] for ti, hi in order]
    total_area = sum(areas)

    weights = [w for _, w in composition]
    targets = [w * total_area / sum(weights) for w in weights]
    n = len(composition)

    best = None
    for cuts in itertools.combinations(range(1, len(order)), n - 1):
        bounds = (0,) + cuts + (len(order),)
        got = [sum(areas[bounds[i]:bounds[i + 1]]) for i in range(n)]
        if any(g <= 0.0 for g in got):
            continue                      # um bioma declarado tem de aparecer
        error = sum(abs(got[i] - targets[i]) for i in range(n))
        if best is None or error < best[0]:
            best = (error, bounds, got)

    _, bounds, got = best

    assignment = {}
    for i, (biome_id, _) in enumerate(composition):
        for index in range(bounds[i], bounds[i + 1]):
            assignment[order[index]] = biome_id

    shares = [100.0 * g / total_area for g in got]
    counts = [bounds[i + 1] - bounds[i] for i in range(n)]
    return assignment, counts, shares


def biome_source(composition):
    assignment, counts, shares = allocate(composition)
    entries = []
    for (ti, hi), biome_id in sorted(assignment.items()):
        entries.append({
            "biome": "%s:%s" % (MOD, biome_id),
            "parameters": {
                "temperature": T_BANDS[ti],
                "humidity": H_BANDS[hi],
                "continentalness": FULL,
                "erosion": FULL,
                "weirdness": FULL,
                "depth": FULL,
                "offset": 0.0,
            },
        })
    return {"type": "minecraft:multi_noise", "biomes": entries}, counts, shares


# ==================================================================== noise settings
#
# A forma do terreno e a mesma familia usada no overworld antigo — um gradiente vertical de
# densidade somado a continents e erosion — mas cada planeta escolhe onde a densidade cruza
# zero (a altura media da terra) e com que forca o ruido a desloca. E o que faz Macragge ser
# montanhoso e Catachan ser baixo e plano, sem trocar uma linha de codigo.

ORES = [
    "minecraft:ore_dirt", "minecraft:ore_gravel", "minecraft:ore_granite_upper",
    "minecraft:ore_granite_lower", "minecraft:ore_diorite_upper", "minecraft:ore_diorite_lower",
    "minecraft:ore_andesite_upper", "minecraft:ore_andesite_lower", "minecraft:ore_tuff",
    "minecraft:ore_coal_upper", "minecraft:ore_coal_lower", "minecraft:ore_iron_upper",
    "minecraft:ore_iron_middle", "minecraft:ore_iron_small", "minecraft:ore_gold",
    "minecraft:ore_gold_lower", "minecraft:ore_redstone", "minecraft:ore_redstone_lower",
    "minecraft:ore_diamond", "minecraft:ore_diamond_large", "minecraft:ore_diamond_buried",
    "minecraft:ore_lapis", "minecraft:ore_lapis_buried", "minecraft:ore_copper",
]

UNDERGROUND_DECORATION = [
    "minecraft:monster_room", "minecraft:monster_room_deep",
    "minecraft:glow_lichen", "minecraft:amethyst_geode",
]

CARVERS = {"air": ["minecraft:cave", "minecraft:cave_extra_underground", "minecraft:canyon"]}

# Mobs hostis vanilla ficam de fora: o mod povoa os planetas com as duas faccoes.
SPAWNERS = {k: [] for k in
            ("monster", "creature", "ambient", "axolotls", "underground_water_creature",
             "water_creature", "water_ambient", "misc")}


# xz_scale 0.75 contra os 0.25 do vanilla: a onda do clima fica tres vezes mais curta.
#
# Nao e gosto, e aritmetica. Um planeta tem borda de 5.000 blocos, entao o jogador so ve um
# quadrado de 10.000 de lado. Com a onda do vanilla, esse quadrado cabe DENTRO de uma unica
# regiao de clima — e a composicao que o planeta promete ("60% montanha") vira sorteio de seed:
# uma semente da um mundo todo frio, a seguinte um mundo todo quente. Encurtando a onda, o
# quadrado passa a conter dezenas de regioes e a composicao converge para o que foi declarado.
def shifted(noise):
    return {"type": "minecraft:shifted_noise", "noise": noise,
            "shift_x": "minecraft:shift_x", "shift_y": 0.0, "shift_z": "minecraft:shift_z",
            "xz_scale": 0.75, "y_scale": 0.0}


def density(terrain):
    half = terrain["span"] / 2.0
    return {
        "type": "minecraft:add",
        "argument1": {
            "type": "minecraft:add",
            "argument1": {"type": "minecraft:y_clamped_gradient",
                          "from_y": int(terrain["zero_at"] - half),
                          "to_y": int(terrain["zero_at"] + half),
                          "from_value": 1.0, "to_value": -1.0},
            "argument2": {"type": "minecraft:mul", "argument1": terrain["continents"],
                          "argument2": "minecraft:overworld/continents"},
        },
        "argument2": {"type": "minecraft:mul", "argument1": terrain["erosion"],
                      "argument2": "minecraft:overworld/erosion"},
    }


def surface_rule(planet_id):
    """Bedrock, as regras por bioma do planeta, e a regra geral no fim."""
    steps = [{
        "type": "minecraft:condition",
        "if_true": {"type": "minecraft:vertical_gradient", "random_name": "minecraft:bedrock_floor",
                    "true_at_and_below": {"above_bottom": 0},
                    "false_at_and_above": {"above_bottom": 5}},
        "then_run": {"type": "minecraft:block", "result_state": {"Name": "minecraft:bedrock"}},
    }]

    for biome_id, (top, under) in sorted(BIOME_SURFACES.items()):
        if biome_id not in [b for b, _ in PLANETS[planet_id]["composition"]]:
            continue
        steps.append({
            "type": "minecraft:condition",
            "if_true": {"type": "minecraft:biome", "biome_is": ["%s:%s" % (MOD, biome_id)]},
            "then_run": {
                "type": "minecraft:sequence",
                "sequence": [
                    {"type": "minecraft:condition", "if_true": floor_at(False),
                     "then_run": {"type": "minecraft:block", "result_state": {"Name": top}}},
                    {"type": "minecraft:condition", "if_true": floor_at(True),
                     "then_run": {"type": "minecraft:block", "result_state": {"Name": under}}},
                ],
            },
        })

    steps += [
        {"type": "minecraft:condition", "if_true": floor_at(False),
         "then_run": {"type": "minecraft:sequence", "sequence": [
             {"type": "minecraft:condition",
              "if_true": {"type": "minecraft:water", "offset": -1,
                          "surface_depth_multiplier": 0, "add_stone_depth": False},
              "then_run": {"type": "minecraft:block",
                           "result_state": {"Name": "minecraft:grass_block",
                                            "Properties": {"snowy": "false"}}}},
             {"type": "minecraft:block", "result_state": {"Name": "minecraft:sand"}},
         ]}},
        {"type": "minecraft:condition", "if_true": floor_at(True),
         "then_run": {"type": "minecraft:block", "result_state": {"Name": "minecraft:dirt"}}},
    ]
    return {"type": "minecraft:sequence", "sequence": steps}


def floor_at(add_surface_depth):
    return {"type": "minecraft:stone_depth", "offset": 0, "surface_type": "floor",
            "add_surface_depth": add_surface_depth, "secondary_depth_range": 0}


# bioma -> (bloco de topo, bloco logo abaixo). Biomas fora daqui usam a regra geral.
BIOME_SURFACES = {
    "ironwood_forest": ("minecraft:podzol", "minecraft:dirt"),
    "sump_marsh": ("firstcrusade:sump_mud", "firstcrusade:sump_mud"),
    "salt_waste": ("firstcrusade:salt_crust", "minecraft:sand"),
    "rocky_highland": ("minecraft:gravel", "minecraft:stone"),
    "volcanic_highland": ("minecraft:blackstone", "minecraft:basalt"),
}


def noise_settings(planet_id, planet):
    return {
        "sea_level": planet["sea_level"],
        "disable_mob_generation": False,
        "aquifers_enabled": False,
        "ore_veins_enabled": False,
        "legacy_random_source": False,
        "default_block": {"Name": "minecraft:stone"},
        "default_fluid": {"Name": "minecraft:water", "Properties": {"level": "0"}},
        "noise": {"min_y": 0, "height": 256, "size_horizontal": 1, "size_vertical": 2},
        "noise_router": {
            "barrier": 0.0, "fluid_level_floodedness": 0.0, "fluid_level_spread": 0.0,
            "lava": 0.0, "vein_toggle": 0.0, "vein_ridged": 0.0, "vein_gap": 0.0,
            "temperature": shifted("minecraft:temperature"),
            "vegetation": shifted("minecraft:vegetation"),
            "continents": "minecraft:overworld/continents",
            "erosion": "minecraft:overworld/erosion",
            "depth": "minecraft:overworld/depth",
            "ridges": "minecraft:overworld/ridges",
            "initial_density_without_jaggedness": density(planet["terrain"]),
            "final_density": density(planet["terrain"]),
        },
        "spawn_target": [{
            "temperature": FULL, "humidity": FULL, "continentalness": [-0.11, 1.0],
            "erosion": FULL, "weirdness": FULL, "depth": 0.0, "offset": 0.0,
        }],
        "surface_rule": surface_rule(planet_id),
    }


def dimension_type():
    return {
        "ultrawarm": False, "natural": True, "piglin_safe": False, "respawn_anchor_works": False,
        "bed_works": True, "has_raids": False, "has_skylight": True, "has_ceiling": False,
        "coordinate_scale": 1.0, "ambient_light": 0.0, "logical_height": 256,
        "effects": "minecraft:overworld", "infiniburn": "#minecraft:infiniburn_overworld",
        "min_y": 0, "height": 256, "monster_spawn_block_light_limit": 0,
        # Forma inteira simples. A forma de IntProvider tambem e aceita, mas exige o
        # aninhamento {"type":"minecraft:uniform","value":{...}} — escrever min/max soltos no
        # topo faz o dimension_type inteiro falhar a carga, e o erro do jogo nao diz qual campo.
        "monster_spawn_light_level": 7,
    }


def main():
    total_cells = 0
    for planet_id, planet in PLANETS.items():
        source, counts, shares = biome_source(planet["composition"])
        total_cells += sum(counts)

        write(os.path.join(DIMTYPE_DIR, planet_id + ".json"), dimension_type())
        write(os.path.join(NOISE_DIR, planet_id + ".json"), noise_settings(planet_id, planet))
        write(os.path.join(DIM_DIR, planet_id + ".json"), {
            "type": "%s:%s" % (MOD, planet_id),
            "generator": {
                "type": "minecraft:noise",
                "settings": "%s:%s" % (MOD, planet_id),
                "biome_source": source,
            },
        })

        share = ", ".join("%s pedido %d%% -> previsto %.0f%% (%d cel)" % (b, w, sh, c)
                          for (b, w), c, sh in zip(planet["composition"], counts, shares))
        print("%-12s %s" % (planet["name"], share))

    print("\n%d planetas, %d celulas de clima escritas" % (len(PLANETS), total_cells))




# ==================================================================== calibracao
#
# As 16 celulas da grade NAO tem a mesma area. O ruido de clima se concentra perto de zero, e
# uma faixa larga em valor pode ser estreita em area (ou o contrario). Distribuir celulas por
# CONTAGEM, como a primeira versao fazia, deu Catachan com 56% de pantano onde a composicao
# pedia 25%.
#
# Estes dois planetas descartaveis medem isso: um pinta um bioma por FAIXA DE TEMPERATURA, o
# outro um bioma por FAIXA DE UMIDADE. Gerando os dois e contando chunks sai a area real de
# cada faixa, e dai a area de cada celula (as duas dimensoes de ruido sao independentes, entao
# a area da celula e o produto das marginais).
#
#   python tools/generate_planets.py --calibrate    escreve os dois planetas de medicao
#
# Depois de medir, os numeros entram em T_AREA/H_AREA e a alocacao passa a ser por area.

PROBE_BIOMES = ["ossuary_tundra", "ironwood_forest", "dark_wilds", "pale_steppe"]


def probe_planet(axis):
    """Um bioma por faixa do eixo pedido; o outro eixo fica inteiro."""
    entries = []
    bands = T_BANDS if axis == "temperature" else H_BANDS
    for i, band in enumerate(bands):
        entries.append({
            "biome": "%s:%s" % (MOD, PROBE_BIOMES[i]),
            "parameters": {
                "temperature": band if axis == "temperature" else FULL,
                "humidity": band if axis == "humidity" else FULL,
                "continentalness": FULL, "erosion": FULL, "weirdness": FULL,
                "depth": FULL, "offset": 0.0,
            },
        })
    return {"type": "minecraft:multi_noise", "biomes": entries}


def write_probes():
    template = PLANETS["macragge"]
    for axis in ("temperature", "humidity"):
        pid = "probe_" + axis
        write(os.path.join(DIMTYPE_DIR, pid + ".json"), dimension_type())
        settings = noise_settings("macragge", template)
        write(os.path.join(NOISE_DIR, pid + ".json"), settings)
        write(os.path.join(DIM_DIR, pid + ".json"), {
            "type": "%s:%s" % (MOD, pid),
            "generator": {"type": "minecraft:noise",
                          "settings": "%s:%s" % (MOD, pid),
                          "biome_source": probe_planet(axis)},
        })
        print("planeta de medicao escrito: %s (%s)" % (pid, axis))


def remove_probes():
    for axis in ("temperature", "humidity"):
        pid = "probe_" + axis
        for d in (DIMTYPE_DIR, NOISE_DIR, DIM_DIR):
            path = os.path.join(d, pid + ".json")
            if os.path.exists(path):
                os.remove(path)
    print("planetas de medicao removidos")


if __name__ == "__main__":
    import sys
    if "--calibrate" in sys.argv:
        write_probes()
    elif "--clean-probes" in sys.argv:
        remove_probes()
    else:
        main()
