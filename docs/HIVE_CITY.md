# First Crusade — HIVE CITY (arquitetura e diário de fases)

> Documento-mestre do sistema de Hive City gótica industrial. Complementa `STATUS.md` e
> `MOD_CONTEXT.md` (não os substitui). Mantenha o changelog no fim ao concluir cada fase.
> Referências visuais: as duas artes de hive city fornecidas pelo dono (escala opressiva,
> verticalidade, arcos góticos, passarelas, canos, iluminação verde/âmbar/vermelha, poluição).

---

## 1. Diagnóstico do projeto (FASE 1 — concluída 2026-07-14)

Fatos verificados no código (fonte de verdade, não na doc):

- **Stack:** Forge 47.4.10 / MC 1.20.1 / Java 17 / Gradle 8.8. GeckoLib 4.4.8.
- **Pacote:** `com.example.examplemod` (plano, 193 classes), classe principal `ExampleMod`,
  `MODID = "firstcrusade"`. Construtor injeta `FMLJavaModLoadingContext`.
- **Registros:** todos como `DeferredRegister` estáticos em `ExampleMod`
  (BLOCKS/ITEMS/CREATIVE_MODE_TABS/ENTITY_TYPES/BLOCK_ENTITY_TYPES/MENU_TYPES). Uma aba
  criativa (`first_crusade_tab`). Blocos usam `BlockBehaviour.Properties.of()` (padrão 1.20.x).
- **Sistema de cidade existente:** procedural bloco-a-bloco com **blocos vanilla**
  (`CityArchitect`, ~1100 linhas), layout radial (`CityLayoutPlan`), footprints/validador
  (`CityStructureFootprint`, `CityPlacementValidator`), enum de estruturas (`CityStructureType`)
  ligado a managers de BlockEntity (Core, Mine, Forge, Barracks…).
- **Construção gradual existente:** `StrategicConstructionBuilder`
  (2 blocos/construtor/tick, `setBlock(..., 3)`) e `StrategicConstructionPlanner/Project` —
  base conceitual perfeita para o modo gameplay da Hive (spec §8).
- **Worldgen existente:** `WorldGenPlacement` (ancoragem no solo com heightmap seguro),
  `WorldSettlementSeeder` (semeia 3 cidades + 3 camps num anel ao redor do spawn).
- **Dimensão existente:** `firstcrusade:planet_secundus` (JSON datapack; min_y 0, height 256,
  sem skylight, effects nether) usada pelo `SpaceportBlock`. **Não** comporta a Hive completa
  (spec §3 pede ~576 de altura) → a Hive terá dimensão própria na FASE 10 (ver §5 abaixo).
- **Comandos:** registrados via `RegisterCommandsEvent` (`StrategicWarAIEvents`), raiz atual
  `/fcstrategy`. O comando da Hive seguirá o mesmo padrão (raiz `/fchive`, FASE 4).
- **Assets:** 14 blockstates/modelos existentes reutilizam texturas vanilla; só havia
  1 textura de bloco própria. **Sem** pasta de tags, **sem** datagen (JSON escrito à mão),
  loot tables de bloco em `data/firstcrusade/loot_tables/blocks/` (apenas 2 existiam).
- **Lang:** `en_us.json` + `pt_br.json`, 450 chaves cada.
- **Sujeira detectada (não tocada):** `src/src/` (cópia antiga de código), `bin/`
  (saída de IDE), `aniumações.zip` na raiz. Candidatos a limpeza manual pelo dono.

### Decisões de integração decorrentes

1. **Não recriar** o sistema de cidades vanilla-villages: ele continua sendo o sistema de
   assentamentos do overworld. A Hive City é um sistema paralelo (`hive/`), que na FASE 10
   se conecta aos managers existentes (Core, facções, tropas) — mesma filosofia, outra escala.
2. **Subpacote** `com.example.examplemod.hive` (o projeto é plano, mas a Hive nasce isolada
   para permitir a futura renomeação de pacote sem dor; spec §18).
3. **Registro isolado**: `HiveBlocks` tem seus próprios `DeferredRegister` e é ligado com
   **uma linha** no construtor do `ExampleMod` — mínimo conflito com o desenvolvimento
   paralelo do dono.
4. **Zero BlockEntity/tick** em blocos decorativos (spec §15). Canos = blockstates 6-direções
   (padrão `PipeBlock` vanilla, shapes cacheados). Luzes = `lightLevel` fixo.
5. Tags `minecraft:mineable/pickaxe` etc. criadas agora (o projeto não tinha nenhuma) —
   sem elas, blocos com `requiresCorrectToolForDrops()` não dropariam nada.

---

## 2. FASE 2 — Fundação de blocos (ESTA ENTREGA)

**29 blocos registrados** (mínimo da spec: 24), aba criativa própria ("Hive City"),
texturas 16×16 100% originais geradas proceduralmente, modelos, blockstates, loot tables,
tags e traduções en/pt.

| Categoria | Blocos |
|---|---|
| Estrutural §9.1 | reinforced_ashcrete (+stairs/slab/wall), cracked_reinforced_ashcrete, riveted_steel_block (+stairs/slab), rusted_riveted_steel, armored_hive_plating |
| Pisos §9.2 | industrial_grating (vazada, luz passa), industrial_catwalk (piso no TOPO do bloco → nivelado com blocos cheios, NPCs andam sem pular), industrial_railing (conecta como grade) |
| Canos §9.3 | large_hive_pipe e pipe_junction (conexão automática nas 6 direções), pressure_valve (eixo + volante) |
| Máquinas §9.4 | machine_casing, industrial_vent (com face frontal) — cascas decorativas; máquinas funcionais ficam para fases futuras |
| Gótico §9.5 | gothic_arch, imperial_column (pilar com eixo), cathedral_wall, skull_wall_relief, aquila_wall_relief |
| Iluminação §9.6 | hive_lumen_strip (15, com eixo), yellow_industrial_lumen (15), green_industrial_lumen (13), red_emergency_lumen (10) |
| Decoração §9.7 | hazard_stripe_panel, cargo_container (com face frontal) |

**Extensibilidade dos canos:** a tag `data/firstcrusade/tags/blocks/pipe_connectable.json`
define a que os canos se conectam. Máquinas futuras entram na tag, sem código.

**Ferramentas:** `tools/HiveTextureGen.java` (repinta as 29 texturas de forma determinística —
mesma convenção do `TextureGen.java` de entidades) e `tools/gen_hive_assets.py` (regenera os
105 JSONs de assets/data). Rode-as só se for alterar arte/modelos.

**Classes novas** (`src/main/java/com/example/examplemod/hive/`): `HiveBlocks` (registro),
`HivePipeBlock`, `HiveCatwalkBlock`, `HiveGratingBlock`, `HiveHorizontalBlock`, `HiveTags`.
**Classe alterada:** `ExampleMod` (+2 linhas no construtor: registro do HiveBlocks).

---

## 2b. FASE 3 — Módulo protótipo `industrial_street_01` (ENTREGUE)

**Template:** `firstcrusade:hive/street/industrial_street_01` —
`data/firstcrusade/structures/hive/street/industrial_street_01.nbt`
(64×48×64, DataVersion 3465, 61 estados na palette, ~38 mil blocos não-ar, estados de
conexão de canos/corrimãos/muretas já gravados — a colocação não depende de block updates).

**Programa do módulo** (síntese das duas artes de referência):
- Rua-canyon N-S de 14 de largura (faixa central de aço, bordas hazard tracejadas,
  canaletas gradeadas com brilho verde por baixo, postes de holofote âmbar).
- OESTE — Manufactorum: hall de pé-direito 12 com banco de máquinas (casing+vents+canos
  verticais com válvulas), colunas 2×2 blindadas, fitas de lúmen no teto; fachada com
  contrafortes coroados de caveira, 3 portais em arco com águia, arcada aberta no N2,
  seteiras gradeadas no N3; anexo administrativo fechado ao sul do N3; terraço com
  chaminés 2×2 (até y44) e parapeitos; escada-de-mão interna liga os 3 pisos.
- LESTE — Hab/Cargo (assimétrico): baia de carga com portão 10×8 moldurado em hazard,
  pilhas de contêineres, mezanino catwalk com corrimão + escada, viga com corrente de
  içamento, alçapão gradeado p/ futura Underhive; generatorium com anel de segurança e
  luzes vermelhas; N2/N3 = hab stack com corredor central de lúmen contínuo, celas,
  janelas gradeadas — N3 noroeste é o SETOR DANIFICADO (divisórias destruídas, piso
  rachado, entulho, só luz vermelha); telhado com tanques e válvulas.
- 3 PONTES: A catwalk aberta y14 (com correntes ancoradas no dossel), B galeria fechada
  de tubulação y22 (interior 3 de largura, cano no eixo, janelas gradeadas), C passarela
  estreita y30 entre os terraços.
- DOSSEL (céu de máquinas): 3 mega-dutos E-W 3×3 (y40-42) com colares blindados e
  válvulas, lampiões pendurados em correntes, 2 pórticos N-S com passarela (folga de 2
  sob os dutos — passagem apertada proposital), 2 canos finos no topo (y44).
- TORRE DE MANUTENÇÃO (vertical): escada-de-mão contínua y2→37, pisos gradeados em
  y14/22/30, portas para cada andar, escotilha para o deque e ligação ao pórtico leste.

**Sockets do módulo:** N/S = rua (x25..38, térreo); W/E = aberturas de corredor N2
(z30..32, y16..19, com corrimão-guarda); vertical = torre (térreo→dossel).

**Comandos novos** (`HiveCommands`, raiz `/fchive`, permissão 2, zero alterações em
classes existentes):
- `/fchive place firstcrusade:hive/street/industrial_street_01 [0-3]` — cola com o canto
  mínimo na posição do jogador (rotações de 90°).
- `/fchive save <ns:caminho> <from> <to>` — captura regiões de QUALQUER tamanho via
  StructureTemplateManager (sem o limite 48³ da GUI) para (mundo)/generated/.

**Ferramenta:** `tools/gen_hive_module.py` regenera o .nbt deterministicamente e produz
plantas/cortes PNG de QA em /tmp. Edite-o para variações (damaged_01, etc.).

**Validação FASE 3 no jogo:** mundo criativo plano → suba para y≈4 com espaço 64×64
livre → `/fchive place firstcrusade:hive/street/industrial_street_01` → (1) atravessar a
rua N-S a pé; (2) entrar no hall pelo portal, subir a escada-de-mão até N2/N3; (3)
cruzar as 3 pontes; (4) subir a torre até o dossel e andar no pórtico; (5) conferir
canos conectados (banco de máquinas, ponte B, dossel); (6) setor danificado só com luz
vermelha; (7) `place` com rotação 1 ao lado para testar encaixe girado.

---

## 2c. FASE 4 — Sistema de módulos (ENTREGUE)

**Registro data-driven:** módulos declarados em `data/firstcrusade/hive_modules/**.json`
(recarregável com `/reload`), carregados por `HiveModuleManager` (reload listener
auto-registrado). Campos: `template`, `category`, `size`, `weight`, `sockets` (tipo por
face local; faces omitidas = `sealed`). Tipos de socket em uso: `street`, `corridor_l2`,
`canopy`, `foundation`, `sealed` — strings livres; dois módulos encaixam quando o socket
de um é igual ao da face oposta do outro (`HiveModule.fits`), já considerando rotação
(`socketAt(face, rotação)`).

**Marcadores (spec §13):** 12 blocos técnicos (`marker_*` — cubo-arame colorido com
código de 2 letras, sem colisão, quebra instantânea, aba criativa no fim). Na colocação,
`HiveMarkerProcessor` (StructureProcessorType `firstcrusade:hive_marker`) converte cada
um em AR e captura `(tipo, posição no mundo)` em `HiveMarkers` — nunca persistem.
O módulo `industrial_street_01` foi regenerado com **25 marcadores** de todos os 12
tipos (workers no manufactorum, civis no corredor hab, guardsmen nos portais da rua,
inimigos no setor danificado, patrulhas na rua e ponte A, loot na baia/torre, cobertura
atrás de colunas, defesa nas pontes/mezanino, comandante na torre, veículo na faixa,
comércio no corredor, construção no setor danificado).

**Comandos (`/fchive`, permissão 2):** `place` (processa marcadores) e `place_raw`
(mantém, para edição), `module list [categoria]`, `module info <id>`, `module place
<id> [rot]` (relata sockets por face no mundo), `markers` (re-lista a última captura com
partículas END_ROD), `show_bounds <id> [rot]` (contorno de partículas, não destrutivo),
`save` (sem limite 48³) e `clear <from> <to>` (guarda de volume 64³). IDs de módulo têm
tab-complete.

**Ligação:** tudo pendurado no `HiveBlocks.register()` já chamado pelo ExampleMod —
nenhuma classe fora de `hive/` foi alterada nesta fase.

---

## 2d. FASE 5 — Distrito SOUTH ASH GATE + CARGO RING (ENTREGUE)

**192×128 em 6 módulos** (nada de NBT monolítico — spec §6), colados de uma vez por
`/fchive district place firstcrusade:south_ash_gate` com **validação automática de
costuras** (cada face adjacente é conferida por socket; incompatibilidades são listadas).
Layout em `data/firstcrusade/hive_districts/south_ash_gate.json` (offsets/rotações,
recarregável). ~365 mil blocos, **64 marcadores** dos 12 tipos.

| Módulo | Dim | Conteúdo |
|---|---|---|
| gates/south_ash_gate_01 | 64³ | portal monumental 48 de largura (túnel 16×16, vão livre 10), portcullis blindada içada com fenda de guincho e correntes, galeria de tiro com buracos-assassinos gradeados e luz vermelha, folhas recolhidas em bolsões, fachada com arco gótico + águia + caveiras, torres com holofotes/poços/ladder ao teto, posto de inspeção com cabines e braços de barreira |
| gates/hive_wall_w_01 / _e_01 | 64³ | muralha de 47 (espessura 32) com **corredor interno, galeria de seteiras e passadiço contínuos entre módulos**, torre defensiva (câmara, janelas gradeadas, teto ameado), contrafortes com caveira, fosso com lodo luminoso, cavaletes anticarro, bunkers de munição no apron; a LESTE, **brecha de cerco** com entulho e spawns de inimigo do lado de fora |
| cargo/cargo_yard_01 | 64×48 | trilhos duplos E-W (vanilla rail, funcionais) cruzando a rua em nível, plataformas de carga com escadas, **guindaste-pórtico com contêiner suspenso por correntes**, pilhas de contêineres, scanner alfandegário (vão 10), guarita, sinais ferroviários, 4 mastros de holofote, rack de canos z58 contínuo pela fileira |
| cargo/warehouse_01 | 64×32 | prateleiras com contêineres, mezanino de escritório com corrimão e escada, **elevador de carga** (poço blindado 4×4 com ladder), números de setor **"01" pintados** na fachada norte, arco cargo_bay a leste com trilhos entrando até os para-choques |
| cargo/military_depot_01 | 64×32 | jaulas de suprimento, passarela de guarda, arsenal em **cofre blindado** contendo a **ENTRADA DA UNDERHIVE** (grade 4×4 sobre lúmen verde, correntes, caveiras, luz vermelha; socket down=underhive_shaft na convenção x48..53/z48..53), zona sul só com luz vermelha, "02" na fachada |

**Sockets novos:** `hive_wall`, `wall_apron`, `cargo_bay`, `cargo_ring`, `ash_wastes`,
`underhive_shaft` (convenções geométricas documentadas em `tools/hive_module_lib.py`).
A rua sai ao **norte do pátio** no socket `street` — o `industrial_street_01` encaixa
direto ali.

**QA:** além do roundtrip NBT, as invariantes de navegação foram **assertadas
programaticamente** (corredor/galeria/passadiço transitáveis de ponta a ponta nos 3
módulos de muralha incl. através das torres; pista central livre y2..6 em todo o
distrito e y2..10 no túnel do portão; ladders todas com suporte; trilhos completos;
arcos abertos; poço da underhive íntegro).

**Ferramentas:** `tools/hive_module_lib.py` (builder + NBT + prévias compartilhados) e
`tools/gen_hive_gate_district.py`. Portar o gen_hive_module.py (FASE 3) para a lib é
limpeza futura anotada.

---

## 2e. FASE 6 — Distrito MANUFACTORUM (ENTREGUE)

**192×128 em 3 módulos de 64×64×64**, industriais de pé-direito alto, projetados para
**empilhar sobre o Cargo Ring** (socket `down=cargo_ring`) — rua atravessa os 3 no socket
`street` e continua no `industrial_street_01`. ~66 mil blocos, 37 marcadores.

**12 blocos novos** (total do mod agora: 43 + 12 marcadores): forge_furnace (fornalha
acesa, luz 13), smelter_crucible (cadinho, luz 14), conveyor_belt (esteira), industrial_
turbine, boiler_tank (caldeira, conecta canos), smoke_stack (chaminé), cogitator_console
(tela verde, luz 7), control_panel, ventilation_duct, industrial_press (prensa), coolant_
tank (tanque translúcido verde, luz 6), imperial_propaganda_panel. Todos sem BlockEntity.

| Módulo | Conteúdo |
|---|---|
| industrial/foundry_01 | bateria de fornalhas na parede sul com **chaminés subindo ao dossel**, fileira de cadinhos de fundição, calha de metal derretido, ponte rolante com gancho, sala de controle envidraçada no mezanino, cartaz de propaganda |
| industrial/assembly_hall_01 | **duas linhas de esteira** longitudinais com prensas periódicas e peças em processamento, esteira transversal ligando-as, consoles de supervisão, dutos de ventilação descendo do dossel, **setor danificado** (linha destruída, entulho, canos rompidos, spawns de inimigo) |
| industrial/generator_hall_01 | **reator central** = pilar de turbinas empilhadas (y2..38) com anel de segurança e luz vermelha, bancos de caldeiras nas paredes, colunas de tanques de refrigerante (brilho verde), cabos grossos ligando reator às paredes, sala de controle com muitos consoles |

**Padrão de casco compartilhado (`hall_shell`):** piso que assenta no Cargo Ring, pilares-
mestres 2×2, **dois níveis de passarela suspensa** (y14 perímetro, y26 galeria de máquinas)
com corrimãos, torre de acesso com ladder apoiada em coluna sólida, **dossel de dutos** no
topo (y42..44, socket canopy) com chaminés atravessando, iluminação alta pendurada em
correntes, túnel da rua na parede norte com números de setor pintados.

**QA:** roundtrip NBT + invariantes de navegação assertadas (túnel da rua livre nos 3;
cabeça livre sobre as passarelas exceto footprints de sala de controle; ladders com
suporte sólido; spawns com cabeça livre; nenhum bloqueio de passagem). 4 defeitos reais
corrigidos (canos/caldeiras cruzando o túnel, ladder solta, spawns sob contêineres).

**Ferramentas:** `tools/gen_hive_manufactorum.py` (usa a lib compartilhada da FASE 5).

---

## 2f. FASE 6.5 — Pacote de Detalhamento (Parte A — ENTREGUE)

Expansão de blocos focada em interiores, mobiliário e a Underhive. **26 blocos/itens novos**
(mod agora: 59 blocos + 12 marcadores + 1 fluido). Escolhas do dono: móveis **funcionais**,
água tóxica **sólida + fluida**, e (Parte B) estátuas em bloco e em modelo.

**Fluido tóxico (funcional):** `toxic_sludge` é um ForgeFlowingFluid real (source+flowing,
`HiveFluids`), verde luminoso (luz 4), mais viscoso que água, **com dano de contato** —
`ToxicSludgeBlock.entityInside` aplica 1 de dano/s + veneno a quem entra (a ameaça da
Underhive, spec §5.14). Vem com balde (`toxic_sludge_bucket`), textura animada (still 16
frames, flow 16 frames + .mcmeta) e client extension com tint. Variante `solid_toxic_sludge`
= bloco cheio translúcido sem dano, para poças decorativas seguras.

**Móveis funcionais:** `hive_chair` e `hive_bench` são **sentáveis** — clique-direito monta
o jogador numa entidade-assento invisível (`HiveSeatEntity`, sem BlockEntity, auto-descarta
ao desmontar ou se a cadeira sumir); FACING vira o encosto. `hive_table` (tampo sobre 4
pernas, dá pra pôr coisas embaixo), `hive_rug` (tapete 1px vermelho com águia dourada),
`shelf_unit` (estante com itens), `supply_crate` (decorativo — contêiner com inventário fica
como follow-up para o Claude Code: exige BlockEntity+menu próprios).

**Luzes fortes (nível 14-15):** `industrial_floodlight`, `hanging_hive_lamp`,
`cathedral_brazier` (braseiro como o da imagem de referência), `warning_beacon` — para
corrigir a iluminação fraca dos interiores.

**Canos maiores:** `huge_hive_pipe` (12px) e `main_pipe_trunk` (14px) — mesma conexão
automática 6-direções do large_hive_pipe (na tag pipe_connectable), para troncos principais.

**Detalhes finos:** `industrial_chain`, `cable_bundle`, `wall_terminal` (tela verde, luz 6),
`sector_number_panel`.

**Java novo:** ToxicSludgeFluid, ToxicSludgeBlock, HiveFluids, HiveChairBlock, HiveSeatEntity,
HiveEntities, HiveShapeBlocks (Table/Rug), HiveClientEvents (renderer do assento + render
layers + client fluid extension). Ligados via HiveBlocks.register (fluidos + entidades).

**Parte B (próxima):** estátuas pequenas (bloco) e grandes (modelo multi-bloco detalhado),
+ o retrabalho de iluminação/corredores-galeria da Manufactorum pedido pelo dono.

**Validação no jogo:** balde de sludge derrama e escorre; ficar nele causa dano+veneno;
sentar/levantar nas cadeiras; luzes iluminam forte (F3 light 14-15); canos grandes conectam;
nenhum item roxo na aba; sem crash de renderer/fluido no log.

---

## 2g. FASE 6.5 — Pacote de Detalhamento (Parte B — ENTREGUE)

**Estátuas + retrabalho da Manufactorum** (iluminação e corredores góticos pedidos ao ver a
arte da passarela). Mod agora: 63 blocos + 12 marcadores + 1 fluido.

**Estátuas pequenas (1 bloco, com FACING):** `saint_bust` (busto encapuzado com auréola),
`aquila_statue` (águia bicéfala dourada em pedestal).

**Estátuas grandes (MULTI-BLOCO):** `saint_statue` e `imperial_guardian_statue` (3 blocos de
altura), `aquila_banner` (estandarte, 2 de altura). Sistema em `HiveStatueBlock`: uma
propriedade `part` (0..3) no padrão porta/flor-alta vanilla — **sem BlockEntity**. O bloco de
baixo (part=0) carrega o modelo alto (>16px de altura, algo que só modelo custom faz — é a
"estrutura de mais de 1 bloco com textura melhor" que o dono pediu); as partes de cima são
modelos vazios. Quebrar qualquer parte remove a coluna toda e dropa 1 item. Colocação exige
altura livre; rotacionável.

**Retrabalho da Manufactorum (`dress()` no gerador):** corrige os interiores escuros e vazios.
Adiciona braseiros de catedral (luz 15) no topo dos 5 pilares-mestres e na balaustrada;
lâmpadas penduradas em corrente do teto alto; **galeria gótica iluminada** rente à parede
oeste (arcada de colunas + arcos + balaustrada com braseiros — direto da arte de referência);
estátuas do Guardião Imperial ladeando o túnel da rua (entrada nobre) + bustos; estandartes
da águia entre pilares; mais lumens no piso; troncos de canos grossos (`main_pipe_trunk`)
descendo pelos cantos. Os 3 módulos foram regenerados e **repassaram todo o QA de navegação**
(as lâmpadas/banners foram posicionados fora da cabeça das passarelas depois que o QA acusou).

**Java novo:** HiveStatueBlock (multi-bloco vertical). **Ferramentas:** HiveTextureGen (+8
texturas), gen_hive_manufactorum (dress), hive_module_lib (blocos de detalhe + fix de
resolução dos canos grandes).

**Validação no jogo:** colocar estátua grande (ocupa 3 de altura, precisa de espaço); quebrar
qualquer parte remove tudo; girar; a Manufactorum agora tem galeria gótica iluminada, braseiros
e estátuas na entrada — bem menos escura. `/fchive district place firstcrusade:manufactorum`.

---

## 2h. FASE 7 — Distrito HAB STACKS + TRANSIT (ENTREGUE)

**192×128 em 3 módulos de 64×64×64**, o setor residencial/cívico que **empilha sobre a
Manufactorum** (socket down=canopy). Rua atravessa no socket street. ~91 mil blocos, 38
marcadores. Primeiro distrito a usar de fato os móveis, luzes e estátuas das fases 6.5.

| Módulo | Conteúdo |
|---|---|
| hab/hab_block_01 | **4 andares** de habitação (12 de pé-direito cada), 16 apês mobiliados (tapete, mesa+cadeiras, beliche, estante, terminal de parede, lâmpada), corredor central iluminado por andar, **átrio vertical** ligando os pisos com estandarte da águia descendo, escada de aço em espiral, telhado com tanques de água e baliza |
| hab/transit_nexus_01 | **nexo de transporte**: duas plataformas de trilho E-W ladeando a rua (vanilla rail funcional), vagão-contêiner parado, quadros de horário (cogitador+painel), **4 elevadores** blindados nos cantos (poço+ladder+patamar), átrio central alto com **estátua monumental do Guardião** entre braseiros, lumens pendurados |
| hab/market_chapel_01 | **mercado** no térreo (fileiras de bancas com mesa/caixa/banco/toldo, banca de comida quente com fornalha+cadinho, colunas) + **capela** no alto (nave com colunas e arcos góticos, tapete central, vitrais coloridos, **altar com águia e 2 estátuas de Santo**, bancos da congregação, braseiro pendente, campanário com baliza) |

**Sockets:** street (N/S), hab_corridor (E/W nos andares 2 e 4), canopy (down, assenta na
Manufactorum), hab_roof (up).

**QA:** roundtrip NBT + invariantes assertadas (túnel da rua livre; ladders com suporte;
spawns com cabeça livre — 2 corrigidos que caíam sob caixas de banca; **estátuas multi-bloco
íntegras**, part=0 com part=1 acima; 49 blocos distintos usados, todos com blockstate). Móveis
"macios" (tapete/grade/catwalk) contam como piso transitável no QA.

**Ferramenta:** tools/gen_hive_hab.py (usa a lib compartilhada; nenhum bloco novo — reusa
todo o catálogo das fases 2–6.5).

---

## 3. Direção visual travada (spec §11/§24)

Paleta central (usada pelo gerador): fuligem `#131417`, ashcrete `#3B3F43`, aço `#454C52`,
ferrugem `#6E3D1C`, verdigre industrial `#4A584D`, latão envelhecido `#83682F`,
osso `#A69C7D`, perigo `#D8A516`, lúmen âmbar `#F0BE4A`, verde-doentio `#7FD69A`,
vermelho-emergência `#D65438`. Luz amarela = habitado; verde = industrial/tóxico;
vermelho = militar/perigo; Underhive = escuridão com pontos vermelhos.

Regra de fachada: nenhuma parede lisa com mais de ~6 blocos contínuos — quebrar com canos,
relevos, vents, lumens, hazard e grating.

---

## 4. Roadmap técnico (FASES 3–10)

- **FASE 3 — Módulo protótipo 64×64** (rua industrial interna): construído em mundo de teste
  com os blocos da FASE 2; salvo por comando próprio (a GUI do structure block limita a
  48×48×48 — nosso `save_module` usa `StructureTemplateManager` direto, sem esse limite).
- **FASE 4 — Sistema de módulos:** registro por categoria (`hive/cargo`, `hive/industrial`…),
  sockets tipados por face (N/S/E/W/Up/Down), rotação, validação de bounding box, bloco
  técnico `hive_marker` convertido por `StructureProcessor` na colocação (spawn/loot/luz/
  patrulha — nunca persiste no mundo), comandos `/fchive place_module|show_bounds|…`.
- **FASE 5 — South Ash Gate + Cargo Ring** (192×128, portão ~48, muralhas 40–60).
- **FASES 6–9 — Manufactorum, Hab Stacks/Transit, Catedral/Administratum, Underhive.**
- **FASE 10 — Geração completa:** dimensão `firstcrusade:hive_world` (min_y −64, height 576 →
  Y máx 511; múltiplos de 16), layout determinístico 12×12 células de 64 (distritos por anéis
  de distância Chebyshev, portões nos eixos), **fila de colocação persistida em SavedData**
  processada no `ServerTickEvent` com orçamento de blocos/tick, flags
  `UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE` para evitar cascatas, passe final de luz por seção.
  Modo gameplay reaproveita `StrategicConstructionBuilder/Planner` (construtores reais).

---

## 5. Validação da FASE 2 no jogo

1. `.\gradlew build` → sem erros.
2. `.\gradlew runClient` → mundo criativo.
3. Aba **"First Crusade — Hive City"**: 29 blocos, nomes en/pt corretos, sem textura roxa.
4. Canos: colocar em L/T/cruz nas 6 direções → braços conectam sozinhos; encostar em
   machine_casing/vent/válvula → conectam (tag). Quebrar vizinho → braço some.
5. Catwalk ao lado de bloco cheio → andar sem pular; olhar por baixo → vãos com canos.
6. Grating no chão → luz atravessa (sem escuridão embaixo); vidro-style entre gratings.
7. Lumens: âmbar 15 / verde 13 / vermelho 10 (F3 light).
8. Stairs/slab/wall funcionam; mureta de ashcrete conecta (tag walls).
9. Sobrevivência: quebrar com picareta de pedra dropa tudo (blindagem exige ferro).
10. Log sem erros de modelo/textura ausente.

---

## 6. Changelog

- **2026-07-14 — FASE 7:** distrito Hab Stacks + Transit (3 módulos, 192×128, empilha na
  Manufactorum): habitação mobiliada de 4 andares, nexo de trilhos+elevadores, mercado+capela
  com altar e estátuas. 38 marcadores. Próximo: FASE 8 (Administratum/Catedral) ou FASE 9
  (Underhive).
- **2026-07-14 — FASE 6.5B:** estátuas pequenas (bloco) e grandes (multi-bloco vertical
  via HiveStatueBlock) + retrabalho de iluminação/corredores góticos da Manufactorum.
  Próximo: FASE 7 (Hab Stacks + Transit).
- **2026-07-14 — FASE 6.5A:** pacote de detalhamento — fluido tóxico funcional (dano),
  móveis sentáveis, tapetes, luzes fortes, canos maiores, detalhes (26 blocos/itens).
  Próximo: Parte B (estátuas) + retrabalho de iluminação da Manufactorum.
- **2026-07-14 — FASE 6:** distrito Manufactorum (3 módulos, 192×128, empilha sobre o
  Cargo Ring) + 12 blocos industriais novos (total 43). QA de navegação assertado.
  Próximo: FASE 7 (Hab Stacks + Transit).
- **2026-07-14 — FASE 5:** distrito South Ash Gate + Cargo Ring (6 módulos, 192×128,
  64 marcadores) + /fchive district list|place com validação de costuras por socket +
  lib compartilhada de geração. Próximo: FASE 6 (Manufactorum).
- **2026-07-14 — FASE 4:** registro data-driven de módulos + sockets tipados com
  rotação; 12 blocos marcadores + StructureProcessor de captura; /fchive module
  list|info|place, markers, show_bounds, place_raw, clear; módulo protótipo regenerado
  com 25 marcadores. Próximo: FASE 5 (South Ash Gate + Cargo Ring, 192×128).
- **2026-07-14 — FASE 3:** módulo `industrial_street_01` (64×48×64) entregue como .nbt
  validado por roundtrip; comandos `/fchive place|save` (auto-registrados, sem tocar em
  classes existentes); gerador `tools/gen_hive_module.py` com prévias de QA. Próximo:
  FASE 4 (registro de módulos, sockets, marcadores, bounding box debug).
- **2026-07-14 — FASE 1+2:** diagnóstico completo; 29 blocos + 29 texturas originais +
  105 JSONs + tags + lang; ferramentas HiveTextureGen/gen_hive_assets; integração de 2 linhas
  no ExampleMod. Próximo: FASE 3 (módulo protótipo 64×64).
