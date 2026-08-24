#!/usr/bin/env python3
"""
The Necrons: GeckoLib models, UV-matched textures and animations for all three.

WHY THIS SCRIPT OWNS THE UVs
---------------------------
`generate_geo_troop_textures.py` reads a Blockbench model and paints whatever UVs it finds. That is
the right shape when a human made the model. Here nobody did — model and texture are both born in
this file, and the trap documented for the Ork placeholders is exactly what happens when the two
drift: `ork_nob.png` is a 64x64 painted for the vanilla humanoid layout while `ork_boy.geo.json`
uses its own, so pointing one at the other scrambles the mob.

So this script does not read UVs, it **assigns** them: `pack()` walks the cubes, lays each one's
six-face net onto the sheet, and writes the chosen (u, v) into the .geo.json *and* hands the same
rectangles to the painter. There is no second copy of the layout to keep in step, and no way for
them to disagree — a cube that moved cannot be painted in its old place, because its old place no
longer exists as a number anywhere.

THE THREE, AND WHY THESE THREE
------------------------------
`PlanetWarState.NecronStage` already reads SILENT -> SCARABS -> WARRIORS -> TOMB_DEFENCES ->
OVERLORD. The stages name the units. Building anything else first would have been building to a
clock the campaign does not keep.

Reference: the owner's two plates — a Warrior phalanx under a green sky with scarabs underfoot, and
an Overlord with staff, cloak and horned crown.

Usage:  python tools/generate_necron_assets.py [--sheet]
"""

import argparse
import json
import math
import os
import random

from PIL import Image

from generate_troop_textures import Canvas, FACE_LIGHT, mix, shift

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "src", "main", "resources", "assets", "firstcrusade")


# =============================================================================== palette
#
# Read off the plates. Necrodermis is not grey: it is a cold green-black that lifts to a dull
# pewter on the edges, which is why every one of these has more green in it than blue.

VOID        = (26, 32, 28)      # deepest shadow, joints, eye sockets
METAL_DARK  = (48, 58, 50)      # underside plate
METAL       = (78, 90, 78)      # main necrodermis
METAL_LIT   = (116, 130, 112)   # top-lit plate
BONE        = (156, 166, 142)   # skull, ribs, the pale structural parts
GAUSS       = (128, 255, 64)    # the green: gauss tubing, eyes, glyphs
GAUSS_HOT   = (214, 255, 176)   # core of the glow
GAUSS_DEEP  = (58, 150, 30)     # where the glow meets metal
GOLD        = (182, 152, 76)    # dynastic trim, Overlord only
GOLD_DARK   = (118, 96, 44)
CLOAK       = (34, 42, 36)      # the Overlord's hanging mantle
CLOAK_LIT   = (58, 70, 58)


# =============================================================================== geometry
#
# A cube in model space. `origin` is the Blockbench corner (x, y, z) and `size` is (w, h, d).
# `uv` is filled in by pack(); nothing writes it by hand.

class Cube:
    def __init__(self, origin, size, paint):
        self.origin = list(origin)
        self.size = list(size)
        self.paint = paint          # which painter to run over this cube's faces
        self.uv = None

    def net_size(self):
        w, h, d = (int(math.ceil(s)) for s in self.size)
        return 2 * (w + d), d + h

    def net(self):
        """The six faces at their packed position, unwrapped the way Minecraft unwraps them."""
        u, v = self.uv
        w, h, d = (int(math.ceil(s)) for s in self.size)
        return {
            "top":    (u + d,         v,     w, d),
            "bottom": (u + d + w,     v,     w, d),
            "right":  (u,             v + d, d, h),
            "front":  (u + d,         v + d, w, h),
            "left":   (u + d + w,     v + d, d, h),
            "back":   (u + d + w + d, v + d, w, h),
        }


class Bone:
    def __init__(self, name, pivot, cubes, parent=None):
        self.name = name
        self.pivot = list(pivot)
        self.cubes = cubes
        self.parent = parent


def pack(bones, sheet):
    """
    Lays every cube's net onto the sheet, left to right, wrapping into rows.

    Raises rather than overflowing: a model that does not fit is a bug to see now, not a texture
    with cubes silently sharing pixels.
    """
    x = y = row_h = 0

    # Tallest first. Shelf packing wastes a whole row's height on its shortest member, so feeding
    # them in descending height keeps the rows dense — which is what lets the Warrior stay on a
    # 64x64 sheet instead of needing the Overlord's 128.
    every = [cube for bone in bones for cube in bone.cubes]
    every.sort(key=lambda cube: cube.net_size()[1], reverse=True)

    for cube in every:
        if True:
            w, h = cube.net_size()

            if x + w > sheet:
                x, y, row_h = 0, y + row_h + 1, 0

            if y + h > sheet:
                raise SystemExit("modelo nao cabe em %dx%d — aumenta a folha" % (sheet, sheet))

            cube.uv = (x, y)
            x += w + 1
            row_h = max(row_h, h)

    return bones


# =============================================================================== the models

def warrior_bones():
    """
    The rank and file: hunched, thin, and mostly skeleton.

    Proportion notes, because the first pass got this wrong: a torso 8 wide by 10 tall reads as a
    refrigerator at Minecraft scale, not as a skeleton. The fix is to split the trunk into a narrow
    chest over an even narrower spine, so the silhouette pinches at the waist — that pinch is the
    single thing that separates "skeleton" from "robot" at twenty blocks. The head comes down to 5
    and sits forward of the shoulders, which is the hunch.
    """
    return [
        Bone("body", [0, 13, 0], [
            Cube([-3.5, 17, -2.5], [7, 6, 4], "torso"),     # chest / ribcage
            Cube([-2, 12, -2], [4, 5, 3], "spine"),         # the waist pinch
            Cube([-2.5, 9, -1.5], [5, 3, 3], "pelvis"),
        ]),
        Bone("collar", [0, 22, 1], [
            Cube([-4, 20, 1], [8, 5, 2], "collar"),         # vane behind the skull
        ], parent="body"),
        Bone("head", [0, 23, -1], [
            Cube([-2.5, 23, -4], [5, 5, 5], "skull"),
        ], parent="body"),
        Bone("right_arm", [-3.5, 22, 0], [
            Cube([-5.5, 21, -2], [2, 2, 3], "shoulder"),
            Cube([-5, 13, -1], [2, 9, 2], "limb"),
        ], parent="body"),
        Bone("left_arm", [3.5, 22, 0], [
            Cube([3.5, 21, -2], [2, 2, 3], "shoulder"),
            Cube([3, 13, -1], [2, 9, 2], "limb"),
        ], parent="body"),
        Bone("gauss", [-4, 17, -1], [
            Cube([-5, 16, -7], [2, 2, 9], "gauss"),         # flayer, levelled at chest height
        ], parent="right_arm"),
        Bone("right_leg", [-2, 9, 0], [
            Cube([-3, 0, -1], [2, 9, 2], "limb"),
        ]),
        Bone("left_leg", [2, 9, 0], [
            Cube([1, 0, -1], [2, 9, 2], "limb"),
        ]),
    ]


def overlord_bones():
    """
    Taller than a Warrior by a head, and read at distance by three things the plate leans on: the
    crown, the mantle and the staff.

    Same pinch as the Warrior but inverted in emphasis: broad shoulders over a narrow waist, so he
    reads as authority rather than as bulk. Widening the chest without narrowing the waist is what
    made the first pass look like armour instead of a king.
    """
    return [
        Bone("body", [0, 14, 0], [
            Cube([-4.5, 21, -2.5], [9, 7, 5], "torso_lord"),   # chest
            Cube([-2.5, 15, -2], [5, 6, 4], "spine"),          # waist
            Cube([-3, 11, -2], [6, 4, 4], "pelvis"),
        ]),
        Bone("cloak", [0, 27, 2.5], [
            Cube([-5.5, 7, 2.5], [11, 20, 2], "cloak"),
        ], parent="body"),
        Bone("collar", [0, 26, 1], [
            Cube([-5.5, 25, 1], [11, 6, 2], "collar_lord"),
        ], parent="body"),
        Bone("head", [0, 28, -1], [
            Cube([-3, 28, -4.5], [6, 6, 5], "skull_lord"),
            Cube([-4.5, 32, -3.5], [9, 3, 2], "crown"),
        ], parent="body"),
        Bone("right_arm", [-4.5, 27, 0], [
            Cube([-7, 25, -2.5], [3, 3, 4], "shoulder_lord"),
            Cube([-6.5, 16, -1.5], [3, 9, 3], "limb_lord"),
        ], parent="body"),
        Bone("left_arm", [4.5, 27, 0], [
            Cube([4, 25, -2.5], [3, 3, 4], "shoulder_lord"),
            Cube([3.5, 16, -1.5], [3, 9, 3], "limb_lord"),
        ], parent="body"),
        Bone("staff", [-5.5, 26, 0], [
            Cube([-6, 9, -1], [2, 26, 2], "staff"),
        ], parent="right_arm"),
        Bone("right_leg", [-2.5, 11, 0], [
            Cube([-4, 0, -1.5], [3, 11, 3], "limb_lord"),
        ]),
        Bone("left_leg", [2.5, 11, 0], [
            Cube([1, 0, -1.5], [3, 11, 3], "limb_lord"),
        ]),
    ]


def scarab_bones():
    """
    Knee-high and wide. Six legs as two blocks rather than six cubes: at this size individual legs
    are one pixel each and read as noise, while two dark masses under a domed shell read as legs.
    """
    return [
        Bone("body", [0, 3, 0], [
            Cube([-4, 2, -5], [8, 4, 10], "carapace"),
            Cube([-3, 1, -4], [6, 1, 8], "underglow"),
        ]),
        Bone("head", [0, 3, -5], [
            Cube([-2, 2, -7], [4, 3, 2], "scarab_head"),
        ], parent="body"),
        Bone("right_legs", [-4, 2, 0], [
            Cube([-5, 0, -4], [1, 2, 8], "limb"),
        ], parent="body"),
        Bone("left_legs", [4, 2, 0], [
            Cube([4, 0, -4], [1, 2, 8], "limb"),
        ], parent="body"),
    ]


# =============================================================================== painters

def base(c, faces, colour, rng, wear=0.10):
    for name, face in faces.items():
        c.fill(face, colour, FACE_LIGHT[name])
        c.gradient(face, 5, -9)
        c.wear(face, rng, wear, dark=-22, light=16)


def plate_lines(c, faces, colour, step=3):
    """Horizontal seams — what turns a flat fill into something machined."""
    for name in ("front", "back", "left", "right"):
        face = faces[name]
        for j in range(step, face[3], step):
            c.hline(face, j, colour, 0, face[2])


def paint_torso(c, faces, rng):
    base(c, faces, METAL, rng)
    plate_lines(c, faces, shift(METAL, -30), 3)

    # The ribcage: pale bars down the chest and back, which is the Warrior's whole read.
    for name in ("front", "back"):
        face = faces[name]
        for j in range(1, face[3] - 2, 2):
            c.hline(face, j, BONE, 1, face[2] - 1)
            c.hline(face, j + 1, shift(BONE, -40), 1, face[2] - 1)

    c.rect(faces["front"], 3, 3, 2, 3, GAUSS_DEEP)
    c.rect(faces["front"], 3, 4, 2, 1, GAUSS)


def paint_torso_lord(c, faces, rng):
    base(c, faces, METAL_DARK, rng)
    plate_lines(c, faces, shift(METAL_DARK, -24), 4)

    for name in ("front", "back"):
        face = faces[name]
        c.outline(face, 2, 2, face[2] - 4, face[3] - 4, GOLD_DARK)
        c.hline(face, 1, GOLD, 2, face[2] - 2)

    # A dynastic glyph on the chest, the one thing that says "this one gives orders".
    front = faces["front"]
    c.rect(front, 4, 4, 2, 4, GAUSS_DEEP)
    c.rect(front, 3, 5, 4, 1, GAUSS_DEEP)
    c.fdot(front, 4, 5, GAUSS)
    c.fdot(front, 5, 5, GAUSS)


def paint_pelvis(c, faces, rng):
    base(c, faces, METAL_DARK, rng)
    plate_lines(c, faces, VOID, 2)


def paint_skull(c, faces, rng, lord=False):
    """
    The face is the model. Two green sockets on a pale skull, a grille where a jaw would be, and
    the deep shadow around them that keeps the green from washing into the metal.
    """
    base(c, faces, BONE, rng, 0.06)

    face = faces["front"]
    w = face[2]

    # Brow ridge.
    c.hline(face, 0, shift(BONE, -50), 0, w)
    c.hline(face, 1, shift(BONE, -28), 0, w)

    # Eye sockets: a dark pit with the glow sitting inside it.
    left_x, right_x = 1, w - 3
    for ex in (left_x, right_x):
        c.rect(face, ex, 2, 2, 2, VOID)
        c.fdot(face, ex, 2, GAUSS_DEEP)
        c.fdot(face, ex + 1, 2, GAUSS)
        c.fdot(face, ex + 1, 3, GAUSS_HOT if lord else GAUSS)

    # Nasal void and the jaw grille.
    c.rect(face, w // 2 - 1, 3, 2, 1, VOID)
    for j in range(face[3] - 3, face[3]):
        for i in range(1, w - 1):
            c.fdot(face, i, j, VOID if i % 2 else shift(BONE, -34))

    # Cheekbones catch the light.
    c.vline(face, 0, shift(BONE, 22), 2, face[3] - 2)
    c.vline(face, w - 1, shift(BONE, 22), 2, face[3] - 2)


def paint_skull_lord(c, faces, rng):
    paint_skull(c, faces, rng, lord=True)
    for name in ("left", "right"):
        c.hline(faces[name], 0, GOLD, 0, faces[name][2])


def paint_crown(c, faces, rng):
    """The horned crown: gold band, and the horns read as raised corners in the top face."""
    base(c, faces, GOLD, rng, 0.14)
    for name in ("front", "back"):
        face = faces[name]
        c.hline(face, face[3] - 1, GOLD_DARK, 0, face[2])
        for i in range(0, face[2], 3):
            c.vline(face, i, GOLD_DARK, 0, face[3])
    top = faces["top"]
    c.rect(top, 0, 0, 2, top[3], GOLD_DARK)
    c.rect(top, top[2] - 2, 0, 2, top[3], GOLD_DARK)
    c.fdot(faces["front"], faces["front"][2] // 2, 0, GAUSS)


def paint_collar(c, faces, rng, lord=False):
    """The vane behind the head — dark outside, glowing on the inner face."""
    base(c, faces, METAL_DARK if not lord else GOLD_DARK, rng, 0.09)
    inner = faces["front"]
    c.fill(inner, VOID)
    for j in range(inner[3]):
        c.hline(inner, j, mix(GAUSS_DEEP, VOID, j / max(1, inner[3] - 1)), 1, inner[2] - 1)
    if lord:
        c.hline(faces["top"], 0, GOLD, 0, faces["top"][2])


def paint_limb(c, faces, rng, lord=False):
    """Necrodermis over a visible cable core: dark bands with a lit strip down the middle."""
    base(c, faces, METAL if not lord else METAL_DARK, rng, 0.12)
    for name in ("front", "back", "left", "right"):
        face = faces[name]
        for j in range(0, face[3], 2):
            c.hline(face, j, shift(METAL, -34), 0, face[2])
        c.vline(face, face[2] // 2, mix(METAL_LIT, GAUSS_DEEP, 0.25), 0, face[3])
    if lord:
        c.hline(faces["front"], 0, GOLD, 0, faces["front"][2])


def paint_gauss(c, faces, rng):
    """
    The flayer. The plate makes the weapon the brightest thing in the picture, so the tubing gets
    the hot core and the casing stays almost black to hold it.
    """
    base(c, faces, VOID, rng, 0.06)
    for name in ("front", "back", "left", "right", "top"):
        face = faces[name]
        mid = face[3] // 2
        c.hline(face, mid, GAUSS_DEEP, 0, face[2])
        if face[3] > 2:
            c.hline(face, mid - 1, GAUSS, 1, face[2] - 1)
            c.hline(face, mid, GAUSS_HOT, 2, max(3, face[2] - 2))
    # The muzzle end burns hardest.
    c.fill(faces["top"], GAUSS_HOT)
    c.fill(faces["bottom"], GAUSS_DEEP)


def paint_staff(c, faces, rng):
    """A long dark shaft with a green head — read from the top down."""
    base(c, faces, METAL_DARK, rng, 0.08)
    for name in ("front", "back", "left", "right"):
        face = faces[name]
        c.vline(face, face[2] // 2, GOLD_DARK, 0, face[3])
        # The crystal at the head of the staff.
        for j in range(0, 4):
            c.hline(face, j, GAUSS if j % 2 else GAUSS_HOT, 0, face[2])
        c.hline(face, 4, GAUSS_DEEP, 0, face[2])
    c.fill(faces["top"], GAUSS_HOT)


def paint_cloak(c, faces, rng):
    """The mantle: heavy vertical folds, no seams, so it does not read as more plating."""
    base(c, faces, CLOAK, rng, 0.05)
    for name in ("front", "back"):
        face = faces[name]
        for i in range(0, face[2], 2):
            c.vline(face, i, shift(CLOAK, -18), 0, face[3])
            if i + 1 < face[2]:
                c.vline(face, i + 1, CLOAK_LIT, 0, face[3])
        c.hline(face, face[3] - 1, VOID, 0, face[2])
        c.hline(face, 0, GOLD_DARK, 0, face[2])


def paint_carapace(c, faces, rng):
    base(c, faces, METAL_DARK, rng, 0.10)
    top = faces["top"]
    # A domed shell: lighter down the spine, dark at the rim.
    for i in range(top[2]):
        t = abs(i - (top[2] - 1) / 2.0) / max(1.0, (top[2] - 1) / 2.0)
        c.vline(top, i, mix(METAL_LIT, VOID, t), 0, top[3])
    c.hline(top, 0, GAUSS_DEEP, 0, top[2])
    for name in ("left", "right"):
        c.hline(faces[name], faces[name][3] - 1, GAUSS_DEEP, 0, faces[name][2])


def paint_underglow(c, faces, rng):
    """The lit belly the plate shows under every scarab."""
    for name, face in faces.items():
        c.fill(face, GAUSS_DEEP)
    c.fill(faces["bottom"], GAUSS)
    c.fill(faces["front"], GAUSS_HOT)


def paint_scarab_head(c, faces, rng):
    base(c, faces, VOID, rng, 0.05)
    c.fill(faces["front"], GAUSS_DEEP)
    c.fdot(faces["front"], 0, 0, GAUSS)
    c.fdot(faces["front"], faces["front"][2] - 1, 0, GAUSS)


def paint_spine(c, faces, rng):
    """The waist: dark, with the vertebral column showing as a pale ladder front and back."""
    base(c, faces, METAL_DARK, rng, 0.10)
    for name in ("front", "back"):
        face = faces[name]
        mid = face[2] // 2
        for j in range(0, face[3]):
            c.fdot(face, mid, j, BONE if j % 2 == 0 else shift(BONE, -40))
        c.vline(face, 0, VOID, 0, face[3])
        c.vline(face, face[2] - 1, VOID, 0, face[3])


def paint_shoulder(c, faces, rng, lord=False):
    """A capped pauldron so the arm has somewhere to hang from."""
    base(c, faces, METAL if not lord else GOLD_DARK, rng, 0.11)
    c.fill(faces["top"], METAL_LIT if not lord else GOLD, FACE_LIGHT["top"])
    for name in ("front", "back", "left", "right"):
        c.hline(faces[name], 0, shift(METAL_LIT, 10) if not lord else GOLD, 0, faces[name][2])


PAINTERS = {
    "torso":        paint_torso,
    "spine":        paint_spine,
    "shoulder":     lambda c, f, r: paint_shoulder(c, f, r, False),
    "shoulder_lord": lambda c, f, r: paint_shoulder(c, f, r, True),
    "torso_lord":   paint_torso_lord,
    "pelvis":       paint_pelvis,
    "skull":        paint_skull,
    "skull_lord":   paint_skull_lord,
    "crown":        paint_crown,
    "collar":       lambda c, f, r: paint_collar(c, f, r, False),
    "collar_lord":  lambda c, f, r: paint_collar(c, f, r, True),
    "limb":         lambda c, f, r: paint_limb(c, f, r, False),
    "limb_lord":    lambda c, f, r: paint_limb(c, f, r, True),
    "gauss":        paint_gauss,
    "staff":        paint_staff,
    "cloak":        paint_cloak,
    "carapace":     paint_carapace,
    "underglow":    paint_underglow,
    "scarab_head":  paint_scarab_head,
}


# =============================================================================== emit

def emit_geo(name, bones, sheet):
    out = []

    for bone in bones:
        entry = {"name": bone.name, "pivot": bone.pivot}

        if bone.parent:
            entry["parent"] = bone.parent

        entry["cubes"] = [
            {"origin": cube.origin, "size": cube.size, "uv": list(cube.uv)}
            for cube in bone.cubes
        ]
        out.append(entry)

    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry." + name,
                "texture_width": sheet,
                "texture_height": sheet,
                "visible_bounds_width": 4,
                "visible_bounds_height": 4,
                "visible_bounds_offset": [0, 1.5, 0],
            },
            "bones": out,
        }],
    }


def emit_texture(bones, sheet, seed):
    rng = random.Random(seed)
    c = Canvas(sheet)

    for bone in bones:
        for cube in bone.cubes:
            PAINTERS[cube.paint](c, cube.net(), rng)

    return c


def rot(bone, frames):
    return {"rotation": frames}


def warrior_animations():
    """
    The march on the plate is unhurried and identical down the rank — so the walk is a slow, even
    stride with almost no bounce, which is what makes a phalanx of them read as machines rather
    than as a crowd.
    """
    return {
        "format_version": "1.8.0",
        "animations": {
            "idle": {
                "loop": True,
                "animation_length": 4.0,
                "bones": {
                    "body": rot(None, {"0.0": [2, 0, 0], "2.0": [3, 0, 0], "4.0": [2, 0, 0]}),
                    "head": rot(None, {"0.0": [0, 0, 0], "1.5": [0, -6, 0], "3.0": [0, 5, 0], "4.0": [0, 0, 0]}),
                    "gauss": rot(None, {"0.0": [0, 0, 0], "2.0": [-2, 0, 0], "4.0": [0, 0, 0]}),
                },
            },
            "walk": {
                "loop": True,
                "animation_length": 1.2,
                "bones": {
                    "body": rot(None, {"0.0": [4, 0, 0], "0.6": [5, 0, 0], "1.2": [4, 0, 0]}),
                    "right_leg": rot(None, {"0.0": [-22, 0, 0], "0.6": [22, 0, 0], "1.2": [-22, 0, 0]}),
                    "left_leg": rot(None, {"0.0": [22, 0, 0], "0.6": [-22, 0, 0], "1.2": [22, 0, 0]}),
                    "right_arm": rot(None, {"0.0": [-8, 0, 0], "0.6": [-14, 0, 0], "1.2": [-8, 0, 0]}),
                    "left_arm": rot(None, {"0.0": [10, 0, 0], "0.6": [-10, 0, 0], "1.2": [10, 0, 0]}),
                },
            },
            "attack": {
                "loop": False,
                "animation_length": 0.7,
                "bones": {
                    # Not a swing: a Warrior levels the flayer and fires.
                    "right_arm": rot(None, {"0.0": [-8, 0, 0], "0.2": [-78, 0, 0], "0.5": [-74, 0, 0], "0.7": [-8, 0, 0]}),
                    "gauss": rot(None, {"0.0": [0, 0, 0], "0.25": [6, 0, 0], "0.7": [0, 0, 0]}),
                    "head": rot(None, {"0.0": [0, 0, 0], "0.2": [-6, 0, 0], "0.7": [0, 0, 0]}),
                },
            },
        },
    }


def overlord_animations():
    return {
        "format_version": "1.8.0",
        "animations": {
            "idle": {
                "loop": True,
                "animation_length": 5.0,
                "bones": {
                    "body": rot(None, {"0.0": [0, 0, 0], "2.5": [1, 0, 0], "5.0": [0, 0, 0]}),
                    "head": rot(None, {"0.0": [0, -8, 0], "2.5": [0, 8, 0], "5.0": [0, -8, 0]}),
                    "cloak": rot(None, {"0.0": [0, 0, 0], "2.5": [-4, 0, 0], "5.0": [0, 0, 0]}),
                    "staff": rot(None, {"0.0": [0, 0, 0], "2.5": [0, 0, 2], "5.0": [0, 0, 0]}),
                },
            },
            "walk": {
                "loop": True,
                "animation_length": 1.6,
                "bones": {
                    "right_leg": rot(None, {"0.0": [-16, 0, 0], "0.8": [16, 0, 0], "1.6": [-16, 0, 0]}),
                    "left_leg": rot(None, {"0.0": [16, 0, 0], "0.8": [-16, 0, 0], "1.6": [16, 0, 0]}),
                    "left_arm": rot(None, {"0.0": [8, 0, 0], "0.8": [-8, 0, 0], "1.6": [8, 0, 0]}),
                    "cloak": rot(None, {"0.0": [-6, 0, 0], "0.8": [-10, 0, 0], "1.6": [-6, 0, 0]}),
                },
            },
            "attack": {
                "loop": False,
                "animation_length": 1.0,
                "bones": {
                    # The staff comes up and the head follows it — an order given, not a blow struck.
                    "right_arm": rot(None, {"0.0": [0, 0, 0], "0.3": [-96, 0, 0], "0.7": [-92, 0, 0], "1.0": [0, 0, 0]}),
                    "staff": rot(None, {"0.0": [0, 0, 0], "0.3": [0, 0, -18], "1.0": [0, 0, 0]}),
                    "head": rot(None, {"0.0": [0, 0, 0], "0.3": [-10, 0, 0], "1.0": [0, 0, 0]}),
                    "cloak": rot(None, {"0.0": [0, 0, 0], "0.3": [-14, 0, 0], "1.0": [0, 0, 0]}),
                },
            },
        },
    }


def scarab_animations():
    return {
        "format_version": "1.8.0",
        "animations": {
            "idle": {
                "loop": True,
                "animation_length": 1.0,
                "bones": {
                    "body": rot(None, {"0.0": [0, 0, 0], "0.5": [3, 0, 0], "1.0": [0, 0, 0]}),
                },
            },
            "walk": {
                "loop": True,
                "animation_length": 0.4,
                "bones": {
                    # Fast and jittery: a swarm should look like it is boiling along the ground.
                    "body": rot(None, {"0.0": [0, -4, 0], "0.2": [0, 4, 0], "0.4": [0, -4, 0]}),
                    "right_legs": rot(None, {"0.0": [-18, 0, 0], "0.2": [18, 0, 0], "0.4": [-18, 0, 0]}),
                    "left_legs": rot(None, {"0.0": [18, 0, 0], "0.2": [-18, 0, 0], "0.4": [18, 0, 0]}),
                },
            },
            "attack": {
                "loop": False,
                "animation_length": 0.3,
                "bones": {
                    "body": rot(None, {"0.0": [0, 0, 0], "0.15": [-22, 0, 0], "0.3": [0, 0, 0]}),
                    "head": rot(None, {"0.0": [0, 0, 0], "0.15": [16, 0, 0], "0.3": [0, 0, 0]}),
                },
            },
        },
    }


MODELS = [
    ("necron_warrior", warrior_bones, warrior_animations, 64, 4001),
    ("necron_overlord", overlord_bones, overlord_animations, 128, 4002),
    ("necron_scarab", scarab_bones, scarab_animations, 64, 4003),
]


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(data, handle, indent=2)
        handle.write("\n")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--sheet", action="store_true", help="also write a contact sheet preview")
    args = parser.parse_args()

    sheets = []

    for name, bones_fn, anim_fn, sheet, seed in MODELS:
        bones = pack(bones_fn(), sheet)

        write_json(os.path.join(ASSETS, "geo", name + ".geo.json"), emit_geo(name, bones, sheet))
        write_json(os.path.join(ASSETS, "animations", name + ".animation.json"), anim_fn())

        canvas = emit_texture(bones, sheet, seed)
        texture_path = os.path.join(ASSETS, "textures", "entity", name + ".png")
        os.makedirs(os.path.dirname(texture_path), exist_ok=True)
        canvas.img.save(texture_path)

        used = sum(1 for bone in bones for _ in bone.cubes)
        print("%-16s %2d cubos  folha %dx%d" % (name, used, sheet, sheet))
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
        out = os.path.join(HERE, "necron_sheet.png")
        contact.save(out)
        print("folha de contacto:", out)


if __name__ == "__main__":
    main()
