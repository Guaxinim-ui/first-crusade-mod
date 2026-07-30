#!/usr/bin/env python3
"""
Previa aproximada de como uma paleta fica no chao, sem abrir o jogo.

Nao e um render de verdade: e a textura de cruz estampada sobre um fundo de bloco de
grama, na densidade que o decorador usa, em escala 1:1 e depois ampliada. Serve para
uma unica pergunta — a vegetacao ainda le como barra solida? — que e exatamente o que
uma folha de contato com fundo xadrez nao responde.

Uso:
    python tools/preview_flora_field.py IMPERIAL ORK FORGE
"""

import os
import random
import sys

from PIL import Image

TEX = os.path.join("src", "main", "resources", "assets", "firstcrusade", "textures", "block")

# Espelha as tabelas de FloraPalette (peso relativo entre as plantas de cada paleta).
PALETTES = {
    "NEUTRAL_DARK": [("dark_fern", 24), ("withered_scrub", 20), ("imperial_grass", 18),
                     ("small_roots", 14), ("scattered_twigs", 12), ("tall_imperial_grass", 4)],
    "IMPERIAL": [("imperial_grass", 30), ("withered_scrub", 18), ("dark_fern", 14),
                 ("roadside_thistle", 10), ("aquila_bloom", 5), ("memorial_bloom", 4),
                 ("ossuary_lily", 3), ("tall_imperial_grass", 8), ("scattered_twigs", 8)],
    "FORGE": [("ash_grass", 26), ("soot_grass", 22), ("burnt_stubble", 14),
              ("promethium_weed", 12), ("chem_bloom", 6), ("tall_ash_grass", 7),
              ("rubble_pebbles", 10)],
    "ORK": [("ork_fungus", 30), ("squig_grass", 22), ("ork_spore_cap", 14),
            ("trampled_grass", 16), ("oil_stained_grass", 12), ("tall_squig_grass", 9),
            ("scattered_twigs", 8)],
    "AGRI": [("field_grass", 32), ("agri_clover", 22), ("pale_field_flower", 14),
             ("irrigation_reed", 12), ("tall_imperial_grass", 10)],
    "CHAOS": [("writhing_grass", 26), ("thornweed", 22), ("corrupted_bloom", 14),
              ("pulsing_root", 12), ("bone_fragments", 8)],
    "DEATH_WORLD": [("venom_frond", 24), ("spine_bush", 20), ("toxic_bloom", 12),
                    ("fanged_sprout", 12), ("mire_reed", 14), ("small_roots", 10)],
    "UNDERHIVE": [("pallid_fungus", 26), ("glow_cap", 16), ("sludge_algae", 16),
                  ("bone_fragments", 8), ("small_roots", 8)],
}

GROUND = {
    "NEUTRAL_DARK": (74, 92, 54), "IMPERIAL": (86, 104, 60), "FORGE": (92, 88, 82),
    "ORK": (78, 96, 52), "AGRI": (98, 118, 62), "CHAOS": (78, 62, 66),
    "DEATH_WORLD": (66, 88, 56), "UNDERHIVE": (74, 68, 58),
}

TILES_X, TILES_Y = 10, 6
CELL = 16
SCALE = 5


def load(name):
    p = os.path.join(TEX, name + ".png")
    if not os.path.exists(p):
        p = os.path.join(TEX, name + "_bottom.png")   # plantas altas
    return Image.open(p).convert("RGBA")


def render(pal_name):
    entries = PALETTES[pal_name]
    rng = random.Random(pal_name)
    total = sum(w for _, w in entries)

    w, h = TILES_X * CELL, TILES_Y * CELL
    base = GROUND[pal_name]
    img = Image.new("RGBA", (w, h), base + (255,))

    # granulado do chao, senao o fundo chapado engana a leitura
    px = img.load()
    for y in range(h):
        for x in range(w):
            d = rng.randint(-12, 12)
            px[x, y] = (max(0, min(255, base[0] + d)),
                        max(0, min(255, base[1] + d)),
                        max(0, min(255, base[2] + d)), 255)

    cache = {}
    for ty in range(TILES_Y):
        for tx in range(TILES_X):
            if rng.random() < 0.18:          # clareiras
                continue
            roll = rng.randrange(total)
            for name, weight in entries:
                roll -= weight
                if roll < 0:
                    break
            if name not in cache:
                cache[name] = load(name)
            img.alpha_composite(cache[name], (tx * CELL, ty * CELL))

    return img.resize((w * SCALE, h * SCALE), Image.NEAREST)


def main():
    wanted = sys.argv[1:] or ["IMPERIAL", "ORK", "FORGE", "AGRI"]
    tiles = [(n, render(n)) for n in wanted if n in PALETTES]

    pad = 10
    cw = tiles[0][1].width
    ch = tiles[0][1].height
    cols = 2
    rows = (len(tiles) + cols - 1) // cols

    sheet = Image.new("RGBA", (cols * cw + pad * (cols + 1), rows * ch + pad * (rows + 1)),
                      (26, 26, 30, 255))
    for i, (_, im) in enumerate(tiles):
        x = pad + (i % cols) * (cw + pad)
        y = pad + (i // cols) * (ch + pad)
        sheet.paste(im, (x, y))

    out = os.path.join("tools", "flora_field_preview.png")
    sheet.save(out)
    print("previa:", out, "->", ", ".join(n for n, _ in tiles))


if __name__ == "__main__":
    main()
