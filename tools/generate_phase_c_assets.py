#!/usr/bin/env python3
"""
Assets de bloco do conteudo da Fase C (os quatro biomas novos).

O que este script possui
------------------------
  assets/firstcrusade/blockstates/<bloco>.json
  assets/firstcrusade/models/block/<bloco>.json
  assets/firstcrusade/models/item/<bloco>.json
  data/firstcrusade/loot_tables/blocks/<bloco>.json
  assets/firstcrusade/textures/block/{sump_mud,salt_crust}.png
  as chaves de idioma dos blocos daqui (merge, nunca sobrescreve chave existente)
  as tags de flora do mod (merge aditivo, ordem preservada)

O que este script NAO possui
----------------------------
  * texturas de planta -> generate_flora_textures.py
  * texturas e JSONs de arvore -> generate_tree_assets.py
  * biomas e distribuicao -> generate_overworld_biomes.py
  * configured/placed features -> generate_worldgen_features.py
  * minecraft:logs / leaves / mineable/* -> datagen (FCBlockTagsProvider)

Ou seja: um caminho de arquivo tem exatamente um dono. E por isso que a lista de blocos
abaixo esta escrita a mao em vez de varrer FCFlora.java — o script nao deve tocar em
nada que outro dono ja escreve.

Uso:
    python tools/generate_phase_c_assets.py
"""

import json
import os
import random

from PIL import Image

SIZE = 16
RES = os.path.join("src", "main", "resources")
A = os.path.join(RES, "assets", "firstcrusade")
D = os.path.join(RES, "data", "firstcrusade")
TEX = os.path.join(A, "textures", "block")
TAGS = os.path.join(D, "tags", "blocks")

MOD = "firstcrusade"


# ----------------------------------------------------------------- os blocos novos
#
# forma: como o bloco se apoia no mundo e, por consequencia, qual modelo ele usa.
#   cross   planta de um bloco (modelo cruz)
#   tall    planta de dois blocos (duas metades)
#   lichen  face plana colada em parede/teto/chao
#   carpet  tapete fino
#   detail  detalhe raso de chao (quad de tile inteiro)
#   soil    bloco solido de terreno

PLANTS = [
    # ---- ironwood forest ---------------------------------------------------
    ("iron_fern",       "cross",  "Iron Fern",           "Samambaia de Ferro"),
    ("resin_moss",      "lichen", "Resin Moss",          "Musgo de Resina"),
    ("shelf_fungus",    "lichen", "Shelf Fungus",        "Fungo de Prateleira"),
    ("needle_litter",   "carpet", "Needle Litter",       "Manta de Agulhas"),

    # ---- sump marsh --------------------------------------------------------
    ("marsh_grass",     "cross",  "Marsh Grass",         "Grama de Pantano"),
    ("bog_fungus",      "cross",  "Bog Fungus",          "Fungo de Turfeira"),
    ("gas_bladder",     "cross",  "Gas Bladder",         "Bolsa de Gas"),
    ("mud_lichen",      "lichen", "Mud Lichen",          "Liquen de Lama"),
    ("tall_marsh_reed", "tall",   "Tall Marsh Reed",     "Junco Alto de Pantano"),

    # ---- ossuary tundra ----------------------------------------------------
    ("frost_lichen",    "lichen", "Frost Lichen",        "Liquen de Geada"),
    ("snow_scrub",      "cross",  "Snow Scrub",          "Arbusto de Neve"),
    ("tundra_moss",     "carpet", "Tundra Moss",         "Musgo de Tundra"),

    # ---- salt waste --------------------------------------------------------
    ("brine_grass",     "cross",  "Brine Grass",         "Grama de Salmoura"),
    ("brine_thistle",   "cross",  "Brine Thistle",       "Cardo de Salmoura"),
    ("salt_flake",      "detail", "Salt Flakes",         "Escamas de Sal"),

    # ---- solos -------------------------------------------------------------
    ("sump_mud",        "soil",   "Sump Mud",            "Lama de Sump"),
    ("salt_crust",      "soil",   "Salt Crust",          "Crosta de Sal"),
]

# A que tag de conjunto cada bloco pertence. Um bloco pode nao estar em nenhuma
# subcategoria (os solos), mas planta alguma sempre entra em flora.
TAG_MEMBERSHIP = {
    "flora": ["iron_fern", "resin_moss", "shelf_fungus", "needle_litter",
              "marsh_grass", "bog_fungus", "gas_bladder", "mud_lichen", "tall_marsh_reed",
              "frost_lichen", "snow_scrub", "tundra_moss",
              "brine_grass", "brine_thistle", "salt_flake"],
    "flora_grass": ["iron_fern", "marsh_grass", "snow_scrub", "brine_grass",
                    "tall_marsh_reed"],
    "flora_flower": ["brine_thistle"],
    "flora_fungus": ["bog_fungus", "shelf_fungus"],
    "flora_lichen": ["resin_moss", "mud_lichen", "frost_lichen"],
    "flora_detritus": ["needle_litter", "tundra_moss", "salt_flake"],
    "flora_tree": ["ironwood_log", "ironwood_leaves", "resin_ironwood_log",
                   "sump_mangrove_log", "sump_mangrove_leaves",
                   "toxic_willow_log", "toxic_willow_leaves",
                   "frostnut_pine_log", "frostnut_pine_leaves",
                   "salt_thorn_log", "fossilized_trunk"],
}


# --------------------------------------------------------------------- utilidades


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def ramp(base, spread=0.24):
    r, g, b = base
    out = []
    for k in (1.0 - spread, 1.0 - spread * 0.5, 1.0, 1.0 + spread * 0.45, 1.0 + spread * 0.8):
        out.append(tuple(max(0, min(255, int(round(c * k)))) for c in (r, g, b)) + (255,))
    return out


# ------------------------------------------------------------------- texturas solo


def soil_texture(name, base, grain=0.55, cracks=0):
    """Tile de terreno: manchas coerentes, nao ruido por pixel.

    Sorteio por pixel da o mesmo problema que as folhas tinham na Fase 3: de longe o
    chao vira chuvisco. Aqui a cor vem de manchas circulares sobrepostas, que e o que
    faz um tile de terra vanilla parecer material e nao estatica.
    """
    rng = random.Random(name)
    pal = ramp(base)
    im = Image.new("RGBA", (SIZE, SIZE), pal[2])
    px = im.load()

    for _ in range(34):
        cx, cy = rng.randrange(SIZE), rng.randrange(SIZE)
        r = rng.uniform(1.0, 3.0)
        tone = pal[rng.choice((0, 1, 1, 2, 3, 3, 4))]
        rr = int(r) + 1
        for dy in range(-rr, rr + 1):
            for dx in range(-rr, rr + 1):
                if (dx * dx + dy * dy) ** 0.5 + rng.uniform(-0.4, 0.4) <= r:
                    px[(cx + dx) % SIZE, (cy + dy) % SIZE] = tone

    for _ in range(int(grain * 40)):
        px[rng.randrange(SIZE), rng.randrange(SIZE)] = pal[rng.choice((0, 4))]

    # Fendas: e o que separa uma crosta seca de um bloco de areia clara.
    for _ in range(cracks):
        x, y = rng.randrange(SIZE), rng.randrange(SIZE)
        for _ in range(rng.randint(4, 9)):
            px[x % SIZE, y % SIZE] = pal[0]
            if rng.random() < 0.5:
                x += rng.choice((-1, 1))
            else:
                y += rng.choice((-1, 1))
    return im


# ---------------------------------------------------------------------- assets


def plant_assets(block, form):
    """Blockstate + modelo(s) + modelo de item + loot, por forma."""
    model = "%s:block/%s" % (MOD, block)
    tex = "%s:block/%s" % (MOD, block)

    if form == "cross":
        write_json(os.path.join(A, "blockstates", block + ".json"),
                   {"variants": {"": {"model": model}}})
        write_json(os.path.join(A, "models", "block", block + ".json"), {
            "parent": "minecraft:block/cross",
            "render_type": "minecraft:cutout",
            "textures": {"cross": tex},
        })
        item_layer = tex

    elif form == "tall":
        write_json(os.path.join(A, "blockstates", block + ".json"), {
            "variants": {
                "half=lower": {"model": model + "_bottom"},
                "half=upper": {"model": model + "_top"},
            }
        })
        for half in ("bottom", "top"):
            write_json(os.path.join(A, "models", "block", "%s_%s.json" % (block, half)), {
                "parent": "minecraft:block/cross",
                "render_type": "minecraft:cutout",
                "textures": {"cross": "%s_%s" % (tex, half)},
            })
        item_layer = tex + "_top"

    elif form == "lichen":
        # Multipart nas seis faces, como o glow_lichen vanilla.
        rotations = [("north", {}), ("east", {"y": 90}), ("south", {"y": 180}),
                     ("west", {"y": 270}), ("up", {"x": 270}), ("down", {"x": 90})]
        parts = []
        for face, extra in rotations:
            apply = {"model": model, "uvlock": True}
            apply.update(extra)
            parts.append({"when": {face: "true"}, "apply": apply})
        write_json(os.path.join(A, "blockstates", block + ".json"), {"multipart": parts})
        write_json(os.path.join(A, "models", "block", block + ".json"), {
            "parent": "%s:block/flora_lichen_face" % MOD,
            "textures": {"lichen": tex},
        })
        item_layer = tex

    elif form == "carpet":
        write_json(os.path.join(A, "blockstates", block + ".json"),
                   {"variants": {"": {"model": model}}})
        write_json(os.path.join(A, "models", "block", block + ".json"), {
            "parent": "minecraft:block/carpet",
            "textures": {"wool": tex},
        })
        item_layer = tex

    elif form == "detail":
        write_json(os.path.join(A, "blockstates", block + ".json"),
                   {"variants": {"": {"model": model}}})
        write_json(os.path.join(A, "models", "block", block + ".json"), {
            "parent": "%s:block/flora_ground_detail" % MOD,
            "textures": {"detail": tex},
        })
        item_layer = tex

    elif form == "soil":
        write_json(os.path.join(A, "blockstates", block + ".json"),
                   {"variants": {"": {"model": model}}})
        write_json(os.path.join(A, "models", "block", block + ".json"), {
            "parent": "minecraft:block/cube_all",
            "textures": {"all": tex},
        })
        # Solo e cubo: o item herda o proprio modelo de bloco em vez de virar sprite.
        write_json(os.path.join(A, "models", "item", block + ".json"), {"parent": model})
        item_layer = None

    else:
        raise ValueError("forma desconhecida: " + form)

    if item_layer:
        write_json(os.path.join(A, "models", "item", block + ".json"), {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": item_layer},
        })

    # ---- loot -----------------------------------------------------------------
    conditions = [{"condition": "minecraft:survives_explosion"}]
    if form == "tall":
        # So a metade de baixo dropa; sem isto uma planta alta rende dois itens.
        conditions.append({
            "condition": "minecraft:block_state_property",
            "block": "%s:%s" % (MOD, block),
            "properties": {"half": "lower"},
        })

    write_json(os.path.join(D, "loot_tables", "blocks", block + ".json"), {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "bonus_rolls": 0,
            "entries": [{"type": "minecraft:item", "name": "%s:%s" % (MOD, block)}],
            "conditions": conditions,
        }],
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


def merge_tag(name, blocks):
    """Acrescenta ao fim, sem reordenar. A ordem de uma tag nao muda comportamento,
    mas preserva-la mantem o diff legivel."""
    path = os.path.join(TAGS, name + ".json")
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    values = data.setdefault("values", [])
    added = 0
    for block in blocks:
        entry = "%s:%s" % (MOD, block)
        if entry not in values:
            values.append(entry)
            added += 1
    write_json(path, data)
    return added


# ------------------------------------------------------------------------- main


def main():
    os.makedirs(TEX, exist_ok=True)

    # Solos: textura propria (as plantas ja vem de generate_flora_textures.py).
    soil_texture("sump_mud", (56, 48, 40), grain=0.7, cracks=0).save(
        os.path.join(TEX, "sump_mud.png"))
    soil_texture("salt_crust", (212, 210, 198), grain=0.35, cracks=5).save(
        os.path.join(TEX, "salt_crust.png"))

    for block, form, _, _ in PLANTS:
        plant_assets(block, form)

    en = {"block.firstcrusade." + b: n for b, _, n, _ in PLANTS}
    pt = {"block.firstcrusade." + b: n for b, _, _, n in PLANTS}
    a1 = merge_lang(os.path.join(A, "lang", "en_us.json"), en)
    a2 = merge_lang(os.path.join(A, "lang", "pt_br.json"), pt)

    tag_total = 0
    for tag_name, blocks in TAG_MEMBERSHIP.items():
        tag_total += merge_tag(tag_name, blocks)

    print("blocos com assets: %d (2 solos + %d plantas)" % (len(PLANTS), len(PLANTS) - 2))
    print("texturas de solo : 2")
    print("lang             : en+%d pt+%d" % (a1, a2))
    print("entradas de tag  : +%d em %d tags" % (tag_total, len(TAG_MEMBERSHIP)))


if __name__ == "__main__":
    main()
