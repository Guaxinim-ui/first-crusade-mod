# Hive City — Relatório de Layout (Fase 1: mapa da geração existente)

**Mod:** First Crusade · Forge 1.20.1 (47.4.10) · Mod ID `firstcrusade`
**Fonte:** leitura direta de `data/firstcrusade/hive_districts/`, `hive_modules/`, `structures/hive/`
e do código em `src/main/java/com/example/examplemod/hive/city/`.
Nenhum ID foi presumido — todos abaixo são confirmados nos arquivos do projeto.

---

## 0. Sistema de geração já existente (importante)

O projeto **já possui** um gerador de cidade completa e integrada, com colocação em lotes:

| Componente | Papel |
|-----------|-------|
| `city/HiveCityLayout.java` | Plano mestre determinístico: grade `(2r+1)²` de distritos, perímetro (portões/muralha/bastião), pilha vertical interior (manufactorum→hab→admin) e spire no centro |
| `city/HiveGenerationQueue.java` | Fila persistida (`SavedData`) de distritos a colocar; sobrevive a save/reload |
| `city/HiveCityTicker.java` | Drena a fila a `DISTRICTS_PER_TICK` (=1) por tick, com **chunk tickets** (`setChunkForced`) e liberação após colocar |
| `city/HiveCityPlacer.java` | Adaptador para `HiveCommands.placeDistrict(...)` (mesma via do `/fchive district place`) |
| `city/HiveCityCommands.java` | `/fchive city generate\|status\|cancel\|tp\|preview` |
| `HiveCommands.java` | `/fchive place\|module\|district\|save\|clear\|blocks test\|...` |

Ou seja: `/fchive city generate` **já monta a cidade inteira num único local, contínua**. O trabalho
desta entrega é adicionar um **modo de teste dedicado** (`build_full_test`) por cima dessa base —
com semente fixa, bounding box registrado, `clear` seguro e pontos de observação — sem duplicar nem
substituir a geração normal (regras 3, 4, 5, 6).

### Envelope vertical (de `HiveWorld.java`, casado com `dimension_type/hive_world.json`)
- `MIN_Y = -64`, `MAX_Y = 511` (altura 576)
- `UNDERHIVE_Y = -64` · `GROUND_Y = 0` (nível de rua) · `LEVEL_HEIGHT = 64` · `CELL = 64`
- Pilha por célula: manufactorum `y=0` → hab `y=64` → admin `y=128` → spire `y=192`

### Grade horizontal (de `HiveCityLayout.java`)
- Distrito = **192(x) × 128(z) × 64(y)** · `CELL_PITCH = 192` (passo entre células)
- `radius r` → grade `(2r+1)×(2r+1)` centrada na origem do mundo

---

## 1. Distritos registrados (9)

Todos os distritos têm footprint **192×128×64** (3 módulos de 64³ na fileira frontal + 3 na fileira
de conexão/serviço `z=64`), exceto `spire` e `visual_test` (módulo único 64³).

| District ID | Categoria/uso | Footprint | Módulos |
|-------------|---------------|-----------|---------|
| `firstcrusade:south_ash_gate` | Portão sul + base de carga | 192×128×64 | warehouse_01, cargo_yard_01, military_depot_01, gates/hive_wall_w_01, gates/**south_ash_gate_01**, gates/hive_wall_e_01 |
| `firstcrusade:hive_wall_line` | Muralha reta + carga | 192×128×64 | warehouse_01, cargo_yard_01, military_depot_01, gates/hive_wall_line_w/c/e_01 |
| `firstcrusade:hive_corner_bastion` | Bastião de canto (L) | 192×128×64 | gates/hive_corner_front_w/c/e_01, gates/hive_corner_rear_w/c/e_01 |
| `firstcrusade:manufactorum` | Industrial (nível 0) | 192×128×64 | industrial/foundry_01, assembly_hall_01, generator_hall_01, connectors/manufactorum_service_w/c/e_01 |
| `firstcrusade:hab_stacks` | Habitação (nível +64) | 192×128×64 | hab/hab_block_01, transit_nexus_01, market_chapel_01, connectors/hab_transit_w/c/e_01 |
| `firstcrusade:administratum` | Administrativo (nível +128) | 192×128×64 | admin/scriptorium_01, cathedral_nave_01, tribunal_01, connectors/admin_processional_w/c/e_01 |
| `firstcrusade:underhive` | Subterrâneo (y=-64) | 192×128×64 | underhive/sump_tunnels_01, collapsed_ruins_01, gang_territory_01, forgotten_catacombs_01, sump_market_01, reactor_abyss_01 |
| `firstcrusade:spire` | Coroa da spire (topo +192) | 64×64×64 | spire/spire_crown_01 |
| `firstcrusade:visual_test` | Setor de teste dos 48 blocos | 64×64×64 | test/hive_visual_test_01 |

## 2. Módulos registrados (40) — todos 64×64×64

- **admin/**: scriptorium_01, cathedral_nave_01, tribunal_01
- **hab/**: hab_block_01, transit_nexus_01, market_chapel_01
- **industrial/**: foundry_01, assembly_hall_01, generator_hall_01
- **cargo/**: warehouse_01, cargo_yard_01, military_depot_01
- **underhive/**: sump_tunnels_01, collapsed_ruins_01, gang_territory_01, forgotten_catacombs_01, sump_market_01, reactor_abyss_01
- **spire/**: spire_crown_01
- **gates/** (11): south_ash_gate_01, hive_wall_w_01, hive_wall_e_01, hive_wall_line_w/c/e_01, hive_corner_front_w/c/e_01, hive_corner_rear_w/c/e_01
- **connectors/** (9) — **transições horizontais já existentes**: admin_processional_w/c/e_01, hab_transit_w/c/e_01, manufactorum_service_w/c/e_01
- **street/**: industrial_street_01
- **test/**: hive_visual_test_01

Cada módulo declara `sockets` por face (ex.: `north/south=street`, `east/west=hab_corridor`,
`down=canopy`, `up=hab_roof`) usados por `HiveModule.fits(...)` para validar costuras entre módulos
adjacentes. Os **templates NBT** correspondentes existem 1:1 em `structures/hive/<cat>/<nome>.nbt`
(40 arquivos, verificados).

## 3. Conexões (transições) por nível

- **Horizontal (dentro do distrito):** a 2ª fileira (`z=64`) de cada distrito de nível é um módulo
  `connectors/*` = rua/processional/transit/serviço, já ligando os 3 módulos frontais.
  - Manufactorum → `manufactorum_service_*` (corredores logísticos/serviço)
  - Hab → `hab_transit_*` (transit nexus / passarelas)
  - Administratum → `admin_processional_*` (avenida processional / arcos)
- **Vertical (entre níveis):** os distritos declaram “aligned vertical transit” (ver descrições de
  `administratum`/`hab_stacks`) e os sockets `up/down` (`canopy`/`hab_roof`/`foundation`) alinham os
  poços entre manufactorum(0)→hab(64)→admin(128)→spire(192), todos na mesma célula (mesmo X/Z).
- **Perímetro:** `south_ash_gate` nos midpoints das 4 bordas, `hive_wall_line` nas bordas retas,
  `hive_corner_bastion` nos 4 cantos (rotações calculadas em `HiveCityLayout.perimeterRotation/cornerRotation`).

## 4. Ordem de geração (bottom-up, de `HiveCityLayout.plan()`)

1. Underhive (y=-64, célula central) → 2. Perímetro (portões/muralha/bastião, y=0) →
3. Pilha interior por célula: manufactorum(0) → hab(64) → admin(128) → 4. Spire (192, global por último).

A fila coloca 1 distrito/tick; o ticker força os chunks do footprint (192×192 conservador) antes de
colar e libera depois.

## 5. Dimensões da cidade de teste (radius 2 → grade 5×5 = 25 células)

- Distritos planejados: **25 células** × (perímetro OU pilha 3-níveis) + underhive + spire.
  - Perímetro (ring==2): 16 células → 4 gates + 8 wall_line + 4 bastiões.
  - Interior (ring<2): 9 células × 3 (man/hab/admin) = 27 + 1 underhive + 1 spire.
  - **Total ≈ 45 colocações de distrito** (ver `tools/generated/hive_full_city_layout.*` para a lista exata com X/Y/Z/rotação).
- Extensão horizontal: `5 × 192 = 960` blocos por lado (≈ 60×60 chunks).
- Extensão vertical usada: `y=-64` (underhive) a `y≈256` (topo da spire).

> Os valores exatos de posição/rotação/bbox de cada distrito são emitidos por
> `tools/generated/hive_full_city_layout.{json,csv,md}` (Fase 14), derivados da mesma matemática de
> `HiveCityLayout` para `radius=2`.
