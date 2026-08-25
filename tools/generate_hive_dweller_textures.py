#!/usr/bin/env python3
"""
As texturas dos moradores da Hive (spec §19).

Este script NAO reimplementa o pintor. Ele IMPORTA o de tools/generate_troop_textures.py — o
Canvas, o layout de UV (PARTS), a paleta e os verbos compartilhados (paint_part, paint_face,
belt, pouches, boot, glove...). E o mesmo gesto que o generate_ork_assets.py ja faz sobre o
empacotador do script Necron: um dono por codigo, e um layout de UV que nao pode divergir entre
dois arquivos porque so existe num.

O que este arquivo possui: cinco receitas civis (worker, merchant, mechanicus_worker, priest,
gang_member) e mais nada. Os soldados continuam sendo do outro script.

Por que civis e nao mais tropas: a Hive precisa de gente que NAO e soldado. O mod ja tinha onze
uniformes e zero macacoes. Um trabalhador com colete de risco le como trabalhador a vinte blocos,
que e a distancia em que essas leituras importam.

Determinismo: a semente e o papel, nunca o relogio. Rodar de novo da bytes identicos.

Uso:
    python tools/generate_hive_dweller_textures.py [--sheet]
"""

import argparse
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from generate_troop_textures import (  # noqa: E402
    Canvas, PARTS, boot, glove, belt, pouches, paint_face, paint_part, shift, straps,
)

try:
    from PIL import Image
except ImportError:  # pragma: no cover
    raise SystemExit("Pillow nao instalado: pip install pillow")


# --------------------------------------------------------------------------- paleta civil
#
# Nenhuma destas cores e de uniforme. O ponto da Hive e que a maioria das pessoas nela nao esta
# em guerra — esta a trabalhar, a vender, a rezar ou a roubar.

SKIN_PALE = (0xC8, 0xA2, 0x84)     # sol nenhum ha geracoes
SKIN_SOOT = (0xA8, 0x86, 0x6A)     # quem trabalha perto do forno
CANVAS_DRAB = (0x6B, 0x63, 0x52)   # lona suja, o tecido barato da Hive
OVERALL_BLUE = (0x3B, 0x4A, 0x5E)  # macacao de manufactorum
HAZARD = (0xD8, 0xA5, 0x16)        # o amarelo de perigo do kit da Hive
RUST = (0x6E, 0x3D, 0x1C)
MERCHANT_RED = (0x7A, 0x2E, 0x2E)
MERCHANT_TRIM = (0xC8, 0xA9, 0x3A)
MECH_RED = (0x8E, 0x1F, 0x1F)      # o vermelho do Mechanicus
MECH_STEEL = (0x6A, 0x70, 0x76)
BONE = (0xA6, 0x9C, 0x7D)
PRIEST_BLACK = (0x22, 0x20, 0x24)
GANG_LEATHER = (0x33, 0x2B, 0x2B)
GANG_ACCENT = (0x7F, 0xD6, 0x9A)   # verde-doentio do underhive
DARK = (0x13, 0x14, 0x17)


# --------------------------------------------------------------------------- receitas
#
# Uma funcao por papel. Cada uma recebe um Canvas e um RNG semeado.


def worker(c, rng):
    """Macacao de manufactorum e colete de risco. Le como trabalhador, nao como soldado."""
    paint_part(c, "head", SKIN_SOOT, rng, wear_amt=0.10)
    paint_face(c, SKIN_SOOT, DARK, rng, stubble=shift(SKIN_SOOT, -40))

    paint_part(c, "body", OVERALL_BLUE, rng, wear_amt=0.16,
               grime_colour=RUST, grime_amt=0.10)
    paint_part(c, "right_arm", OVERALL_BLUE, rng, wear_amt=0.16)
    paint_part(c, "left_arm", OVERALL_BLUE, rng, wear_amt=0.16)
    paint_part(c, "right_leg", OVERALL_BLUE, rng, wear_amt=0.14)
    paint_part(c, "left_leg", OVERALL_BLUE, rng, wear_amt=0.14)

    # Colete de risco: duas faixas verticais na frente e uma horizontal nas costas.
    body = PARTS["body"]
    c.vline(body["front"], 1, HAZARD, 1, 11)
    c.vline(body["front"], 6, HAZARD, 1, 11)
    c.hline(body["back"], 4, HAZARD, 1, 7)

    belt(c, DARK, j=8)
    pouches(c, CANVAS_DRAB, rows=((1, 9),))
    glove(c, "right_arm", CANVAS_DRAB, height=3)
    glove(c, "left_arm", CANVAS_DRAB, height=3)
    boot(c, "right_leg", DARK, height=3)
    boot(c, "left_leg", DARK, height=3)


def merchant(c, rng):
    """Casaco pesado com debrum dourado. O unico da Hive que escolheu a roupa."""
    paint_part(c, "head", SKIN_PALE, rng, wear_amt=0.06)
    paint_face(c, SKIN_PALE, DARK, rng, stubble=None)

    paint_part(c, "body", MERCHANT_RED, rng, wear_amt=0.08)
    paint_part(c, "right_arm", MERCHANT_RED, rng, wear_amt=0.08)
    paint_part(c, "left_arm", MERCHANT_RED, rng, wear_amt=0.08)
    paint_part(c, "right_leg", shift(MERCHANT_RED, -30), rng, wear_amt=0.10)
    paint_part(c, "left_leg", shift(MERCHANT_RED, -30), rng, wear_amt=0.10)

    body = PARTS["body"]
    # Debrum vertical da abertura do casaco e gola dourada.
    c.vline(body["front"], 3, MERCHANT_TRIM, 0, 12)
    c.vline(body["front"], 4, MERCHANT_TRIM, 0, 12)
    c.hline(body["front"], 0, MERCHANT_TRIM)
    c.hline(body["back"], 0, MERCHANT_TRIM)

    belt(c, DARK, j=7, buckle=MERCHANT_TRIM)
    pouches(c, shift(MERCHANT_RED, -50), rows=((0, 8), (6, 8)), trim=MERCHANT_TRIM)
    boot(c, "right_leg", DARK, height=4)
    boot(c, "left_leg", DARK, height=4)


def mechanicus_worker(c, rng):
    """Vermelho do Mechanicus, capuz, e o braco direito ja substituido."""
    paint_part(c, "head", MECH_RED, rng, wear_amt=0.10)

    head = PARTS["head"]
    # Sem rosto: uma fenda de visao no capuz. Nao e preguica de pintar cara — e o ponto.
    c.fill(head["front"], MECH_RED)
    c.hline(head["front"], 3, DARK, 1, 7)
    c.fdot(head["front"], 2, 3, GANG_ACCENT)
    c.fdot(head["front"], 5, 3, GANG_ACCENT)

    paint_part(c, "body", MECH_RED, rng, wear_amt=0.12, grime_colour=RUST, grime_amt=0.08)
    paint_part(c, "left_arm", MECH_RED, rng, wear_amt=0.12)
    paint_part(c, "right_leg", shift(MECH_RED, -35), rng, wear_amt=0.12)
    paint_part(c, "left_leg", shift(MECH_RED, -35), rng, wear_amt=0.12)

    # O braco direito e maquina.
    paint_part(c, "right_arm", MECH_STEEL, rng, wear_amt=0.20, grime_colour=RUST, grime_amt=0.14)
    arm = PARTS["right_arm"]
    for face in ("front", "back", "right", "left"):
        c.hline(arm[face], 5, shift(MECH_STEEL, -40))
        c.hline(arm[face], 9, shift(MECH_STEEL, -40))

    straps(c, DARK)
    belt(c, DARK, j=8)
    boot(c, "right_leg", MECH_STEEL, height=3)
    boot(c, "left_leg", MECH_STEEL, height=3)


def priest(c, rng):
    """Batina preta, estola ossea. O Ministorum na Hive."""
    paint_part(c, "head", SKIN_PALE, rng, wear_amt=0.06)
    paint_face(c, SKIN_PALE, DARK, rng, stubble=shift(SKIN_PALE, -50))

    paint_part(c, "body", PRIEST_BLACK, rng, wear_amt=0.10)
    paint_part(c, "right_arm", PRIEST_BLACK, rng, wear_amt=0.10)
    paint_part(c, "left_arm", PRIEST_BLACK, rng, wear_amt=0.10)
    paint_part(c, "right_leg", PRIEST_BLACK, rng, wear_amt=0.08)
    paint_part(c, "left_leg", PRIEST_BLACK, rng, wear_amt=0.08)

    body = PARTS["body"]
    # Estola: duas tiras claras descendo do pescoco, e a aguia entre elas.
    c.vline(body["front"], 2, BONE, 0, 10)
    c.vline(body["front"], 5, BONE, 0, 10)
    c.fdot(body["front"], 3, 3, MERCHANT_TRIM)
    c.fdot(body["front"], 4, 3, MERCHANT_TRIM)
    c.fdot(body["front"], 3, 4, MERCHANT_TRIM)
    c.fdot(body["front"], 4, 4, MERCHANT_TRIM)
    c.hline(body["back"], 1, BONE, 2, 6)

    belt(c, BONE, j=8)
    boot(c, "right_leg", DARK, height=3)
    boot(c, "left_leg", DARK, height=3)


def gang_member(c, rng):
    """Couro remendado e tinta verde. Quem manda no Underhive quando ninguem olha."""
    paint_part(c, "head", SKIN_PALE, rng, wear_amt=0.14)
    paint_face(c, SKIN_PALE, GANG_ACCENT, rng, stubble=shift(SKIN_PALE, -55))

    # Faixa de tinta sobre os olhos — a marca da gangue, visivel de longe.
    head = PARTS["head"]
    c.hline(head["front"], 2, GANG_ACCENT, 0, 8)

    paint_part(c, "body", GANG_LEATHER, rng, wear_amt=0.24, grime_colour=RUST, grime_amt=0.16)
    paint_part(c, "right_arm", GANG_LEATHER, rng, wear_amt=0.22)
    paint_part(c, "left_arm", SKIN_PALE, rng, wear_amt=0.18)   # uma manga arrancada
    paint_part(c, "right_leg", shift(GANG_LEATHER, 12), rng, wear_amt=0.22)
    paint_part(c, "left_leg", CANVAS_DRAB, rng, wear_amt=0.26) # a outra perna e remendo

    body = PARTS["body"]
    c.hline(body["front"], 5, GANG_ACCENT, 2, 6)
    c.fdot(body["back"], 3, 3, GANG_ACCENT)
    c.fdot(body["back"], 4, 4, GANG_ACCENT)

    belt(c, RUST, j=8)
    pouches(c, RUST, rows=((5, 9),))
    glove(c, "right_arm", RUST, height=2)
    boot(c, "right_leg", DARK, height=3)
    boot(c, "left_leg", RUST, height=2)


# --------------------------------------------------------------------------- catalogo
#
# (nome do papel, pintor). O Java espelha isto em HiveRole; se os dois discordarem, o renderer
# cai na textura do trabalhador e avisa uma vez.

DWELLERS = [
    ("worker", worker),
    ("merchant", merchant),
    ("mechanicus_worker", mechanicus_worker),
    ("priest", priest),
    ("gang_member", gang_member),
]


def build(out_root, make_sheet=False):
    target = os.path.join(out_root, "entity", "hive")
    os.makedirs(target, exist_ok=True)

    written = []
    sheet_imgs = []

    for name, painter in DWELLERS:
        rng = random.Random(f"hive_dweller:{name}")
        c = Canvas()
        painter(c, rng)

        path = os.path.join(target, f"{name}.png")
        c.img.save(path)
        written.append(path)
        sheet_imgs.append((name, c.img))

    if make_sheet:
        cell = 64 * 4
        cols = len(sheet_imgs)
        sheet = Image.new("RGB", (cols * cell, cell), (24, 24, 28))
        for idx, (_, im) in enumerate(sheet_imgs):
            sheet.paste(im.resize((cell, cell), Image.NEAREST).convert("RGB"), (idx * cell, 0))
        sheet.save(os.path.join(os.path.dirname(__file__), "hive_dweller_sheet.png"))

    return written


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--out",
        default=os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                             "assets", "firstcrusade", "textures"),
    )
    parser.add_argument("--sheet", action="store_true",
                        help="also write tools/hive_dweller_sheet.png")
    args = parser.parse_args()

    files = build(os.path.abspath(args.out), args.sheet)
    print(f"{len(files)} textures written")
    for f in files:
        print("  " + os.path.relpath(f, os.path.abspath(args.out)))
