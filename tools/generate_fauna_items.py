#!/usr/bin/env python3
"""Sprites 16x16 e traducoes dos itens da fauna nova.

Um dono por arquivo. Este script possui:

  assets/firstcrusade/textures/item/<item>.png   (os 15 itens listados em ITEMS)
  as chaves de lang que ele injeta em en_us.json e pt_br.json

Os MODELOS de item saem do datagen (FCItemModelProvider.flatItem), nao daqui: o datagen ja
e o dono de src/generated e apaga o que nao for declarado la.

Regra da arte: nada aqui compete com o Blockbench. Sao sprites de inventario de 16x16
desenhadas por forma geometrica simples, no mesmo espirito das da Fase E — legiveis na
barra rapida, e nada mais. Se o dono quiser pintar a mao, apagar o PNG e desenhar por cima
funciona; o script so reescreve quando roda.

Uso:
    python tools/generate_fauna_items.py
"""

import json
import os
import random

from PIL import Image

A = os.path.join("src", "main", "resources", "assets", "firstcrusade")
TEX_ITEM = os.path.join(A, "textures", "item")
LANG = os.path.join(A, "lang")


def clamp(value):
    return max(0, min(255, int(value)))


def shade(colour, factor):
    return tuple(clamp(c * factor) for c in colour)


# ------------------------------------------------------------------------ formas


def draw_hide(px, rng, base, edge):
    """Pele esticada: um losango de cantos arredondados."""
    for y in range(16):
        for x in range(16):
            dx = abs(x - 7.5) / 6.8
            dy = abs(y - 7.5) / 7.2
            if dx ** 1.6 + dy ** 1.6 > 1.0:
                continue
            border = dx ** 1.6 + dy ** 1.6 > 0.72
            tone = edge if border else base
            px[x, y] = shade(tone, 1.0 + rng.uniform(-0.06, 0.06)) + (255,)


def draw_pelt(px, rng, base, edge):
    """Pele com pelo: o losango da pele, mais tufos irregulares na borda."""
    draw_hide(px, rng, base, edge)

    for _ in range(26):
        x = rng.randint(1, 14)
        y = rng.randint(1, 14)
        if px[x, y][3]:
            px[x, y] = shade(base, rng.uniform(0.78, 1.18)) + (255,)

    for x in range(2, 14, 2):
        for y in (1, 14):
            if not px[x, y][3] and px[x, min(max(y + (1 if y < 8 else -1), 0), 15)][3]:
                px[x, y] = shade(edge, 0.9) + (255,)


def draw_fang(px, rng, base, edge):
    """Presa: um cone curvo, ponta para cima."""
    for i in range(48):
        t = i / 47.0
        cx = 5.0 + 5.4 * (t ** 1.5)
        cy = 14.2 - 11.8 * t
        radius = 2.6 * (1.0 - t) + 0.5

        for x in range(16):
            for y in range(16):
                if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= radius * radius:
                    lit = (x + 0.5 - cx) + (y + 0.5 - cy) < -0.4
                    tone = shade(base, 1.18) if lit else base
                    px[x, y] = shade(tone, 1.0 + rng.uniform(-0.04, 0.04)) + (255,)

    outline(px, edge)


def draw_horn(px, rng, base, edge):
    """Chifre: mais grosso e mais curvo que a presa, e com aneis."""
    for i in range(52):
        t = i / 51.0
        cx = 3.6 + 8.2 * (t ** 1.3)
        cy = 14.6 - 10.4 * t
        radius = 3.2 * (1.0 - t) + 0.6

        for x in range(16):
            for y in range(16):
                if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= radius * radius:
                    ring = (i % 9) < 2
                    tone = shade(base, 0.82) if ring else base
                    px[x, y] = shade(tone, 1.0 + rng.uniform(-0.05, 0.05)) + (255,)

    outline(px, edge)


def draw_scale(px, rng, base, edge):
    """Escamas: tres gotas sobrepostas."""
    for cx, cy, radius in ((5.5, 6.0, 3.6), (10.0, 7.5, 3.4), (7.5, 11.0, 3.2)):
        for x in range(16):
            for y in range(16):
                dx = (x + 0.5 - cx) / radius
                dy = (y + 0.5 - cy) / (radius * 1.15)
                if dx * dx + dy * dy > 1.0:
                    continue
                border = dx * dx + dy * dy > 0.66
                tone = edge if border else base
                px[x, y] = shade(tone, 1.0 + rng.uniform(-0.05, 0.05)) + (255,)


def draw_quill(px, rng, base, edge):
    """Quills: tres espinhos em leque."""
    for angle, offset in ((-0.35, -3), (0.0, 0), (0.35, 3)):
        for i in range(30):
            t = i / 29.0
            cx = 7.5 + offset * t + angle * 10.0 * t
            cy = 14.0 - 12.0 * t
            radius = 1.5 * (1.0 - t) + 0.4

            for x in range(16):
                for y in range(16):
                    if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= radius * radius:
                        px[x, y] = shade(base, 1.0 + rng.uniform(-0.07, 0.07)) + (255,)

    outline(px, edge)


def draw_meat(px, rng, base, edge, bone=None):
    """Naco de carne, com osso saindo de um lado."""
    for y in range(16):
        for x in range(16):
            dx = (x - 8.2) / 5.8
            dy = (y - 8.6) / 5.4
            if dx * dx + dy * dy > 1.0:
                continue
            border = dx * dx + dy * dy > 0.7
            tone = edge if border else base
            px[x, y] = shade(tone, 1.0 + rng.uniform(-0.08, 0.08)) + (255,)

    if bone:
        for x in range(1, 6):
            for y in (7, 8):
                px[x, y] = bone + (255,)
        px[1, 6] = bone + (255,)
        px[1, 9] = bone + (255,)


def draw_carapace(px, rng, base, edge, rib):
    """Carapaca pesada: hexagono achatado com nervura central."""
    for y in range(16):
        for x in range(16):
            dx = abs(x - 7.5) / 6.8
            dy = abs(y - 7.5) / 6.2
            if dx + dy * 0.62 > 1.0 or dy > 1.0:
                continue
            border = dx + dy * 0.62 > 0.76
            tone = edge if border else base
            px[x, y] = shade(tone, 1.0 + rng.uniform(-0.07, 0.07)) + (255,)

    for y in range(3, 13):
        if px[7, y][3]:
            px[7, y] = rib + (255,)
        if px[8, y][3]:
            px[8, y] = shade(rib, 0.88) + (255,)


def draw_stinger(px, rng, base, edge, glow):
    """Ferrao: uma agulha curva com a ponta brilhando."""
    for i in range(44):
        t = i / 43.0
        cx = 4.0 + 7.6 * (t ** 1.6)
        cy = 13.8 - 11.4 * t
        radius = 2.4 * (1.0 - t ** 0.8) + 0.4

        for x in range(16):
            for y in range(16):
                if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= radius * radius:
                    tone = glow if t > 0.78 else base
                    px[x, y] = shade(tone, 1.0 + rng.uniform(-0.05, 0.05)) + (255,)

    outline(px, edge)


def draw_trophy(px, rng, base, edge, accent):
    """Trofeu: uma placa com um cranio estilizado."""
    # A placa.
    for y in range(3, 15):
        for x in range(2, 14):
            border = x in (2, 13) or y in (3, 14)
            tone = edge if border else base
            px[x, y] = shade(tone, 1.0 + rng.uniform(-0.05, 0.05)) + (255,)

    # O cranio: uma cupula com duas orbitas e uma mandibula.
    for y in range(5, 10):
        for x in range(5, 11):
            if y == 5 and x in (5, 10):
                continue
            px[x, y] = accent + (255,)

    for x in (6, 9):
        px[x, 7] = shade(base, 0.35) + (255,)
        px[x, 8] = shade(base, 0.35) + (255,)

    for x in range(6, 10):
        px[x, 10] = shade(accent, 0.82) + (255,)
    px[7, 11] = shade(accent, 0.82) + (255,)
    px[8, 11] = shade(accent, 0.82) + (255,)


def outline(px, edge):
    """Contorno escuro em volta do que ja foi desenhado."""
    filled = {(x, y) for x in range(16) for y in range(16) if px[x, y][3]}

    for (x, y) in filled:
        if any((x + dx, y + dy) not in filled
               for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            px[x, y] = edge + (255,)


# ------------------------------------------------------------------------ os itens
#
# (arquivo, funcao de desenho, cores, nome en, nome pt)

ITEMS = [
    ("beast_hide", draw_hide, dict(base=(122, 92, 62), edge=(78, 58, 38)),
     "Beast Hide", "Couro de Fera"),
    ("thick_pelt", draw_pelt, dict(base=(150, 152, 156), edge=(92, 94, 100)),
     "Thick Pelt", "Pele Espessa"),
    ("beast_fang", draw_fang, dict(base=(224, 220, 200), edge=(120, 112, 92)),
     "Beast Fang", "Presa de Fera"),
    ("duskhorn_horn", draw_horn, dict(base=(196, 172, 122), edge=(104, 88, 58)),
     "Duskhorn Horn", "Chifre de Duskhorn"),
    ("serpent_scale", draw_scale, dict(base=(122, 148, 74), edge=(62, 82, 40)),
     "Serpent Scale", "Escama de Serpente"),
    ("knarloc_quill", draw_quill, dict(base=(186, 160, 106), edge=(96, 78, 46)),
     "Knarloc Quill", "Espinho de Knarloc"),
    ("game_meat", draw_meat, dict(base=(176, 74, 66), edge=(112, 42, 40),
                                  bone=(232, 226, 206)),
     "Raw Game Meat", "Carne de Caca Crua"),
    ("cooked_game_meat", draw_meat, dict(base=(142, 88, 48), edge=(88, 52, 28),
                                         bone=(232, 226, 206)),
     "Cooked Game Meat", "Carne de Caca Assada"),
    ("heavy_carapace", draw_carapace, dict(base=(112, 104, 82), edge=(64, 58, 44),
                                           rib=(150, 140, 112)),
     "Heavy Carapace", "Carapaca Pesada"),
    ("devil_stinger", draw_stinger, dict(base=(96, 108, 62), edge=(48, 56, 32),
                                         glow=(168, 216, 90)),
     "Catachan Devil Stinger", "Ferrao de Catachan Devil"),

    # Trofeus. A cor da placa e do bicho; o cranio e sempre osso.
    ("trophy_ambull", draw_trophy, dict(base=(96, 82, 57), edge=(52, 44, 30),
                                        accent=(228, 222, 200)),
     "Ambull Trophy", "Trofeu de Ambull"),
    ("trophy_catachan_devil", draw_trophy, dict(base=(46, 74, 42), edge=(26, 42, 24),
                                                accent=(228, 222, 200)),
     "Catachan Devil Trophy", "Trofeu de Catachan Devil"),
    ("trophy_duskhorn", draw_trophy, dict(base=(62, 54, 48), edge=(34, 30, 26),
                                          accent=(228, 222, 200)),
     "Duskhorn Trophy", "Trofeu de Duskhorn"),
    ("trophy_greater_malkavan_constrictor", draw_trophy,
     dict(base=(58, 74, 46), edge=(32, 42, 26), accent=(228, 222, 200)),
     "Constrictor Trophy", "Trofeu de Constritora"),
    ("trophy_cthellean_cudbear", draw_trophy, dict(base=(74, 62, 50), edge=(40, 34, 26),
                                                   accent=(228, 222, 200)),
     "Cudbear Trophy", "Trofeu de Cudbear"),
]


# ------------------------------------------------------------------------ traducoes
#
# As entidades e os ovos de spawn. Sem isto o jogo mostra
# "entity.firstcrusade.catachan_devil" na tela, que e o defeito mais visivel que existe.

SPECIES_NAMES = [
    ("fenrisian_wolf", "Fenrisian Wolf", "Lobo Fenrisiano"),
    ("arthromite_duneskuttler", "Arthromite Duneskuttler", "Arthromite Duneskuttler"),
    ("dustback_helamite", "Dustback Helamite", "Helamite de Dorso Poeirento"),
    ("cthellean_cudbear", "Cthellean Cudbear", "Cudbear Cthelleano"),
    ("duskhorn", "Duskhorn", "Duskhorn"),
    ("knarloc", "Knarloc", "Knarloc"),
    ("greater_malkavan_constrictor", "Greater Malkavan Constrictor",
     "Grande Constritora de Malkav"),
    ("catachan_barking_toad", "Catachan Barking Toad", "Sapo Ladrador de Catachan"),
    ("catachan_devil", "Catachan Devil", "Diabo de Catachan"),
]


def lang_entries(locale):
    index = 1 if locale == "en_us" else 2
    entries = {}

    for name, en, pt in SPECIES_NAMES:
        label = en if locale == "en_us" else pt
        entries[f"entity.firstcrusade.{name}"] = label
        entries[f"item.firstcrusade.{name}_spawn_egg"] = (
            f"{label} Spawn Egg" if locale == "en_us" else f"Ovo de {label}")

    for item in ITEMS:
        entries[f"item.firstcrusade.{item[0]}"] = item[index + 2]

    return entries


def merge_lang(path, entries):
    data = {}
    if os.path.exists(path):
        with open(path, encoding="utf-8") as handle:
            data = json.load(handle)

    added = 0
    for key, value in entries.items():
        if key not in data:
            added += 1
        data[key] = value

    with open(path, "w", encoding="utf-8") as handle:
        json.dump(data, handle, indent=2, ensure_ascii=False, sort_keys=True)
        handle.write("\n")

    return added


def main():
    os.makedirs(TEX_ITEM, exist_ok=True)

    for name, draw, colours, _en, _pt in ITEMS:
        image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        draw(image.load(), random.Random("fauna_" + name), **colours)
        image.save(os.path.join(TEX_ITEM, name + ".png"))

    print(f"sprites escritas: {len(ITEMS)}")

    for locale in ("en_us", "pt_br"):
        path = os.path.join(LANG, locale + ".json")
        added = merge_lang(path, lang_entries(locale))
        print(f"lang {locale}: {added} chave(s) nova(s)")


if __name__ == "__main__":
    main()
