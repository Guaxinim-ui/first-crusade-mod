#!/usr/bin/env python3
"""
Pixel art for the Imperial troops — 64x64, painted straight onto the vanilla humanoid UV.

WHY THIS IS A SCRIPT AND NOT A FOLDER OF PNGs
---------------------------------------------
Every Imperial troop in this mod renders through the same geometry: `ModelLayers.ZOMBIE`, i.e.
`HumanoidModel.createMesh`, i.e. the ordinary 64x64 player-ish layout with a hat overlay on the
head. One layout, eleven units, and a dozen more later. Painting them by hand means eleven chances
to put the belt one pixel off between two units that are supposed to be the same regiment; a script
means the belt line is a constant and the difference between two troops is only what is meant to
differ — palette, plates, insignia, wear.

It also means a variant is cheap. `guardsman_1` is not a second drawing, it is the same recipe with
a different seed, so a squad reads as a squad instead of as four clones.

THE UV, MEASURED NOT GUESSED
----------------------------
`HumanoidModel.createMesh` lays the parts out at these texOffs, and `box_net` below turns each into
the six faces of an unwrapped cube:

    head       ( 0,  0)  8 x  8 x 8      hat overlay (32, 0), same net
    body       (16, 16)  8 x 12 x 4
    right arm  (40, 16)  4 x 12 x 4      left arm  (32, 48)
    right leg  ( 0, 16)  4 x 12 x 4      left leg  (16, 48)

Nothing else on the sheet is read by the model, so everything else stays transparent. The hat layer
is opaque wherever a unit wears a helmet and empty where it does not — that is what lets a bare
head (Jungle Fighter) and a sealed one (Kasrkin) share a mesh and still read differently at
distance.

WHAT IS DELIBERATELY NOT DONE HERE
----------------------------------
No runtime generation. This script runs once, at authoring time, and commits PNGs. The game only
ever loads finished files — see ImperialTroopAppearance, which resolves a ResourceLocation and
nothing more.

Usage:  python tools/generate_troop_textures.py [--out <assets dir>] [--sheet]
"""

import argparse
import os
import random

from PIL import Image

# --------------------------------------------------------------------------- UV

def box_net(u, v, w, h, d):
    """The six faces of an unwrapped cube, in Minecraft's own order and orientation."""
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + d + w + d, v + d, w, h),
    }


PARTS = {
    "head": box_net(0, 0, 8, 8, 8),
    "hat": box_net(32, 0, 8, 8, 8),
    "body": box_net(16, 16, 8, 12, 4),
    "right_arm": box_net(40, 16, 4, 12, 4),
    "left_arm": box_net(32, 48, 4, 12, 4),
    "right_leg": box_net(0, 16, 4, 12, 4),
    "left_leg": box_net(16, 48, 4, 12, 4),
}

# Faces that face the camera or the sky get the light; the underside never does.
FACE_LIGHT = {"top": 14, "front": 0, "back": -10, "right": -6, "left": 6, "bottom": -26}


# --------------------------------------------------------------------------- colour

def shift(colour, amount):
    """Lightens or darkens without leaving the palette's hue — plain per-channel, clamped."""
    r, g, b = colour[:3]
    a = colour[3] if len(colour) > 3 else 255
    return (
        max(0, min(255, r + amount)),
        max(0, min(255, g + amount)),
        max(0, min(255, b + amount)),
        a,
    )


def mix(a, b, t):
    return (
        int(a[0] + (b[0] - a[0]) * t),
        int(a[1] + (b[1] - a[1]) * t),
        int(a[2] + (b[2] - a[2]) * t),
        255,
    )


class Canvas:
    """A 64x64 sheet plus the handful of drawing verbs these recipes actually need."""

    def __init__(self, size=64):
        self.img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        self.px = self.img.load()
        self.size = size

    # -- raw ----------------------------------------------------------------
    def dot(self, x, y, colour):
        if 0 <= x < self.size and 0 <= y < self.size:
            self.px[x, y] = colour

    # -- face-local ---------------------------------------------------------
    def fill(self, face, colour, light=0):
        x, y, w, h = face
        c = shift(colour, light)
        for j in range(h):
            for i in range(w):
                self.dot(x + i, y + j, c)

    def fdot(self, face, i, j, colour):
        x, y, w, h = face
        if 0 <= i < w and 0 <= j < h:
            self.dot(x + i, y + j, colour)

    def hline(self, face, j, colour, i0=0, i1=None):
        x, y, w, h = face
        i1 = w if i1 is None else i1
        for i in range(max(0, i0), min(w, i1)):
            self.fdot(face, i, j, colour)

    def vline(self, face, i, colour, j0=0, j1=None):
        x, y, w, h = face
        j1 = h if j1 is None else j1
        for j in range(max(0, j0), min(h, j1)):
            self.fdot(face, i, j, colour)

    def rect(self, face, i0, j0, w, h, colour):
        for j in range(j0, j0 + h):
            for i in range(i0, i0 + w):
                self.fdot(face, i, j, colour)

    def outline(self, face, i0, j0, w, h, colour):
        for i in range(i0, i0 + w):
            self.fdot(face, i, j0, colour)
            self.fdot(face, i, j0 + h - 1, colour)
        for j in range(j0, j0 + h):
            self.fdot(face, i0, j, colour)
            self.fdot(face, i0 + w - 1, j, colour)

    # -- texture ------------------------------------------------------------
    def gradient(self, face, top_shift, bottom_shift):
        """Vertical light falloff, so a flat colour reads as a surface instead of a sticker."""
        x, y, w, h = face
        for j in range(h):
            t = j / max(1, h - 1)
            delta = int(top_shift + (bottom_shift - top_shift) * t)
            for i in range(w):
                self.px[x + i, y + j] = shift(self.px[x + i, y + j], delta)

    def wear(self, face, rng, amount=0.12, dark=-26, light=20):
        """Scratches and grime. Per-pixel, seeded, never enough to become noise."""
        x, y, w, h = face
        for j in range(h):
            for i in range(w):
                if self.px[x + i, y + j][3] == 0:
                    continue
                r = rng.random()
                if r < amount * 0.55:
                    self.px[x + i, y + j] = shift(self.px[x + i, y + j], dark)
                elif r < amount:
                    self.px[x + i, y + j] = shift(self.px[x + i, y + j], light)

    def grime(self, face, rng, colour, amount=0.10, from_bottom=True):
        """Dirt that collects where dirt collects: low on the part, not evenly sprayed."""
        x, y, w, h = face
        for j in range(h):
            bias = (j / max(1, h - 1)) if from_bottom else 1.0
            for i in range(w):
                if self.px[x + i, y + j][3] == 0:
                    continue
                if rng.random() < amount * bias:
                    self.px[x + i, y + j] = mix(self.px[x + i, y + j], colour, 0.45)


# --------------------------------------------------------------------------- shared shapes

def paint_part(c, part, base, rng, wear_amt=0.12, grime_colour=None, grime_amt=0.0):
    """Base coat for a whole cube: per-face lighting, falloff, then wear."""
    for name, face in PARTS[part].items():
        c.fill(face, base, FACE_LIGHT[name])
        c.gradient(face, 4, -8)
        c.wear(face, rng, wear_amt)
        if grime_colour and grime_amt:
            c.grime(face, rng, grime_colour, grime_amt)


def paint_face(c, skin, eyes, rng, brow=None, mouth=True, stubble=None):
    """A head that reads as a person from three blocks away: brow, eyes, jaw shadow."""
    head = PARTS["head"]
    for name, face in head.items():
        c.fill(face, skin, FACE_LIGHT[name])
        c.gradient(face, 3, -6)
        c.wear(face, rng, 0.08)

    front = head["front"]
    dark = shift(skin, -46)
    # Row 4 for the eyes, not row 3: an open helmet's rim sits at rows 2-3, and a face whose eyes
    # are under the rim is a face nobody ever sees.
    if brow:
        c.hline(front, 3, brow, 0, 8)
    # Eyes: two pixels each, with a socket shadow above so they do not float.
    for ex in (1, 5):
        c.rect(front, ex, 4, 2, 1, shift(skin, -60))
        c.fdot(front, ex, 4, eyes)
        c.fdot(front, ex + 1, 4, shift(eyes, -30))
    if mouth:
        c.hline(front, 6, dark, 2, 6)
    if stubble:
        for j in range(5, 8):
            for i in range(8):
                if rng.random() < 0.35:
                    c.fdot(front, i, j, mix(skin, stubble, 0.5))


def helmet(c, shell, rim=None, brim=False, band=None, cover_face=False, lens=None,
           vents=None, rng=None):
    """
    The hat overlay. This is the single strongest silhouette cue the model gives us, so each unit
    that wears one gets a shape, not just a colour: a rim, a brim, a band, a visor, lenses.
    """
    hat = PARTS["hat"]
    rim = rim or shift(shell, -34)

    for name, face in hat.items():
        if name == "bottom":
            continue  # under-chin: never seen, and filling it makes the jaw look boxed
        c.fill(face, shell, FACE_LIGHT[name])
        c.gradient(face, 5, -10)
        if rng:
            c.wear(face, rng, 0.11)

    front = hat["front"]
    # A rim across the brow reads as "helmet" rather than "head painted green". It stops at row 3
    # so the eyes on row 4 stay clear of it.
    c.hline(front, 2, rim, 0, 8)
    c.hline(front, 3, shift(rim, -14), 0, 8)

    if not cover_face:
        # Open helmet: clear the face below the rim so the head underneath shows through.
        for j in range(4, 8):
            for i in range(8):
                c.fdot(front, i, j, (0, 0, 0, 0))
        for side in ("left", "right"):
            for j in range(5, 8):
                for i in range(8):
                    c.fdot(hat[side], i, j, (0, 0, 0, 0))
    else:
        # Sealed helmet: a dark visor band and, if asked, lenses inside it.
        c.rect(front, 0, 4, 8, 2, shift(shell, -64))
        if lens:
            for ex in (1, 5):
                c.rect(front, ex, 4, 2, 1, lens)
                c.fdot(front, ex, 4, shift(lens, 40))
        if vents:
            c.rect(front, 2, 6, 4, 2, vents)
            c.fdot(front, 3, 6, shift(vents, -30))
            c.fdot(front, 4, 7, shift(vents, -30))

    if brim:
        # A brim lives on the top face's leading edge — the peaked-cap / bush-hat read.
        c.hline(hat["top"], 7, shift(shell, -20), 0, 8)
        c.hline(front, 2, shift(shell, -44), 0, 8)

    if band:
        # Above the rim, never on it — a band drawn at the rim's row just repaints the rim.
        for name in ("front", "left", "right", "back"):
            c.hline(hat[name], 1, band, 0, 8)


def belt(c, colour, j=7, buckle=None):
    """Waist line across all four sides of the torso, so it does not stop at the seams."""
    body = PARTS["body"]
    for name in ("front", "left", "right", "back"):
        c.hline(body[name], j, colour, 0, 8)
        c.hline(body[name], j + 1, shift(colour, -22), 0, 8)
    if buckle:
        c.rect(body["front"], 3, 7, 2, 2, buckle)


def straps(c, colour):
    """The X of a webbing harness across the chest — cheap, and instantly military."""
    front = PARTS["body"]["front"]
    for k in range(7):
        c.fdot(front, 1 + k, k, colour)
        c.fdot(front, 6 - k, k, shift(colour, -14))


def pouches(c, colour, rows=((1, 8), (5, 8)), trim=None):
    front = PARTS["body"]["front"]
    for (i, j) in rows:
        c.rect(front, i, j, 2, 3, colour)
        c.outline(front, i, j, 2, 3, shift(colour, -34))
        if trim:
            c.fdot(front, i, j, trim)


def shoulder(c, arm, colour, height=3):
    """A pauldron: the top of the arm cube plus the first rows of all four sides."""
    part = PARTS[arm]
    c.fill(part["top"], colour, FACE_LIGHT["top"])
    for name in ("front", "back", "left", "right"):
        for j in range(height):
            c.hline(part[name], j, shift(colour, FACE_LIGHT[name]), 0, 4)
        c.hline(part[name], height, shift(colour, -40), 0, 4)


def glove(c, arm, colour, height=3):
    part = PARTS[arm]
    c.fill(part["bottom"], colour, FACE_LIGHT["bottom"])
    for name in ("front", "back", "left", "right"):
        for j in range(12 - height, 12):
            c.hline(part[name], j, shift(colour, FACE_LIGHT[name]), 0, 4)


def boot(c, leg, colour, height=4, sole=None):
    part = PARTS[leg]
    c.fill(part["bottom"], sole or shift(colour, -40), FACE_LIGHT["bottom"])
    for name in ("front", "back", "left", "right"):
        for j in range(12 - height, 12):
            c.hline(part[name], j, shift(colour, FACE_LIGHT[name]), 0, 4)
        c.hline(part[name], 12 - height, shift(colour, -30), 0, 4)


def knee_pads(c, colour):
    for leg in ("right_leg", "left_leg"):
        c.rect(PARTS[leg]["front"], 0, 5, 4, 2, colour)
        c.hline(PARTS[leg]["front"], 7, shift(colour, -32), 0, 4)


def aquila(c, face, i, j, colour):
    """A two-headed eagle small enough to survive 64x64: five pixels and a spine."""
    c.fdot(face, i + 1, j, colour)
    c.fdot(face, i + 3, j, colour)
    c.fdot(face, i + 2, j + 1, colour)
    c.fdot(face, i, j + 1, colour)
    c.fdot(face, i + 4, j + 1, colour)
    c.fdot(face, i + 2, j + 2, colour)


def chevrons(c, face, i, j, colour, count=2):
    for k in range(count):
        y = j + k * 2
        c.fdot(face, i, y + 1, colour)
        c.fdot(face, i + 1, y, colour)
        c.fdot(face, i + 2, y + 1, colour)


def digits(c, face, i, j, colour, n=3):
    """A stencilled squad/prisoner number. Not readable, and not meant to be — it reads as ink."""
    for k in range(n):
        c.rect(face, i + k * 2, j, 1, 3, colour)


# --------------------------------------------------------------------------- recipes
#
# One function per troop. Each is handed a Canvas and a seeded RNG; the seed is the variant index,
# so variant 1 is the same uniform worn by a different soldier, never a different uniform.

def guardsman(c, rng, v, grade="line"):
    green = (74, 90, 52)
    plate = (62, 74, 44)
    leather = (75, 58, 37)
    skin = [(200, 154, 110), (170, 124, 84), (140, 100, 68)][v % 3]

    paint_part(c, "body", green, rng, 0.13, (58, 52, 40), 0.10)
    for arm in ("right_arm", "left_arm"):
        paint_part(c, arm, green, rng, 0.13, (58, 52, 40), 0.08)
    for leg in ("right_leg", "left_leg"):
        paint_part(c, leg, (62, 74, 46), rng, 0.13, (52, 45, 34), 0.16)

    # Flak plate over the chest: a lighter slab with a hard, dark bottom edge. The contrast is
    # deliberately strong — at twenty blocks the plate is the only thing separating a Guardsman's
    # torso from a green rectangle.
    c.rect(PARTS["body"]["front"], 1, 0, 6, 7, plate)
    c.outline(PARTS["body"]["front"], 1, 0, 6, 7, (28, 34, 20))
    c.hline(PARTS["body"]["front"], 7, (28, 34, 20), 0, 8)
    straps(c, shift(leather, 10))
    belt(c, leather, 7, (150, 128, 70))
    pouches(c, leather)
    boot(c, "right_leg", (52, 42, 30))
    boot(c, "left_leg", (52, 42, 30))
    shoulder(c, "right_arm", plate)
    shoulder(c, "left_arm", plate)

    paint_face(c, skin, (60, 70, 90), rng, brow=shift(skin, -34), stubble=(70, 58, 46) if v else None)
    helmet(c, (85, 100, 59), rng=rng)

    if grade == "line":
        aquila(c, PARTS["body"]["front"], 2, 2, (150, 128, 70))
        digits(c, PARTS["right_arm"]["left"], 0, 5, (190, 190, 180), 2)
    return c


def guardsman_veteran(c, rng, v):
    guardsman(c, rng, v, grade="vet")
    red = (122, 42, 34)
    bronze = (154, 123, 58)
    # Red shoulder cloth on one side only — the asymmetry is what catches the eye in a line.
    shoulder(c, "left_arm", red, 4)
    c.hline(PARTS["left_arm"]["front"], 4, shift(red, -40), 0, 4)
    aquila(c, PARTS["body"]["front"], 2, 2, bronze)
    # Kill markings down the right arm: single pixels with gaps, so they read as tally strokes
    # rather than as a white stripe.
    for k in range(3):
        c.fdot(PARTS["right_arm"]["front"], 1, 6 + k * 2, (204, 198, 182))
    for face in PARTS["body"].values():
        c.wear(face, rng, 0.20)
    return c


def guardsman_sergeant(c, rng, v):
    guardsman(c, rng, v, grade="sgt")
    red = (138, 46, 36)
    bronze = (168, 134, 62)
    shoulder(c, "left_arm", red, 4)
    shoulder(c, "right_arm", (44, 54, 32), 4)
    chevrons(c, PARTS["right_arm"]["front"], 0, 5, bronze, 2)
    aquila(c, PARTS["body"]["front"], 2, 2, bronze)
    c.rect(PARTS["body"]["front"], 1, 1, 6, 1, red)
    belt(c, (60, 46, 30), 7, bronze)
    helmet(c, (72, 86, 50), band=red, rng=rng)
    # A sergeant carries the squad's vox: a box on the back with an antenna up the shoulder.
    c.rect(PARTS["body"]["back"], 2, 2, 4, 5, (48, 56, 36))
    c.outline(PARTS["body"]["back"], 2, 2, 4, 5, (30, 36, 22))
    c.vline(PARTS["left_arm"]["back"], 1, (30, 32, 28), 0, 5)
    return c


def kasrkin(c, rng, v):
    carapace = (44, 52, 38)
    dark = (23, 26, 22)
    lens = (79, 216, 210)

    paint_part(c, "body", carapace, rng, 0.10)
    for arm in ("right_arm", "left_arm"):
        paint_part(c, arm, dark, rng, 0.10)
    for leg in ("right_leg", "left_leg"):
        paint_part(c, leg, dark, rng, 0.10, (40, 40, 36), 0.10)

    # Big carapace slabs: chest, back, thighs. Heavier plating is the whole read.
    c.rect(PARTS["body"]["front"], 0, 0, 8, 7, carapace)
    c.outline(PARTS["body"]["front"], 0, 0, 8, 7, dark)
    c.hline(PARTS["body"]["front"], 3, shift(carapace, -22), 0, 8)
    c.rect(PARTS["body"]["back"], 1, 1, 6, 6, shift(carapace, -14))
    belt(c, dark, 8)
    pouches(c, (36, 42, 32), ((0, 9), (6, 9)))
    shoulder(c, "right_arm", carapace, 5)
    shoulder(c, "left_arm", carapace, 5)
    glove(c, "right_arm", (26, 28, 24))
    glove(c, "left_arm", (26, 28, 24))
    knee_pads(c, carapace)
    boot(c, "right_leg", (26, 28, 24), 3)
    boot(c, "left_leg", (26, 28, 24), 3)

    paint_face(c, (170, 124, 84), (60, 70, 90), rng, mouth=False)
    helmet(c, (38, 45, 33), cover_face=True, lens=lens, vents=(26, 28, 24), rng=rng)
    # Rebreather hose over the shoulder, and a rank pip for variant 1.
    c.vline(PARTS["body"]["front"], 6, (30, 32, 28), 0, 4)
    if v:
        c.fdot(PARTS["body"]["front"], 1, 1, (168, 134, 62))
    return c


def skitarii_ranger(c, rng, v):
    red = (122, 31, 26)
    deep = (76, 20, 16)
    steel = (110, 110, 118)
    bronze = (154, 123, 58)
    glow = (110, 224, 110)

    paint_part(c, "body", red, rng, 0.14, (48, 30, 24), 0.10)
    for arm in ("right_arm", "left_arm"):
        paint_part(c, arm, deep, rng, 0.14)
    for leg in ("right_leg", "left_leg"):
        paint_part(c, leg, deep, rng, 0.14, (44, 36, 30), 0.14)

    # The right arm is the augmetic one: bare steel with a bronze band.
    paint_part(c, "right_arm", steel, rng, 0.18)
    c.rect(PARTS["right_arm"]["front"], 0, 4, 4, 1, bronze)
    c.rect(PARTS["right_arm"]["back"], 0, 4, 4, 1, shift(bronze, -20))
    glove(c, "right_arm", (72, 72, 80), 2)

    # Robe panel and a bronze breastplate.
    c.rect(PARTS["body"]["front"], 2, 0, 4, 6, bronze)
    c.outline(PARTS["body"]["front"], 2, 0, 4, 6, shift(bronze, -44))
    c.rect(PARTS["body"]["front"], 3, 2, 2, 2, deep)
    belt(c, (58, 46, 34), 8)
    # Cabling down both sides of the torso.
    for i in (0, 7):
        c.vline(PARTS["body"]["front"], i, (34, 32, 34), 0, 12)
    c.vline(PARTS["body"]["back"], 3, (34, 32, 34), 0, 10)
    c.vline(PARTS["body"]["back"], 4, shift(steel, -50), 0, 10)
    boot(c, "right_leg", (70, 70, 78), 4)
    boot(c, "left_leg", (70, 70, 78), 4)
    knee_pads(c, steel)

    # A face that is mostly mask: steel jaw, one green optic cluster.
    for name, face in PARTS["head"].items():
        c.fill(face, shift(steel, -28), FACE_LIGHT[name])
        c.gradient(face, 4, -8)
        c.wear(face, rng, 0.14)
    front = PARTS["head"]["front"]
    c.rect(front, 0, 3, 8, 3, (46, 46, 52))
    c.rect(front, 1, 4, 2, 1, glow)
    c.fdot(front, 5, 4, glow)
    c.fdot(front, 6, 4, shift(glow, -60))
    c.rect(front, 2, 6, 4, 2, (58, 58, 64))
    c.hline(front, 1, bronze, 0, 8)
    helmet(c, deep, rng=rng, band=bronze)
    # Mechanicus hood: keep the crown, drop the face so the mask shows.
    for j in range(4, 8):
        for i in range(8):
            c.fdot(PARTS["hat"]["front"], i, j, (0, 0, 0, 0))
    if v:
        c.vline(PARTS["body"]["back"], 6, (34, 32, 34), 0, 8)
    return c


def sister_of_battle(c, rng, v):
    black = (26, 26, 31)
    plate = (38, 38, 45)
    white = (216, 212, 204)
    red = (140, 31, 31)
    gold = (200, 162, 60)

    paint_part(c, "body", black, rng, 0.09)
    for arm in ("right_arm", "left_arm"):
        paint_part(c, arm, plate, rng, 0.09)
    for leg in ("right_leg", "left_leg"):
        paint_part(c, leg, black, rng, 0.09, (48, 48, 52), 0.08)

    # Power-armour chest, then the white tabard that hangs over it. The gold is trim on the
    # shoulders and the neck only: outlining the whole slab turned the torso into a picture frame.
    c.rect(PARTS["body"]["front"], 0, 0, 8, 7, plate)
    c.hline(PARTS["body"]["front"], 0, gold, 0, 8)
    c.hline(PARTS["body"]["front"], 6, shift(plate, -34), 0, 8)
    c.vline(PARTS["body"]["front"], 0, shift(plate, -30), 0, 7)
    c.vline(PARTS["body"]["front"], 7, shift(plate, -30), 0, 7)
    aquila(c, PARTS["body"]["front"], 1, 2, gold)
    c.rect(PARTS["body"]["front"], 2, 6, 4, 6, white)
    c.vline(PARTS["body"]["front"], 2, shift(white, -70), 6, 12)
    c.vline(PARTS["body"]["front"], 5, shift(white, -70), 6, 12)
    c.rect(PARTS["body"]["front"], 3, 8, 2, 3, red)
    c.rect(PARTS["body"]["back"], 1, 1, 6, 6, plate)
    c.outline(PARTS["body"]["back"], 1, 1, 6, 6, gold)
    belt(c, gold, 6)
    shoulder(c, "right_arm", black, 5)
    shoulder(c, "left_arm", black, 5)
    c.hline(PARTS["right_arm"]["front"], 5, gold, 0, 4)
    c.hline(PARTS["left_arm"]["front"], 5, gold, 0, 4)
    # Purity seal: red wax and a strip of parchment down the left arm.
    c.fdot(PARTS["left_arm"]["front"], 1, 6, red)
    c.rect(PARTS["left_arm"]["front"], 1, 7, 1, 3, white)
    glove(c, "right_arm", black, 3)
    glove(c, "left_arm", black, 3)
    boot(c, "right_leg", plate, 4)
    boot(c, "left_leg", plate, 4)

    paint_face(c, (206, 168, 132), (110, 140, 170), rng, brow=(120, 70, 50), mouth=True)
    # Hair rather than a helmet: the Sororitas silhouette is bare-headed under a halo band.
    hat = PARTS["hat"]
    for name, face in hat.items():
        if name == "bottom":
            continue
        c.fill(face, (58, 34, 24) if v == 0 else (30, 26, 24), FACE_LIGHT[name])
        c.gradient(face, 6, -10)
        c.wear(face, rng, 0.10)
    for j in range(3, 8):
        for i in range(1, 7):
            c.fdot(hat["front"], i, j, (0, 0, 0, 0))
    c.hline(hat["front"], 2, gold, 0, 8)
    c.hline(hat["back"], 2, gold, 0, 8)
    c.fdot(hat["front"], 3, 1, gold)
    c.fdot(hat["front"], 4, 1, gold)
    return c


def penal_legionnaire(c, rng, v):
    cloth = (86, 82, 74)
    brown = (90, 70, 50)
    orange = (168, 90, 36)
    rust = (96, 60, 34)

    paint_part(c, "body", cloth, rng, 0.22, (54, 48, 40), 0.26)
    for arm in ("right_arm", "left_arm"):
        paint_part(c, arm, cloth, rng, 0.22, (54, 48, 40), 0.22)
    for leg in ("right_leg", "left_leg"):
        paint_part(c, leg, brown, rng, 0.22, (46, 40, 32), 0.30)

    # One scavenged plate, on one shoulder only. Everything else is cloth.
    shoulder(c, "right_arm", (92, 96, 92), 3)
    c.hline(PARTS["right_arm"]["front"], 3, (52, 54, 52), 0, 4)
    # Prisoner number stencilled on the chest, over an orange band.
    c.rect(PARTS["body"]["front"], 0, 1, 8, 2, orange)
    c.wear(PARTS["body"]["front"], rng, 0.30)
    digits(c, PARTS["body"]["front"], 1, 4, (222, 218, 210), 3)
    c.rect(PARTS["body"]["back"], 1, 2, 6, 2, orange)
    belt(c, rust, 8)
    # Torn hem: bite a few pixels out of the bottom of the tunic and the sleeves.
    for i in range(8):
        if rng.random() < 0.5:
            c.fdot(PARTS["body"]["front"], i, 11, (0, 0, 0, 0))
        if rng.random() < 0.5:
            c.fdot(PARTS["body"]["back"], i, 11, (0, 0, 0, 0))
    boot(c, "right_leg", (58, 48, 38), 3)
    boot(c, "left_leg", (58, 48, 38), 3)

    paint_face(c, (166, 122, 84), (80, 80, 76), rng, stubble=(52, 42, 34))
    # A shock collar instead of a helmet: dark band with a live red light.
    hat = PARTS["hat"]
    for name, face in hat.items():
        if name == "bottom":
            continue
        for j in range(8):
            for i in range(8):
                c.fdot(face, i, j, (0, 0, 0, 0))
    for name in ("front", "back", "left", "right"):
        c.hline(hat[name], 7, (40, 40, 44), 0, 8)
    c.fdot(hat["front"], 4, 7, (210, 60, 50))
    if v:
        # A rag tied round the head.
        for name in ("front", "back", "left", "right"):
            c.hline(hat[name], 2, (120, 60, 44), 0, 8)
    return c


def jungle_fighter(c, rng, v):
    olive = (62, 90, 42)
    dark = (40, 58, 30)
    brown = (78, 59, 37)
    skin = [(185, 133, 92), (146, 100, 66)][v % 2]

    paint_part(c, "body", olive, rng, 0.16, (48, 46, 30), 0.16)
    # Bare arms below a short sleeve: the tell that separates him from every armoured unit.
    for arm in ("right_arm", "left_arm"):
        paint_part(c, arm, skin, rng, 0.10, (60, 54, 36), 0.10)
        part = PARTS[arm]
        for name in ("front", "back", "left", "right"):
            for j in range(4):
                c.hline(part[name], j, shift(olive, FACE_LIGHT[name]), 0, 4)
            c.hline(part[name], 4, shift(olive, -34), 0, 4)
        c.fill(part["top"], olive, FACE_LIGHT["top"])
    for leg in ("right_leg", "left_leg"):
        paint_part(c, leg, olive, rng, 0.16, (44, 38, 26), 0.24)

    # Camouflage blotches, then webbing and a machete sheath on the hip.
    for name in ("front", "back", "left", "right"):
        face = PARTS["body"][name]
        for _ in range(7):
            bx, by = rng.randrange(0, 7), rng.randrange(0, 11)
            c.rect(face, bx, by, rng.randint(1, 2), rng.randint(1, 2), dark)
        for leg in ("right_leg", "left_leg"):
            lf = PARTS[leg][name]
            for _ in range(3):
                c.rect(lf, rng.randrange(0, 3), rng.randrange(0, 11), 1, rng.randint(1, 2), dark)
    straps(c, brown)
    belt(c, brown, 8)
    c.rect(PARTS["body"]["left"], 1, 8, 2, 4, (60, 50, 36))
    c.fdot(PARTS["body"]["left"], 1, 8, (150, 150, 152))
    pouches(c, brown, ((0, 9), (6, 9)))
    boot(c, "right_leg", (54, 44, 30), 3)
    boot(c, "left_leg", (54, 44, 30), 3)

    paint_face(c, skin, (70, 90, 60), rng, stubble=(54, 40, 30))
    # Bandana, not a helmet: only the crown of the hat layer is used.
    hat = PARTS["hat"]
    for name, face in hat.items():
        if name == "bottom":
            continue
        for j in range(8):
            for i in range(8):
                c.fdot(face, i, j, (0, 0, 0, 0))
    band = (122, 42, 34) if v == 0 else (72, 84, 46)
    for name in ("front", "back", "left", "right"):
        c.hline(hat[name], 2, band, 0, 8)
        c.hline(hat[name], 3, shift(band, -26), 0, 8)
        c.hline(hat[name], 1, shift(band, 14), 0, 8)
    c.fill(hat["top"], shift(band, -10), FACE_LIGHT["top"])
    for j in range(4):
        for i in range(8):
            c.fdot(hat["top"], i, j, (0, 0, 0, 0))
    return c


def mine_guard(c, rng, v):
    grey = (78, 82, 86)
    steel = (110, 110, 118)
    yellow = (176, 139, 34)
    soot = (36, 34, 32)

    paint_part(c, "body", grey, rng, 0.16, soot, 0.20)
    for arm in ("right_arm", "left_arm"):
        paint_part(c, arm, grey, rng, 0.16, soot, 0.18)
    for leg in ("right_leg", "left_leg"):
        paint_part(c, leg, shift(grey, -14), rng, 0.16, soot, 0.28)

    # Hazard stripes on the chest and one shoulder — the industrial read at any distance.
    for i in range(8):
        if (i // 2) % 2 == 0:
            c.rect(PARTS["body"]["front"], i, 1, 1, 2, yellow)
    c.outline(PARTS["body"]["front"], 0, 1, 8, 2, shift(yellow, -60))
    c.rect(PARTS["body"]["front"], 1, 4, 6, 3, steel)
    c.outline(PARTS["body"]["front"], 1, 4, 6, 3, shift(steel, -50))
    digits(c, PARTS["body"]["back"], 2, 3, yellow, 2)
    belt(c, (54, 48, 40), 8, steel)
    shoulder(c, "right_arm", steel, 3)
    shoulder(c, "left_arm", yellow, 3)
    glove(c, "right_arm", (60, 50, 38))
    glove(c, "left_arm", (60, 50, 38))
    knee_pads(c, steel)
    boot(c, "right_leg", (52, 48, 44), 4)
    boot(c, "left_leg", (52, 48, 44), 4)

    paint_face(c, (176, 130, 92), (70, 70, 66), rng, mouth=False)
    # Goggles and a dust filter under a hard hat with a lamp.
    front_head = PARTS["head"]["front"]
    c.rect(front_head, 0, 4, 8, 2, (44, 44, 46))
    for ex in (1, 5):
        c.rect(front_head, ex, 4, 2, 1, (198, 150, 60))
    c.rect(front_head, 2, 6, 4, 2, (92, 92, 96))
    helmet(c, yellow, rng=rng)
    hat = PARTS["hat"]
    c.rect(hat["front"], 3, 0, 2, 2, (232, 226, 170))
    c.hline(hat["front"], 2, soot, 0, 8)
    for face in hat.values():
        c.grime(face, rng, soot, 0.22)
    if v:
        c.rect(PARTS["body"]["back"], 2, 5, 4, 4, steel)
    return c


def feudal_knight(c, rng, v):
    steel = (122, 127, 135)
    iron = (74, 78, 85)
    leather = (78, 59, 37)
    tabard = (110, 31, 28)
    gold = (176, 142, 58)

    paint_part(c, "body", iron, rng, 0.18)
    for arm in ("right_arm", "left_arm"):
        paint_part(c, arm, steel, rng, 0.20)
    for leg in ("right_leg", "left_leg"):
        paint_part(c, leg, iron, rng, 0.18, (52, 44, 36), 0.16)

    # Riveted breastplate under a dark red tabard with a heraldic band.
    c.rect(PARTS["body"]["front"], 0, 0, 8, 6, steel)
    c.outline(PARTS["body"]["front"], 0, 0, 8, 6, shift(steel, -50))
    for i in (1, 6):
        for j in (1, 4):
            c.fdot(PARTS["body"]["front"], i, j, shift(steel, 34))
    c.rect(PARTS["body"]["front"], 2, 6, 4, 6, tabard)
    c.outline(PARTS["body"]["front"], 2, 6, 4, 6, shift(tabard, -46))
    aquila(c, PARTS["body"]["front"], 1, 2, gold)
    c.rect(PARTS["body"]["back"], 2, 2, 4, 8, tabard)
    belt(c, leather, 5, gold)
    # Mail sleeves ending in steel pauldrons.
    shoulder(c, "right_arm", steel, 4)
    shoulder(c, "left_arm", steel, 4)
    for arm in ("right_arm", "left_arm"):
        for name in ("front", "back", "left", "right"):
            for j in range(5, 9):
                for i in range(4):
                    if (i + j) % 2 == 0:
                        c.fdot(PARTS[arm][name], i, j, shift(iron, -16))
    glove(c, "right_arm", leather, 3)
    glove(c, "left_arm", leather, 3)
    boot(c, "right_leg", leather, 4)
    boot(c, "left_leg", leather, 4)
    knee_pads(c, steel)

    paint_face(c, (192, 146, 104), (90, 90, 100), rng, stubble=(60, 46, 36))
    # A great helm: fully closed, with a vision slit instead of lenses.
    helmet(c, steel, cover_face=True, rng=rng)
    hat = PARTS["hat"]
    c.rect(hat["front"], 0, 4, 8, 2, shift(iron, -30))
    c.rect(hat["front"], 1, 4, 6, 1, (18, 18, 20))
    for i in range(3, 5):
        c.vline(hat["front"], i, (18, 18, 20), 6, 8)
    c.hline(hat["top"], 3, gold, 0, 8)
    if v:
        # A plume socket and a bloodied edge on the crest.
        c.rect(hat["top"], 3, 0, 2, 3, tabard)
    for face in PARTS["body"].values():
        c.wear(face, rng, 0.22)
    return c


def agri_militia(c, rng, v):
    khaki = (138, 131, 88)
    brown = (86, 66, 42)
    straw = (176, 158, 96)

    paint_part(c, "body", khaki, rng, 0.16, (72, 62, 44), 0.18)
    for arm in ("right_arm", "left_arm"):
        paint_part(c, arm, khaki, rng, 0.16, (72, 62, 44), 0.14)
    for leg in ("right_leg", "left_leg"):
        paint_part(c, leg, brown, rng, 0.16, (58, 48, 34), 0.24)

    c.rect(PARTS["body"]["front"], 1, 1, 6, 4, shift(khaki, -22))
    c.outline(PARTS["body"]["front"], 1, 1, 6, 4, shift(khaki, -50))
    straps(c, brown)
    belt(c, brown, 7)
    pouches(c, brown, ((0, 8), (6, 8)))
    boot(c, "right_leg", (60, 48, 34), 4)
    boot(c, "left_leg", (60, 48, 34), 4)

    paint_face(c, (198, 152, 108), (80, 100, 70), rng, stubble=(70, 56, 40))
    # A wide straw work hat: brim on the top face, crown only on the sides.
    hat = PARTS["hat"]
    for name, face in hat.items():
        if name == "bottom":
            continue
        c.fill(face, straw, FACE_LIGHT[name])
        c.gradient(face, 6, -12)
        c.wear(face, rng, 0.18)
        for j in range(4, 8):
            for i in range(8):
                c.fdot(face, i, j, (0, 0, 0, 0))
    c.hline(hat["front"], 3, shift(straw, -44), 0, 8)
    c.hline(hat["back"], 3, shift(straw, -44), 0, 8)
    if v:
        for name in ("front", "back", "left", "right"):
            c.hline(hat[name], 2, (96, 70, 44), 0, 8)
    return c


def enforcer(c, rng, v):
    navy = (34, 37, 46)
    plate = (48, 52, 62)
    visor = (28, 32, 40)
    white = (206, 204, 198)
    red = (150, 40, 36)

    paint_part(c, "body", navy, rng, 0.11)
    for arm in ("right_arm", "left_arm"):
        paint_part(c, arm, plate, rng, 0.11)
    for leg in ("right_leg", "left_leg"):
        paint_part(c, leg, navy, rng, 0.11, (44, 44, 48), 0.10)

    c.rect(PARTS["body"]["front"], 0, 0, 8, 7, plate)
    c.outline(PARTS["body"]["front"], 0, 0, 8, 7, shift(plate, -34))
    c.rect(PARTS["body"]["front"], 1, 2, 6, 1, white)
    c.rect(PARTS["body"]["back"], 1, 2, 6, 3, white)
    digits(c, PARTS["body"]["back"], 2, 6, navy, 3)
    belt(c, (24, 26, 32), 8, (150, 150, 154))
    shoulder(c, "right_arm", navy, 5)
    shoulder(c, "left_arm", navy, 5)
    c.hline(PARTS["left_arm"]["front"], 5, red, 0, 4)
    glove(c, "right_arm", (24, 26, 32), 3)
    glove(c, "left_arm", (24, 26, 32), 3)
    knee_pads(c, plate)
    boot(c, "right_leg", (24, 26, 32), 4)
    boot(c, "left_leg", (24, 26, 32), 4)

    paint_face(c, (176, 130, 92), (70, 70, 66), rng, mouth=False)
    helmet(c, navy, cover_face=True, lens=None, vents=(24, 26, 32), rng=rng)
    hat = PARTS["hat"]
    c.rect(hat["front"], 0, 3, 8, 4, visor)
    c.hline(hat["front"], 3, shift(visor, 40), 0, 8)
    c.hline(hat["front"], 2, red if v else white, 0, 8)
    return c


def city_commander(c, rng, v):
    coat = (52, 62, 44)
    dark = (36, 44, 30)
    gold = (200, 162, 60)
    red = (128, 36, 32)
    leather = (70, 52, 34)

    paint_part(c, "body", coat, rng, 0.10)
    for arm in ("right_arm", "left_arm"):
        paint_part(c, arm, coat, rng, 0.10)
    for leg in ("right_leg", "left_leg"):
        paint_part(c, leg, dark, rng, 0.10, (48, 44, 34), 0.12)

    # Greatcoat: a centre seam, gold buttons, a red sash across the chest.
    c.vline(PARTS["body"]["front"], 3, shift(coat, -34), 0, 12)
    c.vline(PARTS["body"]["front"], 4, shift(coat, 14), 0, 12)
    for j in (1, 4, 7, 10):
        c.fdot(PARTS["body"]["front"], 2, j, gold)
        c.fdot(PARTS["body"]["front"], 5, j, gold)
    for k in range(8):
        c.fdot(PARTS["body"]["front"], k, 1 + k // 2, red)
    belt(c, leather, 7, gold)
    shoulder(c, "right_arm", dark, 3)
    shoulder(c, "left_arm", dark, 3)
    for arm in ("right_arm", "left_arm"):
        c.hline(PARTS[arm]["front"], 1, gold, 0, 4)
        c.hline(PARTS[arm]["top"] if False else PARTS[arm]["front"], 2, shift(gold, -50), 0, 4)
    glove(c, "right_arm", leather, 3)
    glove(c, "left_arm", leather, 3)
    boot(c, "right_leg", (44, 36, 26), 5)
    boot(c, "left_leg", (44, 36, 26), 5)

    paint_face(c, (198, 152, 108), (90, 100, 120), rng, brow=(90, 70, 50), stubble=(64, 52, 42))
    # Scar over the right eye on variant 1 — an officer who has been in the line.
    if v:
        for j in range(2, 6):
            c.fdot(PARTS["head"]["front"], 5, j, (150, 96, 78))
    # Peaked cap with a gold band.
    helmet(c, dark, brim=True, band=gold, rng=rng)
    aquila(c, PARTS["hat"]["front"], 1, 0, gold)
    return c


# --------------------------------------------------------------------------- catalogue
#
# (folder, filename stem, painter, how many variants). The Java side mirrors this exactly; if the
# two ever disagree the fallback in ImperialTroopAppearance catches it and warns once.

# Hand-painted by the project owner and kept as variants in their own right. The script must never
# write these: they are art, not output, and a regeneration that clobbered them would be a
# regeneration that silently deleted somebody's work. They are the highest index in their set, so
# adding a generated variant later does not renumber them.
OWNER_AUTHORED = {
    "guardsman_3.png",   # was textures/entity/guardsman.png
    "kasrkin_2.png",     # was textures/entity/kasrkin.png
}

TROOPS = [
    ("guardsman", "guardsman", guardsman, 4),
    ("guardsman", "guardsman_veteran", guardsman_veteran, 2),
    ("guardsman", "guardsman_sergeant", guardsman_sergeant, 2),
    ("kasrkin", "kasrkin", kasrkin, 3),
    ("skitarii_ranger", "skitarii_ranger", skitarii_ranger, 2),
    ("sister_of_battle", "sister_of_battle", sister_of_battle, 2),
    ("penal_legionnaire", "penal_legionnaire", penal_legionnaire, 2),
    ("jungle_fighter", "jungle_fighter", jungle_fighter, 2),
    ("mine_guard", "mine_guard", mine_guard, 2),
    ("feudal_knight", "feudal_knight", feudal_knight, 2),
    ("agri_militia", "agri_militia", agri_militia, 2),
    ("enforcer", "enforcer", enforcer, 2),
    ("city_commander", "city_commander", city_commander, 2),
]


def build(out_root, make_sheet=False):
    written = []
    sheet_imgs = []

    for folder, stem, painter, variants in TROOPS:
        target = os.path.join(out_root, "entity", "imperium", folder)
        os.makedirs(target, exist_ok=True)

        for v in range(variants):
            # The seed is the unit and the variant, never the clock: rerunning this script must
            # produce byte-identical PNGs or every run would be a spurious diff.
            rng = random.Random(f"{stem}:{v}")
            c = Canvas()
            painter(c, rng, v)

            name = f"{stem}_{v}.png"
            if name in OWNER_AUTHORED:
                continue

            path = os.path.join(target, name)
            c.img.save(path)
            written.append(path)
            sheet_imgs.append((f"{stem}_{v}", c.img))

    if make_sheet:
        cell = 64 * 4
        cols = 7
        rows = (len(sheet_imgs) + cols - 1) // cols
        sheet = Image.new("RGB", (cols * cell, rows * cell), (24, 24, 28))
        for idx, (_, im) in enumerate(sheet_imgs):
            sheet.paste(im.resize((cell, cell), Image.NEAREST).convert("RGB"),
                        ((idx % cols) * cell, (idx // cols) * cell))
        sheet.save(os.path.join(os.path.dirname(__file__), "troop_sheet.png"))

    return written


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--out",
        default=os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                             "assets", "firstcrusade", "textures"),
    )
    parser.add_argument("--sheet", action="store_true", help="also write tools/troop_sheet.png")
    args = parser.parse_args()

    files = build(os.path.abspath(args.out), args.sheet)
    print(f"{len(files)} textures written")
    for f in files:
        print("  " + os.path.relpath(f, os.path.abspath(args.out)))
