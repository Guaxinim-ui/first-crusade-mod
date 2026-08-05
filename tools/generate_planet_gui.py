#!/usr/bin/env python3
"""
Assets da Imperial Planetary Navigation: os planetas e os marcadores da interface.

O que este script possui
------------------------
  assets/firstcrusade/textures/gui/planets/<planeta>_icon.png    32x32, a linha da lista
  assets/firstcrusade/textures/gui/planets/<planeta>_large.png   128x128, o retrato central
  assets/firstcrusade/textures/gui/planet_navigation/*.png       cadeado, marcadores, pips

O que NAO possui: o bloco do terminal (datagen/blockstates), os textos (lang) nem a moldura da
tela — a moldura e desenhada em codigo (PlanetNavigationTheme), porque uma tela que estica em
qualquer resolucao com tres paineis redimensionaveis fica melhor com retangulos do que com um
PNG de nove fatias.

Por que os retratos sao gerados e nao pintados a mao
-----------------------------------------------------
Porque a textura grande precisa **repetir na horizontal**: a animacao de rotacao desenha a mesma
imagem duas vezes com deslocamento, entao a coluna 127 tem de encostar na coluna 0 sem emenda.
Isso e uma propriedade matematica (todo ruido usa seno/cosseno da longitude), nao uma questao de
gosto, e desenhar a mao uma textura que fecha e trabalhoso e facil de errar.

Cada planeta e um disco iluminado por cima-esquerda, com uma paleta propria, continentes por
ruido, nuvens opcionais e luzes de cidade no lado escuro. O tipo de mundo escolhe o tratamento:
uma colmeia ganha luzes densas, um mundo ork ganha manchas de fumaca, um mundo tumba ganha
fendas verdes geometricas.

Uso:
    python tools/generate_planet_gui.py
"""

import math
import os
import random

from PIL import Image

A = os.path.join("src", "main", "resources", "assets", "firstcrusade")
PLANETS_DIR = os.path.join(A, "textures", "gui", "planets")
NAV_DIR = os.path.join(A, "textures", "gui", "planet_navigation")

LARGE = 128
ICON = 32


def clamp(value, low=0, high=255):
    return max(low, min(high, int(round(value))))


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def noise(seed):
    """Ruido periodico em longitude: uma soma de senos cujos periodos sao inteiros.

    E o que garante que a textura feche na horizontal. Um ruido de gradiente comum nao fecharia,
    e a emenda apareceria como uma listra vertical girando com o planeta.
    """
    rng = random.Random(seed)
    waves = []
    for _ in range(6):
        waves.append((
            rng.randint(1, 5),          # harmonico em longitude (inteiro => periodico)
            rng.uniform(0.5, 3.0),      # frequencia em latitude
            rng.uniform(0, math.tau),   # fase
            rng.uniform(0.4, 1.0),      # peso
        ))

    def sample(longitude, latitude):
        total = 0.0
        weight = 0.0
        for harmonic, lat_freq, phase, amplitude in waves:
            total += amplitude * math.sin(harmonic * longitude + phase) \
                * math.cos(lat_freq * latitude + phase * 0.5)
            weight += amplitude
        return total / weight

    return sample


# nome -> (oceano/base, terra, destaque, tipo)
#
# O "tipo" escolhe o tratamento extra: cidades, fumaca, gelo, fendas.
PLANETS = {
    "imperial_capital": ((26, 34, 52), (58, 66, 82), (216, 182, 92), "city"),
    "cadia": ((44, 56, 52), (92, 104, 92), (208, 96, 72), "war"),
    "armageddon": ((58, 34, 22), (116, 70, 38), (240, 140, 48), "smoke"),
    "forge_world": ((40, 34, 34), (86, 78, 76), (208, 72, 56), "city"),
    "hive_world": ((32, 30, 22), (74, 68, 44), (226, 200, 92), "city"),
    "agri_world": ((34, 76, 126), (74, 138, 74), (176, 196, 108), "clouds"),
    "ork_world": ((44, 50, 26), (90, 110, 42), (150, 90, 40), "smoke"),
    "necron_tomb_world": ((16, 20, 20), (44, 50, 50), (64, 232, 140), "tomb"),
    "catachan": ((26, 60, 48), (46, 110, 58), (120, 170, 80), "clouds"),
    "valhalla": ((92, 118, 140), (188, 206, 218), (236, 246, 252), "ice"),
}


def planet_texture(name, size):
    base, land, accent, kind = PLANETS[name]
    rng = random.Random(name + str(size))
    height = noise(name + "_height")
    detail = noise(name + "_detail")

    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()

    radius = size / 2.0 - 0.5
    centre = size / 2.0 - 0.5

    # Luz vinda de cima-esquerda-frente. Sem terminador o disco vira um circulo chapado.
    light = (-0.55, -0.5, 0.66)

    for y in range(size):
        for x in range(size):
            dx = (x - centre) / radius
            dy = (y - centre) / radius
            r2 = dx * dx + dy * dy

            if r2 > 1.0:
                continue

            dz = math.sqrt(max(0.0, 1.0 - r2))

            # Coordenadas esfericas: longitude gira, latitude sobe. A textura repete em x porque
            # a longitude vai de 0 a tau ao longo da largura.
            longitude = math.atan2(dx, dz)
            latitude = math.asin(max(-1.0, min(1.0, dy)))

            elevation = height(longitude * 2.0, latitude * 2.0)
            fine = detail(longitude * 6.0, latitude * 5.0) * 0.35

            surface = base if elevation + fine < 0.0 else land
            shade = mix(surface, accent, max(0.0, (elevation + fine) * 0.45))

            lambert = max(0.0, dx * light[0] + dy * light[1] + dz * light[2])
            lit = 0.30 + 0.85 * lambert

            colour = tuple(clamp(c * lit) for c in shade)
            night = lambert < 0.16

            if kind == "city" and night and rng.random() < 0.10:
                colour = accent
            elif kind == "city" and not night and rng.random() < 0.02:
                colour = mix(colour, accent, 0.5)

            if kind == "war" and elevation > 0.25 and rng.random() < 0.06:
                colour = mix(colour, (220, 90, 60), 0.7)

            if kind == "smoke":
                smog = detail(longitude * 3.0 + 1.7, latitude * 2.0)
                if smog > 0.25:
                    colour = mix(colour, (70, 60, 54), min(0.65, smog))
                if night and rng.random() < 0.05:
                    colour = accent

            if kind == "clouds":
                cloud = detail(longitude * 4.0 + 0.9, latitude * 3.0)
                if cloud > 0.3:
                    colour = mix(colour, (235, 240, 245), min(0.7, (cloud - 0.3) * 2.2) * lit)

            if kind == "ice":
                if abs(dy) > 0.55 or elevation > 0.35:
                    colour = mix(colour, (240, 248, 255), 0.55)

            if kind == "tomb":
                # Fendas: linhas geometricas, nao ruido. Um mundo necron tem de parecer construido.
                seam = abs(math.sin(longitude * 6.0) + math.sin(latitude * 8.0))
                if seam < 0.18:
                    glow = 1.0 - seam / 0.18
                    colour = mix(colour, accent, 0.25 + 0.6 * glow)

            # Borda: escurece o limbo, que e o que separa o planeta do fundo estrelado.
            limb = 1.0 - r2
            if limb < 0.06:
                colour = tuple(clamp(c * (0.35 + limb / 0.06 * 0.65)) for c in colour)

            px[x, y] = colour + (255,)

    return img


# ============================================================================ marcadores


def lock_icon():
    img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
    px = img.load()
    body, shine = (214, 208, 190, 255), (250, 248, 236, 255)

    for x in range(2, 6):
        px[x, 1] = body
    px[1, 2] = body
    px[6, 2] = body
    px[1, 3] = body
    px[6, 3] = body

    for y in range(4, 8):
        for x in range(1, 7):
            px[x, y] = body
    px[2, 5] = shine
    px[3, 5] = (60, 54, 44, 255)
    px[3, 6] = (60, 54, 44, 255)

    return img


def marker(colour, shape):
    """Marcador de faccao 8x8: um simbolo simples e original por poder."""
    img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
    px = img.load()
    solid = colour + (255,)

    if shape == "aquila":
        # Duas asas e um corpo: leitura imperial sem copiar nenhuma arte existente.
        for x in range(8):
            px[x, 3] = solid
        for y in range(1, 7):
            px[3, y] = solid
            px[4, y] = solid
        px[0, 2] = solid
        px[7, 2] = solid
    elif shape == "fang":
        # Mandibula ork: dois dentes.
        for x in range(1, 7):
            px[x, 4] = solid
            px[x, 5] = solid
        px[2, 3] = solid
        px[5, 3] = solid
        px[1, 6] = solid
        px[6, 6] = solid
    elif shape == "glyph":
        # Glifo necron: circuito geometrico fechado.
        for x in range(1, 7):
            px[x, 1] = solid
            px[x, 6] = solid
        for y in range(1, 7):
            px[1, y] = solid
            px[6, y] = solid
        px[3, 3] = solid
        px[4, 4] = solid
    else:
        # Contestado: as duas metades em diagonal.
        for y in range(8):
            for x in range(8):
                if x + y < 7:
                    px[x, y] = solid

    return img


def danger_pip(colour, filled):
    img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
    px = img.load()
    tone = colour + (255,) if filled else (70, 78, 84, 255)

    for y in range(1, 7):
        for x in range(1, 7):
            edge = x in (1, 6) or y in (1, 6)
            px[x, y] = (tone[0] // 2, tone[1] // 2, tone[2] // 2, 255) if edge else tone

    return img


def emblem():
    """Selo imperial 16x16 desenhado do zero, usado como marca d'agua do cabecalho."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    gold = (217, 182, 92, 255)
    dark = (140, 112, 48, 255)

    for x in range(1, 15):
        px[x, 7] = gold
    for y in range(2, 14):
        px[7, y] = gold
        px[8, y] = gold
    for i in range(4):
        px[2 + i, 5 + i] = dark
        px[13 - i, 5 + i] = dark
    px[7, 1] = gold
    px[8, 1] = gold
    px[7, 14] = dark
    px[8, 14] = dark

    return img


MARKERS = {
    "imperium_marker": lambda: marker((217, 182, 92), "aquila"),
    "ork_marker": lambda: marker((91, 168, 50), "fang"),
    "necron_marker": lambda: marker((63, 224, 122), "glyph"),
    "contested_marker": lambda: marker((208, 96, 48), "split"),
    "locked": lock_icon,
    "imperial_emblem": emblem,
    "danger_low": lambda: danger_pip((111, 191, 111), True),
    "danger_moderate": lambda: danger_pip((217, 192, 92), True),
    "danger_high": lambda: danger_pip((224, 138, 60), True),
    "danger_extreme": lambda: danger_pip((210, 74, 60), True),
}


def main():
    os.makedirs(PLANETS_DIR, exist_ok=True)
    os.makedirs(NAV_DIR, exist_ok=True)

    for name in sorted(PLANETS):
        large = planet_texture(name, LARGE)
        large.save(os.path.join(PLANETS_DIR, name + "_large.png"))

        # O icone e o retrato reamostrado, nao um desenho separado: assim os dois nunca discordam
        # sobre a cor de um planeta.
        large.resize((ICON, ICON), Image.LANCZOS).save(
            os.path.join(PLANETS_DIR, name + "_icon.png"))
        print("  %-20s %dx%d + %dx%d" % (name, LARGE, LARGE, ICON, ICON))

    for name, build in MARKERS.items():
        build().save(os.path.join(NAV_DIR, name + ".png"))

    print("planetas: %d | marcadores: %d" % (len(PLANETS), len(MARKERS)))


if __name__ == "__main__":
    main()
