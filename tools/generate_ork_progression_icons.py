#!/usr/bin/env python3
"""
Os icones da arvore ORK: 5 ramos, 5 estagios e 9 nos que merecem cara propria.

O que este script possui
------------------------
Somente `assets/firstcrusade/textures/gui/progression/ork/*.png`. Nao encosta em textura de
entidade, de item, de bloco, de planeta nem nos icones Imperiais — um caminho, um dono
(ver tools/generate_biomes.py sobre o que custou aprender essa regra).

Por que nao reaproveitar generate_progression_icons.py
------------------------------------------------------
A familia Imperial e vermelha, dourada e simetrica: aguia, peitoral, orgao. A Ork tem que ser a
outra coisa na tela — verde, torta, remendada, com dente. Se as duas saissem do mesmo arquivo, a
tentacao seria compartilhar a paleta, e no fim as duas arvores contariam a mesma historia com
outro nome. Dois arquivos, duas paletas, duas familias.

A gramatica visual
------------------
Nada de simetria: toda chapa e cortada torta, todo parafuso e desalinhado. A luz vem de cima e da
esquerda nos 19, que e metade do motivo de parecerem um conjunto so. O contorno nunca e desenhado
a mao — `outline()` deriva da forma, entao sai exato em qualquer silhueta e continua com 1 pixel
nas diagonais.

Nada de antialiasing: o ImageDraw do Pillow nao suavisa poligono nem elipse, e nenhuma imagem e
redimensionada depois de pronta. Um icone borrado em 40x40 vira uma mancha no jogo.

Determinismo
------------
Nao ha `random` em lugar nenhum. Os desalinhamentos sao constantes escritas a mao, entao rodar o
script duas vezes escreve exatamente os mesmos bytes — que e o que permite versionar os PNGs.

Uso:
    python tools/generate_ork_progression_icons.py
"""

import os

from PIL import Image, ImageDraw

SIZE = 40
OUT = os.path.join("src", "main", "resources", "assets", "firstcrusade",
                   "textures", "gui", "progression", "ork")

# ============================================================================ paleta
#
# Tres tons por material: base, luz e sombra. Verde de Ork, metal enferrujado, dente e o amarelo
# dos Bad Moons. Nada de dourado imperial aqui.

OUTLINE = (12, 14, 10, 255)

SKIN = ((86, 138, 58), (124, 178, 88), (52, 92, 34))
SKIN_DARK = ((66, 112, 44), (98, 148, 68), (38, 72, 26))
IRON = ((116, 116, 122), (162, 162, 168), (68, 68, 76))
RUST = ((146, 90, 48), (186, 128, 76), (96, 54, 26))
TOOTH = ((228, 224, 198), (248, 246, 230), (166, 160, 134))
BLOOD = ((162, 40, 44), (198, 72, 74), (108, 22, 28))
YELLOW = ((214, 176, 60), (240, 212, 108), (148, 116, 24))
DARK = ((40, 44, 38), (68, 74, 64), (20, 22, 18))
SMOKE = ((92, 96, 90), (134, 138, 130), (58, 62, 56))


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


def line(draw, points, colour, width=1):
    draw.line(points, fill=rgba(colour), width=width)


def rivets(draw, spots, colour=IRON):
    """
    Parafusos, sempre em numero impar e nunca alinhados.

    Uma fileira certinha de rebites le como fabrica imperial. O ponto do metal Ork e que alguem
    bateu ate entrar.
    """
    for x, y in spots:
        rect(draw, [x, y, x + 1, y + 1], colour[2])


def tusks(draw, cx, cy, spread=7, height=6):
    """As duas presas de baixo. Nunca do mesmo tamanho: a esquerda e sempre a maior."""
    base, light, shadow = TOOTH
    poly(draw, [(cx - spread, cy), (cx - spread + 4, cy), (cx - spread + 2, cy - height)], base)
    poly(draw, [(cx + spread - 3, cy), (cx + spread, cy), (cx + spread - 1, cy - height + 2)], base)
    rect(draw, [cx - spread, cy - 1, cx - spread + 4, cy], shadow)


def ork_head(draw, cx, cy, w, h, palette=SKIN, jaw=True):
    """
    A cabeca verde: cranio baixo, mandibula larga, testa que avanca.

    Reaproveitada pelos cinco estagios e por metade dos nos. O que muda entre um Boy e um Warboss e
    a proporcao da mandibula, nao um desenho novo — e por isso que os cinco parecem o mesmo bicho
    em cinco tamanhos, que e exatamente a historia que a escada conta.
    """
    base, light, shadow = palette
    half_w = w // 2
    half_h = h // 2

    # Cranio: mais largo embaixo que em cima.
    poly(draw, [(cx - half_w + 2, cy - half_h),
                (cx + half_w - 3, cy - half_h),
                (cx + half_w, cy + half_h - 4),
                (cx + half_w - 4, cy + half_h),
                (cx - half_w + 3, cy + half_h),
                (cx - half_w, cy + half_h - 5)], base)

    # Luz na testa e na face esquerda, sombra na direita. Uma direcao so, nos 19.
    poly(draw, [(cx - half_w + 2, cy - half_h), (cx - 1, cy - half_h),
                (cx - 1, cy + half_h - 2), (cx - half_w, cy + half_h - 5)], light)
    poly(draw, [(cx + 2, cy - half_h), (cx + half_w - 3, cy - half_h),
                (cx + half_w, cy + half_h - 4), (cx + 2, cy + half_h - 1)], shadow)

    # Sobrancelha pesada: uma barra escura atravessada, mais grossa de um lado.
    poly(draw, [(cx - half_w + 1, cy - 3), (cx + half_w - 1, cy - 4),
                (cx + half_w - 1, cy - 1), (cx - half_w + 1, cy - 1)], shadow)

    # Olhos: dois pontos amarelos sob a sobrancelha, o direito um pixel mais alto.
    rect(draw, [cx - 5, cy, cx - 3, cy + 2], YELLOW[0])
    rect(draw, [cx + 3, cy - 1, cx + 5, cy + 1], YELLOW[0])

    if jaw:
        tusks(draw, cx, cy + half_h - 1, spread=half_w - 2, height=6)


# ============================================================================ ramos


def branch_brutal_choppa():
    """Cutelo. Lamina torta de proposito: chapa de sucata batida, nao forjada."""
    img = canvas()
    d = ImageDraw.Draw(img)
    base, light, shadow = IRON

    poly(d, [(11, 4), (30, 8), (33, 26), (13, 24)], base)
    poly(d, [(11, 4), (21, 6), (22, 24), (13, 24)], light)
    poly(d, [(24, 7), (30, 8), (33, 26), (25, 25)], shadow)

    # Um pedaco faltando no fio: o cutelo ja krumpou coisa demais.
    poly(d, [(29, 12), (34, 15), (29, 18)], (0, 0, 0), alpha=0)

    rivets(d, [(15, 9), (19, 15), (26, 12)])

    rect(d, [17, 25, 23, 37], RUST[0])
    rect(d, [17, 25, 19, 37], RUST[1])
    rect(d, [21, 28, 23, 37], RUST[2])
    rect(d, [15, 34, 25, 37], DARK[0])
    return save(img, "branch_brutal_choppa")


def branch_tuff_plate():
    """Chapa remendada: duas placas de tamanhos diferentes, parafusadas por cima uma da outra."""
    img = canvas()
    d = ImageDraw.Draw(img)
    base, light, shadow = IRON

    poly(d, [(7, 7), (30, 5), (33, 30), (10, 34)], base)
    poly(d, [(7, 7), (19, 6), (20, 33), (10, 34)], light)
    poly(d, [(23, 6), (30, 5), (33, 30), (24, 32)], shadow)

    # O remendo: uma segunda chapa, menor, torta e enferrujada.
    poly(d, [(16, 13), (29, 11), (30, 24), (17, 26)], RUST[0])
    poly(d, [(16, 13), (22, 12), (22, 25), (17, 26)], RUST[1])

    rivets(d, [(9, 10), (11, 30), (28, 8), (31, 27), (18, 15), (28, 22), (19, 23)])
    return save(img, "branch_tuff_plate")


def branch_dakka_shoota():
    """Cano grosso, carregador torto, coronha de madeira. Nada nela e reto."""
    img = canvas()
    d = ImageDraw.Draw(img)
    base, light, shadow = IRON

    rect(d, [4, 13, 30, 20], base)          # corpo
    rect(d, [4, 13, 30, 15], light)
    rect(d, [4, 19, 30, 20], shadow)

    rect(d, [28, 14, 37, 19], base)         # cano
    rect(d, [28, 14, 37, 15], light)
    ellipse(d, [34, 12, 39, 21], SMOKE[0])  # boca de sino

    poly(d, [(11, 20), (19, 20), (17, 32), (9, 31)], RUST[0])   # carregador, torto
    poly(d, [(11, 20), (15, 20), (14, 32), (9, 31)], RUST[1])

    poly(d, [(4, 20), (10, 20), (7, 29), (2, 27)], DARK[0])     # coronha
    rivets(d, [(7, 16), (24, 17), (13, 23), (31, 16)])
    return save(img, "branch_dakka_shoota")


def branch_kunnin_teef():
    """
    Tres dentes numa fileira, com raiz. A moeda: KUNNIN paga em dentu, nao em dano.

    Empilhados em profundidade, como estava na primeira versao, os tres liam como papel rasgado —
    sem raiz e sem linha de base comum nada dizia "dente". Uma fileira apoiada na mesma altura, cada
    um com a raiz vermelha embaixo, resolve com a mesma quantidade de pixel.
    """
    img = canvas()
    d = ImageDraw.Draw(img)
    base, light, shadow = TOOTH

    # (esquerda, direita, topo). Alturas diferentes de proposito: fileira certinha e dentadura.
    for left, right, top in ((5, 15, 9), (15, 26, 4), (26, 35, 11)):
        middle = (left + right) // 2
        poly(d, [(left, top), (right, top + 1), (right - 1, 26), (left + 1, 26)], base)
        poly(d, [(left, top), (middle, top), (middle, 26), (left + 1, 26)], light)
        poly(d, [(right - 3, top), (right, top + 1), (right - 1, 26), (right - 3, 26)], shadow)

        # A raiz: sempre mais estreita que a coroa, e sempre suja.
        poly(d, [(left + 2, 26), (right - 2, 26), (middle + 1, 34), (middle - 2, 34)], BLOOD[2])

    # Uma lasca escura no do meio: dente de Ork nao e dente de propaganda.
    rect(d, [22, 8, 24, 16], shadow)
    return save(img, "branch_kunnin_teef")


def branch_waaagh_mouth():
    """Boca escancarada gritando, com as presas e as ondas do som saindo dos dois lados."""
    img = canvas()
    d = ImageDraw.Draw(img)

    ellipse(d, [11, 8, 31, 32], SKIN[0])
    ellipse(d, [11, 8, 22, 32], SKIN[1])
    ellipse(d, [15, 13, 28, 29], DARK[2])        # o buraco do grito

    # Presas: duas em baixo, uma em cima, nenhuma do mesmo tamanho.
    poly(d, [(17, 29), (21, 29), (19, 21)], TOOTH[0])
    poly(d, [(23, 29), (26, 29), (25, 23)], TOOTH[0])
    poly(d, [(20, 13), (24, 13), (22, 19)], TOOTH[2])

    # As ondas. Tres de cada lado, encolhendo: o som e a metade do icone.
    for index, (dx, height) in enumerate(((5, 10), (8, 7), (11, 4))):
        colour = YELLOW[0] if index == 0 else YELLOW[2]
        rect(d, [21 - 11 - dx, 20 - height // 2, 21 - 10 - dx, 20 + height // 2], colour)
        rect(d, [21 + 10 + dx, 20 - height // 2, 21 + 11 + dx, 20 + height // 2], colour)

    return save(img, "branch_waaagh_mouth")


# ============================================================================ estagios
#
# Os cinco sao a mesma cabeca em cinco proporcoes, com uma coisa a mais por degrau. Um icone novo
# por estagio diria "cinco bichos"; a mesma cabeca crescendo diz "o mesmo git, maior", que e a
# unica coisa que a escada Ork faz.


def stage_ork_boy():
    img = canvas()
    d = ImageDraw.Draw(img)
    ork_head(d, 20, 21, 20, 22)
    return save(img, "stage_ork_boy")


def stage_big_boy():
    img = canvas()
    d = ImageDraw.Draw(img)
    ork_head(d, 20, 21, 25, 26)
    return save(img, "stage_big_boy")


def stage_ork_nob():
    """Nob: cabeca maior e a primeira chapa na testa, parafusada torta."""
    img = canvas()
    d = ImageDraw.Draw(img)
    ork_head(d, 20, 22, 28, 28)

    poly(d, [(6, 8), (33, 6), (34, 13), (7, 15)], IRON[0])
    poly(d, [(6, 8), (20, 7), (20, 14), (7, 15)], IRON[1])
    rivets(d, [(9, 10), (18, 9), (30, 9)])
    return save(img, "stage_ork_nob")


def stage_big_nob():
    """Big Nob: a chapa virou capacete, e ele ganhou um chifre torto."""
    img = canvas()
    d = ImageDraw.Draw(img)
    ork_head(d, 20, 23, 30, 30, palette=SKIN_DARK)

    poly(d, [(4, 6), (35, 4), (36, 15), (5, 17)], IRON[0])
    poly(d, [(4, 6), (20, 5), (20, 16), (5, 17)], IRON[1])
    poly(d, [(28, 5), (35, 4), (36, 15), (29, 16)], IRON[2])

    poly(d, [(33, 6), (39, 2), (37, 11)], RUST[0])       # chifre, so de um lado
    rivets(d, [(7, 9), (16, 8), (26, 10), (33, 8), (11, 14)])
    return save(img, "stage_big_nob")


def stage_warboss():
    """Warboss: cabeca cheia de tela, dois chifres e o glifo do chefe na frente."""
    img = canvas()
    d = ImageDraw.Draw(img)
    ork_head(d, 20, 24, 32, 30, palette=SKIN_DARK)

    poly(d, [(2, 5), (37, 2), (38, 16), (3, 19)], IRON[0])
    poly(d, [(2, 5), (20, 4), (20, 18), (3, 19)], IRON[1])
    poly(d, [(29, 3), (37, 2), (38, 16), (30, 17)], IRON[2])

    poly(d, [(2, 6), (0, 0), (9, 3)], RUST[0])           # chifre esquerdo
    poly(d, [(37, 3), (39, 0), (33, 2)], RUST[2])        # direito, menor

    # O glifo: o dente do chefe, em amarelo, torto na chapa.
    poly(d, [(17, 7), (23, 6), (22, 15), (18, 14)], YELLOW[0])
    poly(d, [(17, 7), (20, 7), (20, 15), (18, 14)], YELLOW[1])

    rivets(d, [(6, 9), (12, 7), (27, 8), (34, 11), (9, 15)])
    return save(img, "stage_warboss")


# ============================================================================ nos com cara propria
#
# So os que a Fase D mostra grandes e que um icone de ramo nao explicaria. Todo no ordinario cai no
# icone do ramo dele, que e por que a arvore pode crescer sem desenhar nada.


def node_eadbutt():
    """Uma cabeca de perfil batendo numa chapa, com as linhas de impacto."""
    img = canvas()
    d = ImageDraw.Draw(img)

    ork_head(d, 15, 22, 20, 22, jaw=False)
    rect(d, [28, 6, 34, 36], IRON[0])
    rect(d, [28, 6, 30, 36], IRON[1])

    # Impacto: tres tracos amarelos saindo do ponto de contato.
    for dy, length in ((-7, 5), (0, 8), (7, 5)):
        rect(d, [26, 21 + dy, 26 + length, 22 + dy], YELLOW[0])

    rect(d, [30, 16, 32, 26], RUST[2])      # o amassado na chapa
    return save(img, "node_eadbutt")


def node_krump_first():
    """Punho fechado indo para a direita, com as linhas de velocidade atras."""
    img = canvas()
    d = ImageDraw.Draw(img)
    base, light, shadow = SKIN

    poly(d, [(14, 11), (30, 9), (32, 28), (15, 30)], base)
    poly(d, [(14, 11), (23, 10), (23, 29), (15, 30)], light)
    poly(d, [(27, 9), (30, 9), (32, 28), (28, 29)], shadow)

    # Nos dos dedos: tres sulcos, o do meio mais fundo.
    for x, depth in ((18, 2), (23, 3), (28, 2)):
        rect(d, [x, 12, x + 1, 12 + depth * 3], shadow)

    rect(d, [11, 14, 17, 25], RUST[0])      # o punho da luva
    rivets(d, [(13, 17), (15, 22)])

    for dy, length in ((-6, 7), (2, 10), (9, 6)):
        rect(d, [2, 19 + dy, 2 + length, 20 + dy], YELLOW[2])

    return save(img, "node_krump_first")


def node_not_dead_yet():
    """Cranio rachado com um olho verde ainda aceso dentro. Ele decide quando cai."""
    img = canvas()
    d = ImageDraw.Draw(img)
    base, light, shadow = TOOTH

    poly(d, [(9, 7), (30, 5), (33, 24), (28, 31), (12, 32), (7, 23)], base)
    poly(d, [(9, 7), (20, 6), (20, 32), (12, 32), (7, 23)], light)
    poly(d, [(24, 6), (30, 5), (33, 24), (28, 31), (24, 31)], shadow)

    ellipse(d, [11, 14, 18, 22], DARK[2])        # orbita esquerda
    ellipse(d, [22, 13, 29, 21], DARK[2])
    rect(d, [13, 17, 16, 20], SKIN[1])           # o olho que ainda esta ali

    # A rachadura: uma linha quebrada, nunca reta.
    line(d, [(21, 5), (19, 12), (23, 16), (20, 23)], DARK[2])
    rect(d, [16, 25, 25, 28], shadow)            # os dentes de cima
    return save(img, "node_not_dead_yet")


def node_mega_platin():
    """Ombreira enorme, tres chapas sobrepostas. Grossa demais para um Boy carregar."""
    img = canvas()
    d = ImageDraw.Draw(img)

    poly(d, [(4, 12), (34, 8), (36, 20), (5, 24)], IRON[0])
    poly(d, [(4, 12), (20, 10), (20, 22), (5, 24)], IRON[1])
    poly(d, [(6, 22), (33, 18), (34, 28), (7, 31)], IRON[0])
    poly(d, [(6, 22), (20, 20), (20, 30), (7, 31)], IRON[2])
    poly(d, [(9, 29), (30, 26), (31, 34), (10, 36)], RUST[0])

    rivets(d, [(7, 15), (17, 12), (29, 11), (11, 25), (24, 22), (14, 32), (27, 30)])
    return save(img, "node_mega_platin")


def node_boyz_come_here():
    """Tres cabecas pequenas vindo na direcao de uma seta. A ordem, nao o chefe."""
    img = canvas()
    d = ImageDraw.Draw(img)

    ork_head(d, 10, 26, 13, 14, palette=SKIN_DARK, jaw=False)
    ork_head(d, 22, 29, 12, 13, palette=SKIN_DARK, jaw=False)
    ork_head(d, 32, 25, 11, 12, palette=SKIN_DARK, jaw=False)

    # A seta para cima: para onde eles estao indo.
    poly(d, [(20, 2), (28, 12), (23, 12), (23, 17), (17, 17), (17, 12), (12, 12)], YELLOW[0])
    poly(d, [(20, 2), (23, 6), (23, 17), (17, 17), (17, 12), (12, 12)], YELLOW[1])
    return save(img, "node_boyz_come_here")


def node_im_da_boss():
    """Um dedo apontando, com o glifo do chefe atras. Aponta pro git, os Boyz resolvem."""
    img = canvas()
    d = ImageDraw.Draw(img)
    base, light, shadow = SKIN

    poly(d, [(6, 18), (26, 16), (27, 25), (7, 27)], base)   # mao
    poly(d, [(6, 18), (16, 17), (16, 26), (7, 27)], light)
    rect(d, [24, 18, 36, 22], base)                         # o dedo
    rect(d, [24, 18, 36, 19], light)
    rect(d, [24, 21, 36, 22], shadow)

    rect(d, [8, 24, 20, 30], RUST[0])                       # o bracelete
    rivets(d, [(10, 26), (16, 27)])

    poly(d, [(14, 4), (24, 3), (23, 13), (15, 12)], YELLOW[2])   # glifo do chefe
    poly(d, [(14, 4), (18, 4), (18, 13), (15, 12)], YELLOW[0])
    return save(img, "node_im_da_boss")


def node_waaagh_roar():
    """A boca do ramo, mas cheia: ondas maiores e o verde mais forte. O grito, nao a categoria."""
    img = canvas()
    d = ImageDraw.Draw(img)

    ork_head(d, 20, 22, 26, 26, palette=SKIN, jaw=False)
    ellipse(d, [13, 22, 28, 35], DARK[2])            # a boca aberta
    poly(d, [(15, 34), (19, 34), (17, 26)], TOOTH[0])
    poly(d, [(22, 34), (26, 34), (25, 27)], TOOTH[0])
    poly(d, [(18, 23), (23, 23), (21, 29)], TOOTH[2])

    for dx in (2, 6, 10):
        rect(d, [dx - 2, 12, dx - 1, 24], YELLOW[0] if dx == 2 else YELLOW[2])
        rect(d, [41 - dx, 12, 42 - dx, 24], YELLOW[0] if dx == 2 else YELLOW[2])

    return save(img, "node_waaagh_roar")


def node_loot_it_all():
    """Saco cheio com dentes transbordando. LOOT IT ALL nao e sutil."""
    img = canvas()
    d = ImageDraw.Draw(img)

    poly(d, [(10, 16), (30, 14), (34, 33), (8, 35)], RUST[0])
    poly(d, [(10, 16), (20, 15), (20, 34), (8, 35)], RUST[1])
    poly(d, [(26, 14), (30, 14), (34, 33), (27, 34)], RUST[2])
    rect(d, [12, 13, 28, 17], DARK[0])           # a corda

    # Os dentes saindo por cima: tres, tamanhos diferentes.
    poly(d, [(12, 13), (17, 12), (16, 4), (13, 5)], TOOTH[0])
    poly(d, [(19, 12), (25, 12), (24, 2), (20, 3)], TOOTH[0])
    poly(d, [(26, 13), (30, 13), (30, 7), (27, 7)], TOOTH[2])
    return save(img, "node_loot_it_all")


def node_big_teef():
    """Um dente so, enorme, com a raiz ainda suja. Big gitz got big teef."""
    img = canvas()
    d = ImageDraw.Draw(img)
    base, light, shadow = TOOTH

    poly(d, [(9, 5), (31, 8), (27, 27), (22, 36), (17, 27), (11, 24)], base)
    poly(d, [(9, 5), (20, 6), (20, 36), (17, 27), (11, 24)], light)
    poly(d, [(26, 7), (31, 8), (27, 27), (23, 33)], shadow)

    rect(d, [17, 30, 26, 34], BLOOD[2])          # a raiz, ainda suja
    rect(d, [23, 12, 25, 20], shadow)            # a lasca
    return save(img, "node_big_teef")


# ============================================================================ main


ICONS = [
    branch_brutal_choppa,
    branch_tuff_plate,
    branch_dakka_shoota,
    branch_kunnin_teef,
    branch_waaagh_mouth,
    stage_ork_boy,
    stage_big_boy,
    stage_ork_nob,
    stage_big_nob,
    stage_warboss,
    node_eadbutt,
    node_krump_first,
    node_not_dead_yet,
    node_mega_platin,
    node_boyz_come_here,
    node_im_da_boss,
    node_waaagh_roar,
    node_loot_it_all,
    node_big_teef,
]


def main():
    written = [maker() for maker in ICONS]

    print("%d icones Ork escritos em %s" % (len(written), OUT))
    for name in written:
        path = os.path.join(OUT, name + ".png")
        with Image.open(path) as img:
            filled = sum(1 for pixel in img.getdata() if pixel[3] > 0)
        print("  %-34s %dx%d  %d px opacos" % (name + ".png", img.width, img.height, filled))


if __name__ == "__main__":
    main()
