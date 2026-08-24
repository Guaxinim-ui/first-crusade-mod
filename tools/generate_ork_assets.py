#!/usr/bin/env python3
"""
The four Ork placeholders, given real models: Nob, Meganob, Gretchin and Killa Kan.

WHY THESE FOUR AND NOBODY ELSE
------------------------------
The §29 audit sorted the mod's humanoids into three piles and the important line was that *not every
humanoid is a placeholder*: eleven Imperial troops are humans in armour, so a humanoid model is the
correct model for them and they need better textures some day, not new geometry. These four are the
ones where the shape itself is wrong — three Orks built like men, and a walker built like a man,
which is the worst of them.

The audit named the Nob as the priority and it is worth repeating: he leads squads of Ork Boyz who
*do* have their own model, so today every Ork squad in the game has one oddly-shaped human standing
in the middle of it.

WHY THIS IMPORTS FROM THE NECRON SCRIPT
--------------------------------------
`generate_necron_assets.py` owns the packer that assigns UVs rather than reading them, and that
machinery is what makes model and texture unable to disagree. Importing it is the same move
`generate_geo_troop_textures.py` already makes on `generate_troop_textures.py`, and it means the
Necron script — which has been run and verified — is not edited to add a second caller.

PROPORTION REFERENCE
--------------------
`ork_boy.geo.json`: body 12x13x6, head 10x9x10, arms 5x14x5, legs 5x11x5, 33 units tall. Every Ork
below is that build pushed in one direction, because a Nob who is not obviously a bigger Boy is just
a differently-coloured Boy.

Usage:  python tools/generate_ork_assets.py [--sheet]
"""

import argparse
import os
import random

from PIL import Image

from generate_troop_textures import Canvas, FACE_LIGHT, mix, shift
from generate_necron_assets import Bone, Cube, emit_geo, pack, write_json, base, plate_lines

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "src", "main", "resources", "assets", "firstcrusade")


# =============================================================================== palette

SKIN        = (86, 120, 52)
SKIN_DARK   = (56, 82, 34)
SKIN_LIT    = (120, 156, 76)
LEATHER     = (78, 60, 40)
LEATHER_DK  = (50, 38, 26)
METAL       = (96, 100, 104)
METAL_DARK  = (56, 60, 64)
METAL_LIT   = (140, 146, 150)
RUST        = (126, 76, 42)
TEETH       = (226, 222, 198)
EYE         = (208, 44, 32)
VOID        = (22, 24, 22)
GLYPH       = (216, 196, 60)   # Ork clan yellow, for the Kan's plates


# =============================================================================== painters

def paint_ork_skin(c, faces, rng):
    base(c, faces, SKIN, rng, 0.15)
    for name in ("front", "back", "left", "right"):
        face = faces[name]
        for _ in range(max(2, face[2] * face[3] // 14)):
            i = rng.randrange(face[2])
            j = rng.randrange(face[3])
            c.fdot(face, i, j, shift(SKIN, -30) if rng.random() < 0.6 else SKIN_LIT)


def paint_ork_head(c, faces, rng, big_jaw=True):
    """
    Green, red-eyed, and mostly jaw. The tusks are the read at distance, so they get the brightest
    value on the whole model and sit on the silhouette's edge rather than in the middle of the face.
    """
    paint_ork_skin(c, faces, rng)
    face = faces["front"]
    w, h = face[2], face[3]

    # Heavy brow.
    c.hline(face, 1, SKIN_DARK, 0, w)
    c.hline(face, 2, shift(SKIN_DARK, -18), 0, w)

    # Small red eyes set deep under it.
    for ex in (1, w - 3):
        c.rect(face, ex, 3, 2, 1, VOID)
        c.fdot(face, ex + 1, 3, EYE)

    if big_jaw:
        # Underbite: a dark mouth line with tusks pushing up out of it.
        c.hline(face, h - 3, VOID, 1, w - 1)
        for tx in (1, w - 2):
            c.fdot(face, tx, h - 3, TEETH)
            c.fdot(face, tx, h - 4, TEETH)
        for i in range(3, w - 3, 2):
            c.fdot(face, i, h - 2, TEETH)


def paint_ear(c, faces, rng):
    paint_ork_skin(c, faces, rng)
    for name in ("front", "back"):
        face = faces[name]
        c.hline(face, 0, SKIN_LIT, 0, face[2])
        c.hline(face, face[3] - 1, SKIN_DARK, 0, face[2])


def paint_leather(c, faces, rng):
    base(c, faces, LEATHER, rng, 0.16)
    plate_lines(c, faces, LEATHER_DK, 3)
    for name in ("front", "back"):
        face = faces[name]
        for i in range(1, face[2], 4):
            c.fdot(face, i, face[3] // 2, METAL_LIT)   # studs


def paint_armour(c, faces, rng, trim=None):
    base(c, faces, METAL, rng, 0.18)
    plate_lines(c, faces, METAL_DARK, 4)
    for name, face in faces.items():
        for _ in range(max(1, face[2] * face[3] // 18)):
            i, j = rng.randrange(face[2]), rng.randrange(face[3])
            c.fdot(face, i, j, RUST)
    if trim:
        for name in ("front", "back"):
            c.hline(faces[name], 0, trim, 0, faces[name][2])


def paint_mega_armour(c, faces, rng):
    """Mega armour is a wall with an Ork somewhere inside it: big plates, big rivets, big rust."""
    base(c, faces, METAL, rng, 0.20)
    for name in ("front", "back", "left", "right"):
        face = faces[name]
        c.outline(face, 1, 1, max(1, face[2] - 2), max(1, face[3] - 2), METAL_DARK)
        for i in range(2, face[2] - 1, 3):
            c.fdot(face, i, 1, METAL_LIT)
            c.fdot(face, i, face[3] - 2, METAL_LIT)
        for _ in range(max(2, face[2] * face[3] // 12)):
            c.fdot(face, rng.randrange(face[2]), rng.randrange(face[3]), RUST)
    c.fill(faces["top"], METAL_LIT, FACE_LIGHT["top"])


def paint_klaw(c, faces, rng):
    """The power klaw: rusted iron with a lit edge along the pincer."""
    base(c, faces, METAL_DARK, rng, 0.20)
    for name in ("front", "back", "left", "right"):
        face = faces[name]
        c.hline(face, 0, METAL_LIT, 0, face[2])
        c.hline(face, face[3] - 1, RUST, 0, face[2])
        for j in range(1, face[3] - 1, 2):
            c.hline(face, j, shift(METAL_DARK, -18), 0, face[2])
    c.fill(faces["bottom"], METAL_LIT, FACE_LIGHT["bottom"])


def paint_kan_hull(c, faces, rng):
    """
    A Killa Kan is a boiler with a grot bolted inside. The front gets a vision slit and a clan glyph;
    everything else gets rivets and rust, because a Mek finished it and a Mek does not sand anything.
    """
    base(c, faces, METAL, rng, 0.22)
    for name in ("front", "back", "left", "right"):
        face = faces[name]
        c.outline(face, 0, 0, face[2], face[3], METAL_DARK)
        for i in range(1, face[2] - 1, 3):
            for j in range(1, face[3] - 1, 4):
                c.fdot(face, i, j, METAL_LIT)
        for _ in range(max(3, face[2] * face[3] // 10)):
            c.fdot(face, rng.randrange(face[2]), rng.randrange(face[3]), RUST)

    front = faces["front"]
    # Vision slit — the one dark horizontal that makes the box read as a head.
    c.rect(front, 2, 2, max(2, front[2] - 4), 2, VOID)
    c.hline(front, 2, shift(VOID, 26), 3, front[2] - 3)
    # Clan glyph below it.
    c.rect(front, front[2] // 2 - 1, 6, 3, 3, GLYPH)
    c.fdot(front, front[2] // 2, 7, VOID)


def paint_kan_limb(c, faces, rng):
    base(c, faces, METAL_DARK, rng, 0.20)
    for name in ("front", "back", "left", "right"):
        face = faces[name]
        c.vline(face, 0, METAL_LIT, 0, face[3])
        c.vline(face, face[2] - 1, shift(METAL_DARK, -20), 0, face[3])
        for j in range(0, face[3], 3):
            c.hline(face, j, RUST if j % 6 == 0 else shift(METAL, -22), 0, face[2])


def paint_kan_saw(c, faces, rng):
    """The buzzsaw arm: a toothed disc read as a bright band with notches."""
    base(c, faces, METAL_DARK, rng, 0.16)
    for name in ("front", "back", "left", "right"):
        face = faces[name]
        mid = face[3] // 2
        c.hline(face, mid, METAL_LIT, 0, face[2])
        for i in range(0, face[2], 2):
            c.fdot(face, i, max(0, mid - 1), TEETH)
            c.fdot(face, i, min(face[3] - 1, mid + 1), TEETH)


PAINTERS_ORK = {
    "skin":        paint_ork_skin,
    "head":        lambda c, f, r: paint_ork_head(c, f, r, True),
    "head_small":  lambda c, f, r: paint_ork_head(c, f, r, False),
    "ear":         paint_ear,
    "leather":     paint_leather,
    "armour":      lambda c, f, r: paint_armour(c, f, r, None),
    "armour_trim": lambda c, f, r: paint_armour(c, f, r, GLYPH),
    "mega":        paint_mega_armour,
    "klaw":        paint_klaw,
    "kan_hull":    paint_kan_hull,
    "kan_limb":    paint_kan_limb,
    "kan_saw":     paint_kan_saw,
}


# =============================================================================== models

def nob_bones():
    """
    A Boy scaled up and armoured: 14 wide against the Boy's 12, a head that sits lower between
    bigger shoulders, and a power klaw that makes the silhouette asymmetric — which is the cheapest
    way to make him findable inside a squad of his own Boyz.
    """
    return [
        Bone("body", [0, 12, 0], [
            Cube([-7, 12, -4], [14, 15, 8], "leather"),
        ]),
        Bone("head", [0, 27, 0], [
            Cube([-5, 27, -5], [10, 9, 10], "head"),
        ], parent="body"),
        Bone("right_arm", [-7, 26, 0], [
            Cube([-9, 24, -5], [4, 5, 10], "armour_trim"),   # pauldron
            Cube([-13, 12, -3], [6, 14, 6], "skin"),
        ], parent="body"),
        Bone("left_arm", [7, 26, 0], [
            Cube([5, 24, -5], [4, 5, 10], "armour_trim"),
            Cube([7, 12, -3], [6, 14, 6], "skin"),
        ], parent="body"),
        Bone("klaw", [-10, 12, 0], [
            Cube([-14, 4, -5], [8, 9, 10], "klaw"),
        ], parent="right_arm"),
        Bone("right_leg", [-3, 12, 0], [
            Cube([-6, 0, -3], [6, 12, 6], "skin"),
        ]),
        Bone("left_leg", [3, 12, 0], [
            Cube([0, 0, -3], [6, 12, 6], "skin"),
        ]),
    ]


def meganob_bones():
    """
    Mega armour: the Ork is somewhere inside and you never see him. Everything is plate, the head is
    sunk between shoulder blocks so it barely clears them, and the legs are short — the whole read is
    "wall that walks".
    """
    return [
        Bone("body", [0, 13, 0], [
            Cube([-9, 13, -6], [18, 17, 12], "mega"),
        ]),
        Bone("head", [0, 30, 0], [
            Cube([-4, 29, -4], [8, 7, 8], "head_small"),
        ], parent="body"),
        Bone("right_arm", [-9, 29, 0], [
            Cube([-14, 27, -7], [6, 8, 14], "mega"),          # slab pauldron
            Cube([-15, 15, -4], [7, 13, 8], "mega"),
        ], parent="body"),
        Bone("left_arm", [9, 29, 0], [
            Cube([8, 27, -7], [6, 8, 14], "mega"),
            Cube([8, 15, -4], [7, 13, 8], "mega"),
        ], parent="body"),
        Bone("klaw", [-11, 15, 0], [
            Cube([-16, 6, -6], [9, 10, 12], "klaw"),
        ], parent="right_arm"),
        Bone("right_leg", [-4, 13, 0], [
            Cube([-8, 0, -4], [7, 13, 8], "mega"),
        ]),
        Bone("left_leg", [4, 13, 0], [
            Cube([1, 0, -4], [7, 13, 8], "mega"),
        ]),
    ]


def gretchin_bones():
    """
    All head and ears on a body that is barely there. A grot read as "small Ork" is a failure — the
    proportion is the joke, so the head is nearly as wide as the shoulders and the ears break the
    silhouette on both sides.
    """
    return [
        Bone("body", [0, 6, 0], [
            Cube([-3, 6, -2], [6, 7, 4], "leather"),
        ]),
        Bone("head", [0, 13, 0], [
            Cube([-4, 13, -4], [8, 7, 8], "head"),
        ], parent="body"),
        Bone("right_ear", [-4, 17, 0], [
            Cube([-7, 15, -1], [3, 4, 2], "ear"),
        ], parent="head"),
        Bone("left_ear", [4, 17, 0], [
            Cube([4, 15, -1], [3, 4, 2], "ear"),
        ], parent="head"),
        Bone("right_arm", [-3, 12, 0], [
            Cube([-5, 5, -1], [2, 8, 2], "skin"),
        ], parent="body"),
        Bone("left_arm", [3, 12, 0], [
            Cube([3, 5, -1], [2, 8, 2], "skin"),
        ], parent="body"),
        Bone("right_leg", [-1.5, 6, 0], [
            Cube([-3, 0, -1], [2, 6, 2], "skin"),
        ]),
        Bone("left_leg", [1.5, 6, 0], [
            Cube([1, 0, -1], [2, 6, 2], "skin"),
        ]),
    ]


def killa_kan_bones():
    """
    NOT a humanoid, which is the whole reason it was on the placeholder list. A Kan is a boiler on
    two back-jointed bird legs with tools bolted where arms would be — so the hull is one big block
    with no neck, the legs bend the wrong way, and there is no head at all: the vision slit is on the
    hull.
    """
    return [
        Bone("body", [0, 14, 0], [
            Cube([-7, 14, -6], [14, 14, 12], "kan_hull"),
            Cube([-3, 28, -3], [3, 5, 3], "kan_limb"),        # exhaust stack
            Cube([1, 28, -3], [3, 4, 3], "kan_limb"),
        ]),
        Bone("right_arm", [-7, 24, 0], [
            Cube([-11, 14, -4], [4, 11, 8], "kan_limb"),
        ], parent="body"),
        Bone("left_arm", [7, 24, 0], [
            Cube([7, 14, -4], [4, 11, 8], "kan_limb"),
        ], parent="body"),
        Bone("saw", [-9, 14, 0], [
            Cube([-12, 4, -6], [6, 11, 12], "kan_saw"),
        ], parent="right_arm"),
        Bone("gun", [9, 14, 0], [
            Cube([8, 8, -10], [4, 5, 14], "kan_limb"),
        ], parent="left_arm"),
        # Back-jointed legs: thigh forward, shin back, foot forward again.
        Bone("right_leg", [-4, 14, 0], [
            Cube([-6, 7, -2], [4, 8, 5], "kan_limb"),
            Cube([-6, 1, 1], [4, 7, 4], "kan_limb"),
            Cube([-7, 0, -4], [6, 2, 8], "kan_limb"),
        ]),
        Bone("left_leg", [4, 14, 0], [
            Cube([2, 7, -2], [4, 8, 5], "kan_limb"),
            Cube([2, 1, 1], [4, 7, 4], "kan_limb"),
            Cube([1, 0, -4], [6, 2, 8], "kan_limb"),
        ]),
    ]


# =============================================================================== animations

def rot(frames):
    return {"rotation": frames}


def ork_animations(swing_bone="right_arm", heavy=False):
    """
    The Ork walk: a swagger with real shoulder roll. `heavy` slows it and adds a lean, which is what
    separates a Meganob's trudge from a Boy's jog without a second set of numbers.
    """
    speed = 1.6 if heavy else 1.0
    lean = 6 if heavy else 3

    return {
        "format_version": "1.8.0",
        "animations": {
            "idle": {
                "loop": True,
                "animation_length": 3.0,
                "bones": {
                    "body": rot({"0.0": [lean, 0, 0], "1.5": [lean + 1, 0, 0], "3.0": [lean, 0, 0]}),
                    "head": rot({"0.0": [0, -7, 0], "1.5": [0, 7, 0], "3.0": [0, -7, 0]}),
                    "right_arm": rot({"0.0": [0, 0, 3], "1.5": [-3, 0, 4], "3.0": [0, 0, 3]}),
                    "left_arm": rot({"0.0": [0, 0, -3], "1.5": [-3, 0, -4], "3.0": [0, 0, -3]}),
                },
            },
            "walk": {
                "loop": True,
                "animation_length": 1.0 * speed,
                "bones": {
                    "body": rot({"0.0": [lean, -4, 0], "%.1f" % (0.5 * speed): [lean, 4, 0],
                                 "%.1f" % (1.0 * speed): [lean, -4, 0]}),
                    "right_leg": rot({"0.0": [-30, 0, 0], "%.1f" % (0.5 * speed): [30, 0, 0],
                                      "%.1f" % (1.0 * speed): [-30, 0, 0]}),
                    "left_leg": rot({"0.0": [30, 0, 0], "%.1f" % (0.5 * speed): [-30, 0, 0],
                                     "%.1f" % (1.0 * speed): [30, 0, 0]}),
                    "right_arm": rot({"0.0": [26, 0, 3], "%.1f" % (0.5 * speed): [-26, 0, 3],
                                      "%.1f" % (1.0 * speed): [26, 0, 3]}),
                    "left_arm": rot({"0.0": [-26, 0, -3], "%.1f" % (0.5 * speed): [26, 0, -3],
                                     "%.1f" % (1.0 * speed): [-26, 0, -3]}),
                },
            },
            "attack": {
                "loop": False,
                "animation_length": 0.7,
                "bones": {
                    swing_bone: rot({"0.0": [0, 0, 0], "0.2": [-110, 0, 20], "0.45": [35, 0, -10],
                                     "0.7": [0, 0, 0]}),
                    "body": rot({"0.0": [lean, 0, 0], "0.2": [lean - 6, -14, 0],
                                 "0.45": [lean + 8, 12, 0], "0.7": [lean, 0, 0]}),
                },
            },
        },
    }


def kan_animations():
    """
    A walker does not swagger. The body stays level and the legs do the work, with a heavy settle on
    each footfall — that settle is the only thing that sells weight at this scale.
    """
    return {
        "format_version": "1.8.0",
        "animations": {
            "idle": {
                "loop": True,
                "animation_length": 4.0,
                "bones": {
                    "body": rot({"0.0": [0, 0, 0], "2.0": [1, 0, 0], "4.0": [0, 0, 0]}),
                    "saw": rot({"0.0": [0, 0, 0], "2.0": [-4, 0, 0], "4.0": [0, 0, 0]}),
                },
            },
            "walk": {
                "loop": True,
                "animation_length": 1.4,
                "bones": {
                    "body": rot({"0.0": [0, 0, 2], "0.35": [2, 0, 0], "0.7": [0, 0, -2],
                                 "1.05": [2, 0, 0], "1.4": [0, 0, 2]}),
                    "right_leg": rot({"0.0": [-26, 0, 0], "0.7": [26, 0, 0], "1.4": [-26, 0, 0]}),
                    "left_leg": rot({"0.0": [26, 0, 0], "0.7": [-26, 0, 0], "1.4": [26, 0, 0]}),
                    "right_arm": rot({"0.0": [6, 0, 0], "0.7": [-6, 0, 0], "1.4": [6, 0, 0]}),
                    "left_arm": rot({"0.0": [-6, 0, 0], "0.7": [6, 0, 0], "1.4": [-6, 0, 0]}),
                },
            },
            "attack": {
                "loop": False,
                "animation_length": 0.8,
                "bones": {
                    "right_arm": rot({"0.0": [0, 0, 0], "0.25": [-70, 0, 0], "0.55": [20, 0, 0],
                                      "0.8": [0, 0, 0]}),
                    "saw": rot({"0.0": [0, 0, 0], "0.25": [0, 0, 40], "0.55": [0, 0, -30],
                                "0.8": [0, 0, 0]}),
                    "body": rot({"0.0": [0, 0, 0], "0.25": [-4, -10, 0], "0.55": [4, 8, 0],
                                 "0.8": [0, 0, 0]}),
                },
            },
        },
    }


MODELS = [
    ("ork_nob",   nob_bones,       lambda: ork_animations("klaw"),          128, 5001),
    ("meganob",   meganob_bones,   lambda: ork_animations("klaw", True),    128, 5002),
    ("gretchin",  gretchin_bones,  lambda: ork_animations("right_arm"),      64, 5003),
    ("killa_kan", killa_kan_bones, kan_animations,                          128, 5004),
]


def emit_texture_ork(bones, sheet, seed):
    rng = random.Random(seed)
    c = Canvas(sheet)

    for bone in bones:
        for cube in bone.cubes:
            PAINTERS_ORK[cube.paint](c, cube.net(), rng)

    return c


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--sheet", action="store_true")
    args = parser.parse_args()

    sheets = []

    for name, bones_fn, anim_fn, sheet, seed in MODELS:
        bones = pack(bones_fn(), sheet)

        write_json(os.path.join(ASSETS, "geo", name + ".geo.json"), emit_geo(name, bones, sheet))
        write_json(os.path.join(ASSETS, "animations", name + ".animation.json"), anim_fn())

        canvas = emit_texture_ork(bones, sheet, seed)
        path = os.path.join(ASSETS, "textures", "entity", name + ".png")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        canvas.img.save(path)

        cubes = sum(len(bone.cubes) for bone in bones)
        print("%-12s %2d cubos  folha %dx%d" % (name, cubes, sheet, sheet))
        sheets.append((name, canvas.img))

    if args.sheet:
        pad = 8
        width = sum(img.width for _, img in sheets) + pad * (len(sheets) + 1)
        height = max(img.height for _, img in sheets) + pad * 2
        contact = Image.new("RGBA", (width, height), (18, 20, 18, 255))
        x = pad
        for _, img in sheets:
            contact.paste(img, (x, pad), img)
            x += img.width + pad
        out = os.path.join(HERE, "ork_sheet.png")
        contact.save(out)
        print("folha de contacto:", out)


if __name__ == "__main__":
    main()
