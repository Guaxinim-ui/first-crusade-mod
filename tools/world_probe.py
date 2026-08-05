#!/usr/bin/env python3
"""
Le um mundo salvo do mod: cliente RCON, parser Anvil/NBT e as medicoes que ja provaram valer.

Por que isto vive no repositorio
--------------------------------
Porque a regra numero um deste projeto e nao dizer que algo funciona sem ter medido, e ate agora
o instrumental dessa medicao era reescrito do zero toda vez que fazia falta — RCON, header de
regiao, tags NBT. Reescrever um parser NBT e barato; reescrever os *erros* dele nao e. Este
arquivo ja carrega os dois que custaram medicoes inteiras:

  * a ordem de avaliacao no compound NBT (ver `Nbt.payload`, tag 10);
  * o filtro `Status == "minecraft:full"`, sem o qual chunks de borda mentem na contagem.

O que este script possui: nada dentro de src/. So le arquivos do mundo e fala com o servidor.

Uso (com um servidor dedicado rodando e RCON ligado):
    python tools/world_probe.py biomes  <mundo> <dimensao>
    python tools/world_probe.py fauna   <mundo> <dimensao> [id-da-entidade]
    python tools/world_probe.py sample  <mundo> <dimensao> [raio] [pontos-por-lado]
    python tools/world_probe.py soak    <mundo> <dimensao> <id> <segundos>

  <mundo>     nome da pasta em run/ (o level-name do server.properties)
  <dimensao>  "overworld" ou "firstcrusade:macragge" etc.

Receita do servidor de teste (e o que restaurar depois)
-------------------------------------------------------
Em run/server.properties: enable-rcon=true, rcon.password=..., online-mode=false,
level-name=<mundo de teste>, max-tick-time=-1. **Restaurar tudo depois** — o dono joga por
runClient, e um server.properties alterado atrapalha. Nunca apagar run/world.

Antes de subir: matar servidor orfao (java.exe com 'forgeserveruserdev' na linha de comando),
que segura session.lock e faz o mundo novo falhar sem explicar por que.
"""

import collections
import glob
import gzip
import math
import os
import socket
import struct
import sys
import time
import zlib

RUN = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "run")

# Um forceload aceita ate 256 chunks (16x16) por comando, mas o lote usado aqui e 8x8.
#
# Nao e timidez: um lote de 256 leva minutos para o primeiro chunk ficar `full`, e durante
# esse tempo qualquer criterio de parada baseado em "o numero parou de subir" desiste cedo
# demais e devolve uma amostra vazia. Com 64 chunks o lote inteiro completa em dezenas de
# segundos e o progresso e visivel entre uma leitura e a seguinte.
BLOCK_CHUNKS = 8


# =============================================================================== RCON


class Rcon:
    """Cliente RCON minimo: pacotes tipo 3 (login) e 2 (comando), little-endian."""

    def __init__(self, host="127.0.0.1", port=25575, password=None):
        self.sock = socket.create_connection((host, port), timeout=600)
        self.rid = 0
        if self._send(3, password or os.environ.get("RCON_PASSWORD", "")) is None:
            raise SystemExit("RCON: senha recusada")

    def _send(self, kind, body):
        self.rid += 1
        payload = struct.pack("<ii", self.rid, kind) + body.encode("utf8") + b"\x00\x00"
        self.sock.sendall(struct.pack("<i", len(payload)) + payload)

        size = struct.unpack("<i", self._read(4))[0]
        data = self._read(size)
        rid, _kind = struct.unpack("<ii", data[:8])
        return None if rid == -1 else data[8:-2].decode("utf8", "replace")

    def _read(self, n):
        out = b""
        while len(out) < n:
            chunk = self.sock.recv(n - len(out))
            if not chunk:
                raise SystemExit("RCON: conexao fechada")
            out += chunk
        return out

    def cmd(self, command):
        return self._send(2, command)

    def close(self):
        self.sock.close()


# ================================================================================ NBT


class Nbt:
    def __init__(self, data):
        self.d = data
        self.i = 0

    def u1(self):
        v = self.d[self.i]
        self.i += 1
        return v

    def raw(self, fmt, size):
        v = struct.unpack_from(fmt, self.d, self.i)[0]
        self.i += size
        return v

    def string(self):
        n = self.raw(">H", 2)
        s = self.d[self.i:self.i + n].decode("utf8", "replace")
        self.i += n
        return s

    def payload(self, t):
        if t == 0:
            return None
        if t == 1:
            return self.raw(">b", 1)
        if t == 2:
            return self.raw(">h", 2)
        if t == 3:
            return self.raw(">i", 4)
        if t == 4:
            return self.raw(">q", 8)
        if t == 5:
            return self.raw(">f", 4)
        if t == 6:
            return self.raw(">d", 8)
        if t == 7:
            n = self.raw(">i", 4)
            v = self.d[self.i:self.i + n]
            self.i += n
            return v
        if t == 8:
            return self.string()
        if t == 9:
            item = self.u1()
            n = self.raw(">i", 4)
            return [self.payload(item) for _ in range(n)]
        if t == 10:
            out = {}
            while True:
                tag = self.u1()
                if tag == 0:
                    return out
                # O nome tem de ser lido numa linha propria. Em
                #     out[self.string()] = self.payload(tag)
                # o Python avalia o lado direito ANTES do alvo, entao o payload sai lido na
                # posicao do nome e o arquivo inteiro decodifica errado — silenciosamente.
                name = self.string()
                out[name] = self.payload(tag)
        if t == 11:
            n = self.raw(">i", 4)
            v = list(struct.unpack_from(">%di" % n, self.d, self.i))
            self.i += 4 * n
            return v
        if t == 12:
            n = self.raw(">i", 4)
            v = list(struct.unpack_from(">%dq" % n, self.d, self.i))
            self.i += 8 * n
            return v
        raise ValueError("tag NBT desconhecida: %d" % t)

    def root(self):
        t = self.u1()
        self.string()
        return self.payload(t)


def read_region(path):
    """Gera (chunk_x, chunk_z, raiz) de um .mca. Serve para region/ e para entities/."""
    with open(path, "rb") as f:
        header = f.read(4096)
        if len(header) < 4096:
            return

        rx, rz = [int(p) for p in os.path.basename(path).split(".")[1:3]]

        for index in range(1024):
            entry = struct.unpack_from(">I", header, index * 4)[0]
            offset, sectors = entry >> 8, entry & 0xFF
            if not offset or not sectors:
                continue

            f.seek(offset * 4096)
            length = struct.unpack(">i", f.read(4))[0]
            compression = f.read(1)[0]
            blob = f.read(length - 1)

            if compression == 1:
                blob = gzip.decompress(blob)
            elif compression == 2:
                blob = zlib.decompress(blob)

            try:
                root = Nbt(blob).root()
            except Exception:
                continue

            # Ler enquanto o servidor salva devolve, de vez em quando, um bloco parcial que
            # decodifica como qualquer coisa — ja saiu um float onde devia estar o chunk. Um
            # chunk que nao e composto nao e um chunk, e derrubar a medicao inteira por causa
            # de um setor meio escrito seria pior que ignora-lo.
            if isinstance(root, dict):
                yield rx * 32 + index % 32, rz * 32 + index // 32, root


# ========================================================================== o mundo


def dimension_dir(level, dimension):
    """A pasta de uma dimensao dentro do save."""
    base = os.path.join(RUN, level)
    if dimension in ("overworld", "minecraft:overworld"):
        return base

    namespace, path = dimension.split(":", 1) if ":" in dimension else ("minecraft", dimension)
    return os.path.join(base, "dimensions", namespace, path)


def chunk_biomes(directory):
    """chunk (x, z) -> bioma dominante, SO para chunks completos.

    O filtro de Status e obrigatorio: um chunk parado em `structure_starts` ou `biomes` ja tem
    dados suficientes para o parser e ainda nao tem o mundo que se quer medir. Sem ele, a borda
    de cada amostra entra na conta e a medicao mente para baixo.
    """
    out = {}
    for path in glob.glob(os.path.join(directory, "region", "*.mca")):
        for cx, cz, root in read_region(path):
            if root.get("Status") != "minecraft:full":
                continue

            counts = collections.Counter()
            for section in root.get("sections") or []:
                for biome in (section.get("biomes") or {}).get("palette") or []:
                    counts[biome] += 1

            if counts:
                out[(cx, cz)] = counts.most_common(1)[0][0]

    return out


def entities(directory, wanted=None):
    """Lista de (id, x, y, z) das entidades salvas em entities/*.mca."""
    out = []
    for path in glob.glob(os.path.join(directory, "entities", "*.mca")):
        for _cx, _cz, root in read_region(path):
            for entity in root.get("Entities") or []:
                eid = entity.get("id")
                if wanted and eid != wanted:
                    continue
                pos = entity.get("Pos") or [0, 0, 0]
                out.append((eid, pos[0], pos[1], pos[2]))
    return out


# ======================================================================== medicoes


def cmd_biomes(level, dimension):
    biomes = chunk_biomes(dimension_dir(level, dimension))
    if not biomes:
        print("nenhum chunk completo — o servidor salvou?")
        return

    print("%s: %d chunks completos" % (dimension, len(biomes)))
    for biome, count in collections.Counter(biomes.values()).most_common():
        print("  %-34s %6d (%5.1f%%)" % (biome, count, 100.0 * count / len(biomes)))


def cmd_fauna(level, dimension, wanted=None):
    directory = dimension_dir(level, dimension)
    biomes = chunk_biomes(directory)
    per_biome = collections.Counter(biomes.values())
    found = entities(directory, wanted)

    print("%s: %d chunks completos, %d entidades salvas" % (dimension, len(biomes), len(found)))
    for kind, count in collections.Counter(e[0] for e in found).most_common(12):
        print("  %-40s %d" % (kind, count))

    if not wanted or not found:
        return

    where = collections.Counter()
    for _id, x, _y, z in found:
        where[biomes.get((math.floor(x / 16), math.floor(z / 16)), "(fora da amostra)")] += 1

    print("\n  por bioma                       n   chunks  por chunk")
    for biome, count in where.most_common():
        chunks = per_biome.get(biome, 0)
        density = ("%9.3f" % (count / chunks)) if chunks else "        -"
        print("    %-28s %5d %8d %s" % (biome, count, chunks, density))

    # Aglomeracao na mesma caixa que FCAnimalEntity.tooCrowded conta (+-48 blocos).
    clusters = sorted(
        sum(1 for _i, x2, _y2, z2 in found if abs(x2 - x) <= 48 and abs(z2 - z) <= 48)
        for _id, x, _y, z in found)

    def pct(p):
        return clusters[min(len(clusters) - 1, int(len(clusters) * p))]

    print("\n  aglomerado na caixa de +-48 blocos: mediana %d | p90 %d | maximo %d"
          % (pct(0.5), pct(0.9), clusters[-1]))


def gametime(rcon):
    """O tick atual do servidor, via `time query gametime`."""
    answer = rcon.cmd("time query gametime") or ""
    digits = "".join(c for c in answer if c.isdigit())
    return int(digits) if digits else 0


def wait_ticks(rcon, ticks, timeout=300):
    """Espera o servidor AVANCAR n ticks — nao n segundos.

    Esta distincao e a lição mais cara desta ferramenta. `time.sleep` mede o relogio de quem
    chama; a geracao de chunk consome tempo de servidor. Num servidor saturado (o de medicao
    chegou a 50 minutos de atraso de tick), vinte segundos reais podem valer quase nenhum tick, e
    o `forceload remove` seguinte descarta chunks que mal comecaram. O sintoma no disco sao
    chunks parados em `structure_starts` / `biomes` / `carvers`, e uma contagem de `full` que
    nunca sai dos ~400 chunks da area de spawn.
    """
    start = gametime(rcon)
    deadline = time.time() + timeout

    while time.time() < deadline:
        if gametime(rcon) - start >= ticks:
            return True
        time.sleep(1)

    return False


def wait_generated(rcon, level, dimension, target, timeout=900):
    """Espera ate haver `target` chunks completos no disco — ou ate parar de crescer.

    Esperar por relogio esta errado (mede o relogio de quem chama, nao o servidor) e esperar por
    ticks tambem (num servidor saudavel, 60 ticks sao tres segundos, que nao geram 256 chunks).
    A unica condicao que responde a pergunta certa e a propria pergunta: *ja existe o material?*
    Entao o laco salva, conta os chunks `full` no disco, e so devolve quando o numero chega ao
    alvo ou para de subir em duas leituras seguidas.
    """
    deadline = time.time() + timeout
    previous = -1
    stable = 0

    while time.time() < deadline:
        rcon.cmd("save-all flush")
        time.sleep(2)
        count = len(chunk_biomes(dimension_dir(level, dimension)))

        if count >= target:
            return count, True

        # Cinco leituras iguais (~50 s), nao duas: o contador fica parado enquanto o lote
        # gera, e desistir nesse silencio foi o que produziu amostras de zero chunks.
        stable = stable + 1 if count == previous else 0
        if stable >= 5:
            return count, False

        previous = count
        time.sleep(8)

    return previous, False


def cmd_sample(level, dimension, span=4000, grid=5, password=None):
    """Gera o mundo em lotes: forceload -> esperar o disco -> forceload remove.

    Em lotes porque ~1.800 chunks de uma vez estoura a heap (o Gradle roda com -Xmx3G).
    """
    step = (2 * span) // max(1, grid - 1)
    centres = [(-span + i * step, -span + j * step) for i in range(grid) for j in range(grid)]

    rcon = Rcon(password=password)
    have = len(chunk_biomes(dimension_dir(level, dimension)))

    for n, (cx, cz) in enumerate(centres, 1):
        rcon.cmd("execute in %s run forceload add %d %d %d %d"
                 % (dimension, cx, cz, cx + BLOCK_CHUNKS * 16 - 1, cz + BLOCK_CHUNKS * 16 - 1))

        # Alvo conservador: metade do lote. Um forceload de 16x16 rende os 256 chunks pedidos
        # mais uma borda de vizinhos, mas exigir o numero cheio faria um ponto sobre agua ou
        # borda de mundo travar o lote inteiro ate o prazo.
        have, complete = wait_generated(rcon, level, dimension,
                                        have + BLOCK_CHUNKS * BLOCK_CHUNKS)
        rcon.cmd("execute in %s run forceload remove all" % dimension)
        print("  %d/%d em (%d, %d): %d chunks%s"
              % (n, len(centres), cx, cz, have, "" if complete else "  (parou de crescer)"),
              flush=True)

    rcon.cmd("save-all flush")
    rcon.close()
    time.sleep(3)
    cmd_biomes(level, dimension)


def cmd_soak(level, dimension, wanted, seconds, password=None):
    """Mantem carregada a area que ja tem mais entidades e ve se a populacao cresce sozinha.

    Testar crescimento numa area vazia nao testa nada. O lugar que importa e onde a geracao ja
    deixou populacao — e onde o teto do spawn continuo tem de segurar.
    """
    directory = dimension_dir(level, dimension)
    found = entities(directory, wanted)

    boxes = collections.Counter()
    for _id, x, _y, z in found:
        boxes[(math.floor(x / 256) * 256, math.floor(z / 256) * 256)] += 1

    (bx, bz), seeded = boxes.most_common(1)[0] if boxes else ((0, 0), 0)
    print("area: (%d, %d)+256, %d %s da geracao" % (bx, bz, seeded, wanted), flush=True)

    def in_box():
        return sum(1 for _i, x, _y, z in entities(directory, wanted)
                   if bx <= x <= bx + 255 and bz <= z <= bz + 255)

    rcon = Rcon(password=password)
    rcon.cmd("execute in %s run forceload add %d %d %d %d" % (dimension, bx, bz, bx + 255, bz + 255))
    rcon.cmd("save-all flush")
    time.sleep(3)

    before, world_before = in_box(), len(entities(directory, wanted))
    print("antes: %d na area, %d no mundo" % (before, world_before), flush=True)

    time.sleep(seconds)

    rcon.cmd("save-all flush")
    time.sleep(5)
    after, world_after = in_box(), len(entities(directory, wanted))
    print("depois de %ds: %d na area (%+d), %d no mundo (%+d)"
          % (seconds, after, after - before, world_after, world_after - world_before))

    rcon.cmd("execute in %s run forceload remove all" % dimension)
    rcon.close()


def main():
    if len(sys.argv) < 4:
        raise SystemExit(__doc__.strip())

    mode, level, dimension = sys.argv[1], sys.argv[2], sys.argv[3]
    rest = sys.argv[4:]

    if mode == "biomes":
        cmd_biomes(level, dimension)
    elif mode == "fauna":
        cmd_fauna(level, dimension, rest[0] if rest else None)
    elif mode == "sample":
        cmd_sample(level, dimension,
                   int(rest[0]) if rest else 4000,
                   int(rest[1]) if len(rest) > 1 else 5)
    elif mode == "soak":
        cmd_soak(level, dimension, rest[0], int(rest[1]))
    else:
        raise SystemExit(__doc__.strip())


if __name__ == "__main__":
    main()
