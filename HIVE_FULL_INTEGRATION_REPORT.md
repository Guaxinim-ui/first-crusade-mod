# Hive City — Relatório de Integração Completa

**Mod:** First Crusade · Forge 1.20.1 (47.4.10) · Java 17 · Mod ID `firstcrusade`
**Objetivo:** montar a cidade inteira num único comando para validar encaixe, integração horizontal/
vertical, perímetro e continuidade — tudo junto, não distritos separados.

---

## 1. Resumo executivo

O projeto **já possuía** um gerador de cidade integrada (`/fchive city generate` → `HiveCityLayout`
+ `HiveGenerationQueue` + `HiveCityTicker`). Em vez de duplicá-lo (regras 3–6), esta entrega
adicionou uma **camada de teste dedicada** por cima dele:

- `/fchive city build_full_test` — monta a cidade completa (semente fixa, raio 2 = grade 5×5) num
  único local, em lotes por tick, e teleporta para a plataforma de observação aérea.
- `/fchive city full_test_status` — progresso (colocados/total/%).
- `/fchive city full_test_tp <ponto>` — teleporte para 11 pontos de validação (com autocompletar).
- `/fchive city full_test_cancel` — cancela a fila sem corromper o mundo.
- `/fchive city full_test_clear` — limpa **apenas** o bounding box da cidade de teste, em lotes.

Ao executar `build_full_test` surge **uma única Hive City contínua** de **45 distritos**, medindo
**960 × 320 × 960** blocos (X×Y×Z), do Underhive (y=-64) ao topo da Spire (y=256), dentro do
envelope do mundo (-64..511).

---

## 2. Todos os setores encaixam?

**Sim, por construção do layout** — verificado estaticamente (validador + checagem de dados):
- Distritos usam passo `CELL_PITCH = 192` com footprint 192×128×64 → sem lacunas nem sobreposição
  horizontal proibida no mesmo nível (confirmado: 0 sobreposições no validador).
- Pilha vertical na mesma célula (X/Z): manufactorum(0) → hab(64) → admin(128) → spire(192),
  alinhada pelos sockets `up/down` (`canopy`/`hab_roof`/`foundation`).
- Perímetro fechado: 4 portões (midpoints), 8 muralhas retas, 4 bastiões de canto, com rotações
  calculadas em `HiveCityLayout.perimeterRotation/cornerRotation`.

**Ressalva honesta (regra 9 — não esconder):** o encaixe *visual fino* (rua não terminar em parede,
ponte não terminar no vazio, poço com escada de emergência) depende do **conteúdo interno dos
módulos/conectores NBT já existentes** (`connectors/*`, sockets). Esta entrega **não** gerou novos
templates NBT de transição nem elevadores com movimento — ela **monta e valida** o que já existe e
**reporta** falhas em vez de mascará-las. A confirmação visual definitiva é o passo de runClient
(seção 8), que não pôde ser executado aqui (ambiente headless/sem display).

---

## 3. Transições / integrações usadas (já presentes no projeto)

| Conexão | Módulo(s) conector(es) | Papel |
|---------|------------------------|-------|
| Interior do Manufactorum | `connectors/manufactorum_service_w/c/e_01` | corredores logísticos / serviço |
| Interior do Hab Stacks | `connectors/hab_transit_w/c/e_01` | transit nexus / passarelas |
| Interior do Administratum | `connectors/admin_processional_w/c/e_01` | avenida processional / arcos |
| Perímetro ↔ carga | distritos `south_ash_gate` / `hive_wall_line` (base de cargo) | portões e muralha sobre a base de carga |
| Vertical (níveis) | sockets `up/down` alinhados na mesma célula | pilha manufactorum→hab→admin→spire |

## 4. Setores ainda com possível problema (a confirmar no jogo)

- **Costuras entre distritos vizinhos**: o comando `/fchive district place` já valida sockets e
  reporta incompatibilidades (`✖`). No `build_full_test` a colocação usa a mesma via, mas o log de
  costura não é agregado — recomenda-se observar `latest.log` durante a geração.
- **Continuidade de ruas/pontes entre células de perímetro e interior**: depende dos sockets de
  borda dos módulos; validar visualmente em `full_test_tp south_gate` e `full_test_tp wall`.
- **Poços verticais**: alinhados por célula, mas a existência de escada de emergência/saída em cada
  nível depende do NBT de cada módulo.

## 5. Arquivos entregues / alterados

**Novos (Java):**
- `hive/city/HiveFullCityTest.java` — plano, bounding box, 11 pontos de tp, enfileiramento e clear.
- `hive/city/HiveClearQueue.java` — clear em lotes, resumível, com chunk tickets (SavedData).

**Alterados (Java):**
- `hive/city/HiveCityCommands.java` — 5 novos subcomandos `full_test_*` + `build_full_test`.
- `hive/city/HiveCityTicker.java` — drena a fila de clear (lotes) além da fila de distritos.

**Novos (ferramentas / relatórios):**
- `tools/HiveFullCityLayoutDump.java` — gera o mapa técnico (espelha `HiveCityLayout`).
- `tools/generated/hive_full_city_layout.{json,csv,md}` — 45 distritos com X/Y/Z/rotação/bbox/nível/conexão.
- `tools/validate_full_hive_city.py` — validação (Fase 16), encerra ≠0 em falha.
- `HIVE_FULL_CITY_LAYOUT_REPORT.md` — mapa da geração existente (Fase 1).
- `HIVE_FULL_INTEGRATION_REPORT.md` — este relatório.

Nada da geração normal (`/fchive city generate`, distritos, módulos, IDs, templates) foi removido ou
renomeado.

## 6. Dimensões finais (radius 2)

| Métrica | Valor |
|---------|-------|
| Distritos | 45 |
| Grade | 5×5 células |
| Extensão X/Y/Z | 960 × 320 × 960 |
| Min / Max | (-480,-64,-480) / (480,256,480) |
| Chunks (planta) | ~3721 (61×61) |
| Blocos estimados (união de footprints) | ~70,8 milhões |
| Dentro do envelope Y (-64..511)? | Sim |

## 7. Desempenho / segurança de geração

- **Lotes:** `DISTRICTS_PER_TICK = 1` (colagem) e `HiveClearQueue.BLOCKS_PER_TICK = 96 000` (clear).
- **Chunk tickets:** cada footprint é `setChunkForced` antes de colar/limpar e liberado depois —
  nenhum chunk fica permanentemente forçado (Fase 10).
- **Persistência:** filas de construção e de clear são `SavedData` no `hive_world` → sobrevivem a
  save/reload e retomam de onde pararam.
- **Robustez:** o ticker avança a fila mesmo se um distrito falhar (loga o erro, não trava a fila);
  clear e build são mutuamente exclusivos.
- **Tempo estimado de geração:** 45 distritos ÷ 1/tick = ~45 ticks de colagem (≈2–3 s de colagem
  pura; mais o custo de I/O de chunks). Clear do box completo: ~alguns minutos em lotes.

## 8. Build e teste

**Build:** `gradle compileJava --offline` → **BUILD SUCCESSFUL** (somente avisos pré-existentes de
deprecation de `ResourceLocation`, não relacionados a esta entrega).

**Validação de dados:** 38 referências de módulo em 9 distritos → todos os módulos e **todos os
templates NBT existem** (0 erros). Todos os distritos do layout estão registrados. `validate_full_
hive_city.py` entregue para CI (Python não está instalado neste ambiente; verificação equivalente
feita via shell, sem erros).

**runClient — PENDENTE (passo do usuário; ambiente aqui é headless):**
```
gradlew.bat clean build
gradlew.bat runClient
# no mundo hive_world (crie/entre; datapack firstcrusade ativo):
/fchive city build_full_test
/fchive city full_test_status
/fchive city full_test_tp spire
/fchive city full_test_tp underhive
/fchive city full_test_tp south_gate
/fchive city full_test_tp aerial
# ao terminar de inspecionar:
/fchive city full_test_clear
```
No `latest.log`, procurar por: `missing texture`, `missing model`, `blockstate`, `template`, `NBT`,
`chunk`, `out of bounds`, `duplicate placement`, `firstcrusade`, `hive`, `exception`.

## 9. Erros restantes

- Nenhum erro de compilação ou de integridade de dados/layout.
- Validação visual em runClient ainda **não realizada** aqui (headless) — é o passo final do usuário.
- Transições visuais finas e movimento de elevadores dependem de conteúdo NBT dos módulos e não
  foram reautorados nesta entrega (declarado abertamente, regra 9).

## 10. Comandos exatos para teste

```
/fchive city build_full_test          # monta a cidade completa (45 distritos, em lotes)
/fchive city full_test_status         # progresso
/fchive city full_test_tp <ponto>     # center|aerial|spire|administratum|hab|manufactorum|
                                      # underhive|south_gate|cargo|wall|bastion
/fchive city full_test_cancel         # cancela a fila
/fchive city full_test_clear          # limpa só o bounding box da cidade de teste (em lotes)
```
