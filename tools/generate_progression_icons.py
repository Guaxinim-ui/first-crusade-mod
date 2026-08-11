#!/usr/bin/env python3
"""
Os icones da arvore de progressao Imperial: 5 categorias, 12 implantes, raiz e ascensao.

O que este script possui
------------------------
Somente `assets/firstcrusade/textures/gui/progression/icons/*.png`. Nao encosta em textura de
entidade, de item, de bloco nem de planeta — um caminho, um dono (ver
tools/generate_biomes.py sobre o que custou aprender essa regra).

Por que desenhar em vez de pintar a mao
---------------------------------------
Sao 19 imagens que precisam parecer da mesma familia: mesma paleta, mesmo contorno, mesma leitura
em 40x40. Feitas a mao, a decima nona nao se parece com a primeira. Aqui a paleta e o contorno sao
funcoes, entao a familia sai por construcao — e mudar o vermelho do sangue e mudar uma constante,
nao repintar cinco arquivos.

O contorno e derivado, nao desenhado
------------------------------------
Cada icone e desenhado em cores chapadas e depois passa por `outline()`, que pinta de escuro todo
pixel transparente encostado num pixel opaco. Desenhar o contorno a mao em cada forma daria 19
contornos ligeiramente diferentes; derivar da forma da um contorno exato em todas, inclusive nas
diagonais, e continua sendo 1 pixel em qualquer silhueta.

Nada de antialiasing: o ImageDraw do Pillow nao suavisa poligono nem elipse, e nenhuma imagem e
redimensionada depois de pronta. Um icone borrado em 40x40 vira uma mancha no jogo.

Uso:
    python tools/generate_progression_icons.py
"""

import os

from PIL import Image, ImageDraw

SIZE = 40
OUT = os.path.join("src", "main", "resources", "assets", "firstcrusade",
                   "textures", "gui", "progression", "icons")

# ============================================================================ paleta
#
# Tres tons por material: base, luz e sombra. A luz vai em cima e a esquerda, a sombra embaixo e a
# direita — a mesma direcao de luz em todos os 19, que e metade do motivo de parecerem um conjunto.

OUTLINE = (14, 10, 12, 255)

RED = ((196, 48, 58), (226, 92, 100), (132, 28, 38))
DEEP_RED = ((150, 30, 40), (188, 58, 66), (96, 18, 26))
STEEL = ((124, 140, 158), (172, 186, 198), (74, 86, 100))
BRONZE = ((176, 118, 46), (214, 164, 88), (118, 74, 24))
GREEN = ((78, 122, 70), (114, 162, 100), (46, 78, 44))
GOLD = ((217, 182, 92), (240, 216, 140), (150, 118, 46))
BONE = ((221, 214, 190), (243, 238, 220), (156, 148, 126))
FLESH = ((196, 108, 116), (224, 148, 152), (140, 66, 76))
PINK = ((208, 124, 132), (236, 166, 170), (150, 76, 86))
BRAIN = ((198, 138, 148), (228, 178, 184), (140, 88, 98))
DARK = ((38, 36, 42), (66, 64, 72), (18, 16, 20))
WHITE = ((226, 222, 212), (248, 246, 240), (166, 162, 154))
ACID = ((132, 190, 78), (176, 220, 118), (86, 134, 46))
VIOLET = ((142, 92, 176), (180, 136, 208), (92, 54, 122))


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


def ellipse(draw, box, colour, alpha=255):
    draw.ellipse(box, fill=rgba(colour, alpha))


def rect(draw, box, colour, alpha=255):
    draw.rectangle(box, fill=rgba(colour, alpha))


def poly(draw, points, colour, alpha=255):
    draw.polygon(points, fill=rgba(colour, alpha))


def shade_half(img, left_colour, right_colour, split=20):
    """
    Tinge as duas metades de uma forma **sem sair dela**.

    Desenhar a meia-luz como poligono solto foi o erro da primeira versao do cerebro: o poligono era
    maior que o orgao e reconstruiu um retangulo por cima da silhueta bolhuda que tinha acabado de
    ser desenhada. Aqui a tinta so cai onde ja existe pixel opaco, entao a forma manda na luz e
    nunca o contrario.
    """
    pixels = img.load()
    for y in range(SIZE):
        for x in range(SIZE):
            if pixels[x, y][3] < 128:
                continue
            colour = left_colour if x < split else right_colour
            pixels[x, y] = rgba(colour)


def heart(draw, cx, cy, w, h, palette):
    """Um coracao: dois lobos e um bico. Reaproveitado pela categoria e pelo primeiro orgao."""
    base, light, shadow = palette
    half = w // 2

    ellipse(draw, [cx - half, cy - h // 2, cx, cy + h // 8], base)
    ellipse(draw, [cx, cy - h // 2, cx + half, cy + h // 8], base)
    poly(draw, [(cx - half, cy - h // 12), (cx + half, cy - h // 12), (cx, cy + h // 2)], base)

    # Luz num lobo, sombra no bico: o volume inteiro sai de dois toques.
    ellipse(draw, [cx - half + 2, cy - h // 2 + 2, cx - 2, cy - h // 8], light)
    poly(draw, [(cx - half // 2, cy + h // 6), (cx + half, cy - h // 12), (cx, cy + h // 2)], shadow)


# ============================================================================ categorias


def branch_vitality_heart():
    img = canvas()
    d = ImageDraw.Draw(img)
    heart(d, 20, 20, 28, 28, RED)
    return save(img, "branch_vitality_heart")


def branch_resilience_armour():
    """Peitoral, nao escudo: ombreiras largas, tronco em trapezio e uma costura central."""
    img = canvas()
    d = ImageDraw.Draw(img)
    base, light, shadow = STEEL

    poly(d, [(9, 12), (31, 12), (28, 33), (20, 36), (12, 33)], base)
    rect(d, [4, 9, 13, 19], base)          # ombreira esquerda
    rect(d, [27, 9, 36, 19], base)         # ombreira direita
    poly(d, [(9, 12), (20, 12), (20, 34), (12, 33)], light)
    poly(d, [(20, 12), (31, 12), (28, 33), (20, 34)], shadow)
    rect(d, [19, 13, 21, 33], shadow)      # costura
    rect(d, [4, 9, 13, 12], light)
    rect(d, [27, 16, 36, 19], shadow)
    return save(img, "branch_resilience_armour")


def branch_damage_sword():
    """Lamina para cima, guarda em bronze, punho vermelho. Nao e uma cruz."""
    img = canvas()
    d = ImageDraw.Draw(img)
    steel, steel_light, steel_dark = STEEL

    poly(d, [(20, 3), (25, 11), (25, 24), (15, 24), (15, 11)], steel)
    poly(d, [(20, 3), (25, 11), (25, 24), (20, 24)], steel_dark)
    rect(d, [18, 6, 20, 24], steel_light)
    rect(d, [10, 24, 30, 28], BRONZE[0])
    rect(d, [10, 24, 30, 25], BRONZE[1])
    rect(d, [10, 27, 30, 28], BRONZE[2])
    rect(d, [18, 28, 22, 35], DEEP_RED[0])
    rect(d, [18, 28, 19, 35], DEEP_RED[1])
    ellipse(d, [16, 34, 24, 39], BRONZE[0])
    return save(img, "branch_damage_sword")


def branch_mobility_boot():
    """Bota de cano alto com bico para a direita e tres linhas de velocidade atras."""
    img = canvas()
    d = ImageDraw.Draw(img)
    base, light, shadow = GREEN

    poly(d, [(13, 5), (26, 5), (25, 14), (24, 24), (13, 24)], base)     # cano, com barriga
    poly(d, [(13, 24), (24, 24), (31, 28), (34, 31), (13, 31)], base)   # peito do pe, inclinado
    ellipse(d, [28, 25, 36, 32], base)                                  # ponta redonda

    poly(d, [(13, 5), (19, 5), (19, 31), (13, 31)], light)              # luz na frente do cano
    poly(d, [(21, 8), (26, 5), (25, 14), (24, 24), (21, 24)], shadow)
    poly(d, [(24, 26), (31, 28), (34, 31), (24, 31)], shadow)

    rect(d, [11, 31, 35, 35], DARK[0])                                  # sola
    rect(d, [11, 31, 35, 32], DARK[1])
    rect(d, [12, 35, 18, 37], DARK[0])                                  # salto

    for y in (10, 16, 21):                                              # cadarcos
        rect(d, [14, y, 23, y + 1], shadow)

    for i, y in enumerate((11, 19, 27)):                                # linhas de velocidade
        rect(d, [2 + i * 2, y, 10, y + 2], STEEL[1])

    return save(img, "branch_mobility_boot")


def branch_faith_aquila():
    """Aquila de duas cabecas: corpo central, asas abertas, duas cabecas voltadas para fora."""
    img = canvas()
    d = ImageDraw.Draw(img)
    base, light, shadow = GOLD

    poly(d, [(20, 10), (24, 20), (20, 34), (16, 20)], base)          # corpo
    poly(d, [(16, 13), (2, 18), (6, 22), (16, 22)], base)            # asa esquerda
    poly(d, [(24, 13), (38, 18), (34, 22), (24, 22)], base)          # asa direita
    poly(d, [(16, 13), (2, 18), (6, 20), (16, 18)], light)
    poly(d, [(24, 16), (38, 18), (34, 22), (24, 22)], shadow)
    ellipse(d, [11, 6, 18, 13], base)                                # cabeca esquerda
    ellipse(d, [22, 6, 29, 13], base)                                # cabeca direita
    rect(d, [8, 8, 12, 10], shadow)                                  # bico esquerdo
    rect(d, [28, 8, 32, 10], shadow)                                 # bico direito
    rect(d, [18, 22, 22, 24], shadow)
    poly(d, [(16, 26), (24, 26), (20, 34)], shadow)                  # cauda
    return save(img, "branch_faith_aquila")


# ============================================================================ raiz e ascensao


def root_guardsman_helmet():
    """Capacete da Guarda: casco simples, aba reta, sem grelha. Tem de contrastar com o Astartes."""
    img = canvas()
    d = ImageDraw.Draw(img)
    base, light, shadow = GREEN

    ellipse(d, [8, 8, 32, 30], base)
    rect(d, [8, 19, 32, 27], base)
    ellipse(d, [11, 11, 24, 22], light)
    poly(d, [(20, 9), (31, 16), (31, 27), (20, 27)], shadow)
    rect(d, [5, 26, 35, 30], STEEL[2])     # aba
    rect(d, [5, 26, 35, 27], STEEL[0])
    rect(d, [18, 13, 22, 20], GOLD[0])     # marca imperial
    rect(d, [16, 15, 24, 17], GOLD[0])
    return save(img, "root_guardsman_helmet")


def ascension_space_marine_helmet():
    """O capacete: domo alto, grelha central, lentes vermelhas e uma faixa dourada na testa."""
    img = canvas()
    d = ImageDraw.Draw(img)
    base, light, shadow = WHITE

    poly(d, [(9, 14), (13, 5), (27, 5), (31, 14), (31, 30), (26, 35), (14, 35), (9, 30)], base)
    poly(d, [(9, 14), (13, 5), (20, 5), (20, 35), (14, 35), (9, 30)], light)
    poly(d, [(20, 5), (27, 5), (31, 14), (31, 30), (26, 35), (20, 35)], shadow)

    rect(d, [16, 14, 24, 35], DEEP_RED[2])      # grelha / focinho
    rect(d, [17, 16, 23, 33], DARK[0])
    for y in range(18, 32, 3):
        rect(d, [17, y, 23, y + 1], DARK[2])

    poly(d, [(10, 16), (16, 15), (16, 22), (10, 21)], RED[0])       # lente esquerda
    poly(d, [(24, 15), (30, 16), (30, 21), (24, 22)], RED[0])       # lente direita
    rect(d, [11, 16, 14, 18], RED[1])
    rect(d, [26, 16, 29, 18], RED[1])

    rect(d, [11, 8, 29, 11], GOLD[0])                                # faixa da testa
    rect(d, [11, 8, 29, 9], GOLD[1])
    return save(img, "ascension_space_marine_helmet")


# ============================================================================ os doze orgaos


def implant_secondary_heart():
    """Dois coracoes: o principal e um menor encaixado, com dois vasos saindo."""
    img = canvas()
    d = ImageDraw.Draw(img)

    rect(d, [18, 3, 21, 12], DEEP_RED[2])        # vasos
    rect(d, [12, 6, 15, 14], DEEP_RED[2])
    heart(d, 17, 20, 26, 26, DEEP_RED)
    heart(d, 29, 28, 15, 15, RED)
    return save(img, "implant_secondary_heart")


def implant_ossmodula_bone():
    """Femur grosso com placas minerais crescendo por cima."""
    img = canvas()
    d = ImageDraw.Draw(img)
    base, light, shadow = BONE

    rect(d, [16, 10, 25, 30], base)
    ellipse(d, [10, 4, 21, 14], base)            # epifise superior
    ellipse(d, [19, 4, 30, 14], base)
    ellipse(d, [10, 26, 21, 36], base)           # epifise inferior
    ellipse(d, [19, 26, 30, 36], base)
    rect(d, [16, 10, 19, 30], light)
    rect(d, [23, 12, 25, 30], shadow)
    ellipse(d, [12, 6, 19, 12], light)

    for box in ([13, 16, 19, 20], [22, 22, 28, 26], [14, 24, 18, 27]):
        rect(d, box, STEEL[0])                   # placas minerais
        rect(d, [box[0], box[1], box[2], box[1] + 1], STEEL[1])

    return save(img, "implant_ossmodula_bone")


def implant_biscopea_muscle():
    """Braco flexionado: o biceps e o assunto, entao ele ocupa o centro."""
    img = canvas()
    d = ImageDraw.Draw(img)
    base, light, shadow = DEEP_RED

    # Braco flexionado de verdade: punho em cima, antebraco descendo, cotovelo embaixo a direita e
    # o biceps estufando para a esquerda. A versao anterior era simetrica e por isso lia como um M —
    # um braco so parece um braco quando os dois lados sao diferentes.
    rect(d, [22, 4, 32, 13], FLESH[0])                                   # punho
    rect(d, [22, 4, 26, 13], FLESH[1])
    rect(d, [23, 13, 31, 30], base)                                      # antebraco
    rect(d, [23, 13, 26, 30], light)
    poly(d, [(23, 26), (33, 28), (33, 35), (21, 34)], base)              # cotovelo
    poly(d, [(23, 26), (27, 27), (27, 35), (21, 34)], light)

    ellipse(d, [5, 12, 26, 31], base)                                    # biceps
    ellipse(d, [7, 14, 20, 26], light)
    poly(d, [(16, 14), (26, 18), (26, 30), (14, 30)], shadow)

    for y in (18, 22, 26):                                               # fibras
        rect(d, [9, y, 21, y + 1], shadow)

    rect(d, [28, 16, 31, 30], shadow)
    return save(img, "implant_biscopea_muscle")


def implant_haemastamen_blood():
    """Gota grande com celulas dentro: sangue, e sangue que foi mexido."""
    img = canvas()
    d = ImageDraw.Draw(img)
    base, light, shadow = RED

    poly(d, [(20, 3), (32, 22), (32, 28), (20, 37), (8, 28), (8, 22)], base)
    poly(d, [(20, 3), (14, 14), (10, 24), (14, 33), (20, 37)], light)
    poly(d, [(20, 3), (32, 22), (32, 28), (20, 37)], shadow)

    for box in ([14, 20, 19, 25], [21, 16, 25, 20], [20, 26, 25, 31]):
        ellipse(d, box, DEEP_RED[2])
        ellipse(d, [box[0] + 1, box[1] + 1, box[2] - 2, box[3] - 2], PINK[1])

    return save(img, "implant_haemastamen_blood")


def implant_larraman_clot():
    """Duas bordas de uma ferida se fechando, com a crosta se formando no meio."""
    img = canvas()
    d = ImageDraw.Draw(img)

    # Pele com um corte no meio, nao duas metades separadas: a versao anterior desenhou os dois
    # lados como triangulos opostos e o resultado foi uma gravata-borboleta. Agora e um oval de
    # pele inteiro, cortado por uma fenda irregular que a crosta esta fechando.
    ellipse(d, [3, 8, 37, 32], FLESH[0])
    ellipse(d, [6, 10, 26, 24], FLESH[1])
    poly(d, [(20, 9), (34, 14), (36, 26), (20, 31)], FLESH[2])

    # A fenda: irregular, porque corte reto lê como costura de roupa.
    poly(d, [(18, 9), (22, 12), (17, 17), (23, 22), (18, 27), (22, 31),
             (18, 31), (14, 27), (19, 22), (13, 17), (18, 12), (14, 9)], DEEP_RED[2])

    for y in (12, 17, 22, 27):                                      # pontos da crosta
        rect(d, [11, y, 29, y + 2], DARK[0])
        rect(d, [12, y, 28, y + 1], RED[0])

    poly(d, [(20, 31), (25, 36), (20, 39), (15, 36)], RED[0])       # gota
    poly(d, [(20, 31), (17, 35), (20, 39)], RED[1])
    return save(img, "implant_larraman_clot")


def implant_catalepsean_brain():
    """Cerebro com um hemisferio aceso e outro apagado, e um olho aberto no lado que vigia."""
    img = canvas()
    d = ImageDraw.Draw(img)

    # O contorno do cerebro sai de bolhas ao longo do topo, nao de uma elipse lisa: e a
    # irregularidade que faz o orgao ser lido como cerebro e nao como um ovo.
    # Silhueta primeiro: elipse central mais bolhas no topo e dois lobos do cerebelo embaixo.
    ellipse(d, [5, 10, 35, 30], BRAIN[0])
    for cx in (10, 16, 22, 29):
        ellipse(d, [cx - 6, 5, cx + 6, 17], BRAIN[0])
    ellipse(d, [8, 22, 20, 34], BRAIN[0])
    ellipse(d, [20, 22, 32, 34], BRAIN[0])

    # So agora a luz, e so dentro da forma.
    shade_half(img, BRAIN[1], DARK[0])

    # Sulcos curtos e deslocados linha a linha. Barras do mesmo comprimento em linhas paralelas
    # leem como codigo de barras; e o desencontro delas que lê como cerebro.
    for i, y in enumerate((11, 16, 21, 26, 30)):
        inset = 2 if i % 2 else 0
        rect(d, [7 + inset, y, 16 - inset, y + 1], BRAIN[2])
        rect(d, [23 + inset, y, 32 - inset, y + 1], DARK[2])

    rect(d, [19, 5, 21, 34], BRAIN[2])                              # fissura central

    ellipse(d, [22, 14, 36, 26], WHITE[1])                          # olho, no lado que vigia
    ellipse(d, [25, 15, 34, 25], STEEL[0])
    ellipse(d, [27, 17, 32, 23], DARK[2])
    rect(d, [28, 18, 30, 20], WHITE[1])
    return save(img, "implant_catalepsean_brain")


def implant_preomnor_omophagea_stomach():
    """Estomago com uma helice genetica ao lado: digerir e lembrar o que foi digerido."""
    img = canvas()
    d = ImageDraw.Draw(img)
    base, light, shadow = FLESH

    ellipse(d, [6, 12, 28, 34], base)
    poly(d, [(14, 6), (22, 6), (24, 16), (12, 16)], base)           # esofago
    ellipse(d, [9, 15, 21, 27], light)
    poly(d, [(20, 16), (28, 20), (28, 30), (18, 33)], shadow)
    rect(d, [14, 6, 17, 14], light)

    for y in (10, 16, 22, 28):                                      # helice
        offset = 3 if (y // 6) % 2 == 0 else 0
        rect(d, [28 + offset, y, 33 + offset, y + 2], ACID[0])
    rect(d, [30, 10, 32, 30], ACID[2])
    return save(img, "implant_preomnor_omophagea_stomach")


def implant_multi_lung():
    """Dois pulmoes e um terceiro lobo no centro, embaixo da traqueia. O mais reconhecivel dos doze."""
    img = canvas()
    d = ImageDraw.Draw(img)
    base, light, shadow = PINK

    rect(d, [18, 3, 22, 14], STEEL[0])                              # traqueia
    for y in range(5, 14, 3):
        rect(d, [17, y, 23, y + 1], STEEL[2])

    poly(d, [(17, 12), (17, 30), (10, 34), (4, 28), (5, 16)], base)     # pulmao esquerdo
    poly(d, [(23, 12), (23, 30), (30, 34), (36, 28), (35, 16)], base)   # pulmao direito
    poly(d, [(17, 12), (17, 26), (8, 30), (5, 18)], light)
    poly(d, [(23, 16), (23, 30), (30, 34), (36, 28)], shadow)

    poly(d, [(15, 22), (25, 22), (24, 36), (16, 36)], DEEP_RED[0])      # terceiro lobo
    poly(d, [(15, 22), (20, 22), (20, 36), (16, 36)], DEEP_RED[1])
    rect(d, [19, 14, 21, 24], STEEL[2])
    return save(img, "implant_multi_lung")


def implant_occulobe_lyman_eye_ear():
    """Metade olho, metade ouvido, divididos por uma linha central."""
    img = canvas()
    d = ImageDraw.Draw(img)

    # Os dois elementos ocupam metade da tela cada. A versao anterior deixou o olho do tamanho de
    # um losango de cinco pixels, e um icone que precisa de lupa nao e um icone.
    # Amendoa larga e baixa, e depois esclera, iris e pupila em camadas cada vez menores. A versao
    # anterior punha uma elipse branca por cima da iris e o olho virou um losango vazio.
    poly(d, [(0, 20), (5, 12), (11, 10), (17, 14), (18, 20),
             (12, 28), (6, 29), (1, 24)], WHITE[1])
    ellipse(d, [2, 12, 17, 28], ACID[0])                            # iris grande
    ellipse(d, [4, 14, 15, 26], ACID[2])
    ellipse(d, [6, 16, 13, 24], DARK[2])                            # pupila
    rect(d, [7, 17, 9, 19], WHITE[1])                               # brilho

    ellipse(d, [21, 5, 39, 35], FLESH[0])                           # ouvido: helice grande
    ellipse(d, [24, 9, 36, 31], FLESH[2])
    ellipse(d, [26, 13, 34, 29], FLESH[1])
    ellipse(d, [28, 17, 34, 27], DARK[0])                           # canal
    poly(d, [(21, 26), (28, 30), (25, 36), (21, 34)], FLESH[0])     # lobulo

    rect(d, [19, 4, 21, 36], GOLD[2])                               # divisa
    return save(img, "implant_occulobe_lyman_eye_ear")


def implant_susan_melanochrome_oolitic():
    """O rim e o assunto; a lua e a estase, o arco e a protecao. Nada alem disso."""
    img = canvas()
    d = ImageDraw.Draw(img)
    base, light, shadow = DEEP_RED

    # Um rim de verdade: dois lobos cheios e uma mordida concava do lado interno. A versao anterior
    # era um poligono reto que nao lia como orgao nenhum.
    # O rim: dois lobos cheios a esquerda e a concavidade do hilo aberta para a direita. A mordida
    # e feita tirando pixel (fill transparente) ANTES de qualquer detalhe, senao o passe de
    # contorno acha uma borda interna e desenha um buraco preto no meio do orgao.
    ellipse(d, [6, 6, 30, 34], base)
    ellipse(d, [18, 12, 38, 28], (0, 0, 0, 0))
    ellipse(d, [9, 9, 23, 22], light)
    poly(d, [(18, 26), (28, 28), (24, 34), (16, 33)], shadow)
    rect(d, [26, 18, 37, 22], DEEP_RED[2])                          # ureter saindo do hilo

    # Crescente da estase, grande e no canto livre: um poligono, nunca uma subtracao de circulos.
    poly(d, [(33, 2), (27, 6), (25, 13), (28, 20), (33, 23),
             (29, 17), (28, 12), (30, 7)], VIOLET[0])
    poly(d, [(33, 2), (27, 6), (25, 13), (28, 12), (30, 7)], VIOLET[1])

    # Protecao: um chevron sob o orgao, nao uma moldura em volta dele. A moldura lia como cadeira.
    poly(d, [(6, 30), (20, 36), (34, 30), (34, 34), (20, 39), (6, 34)], STEEL[0])
    poly(d, [(6, 30), (20, 36), (20, 39), (6, 34)], STEEL[1])
    return save(img, "implant_susan_melanochrome_oolitic")


def implant_chemical_maturity_glands():
    """Frasco biologico com uma gota acida saindo e a glandula presa em cima."""
    img = canvas()
    d = ImageDraw.Draw(img)

    ellipse(d, [12, 3, 28, 15], FLESH[0])                           # glandula
    ellipse(d, [15, 5, 24, 12], FLESH[1])
    rect(d, [17, 12, 23, 18], FLESH[2])                             # tubo

    poly(d, [(13, 18), (27, 18), (31, 34), (9, 34)], STEEL[0])      # frasco
    poly(d, [(13, 18), (20, 18), (20, 34), (9, 34)], STEEL[1])
    poly(d, [(14, 24), (26, 24), (29, 32), (11, 32)], ACID[0])      # conteudo
    poly(d, [(14, 24), (20, 24), (20, 32), (11, 32)], ACID[1])

    poly(d, [(33, 24), (37, 30), (33, 34), (29, 30)], ACID[0])      # gota
    poly(d, [(33, 24), (30, 29), (33, 34)], ACID[1])
    return save(img, "implant_chemical_maturity_glands")


def implant_black_carapace():
    """Peitoral preto com plugues neurais vermelhos. Nada a ver com o icone de Resistencia."""
    img = canvas()
    d = ImageDraw.Draw(img)
    base, light, shadow = DARK

    poly(d, [(10, 10), (30, 10), (28, 32), (20, 36), (12, 32)], base)
    poly(d, [(10, 10), (20, 10), (20, 36), (12, 32)], light)
    poly(d, [(20, 10), (30, 10), (28, 32), (20, 36)], shadow)
    rect(d, [4, 8, 12, 17], base)                                   # ombreiras
    rect(d, [28, 8, 36, 17], base)
    rect(d, [4, 8, 12, 10], light)

    for y in (15, 21, 27):                                          # trilhas neurais
        rect(d, [12, y, 28, y + 1], RED[0])
    rect(d, [19, 12, 21, 32], RED[2])

    for box in ([13, 12, 17, 16], [23, 12, 27, 16], [17, 26, 23, 32]):
        rect(d, box, STEEL[2])                                      # encaixes
        rect(d, [box[0] + 1, box[1] + 1, box[2] - 1, box[3] - 1], RED[1])

    return save(img, "implant_black_carapace")


# ============================================================================ main


ICONS = [
    branch_vitality_heart,
    branch_resilience_armour,
    branch_damage_sword,
    branch_mobility_boot,
    branch_faith_aquila,
    root_guardsman_helmet,
    ascension_space_marine_helmet,
    implant_secondary_heart,
    implant_ossmodula_bone,
    implant_biscopea_muscle,
    implant_haemastamen_blood,
    implant_larraman_clot,
    implant_catalepsean_brain,
    implant_preomnor_omophagea_stomach,
    implant_multi_lung,
    implant_occulobe_lyman_eye_ear,
    implant_susan_melanochrome_oolitic,
    implant_chemical_maturity_glands,
    implant_black_carapace,
]


def main():
    written = [maker() for maker in ICONS]

    print("%d icones escritos em %s" % (len(written), OUT))
    for name in written:
        path = os.path.join(OUT, name + ".png")
        with Image.open(path) as img:
            filled = sum(1 for pixel in img.getdata() if pixel[3] > 0)
        print("  %-42s %dx%d  %d px opacos" % (name + ".png", img.width, img.height, filled))


if __name__ == "__main__":
    main()
