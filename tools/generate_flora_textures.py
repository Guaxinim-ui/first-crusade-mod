#!/usr/bin/env python3
"""
Gera as 56 texturas 16x16 da vegetacao do First Crusade.

Por que existe: as texturas da Fase 2 tinham 2-3 cores e laminas retas de largura
constante, o que no jogo aparece como barras solidas em vez de folhagem. Este script
desenha cada planta a partir de uma rampa de 5 tons e de formas que afinam na ponta,
que e o que faz uma textura de planta ler como planta num cubo de 16 pixels.

Regras seguidas em todas as saidas:
  - alfa estritamente 0 ou 255 (os modelos usam render_type cutout);
  - rampa de 5 tons por especie, com sombra de um lado da lamina e luz do outro;
  - laminas com base de 2px afinando para 1px na ponta, com inclinacao/curva;
  - ocupacao dos 16px de largura, nao amontoada de um lado so;
  - determinismo: a semente vem do nome da textura, entao rodar de novo da o mesmo
    resultado e um diff limpo.

Uso:
    python tools/generate_flora_textures.py            # escreve as texturas
    python tools/generate_flora_textures.py --sheet    # + folha de contato ampliada
"""

import argparse
import os
import random

from PIL import Image

SIZE = 16
OUT = os.path.join("src", "main", "resources", "assets", "firstcrusade", "textures", "block")
CLEAR = (0, 0, 0, 0)


# ----------------------------------------------------------------------------- cor


def ramp(base, spread=0.28, warm=0.0):
    """Cinco tons a partir de uma cor base: sombra profunda -> realce.

    'warm' empurra os tons claros para o amarelo e os escuros para o azul, que e o
    que impede uma rampa puramente multiplicativa de parecer plastico.
    """
    r, g, b = base
    out = []
    for i, k in enumerate((1.0 - spread, 1.0 - spread * 0.55, 1.0, 1.0 + spread * 0.5, 1.0 + spread * 0.85)):
        t = (i - 2) / 2.0
        rr = r * k + warm * 26 * t
        gg = g * k + warm * 16 * t
        bb = b * k - warm * 20 * t
        out.append((
            max(0, min(255, int(round(rr)))),
            max(0, min(255, int(round(gg)))),
            max(0, min(255, int(round(bb)))),
            255,
        ))
    return out


# --------------------------------------------------------------------------- tela


class Tex:
    def __init__(self, name, w=SIZE, h=SIZE):
        self.name = name
        self.w = w
        self.h = h
        self.img = Image.new("RGBA", (w, h), CLEAR)
        self.p = self.img.load()
        self.rng = random.Random(hash(name) & 0xFFFFFFFF)

    def put(self, x, y, c, over=True):
        if 0 <= x < self.w and 0 <= y < self.h:
            if over or self.p[x, y][3] == 0:
                self.p[x, y] = c

    def get(self, x, y):
        if 0 <= x < self.w and 0 <= y < self.h:
            return self.p[x, y]
        return CLEAR

    # -- lamina de capim: arco, 1px na maior parte, engrossando so na base --------
    def blade(self, x, ybase, height, pal, lean=0.0, curve=0.0, thick=2, tipup=True):
        """Uma lamina.

        O arco usa t**1.7 em vez de t linear: a folha sobe quase reta e so abre perto
        da ponta, que e como capim realmente cai. Manter 1px de largura acima do
        primeiro quarto e o que impede a textura de virar uma barra solida.

        Cada lamina sorteia um deslocamento na rampa, entao um tufo tem folhas mais
        claras e mais escuras em vez de um pente de uma cor so.
        """
        shift = self.rng.choice((-1, 0, 0, 0, 1))
        direction = 1 if lean >= 0 else -1

        def tone_at(t, i):
            idx = 1 if t < 0.3 else (2 if t < 0.7 else 3)
            # leve variacao ao longo do comprimento: sem ela, o pedaco de folha que
            # cabe no bloco de cima de uma planta alta sai de uma cor chapada so
            if i % 3 == 0:
                idx += 1
            return pal[max(0, min(4, idx + shift))]

        for i in range(height):
            t = i / max(1, height - 1)
            xx = x + int(round(lean * (t ** 1.7) + curve * (t ** 2.4)))
            y = ybase - i

            self.put(xx, y, tone_at(t, i))

            # engrossa so o pe da folha, e no lado de dentro do arco
            if thick >= 2 and t < 0.28:
                self.put(xx - direction, y, pal[max(0, shift)])

        if tipup and height > 3:
            tip_x = x + int(round(lean + curve))
            self.put(tip_x, ybase - height, pal[min(4, 3 + max(0, shift))])

    # -- fronde de samambaia: raque em arco forte + foliolos curtos ---------------
    def frond(self, cx, ybase, height, pal, spread=3, lean=4.0):
        """Uma fronde, desenhada como pena curva e nao como triangulo centrado.

        O que fazia a versao anterior parecer pinheiro de natal era a combinacao de
        raque reta + foliolos longos e simetricos: isso desenha um triangulo. Aqui a
        raque abre forte para um lado (arco), os foliolos sao curtos (2-3px) e seguem
        a inclinacao da raque, entao a silhueta e uma pena — que e o que uma
        samambaia parece a 16 pixels.
        """
        rachis = []
        for i in range(height):
            t = i / max(1, height - 1)
            x = cx + int(round(lean * (t ** 1.9)))
            y = ybase - i
            rachis.append((x, y, t))
            self.put(x, y, pal[1] if i % 2 else pal[2])

        outward = 1 if lean >= 0 else -1

        for idx, (x, y, t) in enumerate(rachis):
            if t < 0.12 or idx % 2:
                continue

            # foliolos curtos, encolhendo para a ponta
            arm = max(1, int(round(spread * (1.0 - t * 0.6))))
            tone = pal[3] if idx % 4 == 0 else pal[2]

            for sign in (-outward, outward):
                reach = arm if sign == outward else max(1, arm - 1)
                for d in range(1, reach + 1):
                    rise = (d + 1) // 2
                    self.put(x + sign * d, y - rise, pal[1] if d == reach else tone)

        self.put(rachis[-1][0] + outward, rachis[-1][1] - 1, pal[4])

    # -- cogumelo: pe + chapeu abaulado com lamelas -------------------------------
    def mushroom(self, cx, ybase, stem_h, cap_w, pal, stem_pal=None):
        sp = stem_pal or pal
        for i in range(stem_h):
            y = ybase - i
            self.put(cx, y, sp[1])
            self.put(cx + 1, y, sp[3] if i < stem_h - 1 else sp[2])

        cap_y = ybase - stem_h
        half = max(2, cap_w // 2)

        # lamelas sob a borda do chapeu
        for d in range(-half, half + 1):
            if abs(d) <= half - 1:
                self.put(cx + d, cap_y, sp[0])

        # Cupula por circunferencia, nao por linhas encolhendo: o degrau linear
        # anterior desenhava um chapeu estreito e chato, que a 16px le como um "T".
        rows = max(2, half)
        for r in range(rows):
            y = cap_y - 1 - r
            v = (r + 0.5) / rows
            reach = int(round(half * (1.0 - v * v) ** 0.5))

            for d in range(-reach, reach + 1):
                if abs(d) == reach and reach > 1:
                    tone = pal[1]
                elif d < -reach + 1 and r >= rows - 2:
                    tone = pal[4]
                else:
                    tone = pal[3] if d < 0 else pal[2]
                self.put(cx + d, y, tone)

    # -- flor: haste, uma folha e um miolo de petalas ------------------------------
    def flower(self, cx, ybase, stem_h, pal, petal, centre=None, leaf=True):
        for i in range(stem_h):
            y = ybase - i
            self.put(cx, y, pal[1] if i % 3 else pal[2])
        if leaf:
            ly = ybase - stem_h // 2
            side = 1 if self.rng.random() < 0.5 else -1
            self.put(cx + side, ly, pal[2])
            self.put(cx + side * 2, ly, pal[1])
            self.put(cx + side * 2, ly - 1, pal[3])

        hy = ybase - stem_h
        # corola de 3x3 com quinas cortadas
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                if abs(dx) == 1 and abs(dy) == 1 and self.rng.random() < 0.45:
                    continue
                tone = petal[3] if (dx <= 0 and dy <= 0) else petal[2]
                if dx == 1 or dy == 1:
                    tone = petal[1]
                self.put(cx + dx, hy + dy, tone)
        self.put(cx, hy - 2, petal[4])
        self.put(cx - 1, hy - 1, petal[4])
        if centre:
            self.put(cx, hy, centre)

    # -- tufo generico: varias laminas espalhadas ---------------------------------
    def tuft(self, pal, count=7, hmin=5, hmax=12, ybase=15, thick=2, lean=2.2, spread=(1, 14)):
        """Um tufo.

        A folha tende a abrir para longe do centro (um tufo real se abre em leque),
        mas uma em cada quatro cai para o outro lado — sem isso o desenho fica um V
        perfeitamente simetrico, que e a leitura mais artificial possivel.
        """
        xs = self._spread(count, spread[0], spread[1])
        centre = (spread[0] + spread[1]) / 2.0

        for x in xs:
            h = self.rng.randint(hmin, hmax)
            side = 1 if x >= centre else -1
            if self.rng.random() < 0.25:
                side = -side

            ln = side * self.rng.uniform(0.55, 1.15) * lean
            cv = side * self.rng.uniform(0.25, 0.9) * lean

            self.blade(x, ybase - self.rng.randint(0, 1), h, pal,
                       lean=ln, curve=cv, thick=thick)

    def _spread(self, count, lo, hi):
        """Posicoes bem distribuidas: uma por celula, com jitter dentro da celula."""
        xs = []
        span = (hi - lo) / max(1, count)
        for i in range(count):
            base = lo + span * i
            xs.append(int(round(base + self.rng.uniform(0, max(0.8, span - 0.6)))))
        self.rng.shuffle(xs)
        return xs

    # -- liquen para a face plana: rendilhado, nao mancha solida ------------------
    def lichen(self, pal, tendrils=9, speckles=26):
        """Musgo/liquen grudado numa parede.

        O desenho e rendilhado de proposito. Uma mancha solida vira um adesivo
        retangular na parede; o que faz o liquen ler como liquen e a colonia crescer
        em veios ramificados com buracos entre eles, deixando a parede aparecer.
        A textura fecha nas bordas (modulo) para colonias vizinhas se emendarem.
        """
        seeds = [(self.rng.randint(0, self.w - 1), self.rng.randint(0, self.h - 1))
                 for _ in range(3)]

        for _ in range(tendrils):
            x, y = self.rng.choice(seeds)
            steps = self.rng.randint(9, 20)
            dx, dy = self.rng.choice(((1, 0), (-1, 0), (0, 1), (0, -1)))

            for s in range(steps):
                if self.rng.random() < 0.35:
                    dx, dy = self.rng.choice(((1, 0), (-1, 0), (0, 1), (0, -1)))

                x = (x + dx) % self.w
                y = (y + dy) % self.h

                tone = pal[2] if s % 3 else pal[3]
                self.put(x, y, tone)

                # engrossa o veio de um lado, dando volume sem fechar tudo
                if self.rng.random() < 0.55:
                    self.put((x + dy) % self.w, (y + dx) % self.h, pal[1])
                if self.rng.random() < 0.16:
                    self.put((x - dy) % self.w, (y - dx) % self.h, pal[4])

        # esporos soltos encostados na colonia
        for _ in range(speckles):
            x = self.rng.randint(0, self.w - 1)
            y = self.rng.randint(0, self.h - 1)
            if self.get(x, y)[3]:
                continue
            near = any(self.get((x + dx) % self.w, (y + dy) % self.h)[3]
                       for dx in (-1, 0, 1) for dy in (-1, 0, 1))
            if near:
                self.put(x, y, pal[self.rng.choice((1, 2))])

    # -- tapete opaco de 16x16 -----------------------------------------------------
    def carpet(self, pal, blobs=26):
        """Folhas caidas / camada de cinza.

        Manchas em ambas as direcoes, nao ruido por pixel nem borrao horizontal: o
        passo de agrupamento anterior so deslocava em X e o resultado eram listras.
        A textura tambem precisa fechar nas bordas (o modulo), senao a emenda entre
        dois blocos de carpete aparece.
        """
        for x in range(self.w):
            for y in range(self.h):
                self.put(x, y, pal[2])

        for _ in range(blobs):
            cx = self.rng.randint(0, self.w - 1)
            cy = self.rng.randint(0, self.h - 1)
            r = self.rng.uniform(1.2, 3.0)
            tone = pal[self.rng.choice((0, 1, 1, 3, 3, 4))]
            rr = int(r) + 1
            for dy in range(-rr, rr + 1):
                for dx in range(-rr, rr + 1):
                    if (dx * dx + dy * dy) ** 0.5 + self.rng.uniform(-0.5, 0.5) <= r:
                        self.put((cx + dx) % self.w, (cy + dy) % self.h, tone)

        # grao fino por cima, para o tapete nao virar um mapa de bolhas
        for _ in range(self.w * self.h // 5):
            x = self.rng.randint(0, self.w - 1)
            y = self.rng.randint(0, self.h - 1)
            self.put(x, y, pal[self.rng.choice((1, 3))])

    # -- detalhe visto de cima (galhos, raizes, pedras, ossos) ---------------------
    def scatter_sticks(self, pal, count=7, length=(3, 7), thick_chance=0.25):
        for _ in range(count):
            x = self.rng.randint(1, self.w - 2)
            y = self.rng.randint(1, self.h - 2)
            horiz = self.rng.random() < 0.5
            n = self.rng.randint(*length)
            drift = self.rng.choice((-1, 0, 0, 1))
            for i in range(n):
                if horiz:
                    xx, yy = x + i, y + (drift if i > n // 2 else 0)
                else:
                    xx, yy = x + (drift if i > n // 2 else 0), y + i
                tone = pal[1] if i in (0, n - 1) else pal[self.rng.choice((2, 2, 3))]
                self.put(xx % self.w, yy % self.h, tone)
                if self.rng.random() < thick_chance:
                    self.put((xx + 1) % self.w, yy % self.h, pal[1])

    def scatter_pebbles(self, pal, count=16):
        for _ in range(count):
            x = self.rng.randint(0, self.w - 1)
            y = self.rng.randint(0, self.h - 1)
            big = self.rng.random() < 0.45
            self.put(x, y, pal[3])
            self.put((x + 1) % self.w, y, pal[2])
            self.put(x, (y + 1) % self.h, pal[1])
            if big:
                self.put((x + 1) % self.w, (y + 1) % self.h, pal[1])
                self.put(x, (y - 1) % self.h, pal[4])

    def scatter_bones(self, pal, count=11):
        """Lascas de osso: muitos fragmentos pequenos, nao poucos ossos grandes.

        Sao restos pisoteados no chao, entao a leitura certa e cascalho claro
        espalhado — femures desenhaveis nao cabem em 16px sem virar um zigue-zague.
        """
        for _ in range(count):
            x = self.rng.randint(0, self.w - 1)
            y = self.rng.randint(0, self.h - 1)
            n = self.rng.randint(2, 3)
            horiz = self.rng.random() < 0.5

            for i in range(n):
                xx, yy = (x + i, y) if horiz else (x, y + i)
                tone = pal[4] if i == 0 else pal[3]
                self.put(xx % self.w, yy % self.h, tone)

            # sombra de contato de um lado, para a lasca assentar no chao
            if horiz:
                self.put(x % self.w, (y + 1) % self.h, pal[1])
            else:
                self.put((x + 1) % self.w, y % self.h, pal[1])

        for _ in range(6):
            self.put(self.rng.randint(0, self.w - 1), self.rng.randint(0, self.h - 1), pal[2])

    def save(self, path):
        self.img.save(path)


# --------------------------------------------------------------------- especies

G = ramp  # atalho

SPECIES = {
    # ---- imperial ----------------------------------------------------------
    "imperial_grass":      ("tuft",   (86, 112, 58),  dict(count=8, hmin=6, hmax=11)),
    "withered_scrub":      ("scrub",  (124, 104, 66), dict(count=7, hmin=4, hmax=8)),
    "dark_fern":           ("fern",   (44, 78, 46),   dict(height=12, spread=5)),
    "memorial_bloom":      ("flower", (78, 96, 62),   dict(stem=8, petal=(214, 216, 206))),
    "aquila_bloom":        ("flower", (86, 98, 56),   dict(stem=8, petal=(206, 168, 54), centre=(120, 84, 22))),
    "ossuary_lily":        ("flower", (92, 104, 78),  dict(stem=9, petal=(228, 226, 214), centre=(196, 176, 120))),
    "roadside_thistle":    ("flower", (104, 100, 62), dict(stem=8, petal=(140, 112, 152), centre=(96, 72, 108))),
    "tall_imperial_grass": ("tall",   (86, 112, 58),  dict(count=7, hmin=21, hmax=30)),

    # ---- forge -------------------------------------------------------------
    "ash_grass":           ("tuft",   (128, 128, 124), dict(count=8, hmin=5, hmax=10)),
    "soot_grass":          ("tuft",   (62, 60, 58),    dict(count=8, hmin=5, hmax=10)),
    "burnt_stubble":       ("stubble", (54, 48, 44),   dict(count=10, hmin=2, hmax=5)),
    "promethium_weed":     ("tuft",   (150, 138, 66),  dict(count=7, hmin=5, hmax=10)),
    "chem_bloom":          ("flower", (98, 122, 70),   dict(stem=7, petal=(150, 214, 96), centre=(214, 240, 160))),
    "tall_ash_grass":      ("tall",   (128, 128, 124), dict(count=7, hmin=20, hmax=29)),

    # ---- hive / underhive --------------------------------------------------
    "crack_weed":          ("tuft",   (98, 106, 82),   dict(count=6, hmin=4, hmax=9)),
    "vent_grass":          ("stubble", (112, 92, 64),  dict(count=9, hmin=2, hmax=5)),
    "pallid_fungus":       ("fungus", (198, 194, 176), dict(caps=3)),
    "glow_cap":            ("fungus", (108, 196, 202), dict(caps=3, stem_col=(196, 206, 200))),
    "hab_fern":            ("fern",   (66, 104, 58),   dict(height=13, spread=5)),
    "sludge_algae":        ("mat",    (94, 96, 58),    dict()),

    # ---- ork ---------------------------------------------------------------
    "ork_fungus":          ("fungus", (94, 156, 54),   dict(caps=3)),
    "ork_spore_cap":       ("fungus", (118, 178, 62),  dict(caps=2, big=True)),
    "squig_grass":         ("tuft",   (78, 148, 56),   dict(count=8, hmin=6, hmax=11)),
    "trampled_grass":      ("stubble", (96, 116, 66),  dict(count=11, hmin=2, hmax=4)),
    "oil_stained_grass":   ("tuft",   (58, 68, 52),    dict(count=8, hmin=5, hmax=10)),
    "tall_squig_grass":    ("tall",   (78, 148, 56),   dict(count=7, hmin=21, hmax=30)),

    # ---- chaos -------------------------------------------------------------
    "thornweed":           ("thorn",  (132, 56, 52),   dict(count=6, hmin=6, hmax=11)),
    "writhing_grass":      ("tuft",   (104, 66, 128),  dict(count=8, hmin=6, hmax=11, curly=True)),
    "corrupted_bloom":     ("flower", (92, 62, 74),    dict(stem=8, petal=(168, 52, 58), centre=(58, 22, 30))),
    "pulsing_root":        ("mat",    (112, 70, 140),  dict(low=True)),
    "chaos_lichen":        ("lichen", (110, 68, 138),  dict()),

    # ---- death world -------------------------------------------------------
    "venom_frond":         ("fern",   (72, 130, 62),   dict(height=13, spread=6)),
    "spine_bush":          ("thorn",  (108, 78, 132),  dict(count=7, hmin=5, hmax=10)),
    "toxic_bloom":         ("flower", (74, 118, 62),   dict(stem=7, petal=(126, 208, 92), centre=(196, 236, 140))),
    "fanged_sprout":       ("fungus", (128, 92, 148),  dict(caps=2)),
    "mire_reed":           ("reed",   (122, 108, 70),  dict(count=6, hmin=8, hmax=14)),
    "tall_mire_reed":      ("tall",   (122, 108, 70),  dict(count=7, hmin=22, hmax=30, reed=True)),

    # ---- ironwood forest (fase C) ------------------------------------------
    "iron_fern":           ("fern",   (38, 76, 68),    dict(height=13, spread=6)),
    "needle_litter":       ("carpet", (76, 62, 44),    dict()),

    # ---- sump marsh (fase C) -----------------------------------------------
    "marsh_grass":         ("tuft",   (92, 116, 66),   dict(count=9, hmin=6, hmax=12)),
    "bog_fungus":          ("fungus", (172, 158, 82),  dict(caps=3)),
    "gas_bladder":         ("mat",    (112, 140, 92),  dict()),
    "tall_marsh_reed":     ("tall",   (104, 122, 72),  dict(count=7, hmin=22, hmax=31, reed=True)),

    # ---- ossuary tundra (fase C) -------------------------------------------
    "snow_scrub":          ("scrub",  (128, 124, 116), dict(count=8, hmin=3, hmax=7)),
    "tundra_moss":         ("carpet", (94, 108, 88),   dict()),

    # ---- salt waste (fase C) ----------------------------------------------
    "brine_grass":         ("tuft",   (178, 172, 142), dict(count=6, hmin=4, hmax=9)),
    "brine_thistle":       ("flower", (146, 148, 132), dict(stem=7, petal=(196, 186, 208), centre=(140, 128, 156))),
    "salt_flake":          ("pebbles", (228, 230, 226), dict()),

    # ---- agri --------------------------------------------------------------
    "field_grass":         ("tuft",   (104, 146, 62),  dict(count=9, hmin=6, hmax=12)),
    "pale_field_flower":   ("flower", (110, 140, 70),  dict(stem=7, petal=(224, 214, 138), centre=(198, 168, 84))),
    "agri_clover":         ("clover", (88, 140, 66),   dict()),
    "irrigation_reed":     ("reed",   (86, 134, 72),   dict(count=6, hmin=9, hmax=15)),

    # ---- liquens (face plana) ----------------------------------------------
    "slag_lichen":         ("lichen", (122, 122, 118), dict()),
    "rust_moss":           ("lichen", (152, 92, 46),   dict()),
    "sump_moss":           ("lichen", (104, 86, 56),   dict()),
    "gutter_lichen":       ("lichen", (114, 116, 104), dict()),
    "gob_moss":            ("lichen", (86, 134, 58),   dict()),
    "resin_moss":          ("lichen", (168, 120, 48),  dict()),
    "shelf_fungus":        ("lichen", (182, 162, 128), dict()),
    "mud_lichen":          ("lichen", (88, 72, 50),    dict()),
    "frost_lichen":        ("lichen", (192, 206, 214), dict()),

    # ---- tapetes -----------------------------------------------------------
    "fallen_leaves":       ("carpet", (118, 88, 50),   dict()),
    "ash_layer":           ("carpet", (134, 132, 128), dict()),

    # ---- detalhe de chao ---------------------------------------------------
    "scattered_twigs":     ("sticks",  (108, 82, 50),  dict(count=8)),
    "small_roots":         ("sticks",  (122, 96, 66),  dict(count=6, length=(4, 9))),
    "rubble_pebbles":      ("pebbles", (126, 124, 120), dict()),
    "bone_fragments":      ("bones",   (208, 204, 186), dict()),
}


# ------------------------------------------------------------------- desenhistas


def draw(name, form, base, kw):
    pal = G(base, warm=0.35 if form in ("tuft", "tall", "reed", "fern") else 0.15)

    if form in ("tuft", "scrub", "stubble", "thorn"):
        t = Tex(name)
        count = kw.get("count", 7)
        hmin, hmax = kw.get("hmin", 5), kw.get("hmax", 11)
        lean = 3.2 if kw.get("curly") else (1.2 if form == "stubble" else 2.2)
        t.tuft(pal, count=count, hmin=hmin, hmax=hmax,
               thick=1 if form == "stubble" else 2, lean=lean)
        if form == "scrub":
            # gravetos secos na base dao volume ao arbusto
            for x in t._spread(4, 2, 13):
                t.put(x, 15, pal[0])
                t.put(x + 1, 15, pal[1])
        if form == "thorn":
            for _ in range(7):
                x = t.rng.randint(1, 14)
                y = t.rng.randint(5, 13)
                if t.get(x, y)[3]:
                    t.put(x + t.rng.choice((-1, 1)), y, pal[4])
        return t

    if form == "fern":
        t = Tex(name)
        h = kw.get("height", 12)
        sp = kw.get("spread", 3)
        # tres frondes abrindo em leque a partir da mesma base
        t.frond(6, 15, h, pal, spread=sp, lean=-3.4)
        t.frond(9, 15, h - 1, pal, spread=sp, lean=4.2)
        t.frond(8, 15, max(5, h - 5), pal, spread=max(1, sp - 1), lean=0.8)
        return t

    if form == "fungus":
        t = Tex(name)
        stem_pal = G(kw["stem_col"]) if kw.get("stem_col") else G(
            (min(255, base[0] + 40), min(255, base[1] + 40), min(255, base[2] + 30)))
        caps = kw.get("caps", 3)
        spots = [(4, 15, 5, 6), (10, 15, 4, 5), (13, 15, 3, 3)][:caps]
        if kw.get("big"):
            spots = [(6, 15, 6, 8), (12, 15, 3, 4)][:caps]
        for cx, yb, sh, cw in spots:
            t.mushroom(cx, yb, sh, cw, pal, stem_pal)
        return t

    if form == "flower":
        t = Tex(name)
        petal = G(kw["petal"], spread=0.30)
        centre = (kw["centre"][0], kw["centre"][1], kw["centre"][2], 255) if kw.get("centre") else None
        t.flower(7, 15, kw.get("stem", 8), pal, petal, centre)
        # uma segunda flor mais baixa da moita, em vez de um talo solitario
        t.flower(12, 15, max(4, kw.get("stem", 8) - 3), pal, petal, centre, leaf=False)
        for x in (2, 4):
            t.blade(x, 15, t.rng.randint(3, 5), pal, lean=-0.8, thick=1)
        return t

    if form == "reed":
        t = Tex(name)
        for x in t._spread(kw.get("count", 6), 1, 14):
            h = t.rng.randint(kw.get("hmin", 8), kw.get("hmax", 14))
            t.blade(x, 15, h, pal, lean=t.rng.uniform(-0.8, 0.8), thick=1)
            # folha estreita saindo do talo
            ly = 15 - int(h * 0.55)
            side = t.rng.choice((-1, 1))
            t.put(x + side, ly, pal[2])
            t.put(x + side * 2, ly - 1, pal[1])
        return t

    if form == "clover":
        t = Tex(name)
        for cx, yb, h in ((4, 15, 6), (9, 15, 8), (13, 15, 5)):
            for i in range(h):
                t.put(cx, yb - i, pal[1] if i % 2 else pal[2])
            hy = yb - h
            # tres foliolos arredondados
            for dx, dy in ((-1, -1), (1, -1), (0, -2)):
                t.put(cx + dx, hy + dy, pal[3])
                t.put(cx + dx, hy + dy - 1, pal[4] if dy == -2 else pal[2])
                t.put(cx + dx + (1 if dx <= 0 else -1), hy + dy, pal[2])
        return t

    if form == "mat":
        t = Tex(name)
        base_y = 15
        rows = 3 if kw.get("low") else 4
        for y in range(base_y, base_y - rows, -1):
            for x in range(t.w):
                if t.rng.random() < 0.78:
                    tone = pal[t.rng.choice((1, 2, 2, 3))]
                    t.put(x, y, tone)
        for x in t._spread(6, 1, 14):
            t.blade(x, base_y - rows + 1, t.rng.randint(2, 4), pal,
                    lean=t.rng.uniform(-1, 1), thick=1)
        return t

    if form == "lichen":
        t = Tex(name)
        t.lichen(pal)
        return t

    if form == "carpet":
        t = Tex(name)
        t.carpet(pal)
        return t

    if form == "sticks":
        t = Tex(name)
        t.scatter_sticks(pal, count=kw.get("count", 7), length=kw.get("length", (3, 7)))
        return t

    if form == "pebbles":
        t = Tex(name)
        t.scatter_pebbles(pal)
        return t

    if form == "bones":
        t = Tex(name)
        t.scatter_bones(pal)
        return t

    raise ValueError("forma desconhecida: " + form)


def draw_tall(name, base, kw):
    """Planta de dois blocos: desenhada inteira em 16x32 e depois cortada.

    Desenhar inteiro e o unico jeito de garantir que a lamina que sai do bloco de
    baixo continue exatamente no mesmo pixel no bloco de cima.
    """
    pal = G(base, warm=0.35)
    t = Tex(name, w=SIZE, h=SIZE * 2)
    count = kw.get("count", 8)

    for x in t._spread(count, 1, 14):
        h = t.rng.randint(kw.get("hmin", 17), kw.get("hmax", 26))
        # pes em alturas diferentes: com todos os talos comecando na mesma linha o
        # bloco de baixo vira um retangulo escuro macico
        base_y = 31 - t.rng.randint(0, 2)
        if kw.get("reed"):
            t.blade(x, base_y, h, pal, lean=t.rng.uniform(-1.2, 1.2), thick=1)
            ly = base_y - int(h * 0.6)
            side = t.rng.choice((-1, 1))
            t.put(x + side, ly, pal[2])
            t.put(x + side * 2, ly - 1, pal[1])
        else:
            side = 1 if x < t.w / 2 else -1
            if t.rng.random() < 0.25:
                side = -side
            t.blade(x, base_y, h, pal,
                    lean=side * t.rng.uniform(1.8, 3.8),
                    curve=side * t.rng.uniform(0.6, 2.0),
                    thick=2 if t.rng.random() < 0.6 else 1)

    top = t.img.crop((0, 0, SIZE, SIZE))
    bottom = t.img.crop((0, SIZE, SIZE, SIZE * 2))
    return top, bottom


# ------------------------------------------------------------------------- main


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--sheet", action="store_true", help="grava uma folha de contato ampliada")
    ap.add_argument("--out", default=OUT)
    args = ap.parse_args()

    os.makedirs(args.out, exist_ok=True)
    written = []

    for name, (form, base, kw) in sorted(SPECIES.items()):
        if form == "tall":
            top, bottom = draw_tall(name, base, kw)
            top.save(os.path.join(args.out, name + "_top.png"))
            bottom.save(os.path.join(args.out, name + "_bottom.png"))
            written += [name + "_top", name + "_bottom"]
        else:
            tex = draw(name, form, base, kw)
            tex.save(os.path.join(args.out, name + ".png"))
            written.append(name)

    print("texturas geradas: %d" % len(written))

    if args.sheet:
        cols = 8
        rows = (len(written) + cols - 1) // cols
        scale = 6
        pad = 4
        cell = SIZE * scale + pad
        sheet = Image.new("RGBA", (cols * cell + pad, rows * cell + pad), (34, 34, 38, 255))
        for i, n in enumerate(sorted(written)):
            im = Image.open(os.path.join(args.out, n + ".png")).convert("RGBA")
            im = im.resize((SIZE * scale, SIZE * scale), Image.NEAREST)
            # xadrez atras, para o alfa ficar visivel
            bg = Image.new("RGBA", im.size, (54, 54, 60, 255))
            for by in range(0, im.size[1], scale * 2):
                for bx in range(0, im.size[0], scale * 2):
                    for yy in range(by, min(by + scale * 2, im.size[1])):
                        for xx in range(bx, min(bx + scale * 2, im.size[0])):
                            if ((xx // (scale * 2)) + (yy // (scale * 2))) % 2:
                                bg.putpixel((xx, yy), (44, 44, 50, 255))
            bg.alpha_composite(im)
            x = pad + (i % cols) * cell
            y = pad + (i // cols) * cell
            sheet.paste(bg, (x, y))
        out = os.path.join("tools", "flora_texture_sheet.png")
        sheet.save(out)
        print("folha de contato:", out)


if __name__ == "__main__":
    main()
