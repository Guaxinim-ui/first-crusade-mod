# Patch para HIVE_CITY.md — FASE 10

> Insira a seção **2k** logo após a seção **2j** (FASE 9 — Underhive), e a entrada de
> changelog no topo da seção **6. Changelog**.

---

## 2k. FASE 10 — SPIRE + GERAÇÃO AUTOMÁTICA DA CIDADE (ENTREGUE)

O coroamento do sistema: uma **dimensão própria** para a colmeia e um **gerador automático**
que monta a hive inteira empilhando os distritos das fases 5–9, sem colar distrito por distrito
à mão. Código isolado num subpacote novo `com.example.examplemod.hive.city`; ligação ao mod em
3 pontos de 1 linha (ver `docs/FASE10_INTEGRATION.md`).

### Dimensão `firstcrusade:hive_world`
Datapack puro (sem código de registro no 1.20.1): `dimension_type` com **min_y −64, height 576**
(Y −64..511, ambos múltiplos de 16 → cabe Underhive embaixo, ~4 níveis de 64 e o Spire no topo),
`has_ceiling=false`, escura (`ambient_light 0.1`, `fixed_time` meia-noite, efeitos do nether para
não mostrar céu azul pelas frestas). Gerador **flat** com **1 camada de bedrock** em y=−64 — o
terreno é vazio de propósito: **a cidade É o terreno**. Bioma `hive_floor` plano, sem spawns
naturais nem features (a cidade controla os spawns via marcadores).

### Layout determinístico (`HiveCityLayout`)
`seed (long) + raio → lista ORDENADA de distritos {id, origin, rotation}`, bottom-up:
1. **Underhive** (y=−64) sob a célula central;
2. **South Ash Gate** (muralha+portão) em toda a **anel externo** (rotação vira a muralha para
   fora — N/E/S/O = rot 0/1/2/3);
3. **Manufactorum → Hab Stacks → Administratum** empilhados (y +64/+128/+192) em cada célula
   **interior**, com rua N/S alinhada célula-a-célula (rotação 0 → malha viária contínua);
4. **Spire** no topo central (y +256), **opcional** (só entra se o distrito existir).

Grid quadrado de **pitch 192** (a maior aresta do footprint 192×128) → distritos girados nunca
se sobrepõem nem deixam vão. Anéis por **distância de Chebyshev** do centro. **Determinístico**:
mesmo seed+raio → plano idêntico (`RandomSource` semeado, reservado para variantes futuras;
nenhuma dependência de ordem de HashMap/relógio). O raio 2 (5×5) produz **45 distritos**
(16 muralha + 27 interior + 1 underhive + 1 spire).

### Colocação escalonada e persistida
- `HiveGenerationQueue` (**SavedData** no nível hive_world): fila de tarefas, 1 distrito por
  tarefa, com `seed/totalPlanned/placedSoFar` — **persiste em save/quit/reload**, cidade
  meio-construída continua.
- `HiveCityTicker` (`LevelTickEvent`, fase END): drena **N distritos/tick** (config
  `DISTRICTS_PER_TICK`, default 1) para **não travar o servidor** (armadilha da spec). Antes de
  cada colagem **force-load** dos chunks-alvo (`setChunkForced`) — senão `placeInWorld` em chunk
  não carregado falha silencioso — e libera depois. Feedback a cada 10%. Uma tarefa com erro é
  pulada (nunca trava a fila).
- `HiveCityPlacer`: seam único que chama o MESMO caminho de `/fchive district place` (processa
  marcadores e valida costuras por socket).

### Comandos novos (`/fchive city`, permissão 2)
- `generate [seed]` — planeja + enfileira; **imprime o plano inteiro no chat antes de colar 1
  bloco** (dry-run da spec §3.2).
- `status` — colocados/total/%/restantes/seed.
- `cancel` — limpa a fila (blocos já colados permanecem).
- `tp` — teleporta para o spawn da hive (centro, nível da rua).

### QA / validação
`tools/validate_fase10.py` roda **sem gradle/rede** (ambiente de dev do Claude, spec §4): valida
os 3 JSONs da dimensão (envelope, múltiplos de 16, gerador vazio, bioma sem spawn), re-implementa
a matemática do layout em Python e checa **determinismo, ausência de sobreposição horizontal por
nível Y, empilhamento em passos de 64, emissão bottom-up (underhive primeiro, spire por último) e
rotações de perímetro**, e **cruza as constantes Java↔Python** para não divergirem, incluindo a
guarda do bug da Fase 7 (id de distrito sem prefixo `hive/`). **51 checagens, todas passando.**
Dois bugs reais foram pegos pela validação e corrigidos antes da entrega: sobreposição de
footprint (grid retangular → quadrado) e Spire não sendo globalmente o último (passe final
separado). A API do SavedData foi corrigida para a forma de 3 argumentos do 47.x (a sobrecarga
`SavedData.Factory` é 1.20.2+ e não compilaria).

### Ligação marcadores → facções (spec §2.5)
Deixada como follow-up opcional: os marcadores capturados na colocação (`HiveMarkers`) já ficam
disponíveis para alimentar `WorldSettlementSeeder`/`OrkCorruptionManager` etc. — plugar quando o
dono quiser spawns/loot/patrulhas de verdade na cidade gerada.

**Ferramentas:** `tools/validate_fase10.py`. **Classes novas:** as 6 de `hive/city/`.
**Classes alteradas:** `HiveCommands` (+1 linha) e `HiveCityPlacer` (wiring de 1 método) — ver
guia de integração.

---

## Entrada de changelog (topo da seção 6)

- **2026-07-17 — FASE 10:** dimensão própria `firstcrusade:hive_world` (min_y −64, height 576,
  gerador flat vazio) + **geração automática determinística** da cidade: `HiveCityLayout` (seed →
  plano ordenado, anéis de Chebyshev, muralha no perímetro, pilha manufactorum/hab/admin no
  interior, spire opcional no topo central), `HiveGenerationQueue` (SavedData, persiste
  save/quit/reload) drenada por `HiveCityTicker` a N distritos/tick com force-load de chunks e
  feedback de progresso, comandos `/fchive city generate|status|cancel|tp`. Validação programática
  de 51 checagens (JSONs da dimensão + matemática do layout + sync Java↔Python) — 2 bugs de layout
  e 1 de API corrigidos antes da entrega. Ligação ao mod em 3 pontos de 1 linha, tudo isolado em
  `hive/city/`. Follow-up opcional: ligar marcadores aos managers de facção. **Sistema de Hive
  City completo (fases 1–10).**
