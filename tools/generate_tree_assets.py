#!/usr/bin/env python3
"""
Gera texturas e JSONs das seis arvores do First Crusade.

Escopo: 6 troncos (lado + topo) e 5 folhagens = 17 texturas, mais blockstates,
modelos de bloco e item, loot tables e as chaves de idioma.

As tags (minecraft:logs, minecraft:leaves, mineable/*) NAO saem daqui: elas pertencem
ao datagen (FCBlockTagsProvider), que ja e dono desses arquivos em src/generated.
Escrever os mesmos caminhos aqui criaria dois donos para o mesmo arquivo.

Uso:
    python tools/generate_tree_assets.py           # escreve tudo
    python tools/generate_tree_assets.py --sheet   # + folha de contato
"""

import argparse
import json
import os
import random

from PIL import Image

SIZE = 16
RES = os.path.join("src", "main", "resources")
A = os.path.join(RES, "assets", "firstcrusade")
D = os.path.join(RES, "data", "firstcrusade")
TEX = os.path.join(A, "textures", "block")

MODID = "firstcrusade"


def ramp(base, spread=0.26):
    r, g, b = base
    out = []
    for k in (1.0 - spread, 1.0 - spread * 0.5, 1.0, 1.0 + spread * 0.45, 1.0 + spread * 0.8):
        out.append(tuple(max(0, min(255, int(round(c * k)))) for c in (r, g, b)) + (255,))
    return out


# ---------------------------------------------------------------------- texturas


def bark(name, base, knots=2, streaks=True):
    """Casca: faixas verticais irregulares. Precisa ladrilhar sem costura em Y e X."""
    rng = random.Random(name)
    pal = ramp(base)
    im = Image.new("RGBA", (SIZE, SIZE), pal[2])
    px = im.load()

    # colunas de tom, com larguras variadas
    x = 0
    while x < SIZE:
        w = rng.randint(1, 3)
        tone = pal[rng.choice((0, 1, 1, 2, 3, 3, 4))]
        for xx in range(x, min(SIZE, x + w)):
            for y in range(SIZE):
                px[xx % SIZE, y] = tone
        x += w

    if streaks:
        # quebras horizontais curtas, para a casca nao virar codigo de barras
        for _ in range(SIZE):
            y = rng.randrange(SIZE)
            x0 = rng.randrange(SIZE)
            n = rng.randint(1, 3)
            tone = pal[rng.choice((0, 1, 3))]
            for i in range(n):
                px[(x0 + i) % SIZE, y] = tone

    for _ in range(knots):
        cx, cy = rng.randrange(SIZE), rng.randrange(SIZE)
        px[cx, cy] = pal[0]
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            px[(cx + dx) % SIZE, (cy + dy) % SIZE] = pal[1]
    return im


def bark_top(name, base):
    """Topo do tronco: aneis concentricos."""
    rng = random.Random(name + "_top")
    pal = ramp(base)
    im = Image.new("RGBA", (SIZE, SIZE), pal[1])
    px = im.load()
    cx = cy = 7.5
    for y in range(SIZE):
        for x in range(SIZE):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5 + rng.uniform(-0.6, 0.6)
            idx = 3 if int(d) % 2 == 0 else 2
            if d > 6.5:
                idx = 1                       # casca na borda
            if d > 7.4:
                idx = 0
            px[x, y] = pal[idx]
    px[7, 7] = pal[0]
    px[8, 7] = pal[0]
    return im


def foliage(name, base, dense=0.92, speck=None):
    """Folhagem.

    A primeira versao sorteava alfa por pixel e depois furava 7 buracos: dava ~35% de
    transparencia espalhada como ruido, e no jogo a copa lia como chuvisco de TV.

    Aqui a massa comeca cheia, o tom varia em manchas coerentes (nao por pixel), e a
    transparencia vem de poucos buracos maiores concentrados perto das bordas — que e
    como uma folha de arvore vanilla e feita: solida com vaos, nao granulada.
    """
    rng = random.Random(name)
    pal = ramp(base, spread=0.30)
    im = Image.new("RGBA", (SIZE, SIZE), pal[2])
    px = im.load()

    # manchas de tom, com envoltorio circular e fechando nas bordas
    for _ in range(26):
        cx, cy = rng.randrange(SIZE), rng.randrange(SIZE)
        r = rng.uniform(1.2, 2.8)
        tone = pal[rng.choice((0, 1, 1, 3, 3, 4))]
        rr = int(r) + 1
        for dy in range(-rr, rr + 1):
            for dx in range(-rr, rr + 1):
                if (dx * dx + dy * dy) ** 0.5 + rng.uniform(-0.45, 0.45) <= r:
                    px[(cx + dx) % SIZE, (cy + dy) % SIZE] = tone

    # vaos: poucos, maiores, e mais provaveis longe do centro do tile
    holes = int((1.0 - dense) * 90)
    for _ in range(holes):
        cx, cy = rng.randrange(SIZE), rng.randrange(SIZE)
        r = rng.uniform(0.9, 1.9)
        rr = int(r) + 1
        for dy in range(-rr, rr + 1):
            for dx in range(-rr, rr + 1):
                if (dx * dx + dy * dy) ** 0.5 + rng.uniform(-0.4, 0.4) <= r:
                    px[(cx + dx) % SIZE, (cy + dy) % SIZE] = (0, 0, 0, 0)

    if speck:
        sp = ramp(speck, spread=0.2)
        for _ in range(12):
            x, y = rng.randrange(SIZE), rng.randrange(SIZE)
            if px[x, y][3]:
                px[x, y] = sp[3]
                if rng.random() < 0.4:
                    px[(x + 1) % SIZE, y] = sp[2]
    return im


def fungal_cap(name, base, speck):
    """Chapeu de torre fungica: massa cheia com poros, nao folha."""
    rng = random.Random(name)
    pal = ramp(base, spread=0.24)
    im = Image.new("RGBA", (SIZE, SIZE), pal[2])
    px = im.load()
    for y in range(SIZE):
        for x in range(SIZE):
            px[x, y] = pal[rng.choice((1, 2, 2, 3))]
    sp = ramp(speck, spread=0.2)
    for _ in range(22):
        cx, cy = rng.randrange(SIZE), rng.randrange(SIZE)
        px[cx, cy] = sp[3]
        if rng.random() < 0.5:
            px[(cx + 1) % SIZE, cy] = sp[2]
    return im


# ---------------------------------------------------------------------- especies

SPECIES = [
    # id,               bark,            top,             folhagem,        speck
    ("imperial_pine",  (86, 66, 48),    (128, 104, 76),  (48, 74, 46),    None),
    ("ash_snag",       (92, 88, 84),    (120, 116, 110), None,            None),
    ("charred_snag",   (46, 42, 40),    (68, 62, 58),    None,            None),
    ("blighted_oak",   (104, 78, 54),   (146, 118, 82),  (62, 96, 52),    (96, 128, 62)),
    ("ork_fungal",     (104, 128, 62),  (150, 168, 96),  (96, 152, 54),   (188, 214, 96)),
    ("venom_bough",    (98, 74, 52),    (140, 112, 78),  (70, 128, 58),   (150, 206, 92)),
    ("orchard",        (116, 88, 56),   (162, 128, 84),  (94, 142, 62),   (206, 176, 96)),
    ("warped_bough",   (86, 62, 92),    (122, 92, 128),  (104, 66, 132),  (170, 112, 196)),

    # ---- fase C: as arvores dos quatro biomas novos -------------------------
    # Ironwood: casca cinza-azulada quase mineral, copa verde-azul escura.
    ("ironwood",       (74, 80, 86),    (108, 114, 120), (40, 74, 70),    (72, 108, 100)),
    # Mesma especie com a resina escorrida e endurecida: so o tronco muda.
    ("resin_ironwood", (118, 88, 44),   (152, 118, 62),  None,            None),
    ("sump_mangrove",  (82, 62, 44),    (116, 92, 66),   (74, 104, 58),   (128, 148, 78)),
    ("toxic_willow",   (96, 84, 60),    (134, 118, 88),  (128, 164, 82),  (188, 214, 112)),
    ("frostnut_pine",  (48, 46, 50),    (74, 72, 78),    (54, 92, 92),    (140, 178, 182)),
    ("salt_thorn",     (146, 140, 126), (176, 170, 156), None,            None),
    # Tronco petrificado: cinza de pedra, sem nenhum tom quente de madeira.
    ("fossilized",     (124, 122, 118), (152, 150, 146), None,            None),

    # ---- fase D: as madeiras das frutiferas ---------------------------------
    ("rationfruit",    (112, 84, 52),   (156, 124, 84),  (86, 132, 58),   (176, 148, 70)),
    ("lumenfruit",     (96, 80, 66),    (134, 116, 96),  (72, 130, 132),  (158, 224, 226)),
    # Sem madeira propria: emprestam tronco de outra especie, so a copa muda.
    ("feed_pod",       None,            None,            (140, 150, 70),  (196, 190, 96)),
    ("venom_pear",     None,            None,            (92, 78, 118),   (162, 118, 186)),
]

# nome do bloco de tronco e de folhagem por especie
BLOCKS = {
    "imperial_pine": ("imperial_pine_log", "imperial_pine_leaves"),
    "ash_snag":      ("ash_snag_log", None),
    "charred_snag":  ("charred_snag_log", None),
    "blighted_oak":  ("blighted_oak_log", "blighted_oak_leaves"),
    "ork_fungal":    ("ork_fungal_stalk", "ork_fungal_cap"),
    "venom_bough":   ("venom_bough_log", "venom_bough_leaves"),
    "orchard":       ("orchard_log", "orchard_leaves"),
    "warped_bough":  ("warped_bough_log", "warped_bough_leaves"),
    "ironwood":      ("ironwood_log", "ironwood_leaves"),
    # Variante de tronco da mesma especie: reaproveita ironwood_leaves, entao nao declara
    # folhagem propria aqui.
    "resin_ironwood": ("resin_ironwood_log", None),
    "sump_mangrove": ("sump_mangrove_log", "sump_mangrove_leaves"),
    "toxic_willow":  ("toxic_willow_log", "toxic_willow_leaves"),
    "frostnut_pine": ("frostnut_pine_log", "frostnut_pine_leaves"),
    "salt_thorn":    ("salt_thorn_log", None),
    "fossilized":    ("fossilized_trunk", None),
    "rationfruit":   ("rationfruit_log", "rationfruit_leaves"),
    "lumenfruit":    ("lumenfruit_log", "lumenfruit_leaves"),
    "feed_pod":      (None, "feed_pod_leaves"),
    "venom_pear":    (None, "venom_pear_leaves"),
}

NAMES_EN = {
    "imperial_pine_log": "Imperial Pine Log",
    "imperial_pine_leaves": "Imperial Pine Needles",
    "ash_snag_log": "Ash Snag",
    "charred_snag_log": "Charred Snag",
    "blighted_oak_log": "Blighted Oak Log",
    "blighted_oak_leaves": "Blighted Oak Leaves",
    "ork_fungal_stalk": "Ork Fungal Stalk",
    "ork_fungal_cap": "Ork Fungal Cap",
    "venom_bough_log": "Venom Bough Log",
    "venom_bough_leaves": "Venom Bough Leaves",
    "orchard_log": "Orchard Log",
    "orchard_leaves": "Orchard Leaves",
    "warped_bough_log": "Warped Bough Log",
    "warped_bough_leaves": "Warped Bough Leaves",
    "ironwood_log": "Ironwood Log",
    "ironwood_leaves": "Ironwood Leaves",
    "resin_ironwood_log": "Resin Ironwood Log",
    "sump_mangrove_log": "Sump Mangrove Log",
    "sump_mangrove_leaves": "Sump Mangrove Leaves",
    "toxic_willow_log": "Toxic Willow Log",
    "toxic_willow_leaves": "Toxic Willow Leaves",
    "frostnut_pine_log": "Frostnut Pine Log",
    "frostnut_pine_leaves": "Frostnut Pine Needles",
    "salt_thorn_log": "Salt Thorn",
    "fossilized_trunk": "Fossilized Trunk",
    "rationfruit_log": "Rationfruit Log",
    "rationfruit_leaves": "Rationfruit Leaves",
    "lumenfruit_log": "Lumenfruit Log",
    "lumenfruit_leaves": "Lumenfruit Leaves",
    "feed_pod_leaves": "Feed Pod Leaves",
    "venom_pear_leaves": "Venom Pear Leaves",
}

NAMES_PT = {
    "imperial_pine_log": "Tronco de Pinheiro Imperial",
    "imperial_pine_leaves": "Agulhas de Pinheiro Imperial",
    "ash_snag_log": "Tronco Morto de Cinzas",
    "charred_snag_log": "Tronco Carbonizado",
    "blighted_oak_log": "Tronco de Carvalho Crestado",
    "blighted_oak_leaves": "Folhas de Carvalho Crestado",
    "ork_fungal_stalk": "Caule Fungico Ork",
    "ork_fungal_cap": "Chapeu Fungico Ork",
    "venom_bough_log": "Tronco de Ramo Venenoso",
    "venom_bough_leaves": "Folhas de Ramo Venenoso",
    "orchard_log": "Tronco de Pomar",
    "orchard_leaves": "Folhas de Pomar",
    "warped_bough_log": "Tronco de Ramo Deturpado",
    "warped_bough_leaves": "Folhas de Ramo Deturpado",
    "ironwood_log": "Tronco de Ferrofuste",
    "ironwood_leaves": "Folhas de Ferrofuste",
    "resin_ironwood_log": "Tronco de Ferrofuste Resinoso",
    "sump_mangrove_log": "Tronco de Mangue de Sump",
    "sump_mangrove_leaves": "Folhas de Mangue de Sump",
    "toxic_willow_log": "Tronco de Salgueiro Toxico",
    "toxic_willow_leaves": "Folhas de Salgueiro Toxico",
    "frostnut_pine_log": "Tronco de Pinheiro Gelanoz",
    "frostnut_pine_leaves": "Agulhas de Pinheiro Gelanoz",
    "salt_thorn_log": "Espinho de Sal",
    "fossilized_trunk": "Tronco Fossilizado",
    "rationfruit_log": "Tronco de Fruta-Racao",
    "rationfruit_leaves": "Folhas de Fruta-Racao",
    "lumenfruit_log": "Tronco de Fruta-Lumen",
    "lumenfruit_leaves": "Folhas de Fruta-Lumen",
    "feed_pod_leaves": "Folhas de Vagem de Racao",
    "venom_pear_leaves": "Folhas de Pera Venenosa",
}


# ------------------------------------------------------------------------ json


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def log_assets(block):
    """Blockstate com os tres eixos + modelos de bloco e item."""
    model = "%s:block/%s" % (MODID, block)
    write_json(os.path.join(A, "blockstates", block + ".json"), {
        "variants": {
            "axis=y": {"model": model},
            "axis=z": {"model": model, "x": 90},
            "axis=x": {"model": model, "x": 90, "y": 90},
        }
    })
    write_json(os.path.join(A, "models", "block", block + ".json"), {
        "parent": "minecraft:block/cube_column",
        "textures": {
            "end": "%s:block/%s_top" % (MODID, block),
            "side": "%s:block/%s" % (MODID, block),
        },
    })
    write_json(os.path.join(A, "models", "item", block + ".json"), {"parent": model})
    # tronco derruba a si mesmo, sem condicao
    write_json(os.path.join(D, "loot_tables", "blocks", block + ".json"), {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "bonus_rolls": 0,
            "entries": [{"type": "minecraft:item", "name": "%s:%s" % (MODID, block)}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
    })


def leaves_assets(block):
    model = "%s:block/%s" % (MODID, block)
    write_json(os.path.join(A, "blockstates", block + ".json"), {
        "variants": {"": {"model": model}}
    })
    # render_type explicito: o Forge so trata folhas vanilla como recortadas por
    # caso especial. Sem esta linha a folhagem do mod renderiza solida, os furos da
    # textura viram preto e a copa fica um cubo macico.
    write_json(os.path.join(A, "models", "block", block + ".json"), {
        "parent": "minecraft:block/leaves",
        "render_type": "minecraft:cutout_mipped",
        "textures": {"all": "%s:block/%s" % (MODID, block)},
    })
    write_json(os.path.join(A, "models", "item", block + ".json"), {"parent": model})
    # Sem mudas no mod, entao a folhagem so cai com tesoura ou toque suave; caso
    # contrario da gravetos, como as folhas vanilla.
    write_json(os.path.join(D, "loot_tables", "blocks", block + ".json"), {
        "type": "minecraft:block",
        "pools": [
            {
                "rolls": 1,
                "bonus_rolls": 0,
                "entries": [{
                    "type": "minecraft:alternatives",
                    "children": [{
                        "type": "minecraft:item",
                        "name": "%s:%s" % (MODID, block),
                        "conditions": [{
                            "condition": "minecraft:any_of",
                            "terms": [
                                {"condition": "minecraft:match_tool",
                                 "predicate": {"items": ["minecraft:shears"]}},
                                {"condition": "minecraft:match_tool",
                                 "predicate": {"enchantments": [
                                     {"enchantment": "minecraft:silk_touch",
                                      "levels": {"min": 1}}]}},
                            ],
                        }],
                    }],
                }],
            },
            {
                "rolls": 1,
                "bonus_rolls": 0,
                "conditions": [{
                    "condition": "minecraft:inverted",
                    "term": {
                        "condition": "minecraft:any_of",
                        "terms": [
                            {"condition": "minecraft:match_tool",
                             "predicate": {"items": ["minecraft:shears"]}},
                            {"condition": "minecraft:match_tool",
                             "predicate": {"enchantments": [
                                 {"enchantment": "minecraft:silk_touch",
                                  "levels": {"min": 1}}]}},
                        ],
                    },
                }],
                "entries": [{
                    "type": "minecraft:item",
                    "name": "minecraft:stick",
                    "functions": [
                        {"function": "minecraft:set_count",
                         "count": {"type": "minecraft:uniform", "min": 1, "max": 2}},
                        {"function": "minecraft:explosion_decay"},
                    ],
                    "conditions": [{
                        "condition": "minecraft:table_bonus",
                        "enchantment": "minecraft:fortune",
                        "chances": [0.02, 0.022222223, 0.025, 0.033333335, 0.1],
                    }],
                }],
            },
        ],
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


# ------------------------------------------------------------------------ main


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--sheet", action="store_true")
    args = ap.parse_args()

    os.makedirs(TEX, exist_ok=True)
    written = []

    for sid, barkc, topc, folc, speck in SPECIES:
        log_block, leaf_block = BLOCKS[sid]

        # Especie que empresta o tronco de outra nao gera madeira propria.
        if log_block:
            bark(log_block, barkc).save(os.path.join(TEX, log_block + ".png"))
            bark_top(log_block, topc).save(os.path.join(TEX, log_block + "_top.png"))
            written += [log_block, log_block + "_top"]
            log_assets(log_block)

        if leaf_block:
            if sid == "ork_fungal":
                fungal_cap(leaf_block, folc, speck).save(os.path.join(TEX, leaf_block + ".png"))
            else:
                # Coniferas ficam mais rendadas (agulha deixa ver o ceu); o ferrofuste
                # e o mais fechado do mod, que e o que faz o chao dele ser escuro.
                dense = {"imperial_pine": 0.80, "frostnut_pine": 0.78,
                         "ironwood": 0.90, "toxic_willow": 0.82}.get(sid, 0.86)
                foliage(leaf_block, folc, dense=dense, speck=speck).save(
                    os.path.join(TEX, leaf_block + ".png"))
            written.append(leaf_block)
            leaves_assets(leaf_block)

    en = {"block.firstcrusade." + k: v for k, v in NAMES_EN.items()}
    pt = {"block.firstcrusade." + k: v for k, v in NAMES_PT.items()}
    a1 = merge_lang(os.path.join(A, "lang", "en_us.json"), en)
    a2 = merge_lang(os.path.join(A, "lang", "pt_br.json"), pt)

    print("texturas: %d | blocos: %d | lang en+%d pt+%d" % (len(written), len(NAMES_EN), a1, a2))

    if args.sheet:
        scale, pad, cols = 6, 4, 6
        rows = (len(written) + cols - 1) // cols
        cell = SIZE * scale + pad
        sheet = Image.new("RGBA", (cols * cell + pad, rows * cell + pad), (34, 34, 38, 255))
        for i, n in enumerate(written):
            im = Image.open(os.path.join(TEX, n + ".png")).convert("RGBA")
            im = im.resize((SIZE * scale, SIZE * scale), Image.NEAREST)
            bg = Image.new("RGBA", im.size, (52, 52, 58, 255))
            bg.alpha_composite(im)
            sheet.paste(bg, (pad + (i % cols) * cell, pad + (i // cols) * cell))
        out = os.path.join("tools", "tree_texture_sheet.png")
        sheet.save(out)
        print("folha:", out)


if __name__ == "__main__":
    main()
