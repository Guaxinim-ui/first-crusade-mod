#!/usr/bin/env python3
"""Os treze sitios de fauna: configured_feature, placed_feature e a entrada no bioma.

Um dono por arquivo. Este script e o dono de:

  data/firstcrusade/worldgen/configured_feature/site_*.json
  data/firstcrusade/worldgen/placed_feature/site_*.json
  a etapa 4 (surface_structures) da lista de features de cada bioma

Ele NAO mexe nas etapas 1 (lakes) e 9 (vegetal_decoration), que sao de
generate_worldgen_features.py, nem no resto do bioma, que e de generate_biomes.py. Mexer
sem pedir licenca ja apagou toda a vegetacao do mod uma vez — ver a memoria
[[worldgen-script-ownership]]. Por isso `merge_sites` le o bioma existente e substitui
somente a etapa 4.

A geometria dos sitios esta em Java (FaunaSiteFeature, seis formas). O que esta aqui e a
PARAMETRIZACAO: quem mora, quantos, que raio, que blocos de detrito, e com que raridade
aparece. Trocar "um rancho tem 3 a 8 Grox" para outro numero e editar uma linha aqui e
rodar o script; nao recompila nada.

Uso:
    python tools/generate_fauna_sites.py
"""

import json
import os

RES = os.path.join("src", "main", "resources")
WORLDGEN = os.path.join(RES, "data", "firstcrusade", "worldgen")
CONFIGURED = os.path.join(WORLDGEN, "configured_feature")
PLACED = os.path.join(WORLDGEN, "placed_feature")
BIOME_DIR = os.path.join(WORLDGEN, "biome")

MODID = "firstcrusade"

# A etapa da lista de features do bioma que este script possui.
SURFACE_STRUCTURES_STAGE = 4


# ------------------------------------------------------------------ blocos de detrito
#
# Os props sao a PISTA AMBIENTAL do briefing. A regra que os escolhe: cada sitio usa o
# detrito que a criatura dele produziria. Ossos onde algo come; sucata onde ha Orks;
# vegetacao morta onde ha veneno. Trocar isso por "ossos em tudo" apagaria a diferenca
# entre um ninho de Devil e um curral Ork, que e justamente o que o jogador le de longe.

BONES = f"{MODID}:bone_fragments"
TWIGS = f"{MODID}:scattered_twigs"
PEBBLES = f"{MODID}:rubble_pebbles"
LEAVES = f"{MODID}:fallen_leaves"
ASH = f"{MODID}:ash_layer"

DEAD_BUSH = "minecraft:dead_bush"
COBWEB = "minecraft:cobweb"


def state(name):
    return {"Name": name}


# ---------------------------------------------------------------------- os sitios
#
# (nome, forma, mob, min, max, raio, props, prop_tries, floor, frame, raridade, biomas)
#
# `prop_tries` e TENTATIVA, nao resultado: cada uma sorteia um ponto no sitio e so vale se
# cair em ar com chao solido embaixo. Medido em servidor, ~1 em 6 vinga (as outras caem na
# cerca, no poco da toca ou em terreno inclinado). Por isso os numeros parecem altos: 30
# tentativas dao os ~5 ossos que fazem a pista ambiental ser legivel.
#
# `raridade` e o denominador do rarity_filter: 1 em N chunks tenta gerar. Os numeros seguem
# a tabela de raridade do briefing, e a diferenca entre eles E o que faz cada encontro valer
# o que devia valer:
#
#   comum        1 em 24-40    (Grox Ranch, Squig Pen)
#   incomum      1 em 60-90    (lobo, helamite, knarloc, canil)
#   raro         1 em 140-220  (Ambull, Cudbear, Duneskuttler, Duskhorn, Constrictor)
#   apex         1 em 500-900  (Barking Toad, Catachan Devil)

SITES = [
    # ---------------------------------------------------------------- necron
    #
    # A ruina que abre o mundo-tumba. Fica no salt_waste, que existe em Armageddon (30%), Forge
    # World (25%) e no proprio mundo-tumba (60%) — as tumbas estao sob o sal em todo o lado, e o
    # mundo-tumba e so onde sao mais densas. Raridade alta de proposito: e uma chave, nao mobilia.
    dict(
        name="site_necron_ruin",
        shape="ruin",
        mob=f"{MODID}:necron_scarab",
        min_count=2, max_count=4,
        radius=6,
        props=[f"{MODID}:salt_crust", "minecraft:polished_deepslate"],
        prop_tries=20,
        floor="minecraft:polished_deepslate",
        frame="minecraft:deepslate_tiles",
        centre=f"{MODID}:necron_reliquary",
        rarity=220,
        biomes=["salt_waste"],
    ),

    # A tumba a serio: boca a superficie, poco, e a camara do Senhor no fundo.
    #
    # Separada da ruina de proposito. A ruina e a CHAVE (relicario, raridade 220, espalhada por tres
    # planetas); esta e o LUGAR, e so faz sentido no proprio mundo-tumba. Por isso o bioma e o mesmo
    # salt_waste — que e 60% de Sekhet — mas a raridade e muito mais alta: uma tumba dessas por
    # planeta e o que se quer, nao uma por vale.
    #
    # `champion` poe o Senhor exactamente no trono e `mob` poe a guarda de Guerreiros a volta dele.
    # Os escaravelhos ficam de fora aqui: eles sao o que o despertar manda ao jogador la fora, e
    # enche-los na camara tiraria o contraste entre "a tumba mandou-te alguma coisa" e "chegaste ao
    # fundo dela".
    dict(
        name="site_necron_tomb",
        shape="tomb",
        mob=f"{MODID}:necron_warrior",
        min_count=4, max_count=6,
        radius=9,
        props=["firstcrusade:necron_stone", "firstcrusade:necron_glyph"],
        prop_tries=14,
        floor=f"{MODID}:necron_stone",
        frame=f"{MODID}:necron_stone",
        centre=f"{MODID}:necron_glyph",
        glow=f"{MODID}:necron_conduit",
        champion=f"{MODID}:necron_overlord",
        rarity=900,
        biomes=["salt_waste"],
    ),

    # ---------------------------------------------------------------- imperial
    dict(
        name="site_grox_ranch",
        shape="pen",
        mob=f"{MODID}:grox",
        min_count=3, max_count=8,
        radius=7,
        props=[TWIGS, PEBBLES],
        prop_tries=30,
        floor="minecraft:coarse_dirt",
        frame="minecraft:oak_fence",
        rarity=30,
        biomes=["pale_steppe", "rocky_highland"],
    ),
    dict(
        name="site_imperial_kennel",
        shape="pen",
        mob=f"{MODID}:cyber_mastiff",
        min_count=1, max_count=3,
        radius=5,
        props=[BONES, PEBBLES],
        prop_tries=24,
        floor="minecraft:gravel",
        # Cerca de ferro: e um canil dos Arbites, nao um chiqueiro. O material e a unica coisa
        # que separa esta planta baixa da do rancho.
        frame="minecraft:iron_bars",
        rarity=80,
        biomes=["pale_steppe", "rocky_highland"],
    ),

    # ---------------------------------------------------------------- ork
    dict(
        name="site_squig_pen",
        shape="pen",
        mob=f"{MODID}:squig",
        min_count=3, max_count=8,
        radius=6,
        props=[BONES, TWIGS],
        prop_tries=42,
        floor="minecraft:coarse_dirt",
        # Sucata: cerca torta de metal batido. O curral Ork tem de parecer improvisado.
        frame="minecraft:iron_bars",
        rarity=36,
        biomes=["ash_waste"],
    ),

    # ---------------------------------------------------------------- ash wastes
    dict(
        name="site_ambull_burrow",
        shape="burrow",
        mob=f"{MODID}:ambull",
        min_count=1, max_count=2,
        radius=5,
        # Ossos e capacetes: os restos da equipe de mineracao que encontrou o bicho. Esta e a
        # pista ambiental mais importante do mod — o jogador le a historia antes do combate.
        props=[BONES, PEBBLES],
        prop_tries=48,
        floor="minecraft:coarse_dirt",
        frame=None,
        rarity=170,
        biomes=["rocky_highland", "ash_waste"],
    ),
    dict(
        name="site_duneskuttler_nest",
        shape="nest",
        mob=f"{MODID}:arthromite_duneskuttler",
        min_count=1, max_count=3,
        radius=5,
        props=[BONES, ASH, PEBBLES],
        prop_tries=36,
        floor="minecraft:sand",
        frame=None,
        rarity=150,
        biomes=["ash_waste", "salt_waste"],
    ),
    dict(
        name="site_helamite_post",
        shape="camp",
        mob=f"{MODID}:dustback_helamite",
        min_count=2, max_count=5,
        radius=6,
        props=[ASH, TWIGS],
        prop_tries=24,
        floor="minecraft:coarse_dirt",
        frame="minecraft:brown_wool",
        rarity=90,
        biomes=["ash_waste", "salt_waste"],
    ),

    # ---------------------------------------------------------------- death world
    dict(
        name="site_barking_toad_clearing",
        shape="clearing",
        mob=f"{MODID}:catachan_barking_toad",
        min_count=1, max_count=1,
        radius=6,
        # Vegetacao morta e teia: o que a toxina deixa para tras. Nada de ossos aqui — o sapo
        # nao come ninguem, ele so mata.
        props=[DEAD_BUSH, COBWEB, LEAVES],
        prop_tries=42,
        floor="minecraft:mud",
        frame=None,
        rarity=620,
        biomes=["death_jungle", "sump_marsh"],
    ),
    dict(
        name="site_catachan_devil_nest",
        shape="nest",
        mob=f"{MODID}:catachan_devil",
        min_count=1, max_count=1,
        radius=8,
        props=[BONES, COBWEB, DEAD_BUSH, TWIGS],
        prop_tries=78,
        floor="minecraft:coarse_dirt",
        frame=None,
        # O sitio mais raro do mod, e de longe. Um Catachan Devil por muitas centenas de
        # chunks — o briefing pede isso em maiusculas e ele esta certo: uma segunda ocorrencia
        # no mesmo dia transforma a criatura de historia em tarefa.
        rarity=900,
        biomes=["death_jungle"],
    ),
    dict(
        name="site_constrictor_nest",
        shape="nest",
        mob=f"{MODID}:greater_malkavan_constrictor",
        min_count=1, max_count=1,
        radius=6,
        props=[BONES, LEAVES, TWIGS],
        prop_tries=48,
        floor="minecraft:mud",
        frame=None,
        rarity=200,
        biomes=["death_jungle", "sump_marsh"],
    ),
    dict(
        name="site_cudbear_den",
        shape="den",
        mob=f"{MODID}:cthellean_cudbear",
        min_count=1, max_count=2,
        radius=5,
        props=[BONES, TWIGS, LEAVES],
        prop_tries=36,
        floor=None,
        frame="minecraft:oak_log",
        rarity=160,
        biomes=["dark_wilds", "ironwood_forest", "death_jungle"],
    ),

    # ---------------------------------------------------------------- frio
    dict(
        name="site_fenrisian_wolf_den",
        shape="den",
        mob=f"{MODID}:fenrisian_wolf",
        min_count=2, max_count=5,
        radius=5,
        # Cadaveres de presa: e o que anuncia a matilha antes do uivo.
        props=[BONES, PEBBLES],
        prop_tries=42,
        floor=None,
        frame="minecraft:spruce_log",
        rarity=70,
        biomes=["ossuary_tundra", "ironwood_forest"],
    ),

    # ---------------------------------------------------------------- kroot / planicie
    dict(
        name="site_knarloc_pen",
        shape="pen",
        mob=f"{MODID}:knarloc",
        min_count=1, max_count=3,
        radius=6,
        props=[BONES, TWIGS],
        prop_tries=30,
        floor="minecraft:coarse_dirt",
        frame="minecraft:jungle_fence",
        rarity=85,
        biomes=["dark_wilds", "pale_steppe"],
    ),
    dict(
        name="site_duskhorn_herd",
        shape="clearing",
        mob=f"{MODID}:duskhorn",
        min_count=2, max_count=5,
        radius=9,
        # Vegetacao pisoteada e trilha larga: o rastro da manada, que o jogador cruza antes de
        # ver a manada.
        props=[TWIGS, PEBBLES, DEAD_BUSH],
        prop_tries=54,
        floor="minecraft:coarse_dirt",
        frame=None,
        rarity=140,
        biomes=["pale_steppe", "dark_wilds"],
    ),
]


# ------------------------------------------------------------------------ escrita


def configured(site):
    config = {
        "shape": site["shape"],
        "min_count": site["min_count"],
        "max_count": site["max_count"],
        "radius": site["radius"],
        "prop_tries": site["prop_tries"],
        "props": [state(block) for block in site["props"]],
    }

    if site.get("mob"):
        config["mob"] = site["mob"]
    if site.get("floor"):
        config["floor"] = state(site["floor"])
    if site.get("frame"):
        config["frame"] = state(site["frame"])
    if site.get("centre"):
        config["centre"] = state(site["centre"])
    if site.get("champion"):
        config["champion"] = site["champion"]
    if site.get("glow"):
        config["glow"] = state(site["glow"])

    return {"type": f"{MODID}:fauna_site", "config": config}


def placed(site):
    return {
        "feature": f"{MODID}:{site['name']}",
        "placement": [
            {"type": "minecraft:rarity_filter", "chance": site["rarity"]},
            {"type": "minecraft:in_square"},
            {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"},
            {"type": "minecraft:biome"},
        ],
    }


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(data, handle, indent=2, ensure_ascii=False)
        handle.write("\n")


def merge_sites():
    """Reescreve SO a etapa 4 de cada bioma, preservando todo o resto do arquivo."""
    by_biome = {}
    for site in SITES:
        for biome in site["biomes"]:
            by_biome.setdefault(biome, []).append(f"{MODID}:{site['name']}")

    touched = 0
    for biome, features in by_biome.items():
        path = os.path.join(BIOME_DIR, biome + ".json")
        if not os.path.exists(path):
            print(f"  ! bioma inexistente, pulado: {biome}")
            continue

        with open(path, encoding="utf-8") as handle:
            data = json.load(handle)

        stages = data.get("features") or []
        while len(stages) <= SURFACE_STRUCTURES_STAGE:
            stages.append([])

        stages[SURFACE_STRUCTURES_STAGE] = sorted(features)
        data["features"] = stages
        write(path, data)
        touched += 1

    return touched, by_biome


def main():
    for site in SITES:
        write(os.path.join(CONFIGURED, site["name"] + ".json"), configured(site))
        write(os.path.join(PLACED, site["name"] + ".json"), placed(site))

    touched, by_biome = merge_sites()

    print(f"sitios escritos: {len(SITES)}")
    print(f"biomas atualizados (etapa {SURFACE_STRUCTURES_STAGE}): {touched}")
    for biome in sorted(by_biome):
        names = [n.split(":", 1)[1] for n in sorted(by_biome[biome])]
        print(f"  {biome:20} {', '.join(names)}")


if __name__ == "__main__":
    main()
