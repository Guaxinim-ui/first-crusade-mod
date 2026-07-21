#!/usr/bin/env python3
"""Validador estático da Hive City — auditoria distrito -> módulo -> template -> assets.

Roda inteiramente offline (sem Minecraft, sem gradle) sobre os arquivos reais do repositório.
Cobre a lista de erros do spec (docs prompt mestre §19):

  missing texture, missing model, missing blockstate, template not found,
  invalid NBT, duplicate placement, out of bounds (footprint declarado x real),
  socket mismatch, null registry object (referência quebrada), + módulos/NBTs órfãos.

Uso:
    python tools/hive_city_validate.py
Saída:
    resumo no console (PASS/FAIL por categoria) + relatório completo em
    tools/generated/HIVE_CITY_VALIDATION_REPORT.md

Convenções de resolução de ResourceLocation replicadas do código real (não reinventadas):
  - Districts: data/<ns>/hive_districts/<path>.json  (HiveDistricts.java, SimpleJsonResourceReloadListener)
  - Modules:   data/<ns>/hive_modules/<path>.json     (HiveModuleManager.java, idem)
  - Templates: data/<ns>/structures/<path>.nbt        (StructureTemplateManager, convenção vanilla)
  - Blockstates: assets/<ns>/blockstates/<path>.json
  - Models:      assets/<ns>/models/<path>.json        (blockstate model refs already include "block/")
  - Textures:    assets/<ns>/textures/<path>.png       (model texture refs already include "block/")

Checagem de socket (seams) replica EXATAMENTE HiveCommands.districtPlace's touchingFace +
HiveModule.socketAt/fits, à rotação de distrito 0 (rotação global da cidade não afeta costuras
INTERNAS de um distrito — gira o distrito inteiro rigidamente, preservando o encaixe relativo).

Checagem de blockstate/model/textura é "best-effort" por bloco (não por combinação exata de
propriedades): para cada bloco firstcrusade:X usado em algum template, confere que o blockstate
existe e que TODOS os modelos que ele referencia (variants ou multipart) resolvem, e que TODAS
as texturas desses modelos resolvem. Não tenta reconstruir a ordem exata de propriedades que o
Minecraft usaria para casar uma variant (exigiria introspecção da StateDefinition Java) — mas
captura os três defeitos reais que travam o carregamento do jogo: blockstate ausente, modelo
ausente, textura ausente.
"""
import gzip
import io
import json
import os
import struct
import sys
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(ROOT, "src", "main", "resources", "data")
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets")
OUT_MD = os.path.join(ROOT, "tools", "generated", "HIVE_CITY_VALIDATION_REPORT.md")

# The 8 district ids HiveCityLayout.plan() will actually request (spire is optional/only if
# registered, but must exist for the full city per the master spec).
EXPECTED_DISTRICT_IDS = [
    "firstcrusade:south_ash_gate", "firstcrusade:hive_wall_line", "firstcrusade:hive_corner_bastion",
    "firstcrusade:manufactorum", "firstcrusade:hab_stacks", "firstcrusade:administratum",
    "firstcrusade:underhive", "firstcrusade:spire",
]

DIRS = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0),
        "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}

errors = []
warnings = []
info = []


def err(cat, msg):
    errors.append((cat, msg))


def warn(cat, msg):
    warnings.append((cat, msg))


# ------------------------------------------------------------------ ResourceLocation helpers

def rl_split(rl):
    if ":" in rl:
        ns, path = rl.split(":", 1)
    else:
        ns, path = "minecraft", rl
    return ns, path


def district_json_path(rl):
    ns, path = rl_split(rl)
    return os.path.join(DATA, ns, "hive_districts", path + ".json")


def module_json_path(rl):
    ns, path = rl_split(rl)
    return os.path.join(DATA, ns, "hive_modules", path + ".json")


def template_nbt_path(rl):
    ns, path = rl_split(rl)
    return os.path.join(DATA, ns, "structures", path + ".nbt")


def blockstate_path(rl):
    ns, path = rl_split(rl)
    return os.path.join(ASSETS, ns, "blockstates", path + ".json")


def model_path(rl):
    ns, path = rl_split(rl)
    return os.path.join(ASSETS, ns, "models", path + ".json")


def texture_path(rl):
    ns, path = rl_split(rl)
    return os.path.join(ASSETS, ns, "textures", path + ".png")


# ------------------------------------------------------------------ NBT reader (generic, gzip)
# Mirrors the writer in hive_module_lib.py but handles the full tag type range (1..12) so it
# also reads structures authored in-game via /fchive save (which may carry entity/BE data with
# tag types the pure procedural writer never emits).

TAG_END, TAG_BYTE, TAG_SHORT, TAG_INT, TAG_LONG, TAG_FLOAT, TAG_DOUBLE = 0, 1, 2, 3, 4, 5, 6
TAG_BYTE_ARRAY, TAG_STRING, TAG_LIST, TAG_COMPOUND, TAG_INT_ARRAY, TAG_LONG_ARRAY = 7, 8, 9, 10, 11, 12


class NbtReader:
    def __init__(self, buf):
        self.b = buf
        self.i = 0

    def u8(self):
        v = self.b[self.i]; self.i += 1; return v

    def i16(self):
        v = struct.unpack_from(">h", self.b, self.i)[0]; self.i += 2; return v

    def i32(self):
        v = struct.unpack_from(">i", self.b, self.i)[0]; self.i += 4; return v

    def i64(self):
        v = struct.unpack_from(">q", self.b, self.i)[0]; self.i += 8; return v

    def f32(self):
        v = struct.unpack_from(">f", self.b, self.i)[0]; self.i += 4; return v

    def f64(self):
        v = struct.unpack_from(">d", self.b, self.i)[0]; self.i += 8; return v

    def string(self):
        n = self.i16() & 0xFFFF
        s = self.b[self.i:self.i + n].decode("utf-8"); self.i += n; return s

    def payload(self, tag):
        if tag == TAG_BYTE: return self.u8()
        if tag == TAG_SHORT: return self.i16()
        if tag == TAG_INT: return self.i32()
        if tag == TAG_LONG: return self.i64()
        if tag == TAG_FLOAT: return self.f32()
        if tag == TAG_DOUBLE: return self.f64()
        if tag == TAG_BYTE_ARRAY:
            n = self.i32(); v = self.b[self.i:self.i + n]; self.i += n; return v
        if tag == TAG_STRING: return self.string()
        if tag == TAG_LIST:
            et = self.u8(); n = self.i32()
            return [self.payload(et) for _ in range(n)]
        if tag == TAG_COMPOUND:
            d = {}
            while True:
                t = self.u8()
                if t == TAG_END: break
                k = self.string()
                d[k] = (t, self.payload(t))
            return d
        if tag == TAG_INT_ARRAY:
            n = self.i32(); return [self.i32() for _ in range(n)]
        if tag == TAG_LONG_ARRAY:
            n = self.i32(); return [self.i64() for _ in range(n)]
        raise ValueError(f"unknown NBT tag {tag}")

    def root(self):
        t = self.u8()
        if t != TAG_COMPOUND:
            raise ValueError("root is not a compound")
        self.string()  # root name, discarded
        return self.payload(TAG_COMPOUND)


def read_structure_nbt(path):
    """Returns (size:[x,y,z], palette:[state_key,...], blocks:[(pos,palette_idx),...]) or raises."""
    with gzip.open(path, "rb") as f:
        buf = f.read()
    root = NbtReader(buf).root()
    size_tag = root["size"][1]
    size = [v for (_, v) in size_tag]
    palette_tag = root["palette"][1]
    palette = []
    for (_, comp) in palette_tag:
        name = comp["Name"][1]
        if "Properties" in comp:
            props = comp["Properties"][1]
            propstr = ";".join(f"{k}={v}" for k, (_, v) in sorted(props.items()))
            palette.append(f"{name}|{propstr}")
        else:
            palette.append(name)
    blocks = []
    for (_, comp) in root["blocks"][1]:
        pos = [v for (_, v) in comp["pos"][1]]
        pidx = comp["state"][1]
        blocks.append((pos, pidx))
    return size, palette, blocks


# ------------------------------------------------------------------ 1. load districts + modules

def load_all_districts():
    base = os.path.join(DATA, "firstcrusade", "hive_districts")
    out = {}
    for fn in sorted(os.listdir(base)):
        if not fn.endswith(".json"):
            continue
        rl = "firstcrusade:" + fn[:-5]
        with open(os.path.join(base, fn), encoding="utf-8") as f:
            try:
                out[rl] = json.load(f)
            except json.JSONDecodeError as e:
                err("invalid-json", f"district {rl}: {e}")
    return out


def load_all_modules():
    base = os.path.join(DATA, "firstcrusade", "hive_modules")
    out = {}
    for dirpath, _, files in os.walk(base):
        for fn in files:
            if not fn.endswith(".json"):
                continue
            full = os.path.join(dirpath, fn)
            rel = os.path.relpath(full, base).replace(os.sep, "/")[:-5]
            rl = "firstcrusade:" + rel
            with open(full, encoding="utf-8") as f:
                try:
                    out[rl] = json.load(f)
                except json.JSONDecodeError as e:
                    err("invalid-json", f"module {rl}: {e}")
    return out


def all_template_files():
    base = os.path.join(DATA, "firstcrusade", "structures")
    out = set()
    for dirpath, _, files in os.walk(base):
        for fn in files:
            if fn.endswith(".nbt"):
                full = os.path.join(dirpath, fn)
                rel = os.path.relpath(full, base).replace(os.sep, "/")[:-4]
                out.add("firstcrusade:" + rel)
    return out


# ------------------------------------------------------------------ 2. reference resolution

def check_references(districts, modules, templates_on_disk):
    referenced_modules = set()
    for did, dj in districts.items():
        seen_offsets = {}
        for entry in dj.get("modules", []):
            mrl = entry["module"]
            referenced_modules.add(mrl)
            if mrl not in modules:
                err("missing-module", f"district {did} -> module {mrl} has no hive_modules/*.json")
                continue
            off = tuple(entry["offset"])
            if off in seen_offsets:
                err("duplicate-placement",
                    f"district {did}: modules {seen_offsets[off]} and {mrl} share offset {off}")
            seen_offsets[off] = mrl

    referenced_templates = set()
    for mid, mj in modules.items():
        trl = mj.get("template")
        if trl is None:
            err("bad-module", f"module {mid} has no 'template' field")
            continue
        referenced_templates.add(trl)
        if trl not in templates_on_disk:
            err("template-not-found", f"module {mid} -> template {trl} (expected {template_nbt_path(trl)})")

    orphan_modules = set(modules) - referenced_modules
    for mid in sorted(orphan_modules):
        warn("orphan-module", f"module {mid} exists but is not referenced by any district")

    orphan_templates = templates_on_disk - referenced_templates
    for trl in sorted(orphan_templates):
        warn("orphan-template", f"template {trl} exists but is not referenced by any module")

    for expected in EXPECTED_DISTRICT_IDS:
        if expected not in districts:
            err("missing-district", f"HiveCityLayout expects district {expected} but it has no JSON")


# ------------------------------------------------------------------ 3. NBT size + palette collection

def check_nbt_and_collect_palette(modules):
    """Returns global set of unique firstcrusade block base-names actually used in any template."""
    used_blocks = set()
    for mid, mj in modules.items():
        trl = mj.get("template")
        if trl is None:
            continue
        path = template_nbt_path(trl)
        if not os.path.isfile(path):
            continue  # already reported by check_references
        try:
            size, palette, blocks = read_structure_nbt(path)
        except Exception as e:
            err("invalid-nbt", f"module {mid} -> {trl}: failed to parse ({e})")
            continue
        declared = mj.get("size")
        if declared is not None and list(declared) != list(size):
            err("size-mismatch",
                f"module {mid}: declared size {declared} != actual NBT size {size}")
        for key in palette:
            base = key.split("|")[0]
            ns, path_part = rl_split(base)
            if ns != "firstcrusade":
                continue
            name = path_part.split("/")[-1] if "/" in path_part else path_part
            if name == "air":
                continue
            used_blocks.add(name)
    return used_blocks


# ------------------------------------------------------------------ 4. blockstate/model/texture chain

def resolve_model_refs_from_blockstate(bs_json):
    """Return set of model ResourceLocations referenced anywhere in a blockstate json."""
    models = set()

    def take_apply(apply):
        entries = apply if isinstance(apply, list) else [apply]
        for e in entries:
            if isinstance(e, dict) and "model" in e:
                models.add(e["model"])

    if "variants" in bs_json:
        for _, v in bs_json["variants"].items():
            take_apply(v)
    if "multipart" in bs_json:
        for case in bs_json["multipart"]:
            if "apply" in case:
                take_apply(case["apply"])
    return models


def resolve_textures_from_model(model_json):
    """Return set of texture ResourceLocations directly referenced (skips '#ref' indirections)."""
    out = set()
    textures = model_json.get("textures", {})
    for _, v in textures.items():
        if isinstance(v, str) and not v.startswith("#"):
            out.add(v)
    return out


def check_assets(used_blocks):
    model_cache = {}
    for name in sorted(used_blocks):
        bs_rl = f"firstcrusade:{name}"
        bs_path = blockstate_path(bs_rl)
        if not os.path.isfile(bs_path):
            err("missing-blockstate", f"block firstcrusade:{name} used in a template but no blockstate at {bs_path}")
            continue
        with open(bs_path, encoding="utf-8") as f:
            try:
                bs_json = json.load(f)
            except json.JSONDecodeError as e:
                err("invalid-json", f"blockstate {bs_rl}: {e}")
                continue
        model_rls = resolve_model_refs_from_blockstate(bs_json)
        if not model_rls:
            warn("empty-blockstate", f"blockstate {bs_rl} declares no variants/multipart models")
        for mrl in model_rls:
            ns, _ = rl_split(mrl)
            if ns != "firstcrusade":
                continue  # vanilla-provided model, not ours to validate
            if mrl in model_cache:
                textures = model_cache[mrl]
                if textures is None:
                    err("missing-model", f"block {bs_rl} -> model {mrl} (expected {model_path(mrl)})")
                    continue
            else:
                mpath = model_path(mrl)
                if not os.path.isfile(mpath):
                    err("missing-model", f"block {bs_rl} -> model {mrl} (expected {mpath})")
                    model_cache[mrl] = None
                    continue
                with open(mpath, encoding="utf-8") as f:
                    try:
                        model_json = json.load(f)
                    except json.JSONDecodeError as e:
                        err("invalid-json", f"model {mrl}: {e}")
                        model_cache[mrl] = None
                        continue
                if "parent" in model_json and "textures" not in model_json:
                    # Inherits textures from a parent chain — not deep-resolved (rare in this
                    # project; every custom Blockbench export seen so far is fully self-contained).
                    info.append(("uses-parent", f"model {mrl} inherits textures via parent={model_json['parent']}"))
                    model_cache[mrl] = set()
                    continue
                textures = resolve_textures_from_model(model_json)
                model_cache[mrl] = textures
            for trl in textures:
                tns, _ = rl_split(trl)
                if tns != "firstcrusade":
                    continue
                tpath = texture_path(trl)
                if not os.path.isfile(tpath):
                    err("missing-texture", f"block {bs_rl} -> model {mrl} -> texture {trl} (expected {tpath})")


# ------------------------------------------------------------------ 5. socket seam validation
# Reimplements HiveCommands.districtPlace's touchingFace + HiveModule.socketAt/fits at
# district-internal rotation 0 (see module docstring for why global district rotation doesn't
# affect this check).

ROT_NONE, ROT_CW90, ROT_180, ROT_CCW90 = 0, 1, 2, 3


def rotated_size(size, rot):
    if rot in (ROT_CW90, ROT_CCW90):
        return (size[2], size[1], size[0])
    return tuple(size)


def local_face_for_world_face(world_face, rot):
    """Inverse-rotate a world-space horizontal face back to local module space (matches
    HiveModule.socketAt: localFace = inverse(rotation).rotate(worldFace))."""
    order = ["north", "east", "south", "west"]  # clockwise ring, matches Direction rotation
    if world_face not in order:
        return world_face  # up/down unaffected by Y rotation
    inv = {ROT_NONE: 0, ROT_CW90: -1, ROT_180: 2, ROT_CCW90: 1}[rot]
    idx = (order.index(world_face) + inv) % 4
    return order[idx]


def socket_at(sockets, world_face, rot):
    if world_face in ("up", "down"):
        return sockets.get(world_face, "sealed")
    local = local_face_for_world_face(world_face, rot)
    return sockets.get(local, "sealed")


def fits(a, b):
    return a != "sealed" and a == b


def touching_face(a_min, a_size, b_min, b_size):
    ax0, ay0, az0 = a_min; ax1, ay1, az1 = ax0 + a_size[0], ay0 + a_size[1], az0 + a_size[2]
    bx0, by0, bz0 = b_min; bx1, by1, bz1 = bx0 + b_size[0], by0 + b_size[1], bz0 + b_size[2]
    xo = ax0 < bx1 and bx0 < ax1
    yo = ay0 < by1 and by0 < ay1
    zo = az0 < bz1 and bz0 < az1
    if ax1 == bx0 and yo and zo: return "east"
    if bx1 == ax0 and yo and zo: return "west"
    if az1 == bz0 and xo and yo: return "south"
    if bz1 == az0 and xo and yo: return "north"
    if ay1 == by0 and xo and zo: return "up"
    if by1 == ay0 and xo and zo: return "down"
    return None


def check_seams(districts, modules):
    seam_total = 0
    seam_bad = 0
    for did, dj in districts.items():
        placed = []
        for entry in dj.get("modules", []):
            mrl = entry["module"]
            mj = modules.get(mrl)
            if mj is None:
                continue  # already reported
            rot = entry.get("rotation", 0)
            size = rotated_size(mj["size"], rot)
            placed.append((mrl, tuple(entry["offset"]), size, rot, mj.get("sockets", {})))
        for i in range(len(placed)):
            for j in range(len(placed)):
                if i == j:
                    continue
                aid, amin, asize, arot, asock = placed[i]
                bid, bmin, bsize, brot, bsock = placed[j]
                face = touching_face(amin, asize, bmin, bsize)
                if face is None or face in ("west", "north", "down"):
                    continue  # count each seam once (mirror of the Java loop's skip logic)
                seam_total += 1
                sa = socket_at(asock, face, arot)
                opposite = {"north": "south", "south": "north", "east": "west", "west": "east",
                            "up": "down", "down": "up"}[face]
                sb = socket_at(bsock, opposite, brot)
                if not fits(sa, sb):
                    seam_bad += 1
                    err("socket-mismatch",
                        f"district {did}: {aid} {face} [{sa}] <-> [{sb}] {bid}")
    return seam_total, seam_bad


# ------------------------------------------------------------------ main

def main():
    districts = load_all_districts()
    modules = load_all_modules()
    templates_on_disk = all_template_files()

    check_references(districts, modules, templates_on_disk)
    used_blocks = check_nbt_and_collect_palette(modules)
    check_assets(used_blocks)
    seam_total, seam_bad = check_seams(districts, modules)

    lines = []
    lines.append("# Hive City — static validation report\n")
    lines.append(f"Districts scanned: {len(districts)}  |  Modules scanned: {len(modules)}  |  "
                  f"Templates on disk: {len(templates_on_disk)}  |  Unique firstcrusade blocks in use: {len(used_blocks)}\n")
    lines.append(f"Seams checked: {seam_total}  |  Seam mismatches: {seam_bad}\n")
    lines.append(f"\n## Errors ({len(errors)})\n")
    by_cat = defaultdict(list)
    for cat, msg in errors:
        by_cat[cat].append(msg)
    for cat in sorted(by_cat):
        lines.append(f"\n### {cat} ({len(by_cat[cat])})\n")
        for msg in by_cat[cat]:
            lines.append(f"- {msg}\n")
    lines.append(f"\n## Warnings ({len(warnings)})\n")
    by_cat_w = defaultdict(list)
    for cat, msg in warnings:
        by_cat_w[cat].append(msg)
    for cat in sorted(by_cat_w):
        lines.append(f"\n### {cat} ({len(by_cat_w[cat])})\n")
        for msg in by_cat_w[cat]:
            lines.append(f"- {msg}\n")
    if info:
        lines.append(f"\n## Info ({len(info)})\n")
        for cat, msg in info:
            lines.append(f"- [{cat}] {msg}\n")

    os.makedirs(os.path.dirname(OUT_MD), exist_ok=True)
    with open(OUT_MD, "w", encoding="utf-8") as f:
        f.writelines(lines)

    print(f"Districts: {len(districts)}  Modules: {len(modules)}  Templates: {len(templates_on_disk)}  "
          f"Unique blocks: {len(used_blocks)}")
    print(f"Seams checked: {seam_total}  mismatches: {seam_bad}")
    print(f"ERRORS: {len(errors)}   WARNINGS: {len(warnings)}")
    if errors:
        print("\n-- ERROR SUMMARY --")
        for cat in sorted(by_cat):
            print(f"  {cat}: {len(by_cat[cat])}")
    print(f"\nFull report: {os.path.relpath(OUT_MD, ROOT)}")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
