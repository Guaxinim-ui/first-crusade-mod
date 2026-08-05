#!/usr/bin/env python3
"""
Previa de um modelo GeckoLib do mod: renderiza o geo.json com a textura dele num PNG.

Para que serve: um geo.json escrito a mao (ou gerado por script) so revela que esta errado
quando o mob aparece no jogo, e "abrir o jogo" e o passo mais caro do ciclo. Isto responde em
um segundo as tres perguntas que costumam falhar — a silhueta fecha? as patas encostam no chao?
a UV caiu na parte certa da textura? — sem subir cliente nenhum.

O que este script possui: nada dentro de src/. Ele so le, e escreve o PNG onde mandarem.

Sao tres vistas na mesma folha — lateral, frontal e 3/4 — porque uma so mente. Numa projecao
3/4 sozinha e impossivel dizer se o bloco no canto e a cabeca ou a garupa, e foi exatamente essa
duvida que motivou as outras duas: a lateral responde a silhueta, a frontal responde a largura.

Limites, declarados de proposito para nao serem confundidos com o jogo:
  - pose de repouso; nao aplica animacao nem rotacao de osso (o formato bedrock ja da origens
    absolutas, entao a pose parada nao precisa da hierarquia);
  - projecao ortografica, sem perspectiva, sem sombra propria;
  - a face inteira recebe a cor media da regiao UV dela, entao serve para conferir que a UV
    caiu no lugar, nao para julgar o desenho da textura.

Uso:
    python tools/preview_geo_model.py grox [saida.png]
"""

import json
import os
import sys

from PIL import Image, ImageDraw

A = os.path.join("src", "main", "resources", "assets", "firstcrusade")

# Cada camera e (contribuicao de x, contribuicao de z, inclinacao vertical) na tela. y sempre
# sobe. "iso" e a vista 3/4 classica; as outras duas sao projecoes puras de eixo.
#
# O bicho olha para -z, entao na lateral o focinho fica a esquerda e a garupa a direita — a
# mesma orientacao em que se desenha um animal em qualquer folha de referencia.
CAMERAS = {
    "lateral": ((0.0, -1.0), (0.0, 0.0)),
    "frontal": ((-1.0, 0.0), (0.0, 0.0)),
    "iso":     ((0.82, -0.52), (-0.20, -0.34)),
}

SCALE = 7.0
MARGIN = 16

# As seis faces de um cubo, cada uma com os quatro cantos em fracoes do tamanho e a normal.
FACES = {
    "top":    (((0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)), (0, 1, 0)),
    "bottom": (((0, 0, 1), (1, 0, 1), (1, 0, 0), (0, 0, 0)), (0, -1, 0)),
    "front":  (((0, 0, 0), (1, 0, 0), (1, 1, 0), (0, 1, 0)), (0, 0, -1)),
    "back":   (((1, 0, 1), (0, 0, 1), (0, 1, 1), (1, 1, 1)), (0, 0, 1)),
    "right":  (((0, 0, 1), (0, 0, 0), (0, 1, 0), (0, 1, 1)), (-1, 0, 0)),
    "left":   (((1, 0, 0), (1, 0, 1), (1, 1, 1), (1, 1, 0)), (1, 0, 0)),
}

# Quanta luz cada direcao recebe. Os mesmos numeros do sombreamento de bloco do jogo, para a
# previa nao mentir sobre contraste.
SHADE = {(0, 1, 0): 1.0, (0, -1, 0): 0.5, (0, 0, -1): 0.8,
         (0, 0, 1): 0.8, (-1, 0, 0): 0.62, (1, 0, 0): 0.62}


def project(camera, x, y, z):
    (sx_x, sx_z), (sy_x, sy_z) = camera
    return (x * sx_x + z * sx_z, -y + x * sy_x + z * sy_z)


def uv_rect(u, v, w, h, d, face):
    """O retangulo da face no desdobramento de caixa padrao — a mesma conta do gerador."""
    return {
        "top":    (u + d, v, u + d + w, v + d),
        "bottom": (u + d + w, v, u + d + 2 * w, v + d),
        "right":  (u, v + d, u + d, v + d + h),
        "front":  (u + d, v + d, u + d + w, v + d + h),
        "left":   (u + d + w, v + d, u + 2 * d + w, v + d + h),
        "back":   (u + 2 * d + w, v + d, u + 2 * (d + w), v + d + h),
    }[face]


def average_colour(texture, rect):
    x0, y0, x1, y1 = (int(round(c)) for c in rect)
    x1, y1 = max(x1, x0 + 1), max(y1, y0 + 1)
    region = texture.crop((x0, y0, x1, y1))

    total = [0, 0, 0]
    count = 0
    for pixel in region.getdata():
        if pixel[3] == 0:
            continue
        for i in range(3):
            total[i] += pixel[i]
        count += 1

    if not count:
        return (255, 0, 220)

    return tuple(c // count for c in total)


def view(cubes, texture, camera_name):
    """Uma vista: os poligonos ordenados de tras para a frente, ja com cor e sombreamento."""
    camera = CAMERAS[camera_name]
    (sx_x, sx_z), _ = camera

    polygons = []
    for cube in cubes:
        ox, oy, oz = cube["origin"]
        w, h, d = cube["size"]
        u, v = cube["uv"]

        for face, (corners, normal) in FACES.items():
            points = []
            depth = 0.0
            for fx, fy, fz in corners:
                x, y, z = ox + fx * w, oy + fy * h, oz + fz * d
                points.append(project(camera, x, y, z))
                # Profundidade = o eixo que a camera olha. Para uma vista de eixo puro e o
                # eixo ausente da tela; para a 3/4, a diagonal entre os dois.
                depth += x * -sx_z + z * sx_x + y * 0.35

            colour = average_colour(texture, uv_rect(u, v, w, h, d, face))
            shade = SHADE[normal]
            polygons.append((depth / 4.0, points, tuple(int(c * shade) for c in colour)))

    return sorted(polygons, key=lambda item: item[0])


def draw_view(polygons, label):
    xs = [p[0] for _d, pts, _c in polygons for p in pts]
    ys = [p[1] for _d, pts, _c in polygons for p in pts]

    width = int((max(xs) - min(xs)) * SCALE) + MARGIN * 2
    height = int((max(ys) - min(ys)) * SCALE) + MARGIN * 2
    img = Image.new("RGBA", (width, height), (28, 28, 32, 255))
    draw = ImageDraw.Draw(img)

    # A linha y=0 do modelo: e ela que responde "as patas encostam no chao?". A tela cresce
    # para baixo (a projecao nega y), entao o chao fica na parte de baixo do quadro.
    ground = MARGIN + (0 - min(ys)) * SCALE
    draw.line([(0, ground), (width, ground)], fill=(70, 70, 80, 255))

    for _depth, points, colour in polygons:
        draw.polygon([(MARGIN + (x - min(xs)) * SCALE, MARGIN + (y - min(ys)) * SCALE)
                      for x, y in points], fill=colour + (255,),
                     outline=tuple(int(c * 0.75) for c in colour) + (255,))

    draw.text((6, 4), label, fill=(150, 150, 160, 255))
    return img


def render(name, out_path):
    geo = json.load(open(os.path.join(A, "geo", name + ".geo.json"), encoding="utf-8"))
    texture = Image.open(os.path.join(A, "textures", "entity", name + ".png")).convert("RGBA")

    geometry = geo["minecraft:geometry"][0]
    cubes = [cube for bone in geometry["bones"] for cube in bone.get("cubes", [])]

    panels = [draw_view(view(cubes, texture, cam), cam) for cam in CAMERAS]

    width = sum(p.width for p in panels) + 8 * (len(panels) - 1)
    height = max(p.height for p in panels)
    sheet = Image.new("RGBA", (width, height), (18, 18, 22, 255))

    x = 0
    for panel in panels:
        sheet.alpha_composite(panel, (x, height - panel.height))
        x += panel.width + 8

    sheet.save(out_path)
    return len(cubes), sheet.size


def main():
    if len(sys.argv) < 2:
        raise SystemExit(__doc__.strip())

    name = sys.argv[1]
    out = sys.argv[2] if len(sys.argv) > 2 else name + "_preview.png"
    count, size = render(name, out)
    print("%s: %d cubos -> %s (%dx%d)" % (name, count, out, size[0], size[1]))


if __name__ == "__main__":
    main()
