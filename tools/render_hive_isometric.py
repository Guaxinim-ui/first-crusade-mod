#!/usr/bin/env python3
"""Render a lightweight isometric QA preview from a generator's ModuleBuilder grid."""
from pathlib import Path
import runpy
import sys
from PIL import Image, ImageDraw, ImageEnhance, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
TOOLS = Path(__file__).resolve().parent
sys.path.insert(0, str(TOOLS))

if len(sys.argv) not in (3, 4):
    raise SystemExit("usage: render_hive_isometric.py <generator.py> <output.png> [cutaway]")
cutaway = len(sys.argv) == 4 and sys.argv[3].lower() == "cutaway"

ns = runpy.run_path(str(Path(sys.argv[1]).resolve()))
b = ns.get("b")
if b is None:
    raise SystemExit("generator did not expose global ModuleBuilder as 'b'")

AIR = "minecraft:air"
TW, TH, BH = 12, 6, 8
margin = 80
width = (b.sx + b.sz) * TW // 2 + margin * 2
height = (b.sx + b.sz) * TH // 2 + b.sy * BH + margin * 2
im = Image.new("RGB", (width, height), (12, 12, 18))
d = ImageDraw.Draw(im)
ox = width // 2
oy = margin + b.sy * BH

texture_dir = ROOT / "src/main/resources/assets/firstcrusade/textures/block"
cache = {}

def block_id(state):
    core = state.split("|")[0]
    return core.split(":", 1)[1] if ":" in core else core

def avg_color(bid):
    if bid in cache:
        return cache[bid]
    if bid.startswith("marker_"):
        cache[bid] = None
        return None
    path = texture_dir / f"{bid}.png"
    if path.exists():
        tex = Image.open(path).convert("RGBA").resize((16, 16))
        pix = [p for p in tex.getdata() if p[3] > 20]
        if pix:
            c = tuple(sum(p[i] for p in pix) // len(pix) for i in range(3))
        else:
            c = (90, 90, 96)
    else:
        fallbacks = {
            "reinforced_ashcrete": (72, 74, 80), "cracked_reinforced_ashcrete": (61, 62, 68),
            "riveted_steel_block": (82, 87, 95), "rusted_riveted_steel": (115, 70, 43),
            "chain": (120, 120, 126), "air": (12, 12, 18),
        }
        c = fallbacks.get(bid, (85, 82, 92))
    cache[bid] = c
    return c

def shade(c, factor):
    return tuple(max(0, min(255, int(v * factor))) for v in c)

def state_at(x, y, z):
    if not (0 <= x < b.sx and 0 <= y < b.sy and 0 <= z < b.sz):
        return AIR
    return b.grid.get((x, y, z), AIR)

def project(x, y, z):
    return ox + (x - z) * TW // 2, oy + (x + z) * TH // 2 - y * BH

# Draw a faint ground-plane guide.
for q in range(0, b.sx + 1, 8):
    p1 = project(q, 0, 0); p2 = project(q, 0, b.sz)
    d.line([p1, p2], fill=(28, 26, 36), width=1)
for q in range(0, b.sz + 1, 8):
    p1 = project(0, 0, q); p2 = project(b.sx, 0, q)
    d.line([p1, p2], fill=(28, 26, 36), width=1)

blocks = []
for (x, y, z), state in b.grid.items():
    if state == AIR or block_id(state).startswith("marker_"):
        continue
    # QA-only cutaway: remove the two walls facing the isometric camera while preserving floors,
    # stairs and interior furniture. The NBT itself is unchanged.
    if cutaway and y > 2 and (x <= 7 or z <= 6):
        continue
    blocks.append((x + z, y, x, z, state))
blocks.sort()

for _, y, x, z, state in blocks:
    bid = block_id(state)
    c = avg_color(bid)
    if c is None:
        continue
    cx, cy = project(x, y, z)
    top = [(cx, cy - BH), (cx + TW//2, cy - BH + TH//2),
           (cx, cy - BH + TH), (cx - TW//2, cy - BH + TH//2)]
    left = [(cx - TW//2, cy - BH + TH//2), (cx, cy - BH + TH),
            (cx, cy + TH), (cx - TW//2, cy + TH//2)]
    right = [(cx + TW//2, cy - BH + TH//2), (cx, cy - BH + TH),
             (cx, cy + TH), (cx + TW//2, cy + TH//2)]

    # Camera sees the -x and -z faces. Draw only exposed faces.
    if state_at(x - 1, y, z) == AIR:
        d.polygon(left, fill=shade(c, 0.68), outline=shade(c, 0.42))
    if state_at(x, y, z - 1) == AIR:
        d.polygon(right, fill=shade(c, 0.82), outline=shade(c, 0.46))
    if state_at(x, y + 1, z) == AIR:
        d.polygon(top, fill=shade(c, 1.10), outline=shade(c, 0.52))

# Crop empty borders and add a restrained technical frame.
bbox = im.getbbox()
im = im.crop((max(0, bbox[0]-30), max(0, bbox[1]-30), min(im.width, bbox[2]+30), min(im.height, bbox[3]+30)))
im = ImageEnhance.Contrast(im).enhance(1.08)
# Slightly upscale for easier inspection.
im = im.resize((im.width * 2, im.height * 2), Image.Resampling.NEAREST)
out = Path(sys.argv[2]).resolve()
out.parent.mkdir(parents=True, exist_ok=True)
im.save(out)
print(out)
