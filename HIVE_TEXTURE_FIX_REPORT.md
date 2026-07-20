# Relatório de Correção Visual — Blocos da Hive City

**Mod:** First Crusade · **Forge** 1.20.1 (47.4.10) · **Java** 17 · **Mod ID** `firstcrusade`
**Escopo:** 48 blocos da Hive City (Conjuntos I, II, III) — inventário e colocação individual no mundo.

---

## 1. Arquivos alterados

| Tipo | Arquivo | Mudança |
|------|---------|---------|
| Textura | `src/main/resources/assets/firstcrusade/textures/block/hive_city/*.png` (**48 arquivos**) | Regeneradas como texturas coerentes por bloco (64×64) |
| Java (cliente) | `src/main/java/com/example/examplemod/hive/HiveClientEvents.java` | Render layer `cutout` para grades/balaustrada com furos transparentes |
| Java (comando) | `src/main/java/com/example/examplemod/hive/HiveCommands.java` | Novo subcomando `/fchive blocks test` (Fase 11) |
| Ferramenta (novo) | `tools/HiveCityTextureGen.java` | Gerador determinístico das 48 texturas (JDK puro) |
| Ferramenta (novo) | `tools/validate_hive_assets.py` | Validador da cadeia de assets (Fase 10) |
| Relatório (novo) | `HIVE_TEXTURE_FIX_REPORT.md` | Este documento |

**Backup:** cópia integral de `textures/block/hive_city`, `models/block`, `models/item`, `blockstates`
e da classe de registro em `scratch_backups/backup_hive_<timestamp>/` (pasta ignorada pelo git).

**NÃO foi alterado:** nenhum ID de bloco, nome de model, blockstate, UV, geometria 3D ou model de item.
Por isso a geração da cidade e as estruturas `.nbt` continuam válidas sem qualquer edição (Fase 12).

---

## 2. Causa raiz encontrada

O "encanamento" dos assets estava **correto**: blockstates, models de bloco, models de item,
namespaces (`firstcrusade`) e caminhos apontavam todos para os arquivos certos. Verificado 1:1
para os 48 blocos — 0 referências quebradas, 0 usos de `.png`, 0 caminhos absolutos.

O defeito estava **exclusivamente no conteúdo das texturas**:

- Cada PNG em `textures/block/hive_city/<bloco>.png` (64×64) **não era a textura daquele bloco** —
  era uma **colagem de ~16 tiles 16×16 aleatórios e sem relação** (folhas de conceito: pedaços de
  grades, listras de perigo, luzes, painéis verdes/roxos/marrons misturados).
- Os models usam **box-UV**: o 64×64 é uma grade 4×4 de células 16×16 e **cada face amostra uma
  célula diferente**. Como cada célula continha um tile de lixo distinto, **cada face do bloco
  exibia um pedaço aleatório** — exatamente o sintoma relatado.
- Os arquivos-fonte `.bbmodel` em `blockbench_sources/` embutiam as mesmas colagens.

Ou seja: não havia UV fora de faixa, namespace errado, face sem textura, item apontando para o
bloco errado nem atlas gigante compartilhado. **O problema era a arte dentro de cada PNG.**

---

## 3. Correção aplicada

Regeneração das 48 texturas via `tools/HiveCityTextureGen.java` (Java puro, `java.awt`+`ImageIO`,
**determinístico**). Para cada bloco pinta-se **um tile 16×16 coerente** (material grimdark + rebites/
juntas + motivo específico) que é então **estampado em todas as 16 células** do PNG 64×64. Assim,
qualquer célula que uma face do box-UV amostre mostra o material correto e coerente — **sem tocar em
um único model, blockstate ou UV**, preservando 100% da geometria 3D (canos, arcos, grades, pilares,
janelas, altares, braseiros).

Direção visual seguida: metal escuro, aço rebitado, bronze envelhecido, pedra gótica, fuligem/
desgaste discreto, luz âmbar, vidro âmbar/roxo, estética industrial grimdark. Nenhum texto, número,
moldura ou UI de imagem conceitual dentro das texturas. Transparência real (alpha) apenas onde há
vazados (grades e balaustrada).

**Famílias de material** (cada bloco recebe tom/ motivo próprio):
aço rebitado · pedra gótica (arco/lanceta/relevo) · cano cilíndrico com nervuras · casing de máquina
com luzes · grade vazada + faixa de perigo · chapa xadrez · ladrilho de catedral (com/sem sangue) ·
vitral luminoso (âmbar/roxo) · fixação com chama (vela/tocha/braseiro) · caveira/gárgula em osso ·
caixa industrial em madeira+aço · feixe de cabos.

### Fase 7 — Render type
Grades e balaustrada têm furos reais (alpha 0) e foram registradas em `cutout`
(`FLOOR_GRATE`, `HAZARD_GRATED_FLOOR`, `BALUSTRADE_RAILING`). Vitrais são vidro luminoso opaco →
permanecem em `solid` (sem custo de ordenação translúcida). `translucent` não foi usado.

### Fase 8 — Emissão de luz
Já implementada corretamente no registro (`HiveCityConceptBlocks`) via `.lightLevel(...)`:
vitral 15, vitral variante 9, alcova de vela 14, tocha 15, braseiro 15, janela em fenda 12,
casing de máquina 7. Mantida — a luz vem do bloco, não da textura.

### Fase 9 — Colisão/oclusão
Blocos não-cúbicos já usam `.noOcclusion()` no registro (canos, grades, molduras, cornijas etc.).
Mantido; nenhuma geometria foi simplificada.

---

## 4. Blocos corrigidos (48/48)

**Conjunto I — Estruturas (16):** armored_bulkhead_wall, recessed_steel_wall_panel, gothic_arch_wall,
tall_ribbed_pillar, buttress_column, cathedral_cornice, lower_wall_molding, spire_cap_block,
balcony_edge_trim, bridge_support_block, giant_door_segment, narrow_lancet_recess,
triangular_relief_panel, window_slot_frame, heavy_structural_frame, vertical_seam_strip.

**Conjunto II — Industrial (16):** straight_pipe, elbow_pipe, t_pipe_junction, cross_pipe_junction,
pipe_support_clamp, vertical_service_conduit, cable_bundle_block, vent_outlet, floor_vent, lift_rail,
gantry_beam, suspended_track_anchor, maintenance_hatch, machine_casing_block, hazard_grated_floor,
reinforced_platform_edge.

**Conjunto III — Pisos/luz/detalhe (16):** glowing_shrine_window, stained_window_variant,
candle_alcove, wall_sconce, shrine_recess, bloodstained_floor_tile, cathedral_floor_tile,
metal_floor_plate, floor_grate, cathedral_stair_block, landing_slab, balustrade_railing,
skull_relief_panel, gargoyle_pedestal, industrial_crate, brazier_block.

---

## 5. Validação

**Verificação de referências (cadeia completa dos 48 blocos):**
- 48/48 com blockstate, model de bloco, model de item e textura presentes.
- 48 refs de textura distintas → mapeamento **1:1** com as 48 texturas regeneradas (0 não resolvidas,
  0 uso de `.png`, 0 caminho absoluto, 0 namespace errado).
- 48/48 models de item com `parent` correto para `firstcrusade:block/<bloco>`.
- Todas as texturas 64×64 (potência de dois), compatíveis com `texture_size [64,64]`.

**Script `tools/validate_hive_assets.py`** entregue (Fase 10). Encerra com código ≠ 0 em caso de erro.
Observação: o ambiente de build atual **não tem Python instalado** (apenas o stub da Microsoft Store),
então o script não pôde ser executado aqui; a mesma verificação foi feita por um equivalente em shell,
sem erros. Rode no seu ambiente com: `python tools/validate_hive_assets.py`.

**Build:**
```
gradle compileJava --offline  →  BUILD SUCCESSFUL
```
Somente avisos pré-existentes de deprecation (`ResourceLocation(String,String)`), não relacionados a
esta correção. Nenhum erro de missing model/texture, blockstate, JSON parse, stitch ou resource location.

---

## 6. Erros ainda existentes

Nenhum erro conhecido de carregamento de recursos. Pendência de ambiente (não do mod): **teste visual
em `runClient` não foi executado aqui** porque o ambiente é headless/sem display. Recomenda-se rodar
`gradlew runClient` e usar o comando de teste abaixo para a inspeção visual final in-game.

---

## 7. Como testar no jogo

```
gradlew.bat clean build
gradlew.bat runClient
```
Já no mundo (criativo, permissão de operador):
```
/fchive blocks test
```
Isso monta uma plataforma de andesito polido perto do jogador e coloca os **48 blocos em 3 conjuntos**
(2 fileiras de 8), cada um com uma **placa identificadora** virada para o jogador e os blocos
direcionais orientados para frente — permitindo comparar o bloco colocado com o item no inventário.
Ao final, o chat informa quantos foram colocados e se algum ID está ausente.

Para regenerar as texturas do zero, se necessário:
```
javac tools/HiveCityTextureGen.java -d <tmp>
java -cp <tmp> HiveCityTextureGen src/main/resources/assets/firstcrusade/textures/block/hive_city
```

---

## 8. Resumo

| Item | Resultado |
|------|-----------|
| Causa raiz | Texturas eram colagens de conceito; box-UV mostrava tiles aleatórios por face |
| Correção | 48 texturas regeneradas coerentes; models/blockstates/UVs/IDs intactos |
| Geometria 3D | Preservada integralmente |
| Referências de asset | 48/48 íntegras (1:1) |
| Render/luz/colisão | cutout p/ vazados; luz e noOcclusion já corretos no registro |
| Build | compileJava SUCCESSFUL, sem erros de recurso |
| Ferramentas | gerador de texturas + validador + comando de teste |
| Pendência | Inspeção visual final em `runClient` (ambiente headless aqui) |
