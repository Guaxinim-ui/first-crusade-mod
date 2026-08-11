#!/usr/bin/env python3
"""
Pixel art for the two GeckoLib Imperial troops — 64x128, painted onto the model's own UV.

WHY THIS IS SEPARATE FROM generate_troop_textures.py
----------------------------------------------------
The eleven `ModelLayers.ZOMBIE` troops share one hard-coded vanilla layout, so that script can hold
the UV as a constant. These two do not: `guardsman_rifleman` and `guardsman_sergeant` are Blockbench
models with twenty-odd cubes each, at coordinates that live in the .geo.json and nowhere else. So
this script *reads the model* and paints whatever it finds. Change a cube in Blockbench, re-run, and
the texture still lines up — there is no second copy of the layout to keep in step.

DELIBERATELY SHARED UV
----------------------
Both models point `left_arm` and `right_arm` at the same UV, and the same for forearms, hands, legs,
shins and feet. That is mirroring on purpose, not a mistake, and it is left alone: painting the arm
paints both arms, which is exactly what a uniform should do. Nothing here "fixes" it.

WHAT CHANGED AND WHY
--------------------
The four sheets that shipped were flat fills — one colour per cube, no plates, no belt, no insignia
— and the Sergeant differed from the Rifleman by a slightly warmer brown. At any distance they were
the same soldier. These give the Rifleman a Guardsman's flak kit and the Sergeant a red pauldron,
bronze chevrons, a banded helmet and a laspistol/chainsword rig, so the squad leader is findable in
a firing line.

Usage:  python tools/generate_geo_troop_textures.py [--sheet]
"""

import argparse
import json
import math
import os
import random

from PIL import Image

from generate_troop_textures import Canvas, FACE_LIGHT, aquila, chevrons, digits, mix, shift

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "src", "main", "resources", "assets", "firstcrusade")


# --------------------------------------------------------------------------- model reading

def net(uv, size):
    """A Blockbench cube's six faces, unwrapped exactly the way Minecraft unwraps them."""
    u, v = int(uv[0]), int(uv[1])
    w, h, d = (int(math.ceil(s)) for s in size)
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + d + w + d, v + d, w, h),
    }


def read_model(name):
    """bone name -> list of face-dicts, plus the sheet size the model declares."""
    path = os.path.join(ASSETS, "geo", name + ".geo.json")
    with open(path, encoding="utf-8") as handle:
        data = json.load(handle)

    geometry = data["minecraft:geometry"][0]
    description = geometry["description"]
    width = int(description.get("texture_width", 64))
    height = int(description.get("texture_height", 64))

    bones = {}
    for bone in geometry["bones"]:
        cubes = bone.get("cubes", [])
        if not cubes:
            continue
        bones[bone["name"]] = [net(cube["uv"], cube["size"]) for cube in cubes]

    return bones, width, height


# --------------------------------------------------------------------------- painting

def coat(c, boxes, colour, rng, wear=0.13, grime=None, grime_amt=0.0):
    for box in boxes:
        for face_name, face in box.items():
            c.fill(face, colour, FACE_LIGHT[face_name])
            c.gradient(face, 4, -8)
            c.wear(face, rng, wear)
            if grime and grime_amt:
                c.grime(face, rng, grime, grime_amt)


def front_of(boxes, index=0):
    return boxes[index]["front"]


def paint_head(c, boxes, skin, rng, stubble=None, scar=False):
    """The head cube is a plain 8x8x8, so the vanilla face rules apply unchanged."""
    coat(c, boxes, skin, rng, 0.07)
    face = boxes[0]["front"]

    c.hline(face, 3, shift(skin, -34), 0, 8)
    for ex in (1, 5):
        c.rect(face, ex, 4, 2, 1, shift(skin, -60))
        c.fdot(face, ex, 4, (62, 74, 92))
        c.fdot(face, ex + 1, 4, (40, 50, 66))
    c.hline(face, 6, shift(skin, -46), 2, 6)

    if stubble:
        for j in range(5, 8):
            for i in range(8):
                if rng.random() < 0.32:
                    c.fdot(face, i, j, mix(skin, stubble, 0.5))
    if scar:
        for j in range(2, 6):
            c.fdot(face, 5, j, (150, 96, 78))


def paint_helmet(c, boxes, shell, rng, band=None, crest=None):
    """
    The helmet is a shallow slab sitting on the crown, not a full head-sized box, so it gets a rim
    on the sides rather than a visor: from the front you see its leading edge and the face below it.
    """
    coat(c, boxes, shell, rng, 0.12)
    for box in boxes:
        for name in ("front", "back", "left", "right"):
            face = box[name]
            c.hline(face, face[3] - 1, shift(shell, -44), 0, face[2])
            if band:
                c.hline(face, 0, band, 0, face[2])
    if crest:
        top = boxes[0]["top"]
        c.rect(top, top[2] // 2 - 1, 1, 2, max(1, top[3] - 2), crest)


def paint_torso(c, chest, body, pelvis, kit, rng):
    """Flak plate over the ribs, webbing over the belly, a belt on the hips."""
    plate, cloth, leather, buckle = kit

    coat(c, chest, plate, rng, 0.14, (58, 52, 40), 0.10)
    coat(c, body, cloth, rng, 0.15, (58, 52, 40), 0.12)
    coat(c, pelvis, leather, rng, 0.15, (52, 45, 34), 0.14)

    chest_front = front_of(chest)
    c.outline(chest_front, 0, 0, chest_front[2], chest_front[3], shift(plate, -46))
    c.hline(chest_front, chest_front[3] - 1, shift(plate, -46), 0, chest_front[2])

    # Webbing X across the belly cube.
    belly = front_of(body)
    for k in range(min(belly[2], belly[3])):
        c.fdot(belly, k, k, shift(leather, 16))
        c.fdot(belly, belly[2] - 1 - k, k, shift(leather, -4))

    hips = front_of(pelvis)
    c.hline(hips, 1, leather, 0, hips[2])
    c.hline(hips, 2, shift(leather, -26), 0, hips[2])
    c.rect(hips, hips[2] // 2 - 1, 1, 2, 2, buckle)
    # Pouches on the hips, left and right of the buckle.
    for i in (0, hips[2] - 2):
        c.rect(hips, i, 3, 2, hips[3] - 3, shift(leather, -12))
        c.outline(hips, i, 3, 2, hips[3] - 3, shift(leather, -40))


def paint_limbs(c, bones, sleeve, glove_colour, trouser, boot_colour, rng):
    coat(c, bones["right_arm"], sleeve, rng, 0.14, (58, 52, 40), 0.08)
    coat(c, bones["right_forearm"], sleeve, rng, 0.14, (58, 52, 40), 0.10)
    coat(c, bones["right_hand"], glove_colour, rng, 0.12)
    coat(c, bones["right_leg"], trouser, rng, 0.14, (52, 45, 34), 0.14)
    coat(c, bones["right_shin"], trouser, rng, 0.14, (52, 45, 34), 0.20)
    coat(c, bones["right_foot"], boot_colour, rng, 0.12, (44, 38, 30), 0.24)

    # Knee pad on the shin cube's leading face.
    shin = front_of(bones["right_shin"])
    c.rect(shin, 0, 0, shin[2], 2, shift(trouser, 22))
    c.hline(shin, 2, shift(trouser, -34), 0, shin[2])


def paint_pack(c, boxes, colour, rng, strap):
    coat(c, boxes, colour, rng, 0.14, (52, 48, 38), 0.12)
    back = boxes[0]["back"]
    c.outline(back, 0, 0, back[2], back[3], shift(colour, -40))
    c.vline(back, 1, strap, 0, back[3])
    c.vline(back, back[2] - 2, strap, 0, back[3])


def paint_weapon(c, boxes, metal, rng, accent=None):
    coat(c, boxes, metal, rng, 0.18)
    for box in boxes:
        face = box["front"]
        if accent and face[2] >= 2 and face[3] >= 2:
            c.fdot(face, 0, 0, accent)


# --------------------------------------------------------------------------- recipes

def rifleman(c, bones, rng, v):
    green = (74, 90, 52)
    plate = (62, 74, 44)
    leather = (75, 58, 37)
    skin = [(200, 154, 110), (176, 130, 88), (146, 104, 70), (122, 84, 56)][v % 4]

    paint_torso(c, bones["chest"], bones["body"], bones["pelvis"],
                (plate, green, leather, (150, 128, 70)), rng)
    paint_limbs(c, bones, green, (58, 46, 32), (66, 78, 48), (52, 42, 30), rng)
    paint_pack(c, bones["backpack"], (66, 76, 48), rng, leather)
    paint_head(c, bones["head"], skin, rng, stubble=(70, 58, 46) if v % 2 else None)
    paint_helmet(c, bones["helmet"], (85, 100, 59), rng)
    paint_weapon(c, bones["lasgun"], (48, 50, 46), rng, (150, 60, 50))
    paint_weapon(c, bones["magazine"], (60, 62, 56), rng)

    chest_front = front_of(bones["chest"])
    aquila(c, chest_front, 1, 1, (150, 128, 70))
    # Squad number stencilled on the pack, different per variant.
    digits(c, bones["backpack"][0]["back"], 1, 1, (196, 192, 178), 1 + v % 3)
    return c


def sergeant(c, bones, rng, v):
    green = (66, 80, 46)
    plate = (56, 68, 40)
    leather = (60, 46, 30)
    red = (138, 46, 36)
    bronze = (168, 134, 62)
    skin = [(198, 150, 106), (168, 122, 82), (140, 98, 64), (196, 152, 112)][v % 4]

    paint_torso(c, bones["chest"], bones["body"], bones["pelvis"],
                (plate, green, leather, bronze), rng)
    paint_limbs(c, bones, green, (46, 38, 28), (58, 70, 42), (44, 36, 26), rng)
    paint_pack(c, bones["backpack"], (52, 62, 40), rng, leather)
    paint_head(c, bones["head"], skin, rng, stubble=(64, 50, 40), scar=(v % 2 == 1))
    # Banded helmet with a short crest: the squad leader is the one with the red stripe.
    paint_helmet(c, bones["helmet"], (60, 74, 44), rng, band=red, crest=red)
    paint_weapon(c, bones["laspistol"], (44, 46, 42), rng, bronze)
    paint_weapon(c, bones["chainsword"], (72, 74, 78), rng, red)

    chest_front = front_of(bones["chest"])
    c.hline(chest_front, 0, red, 0, chest_front[2])
    aquila(c, chest_front, 1, 1, bronze)

    # Rank chevrons on the upper arm — shared UV, so both arms carry them, which is correct.
    arm = front_of(bones["right_arm"])
    chevrons(c, arm, 0, 1, bronze, 2)

    # Red pauldron: the top of the arm cube and the first row of each side.
    for box in bones["right_arm"]:
        c.fill(box["top"], red, FACE_LIGHT["top"])
        for name in ("front", "back", "left", "right"):
            c.hline(box[name], 0, shift(red, FACE_LIGHT[name]), 0, box[name][2])
            c.hline(box[name], 1, shift(red, -40), 0, box[name][2])

    digits(c, bones["backpack"][0]["back"], 1, 1, (214, 208, 192), 3)
    return c


MODELS = [
    ("guardsman_rifleman", rifleman, 4),
    ("guardsman_sergeant", sergeant, 4),
]


def build(make_sheet=False):
    out_dir = os.path.abspath(os.path.join(ASSETS, "textures", "entity"))
    written = []
    previews = []

    for name, painter, variants in MODELS:
        bones, width, height = read_model(name)

        for v in range(variants):
            rng = random.Random(f"geo:{name}:{v}")
            c = Canvas(size=max(width, height))
            painter(c, bones, rng, v)

            # The model declares 64x128; Canvas is square, so crop to what the model asked for.
            img = c.img.crop((0, 0, width, height))
            path = os.path.join(out_dir, f"{name}_{v}.png")
            img.save(path)
            written.append(path)
            previews.append((f"{name}_{v}", img))

    if make_sheet:
        scale = 4
        cell_w, cell_h = 64 * scale + 8, 128 * scale
        sheet = Image.new("RGB", (cell_w * len(previews), cell_h), (24, 24, 28))
        for idx, (_, img) in enumerate(previews):
            big = img.resize((64 * scale, 128 * scale), Image.NEAREST)
            sheet.paste(big.convert("RGB"), (idx * cell_w, 0), big)
        sheet.save(os.path.join(HERE, "geo_troop_sheet.png"))

    return written


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--sheet", action="store_true")
    args = parser.parse_args()

    files = build(args.sheet)
    print(f"{len(files)} textures written")
    for f in files:
        print("  " + os.path.basename(f))
