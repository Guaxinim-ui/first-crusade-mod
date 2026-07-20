#!/usr/bin/env python3
"""
FASE 10 — Validador de assets dos blocos da Hive City (First Crusade).

Percorre toda a cadeia blockstate -> model de bloco -> textura, e model de item -> model
de bloco, para os 48 blocos da Hive City, e reporta qualquer inconsistência que faça um
bloco renderizar errado. Encerra com código != 0 se encontrar erros (ideal para CI/build).

Somente stdlib (json, struct, os, sys). Rodar da raiz do projeto:

    python tools/validate_hive_assets.py
"""
import json
import os
import struct
import sys

NS = "firstcrusade"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", NS)
BLOCKSTATES = os.path.join(ASSETS, "blockstates")
MODELS_BLOCK = os.path.join(ASSETS, "models", "block")
MODELS_ITEM = os.path.join(ASSETS, "models", "item")
TEXTURES = os.path.join(ASSETS, "textures")

BLOCKS = [
    # Conjunto I — estruturas
    "armored_bulkhead_wall", "recessed_steel_wall_panel", "gothic_arch_wall", "tall_ribbed_pillar",
    "buttress_column", "cathedral_cornice", "lower_wall_molding", "spire_cap_block",
    "balcony_edge_trim", "bridge_support_block", "giant_door_segment", "narrow_lancet_recess",
    "triangular_relief_panel", "window_slot_frame", "heavy_structural_frame", "vertical_seam_strip",
    # Conjunto II — sistemas industriais
    "straight_pipe", "elbow_pipe", "t_pipe_junction", "cross_pipe_junction",
    "pipe_support_clamp", "vertical_service_conduit", "cable_bundle_block", "vent_outlet",
    "floor_vent", "lift_rail", "gantry_beam", "suspended_track_anchor",
    "maintenance_hatch", "machine_casing_block", "hazard_grated_floor", "reinforced_platform_edge",
    # Conjunto III — pisos, iluminação e detalhes
    "glowing_shrine_window", "stained_window_variant", "candle_alcove", "wall_sconce",
    "shrine_recess", "bloodstained_floor_tile", "cathedral_floor_tile", "metal_floor_plate",
    "floor_grate", "cathedral_stair_block", "landing_slab", "balustrade_railing",
    "skull_relief_panel", "gargoyle_pedestal", "industrial_crate", "brazier_block",
]

errors = []
warnings = []
tex_users = {}   # textura -> [blocos que a usam]  (detecção de atlas compartilhado)


def err(msg):
    errors.append(msg)


def warn(msg):
    warnings.append(msg)


def png_size(path):
    """Retorna (w, h) de um PNG, ou None se corrompido/ausente."""
    try:
        with open(path, "rb") as f:
            head = f.read(24)
        if head[:8] != b"\x89PNG\r\n\x1a\n" or head[12:16] != b"IHDR":
            return None
        return struct.unpack(">II", head[16:24])
    except OSError:
        return None


def texture_path(ref):
    """Converte 'firstcrusade:block/hive_city/x' -> caminho absoluto do .png."""
    if ":" in ref:
        ns, path = ref.split(":", 1)
    else:
        ns, path = "minecraft", ref
    if ns != NS:
        return None, ns, path
    return os.path.join(TEXTURES, *path.split("/")) + ".png", ns, path


def load_json(path, label):
    if not os.path.isfile(path):
        err(f"[{label}] arquivo ausente: {os.path.relpath(path, ROOT)}")
        return None
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except (json.JSONDecodeError, OSError) as e:
        err(f"[{label}] JSON inválido em {os.path.relpath(path, ROOT)}: {e}")
        return None


def check_texture_ref(block, key, ref):
    if not isinstance(ref, str):
        err(f"[{block}] textura '#{key}' não é string: {ref!r}")
        return
    if ref.startswith("#"):
        return  # variável -> resolvida na checagem de faces
    if ".png" in ref:
        err(f"[{block}] ref de textura contém '.png' (proibido): {ref}")
    if "textures/" in ref:
        err(f"[{block}] ref de textura contém 'textures/' (use 'block/...'): {ref}")
    if ref.startswith("/") or ":\\" in ref or ref[1:3] == ":/":
        err(f"[{block}] caminho absoluto de textura: {ref}")
    abspath, ns, path = texture_path(ref)
    if ns != NS:
        warn(f"[{block}] textura em namespace '{ns}' (esperado {NS}): {ref}")
        return
    if abspath and not os.path.isfile(abspath):
        err(f"[{block}] textura inexistente: {ref} -> {os.path.relpath(abspath, ROOT)}")
        return
    dim = png_size(abspath)
    if dim is None:
        err(f"[{block}] PNG corrompido/ilegível: {os.path.relpath(abspath, ROOT)}")
    else:
        w, h = dim
        if (w & (w - 1)) or (h & (h - 1)):
            warn(f"[{block}] textura {ref} não é potência de dois: {w}x{h}")
        tex_users.setdefault(ref, []).append(block)


def check_uvs(block, model):
    tw, th = 16, 16
    ts = model.get("texture_size")
    if isinstance(ts, list) and len(ts) == 2:
        tw, th = ts
    for i, elem in enumerate(model.get("elements", [])):
        for face, fdata in elem.get("faces", {}).items():
            if "texture" not in fdata:
                err(f"[{block}] elemento {i} face '{face}' sem 'texture'")
            uv = fdata.get("uv")
            if uv is None:
                continue
            if len(uv) != 4:
                err(f"[{block}] elemento {i} face '{face}' uv malformada: {uv}")
                continue
            u0, v0, u1, v1 = uv
            if min(uv) < 0:
                err(f"[{block}] elemento {i} face '{face}' uv negativa: {uv}")
            if max(u0, u1) > tw or max(v0, v1) > th:
                err(f"[{block}] elemento {i} face '{face}' uv fora de texture_size "
                    f"{tw}x{th}: {uv}")
            if u0 == u1 or v0 == v1:
                warn(f"[{block}] elemento {i} face '{face}' uv degenerada (área 0): {uv}")


def main():
    print("== Validação de assets — Hive City (48 blocos) ==\n")
    for block in BLOCKS:
        # 1) blockstate -> models existem
        bs = load_json(os.path.join(BLOCKSTATES, block + ".json"), "blockstate")
        if bs:
            refs = []
            for variant in bs.get("variants", {}).values():
                vs = variant if isinstance(variant, list) else [variant]
                refs += [v.get("model") for v in vs if isinstance(v, dict)]
            for part in bs.get("multipart", []):
                ap = part.get("apply", {})
                aps = ap if isinstance(ap, list) else [ap]
                refs += [a.get("model") for a in aps if isinstance(a, dict)]
            for ref in refs:
                if not ref:
                    continue
                _, ns, path = texture_path(ref)
                if ns != NS:
                    warn(f"[{block}] blockstate referencia namespace '{ns}': {ref}")
                    continue
                mp = os.path.join(MODELS_BLOCK, os.path.basename(path) + ".json")
                if not os.path.isfile(mp):
                    err(f"[{block}] blockstate aponta para model inexistente: {ref}")

        # 2) model de bloco -> texturas
        model = load_json(os.path.join(MODELS_BLOCK, block + ".json"), "model")
        if model:
            for key, ref in model.get("textures", {}).items():
                check_texture_ref(block, key, ref)
            # faces referenciam variáveis existentes
            declared = set(model.get("textures", {}).keys())
            for i, elem in enumerate(model.get("elements", [])):
                for face, fdata in elem.get("faces", {}).items():
                    t = fdata.get("texture", "")
                    if t.startswith("#") and t[1:] not in declared:
                        err(f"[{block}] elemento {i} face '{face}' usa var inexistente: {t}")
            check_uvs(block, model)

        # 3) model de item -> aponta para o bloco correto
        item = load_json(os.path.join(MODELS_ITEM, block + ".json"), "item")
        if item:
            parent = item.get("parent", "")
            if not parent.endswith("block/" + block):
                err(f"[{block}] item model parent incorreto: {parent!r} "
                    f"(esperado {NS}:block/{block})")

    # 4) atlas compartilhado não-intencional (mesma textura em muitos blocos distintos)
    for ref, users in tex_users.items():
        uniq = sorted(set(users))
        if len(uniq) > 3:
            warn(f"textura compartilhada por {len(uniq)} blocos (verifique se é intencional): "
                 f"{ref} -> {', '.join(uniq)}")

    print(f"Blocos verificados: {len(BLOCKS)}")
    print(f"Texturas distintas referenciadas: {len(tex_users)}\n")
    if warnings:
        print(f"-- {len(warnings)} aviso(s) --")
        for w in warnings:
            print("  ! " + w)
        print()
    if errors:
        print(f"-- {len(errors)} ERRO(s) --")
        for e in errors:
            print("  x " + e)
        print("\nRESULTADO: FALHOU")
        return 1
    print("RESULTADO: OK — todos os assets da Hive City são consistentes.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
