#!/usr/bin/env python3
"""Converte os .bbmodel aprovados do dono nos tres arquivos que o GeckoLib le.

    <fonte>.bbmodel  ->  assets/firstcrusade/geo/<especie>.geo.json
                         assets/firstcrusade/animations/<especie>.animation.json
                         assets/firstcrusade/textures/entity/<especie>.png

Por que um conversor em vez de "Export Bedrock Geometry" no Blockbench: sao 13 modelos
que vao ser reexportados a cada ajuste de arte, e o export manual perde a textura
embutida e renomeia arquivo. Aqui a fonte de verdade continua sendo o .bbmodel do dono.

REGRA DE OURO: este script NAO cria arte. Ele nao mexe em UV, nao repinta textura, nao
simplifica cubo. Toda a geometria, todo o UV e toda a animacao saem como o dono deixou.
Se a silhueta estiver errada no jogo, o erro esta no .bbmodel e se corrige no Blockbench.

A conversao Blockbench -> Bedrock foi lida do `app.asar` da instalacao do Blockbench
(codec `bedrock`, funcoes compileCube/compileGroup/compileAnimation/compileBedrockKeyframe),
nao escrita de memoria. As tres regras que ninguem acerta de cabeca:

1. **Bedrock espelha o eixo X.** `origin.x = -(from.x + size.x)`, pivot de bone e de cubo
   com x negado, rotacao com x e y negados.
2. **As faces `up`/`down` tem o UV invertido**: o canto anda uv+uv_size e o tamanho fica
   negativo. Sem isso o topo e a barriga de todo cubo saem espelhados.
3. **Keyframe de animacao tambem espelha**: `position` nega x; `rotation` nega x e y.
   `scale` nao mexe.

Uso:
    python tools/bbmodel_to_geckolib.py                # todas as especies do manifesto
    python tools/bbmodel_to_geckolib.py duskhorn grox  # so estas
    python tools/bbmodel_to_geckolib.py --list         # so mostra o que seria feito
"""

import base64
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from bbmodel_lib import load_bbmodel, newest_backup

RES = os.path.join("src", "main", "resources", "assets", "firstcrusade")
GEO_DIR = os.path.join(RES, "geo")
ANIM_DIR = os.path.join(RES, "animations")
TEX_DIR = os.path.join(RES, "textures", "entity")

DOWNLOADS = os.path.expanduser("~/Downloads")


# ---------------------------------------------------------------------- manifesto
#
# Nome da especie no mod -> arquivo fonte. A versao escolhida e sempre a ULTIMA que o dono
# exportou; quando ele mandar uma nova, e so trocar a linha aqui.
#
# O Ambull e o unico que nunca foi exportado para o Downloads — ele existe so como autosave
# do Blockbench, que vem comprimido. `bbmodel_lib` le os dois formatos, entao a entrada
# aponta para o autosave mais recente por fragmento de nome.

SOURCES = {
    # ja existiam no mod com modelo gerado por script; passam a usar a arte do dono
    "grox": "grox_v4_shell_horns_teeth.bbmodel",
    "squig": "squig_v6_head_follows_jaw.bbmodel",
    "cyber_mastiff": "cyber_mastiff_v4_no_floating_parts.bbmodel",
    "ambull": ("backup", "ambull"),
    "ash_strider": "ash_strider_v12_bigger_jaw.bbmodel",

    # especies novas
    "catachan_barking_toad": "catachan_barking_toad_v1_detailed_animated.bbmodel",
    "cthellean_cudbear": "cthellean_cudbear_v1_detailed_animated.bbmodel",
    "arthromite_duneskuttler": "arthromite_duneskuttler_v1_detailed_animated.bbmodel",
    "catachan_devil": "catachan_devil_v1_detailed_animated.bbmodel",
    "dustback_helamite": "dustback_helamite_v1_detailed_animated.bbmodel",
    "fenrisian_wolf": "fenrisian_wolf_v2_minecraft_head.bbmodel",
    "greater_malkavan_constrictor":
        "greater_malkavan_constrictor_v1_detailed_animated.bbmodel",
    "knarloc": "knarloc_v1_detailed_animated.bbmodel",
    "duskhorn": "duskhorn_v1_detailed_animated.bbmodel",
}


def source_path(spec):
    if isinstance(spec, tuple) and spec[0] == "backup":
        found = newest_backup(spec[1])
        if not found:
            raise SystemExit("autosave do Blockbench nao encontrado: %s" % spec[1])
        return found
    return os.path.join(DOWNLOADS, spec)


# ------------------------------------------------------------------- geometria


def _num(value):
    """Blockbench grava numero puro; molang vem como string e passa intacto."""
    if isinstance(value, str):
        text = value.strip()
        try:
            return float(text) if ("." in text or "e" in text.lower()) else int(text)
        except ValueError:
            return value
    if isinstance(value, float) and value.is_integer():
        return int(value)
    return value


def _round(value, places=6):
    if isinstance(value, (int, float)):
        return _num(round(float(value), places))
    return value


def _vec(values):
    return [_round(v) for v in values]


def _is_zero(values):
    return all(isinstance(v, (int, float)) and abs(v) < 1e-9 for v in values)


FACE_ORDER = ("north", "east", "south", "west", "up", "down")


def compile_cube(element):
    """compileCube do codec bedrock, incluindo o espelho de X e o flip de up/down."""
    frm = element["from"]
    to = element["to"]
    size = [to[i] - frm[i] for i in range(3)]

    cube = {
        "origin": _vec([-(frm[0] + size[0]), frm[1], frm[2]]),
        "size": _vec(size),
    }

    inflate = element.get("inflate")
    if inflate:
        cube["inflate"] = _round(inflate)

    rotation = element.get("rotation") or [0, 0, 0]
    if not _is_zero(rotation):
        origin = element.get("origin") or [0, 0, 0]
        cube["pivot"] = _vec([-origin[0], origin[1], origin[2]])
        cube["rotation"] = _vec([-rotation[0], -rotation[1], rotation[2]])

    uv = {}
    for key in FACE_ORDER:
        face = (element.get("faces") or {}).get(key)
        if not face or face.get("texture") is None:
            continue

        box = face["uv"]
        corner = [_round(box[0]), _round(box[1])]
        extent = [_round(box[2] - box[0]), _round(box[3] - box[1])]

        # As duas faces horizontais saem espelhadas se este flip nao acontecer.
        if key in ("up", "down"):
            corner = [_round(corner[0] + extent[0]), _round(corner[1] + extent[1])]
            extent = [_round(-extent[0]), _round(-extent[1])]

        entry = {"uv": corner, "uv_size": extent}
        if face.get("rotation"):
            entry["uv_rotation"] = _round(face["rotation"])
        uv[key] = entry

    cube["uv"] = uv
    return cube


def compile_bones(model):
    """Percorre o outliner e devolve a lista de bones na ordem em que aparece.

    A arvore esta no `outliner` (so uuid + children) e os dados de cada grupo estao numa
    lista separada, `groups`. Ler o nome do no do outliner devolve KeyError — foi assim que
    esta funcao falhou na primeira tentativa.
    """
    groups = {group["uuid"]: group for group in model.get("groups", [])}
    elements = {element["uuid"]: element for element in model.get("elements", [])}
    bones = []

    def walk(node, parent_name):
        if isinstance(node, str):
            return

        group = groups.get(node["uuid"])
        if group is None or not group.get("export", True):
            return

        bone = {"name": group["name"]}
        if parent_name:
            bone["parent"] = parent_name

        origin = group.get("origin") or [0, 0, 0]
        bone["pivot"] = _vec([-origin[0], origin[1], origin[2]])

        rotation = group.get("rotation") or [0, 0, 0]
        if not _is_zero(rotation):
            bone["rotation"] = _vec([-rotation[0], -rotation[1], rotation[2]])

        cubes = []
        for child in node.get("children", []):
            if isinstance(child, str):
                element = elements.get(child)
                if element and element.get("type", "cube") == "cube" \
                        and element.get("export", True):
                    cubes.append(compile_cube(element))

        if cubes:
            bone["cubes"] = cubes

        bones.append(bone)

        for child in node.get("children", []):
            walk(child, group["name"])

    for root in model.get("outliner", []):
        walk(root, None)

    # Cubos soltos na raiz do outliner nao pertencem a bone nenhum; o Bedrock nao tem
    # onde poe-los, e perde-los em silencio seria arte desaparecendo. Vao para um bone
    # sintetico com o nome da raiz do modelo.
    loose = [elements[uuid] for uuid in
             [n for n in model.get("outliner", []) if isinstance(n, str)]
             if uuid in elements]
    if loose:
        bones.append({
            "name": "loose",
            "pivot": [0, 0, 0],
            "cubes": [compile_cube(e) for e in loose if e.get("export", True)],
        })

    return bones


def visible_bounds(model):
    """Caixa que o Bedrock usa para culling. Medida da geometria, com folga."""
    lo = [1e9, 1e9, 1e9]
    hi = [-1e9, -1e9, -1e9]

    for element in model.get("elements", []):
        if element.get("type", "cube") != "cube":
            continue
        for axis in range(3):
            lo[axis] = min(lo[axis], element["from"][axis], element["to"][axis])
            hi[axis] = max(hi[axis], element["from"][axis], element["to"][axis])

    if lo[0] > hi[0]:
        return 2, 2, [0, 1, 0]

    width = max(hi[0] - lo[0], hi[2] - lo[2]) / 16.0
    height = (hi[1] - lo[1]) / 16.0
    offset_y = (hi[1] + lo[1]) / 2.0 / 16.0

    return (round(width + 0.5, 4), round(height + 0.5, 4),
            [0, round(offset_y, 4), 0])


def build_geometry(name, model):
    resolution = model.get("resolution") or {}
    width, height, offset = visible_bounds(model)

    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry." + name,
                "texture_width": resolution.get("width", 16),
                "texture_height": resolution.get("height", 16),
                "visible_bounds_width": width,
                "visible_bounds_height": height,
                "visible_bounds_offset": offset,
            },
            "bones": compile_bones(model),
        }],
    }


# ------------------------------------------------------------------- animacao


CHANNELS = ("rotation", "position", "scale")


def timecode(time):
    text = ("%.4f" % float(time)).rstrip("0").rstrip(".")
    if not text or text == "-0":
        text = "0"
    if "." not in text:
        text += ".0"
    return text


def data_point(point, channel):
    """Um data point -> [x, y, z], com o espelho de eixo que o Bedrock exige."""
    values = [_num(point.get(axis, 0) if point.get(axis, "") != "" else 0)
              for axis in ("x", "y", "z")]

    if channel in ("position", "rotation"):
        values[0] = _invert(values[0])
    if channel == "rotation":
        values[1] = _invert(values[1])

    return [_round(v) for v in values]


def _invert(value):
    """Nega numero; expressao molang ganha o sinal por fora, como o Blockbench faz."""
    if isinstance(value, str):
        return "-(%s)" % value
    return -value


def compile_channel(keyframes, channel):
    ordered = sorted(keyframes, key=lambda kf: kf["time"])
    out = {}

    for position, frame in enumerate(ordered):
        points = frame.get("data_points") or [{}]
        previous = ordered[position - 1] if position > 0 else None
        interpolation = frame.get("interpolation", "linear")

        if interpolation == "catmullrom":
            include_pre = (previous is None and frame["time"] > 0) \
                or (previous is not None
                    and previous.get("interpolation") != "catmullrom")
            entry = {}
            if include_pre:
                entry["pre"] = data_point(points[0], channel)
                entry["post"] = data_point(points[min(1, len(points) - 1)], channel)
            else:
                entry["post"] = data_point(points[0], channel)
            entry["lerp_mode"] = "catmullrom"
            out[timecode(frame["time"])] = entry
        elif len(points) == 1:
            if previous is not None and previous.get("interpolation") == "step":
                previous_points = previous.get("data_points") or [{}]
                out[timecode(frame["time"])] = {
                    "pre": data_point(
                        previous_points[min(1, len(previous_points) - 1)], channel),
                    "post": data_point(points[0], channel),
                }
            else:
                out[timecode(frame["time"])] = data_point(points[0], channel)
        else:
            out[timecode(frame["time"])] = {
                "pre": data_point(points[0], channel),
                "post": data_point(points[1], channel),
            }

    # Um unico keyframe sem curva e um valor constante; o Bedrock aceita o valor solto e
    # isso e o que o Blockbench grava.
    if len(out) == 1:
        only = ordered[0]
        if len(only.get("data_points") or []) == 1 \
                and only.get("interpolation") != "catmullrom":
            return list(out.values())[0]

    return out


def build_animations(model):
    animations = {}

    for animation in model.get("animations", []):
        tag = {}

        loop = animation.get("loop")
        if loop == "hold":
            tag["loop"] = "hold_on_last_frame"
        elif loop == "loop":
            tag["loop"] = True

        length = animation.get("length")
        if length:
            tag["animation_length"] = _round(length, 4)

        bones = {}
        for animator in (animation.get("animators") or {}).values():
            if animator.get("type") != "bone":
                continue

            keyframes = animator.get("keyframes") or []
            if not keyframes:
                continue

            channels = {}
            for channel in CHANNELS:
                selected = [kf for kf in keyframes if kf.get("channel") == channel]
                if selected:
                    channels[channel] = compile_channel(selected, channel)

            if channels:
                bones[animator["name"]] = channels

        tag["bones"] = bones
        animations[animation["name"]] = tag

    return {"format_version": "1.8.0", "animations": animations}


# ------------------------------------------------------------------- textura


def extract_texture(model):
    textures = model.get("textures") or []
    if not textures:
        return None

    source = textures[0].get("source") or ""
    if "," not in source:
        return None

    return base64.b64decode(source.split(",", 1)[1])


# ------------------------------------------------------------------- escrita


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(data, handle, indent=2, ensure_ascii=False)
        handle.write("\n")


def convert(name, spec, dry_run=False):
    path = source_path(spec)
    model = load_bbmodel(path)

    geometry = build_geometry(name, model)
    animations = build_animations(model)
    texture = extract_texture(model)

    bones = geometry["minecraft:geometry"][0]["bones"]
    cubes = sum(len(bone.get("cubes", [])) for bone in bones)
    anim_names = sorted(animations["animations"])

    print("%-30s %3d bones %4d cubos  anims: %s"
          % (name, len(bones), cubes, ", ".join(anim_names)))

    if dry_run:
        return

    write_json(os.path.join(GEO_DIR, name + ".geo.json"), geometry)
    write_json(os.path.join(ANIM_DIR, name + ".animation.json"), animations)

    if texture:
        os.makedirs(TEX_DIR, exist_ok=True)
        with open(os.path.join(TEX_DIR, name + ".png"), "wb") as handle:
            handle.write(texture)
    else:
        print("   ! sem textura embutida — o png antigo ficou como estava")


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    dry_run = "--list" in sys.argv[1:]

    wanted = args or sorted(SOURCES)
    unknown = [name for name in wanted if name not in SOURCES]
    if unknown:
        raise SystemExit("especie fora do manifesto: %s" % ", ".join(unknown))

    for name in wanted:
        convert(name, SOURCES[name], dry_run)

    print("\n%d modelo(s) %s" % (len(wanted), "listado(s)" if dry_run else "convertido(s)"))


if __name__ == "__main__":
    main()
