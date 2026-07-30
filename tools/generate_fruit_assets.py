#!/usr/bin/env python3
"""
Assets das frutas da Fase D: nos de fruto (4 estagios cada) e os itens.

O que este script possui
------------------------
  textures/block/<no>_stage<0-3>.png     as quatro fases do no
  textures/item/<fruta>.png              o item colhido
  blockstates/<no>.json                  variante por age
  models/block/<no>_stage<n>.json        cacho pendurado no teto
  models/item/<fruta>.json               sprite
  loot_tables/blocks/<no>.json           so a fruta madura dropa ao quebrar
  as chaves de idioma dos itens e dos nos

O que NAO possui: madeira e folhagem das frutiferas (generate_tree_assets.py), features de
worldgen (generate_worldgen_features.py) e as tags de tronco/folha (datagen).

Uso:
    python tools/generate_fruit_assets.py
"""

import json
import os
import random

from PIL import Image

SIZE = 16
RES = os.path.join("src", "main", "resources")
A = os.path.join(RES, "assets", "firstcrusade")
D = os.path.join(RES, "data", "firstcrusade")
TEX_B = os.path.join(A, "textures", "block")
TEX_I = os.path.join(A, "textures", "item")

MOD = "firstcrusade"

# no, fruta, cor da fruta, cor do talo, EN no, PT no, EN fruta, PT fruta
FRUITS = [
    ("rationfruit_node", "ration_fruit", (196, 132, 54), (96, 116, 58),
     "Rationfruit", "Fruta-Racao", "Ration Fruit", "Fruta-Racao"),
    ("feed_pod_node", "grox_feed_pod", (188, 176, 84), (110, 122, 62),
     "Feed Pod", "Vagem de Racao", "Grox Feed Pod", "Vagem de Racao de Grox"),
    ("lumenfruit_node", "lumenfruit", (150, 226, 228), (86, 128, 118),
     "Lumenfruit", "Fruta-Lumen", "Lumenfruit", "Fruta-Lumen"),
    ("frostnut_node", "frostnut", (214, 216, 206), (74, 92, 92),
     "Frostnut", "Noz-Gelada", "Frostnut", "Noz-Gelada"),
    ("venom_pear_node", "venom_pear", (158, 108, 186), (92, 88, 62),
     "Venom Pear", "Pera Venenosa", "Venom Pear", "Pera Venenosa"),
]


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def ramp(base, spread=0.26):
    r, g, b = base
    out = []
    for k in (1.0 - spread, 1.0 - spread * 0.5, 1.0, 1.0 + spread * 0.5, 1.0 + spread * 0.85):
        out.append(tuple(max(0, min(255, int(round(c * k)))) for c in (r, g, b)) + (255,))
    return out


CLEAR = (0, 0, 0, 0)


def node_texture(name, stage, fruit_col, stem_col):
    """O no em uma das quatro fases: talo curto no topo e o fruto crescendo sob ele.

    O crescimento e legivel de longe de proposito — raio e altura mudam juntos, entao a fase
    madura tem cerca do dobro da area da primeira. Um jogador precisa saber se vale a pena
    andar ate a arvore antes de chegar nela.
    """
    rng = random.Random("%s%d" % (name, stage))
    pal = ramp(fruit_col)
    stem = ramp(stem_col, spread=0.2)
    im = Image.new("RGBA", (SIZE, SIZE), CLEAR)
    px = im.load()

    # talo: sempre colado no topo do tile, que e onde a folha fica
    stem_len = (2, 2, 3, 3)[stage]
    for y in range(stem_len):
        px[7, y] = stem[1]
        px[8, y] = stem[2]

    radius = (1.6, 2.2, 2.9, 3.5)[stage]
    cx = 7.5
    cy = stem_len + radius - 0.5

    for y in range(SIZE):
        for x in range(SIZE):
            dx, dy = x - cx, y - cy
            d = (dx * dx + dy * dy * 0.85) ** 0.5
            if d > radius + rng.uniform(-0.25, 0.25):
                continue
            # luz vinda de cima-esquerda, como o resto do pacote
            if d < radius * 0.45 and dx < 0 and dy < 0:
                tone = pal[4]
            elif d > radius * 0.82:
                tone = pal[0]
            else:
                tone = pal[rng.choice((1, 2, 2, 3))]
            px[x, y] = tone

    # brilho especular de um pixel: e o que faz o fruto parecer fruto e nao bolinha
    if stage >= 2:
        px[int(cx - radius * 0.45), int(cy - radius * 0.45)] = pal[4]
    return im


def item_texture(name, fruit_col, stem_col):
    """O item na mao: o mesmo fruto, maduro, com um caule curto."""
    rng = random.Random(name + "_item")
    pal = ramp(fruit_col)
    stem = ramp(stem_col, spread=0.2)
    im = Image.new("RGBA", (SIZE, SIZE), CLEAR)
    px = im.load()

    cx, cy, radius = 7.5, 9.0, 4.6
    for y in range(SIZE):
        for x in range(SIZE):
            dx, dy = x - cx, y - cy
            d = (dx * dx + dy * dy * 0.9) ** 0.5
            if d > radius + rng.uniform(-0.3, 0.3):
                continue
            if d < radius * 0.4 and dx < 0 and dy < 0:
                tone = pal[4]
            elif d > radius * 0.84:
                tone = pal[0]
            else:
                tone = pal[rng.choice((1, 2, 2, 3))]
            px[x, y] = tone

    for y in range(2, 5):
        px[8, y] = stem[1]
    px[9, 2] = stem[3]
    px[10, 3] = stem[2]
    px[int(cx - radius * 0.4), int(cy - radius * 0.4)] = pal[4]
    return im


def node_assets(node, fruit):
    write_json(os.path.join(A, "blockstates", node + ".json"), {
        "variants": {
            "age=%d" % n: {"model": "%s:block/%s_stage%d" % (MOD, node, n)}
            for n in range(4)
        }
    })

    for n in range(4):
        # Cacho pendurado: duas faces cruzadas, encostadas no teto do bloco. Nao usa o modelo
        # `cross` do vanilla porque aquele nasce no CHAO do bloco — um fruto desenhado assim
        # apareceria flutuando um bloco abaixo da copa.
        size = (5, 6, 8, 8)[n]
        low = 16 - (5, 6, 7, 8)[n]
        half = size / 2.0
        write_json(os.path.join(A, "models", "block", "%s_stage%d.json" % (node, n)), {
            "parent": "minecraft:block/block",
            "render_type": "minecraft:cutout",
            "ambientocclusion": False,
            "textures": {
                "fruit": "%s:block/%s_stage%d" % (MOD, node, n),
                "particle": "%s:block/%s_stage%d" % (MOD, node, n),
            },
            "elements": [
                {
                    "from": [8 - half, low, 8],
                    "to": [8 + half, 16, 8],
                    "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": 45,
                                 "rescale": True},
                    "shade": False,
                    "faces": {
                        "north": {"uv": [0, 0, 16, 16], "texture": "#fruit"},
                        "south": {"uv": [0, 0, 16, 16], "texture": "#fruit"},
                    },
                },
                {
                    "from": [8, low, 8 - half],
                    "to": [8, 16, 8 + half],
                    "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": 45,
                                 "rescale": True},
                    "shade": False,
                    "faces": {
                        "west": {"uv": [0, 0, 16, 16], "texture": "#fruit"},
                        "east": {"uv": [0, 0, 16, 16], "texture": "#fruit"},
                    },
                },
            ],
        })

    # Quebrar o no so rende fruta se ela estava madura — colher com a mao (clique direito) e
    # o caminho normal e deixa o no de pe.
    write_json(os.path.join(D, "loot_tables", "blocks", node + ".json"), {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "bonus_rolls": 0,
            "entries": [{"type": "minecraft:item", "name": "%s:%s" % (MOD, fruit)}],
            "conditions": [
                {"condition": "minecraft:survives_explosion"},
                {"condition": "minecraft:block_state_property",
                 "block": "%s:%s" % (MOD, node),
                 "properties": {"age": "3"}},
            ],
        }],
    })


def item_assets(fruit):
    write_json(os.path.join(A, "models", "item", fruit + ".json"), {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "%s:item/%s" % (MOD, fruit)},
    })


def merge_lang(path, additions):
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    added = 0
    for k, v in additions.items():
        if k not in data:
            data[k] = v
            added += 1
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")
    return added


def main():
    os.makedirs(TEX_B, exist_ok=True)
    os.makedirs(TEX_I, exist_ok=True)

    en, pt = {}, {}
    textures = 0

    for node, fruit, col, stem, node_en, node_pt, item_en, item_pt in FRUITS:
        for n in range(4):
            node_texture(node, n, col, stem).save(
                os.path.join(TEX_B, "%s_stage%d.png" % (node, n)))
            textures += 1
        item_texture(fruit, col, stem).save(os.path.join(TEX_I, fruit + ".png"))
        textures += 1

        node_assets(node, fruit)
        item_assets(fruit)

        en["block.firstcrusade." + node] = node_en
        pt["block.firstcrusade." + node] = node_pt
        en["item.firstcrusade." + fruit] = item_en
        pt["item.firstcrusade." + fruit] = item_pt

    a1 = merge_lang(os.path.join(A, "lang", "en_us.json"), en)
    a2 = merge_lang(os.path.join(A, "lang", "pt_br.json"), pt)

    print("frutas: %d | texturas: %d | lang en+%d pt+%d" % (len(FRUITS), textures, a1, a2))


if __name__ == "__main__":
    main()
