#!/usr/bin/env python3
"""
Assets da fauna (Fase E): modelo GeckoLib, animacoes, textura e itens de drop de cada especie.

O que este script possui
------------------------
  assets/firstcrusade/geo/<especie>.geo.json
  assets/firstcrusade/animations/<especie>.animation.json
  assets/firstcrusade/textures/entity/<especie>.png
  assets/firstcrusade/textures/item/<drop>.png  +  models/item/<drop>.json
  as chaves de idioma das especies, dos ovos e dos drops

O que NAO possui: o registro (com.example.examplemod.animal.FCAnimals), as loot tables e os
modelos de ovo (datagen) e os spawners de bioma (tools/generate_biomes.py). Um caminho de
arquivo, um dono.

Por que um script para todas as especies e nao um por bicho: porque o corpo de um quadrupede e
sempre o mesmo problema resolvido com numeros diferentes, e porque o empacotador de UV e a
pintura por face sao o codigo que nao pode divergir entre especies. Um bicho com forma propria
(o Squig e uma bola, o Ambull e um bipede) declara os cubos dele na mao, e continua usando o
mesmo empacotador.

Conferir o resultado com:
    python tools/preview_geo_model.py <especie> saida.png

Uso:
    python tools/generate_animal_assets.py [especie ...]
"""

import json
import math
import os
import random
import sys

from PIL import Image

RES = os.path.join("src", "main", "resources")
A = os.path.join(RES, "assets", "firstcrusade")

GEO_DIR = os.path.join(A, "geo")
ANIM_DIR = os.path.join(A, "animations")
TEX_ENTITY = os.path.join(A, "textures", "entity")
TEX_ITEM = os.path.join(A, "textures", "item")
MODEL_ITEM = os.path.join(A, "models", "item")
LANG_DIR = os.path.join(A, "lang")

TEX_W, TEX_H = 128, 128


# ============================================================================ esqueletos
#
# Um cubo e (nome, origem, tamanho, osso). Origem = canto minimo em unidades de modelo, y=0 no
# chao. A UV nao aparece aqui: sai do empacotador, e a mesma conta pinta a textura.
#
# Um osso e (pai, pivo). O pivo da cabeca fica no ALTO e ATRAS dela, na juncao com o pescoco:
# e o que faz "abaixar a cabeca" girar em torno do pescoco em vez de a cabeca afundar no chao.


def quadruped(body, leg, head, tail, head_drop=1, hump=None, horns=None, snout=None):
    """O esqueleto comum: corpo, quatro patas, cabeca e cauda.

    `head_drop` desce a cabeca em relacao a barriga. Nunca deixar em zero: alinhada com o corpo,
    a silhueta lateral vira um bloco continuo do focinho a garupa e o bicho perde a cabeca —
    literalmente, nao da para dizer onde uma acaba e o outro comeca.
    """
    bw, bh, bd = body
    lw, lh = leg
    hw, hh, hd = head
    tw, th, td = tail

    body_y = lh
    body_top = body_y + bh
    nose = -bd // 2 - hd

    cubes = [
        ("body", (-bw // 2, body_y, -bd // 2), (bw, bh, bd), "body"),
        ("head", (-hw // 2, body_y - head_drop, -bd // 2 - hd), (hw, hh, hd), "head"),
        ("tail", (-tw // 2, body_top - th - 2, bd // 2), (tw, th, td), "tail"),
    ]

    for side, sign in (("right", -1), ("left", 1)):
        for end, z in (("front", -bd // 2 + 2), ("back", bd // 2 - 2 - lw)):
            name = "leg_%s_%s" % (end, side)
            x = -bw // 2 + 1 if sign < 0 else bw // 2 - 1 - lw
            cubes.append((name, (x, 0, z), (lw, lh, lw), name))

    if hump:
        hpw, hph, hpd = hump
        cubes.insert(1, ("hump", (-hpw // 2, body_top, -hpd // 2 - bd // 6),
                         (hpw, hph, hpd), "body"))

    if snout:
        sw, sh, sd = snout
        cubes.append(("snout", (-sw // 2, body_y - head_drop + 1, nose - sd),
                      (sw, sh, sd), "head"))

    if horns:
        # Chifre em dois segmentos: a base sai para o lado, a ponta sobe e avanca. Um cubo unico
        # aponta so para onde o eixo dele aponta, e na vista de frente — a vista de quem vai ser
        # chifrado — ele some num quadrado de tres pixels.
        base, tip = horns
        by = body_y - head_drop + hh - 4
        for side, sign in (("right", -1), ("left", 1)):
            bx = -hw // 2 - base[0] if sign < 0 else hw // 2
            tx = bx - 2 if sign < 0 else bx + 2
            cubes.append(("horn_base_%s" % side, (bx, by, nose + 3), base, "head"))
            cubes.append(("horn_tip_%s" % side, (tx, by + 2, nose - 1), tip, "head"))

    bones = {
        "body": (None, (0, body_y + bh // 2, 0)),
        "head": ("body", (0, body_top - 2, -bd // 2)),
        "tail": ("body", (0, body_top - 2, bd // 2)),
    }
    for side in ("right", "left"):
        for end in ("front", "back"):
            name = "leg_%s_%s" % (end, side)
            z = -bd // 2 + 2 + lw // 2 if end == "front" else bd // 2 - 2 - lw // 2
            x = (-bw // 2 + 1 + lw // 2) if side == "right" else (bw // 2 - 1 - lw // 2)
            bones[name] = ("body", (x, lh, z))

    return cubes, bones


def biped(body, leg, head, arm):
    """Corpo ereto, dois pes e dois bracos. Usado pelo Ambull, que cava com os bracos."""
    bw, bh, bd = body
    lw, lh = leg
    hw, hh, hd = head
    aw, ah, ad = arm

    body_y = lh
    body_top = body_y + bh

    cubes = [
        ("body", (-bw // 2, body_y, -bd // 2), (bw, bh, bd), "body"),
        ("head", (-hw // 2, body_top - 2, -bd // 2 - hd + 3), (hw, hh, hd), "head"),
    ]

    for side, sign in (("right", -1), ("left", 1)):
        x = -bw // 2 - aw if sign < 0 else bw // 2
        cubes.append(("arm_%s" % side, (x, body_top - ah - 2, -ad // 2), (aw, ah, ad),
                      "arm_%s" % side))
        lx = -bw // 2 + 1 if sign < 0 else bw // 2 - 1 - lw
        cubes.append(("leg_%s" % side, (lx, 0, -lw // 2), (lw, lh, lw), "leg_%s" % side))

    bones = {
        "body": (None, (0, body_y, 0)),
        "head": ("body", (0, body_top, -bd // 2)),
        "arm_right": ("body", (-bw // 2, body_top - 2, 0)),
        "arm_left": ("body", (bw // 2, body_top - 2, 0)),
        "leg_right": ("body", (-bw // 2 + 1 + lw // 2, lh, 0)),
        "leg_left": ("body", (bw // 2 - 1 - lw // 2, lh, 0)),
    }
    return cubes, bones


def squig_shape():
    """Uma bola com boca e dois pes. O Squig nao tem corpo e cabeca: ele e a cabeca.

    A primeira versao saiu um cubo vermelho: mandibula pequena demais e escondida sob o corpo,
    e a crista dos olhos rente ao topo. Um bicho cuja unica ideia e "boca" precisa que a boca
    seja a maior coisa da silhueta — entao a mandibula saiu para fora na frente, ficou tao larga
    quanto o corpo, e a crista subiu acima da linha das costas.
    """
    cubes = [
        ("body", (-6, 5, -5), (12, 12, 12), "body"),
        ("jaw", (-6, 2, -10), (12, 6, 7), "jaw"),
        ("eye_ridge", (-5, 16, -6), (10, 3, 6), "body"),
        ("leg_right", (-4, 0, 0), (3, 5, 3), "leg_right"),
        ("leg_left", (1, 0, 0), (3, 5, 3), "leg_left"),
    ]
    bones = {
        "body": (None, (0, 11, 0)),
        "jaw": ("body", (0, 8, -5)),
        "leg_right": ("body", (-2.5, 5, 1.5)),
        "leg_left": ("body", (2.5, 5, 1.5)),
    }
    return cubes, bones


# ============================================================================ as especies
#
# Cada uma declara forma, paleta e o que a anima. As proporcoes saem da hitbox declarada em
# FCAnimals: o corpo ocupa a largura dela e a cabeca sai por fora na frente, porque a hitbox e
# onde o bicho esbarra, nao onde ele acaba.

SPECIES = {}


def species(name, shape, palette, scale=1.0, gait=26, **kwargs):
    cubes, bones = shape
    SPECIES[name] = dict(cubes=cubes, bones=bones, palette=palette, gait=gait, **kwargs)


# Grox: gado imperial. Pesado, com giba sobre os ombros e chifres para a frente.
species(
    "grox",
    quadruped(body=(14, 13, 28), leg=(5, 12), head=(10, 11, 13), tail=(4, 5, 10),
              head_drop=1, hump=(10, 4, 13), horns=((3, 3, 7), (2, 2, 7))),
    palette=dict(hide=(110, 92, 70), horn=(200, 190, 162), hoof=(58, 50, 42),
                 eye=(196, 150, 60)),
    gait=26, graze=True)

# Cyber-mastiff: cao de guarda dos Arbites, mais implante que animal. Focinho longo, corpo
# baixo e comprido, e um olho que brilha — a unica cor viva de todo o conjunto.
species(
    "cyber_mastiff",
    quadruped(body=(8, 8, 18), leg=(4, 11), head=(7, 7, 8), tail=(3, 3, 7),
              head_drop=0, snout=(4, 4, 5)),
    palette=dict(hide=(72, 68, 66), horn=(140, 140, 146), hoof=(40, 38, 38),
                 eye=(220, 60, 40)),
    gait=34)

# Sump Rat: baixo, comprido e de cauda longa. A silhueta inteira e "passa por baixo da porta".
species(
    "sump_rat",
    quadruped(body=(6, 5, 12), leg=(3, 3), head=(5, 5, 5), tail=(2, 2, 14),
              head_drop=0, snout=(3, 3, 4)),
    palette=dict(hide=(84, 76, 62), horn=(150, 140, 120), hoof=(52, 46, 38),
                 eye=(200, 90, 70)),
    gait=38)

# Ash Strider: pernalta dos ermos. Corpo pequeno muito alto, e as pernas sao o bicho.
species(
    "ash_strider",
    quadruped(body=(7, 7, 18), leg=(3, 22), head=(6, 6, 7), tail=(2, 2, 9),
              head_drop=-5),
    palette=dict(hide=(126, 120, 112), horn=(178, 172, 160), hoof=(70, 66, 60),
                 eye=(240, 230, 180)),
    gait=20, graze=True)

# Squig: bola de dentes. Nao tem pescoco porque nao tem para onde.
species("squig", squig_shape(),
        palette=dict(hide=(150, 40, 46), horn=(236, 232, 214), hoof=(96, 24, 28),
                     eye=(250, 230, 90)),
        gait=0, hop=True)

# Ambull: escavador. Bracos enormes, corpo curvado, cabeca enfiada nos ombros.
species(
    "ambull",
    biped(body=(16, 18, 12), leg=(6, 12), head=(10, 9, 10), arm=(6, 20, 6)),
    palette=dict(hide=(96, 82, 58), horn=(206, 196, 158), hoof=(58, 50, 36),
                 eye=(240, 120, 40)),
    gait=22, dig=True)


# ============================================================================ empacotador UV


def pack_uvs(cubes):
    """Distribui os cubos na textura em linhas; devolve nome -> (u, v).

    O desdobramento de caixa ocupa 2*(d+w) por d+h, entao empacotar e enfileirar retangulos.
    """
    placed = {}
    x = y = row_height = 0

    for name, _origin, (w, h, d), _bone in cubes:
        box_w, box_h = 2 * (d + w), d + h

        if x + box_w > TEX_W:
            x, y = 0, y + row_height
            row_height = 0

        placed[name] = (x, y)
        x += box_w
        row_height = max(row_height, box_h)

    if y + row_height > TEX_H:
        raise SystemExit("os cubos nao cabem em %dx%d" % (TEX_W, TEX_H))

    return placed


def faces(u, v, w, h, d):
    """As seis faces no desdobramento padrao, como retangulos (x0, y0, x1, y1)."""
    return {
        "top":    (u + d, v, u + d + w, v + d),
        "bottom": (u + d + w, v, u + d + 2 * w, v + d),
        "right":  (u, v + d, u + d, v + d + h),
        "front":  (u + d, v + d, u + d + w, v + d + h),
        "left":   (u + d + w, v + d, u + 2 * d + w, v + d + h),
        "back":   (u + 2 * d + w, v + d, u + 2 * (d + w), v + d + h),
    }


# ============================================================================== o geo.json


def geo_model(name, data, uvs):
    order = []
    for _n, _o, _s, bone in data["cubes"]:
        if bone not in order:
            order.append(bone)
    for bone in data["bones"]:
        if bone not in order:
            order.append(bone)

    # Pai antes de filho, senao o GeckoLib nao resolve a hierarquia.
    order.sort(key=lambda b: 0 if data["bones"].get(b, (None, None))[0] is None else 1)

    bones = []
    for bone in order:
        parent, pivot = data["bones"][bone]
        entry = {"name": bone, "pivot": list(pivot)}
        if parent:
            entry["parent"] = parent

        cubes = [{"origin": list(o), "size": list(s), "uv": list(uvs[n])}
                 for n, o, s, owner in data["cubes"] if owner == bone]
        if cubes:
            entry["cubes"] = cubes

        bones.append(entry)

    height = max(o[1] + s[1] for _n, o, s, _b in data["cubes"])

    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry." + name,
                "texture_width": TEX_W,
                "texture_height": TEX_H,
                "visible_bounds_width": 4,
                "visible_bounds_height": max(2, height // 8),
                "visible_bounds_offset": [0, height / 32.0, 0],
            },
            "bones": bones,
        }],
    }


# ============================================================================ animacoes
#
# Quatro no maximo, e cada uma existe porque algum codigo a dispara: idle e walk pelo
# controlador de movimento, attack por doHurtTarget, graze pelo GrazeGoal. Animacao que nada
# dispara nao entra — seria o equivalente visual de um config que nao faz nada.


def animations(data):
    swing = data["gait"]
    legs = sorted(b for b in data["bones"] if b.startswith("leg_"))
    has_tail = "tail" in data["bones"]

    def leg_track(phase):
        return {"rotation": {"0.0": [swing * phase, 0, 0],
                             "0.5": [-swing * phase, 0, 0],
                             "1.0": [swing * phase, 0, 0]}}

    walk_bones = {}
    if data.get("hop"):
        # O Squig nao anda: ele pula. O corpo inteiro sobe e desce, e os pes acompanham.
        walk_bones["body"] = {"position": {"0.0": [0, 0, 0], "0.25": [0, 4, 0],
                                           "0.5": [0, 0, 0], "1.0": [0, 0, 0]},
                              "rotation": {"0.0": [0, 0, 0], "0.25": [-14, 0, 0],
                                           "0.5": [8, 0, 0], "1.0": [0, 0, 0]}}
        for i, leg in enumerate(legs):
            walk_bones[leg] = {"rotation": {"0.0": [0, 0, 0], "0.25": [-30, 0, 0],
                                            "0.5": [10, 0, 0], "1.0": [0, 0, 0]}}
    else:
        # Diagonais opostas juntas: e o andar de qualquer quadrupede. Sem isso o bicho anda
        # como uma mesa. Num bipede as duas pernas apenas se alternam.
        for leg in legs:
            diagonal = ("front" in leg and "right" in leg) or ("back" in leg and "left" in leg)
            if "front" not in leg and "back" not in leg:
                diagonal = "right" in leg
            walk_bones[leg] = leg_track(1 if diagonal else -1)

        walk_bones["body"] = {"rotation": {"0.0": [0, 0, 2], "0.5": [0, 0, -2], "1.0": [0, 0, 2]},
                              "position": {"0.0": [0, 0, 0], "0.25": [0, 0.5, 0],
                                           "0.75": [0, 0.5, 0], "1.0": [0, 0, 0]}}
        walk_bones["head"] = {"rotation": {"0.0": [3, 0, 0], "0.5": [-3, 0, 0], "1.0": [3, 0, 0]}}

    if has_tail:
        walk_bones["tail"] = {"rotation": {"0.0": [0, -7, 0], "0.5": [0, 7, 0], "1.0": [0, -7, 0]}}

    idle_bones = {
        "body": {"position": {"0.0": [0, 0, 0], "2.0": [0, 0.4, 0], "4.0": [0, 0, 0]}},
    }
    if "head" in data["bones"]:
        idle_bones["head"] = {"rotation": {"0.0": [0, 0, 0], "1.3": [4, -6, 0],
                                           "2.6": [-2, 5, 0], "4.0": [0, 0, 0]}}
    if "jaw" in data["bones"]:
        idle_bones["jaw"] = {"rotation": {"0.0": [0, 0, 0], "2.0": [12, 0, 0], "4.0": [0, 0, 0]}}
    if has_tail:
        idle_bones["tail"] = {"rotation": {"0.0": [0, 0, 0], "2.0": [0, 9, 0], "4.0": [0, 0, 0]}}

    if data.get("dig"):
        attack_bones = {
            "arm_right": {"rotation": {"0.0": [0, 0, 0], "0.15": [-120, 0, 0],
                                       "0.35": [30, 0, 0], "0.7": [0, 0, 0]}},
            "arm_left": {"rotation": {"0.0": [0, 0, 0], "0.25": [-110, 0, 0],
                                      "0.45": [25, 0, 0], "0.7": [0, 0, 0]}},
        }
    elif "jaw" in data["bones"]:
        attack_bones = {
            "jaw": {"rotation": {"0.0": [0, 0, 0], "0.1": [46, 0, 0], "0.3": [0, 0, 0],
                                 "0.7": [0, 0, 0]}},
            "body": {"position": {"0.0": [0, 0, 0], "0.2": [0, 0, -3], "0.7": [0, 0, 0]}},
        }
    else:
        # A chifrada/mordida: a cabeca desce, o corpo joga o peso para a frente e volta.
        attack_bones = {
            "head": {"rotation": {"0.0": [0, 0, 0], "0.12": [28, 0, 0],
                                  "0.3": [-34, 0, 0], "0.7": [0, 0, 0]}},
            "body": {"rotation": {"0.0": [0, 0, 0], "0.12": [6, 0, 0],
                                  "0.3": [-8, 0, 0], "0.7": [0, 0, 0]},
                     "position": {"0.0": [0, 0, 0], "0.3": [0, 0, -2.5], "0.7": [0, 0, 0]}},
        }

    out = {
        "idle": {"loop": True, "animation_length": 4.0, "bones": idle_bones},
        "walk": {"loop": True, "animation_length": 1.0, "bones": walk_bones},
        "attack": {"loop": False, "animation_length": 0.7, "bones": attack_bones},
    }

    if data.get("graze"):
        # Dura o que o GrazeGoal dura (40 ticks = 2 s), com a subida no fim para nao cortar seco.
        out["graze"] = {"loop": False, "animation_length": 2.0, "bones": {
            "head": {"rotation": {"0.0": [0, 0, 0], "0.4": [46, 0, 0],
                                  "1.5": [44, 6, 0], "2.0": [0, 0, 0]},
                     "position": {"0.0": [0, 0, 0], "0.4": [0, -2, -1],
                                  "1.5": [0, -2, -1], "2.0": [0, 0, 0]}},
        }}

    return {"format_version": "1.8.0", "animations": out}


# ============================================================================== a textura


def shade(color, factor):
    return tuple(max(0, min(255, int(round(c * factor)))) for c in color)


def paint_rect(px, rect, base, rng, grain=18, speckle=0.0, speckle_color=None):
    x0, y0, x1, y1 = rect
    for x in range(x0, x1):
        for y in range(y0, y1):
            if speckle and rng.random() < speckle:
                px[x, y] = speckle_color + (255,)
                continue
            n = rng.randint(-grain, grain)
            px[x, y] = tuple(max(0, min(255, c + n)) for c in base) + (255,)


def entity_texture(name, data, uvs):
    """Pinta a pele a partir do proprio layout UV: dorso queimado, barriga clara, casco escuro.

    A leitura pretendida e sempre "bicho que sobreviveu num mundo ruim" — nao ha cor viva em
    lugar nenhum a nao ser o olho, que e o que se ve primeiro de longe.
    """
    rng = random.Random(name)
    img = Image.new("RGBA", (TEX_W, TEX_H), (0, 0, 0, 0))
    px = img.load()

    hide = data["palette"]["hide"]
    horn = data["palette"]["horn"]
    hoof = data["palette"]["hoof"]
    eye = data["palette"]["eye"]
    back = shade(hide, 0.72)
    belly = shade(hide, 1.28)

    for cube_name, _origin, (w, h, d), _bone in data["cubes"]:
        u, v = uvs[cube_name]
        f = faces(u, v, w, h, d)

        if cube_name.startswith("horn"):
            for rect in f.values():
                paint_rect(px, rect, horn, rng, grain=10)
            paint_rect(px, f["front"], shade(horn, 0.78), rng, grain=8)
            continue

        for rect in f.values():
            paint_rect(px, rect, hide, rng)

        # As placas do lombo so no corpo: num bicho todo manchado elas deixam de ser silhueta e
        # viram ruido.
        paint_rect(px, f["top"], back, rng, grain=14,
                   speckle=0.12 if cube_name in ("body", "hump") else 0.0,
                   speckle_color=shade(hide, 0.55))
        paint_rect(px, f["bottom"], belly, rng, grain=12)

        if cube_name.startswith("leg"):
            _x0, _y0, _x1, y1 = f["front"]
            hoof_top = y1 - max(2, h // 5)
            for key in ("front", "back", "left", "right"):
                rx0, _ry0, rx1, ry1 = f[key]
                paint_rect(px, (rx0, hoof_top, rx1, ry1), hoof, rng, grain=8)
            paint_rect(px, f["bottom"], hoof, rng, grain=6)

        if cube_name in ("head", "jaw", "eye_ridge"):
            for key in ("right", "left"):
                rx0, ry0, rx1, _ry1 = f[key]
                ex = rx0 + max(1, (rx1 - rx0) // 3)
                ey = ry0 + min(3, max(1, h // 3))
                px[ex, ey] = (24, 20, 16, 255)
                if ey + 1 < TEX_H:
                    px[ex, ey + 1] = eye + (255,)

        if cube_name in ("jaw", "snout"):
            # Dentes: a fileira clara na borda de baixo da mandibula.
            rx0, _ry0, rx1, ry1 = f["front"]
            for x in range(rx0, rx1, 2):
                px[x, ry1 - 1] = horn + (255,)

    return img


# ================================================================================ os drops


def item_texture(kind, base, edge, fat):
    rng = random.Random("item_" + kind)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()

    if kind == "horn":
        # Cone curvo desenhado varrendo um disco de raio decrescente. Desenhar por linha de y
        # da degraus, porque perto da ponta a curva anda mais em x do que em y.
        for i in range(49):
            t = i / 48.0
            cx, cy = 4.2 + 6.6 * (t ** 1.45), 14.0 - 11.6 * t
            radius = 2.7 * (1.0 - t) + 0.55
            for x in range(16):
                for y in range(16):
                    if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= radius * radius:
                        tone = fat if (x + 0.5 - cx) + (y + 0.5 - cy) < -0.4 else base
                        n = rng.randint(-8, 8)
                        px[x, y] = tuple(max(0, min(255, c + n)) for c in tone) + (255,)

        filled = {(x, y) for x in range(16) for y in range(16) if px[x, y][3]}
        for (x, y) in filled:
            if any((x + dx, y + dy) not in filled
                   for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                px[x, y] = edge + (255,)
        return img

    if kind == "plate":
        # Placa de quitina: hexagono achatado com nervura.
        for y in range(16):
            for x in range(16):
                dx, dy = abs(x - 7.5) / 6.5, abs(y - 7.5) / 6.0
                if dx + dy * 0.65 > 1.0 or dy > 1.0:
                    continue
                border = dx + dy * 0.65 > 0.78
                tone = edge if border else base
                n = rng.randint(-10, 10)
                px[x, y] = tuple(max(0, min(255, c + n)) for c in tone) + (255,)
        for y in range(4, 12):
            if px[7, y][3]:
                px[7, y] = fat + (255,)
        return img

    # Naco de carne / pele esticada.
    for y in range(16):
        for x in range(16):
            if kind == "hide":
                inside = 2 <= x <= 13 and 3 <= y <= 12
                border = x in (2, 13) or y in (3, 12)
            else:
                dx, dy = (x - 7.5) / 6.2, (y - 8.0) / 5.4
                inside = dx * dx + dy * dy <= 1.0
                border = dx * dx + dy * dy > 0.72

            if not inside:
                continue

            tone = edge if border else base
            n = rng.randint(-12, 12)
            px[x, y] = tuple(max(0, min(255, c + n)) for c in tone) + (255,)

    if kind == "meat":
        for (x, y) in ((6, 6), (7, 6), (9, 8), (5, 9), (10, 10), (7, 11)):
            if px[x, y][3]:
                px[x, y] = fat + (255,)

    return img


# tecnica, item, cores (base, borda, veio), EN, PT
DROPS = [
    ("meat", "grox_meat", ((158, 62, 58), (120, 40, 40), (206, 150, 140)),
     "Raw Grox Meat", "Carne Crua de Grox"),
    ("meat", "cooked_grox_meat", ((128, 78, 44), (92, 54, 30), (186, 138, 84)),
     "Cooked Grox Meat", "Carne de Grox Assada"),
    ("hide", "grox_hide", ((120, 96, 68), (86, 68, 48), (150, 126, 96)),
     "Grox Hide", "Couro de Grox"),
    ("horn", "grox_horn", ((198, 188, 160), (150, 140, 116), (226, 220, 198)),
     "Grox Horn", "Chifre de Grox"),
    ("meat", "scavenged_meat", ((138, 74, 76), (104, 52, 54), (188, 150, 132)),
     "Scavenged Meat", "Carne Catada"),
    ("meat", "cooked_scavenged_meat", ((122, 82, 52), (88, 58, 36), (176, 138, 92)),
     "Cooked Scavenged Meat", "Carne Catada Assada"),
    ("plate", "chitin_plate", ((132, 124, 106), (94, 88, 76), (176, 168, 148)),
     "Chitin Plate", "Placa de Quitina"),
]

NAMES = {
    "grox": ("Grox", "Grox"),
    "cyber_mastiff": ("Cyber-mastiff", "Cibermastim"),
    "sump_rat": ("Sump Rat", "Rato de Sump"),
    "ash_strider": ("Ash Strider", "Pernalta das Cinzas"),
    "squig": ("Squig", "Squig"),
    "ambull": ("Ambull", "Ambull"),
}


# ================================================================================= escrita


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def merge_lang(path, entries):
    with open(path, encoding="utf-8") as f:
        data = json.load(f)

    added = 0
    for key, value in entries.items():
        if key not in data:
            data[key] = value
            added += 1

    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")

    return added


def main():
    wanted = sys.argv[1:] or sorted(SPECIES)

    for directory in (GEO_DIR, ANIM_DIR, TEX_ENTITY, TEX_ITEM, MODEL_ITEM):
        os.makedirs(directory, exist_ok=True)

    en, pt = {}, {}

    for name in wanted:
        data = SPECIES[name]
        uvs = pack_uvs(data["cubes"])

        write_json(os.path.join(GEO_DIR, name + ".geo.json"), geo_model(name, data, uvs))
        write_json(os.path.join(ANIM_DIR, name + ".animation.json"), animations(data))
        entity_texture(name, data, uvs).save(os.path.join(TEX_ENTITY, name + ".png"))

        label_en, label_pt = NAMES[name]
        en["entity.firstcrusade." + name] = label_en
        pt["entity.firstcrusade." + name] = label_pt
        en["item.firstcrusade.%s_spawn_egg" % name] = "%s Spawn Egg" % label_en
        pt["item.firstcrusade.%s_spawn_egg" % name] = "Ovo Gerador de %s" % label_pt

        print("  %-14s %2d cubos, %2d ossos" % (name, len(data["cubes"]), len(data["bones"])))

    for kind, item, colours, label_en, label_pt in DROPS:
        item_texture(kind, *colours).save(os.path.join(TEX_ITEM, item + ".png"))
        write_json(os.path.join(MODEL_ITEM, item + ".json"), {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": "firstcrusade:item/" + item},
        })
        en["item.firstcrusade." + item] = label_en
        pt["item.firstcrusade." + item] = label_pt

    a1 = merge_lang(os.path.join(LANG_DIR, "en_us.json"), en)
    a2 = merge_lang(os.path.join(LANG_DIR, "pt_br.json"), pt)

    print("especies: %d | drops: %d | lang en+%d pt+%d" % (len(wanted), len(DROPS), a1, a2))


if __name__ == "__main__":
    main()
