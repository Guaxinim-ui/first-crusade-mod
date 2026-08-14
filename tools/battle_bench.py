"""Benchmark de batalha do First Crusade: mede MSPT do servidor com N tropas por lado.

Companheiro do world_probe.py (que mede worldgen). Este mede COMBATE.

    python tools/battle_bench.py <rotulo> [por_lado] [--wall] [--senha SENHA]

Exemplos:
    python tools/battle_bench.py antes 100
    python tools/battle_bench.py depois 100 --wall
    python tools/battle_bench.py grande 250

Pre-requisitos: servidor dedicado de pe com RCON ligado (ver a receita em
dedicated-server-world-check). O script constroi a arena, invoca as duas linhas, amostra
/forge tps enquanto elas lutam, limpa tudo e imprime um resumo.

DUAS ARMADILHAS QUE JA CUSTARAM MEDICOES ERRADAS AQUI:

1. **A primeira rodada depois de subir o servidor mente.** JIT ainda esta compilando os caminhos
   quentes e chunks ainda estao carregando; ela sai 40-60% mais cara que as seguintes. Rode pelo
   menos 3 vezes e DESCARTE a primeira. Uma unica amostra ja levou a anunciar um ganho de 48% que
   nao existia.

2. **Campo aberto nao mede aquisicao de alvo.** Sem geometria, o raycast de linha de visao morre
   em poucos blocos de ar e custa quase nada, entao o custo de varredura some no meio do custo de
   mover os mobs. Use --wall para por um muro com duas passagens entre as linhas: e ai que a
   diferenca entre uma varredura burra e uma varredura ordenada por distancia aparece.

O que este script NAO mede: FPS (e cliente), memoria, e o custo por secao do tick. Para atribuir
tempo a uma secao especifica e preciso spark.
"""
import re
import sys
import time

sys.path.insert(0, __file__.rsplit("battle_bench.py", 1)[0])
from world_probe import Rcon  # noqa: E402

IMPERIUM = "firstcrusade:guardsman"
ORK = "firstcrusade:ork_boy"

# Arena no ar, acima de qualquer relevo: sem morro, sem buraco, sem queda. A primeira versao
# deste teste invocava sobre terreno desconhecido e mediu morte por queda achando que era combate.
CX, CZ, PY = 1200, 1200, 200
GAP = 20
HALF_X, HALF_Z = 40, 20

SAMPLES = 10
SAMPLE_EVERY = 3.0

# O servidor formata numeros no locale da maquina: em pt-BR sai "5,926 ms", com virgula.
MSPT = re.compile(r"Mean tick time:\s*([0-9]+(?:[.,][0-9]+)?)\s*ms")


def count(rcon, entity_id):
    """Conta entidades pelo retorno de /tag: 'Added tag ... to N entities'."""
    out = rcon.cmd(f"execute positioned {CX} {PY} {CZ} run "
                   f"tag @e[type={entity_id},distance=..160] add fccount")
    if not out:
        return 0
    rcon.cmd(f"execute positioned {CX} {PY} {CZ} run "
             f"tag @e[type={entity_id},distance=..160] remove fccount")
    words = out.split()
    for i, word in enumerate(words):
        if word == "to" and i + 1 < len(words) and words[i + 1].isdigit():
            return int(words[i + 1])
    return 1 if "Added tag" in out else 0


def overworld_mspt(rcon):
    out = rcon.cmd("forge tps") or ""
    for line in out.splitlines():
        if "minecraft:overworld" in line:
            found = MSPT.search(line)
            if found:
                return float(found.group(1).replace(",", "."))
    found = MSPT.search(out)
    return float(found.group(1).replace(",", ".")) if found else None


def clear_field(rcon):
    for entity_id in (IMPERIUM, ORK):
        rcon.cmd(f"execute positioned {CX} {PY} {CZ} run "
                 f"kill @e[type={entity_id},distance=..160]")


def build_arena(rcon, wall):
    rcon.cmd(f"fill {CX - HALF_X} {PY - 1} {CZ - HALF_Z} "
             f"{CX + HALF_X} {PY - 1} {CZ + HALF_Z} minecraft:stone")
    rcon.cmd(f"fill {CX - HALF_X} {PY} {CZ - HALF_Z} "
             f"{CX + HALF_X} {PY + 4} {CZ + HALF_Z} minecraft:air")

    if wall:
        rcon.cmd(f"fill {CX} {PY} {CZ - HALF_Z} {CX} {PY + 3} {CZ + HALF_Z} minecraft:stone")
        # Duas passagens: sem elas viram dois grupos parados em vez de uma batalha.
        rcon.cmd(f"fill {CX} {PY} {CZ - 3} {CX} {PY + 3} {CZ - 2} minecraft:air")
        rcon.cmd(f"fill {CX} {PY} {CZ + 2} {CX} {PY + 3} {CZ + 3} minecraft:air")


def tear_down_arena(rcon):
    rcon.cmd(f"fill {CX - HALF_X} {PY - 1} {CZ - HALF_Z} "
             f"{CX + HALF_X} {PY + 4} {CZ + HALF_Z} minecraft:air")


def main(argv):
    args = [a for a in argv[1:] if not a.startswith("--")]
    wall = "--wall" in argv
    password = None
    if "--senha" in argv:
        password = argv[argv.index("--senha") + 1]

    label = args[0] if args else "sem-rotulo"
    per_side = int(args[1]) if len(args) > 1 else 100

    rcon = Rcon(password=password)
    rcon.cmd("gamerule doMobSpawning false")
    rcon.cmd(f"forceload add {CX - 100} {CZ - 100} {CX + 100} {CZ + 100}")
    time.sleep(2)
    build_arena(rcon, wall)
    clear_field(rcon)
    time.sleep(3)

    idle = overworld_mspt(rcon)
    arena = "muro" if wall else "campo aberto"
    print(f"[{label}] arena={arena}  ocioso={idle} ms/tick")

    for i in range(per_side):
        rcon.cmd(f"summon {IMPERIUM} {CX - GAP + (i % 12)} {PY} {CZ - 6 + (i // 12)}")
    for i in range(per_side):
        rcon.cmd(f"summon {ORK} {CX + GAP + (i % 12)} {PY} {CZ - 6 + (i // 12)}")

    print(f"[{label}] {per_side} por lado; amostrando "
          f"{int(SAMPLES * SAMPLE_EVERY)} s")

    samples = []
    for step in range(SAMPLES):
        time.sleep(SAMPLE_EVERY)
        mspt = overworld_mspt(rcon)
        if mspt is not None:
            samples.append(mspt)
        print(f"  t+{int((step + 1) * SAMPLE_EVERY):>3}s  mspt={mspt!s:<8} "
              f"vivos: Imperium={count(rcon, IMPERIUM):<4} Orks={count(rcon, ORK)}")

    clear_field(rcon)
    tear_down_arena(rcon)
    rcon.cmd(f"forceload remove {CX - 100} {CZ - 100} {CX + 100} {CZ + 100}")
    rcon.close()

    if not samples:
        print("Nenhuma amostra de MSPT: /forge tps respondeu algo inesperado.")
        return 1

    print(f"[{label}] RESUMO  arena={arena}  ocioso={idle} ms  "
          f"pico={max(samples)} ms  media={sum(samples) / len(samples):.2f} ms  n={len(samples)}")
    print("       (lembrete: descarte a primeira rodada apos subir o servidor)")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
