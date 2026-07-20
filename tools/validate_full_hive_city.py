#!/usr/bin/env python3
"""
FASE 16 — Validador da cidade completa (First Crusade Hive City).

Valida a consistência entre o plano de layout (tools/generated/hive_full_city_layout.json),
os distritos/módulos registrados em data/firstcrusade/ e os templates NBT. Encerra com código != 0
se encontrar falhas obrigatórias.

Checagens:
  1. Todo distrito usado no layout existe em data/firstcrusade/hive_districts/.
  2. Todo módulo referenciado por um distrito existe em data/firstcrusade/hive_modules/.
  3. Todo template NBT referenciado por um módulo existe em data/firstcrusade/structures/.
  4. Nenhum bounding box do layout excede o envelope vertical do mundo (-64..511).
  5. Nenhuma sobreposição horizontal proibida (mesmo nível Y, células diferentes se cruzando).
  6. Nenhum ID duplicado exatamente na mesma posição (x,y,z).
  7. Tamanhos de módulo declarados são potência de dois e coerentes (64³).

Somente stdlib. Rodar da raiz do projeto:
    python tools/validate_full_hive_city.py
"""
import json
import os
import sys

NS = "firstcrusade"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(ROOT, "src", "main", "resources", "data", NS)
DISTRICTS = os.path.join(DATA, "hive_districts")
MODULES = os.path.join(DATA, "hive_modules")
STRUCTURES = os.path.join(DATA, "structures")
LAYOUT = os.path.join(ROOT, "tools", "generated", "hive_full_city_layout.json")

MIN_Y, MAX_Y = -64, 511

errors, warnings = [], []
def err(m): errors.append(m)
def warn(m): warnings.append(m)


def rel_module_path(module_id):
    # firstcrusade:hab/hab_block_01 -> .../hive_modules/hab/hab_block_01.json
    path = module_id.split(":", 1)[1] if ":" in module_id else module_id
    return os.path.join(MODULES, *path.split("/")) + ".json"


def rel_template_path(template_id):
    # firstcrusade:hive/hab/hab_block_01 -> .../structures/hive/hab/hab_block_01.nbt
    path = template_id.split(":", 1)[1] if ":" in template_id else template_id
    return os.path.join(STRUCTURES, *path.split("/")) + ".nbt"


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def check_data_integrity():
    """Distritos -> módulos -> templates existem e são coerentes."""
    if not os.path.isdir(DISTRICTS):
        err(f"pasta de distritos ausente: {DISTRICTS}")
        return {}
    district_ids = {}
    for fn in sorted(os.listdir(DISTRICTS)):
        if not fn.endswith(".json"):
            continue
        did = f"{NS}:{fn[:-5]}"
        try:
            d = load(os.path.join(DISTRICTS, fn))
        except (OSError, json.JSONDecodeError) as e:
            err(f"[{did}] JSON inválido: {e}")
            continue
        district_ids[did] = d
        for entry in d.get("modules", []):
            mod = entry.get("module")
            if not mod:
                err(f"[{did}] entrada de módulo sem 'module'")
                continue
            mp = rel_module_path(mod)
            if not os.path.isfile(mp):
                err(f"[{did}] módulo inexistente: {mod} -> {os.path.relpath(mp, ROOT)}")
                continue
            m = load(mp)
            tmpl = m.get("template")
            if not tmpl:
                err(f"[{mod}] sem 'template'")
            else:
                tp = rel_template_path(tmpl)
                if not os.path.isfile(tp):
                    err(f"[{mod}] template NBT inexistente: {tmpl} -> {os.path.relpath(tp, ROOT)}")
            size = m.get("size")
            if not (isinstance(size, list) and len(size) == 3):
                err(f"[{mod}] 'size' malformado: {size}")
            else:
                for v in size:
                    if v <= 0 or (v & (v - 1)):
                        warn(f"[{mod}] dimensão não potência de dois: {size}")
                        break
    return district_ids


def check_layout(district_ids):
    """Layout consistente: IDs existem, dentro do envelope, sem sobreposição proibida."""
    if not os.path.isfile(LAYOUT):
        warn(f"layout não gerado ainda: {os.path.relpath(LAYOUT, ROOT)} "
             f"(rode HiveFullCityLayoutDump). Pulando checagens de layout.")
        return
    plan = load(LAYOUT)
    placements = plan.get("placements", [])
    if not placements:
        err("layout sem 'placements'")
        return

    seen_pos = {}
    boxes = []
    for p in placements:
        did = p["district"]
        if did not in district_ids:
            err(f"layout usa distrito não registrado: {did} (ordem {p.get('order')})")
        bb = p.get("bbox")
        if bb and len(bb) == 6:
            if bb[1] < MIN_Y or bb[4] > MAX_Y:
                err(f"[{did}] bbox fora do envelope Y {MIN_Y}..{MAX_Y}: {bb}")
            boxes.append((did, p.get("y"), bb, p.get("order")))
        key = (p.get("x"), p.get("y"), p.get("z"))
        if key in seen_pos:
            err(f"colocação duplicada exatamente em {key}: {did} e {seen_pos[key]}")
        else:
            seen_pos[key] = did

    # sobreposição horizontal no mesmo nível Y (células diferentes não podem se cruzar)
    for i in range(len(boxes)):
        for j in range(i + 1, len(boxes)):
            d1, y1, b1, o1 = boxes[i]
            d2, y2, b2, o2 = boxes[j]
            if y1 != y2:
                continue  # níveis diferentes = pilha vertical intencional
            # interseção horizontal estrita (com folga de 1 para bordas encostando)
            xo = b1[0] < b2[3] - 1 and b2[0] < b1[3] - 1
            zo = b1[2] < b2[5] - 1 and b2[2] < b1[5] - 1
            if xo and zo:
                err(f"sobreposição horizontal proibida no nível Y={y1}: "
                    f"{d1}(#{o1}) x {d2}(#{o2})")


def main():
    print("== Validação da cidade completa — Hive City ==\n")
    district_ids = check_data_integrity()
    print(f"Distritos registrados: {len(district_ids)}")
    check_layout(district_ids)

    if warnings:
        print(f"\n-- {len(warnings)} aviso(s) --")
        for w in warnings:
            print("  ! " + w)
    if errors:
        print(f"\n-- {len(errors)} ERRO(s) --")
        for e in errors:
            print("  x " + e)
        print("\nRESULTADO: FALHOU")
        return 1
    print("\nRESULTADO: OK — dados e layout da cidade completa consistentes.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
