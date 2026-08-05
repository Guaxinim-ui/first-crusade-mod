#!/usr/bin/env python3
"""
Os biomas do First Crusade: clima, cores e carvers. Um arquivo por bioma.

Este script NAO decide onde cada bioma aparece — isso e dos planetas
(tools/generate_planets.py), que declaram a composicao de cada mundo. Nem decide que
vegetacao cada um tem — isso e de tools/generate_worldgen_features.py, dono das etapas 1
(lakes) e 9 (vegetal_decoration) da lista de features.

Um caminho de arquivo, um dono. Ja custou caro aqui: este script escrevia a lista de
features inteira e apagava em silencio a vegetacao de todos os biomas quando rodava depois
do outro. merge_features() existe por causa disso.

Uso:
    python tools/generate_biomes.py
"""

import json
import os

RES = os.path.join("src", "main", "resources")
BIOME_DIR = os.path.join(RES, "data", "firstcrusade", "worldgen", "biome")

MODID = "firstcrusade"

# Minerios e decoracao subterranea vanilla. Sem isto o mundo nao tem o que minerar.
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


# Etapas de feature que pertencem a generate_worldgen_features.py, nao a este script.
# Elas sao PRESERVADAS quando o bioma ja existe em disco.
#
# Isto nao e zelo abstrato: este script escrevia a lista de features inteira, entao rodar
# os dois na ordem errada (ou rodar so este) apagava silenciosamente toda a vegetacao dos
# biomas. O sintoma e cruel — o mundo gera, os biomas aparecem, as cores estao certas, o
# solo esta certo, e simplesmente nao existe uma planta em lugar nenhum. Custou um mundo de
# teste inteiro para achar. Agora os dois scripts podem rodar em qualquer ordem.
FOREIGN_STAGES = {
    1: "lakes",
    9: "vegetal_decoration",
}


def features(extra_local=None):
    """As 11 etapas de feature, na ordem que o jogo espera.

    As etapas de FOREIGN_STAGES saem vazias aqui e sao preenchidas pelo script de
    features; ver merge_features().
    """
    return [
        [],                                   # 0 raw_generation
        [],                                   # 1 lakes  (sem oceano/lago por enquanto)
        extra_local or [],                    # 2 local_modifications
        [],                                   # 3 underground_structures
        [],                                   # 4 surface_structures
        [],                                   # 5 strongholds
        list(ORES),                           # 6 underground_ores
        list(UNDERGROUND_DECORATION),         # 7 underground_decoration
        [],                                   # 8 fluid_springs
        [],                                   # 9 vegetal_decoration  <- outro dono
        ["minecraft:freeze_top_layer"],       # 10 top_layer_modification
    ]


def merge_features(path, fresh):
    """Devolve a lista de features a gravar, preservando as etapas de outro dono."""
    if not os.path.exists(path):
        return fresh

    with open(path, encoding="utf-8") as f:
        existing = json.load(f).get("features") or []

    for stage in FOREIGN_STAGES:
        if stage < len(existing) and existing[stage]:
            fresh[stage] = existing[stage]
    return fresh


# ------------------------------------------------------------------------ carvers
#
# Carvers do vanilla, de propósito.
#
# Houve uma hipótese de que eles fossem a causa do chão esburacado: o `minecraft:cave` sorteia o
# centro em `uniform(above_bottom 8, absolute 180)`, e num mundo min_y=0 com a densidade cruzando
# zero em y=54 sobram ~45 blocos de rocha para o mesmo volume de caverna que o vanilla espalha por
# ~128. Plausível, e errado: limitar o teto das cavernas nao mexeu no numero medido. A causa era o
# filtro das manchas de planta (ver plant_filter em generate_worldgen_features.py).
#
# Ficaram os do vanilla porque cortar o teto das cavernas custaria toda entrada de caverna na
# superficie — perda real de jogabilidade em troca de nenhum ganho medido.
CARVERS = {"air": ["minecraft:cave", "minecraft:cave_extra_underground", "minecraft:canyon"]}

# Mobs hostis vanilla ficam de fora: o mod povoa o mundo com as duas faccoes.
CATEGORIES = ("monster", "creature", "ambient", "axolotls", "underground_water_creature",
              "water_creature", "water_ambient", "misc")


def spawners(fauna=None):
    out = {k: [] for k in CATEGORIES}
    out["creature"] = list(fauna or [])
    return out


def spawn(entity, weight, min_count, max_count):
    """Uma entrada da lista de spawners: quem, com que peso, e em grupos de que tamanho."""
    return {"type": entity, "weight": weight, "minCount": min_count, "maxCount": max_count}


# Quantas manadas um chunk novo nasce com — e este e o teto de populacao da fauna na geracao.
#
# Nao e um numero de gosto. Na geracao de chunk o nivel e um WorldGenRegion, cujas consultas de
# entidade devolvem lista vazia por contrato, entao o teto em Java (FCAnimalEntity.tooCrowded)
# le sempre zero e nao pode ajudar ali. Quem limita a populacao inicial e esta probabilidade
# vezes o tamanho do grupo, e mais nada. Ver FCAnimals para os dois caminhos de spawn.
#
# O default do vanilla e 0,1. A estepe fica nele porque o Grox e a unica criatura dela — numa
# planicie vanilla essa mesma probabilidade e dividida entre vaca, ovelha, porco e galinha.
CREATURE_PROBABILITY_DEFAULT = 0.1


def biome(temperature, downfall, grass, foliage, water, fog, sky, extra_local=None,
          fauna=None, creature_probability=None):
    data = {
        "temperature": temperature,
        "downfall": downfall,
        "has_precipitation": downfall > 0.0,
        "effects": {
            "sky_color": sky,
            "fog_color": fog,
            "water_color": water,
            "water_fog_color": water,
            "grass_color": grass,
            "foliage_color": foliage,
        },
        "spawners": spawners(fauna),
        "spawn_costs": {},
        "carvers": CARVERS,
        "features": features(extra_local),
    }

    if creature_probability is not None:
        data["creature_spawn_probability"] = creature_probability

    return data


# ---------------------------------------------------------------------------- fauna
#
# Fase E. Um bioma so ganha uma especie quando ela faz sentido nele — "todo mundo tem bicho" e
# como se perde a leitura de um planeta. A regra que organiza a tabela inteira: cada especie
# pertence a UM ambiente, e a densidade dela naquele ambiente e maior que em qualquer outro.
# Sem isso nao existe "o pasto" nem "o pantano": existe fauna espalhada.
#
#   grox          gado imperial, pasta em campo aberto  -> estepe (e um pouco no morro)
#   cyber_mastiff cao dos Arbites, anda onde ha lei      -> estepe e morro de Macragge
#   ash_strider   vive do que cresce na crosta           -> cinzas e sal (Armageddon)
#   squig         veio junto com os Orks                 -> cinzas (Armageddon)
#   sump_rat      a agua que ninguem bebe                -> pantano (Catachan)
#   ambull        infestacao, nao populacao              -> morro rochoso, peso minimo
GROX = "firstcrusade:grox"
CYBER_MASTIFF = "firstcrusade:cyber_mastiff"
ASH_STRIDER = "firstcrusade:ash_strider"
SQUIG = "firstcrusade:squig"
SUMP_RAT = "firstcrusade:sump_rat"
AMBULL = "firstcrusade:ambull"


# ---------------------------------------------------------------------- os biomas

BIOMES = {
    # A mata escura padrao: verde apagado, humido, a maior parte do mundo.
    "dark_wilds": biome(0.6, 0.7, 0x4C7A3A, 0x3F6B32, 0x3F5E7A, 0xA8B4C0, 0x7CA8D8),

    # Deserto de cinzas: grama cinza de verdade, ceu lavado, sem chuva.
    "ash_waste": biome(1.4, 0.0, 0x8A8A82, 0x77776E, 0x50565C, 0xB8B4AC, 0x9FAAB4,
                       fauna=[spawn(ASH_STRIDER, 6, 1, 3),
                              spawn(SQUIG, 5, 1, 3)],
                       creature_probability=0.07),

    # Mundo-morte: quente, encharcado, verde agressivo.
    "death_jungle": biome(1.1, 1.0, 0x4E9440, 0x3E8235, 0x2E6B5A, 0x93B48A, 0x6FB0D4),

    # Estepe palida: seca e aberta, o "entre" dos outros tres. E o pasto do mod — o unico bioma
    # com capim alto em campo aberto, entao e onde o Grox vive de verdade.
    "pale_steppe": biome(0.9, 0.2, 0x7E8C55, 0x6E7C48, 0x44607A, 0xC0BFA8, 0x8FB6DC,
                         fauna=[spawn(GROX, 8, 2, 4),
                                spawn(CYBER_MASTIFF, 3, 1, 2)],
                         creature_probability=CREATURE_PROBABILITY_DEFAULT),

    # ------------------------------------------------------------------ fase C
    #
    # Ferrofuste: fria, umida e azulada. A folhagem puxa para o azul-verde de proposito —
    # e o que separa esta mata da dark_wilds de longe, antes de dar para ver a arvore.
    "ironwood_forest": biome(0.35, 0.8, 0x3E6A56, 0x33604E, 0x37536B, 0x8C9AA4, 0x6C90B4),

    # Pantano de sump: agua preta, ar parado. O fog escuro e baixo faz a visibilidade
    # cair sem custar uma unica particula.
    "sump_marsh": biome(0.8, 0.9, 0x5E6B3A, 0x4F5C32, 0x21281F, 0x59604A, 0x6A8090,
                        fauna=[spawn(SUMP_RAT, 10, 2, 4)],
                        creature_probability=0.12),

    # Tundra ossuaria: tem precipitacao e temperatura abaixo de 0.15, que e o par que
    # o freeze_top_layer exige para cobrir de neve e congelar agua parada.
    "ossuary_tundra": biome(0.0, 0.5, 0x8A9686, 0x76826F, 0x3D5A72, 0xC8D2D8, 0x9CBAD4),

    # Ermo de sal: quente, sem chuva nenhuma, tudo lavado. Grama palida quase branca.
    "salt_waste": biome(1.6, 0.0, 0xB4B096, 0xA39F88, 0x6E7C74, 0xD8D4C4, 0xB0BCC0,
                        fauna=[spawn(ASH_STRIDER, 6, 1, 2)],
                        creature_probability=0.03),

    # ------------------------------------------------------- planetas (fase de planetas)
    #
    # Os dois abaixo existem para os planetas e nao usam nenhum bloco novo: sao paleta,
    # regra de superficie e uma lista de features montadas com o kit que ja existe.

    # Macragge: "mais de tres quartos da massa de terra e montanha rochosa quase sem vida,
    # e a populacao vive nas terras baixas". Frio, seco, cinza-azulado, quase esteril.
    #
    # Grox aqui em um terco da densidade da estepe, e em grupos menores: rebanho que subiu o
    # morro, nao rebanho que mora nele. E a diferenca de densidade entre os dois biomas vizinhos
    # que faz a estepe ler como pasto — se houvesse a mesma quantidade nos dois, nao haveria
    # pasto nenhum, so bicho espalhado.
    "rocky_highland": biome(0.25, 0.3, 0x7A8478, 0x69736A, 0x3D5A72, 0xB0BAC4, 0x8AA6C4,
                            fauna=[spawn(GROX, 6, 1, 3),
                                   spawn(CYBER_MASTIFF, 3, 1, 2),
                                   # Peso 1 contra 9 do resto: um Ambull por muitos morros.
                                   # O bicho so vale a pena enquanto for uma historia, e
                                   # historia que se repete no mesmo dia vira tarefa.
                                   spawn(AMBULL, 1, 1, 1)],
                            creature_probability=0.03),

    # Armageddon: as Sreya Rock Mountains sao vulcanicas ativas. Quente, sem chuva, ceu sujo.
    "volcanic_highland": biome(1.8, 0.0, 0x6E6058, 0x5E5048, 0x4A3A32, 0x8A6A56, 0x9E7A62),
}


# ------------------------------------------------------- distribuicao (multi_noise)
#
# Cada entrada e um ponto/faixa no espaco de parametros do gerador. continentalness e
# erosion cobrem toda a faixa em todas as entradas: e isso que garante que nenhum ponto
# do mundo caia fora e vire oceano.

FULL = [-1.0, 1.0]


def entry(biome_id, temperature, humidity, weirdness=FULL, offset=0.0):
    return {
        "biome": "%s:%s" % (MODID, biome_id),
        "parameters": {
            "temperature": temperature,
            "humidity": humidity,
            "continentalness": FULL,
            "erosion": FULL,
            "weirdness": weirdness,
            "depth": FULL,
            "offset": offset,
        },
    }




def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def main():
    kept = 0
    for name, data in BIOMES.items():
        path = os.path.join(BIOME_DIR, name + ".json")
        data["features"] = merge_features(path, list(data["features"]))
        kept += sum(1 for s in FOREIGN_STAGES if data["features"][s])
        write(path, data)

    print("biomas escritos: %d (%s)" % (len(BIOMES), ", ".join(sorted(BIOMES))))
    print("etapas de feature preservadas de outro dono: %d" % kept)


if __name__ == "__main__":
    main()
