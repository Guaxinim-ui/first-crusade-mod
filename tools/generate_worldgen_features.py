#!/usr/bin/env python3
"""
Gera a vegetacao natural em WORLDGEN: configured features, placed features, e as liga
na etapa vegetal_decoration de cada bioma.

Por que existe
--------------
Ate agora toda a vegetacao vinha do decorador por chunk, em runtime. Isso tem um limite
estrutural que nenhum ajuste de orcamento resolve: o chunk nasce, o jogador chega, e a
fila ainda nao passou por ali. Correndo, voando ou de veiculo o jogador ganha da fila
sempre — e ve terreno pelado.

A partir daqui a ambientacao e dividida em duas camadas:

  Camada 1 (este arquivo)  vegetacao NATURAL, criada junto com o chunk. Quando o chunk
                           nasce ja tem capim, arvores, pedras e troncos. Nao existe
                           janela de tempo em que a mata "ainda nao chegou".

  Camada 2 (runtime)       transformacao TERRITORIAL: o que muda quando uma faccao toma
                           a regiao, quando um campo queima, quando o Caos corrompe. Isso
                           nao pode ser worldgen porque muda depois que o chunk existe.

O decorador em runtime deixa de plantar as paletas naturais (ver FloraPalette.isNatural)
justamente para as duas camadas nao se somarem.

Uso:
    python tools/generate_worldgen_features.py
"""

import json
import os

RES = os.path.join("src", "main", "resources")
CF = os.path.join(RES, "data", "firstcrusade", "worldgen", "configured_feature")
PF = os.path.join(RES, "data", "firstcrusade", "worldgen", "placed_feature")
BIOME = os.path.join(RES, "data", "firstcrusade", "worldgen", "biome")

MOD = "firstcrusade"


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def state(name, props=None):
    s = {"Name": name}
    if props:
        s["Properties"] = props
    return s


def simple(name, props=None):
    return {"type": "minecraft:simple_state_provider", "state": state(name, props)}


def leaves_state(name):
    # Folhas colocadas por worldgen entram com distance 7/persistent false; o proprio
    # feature de arvore recalcula a distancia ao fechar a copa.
    return simple(name, {"distance": "7", "persistent": "false", "waterlogged": "false"})


def dead_state(name):
    """Provider de "folhagem" de um tronco morto: o proprio tronco, sem propriedade de folha.

    Um snag nao tem copa, mas o codec de arvore exige um foliage_provider. Passar o tronco
    por leaves_state() colocaria distance/persistent/waterlogged num RotatedPillarBlock, que
    nao tem nenhuma dessas propriedades — funciona por acidente hoje e e exatamente o tipo de
    coisa que quebra numa atualizacao.
    """
    return simple(name, {"axis": "y"})


# ------------------------------------------------------------------ arvores


def fruit_nodes(node, probability=0.22, spacing=2):
    """Decorador que pendura no de fruto na FACE DE BAIXO da copa.

    E o mesmo `attached_to_leaves` que o vanilla usa para o propagulo de mangue, e ele resolve
    sozinho as quatro exigencias do escopo: so em folha (nunca no tronco), so virado para baixo
    (nunca fruto flutuando), `required_empty_blocks` garante espaco livre embaixo, e o raio de
    exclusao limita o total a poucos nos por arvore em vez de forrar a copa.

    Com raio 2 em XZ uma copa comum recebe de 1 a 4 nos — a faixa que o escopo pede — sem que
    seja preciso contar nada em codigo.
    """
    return {
        "type": "minecraft:attached_to_leaves",
        "probability": probability,
        "exclusion_radius_xz": spacing,
        "exclusion_radius_y": 1,
        "required_empty_blocks": 2,
        "block_provider": simple("%s:%s" % (MOD, node)),
        "directions": ["down"],
    }


def tree(trunk, foliage, trunk_placer, foliage_placer, size, root=None, decorators=None):
    # Tronco morto: a "folhagem" e o proprio tronco, entao entra sem propriedade de folha.
    provider = dead_state(foliage) if foliage == trunk else leaves_state(foliage)
    cfg = {
        "trunk_provider": simple(trunk, {"axis": "y"}),
        "foliage_provider": provider,
        "dirt_provider": simple("minecraft:dirt"),
        "trunk_placer": trunk_placer,
        "foliage_placer": foliage_placer,
        "minimum_size": size,
        "decorators": decorators or [],
        "ignore_vines": True,
        "force_dirt": False,
    }
    if root:
        cfg["root_placer"] = root
    return {"type": "minecraft:tree", "config": cfg}


def straight(base, a, b):
    return {"type": "minecraft:straight_trunk_placer",
            "base_height": base, "height_rand_a": a, "height_rand_b": b}


def fancy(base, a, b):
    return {"type": "minecraft:fancy_trunk_placer",
            "base_height": base, "height_rand_a": a, "height_rand_b": b}


def forking(base, a, b):
    return {"type": "minecraft:forking_trunk_placer",
            "base_height": base, "height_rand_a": a, "height_rand_b": b}


def spruce_foliage(radius, offset, lo, hi):
    return {"type": "minecraft:spruce_foliage_placer", "radius": radius, "offset": offset,
            "trunk_height": {"type": "minecraft:uniform",
                             "value": {"min_inclusive": lo, "max_inclusive": hi}}}


def blob_foliage(radius, offset, height):
    return {"type": "minecraft:blob_foliage_placer",
            "radius": radius, "offset": offset, "height": height}


def two_layers(limit, lower, upper):
    return {"type": "minecraft:two_layers_feature_size",
            "limit": limit, "lower_size": lower, "upper_size": upper}


# ---------------------------------------------------------------------- geometria da copa
#
# Duas medidas governam toda a tabela abaixo, e as duas saem de defeito observado em jogo, nao
# de gosto:
#
# ALTURA LIVRE. A copa tem de comecar alto o bastante para se andar embaixo. A conta e:
#
#     folha mais baixa = altura_do_tronco + offset - altura_da_folhagem
#
# Na primeira versao a copa descia ate ABAIXO da base do tronco: a mediana medida na dark_wilds
# foi -3 e o minimo -7, ou seja folha dentro do chao e nenhuma passagem. As formas aqui miram
# 5-9 blocos livres, medidos, nao estimados.
#
# RAIO <= 3. Uma folha decai quando nenhum tronco esta a 6 passos de Manhattan. Numa copa de
# raio 4 sobre um tronco unico, a quina (4,0)+(0,3) da 7 — a folha nasce e apodrece. Media
# medida: 6-8% da folhagem orfa, que e o que aparecia como "arvore voando sem folhas". Raio 3
# mantem a quina em 6. Raio 4 so no ferrofuste antigo, porque `fancy` poe galho DENTRO da copa
# e assim todo bloco de folha tem madeira perto.
#
# Consequencia de projeto: `fancy` deixou de ser a forma das arvores comuns. Ele ramifica a
# partir de ~0,618 da altura e pendura folhagem em cada galho, o que fecha o sub-bosque de um
# jeito que nenhum ajuste de offset resolve. Fica reservado aos exemplares raros e altos.

TREES = {
    # coniferas altas da mata escura
    "imperial_pine": tree("firstcrusade:imperial_pine_log", "firstcrusade:imperial_pine_leaves",
                          straight(11, 4, 3), spruce_foliage(3, 1, 5, 8), two_layers(2, 0, 2)),
    "wild_pine": tree("firstcrusade:imperial_pine_log", "firstcrusade:imperial_pine_leaves",
                      straight(14, 5, 3), spruce_foliage(3, 1, 7, 11), two_layers(2, 0, 2)),
    # folhosa que quebra a monotonia da conifera
    "blighted_oak": tree("firstcrusade:blighted_oak_log", "firstcrusade:blighted_oak_leaves",
                         straight(8, 3, 2), blob_foliage(3, 1, 4), two_layers(1, 0, 2)),
    "scrub_oak": tree("firstcrusade:blighted_oak_log", "firstcrusade:blighted_oak_leaves",
                      forking(6, 2, 1), blob_foliage(2, 1, 2), two_layers(1, 0, 1)),
    # madeira morta: tronco sem copa nenhuma
    "ash_snag": tree("firstcrusade:ash_snag_log", "firstcrusade:ash_snag_log",
                     straight(5, 4, 2), blob_foliage(0, 0, 1), two_layers(1, 0, 1)),
    "charred_snag": tree("firstcrusade:charred_snag_log", "firstcrusade:charred_snag_log",
                         straight(4, 3, 2), blob_foliage(0, 0, 1), two_layers(1, 0, 1)),
    # gigante da selva mortal: alto, e a copa toda em cima. Uma selva onde nao se anda nao e
    # uma selva perigosa, e uma parede.
    "venom_bough": tree("firstcrusade:venom_bough_log", "firstcrusade:venom_bough_leaves",
                        straight(13, 5, 3), blob_foliage(3, 1, 5), two_layers(2, 0, 2)),
    # torre fungica Ork (worldgen so a coloca em territorio Ork via runtime; aqui fica
    # registrada para reuso)
    "ork_fungal_tower": tree("firstcrusade:ork_fungal_stalk", "firstcrusade:ork_fungal_cap",
                             straight(7, 3, 2), blob_foliage(3, 1, 2), two_layers(1, 0, 1)),

    # ---------------------------------------------------------------- fase C
    #
    # Regra que vale para as tres proximas: uma ESPECIE, varias FEATURES. Ferrofuste
    # comum, ferrofuste antigo e ferrofuste resinoso compartilham a mesma copa, entao a
    # mata le como um bosque so — o que muda e a silhueta, nao a paleta.
    "ironwood": tree("firstcrusade:ironwood_log", "firstcrusade:ironwood_leaves",
                     straight(10, 3, 2), blob_foliage(3, 1, 4), two_layers(1, 0, 2)),
    # O unico com `fancy` e raio 4: os galhos ficam dentro da copa, entao a folha tem madeira
    # perto e nao apodrece. E raro (1 por chunk), que e o que o torna um marco na paisagem.
    "ironwood_ancient": tree("firstcrusade:ironwood_log", "firstcrusade:ironwood_leaves",
                             fancy(15, 5, 3), blob_foliage(4, 1, 4), two_layers(2, 0, 2)),
    "resin_ironwood": tree("firstcrusade:resin_ironwood_log", "firstcrusade:ironwood_leaves",
                           straight(11, 4, 2), spruce_foliage(3, 1, 6, 9), two_layers(2, 0, 2)),

    # Mangue: baixo, aberto e com raizes de verdade acima do lodo — e a raiz que faz o
    # pantano parecer pantano, e nao um lago com arvores dentro.
    "sump_mangrove": tree(
        "firstcrusade:sump_mangrove_log", "firstcrusade:sump_mangrove_leaves",
        forking(7, 3, 2), blob_foliage(3, 1, 3), two_layers(1, 0, 1),
        root={
            "type": "minecraft:mangrove_root_placer",
            "root_provider": simple("firstcrusade:sump_mangrove_log", {"axis": "y"}),
            "trunk_offset_y": {"type": "minecraft:constant", "value": 1},
            # Sem above_root_placement de proposito: o vanilla poe tapete de musgo em cima
            # da raiz, e o equivalente aqui seria uma planta do mod — que so sobrevive sobre
            # solo, nao sobre tronco. Colocada assim ela ficaria de pe ate a primeira
            # atualizacao de bloco e depois cairia.
            "mangrove_root_placement": {
                "max_root_width": 3,
                "max_root_length": 5,
                "random_skew_chance": 0.5,
                "can_grow_through": [
                    "firstcrusade:sump_mud", "minecraft:mud", "minecraft:muddy_mangrove_roots",
                    "minecraft:dirt", "minecraft:coarse_dirt", "minecraft:grass_block",
                ],
                "muddy_roots_in": "#minecraft:dirt",
                "muddy_roots_provider": simple("firstcrusade:sump_mangrove_log", {"axis": "y"}),
            },
        }),
    "rotting_sump_tree": tree(
        "firstcrusade:sump_mangrove_log", "firstcrusade:sump_mangrove_log",
        forking(4, 3, 1), blob_foliage(0, 0, 1), two_layers(1, 0, 1)),
    "toxic_willow": tree("firstcrusade:toxic_willow_log", "firstcrusade:toxic_willow_leaves",
                         straight(10, 4, 2), blob_foliage(3, 1, 4), two_layers(1, 0, 1)),

    # Conifera estreita da tundra, e a mesma morta em pe. Raio 3 (era 2): com 2 a copa saia
    # um tufo fino no alto de um poste, o que na tela le como arvore sem folha.
    "frostnut_pine": tree("firstcrusade:frostnut_pine_log", "firstcrusade:frostnut_pine_leaves",
                          straight(10, 4, 2), spruce_foliage(3, 1, 5, 8), two_layers(2, 0, 2)),
    "frozen_dead_tree": tree("firstcrusade:frostnut_pine_log", "firstcrusade:frostnut_pine_log",
                             straight(4, 3, 2), blob_foliage(0, 0, 1), two_layers(1, 0, 1)),

    # Ermo de sal: dois troncos mortos, ambos sem copa.
    "salt_thorn": tree("firstcrusade:salt_thorn_log", "firstcrusade:salt_thorn_log",
                       forking(3, 2, 1), blob_foliage(0, 0, 1), two_layers(1, 0, 1)),
    "fossilized_trunk": tree("firstcrusade:fossilized_trunk", "firstcrusade:fossilized_trunk",
                             straight(4, 4, 2), blob_foliage(0, 0, 1), two_layers(1, 0, 1)),

    # ---------------------------------------------------------------- fase D: frutiferas
    #
    # Tres madeiras novas e duas emprestadas. Uma silhueta nova nao merece uma especie nova: o
    # pinheiro gelanoz e o ramo venenoso ja crescem onde a fruta deles cresce, entao eles ganham
    # um feature proprio com no de fruto em vez de uma arvore inteira sosia.
    #
    # Todas seguem a mesma geometria de copa das outras (raio <= 3, folha mais baixa a 5+ do chao):
    # um pomar em que nao se anda nao serve para colher.
    "rationfruit": tree(
        "firstcrusade:rationfruit_log", "firstcrusade:rationfruit_leaves",
        straight(7, 3, 2), blob_foliage(3, 1, 3), two_layers(1, 0, 2),
        decorators=[fruit_nodes("rationfruit_node", 0.30)]),

    # Pomar plantado: baixo e regular, porque alguem o plantou em linha.
    "feed_pod": tree(
        "firstcrusade:orchard_log", "firstcrusade:feed_pod_leaves",
        straight(6, 2, 2), blob_foliage(3, 1, 3), two_layers(1, 0, 1),
        decorators=[fruit_nodes("feed_pod_node", 0.34)]),

    "lumenfruit": tree(
        "firstcrusade:lumenfruit_log", "firstcrusade:lumenfruit_leaves",
        forking(8, 3, 2), blob_foliage(3, 1, 3), two_layers(1, 0, 1),
        decorators=[fruit_nodes("lumenfruit_node", 0.26)]),

    # A mesma conifera da tundra, desta vez carregada. Nao e outra especie — e a mesma arvore
    # num ano bom, e por isso e menos comum que a versao sem fruto.
    "frostnut_pine_bearing": tree(
        "firstcrusade:frostnut_pine_log", "firstcrusade:frostnut_pine_leaves",
        straight(10, 4, 2), spruce_foliage(3, 1, 5, 8), two_layers(2, 0, 2),
        decorators=[fruit_nodes("frostnut_node", 0.24)]),

    "venom_pear": tree(
        "firstcrusade:venom_bough_log", "firstcrusade:venom_pear_leaves",
        straight(12, 4, 3), blob_foliage(3, 1, 4), two_layers(2, 0, 2),
        decorators=[fruit_nodes("venom_pear_node", 0.24)]),
}


# ------------------------------------------------------------------ manchas de flora


def plant_filter(block, props=None):
    """O filtro de uma planta: o alvo tem de estar VAZIO **e** o chao tem de servir.

    A parte do "vazio" e a que faltava, e a omissao foi caríssima. `SimpleBlockFeature` so
    consulta `canSurvive` — ele **nao** verifica se a posicao esta livre antes de escrever.
    Com `y_spread`, o `random_patch` sorteia posicoes DENTRO do terreno, e ali `would_survive`
    responde "sim" (o bloco de baixo e terra), entao a feature trocava um bloco de chao por
    uma planta. Cada planta abria um buraco de um bloco, e com dezenas de tentativas por
    chunk o chao virava um xadrez de covas — em todos os biomas, porque todos passam por aqui.

    O vanilla usa so `matching_blocks: air` em `patch_grass`. Aqui vao os dois: `air` impede
    a escavacao e `would_survive` impede planta em chao que nao a sustenta (a regra de solo
    do mod e mais estreita que a do vanilla, ver FloraTags.GROUND_*).
    """
    target = state("%s:%s" % (MOD, block), props)
    return {
        "type": "minecraft:block_predicate_filter",
        "predicate": {
            "type": "minecraft:all_of",
            "predicates": [
                {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"},
                # O bloco de baixo tem de ser CHAO, nao qualquer coisa solida.
                #
                # `would_survive` sozinho nao basta para tapete: `CarpetBlock.canSurvive` so exige
                # que o bloco de baixo nao seja ar, entao tapete sobre tapete passa. Com y_spread 3
                # a mancha tenta posicoes acima das que ja preencheu e o resultado, em jogo, e manta
                # de agulhas com dois blocos de altura — o dono viu e reportou.
                #
                # Exigir a tag de chao resolve para toda forma de uma vez: planta nao nasce sobre
                # planta, tapete nao nasce sobre tapete, e nada nasce sobre tronco ou folha.
                {"type": "minecraft:matching_block_tag",
                 "offset": [0, -1, 0],
                 "tag": "%s:flora_ground_any" % MOD},
                {"type": "minecraft:would_survive", "state": target},
            ],
        },
    }


def patch(block, tries=48, xz=7, y=3, on_ground=True):
    """Mancha de plantas pequenas, no formato que o vanilla usa para capim e flores."""
    inner = {
        "feature": {"type": "minecraft:simple_block",
                    "config": {"to_place": simple("%s:%s" % (MOD, block))}},
        "placement": [plant_filter(block)],
    }
    return {"type": "minecraft:random_patch",
            "config": {"tries": tries, "xz_spread": xz, "y_spread": y, "feature": inner}}


def tall_patch(block, tries=24):
    """Planta de dois blocos: o feature de bloco duplo cuida das duas metades."""
    inner = {
        "feature": {"type": "minecraft:simple_block",
                    "config": {"to_place": simple("%s:%s" % (MOD, block), {"half": "lower"})}},
        "placement": [plant_filter(block, {"half": "lower"})],
    }
    return {"type": "minecraft:random_patch",
            "config": {"tries": tries, "xz_spread": 6, "y_spread": 2, "feature": inner}}


def rock(block):
    """Pedra/entulho: um pequeno blob na superficie."""
    return {"type": "minecraft:forest_rock", "config": {"state": state(block)}}


def fallen_log(block):
    """Tronco caido: coluna deitada de 2-4 blocos."""
    return {"type": "minecraft:block_pile",
            "config": {"state_provider": simple(block, {"axis": "x"})}}


# ------------------------------------------------------------------ placed features


def placed(feature, count, rarity=None, heightmap="OCEAN_FLOOR_WG"):
    p = []
    if rarity:
        p.append({"type": "minecraft:rarity_filter", "chance": rarity})
    if count:
        p.append({"type": "minecraft:count", "count": count})
    p += [{"type": "minecraft:in_square"},
          {"type": "minecraft:heightmap", "heightmap": heightmap},
          {"type": "minecraft:biome"}]
    return {"feature": "%s:%s" % (MOD, feature), "placement": p}


def placed_name(feature, count, rarity, variant_count):
    """Nome do arquivo de placed feature para uma dada densidade.

    Um placed feature carrega a CONTAGEM, entao dois biomas que querem a mesma planta em
    densidades diferentes precisam de dois arquivos. A versao anterior deste script usava
    o nome da configured feature direto: 'patch_withered_scrub' era escrito uma vez por
    bioma que o pedia e o ultimo a escrever ganhava — na pratica todos os biomas herdavam
    a densidade de um deles. O sufixo so aparece quando existe mais de uma variante, para
    o caso comum continuar com o nome limpo.
    """
    if variant_count < 2:
        return feature

    suffix = ""
    if count:
        suffix += "_n%d" % count
    if rarity:
        suffix += "_r%d" % rarity
    return feature + (suffix or "_plain")


# ------------------------------------------------------------------ o que cada bioma tem
#
# (feature, contagem por chunk, raridade 1-em-N)

BIOME_VEGETATION = {
    "dark_wilds": [
        ("tree_wild_pine", 5, None),
        ("tree_blighted_oak", 3, None),
        ("tree_scrub_oak", 2, None),
        ("patch_imperial_grass", 8, None),
        ("patch_withered_scrub", 5, None),
        ("patch_dark_fern", 4, None),
        ("patch_scattered_twigs", 3, None),
        ("patch_small_roots", 2, None),
        ("patch_fallen_leaves", 2, None),
        ("tall_tall_imperial_grass", 2, None),
        ("rock_mossy", 1, 3),
        ("log_pine", 1, 4),
        ("tree_rationfruit", 1, 8),
    ],
    "pale_steppe": [
        ("tree_scrub_oak", 1, 2),
        ("tree_imperial_pine", 1, 5),
        ("patch_withered_scrub", 9, None),
        ("patch_imperial_grass", 7, None),
        ("patch_roadside_thistle", 3, None),
        ("patch_ash_grass", 2, None),
        ("tall_tall_imperial_grass", 3, None),
        ("patch_scattered_twigs", 2, None),
        ("rock_stone", 1, 2),
        ("tree_rationfruit", 1, 3),
        ("tree_feed_pod", 1, 6),
    ],
    "ash_waste": [
        ("tree_ash_snag", 3, None),
        ("tree_charred_snag", 2, None),
        ("patch_ash_grass", 7, None),
        ("patch_soot_grass", 5, None),
        ("patch_burnt_stubble", 6, None),
        ("patch_ash_layer", 4, None),
        ("patch_rubble_pebbles", 4, None),
        ("patch_scattered_twigs", 2, None),
        ("tall_tall_ash_grass", 2, None),
        ("rock_blackstone", 1, 2),
        ("log_charred", 1, 3),
    ],
    "death_jungle": [
        ("tree_venom_bough", 7, None),
        ("tree_scrub_oak", 3, None),
        ("patch_venom_frond", 9, None),
        ("patch_spine_bush", 6, None),
        ("patch_toxic_bloom", 3, None),
        ("patch_fanged_sprout", 3, None),
        ("patch_mire_reed", 4, None),
        ("patch_small_roots", 3, None),
        ("tall_tall_mire_reed", 2, None),
        ("rock_mossy", 1, 2),
        ("log_venom", 1, 3),
        ("tree_venom_pear", 1, 3),
    ],

    # ------------------------------------------------------------------ fase C
    #
    # A floresta mais fechada do mod: 11 arvores por chunk contra 10 do dark_wilds, e o
    # chao e todo samambaia e agulha caida. E o unico bioma com dossel de verdade.
    "ironwood_forest": [
        ("tree_ironwood", 7, None),
        ("tree_resin_ironwood", 3, None),
        ("tree_ironwood_ancient", 1, None),
        ("patch_iron_fern", 9, None),
        ("patch_dark_fern", 5, None),
        ("patch_withered_scrub", 3, None),
        ("patch_needle_litter", 6, None),
        ("patch_small_roots", 4, None),
        ("patch_scattered_twigs", 3, None),
        ("rock_mossy", 1, 2),
        ("rock_deepslate", 1, 3),
        ("log_ironwood", 1, 3),
    ],

    # Pantano: os lagos vem na etapa 1 (ver LAKES), nao aqui — agua tem de existir antes
    # da vegetacao, senao os juncos nascem em terreno que a poca depois inunda.
    "sump_marsh": [
        ("tree_sump_mangrove", 4, None),
        ("tree_toxic_willow", 2, None),
        ("tree_rotting_sump_tree", 2, None),
        ("patch_marsh_grass", 10, None),
        ("patch_mire_reed", 7, None),
        ("patch_bog_fungus", 4, None),
        ("patch_gas_bladder", 3, None),
        ("patch_sludge_algae", 4, None),
        ("tall_tall_marsh_reed", 3, None),
        ("patch_small_roots", 3, None),
        ("log_ironwood", 1, 4),
        ("tree_lumenfruit", 1, 3),
    ],

    # Tundra: arvore quase nenhuma e osso por todo lado. O que faz a regiao e o vazio.
    "ossuary_tundra": [
        ("tree_frostnut_pine", 2, None),
        ("tree_frozen_dead_tree", 1, None),
        ("patch_snow_scrub", 7, None),
        ("patch_withered_scrub", 4, None),
        ("patch_tundra_moss", 4, None),
        ("patch_bone_fragments", 3, None),
        ("patch_ossuary_lily", 1, 3),
        ("patch_scattered_twigs", 2, None),
        ("rock_packed_ice", 1, 3),
        ("rock_stone", 1, 4),
        ("tree_frostnut_pine_bearing", 1, 4),
    ],

    # ------------------------------------------------------ biomas de planeta
    #
    # Montanha de Macragge: quase esteril de proposito. Um punhado de moitas nas fendas, muita
    # pedra solta, e uma conifera a cada tres chunks marcando um vale abrigado.
    "rocky_highland": [
        ("tree_imperial_pine", 1, 3),
        ("patch_withered_scrub", 4, None),
        ("patch_imperial_grass", 2, None),
        ("patch_rubble_pebbles", 6, None),
        ("patch_scattered_twigs", 2, None),
        ("rock_stone", 2, None),
        ("rock_deepslate", 1, 2),
    ],

    # Terras altas vulcanicas de Armageddon: rocha negra, cinza e tronco carbonizado.
    "volcanic_highland": [
        ("tree_charred_snag", 2, None),
        ("patch_soot_grass", 4, None),
        ("patch_ash_layer", 5, None),
        ("patch_burnt_stubble", 3, None),
        ("patch_rubble_pebbles", 5, None),
        ("rock_blackstone", 2, None),
        ("log_charred", 1, 3),
    ],

    # O bioma mais vazio do mod, e de proposito: um horizonte aberto tambem e paisagem.
    "salt_waste": [
        ("tree_salt_thorn", 1, 2),
        ("tree_fossilized_trunk", 1, 8),
        ("patch_brine_grass", 5, None),
        ("patch_salt_flake", 6, None),
        ("patch_withered_scrub", 2, None),
        ("patch_bone_fragments", 2, None),
        ("patch_brine_thistle", 1, 3),
        ("rock_calcite", 1, 3),
    ],
}

# Lagos por bioma (etapa 1: lakes). Roda antes da vegetacao de proposito.
LAKES = {
    "sump_marsh": [("lake_marsh_pool", None, 5)],
}

PATCHES = {
    "imperial_grass": 56, "withered_scrub": 48, "dark_fern": 40, "scattered_twigs": 32,
    "small_roots": 28, "roadside_thistle": 28, "ash_grass": 48, "soot_grass": 44,
    "burnt_stubble": 48, "rubble_pebbles": 32, "venom_frond": 52, "spine_bush": 44,
    "toxic_bloom": 24, "fanged_sprout": 24, "mire_reed": 36, "fallen_leaves": 28,
    "ash_layer": 36,
    # ---- fase C ----
    "iron_fern": 52, "needle_litter": 40, "marsh_grass": 56, "bog_fungus": 26,
    "gas_bladder": 20, "sludge_algae": 32, "snow_scrub": 44, "tundra_moss": 30,
    "bone_fragments": 22, "ossuary_lily": 16, "brine_grass": 30, "salt_flake": 34,
    "brine_thistle": 14,
}

TALL = ["tall_imperial_grass", "tall_ash_grass", "tall_mire_reed", "tall_marsh_reed"]

ROCKS = {"rock_mossy": "minecraft:mossy_cobblestone",
         "rock_stone": "minecraft:cobblestone",
         "rock_blackstone": "minecraft:blackstone",
         "rock_deepslate": "minecraft:cobbled_deepslate",
         "rock_packed_ice": "minecraft:packed_ice",
         "rock_calcite": "minecraft:calcite"}

LOGS = {"log_pine": "firstcrusade:imperial_pine_log",
        "log_charred": "firstcrusade:charred_snag_log",
        "log_venom": "firstcrusade:venom_bough_log",
        "log_ironwood": "firstcrusade:ironwood_log"}

# Poca de pantano. barrier_states usa o proprio solo do bioma, para a borda da poca nao
# aparecer como um anel de pedra no meio do lodo.
LAKE_FEATURES = {
    "lake_marsh_pool": {
        "type": "minecraft:lake",
        "config": {
            "fluid": simple("minecraft:water", {"level": "0"}),
            "barrier": simple("firstcrusade:sump_mud"),
        },
    },
}


def main():
    # --- configured features -------------------------------------------------
    for name, cfg in TREES.items():
        write(os.path.join(CF, "tree_%s.json" % name), cfg)

    for block, tries in PATCHES.items():
        write(os.path.join(CF, "patch_%s.json" % block), patch(block, tries=tries))

    for block in TALL:
        write(os.path.join(CF, "tall_%s.json" % block), tall_patch(block))

    for name, block in ROCKS.items():
        write(os.path.join(CF, "%s.json" % name), rock(block))

    for name, block in LOGS.items():
        write(os.path.join(CF, "%s.json" % name), fallen_log(block))

    for name, cfg in LAKE_FEATURES.items():
        write(os.path.join(CF, "%s.json" % name), cfg)

    # --- placed features -----------------------------------------------------
    #
    # Uma passagem so para descobrir quantas densidades distintas cada feature tem; e essa
    # contagem que decide se o nome leva sufixo.
    variants = {}
    for entries in list(BIOME_VEGETATION.values()) + list(LAKES.values()):
        for feature, count, rarity in entries:
            variants.setdefault(feature, set()).add((count, rarity))

    names = {}
    for feature, combos in variants.items():
        for count, rarity in combos:
            names[(feature, count, rarity)] = placed_name(feature, count, rarity, len(combos))

    for (feature, count, rarity), name in names.items():
        # Lago usa WORLD_SURFACE_WG: OCEAN_FLOOR_WG ignora agua e afundaria a poca dentro
        # do terreno que a poca anterior acabou de escavar.
        is_lake = feature in LAKE_FEATURES
        heightmap = "WORLD_SURFACE_WG" if is_lake else "OCEAN_FLOOR_WG"
        write(os.path.join(PF, "%s.json" % name), placed(feature, count, rarity, heightmap))

    # --- liga nas etapas de feature de cada bioma -----------------------------
    for biome_name, entries in BIOME_VEGETATION.items():
        path = os.path.join(BIOME, biome_name + ".json")
        with open(path, encoding="utf-8") as f:
            data = json.load(f)

        data["features"][1] = ["%s:%s" % (MOD, names[e]) for e in LAKES.get(biome_name, [])]
        data["features"][9] = ["%s:%s" % (MOD, names[e]) for e in entries]
        write(path, data)

    # --- limpa placed features orfaos ----------------------------------------
    #
    # Este script e dono unico do diretorio, entao um arquivo que ele nao acabou de
    # escrever e sobra de uma versao anterior. Deixa-lo la e pior do que parece: um placed
    # feature invalido ou nao referenciado ainda e carregado e validado pelo jogo.
    keep = {name + ".json" for name in names.values()}
    removed = 0
    for existing in sorted(os.listdir(PF)):
        if existing.endswith(".json") and existing not in keep:
            os.remove(os.path.join(PF, existing))
            removed += 1

    total_cf = (len(TREES) + len(PATCHES) + len(TALL) + len(ROCKS) + len(LOGS)
                + len(LAKE_FEATURES))
    print("configured features: %d" % total_cf)
    print("placed features    : %d (orfaos removidos: %d)" % (len(names), removed))
    for b, e in BIOME_VEGETATION.items():
        lakes = len(LAKES.get(b, []))
        print("  %-16s %2d na vegetal_decoration%s"
              % (b, len(e), (" + %d lago(s)" % lakes) if lakes else ""))


if __name__ == "__main__":
    main()
