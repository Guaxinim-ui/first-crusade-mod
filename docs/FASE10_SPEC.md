# FASE 10 — Spire + Geração Automática da Hive City
## Especificação técnica para execução (retomar em conversa nova)

Este documento contém TODO o contexto necessário para implementar a Fase 10 sem
redescobrir nada. Ao abrir a nova conversa, comece com: *"Vamos fazer a Fase 10 do mod
First Crusade seguindo docs/FASE10_SPEC.md"* e anexe/aponte para este arquivo + o
docs/HIVE_CITY.md.

---

## 0. Estado atual do projeto (o que já existe e funciona)

- Mod Forge 1.20.1, MODID `firstcrusade`, pacote base `com.example.examplemod`, classe
  principal `ExampleMod`. Todo o código da Hive isolado em `com.example.examplemod.hive`.
- **71 blocos + 12 marcadores + 1 fluido tóxico** registrados via `HiveBlocks.register()`.
- **Sistema de módulos data-driven** (`HiveModuleManager`, `HiveModule`): módulos em
  `data/firstcrusade/hive_modules/**.json` com `template`, `category`, `size`, `weight`,
  `sockets` (tipo por face; encaixe por `HiveModule.fits` + `socketAt(face, rotação)`).
- **Sistema de distritos** (`HiveDistricts`): `data/firstcrusade/hive_districts/*.json`
  com lista de `{module, offset, rotation}`. Colados por `/fchive district place`.
- **5 distritos prontos** (todos 192×128 na horizontal, exceto onde nota):
  - `south_ash_gate` (6 módulos, portão+muralha+cargo, tem o poço da Underhive)
  - `manufactorum` (3, empilha sobre cargo; down=cargo_ring)
  - `hab_stacks` (3, empilha sobre manufactorum; down=canopy)
  - `administratum` (3, empilha sobre hab; down=hab_roof, up=spire_base)
  - `underhive` (3, embaixo de tudo; up=underhive_ceiling)
- **StructureProcessor de marcadores** (`HiveMarkerProcessor`): converte blocos marcadores
  em ar e captura (tipo, pos) em `HiveMarkers`.
- **Comandos `/fchive`** (`HiveCommands`, permissão 2): place, place_raw, module
  list/info/place, district list/place (com validação de costuras por socket), markers,
  show_bounds, save (sem limite), clear.
- **IDs de módulo**: o id é o caminho dentro de `hive_modules/` SEM prefixo `hive/`
  (ex.: `firstcrusade:admin/scriptorium_01`). O `template` DENTRO do json TEM `hive/`
  (ex.: `firstcrusade:hive/admin/scriptorium_01`) apontando para o .nbt em
  `structures/hive/...`. **NÃO CONFUNDIR** — foi o bug da Fase 7.
- **Teste de referências**: já existe padrão que valida distrito→módulo→nbt em todos os
  distritos. Rodar sempre após criar/editar distrito.

---

## 1. Objetivo da Fase 10

Duas entregas relacionadas:

### 1A. Spire (o pináculo) — 1-2 módulos novos
O topo cerimonial da hive, que assenta no socket `spire_base` (up do administratum).
Palácio do governador / farol / a agulha que corona a cidade. Isto é trabalho de módulo
convencional (como as fases 5-9) — pode até ser feito nesta conversa se sobrar espaço,
mas o núcleo da Fase 10 é o 1B.

### 1B. Geração automática da cidade completa
Um sistema que monta a hive INTEIRA automaticamente, empilhando os distritos existentes
num layout coerente, sem o jogador colar distrito por distrito à mão.

---

## 2. Arquitetura proposta da geração (1B)

### 2.1. Dimensão própria `hive_world`
- `data/firstcrusade/dimension/hive_world.json` + `dimension_type/hive_world.json`.
- Sugestão: `min_y = -64`, `height = 576` (permite Underhive embaixo + ~8 níveis de 64
  + Spire no topo). `has_ceiling=false`, `has_skylight=true` (mas a hive é coberta).
- Biome único plano/vazio (void ou um `hive_floor` custom) — a cidade É o terreno.
- ChunkGenerator: o mais simples possível — um FlatLevelSource vazio, ou um gerador que
  só põe bedrock em y=min. A cidade vem da colocação de estruturas, não do terreno.

### 2.2. Layout determinístico por seed
Classe `HiveCityLayout`:
- Entrada: seed (long) + tamanho alvo (ex.: 768×768 = 12×12 células de 64, ou 4×4
  super-blocos de 192×128... decidir grid).
- Saída: lista ordenada de `PlacedDistrict {districtId, origin(BlockPos), rotation}`.
- Regras de empilhamento vertical (do fundo ao topo):
  1. `underhive` (y base, ex.: -64..-1 relativo)
  2. `south_ash_gate` / cargo (y 0..127) — a base sólida com muralhas na borda
  3. `manufactorum` (empilha sobre cargo)
  4. `hab_stacks` (empilha sobre manufactorum)
  5. `administratum` (empilha sobre hab)
  6. `spire` (no centro, sobre o administratum central)
- Regras horizontais: muralhas (`south_ash_gate`) na PERIMETRIA; indústria/hab/admin no
  interior; ruas alinhadas célula-a-célula pelo socket `street` (x25..38 local) para a
  malha viária ser contínua entre módulos vizinhos.
- Determinismo: mesmo seed → mesma cidade. Usar `RandomSource` semeado; escolher variantes
  de módulo por `weight` quando houver múltiplos na categoria.
- Validação de costuras: reusar `HiveModule.socketAt`/`fits` para garantir que vizinhos
  encaixam; onde não encaixam, escolher outra variante/rotação ou deixar parede `sealed`.

### 2.3. Colocação escalonada (o ponto crítico)
Colocar milhões de blocos de uma vez TRAVA o servidor. Solução:
- `HiveGenerationQueue` (SavedData, persiste no nível): fila de tarefas de colocação,
  cada tarefa = 1 módulo (ou 1 fatia de módulo) a colar em (origin, rotation).
- Processada em `ServerTickEvent` (ou `LevelTickEvent`): N tarefas por tick (config,
  ex.: 1-2 módulos/tick), até esvaziar. Persistir progresso para sobreviver a
  save/quit/reload.
- Cada tarefa usa o mesmo `placeInWorld` + `HiveMarkerProcessor` dos comandos atuais.
- Feedback: mensagem de progresso a cada X% ou contador de módulos restantes.
- Considerar: forçar carregamento dos chunks-alvo (ForgeChunkManager) durante a colocação
  da tarefa, senão placeInWorld em chunk não carregado falha silenciosamente.

### 2.4. Comandos novos (`HiveCommands`)
- `/fchive city generate [seed]` — enfileira a cidade inteira (dimensão hive_world).
- `/fchive city status` — módulos colocados / restantes / % .
- `/fchive city cancel` — limpa a fila.
- `/fchive city tp` — teleporta o jogador para a dimensão/spawn da hive.

### 2.5. Ligações com sistemas existentes do mod (opcional, se o dono quiser)
- Os marcadores capturados (`HiveMarkers`) podem alimentar os managers de facção que já
  existem no mod (WorldSettlementSeeder, OrkCorruptionManager, etc.) — spawns de civis,
  guardas, inimigos, loot, patrulhas nos pontos marcados. Confirmar com o dono se quer
  isso agora ou depois.

---

## 3. Ordem de implementação sugerida (para a nova conversa)

1. Dimensão `hive_world` vazia + comando `/fchive city tp` (validar que entra numa dim
   plana vazia).
2. `HiveCityLayout` só com LOG (sem colocar nada): `/fchive city generate` imprime o
   plano (lista de distritos+origin+rotation) e valida costuras. Conferir o layout no
   papel antes de colar 1 bloco.
3. `HiveGenerationQueue` + tick processor: colar de verdade, escalonado. Começar com um
   layout PEQUENO (2×2 células) para testar performance.
4. Escalar para 768×768; ajustar módulos/tick para não travar.
5. `city status/cancel`. Persistência testada (quit no meio, volta, continua).
6. (Opcional) Spire no topo central.
7. (Opcional) Ligar marcadores aos managers de facção.

---

## 4. Armadilhas conhecidas (aprendidas nas fases 2-9)

- **API do Forge 1.20.1**: `RegisterClientExtensionsEvent` NÃO existe no 1.20.1 base (é
  1.20.4+/NeoForge). Para fluido, usar `IClientFluidTypeExtensions` via
  `FluidType.initializeClient` override (já corrigido no HiveFluids atual). Cuidado com
  qualquer API nova — validar contra 47.x antes de usar.
- **Assets não chegam ao VS Code**: entregar sempre um pacote que inclua TODOS os assets
  referenciados. Vários blocos apareceram cinza porque o zip não foi aplicado. Rodar a
  verificação modelo→textura antes de empacotar.
- **id de módulo vs template**: ver seção 0. Rodar o teste de referências.
- **placeInWorld em chunk não carregado** falha silencioso — forçar chunk load.
- **Colocação grande trava** — SEMPRE escalonar por tick, nunca de uma vez.
- **Ambiente de dev do Claude não roda gradlew** (sem rede) — validação é programática
  (ler .nbt, checar JSONs). O dono compila/testa no VS Code e reporta.
- **Não confundir o sistema Hive com o resto do mod** — o mod tem MUITAS outras classes
  (CityArchitect, WorldGenPlacement, etc.) que NÃO são da Hive. A Hive é auto-contida em
  `com.example.examplemod.hive` + ligada por 1 linha no construtor do ExampleMod.

---

## 5. Referência rápida de sockets (para o layout)

- `street` (x25..38 local): malha viária, faces N/S da maioria dos módulos.
- Vertical: `cargo_ring`→`canopy`→`hab_roof`→`spire_base` (a pilha central).
- `underhive_ceiling`/`underhive_shaft`: liga underhive à cargo.
- `hive_wall`/`ash_wastes`: muralha externa (perímetro da cidade).
- Detalhes geométricos de cada socket documentados no topo de `tools/hive_module_lib.py`.

---

## 6. Checklist de entrega da Fase 10

- [ ] Dimensão hive_world acessível e vazia
- [ ] `/fchive city generate [seed]` determinístico (mesmo seed = mesma cidade)
- [ ] Layout valida costuras antes de colar
- [ ] Colocação escalonada por tick, sem travar o servidor
- [ ] Fila persiste em save/quit/reload
- [ ] `/fchive city status/cancel/tp`
- [ ] Documentar no HIVE_CITY.md (seção 2k) + changelog
- [ ] Pacote com TODOS os arquivos novos (java + data da dimensão) e teste programático
