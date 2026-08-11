#!/usr/bin/env python3
"""
Os nove icones da arvore de Comando Imperial.

O que este script possui
------------------------
Somente `assets/firstcrusade/textures/gui/progression/commander/*.png`. Nao encosta na pasta
`icons/` da arvore Astartes — aquele caminho tem outro dono
(tools/generate_progression_icons.py). Um caminho, um script: e a regra que este projeto aprendeu
depois de dois geradores no mesmo diretorio apagarem o trabalho um do outro em silencio.

Por que desenhar em vez de pintar a mao
---------------------------------------
Sao nove imagens que precisam parecer da mesma familia — e da mesma familia que as 19 da outra
arvore, porque as duas aparecem na mesma tela. A paleta e o contorno sao os mesmos daquele script,
copiados de proposito como constantes e nao importados: os dois arquivos sao independentes, e um
import criaria uma dependencia entre dois geradores que devem poder ser rodados sozinhos.

O contorno e derivado, nao desenhado
------------------------------------
Cada icone e desenhado em cores chapadas e depois passa por `outline()`, que pinta de escuro todo
pixel transparente encostado num pixel opaco. Derivar da forma da um contorno exato em qualquer
silhueta, inclusive nas diagonais, e continua sendo 1 pixel.

Nada de antialiasing e nada de redimensionar depois de pronto: um icone borrado em 40x40 vira uma
mancha no jogo.

Uso:
    python tools/generate_commander_icons.py
"""

import os

from PIL import Image, ImageDraw

SIZE = 40
OUT = os.path.join("src", "main", "resources", "assets", "firstcrusade",
                   "textures", "gui", "progression", "commander")

# ============================================================================ paleta
#
# Mesma direcao de luz dos icones Astartes: luz em cima e a esquerda, sombra embaixo e a direita.

OUTLINE = (14, 10, 12, 255)

RED = ((196, 48, 58), (226, 92, 100), (132, 28, 38))
DEEP_RED = ((150, 30, 40), (188, 58, 66), (96, 18, 26))
STEEL = ((124, 140, 158), (172, 186, 198), (74, 86, 100))
BRONZE = ((176, 118, 46), (214, 164, 88), (118, 74, 24))
GOLD = ((217, 182, 92), (240, 216, 140), (150, 118, 46))
BONE = ((221, 214, 190), (243, 238, 220), (156, 148, 126))
DARK = ((38, 36, 42), (66, 64, 72), (18, 16, 20))
KHAKI = ((118, 112, 78), (152, 146, 106), (78, 74, 50))
# Um khaki mais claro para os capacetes: em 40x40 o tom de campanha some contra o fundo escuro
# da tela, e o icone vira uma mancha. A silhueta so le se o capacete for mais claro que o fundo.
HELM = ((160, 152, 112), (198, 190, 146), (104, 98, 68))
GREEN = ((78, 122, 70), (114, 162, 100), (46, 78, 44))
SPARK = ((246, 230, 140), (255, 248, 200), (196, 172, 80))


def rgba(colour, alpha=255):
    return (colour[0], colour[1], colour[2], alpha)


def canvas():
    return Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))


def outline(img):
    """Pinta de escuro todo pixel vazio encostado numa forma. Derivado, nunca desenhado a mao."""
    pixels = img.load()
    edge = []

    for y in range(SIZE):
        for x in range(SIZE):
            if pixels[x, y][3] != 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < SIZE and 0 <= ny < SIZE and pixels[nx, ny][3] > 128:
                    edge.append((x, y))
                    break

    for x, y in edge:
        pixels[x, y] = OUTLINE


def save(img, name):
    outline(img)
    os.makedirs(OUT, exist_ok=True)
    img.save(os.path.join(OUT, name + ".png"))
    return name


# ============================================================================ primitivas


def rect(draw, box, colour, alpha=255):
    draw.rectangle(box, fill=rgba(colour, alpha))


def poly(draw, points, colour, alpha=255):
    draw.polygon(points, fill=rgba(colour, alpha))


def ellipse(draw, box, colour, alpha=255):
    draw.ellipse(box, fill=rgba(colour, alpha))


def helmet(draw, cx, cy, w, h, palette):
    """Um capacete de guardsman visto de frente: casco, aba e viseira. Reaproveitado tres vezes."""
    base, light, shadow = palette
    half = w // 2

    ellipse(draw, [cx - half, cy - h // 2, cx + half, cy + h // 2], base)
    rect(draw, [cx - half - 1, cy + h // 6, cx + half + 1, cy + h // 3], shadow)   # aba
    ellipse(draw, [cx - half + 1, cy - h // 2 + 1, cx, cy], light)                 # luz
    rect(draw, [cx - half + 2, cy - h // 8, cx + half - 2, cy + h // 8], DARK[0])  # viseira


def aquila(draw, cx, cy, span, palette):
    """Uma aguia de duas cabecas, reduzida ao que le em 40x40: asas, corpo e duas cabecas."""
    base, light, shadow = palette
    half = span // 2

    poly(draw, [(cx - half, cy - 3), (cx - 3, cy), (cx - 3, cy + 4), (cx - half, cy + 3)], base)
    poly(draw, [(cx + half, cy - 3), (cx + 3, cy), (cx + 3, cy + 4), (cx + half, cy + 3)], shadow)
    rect(draw, [cx - 3, cy - 2, cx + 3, cy + 8], base)
    rect(draw, [cx - 3, cy - 2, cx, cy + 8], light)
    rect(draw, [cx - 4, cy - 6, cx - 1, cy - 2], base)   # cabeca esquerda
    rect(draw, [cx + 1, cy - 6, cx + 4, cy - 2], shadow)  # cabeca direita
    poly(draw, [(cx - 3, cy + 8), (cx + 3, cy + 8), (cx, cy + 13)], shadow)


def chevron(draw, cx, top, width, thickness, colour):
    """Um galao apontando para baixo. Tres deles fazem o sargento."""
    half = width // 2
    for offset in range(thickness):
        y = top + offset
        poly(draw, [(cx - half, y), (cx, y + half), (cx + half, y),
                    (cx + half - 2, y), (cx, y + half - 2), (cx - half + 2, y)], colour)


# ============================================================================ os nove icones


def command_authority_cap():
    """Bone de oficial: copa, pala escura e uma aguia pequena na frente."""
    img = canvas()
    d = ImageDraw.Draw(img)
    base, light, shadow = KHAKI

    ellipse(d, [8, 8, 32, 26], base)             # copa
    rect(d, [8, 17, 32, 24], base)
    poly(d, [(8, 17), (20, 17), (20, 24), (8, 24)], light)
    poly(d, [(20, 17), (32, 17), (32, 24), (20, 24)], shadow)

    rect(d, [6, 24, 34, 28], DARK[0])            # pala
    rect(d, [6, 24, 34, 25], DARK[1])
    rect(d, [8, 20, 32, 23], DEEP_RED[0])        # faixa

    aquila(d, 20, 14, 12, GOLD)
    return save(img, "command_authority_cap")


def command_squad_vox():
    """Radio vox com antena e tres capacetes pequenos: o primeiro chamado."""
    img = canvas()
    d = ImageDraw.Draw(img)
    base, light, shadow = STEEL

    rect(d, [10, 6, 30, 24], base)               # caixa
    poly(d, [(10, 6), (20, 6), (20, 24), (10, 24)], light)
    poly(d, [(20, 6), (30, 6), (30, 24), (20, 24)], shadow)
    rect(d, [13, 9, 27, 15], DARK[0])            # mostrador
    rect(d, [14, 10, 20, 14], GREEN[1])
    rect(d, [13, 18, 17, 21], BRONZE[0])         # botoes
    rect(d, [19, 18, 23, 21], BRONZE[2])

    rect(d, [27, 1, 29, 7], STEEL[2])            # antena
    rect(d, [26, 1, 30, 3], SPARK[0])

    for cx in (10, 20, 30):                      # tres capacetes
        helmet(d, cx, 31, 8, 8, HELM)

    return save(img, "command_squad_vox")


def command_reinforced_squad():
    """Cinco capacetes em duas fileiras: o esquadrao que cresceu."""
    img = canvas()
    d = ImageDraw.Draw(img)

    for cx in (8, 20, 32):
        helmet(d, cx, 14, 10, 11, HELM)
    for cx in (14, 26):
        helmet(d, cx, 28, 10, 11, HELM)

    rect(d, [4, 36, 36, 38], BRONZE[0])          # base, para nao flutuarem
    return save(img, "command_reinforced_squad")


def command_combat_section():
    """Formacao: tres fileiras de soldados esquematicos vistas de cima, com uma seta a frente."""
    img = canvas()
    d = ImageDraw.Draw(img)
    base, light, shadow = KHAKI

    base, light, shadow = HELM

    for row, y in enumerate((14, 22, 30)):
        count = 3 + row
        span = 6 * count
        start = 20 - span // 2
        for i in range(count):
            x = start + i * 6
            rect(d, [x, y, x + 4, y + 5], base)
            rect(d, [x, y, x + 2, y + 5], light)
            rect(d, [x + 3, y + 3, x + 4, y + 5], shadow)

    # Um galao dourado acima da formacao, nao um triangulo cheio: o triangulo lia como telhado.
    chevron(d, 20, 4, 20, 4, rgba(GOLD[0]))
    chevron(d, 20, 5, 20, 2, rgba(GOLD[1]))
    return save(img, "command_combat_section")


def command_assault_platoon():
    """Aquila dourada sobre uma formacao cerrada: o pelotao inteiro."""
    img = canvas()
    d = ImageDraw.Draw(img)
    base, light, shadow = HELM

    aquila(d, 20, 11, 17, GOLD)

    for y in (26, 33):
        for i in range(6):
            x = 3 + i * 6
            rect(d, [x, y, x + 4, y + 5], base)
            rect(d, [x, y, x + 2, y + 5], light)
            rect(d, [x + 3, y + 3, x + 4, y + 5], shadow)

    return save(img, "command_assault_platoon")


def command_field_sergeant():
    """Tres galoes de sargento sobre a manga."""
    img = canvas()
    d = ImageDraw.Draw(img)

    rect(d, [7, 4, 33, 36], KHAKI[0])            # manga
    rect(d, [7, 4, 20, 36], KHAKI[1])
    rect(d, [26, 4, 33, 36], KHAKI[2])

    for top in (8, 17, 26):
        chevron(d, 20, top, 22, 5, rgba(BONE[0]))
        chevron(d, 20, top + 1, 22, 3, rgba(BONE[1]))

    return save(img, "command_field_sergeant")


def command_priority_vox():
    """O mesmo radio, agora com um raio: a chamada que fura a fila."""
    img = canvas()
    d = ImageDraw.Draw(img)
    base, light, shadow = STEEL

    rect(d, [8, 10, 26, 32], base)
    poly(d, [(8, 10), (17, 10), (17, 32), (8, 32)], light)
    poly(d, [(17, 10), (26, 10), (26, 32), (17, 32)], shadow)
    rect(d, [11, 13, 23, 20], DARK[0])
    rect(d, [12, 14, 17, 19], GREEN[1])
    rect(d, [11, 24, 15, 28], BRONZE[0])
    rect(d, [17, 24, 21, 28], BRONZE[2])

    rect(d, [23, 4, 25, 11], STEEL[2])           # antena

    poly(d, [(30, 4), (37, 4), (32, 18), (36, 18), (27, 36), (30, 21), (26, 21)], SPARK[0])
    poly(d, [(30, 4), (34, 4), (31, 18), (33, 18), (28, 32), (30, 21), (27, 21)], SPARK[1])
    return save(img, "command_priority_vox")


def command_forward_insertion():
    """Marcador de desembarque: circulo de pouso e uma seta grossa apontando para dentro dele."""
    img = canvas()
    d = ImageDraw.Draw(img)

    ellipse(d, [5, 24, 35, 38], DARK[0])         # zona de pouso, em perspectiva
    ellipse(d, [8, 26, 32, 36], DEEP_RED[0])
    ellipse(d, [12, 28, 28, 34], DARK[2])

    rect(d, [17, 2, 23, 18], GOLD[0])            # haste
    rect(d, [17, 2, 19, 18], GOLD[1])
    poly(d, [(11, 16), (29, 16), (20, 27)], GOLD[0])
    poly(d, [(11, 16), (20, 16), (20, 27)], GOLD[1])
    poly(d, [(20, 16), (29, 16), (20, 27)], GOLD[2])
    return save(img, "command_forward_insertion")


def command_coordinated_assault():
    """Espada cruzando um vox: a ordem e o golpe no mesmo simbolo."""
    img = canvas()
    d = ImageDraw.Draw(img)
    base, light, shadow = STEEL

    rect(d, [4, 16, 20, 34], base)               # vox, atras
    poly(d, [(4, 16), (12, 16), (12, 34), (4, 34)], light)
    poly(d, [(12, 16), (20, 16), (20, 34), (12, 34)], shadow)
    rect(d, [7, 19, 17, 25], DARK[0])
    rect(d, [8, 20, 12, 24], GREEN[1])
    rect(d, [17, 10, 19, 17], STEEL[2])          # antena do vox

    poly(d, [(30, 3), (35, 10), (35, 22), (25, 22), (25, 10)], STEEL[0])   # lamina
    poly(d, [(30, 3), (35, 10), (35, 22), (30, 22)], STEEL[2])
    rect(d, [28, 6, 30, 22], STEEL[1])
    rect(d, [21, 22, 39, 26], BRONZE[0])         # guarda
    rect(d, [21, 22, 39, 23], BRONZE[1])
    rect(d, [28, 26, 32, 35], DEEP_RED[0])       # punho
    rect(d, [28, 26, 29, 35], DEEP_RED[1])
    ellipse(d, [26, 34, 34, 39], BRONZE[0])
    return save(img, "command_coordinated_assault")


# ============================================================================ main


ICONS = [
    command_authority_cap,
    command_squad_vox,
    command_reinforced_squad,
    command_combat_section,
    command_assault_platoon,
    command_field_sergeant,
    command_priority_vox,
    command_forward_insertion,
    command_coordinated_assault,
]


def main():
    written = [maker() for maker in ICONS]

    print("%d icones escritos em %s" % (len(written), OUT))
    for name in written:
        path = os.path.join(OUT, name + ".png")
        with Image.open(path) as img:
            filled = sum(1 for pixel in img.getdata() if pixel[3] > 0)
        print("  %-40s %dx%d  %d px opacos" % (name + ".png", img.width, img.height, filled))


if __name__ == "__main__":
    main()
