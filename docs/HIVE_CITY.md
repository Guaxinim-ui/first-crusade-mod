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

## 2i. FASE 8 — Distrito ADMINISTRATUM + CATEDRAL (ENTREGUE)

**192×128 em 3 módulos de 64×64×64**, o coroamento cívico-religioso da colmeia — o distrito
mais monumental. Empilha sobre o Hab Stacks (down=hab_roof). ~102 mil blocos, 43 marcadores.

| Módulo | Conteúdo |
|---|---|
| admin/cathedral_nave_01 | **PEÇA CENTRAL: nave monumental** de teto altíssimo (y52) com dupla arcada de colunas e arcobotantes, via processional de tapete vermelho, vitrais gigantes nas duas paredes, **órgão de canos** sobre a entrada, **altar-mor** ao fundo com águia colossal, 2 Santos + 2 Guardiões monumentais, braseiros, bancos da congregação e candelabros pendentes de braseiro em corrente |
| admin/scriptorium_01 | arquivo/escritório de 4 andares com salas mobiliadas (mesa, cadeira, estantes de arquivo, terminal), **sala de cogitadores** (banco de servidores + telas), arquivo vertical de estantes altas, estátua da águia no saguão, telhado com antenas/balizas |
| admin/tribunal_01 | **grande salão de justiça** (colunas majestosas, estrado do juiz elevado com 2 Guardiões e águia atrás, bancos do público, balaustrada, vitrais, candelabros) + **bloco de celas** no alto (corredor de vigília com celas gradeadas, catres, luz vermelha) |

**Sockets:** street (N/S), admin_hall (E/W), hab_roof (down, assenta no Hab), spire_base (up
— gancho para o futuro Spire da Fase final).

**QA reforçado:** além do roundtrip + navegação (túnel livre, ladders, spawns, estátuas
íntegras), adicionei um **teste automático de referências distrito→módulo→nbt** que roda em
TODOS os distritos. Ele teria pego na hora o bug da Fase 7 (id do módulo com 'hive/' a mais).
Auditoria dos 4 distritos: todos válidos. 1 defeito corrigido (escadaria caindo sobre a rua).

**Nota:** a Fase 7 (hab_stacks) teve o id de módulo corrigido no distrito
(firstcrusade:hab/... em vez de firstcrusade:hive/hab/...) — incluído no pacote COMPLETO.

**Ferramenta:** tools/gen_hive_administratum.py (lib compartilhada; sem blocos novos).

---

## 2j. FASE 9 — Distrito UNDERHIVE (ENTREGUE)

**192×128 em 3 módulos de 64×48×64**, a subcidade escura sob a colmeia — conecta ao poço da
underhive no military_depot da Fase 5 (up=underhive_ceiling). Ambiente escuro iluminado por
fungos, fogueiras e lúmens quebrados. **8 blocos novos** (mod agora: 71 blocos + 12 marcadores
+ 1 fluido). 28 marcadores.

**Blocos novos:** rubble (escombros), underhive_concrete (rachado), scrap_pile (sucata),
glow_fungus (fungo bioluminescente, luz 8), toxic_barrel (tambor vazando), corrugated_wall
(barricada de gangue), gang_fire (fogueira, luz 15), gang_marking (grafite de caveira).

| Módulo | Conteúdo |
|---|---|
| underhive/sump_tunnels_01 | **coletor/esgoto**: grande canal de **água tóxica de verdade** (toxic_sludge, com dano) atravessando, canos despejando, passarela de grade sobre o canal, túneis de manutenção, tambores tóxicos, escada-de-mão subindo ao poço da hive |
| underhive/collapsed_ruins_01 | **ruínas**: montanha de escombros de um teto desabado, fragmentos de parede de catedral espetados, uma **estátua de Santo tombada**, meias-paredes de habitação exposta (mostrando apês em corte com móveis abandonados), poças de água tóxica infiltrada, caminho serpenteante transitável |
| underhive/gang_territory_01 | **território de gangue**: barricadas de sucata corrugada com passagens, acampamento central com fogueiras e assentos, pilhas de sucata, **arena de luta** rebaixada com arquibancada, **trono do chefe** numa plataforma, grafites de caveira pelas paredes |

**Sockets:** tunnel (N/S), cavern (E/W), underhive_ceiling (up), bedrock (down).

**QA:** roundtrip + navegação (ladders com suporte — 1 poço corrigido; **spawns fora da água
tóxica** para não tomarem dano ao nascer; spawns com cabeça livre; blocos existentes). Água
tóxica é fluido real (393 blocos no sump). **Auditoria de referências rodada nos 5 distritos
— todos OK.**

**Casco de caverna (`cavern_shell`):** piso irregular de escombros, teto rachado, vigas
enferrujadas aleatórias, ~40 fungos luminosos por módulo como iluminação principal.

**Ferramenta:** tools/gen_hive_underhive.py.

---

## 2k. FASE 10 — Geração completa da cidade (dimensão, layout, fila, ticker)

**Documentação retroativa** — este sistema já existia no código (`hive/city/`) antes desta
entrega, mas nunca tinha sido registrado aqui. Descrito abaixo pelo comportamento real do
código (fonte de verdade), não por suposição.

**Dimensão própria:** `firstcrusade:hive_world` (`HiveWorld.java`) — min_y −64, altura 576
(Y −64..511). Constantes: `UNDERHIVE_Y=-64`, `GROUND_Y=0`, `LEVEL_HEIGHT=64`.

**Layout determinístico (`HiveCityLayout.java`):** grade quadrada `(2·raio+1)²` de super-células
de `CELL_PITCH=192`. Anel de Chebyshev decide o papel de cada célula: anel externo = 4 portões
(`south_ash_gate`) nos eixos + `hive_wall_line` nos setores retos + `hive_corner_bastion` nos 4
cantos; interior = pilha `manufactorum → hab_stacks → administratum`; centro = + `underhive`
(abaixo) e `spire` (no topo). RNG seedado reservado para variação futura — sem `Math.random`,
sem ordem de `HashMap`, contrato determinístico documentado no arquivo.

**Fila persistida (`HiveGenerationQueue`, `SavedData` em `hive_world`):** plano vira uma fila de
tarefas (distrito, origem, rotação); sobrevive save/quit/reload. `HiveCityTicker` drena 1
distrito/tick (`DISTRICTS_PER_TICK`), force-loading só os chunks do footprint sendo colado e
liberando-os no mesmo tick — nenhum chunk fica forçado permanentemente. Falha de um distrito loga
e `pop()` avança mesmo assim (a fila nunca trava por um distrito ruim). `HiveClearQueue` faz o
mesmo padrão para limpar em lotes (96k blocos/tick).

**Comandos (`/fchive city ...`):** `generate [seed]`, `status`, `cancel`, `preview`,
`build_full_test`/`full_test_status`/`full_test_cancel`/`full_test_clear`/`full_test_tp <ponto>`,
`tp`. `HiveFullCityTest` fixa seed=40000/raio=2 (grade 5×5, ~45 distritos) para validação
integrada repetível.

---

## 2l. Rebuild V2 dos distritos (Administratum, Hab Stacks, Manufactorum, Underhive, Perímetro, Spire)

**Documentação retroativa.** Todos os 8 distritos reais passaram por um rebuild "v2" usando os
geradores `tools/gen_hive_*_v2.py` (+ `gen_hive_spire.py`) sobre a biblioteca compartilhada
`tools/hive_module_lib.py` — não estava registrado no changelog (só a entrada de 2026-07-19 do
Administratum mencionava "v2"). Confirmado por inspeção real dos módulos, distritos e prévias
PNG de QA (`tools/previews_*_v2/`), não por suposição:

- **Administratum, Hab Stacks, Manufactorum:** cada um ganhou uma **segunda fileira de módulos
  "connector"** (`connectors/admin_processional_*`, `connectors/hab_transit_*`,
  `connectors/manufactorum_service_*`) atrás da fileira original — o distrito inteiro passou de
  3 para 6 módulos (192×128×64 completo), com trânsito vertical alinhado entre as duas fileiras.
  Isso é exatamente o tipo de trabalho de integração horizontal que a spec pede (nenhuma rua
  terminando em parede cega).
- **Underhive:** de 3 para 6 módulos (`forgotten_catacombs_01`, `sump_market_01`,
  `reactor_abyss_01` somam-se aos 3 originais).
- **Perímetro (`hive_wall_line`, `hive_corner_bastion`):** reconstruídos com 6 módulos cada
  (torres desiguais, contraforte, passadiço de 3 níveis) — `hive_wall_line` reaproveita a
  fileira de cargo já estabelecida (`cargo/warehouse_01` etc.); `hive_corner_bastion` é uma
  fortaleza em L com fileira frontal+traseira simétrica.
- **Spire:** um único módulo monumental `spire/spire_crown_01` (96×128×96 — base cruciforme
  escalonada, torres gêmeas, coroa) centralizado na célula central 192×128, com prévia
  isométrica dedicada (`tools/previews_spire_v2/`).
- **`south_ash_gate`** permanece na configuração original da Fase 5 (conteúdo dos módulos já é
  "v2" — ver `description` de `south_ash_gate_01.json` — mas sem a fileira connector extra; um
  portão real precisa de uma brecha na muralha, não de uma fileira de serviço duplicada, então
  a assimetria com os outros dois distritos de perímetro é uma decisão de design, não um bug).

**Validação visual** feita nesta entrega por inspeção real das prévias PNG (planta/corte/
isométrica) — não por leitura de nome de arquivo: nave gótica com telhado em clerestório
(Administratum), muralha com 3 níveis de passadiço e luz repetida (perímetro), base
cruciforme escalonada com torres gêmeas (Spire). Nenhuma reconstrução do zero foi necessária.

---

## 2m. FASE 11 — Marcadores persistentes + validação estática (ESTA ENTREGA)

**Problema (spec §11):** `HiveMarkers` (o buffer de captura usado durante `placeInWorld`) só
guardava a **última** colocação num campo estático em memória — numa cidade de ~45 distritos,
todos os marcadores exceto os do último distrito colado eram perdidos, e nada sobrevivia a
salvar/fechar/recarregar o mundo.

**`HiveCityMarkerData`** (novo, `hive/city/`) — `SavedData` em `hive_world` (mesmo padrão de
`HiveGenerationQueue`/`HiveClearQueue`, `computeIfAbsent` de 3 argumentos do Forge 47.x). Guarda,
por cidade: id, seed, centro, bounding box, estado de conclusão; por **instância de distrito**
(cada cópia física colocada — um distrito pode repetir várias vezes na cidade): id, distrito,
origem, rotação, `SectorState` (NORMAL/CONTESTED/ENEMY_CONTROLLED); por **marcador**: id,
instância dona, distrito, tipo, posição, ativo/inativo, UUID de entidade vinculada, próximo
horário de respawn, loot coletado. `PATROL_POINT`s da mesma instância formam a rota de patrulha
implícita dessa instância (`patrolRoute(instanceId)`).

Só o caminho REAL de geração de cidade (`HiveCommands.placeDistrict`, usado por
`HiveCityPlacer`→`HiveCityTicker`) grava nesse store — o comando de dev `/fchive district place
<id>` (teste isolado de um distrito, sem contexto de cidade) continua usando só o buffer efêmero
`HiveMarkers.last()`, para não misturar testes ad-hoc com a cidade real. `resetForNewCity` zera
o store a cada `/fchive city generate` ou `build_full_test` novo; `markComplete()` marca a cidade
como concluída quando a fila de geração esvazia.

**Comandos novos:** `/fchive validate markers` (resumo: cidade/seed/contagens por tipo),
`/fchive debug marker <tipo>` (partículas + contagem de todo marcador persistido daquele tipo).

**Validação estática distrito→módulo→template→assets (spec §19):** nenhum validador reusável
existia (o "teste automático" citado no changelog da Fase 8 foi um script avulso, não
commitado). Dois validadores equivalentes foram escritos — `tools/hive_city_validate.py`
(Python) e `tools/HiveCityValidate.java` (porta sem dependências, usada nesta sessão porque o
ambiente não tinha Python instalado, só o stub da Microsoft Store). Ambos rodam 100% offline:

- Resolve toda referência distrito→módulo e módulo→template NBT (convenção real de
  `SimpleJsonResourceReloadListener`/`StructureTemplateManager`, não reinventada).
- Lê o NBT gzip de cada template (parser NBT genérico, tags 1–12) e confere o `size` declarado
  no JSON contra o tamanho real.
- Coleta cada bloco `firstcrusade:*` usado em qualquer paleta e confere blockstate → modelo(s)
  → textura(s) existem no disco (pega exatamente as 3 causas reais de crash: blockstate/modelo/
  textura ausente).
- Reimplementa `HiveCommands.touchingFace` + `HiveModule.socketAt`/`fits` para conferir toda
  costura interna de todo distrito, à rotação 0 (rotação global da cidade gira um distrito
  inteiro rigidamente — não afeta o encaixe interno entre seus próprios módulos).
- Lista módulos/templates órfãos (não referenciados) como aviso, não erro.

**Resultado da primeira execução real:** 9 distritos, 42 módulos, 42 templates, 121 blocos
`firstcrusade:*` únicos em uso, 49 costuras conferidas. **Zero** erros de blockstate/modelo/
textura/referência ausente. **3 incompatibilidades de socket reais encontradas e corrigidas**
(ver abaixo) — depois da correção, 49/49 costuras OK.

**Bug real encontrado:** `gates/hive_wall_line_{w,c,e}_01.json` declaravam `north=south=east=
west="hive_wall"` uniformemente — copiado do par simétrico frente/fundo do `hive_corner_bastion`
(onde isso é correto), mas o `hive_wall_line` na verdade encosta na mesma fileira de cargo
estabelecida (`cargo/warehouse_01`/`cargo_yard_01`/`military_depot_01`, offset z=0) que o portão
original (`hive_wall_w_01`/`hive_wall_e_01`/`south_ash_gate_01`) já usa com sucesso — essa
fileira espera `wall_apron` (w/e) ou `street` (centro) na face voltada pra dentro, não
`hive_wall`. Corrigido nos 3 JSONs de módulo **e** na fonte do gerador
(`tools/gen_hive_perimeter_v2.py`, que antes escrevia o mesmo dicionário de sockets pros 9
módulos do arquivo — wall-line e corner-bastion — num único loop) para não regredir na próxima
geração. Inspecionei a geometria real do gerador (`straight_wall_builder()`): a massa da
muralha fica recuada de ambas as bordas do módulo (chão livre nos dois lados, portas só na face
sul) — o que dá confiança de que a correção reflete compatibilidade física real, não só um
rótulo. **Confirmação final ainda depende de teste em jogo** (`/fchive district place
firstcrusade:hive_wall_line`, andar da fileira de cargo até a muralha).

**Ferramentas:** `tools/hive_city_validate.py`, `tools/HiveCityValidate.java`. Rodar (raiz do
repo): `javac tools/HiveCityValidate.java -d <tmp> && java -cp <tmp> HiveCityValidate` — escreve
`tools/generated/HIVE_CITY_VALIDATION_REPORT.md`.

### Duas pontas investigadas (resolvidas por inspeção das prévias reais, não por suposição)

1. **`south_ash_gate` "sem paridade":** era um erro de leitura meu — o distrito **sempre** foi
   6 módulos (fileira de cargo em z=0 + fileira de portão/muralha em z=64, 192×128 completo), tem
   rebuild v2 real (prévia completa em `tools/previews_south_gate_v2/`, plano mostra galpão +
   depósito militar com o poço da underhive + rua atravessando o portão de norte a sul) e passa a
   validação de costuras (0 mismatch). A **única** coisa desatualizada era o campo `description`
   do JSON do distrito, ainda no texto Fase-5 em português — o gerador v2 reescreve os JSONs de
   MÓDULO mas nunca tocou no JSON do DISTRITO. **Corrigido** para descrição v2 precisa. Nenhum
   trabalho estrutural era necessário.

2. **"Vazio" da espinha do Underhive:** é atmosfera de caverna **proposital**, não um buraco de
   continuidade. O plano de chão mostra uma subcidade rica e conectada (canais de lodo tóxico,
   arena de luta, poço do reator com passarelas, corredor de largura total em ~z=30). As três
   seções norte-sul (`section_sump_x31`, `section_ruins_market_x95`, `section_gang_reactor_x159`)
   mostram, todas, **chão de escombros contínuo ao longo dos 128 de profundidade**, incluindo a
   costura entre as duas fileiras de módulos (z=63) — as duas fileiras se conectam no nível do
   piso. O grande volume escuro acima é o "abismo industrial" que a spec pede; o poço do reator
   (x159) tem circulação vertical real (passarelas + escadas descendo pelo vão). **Nenhum
   conserto necessário.** Ressalva honesta: continuidade de piso confirmada por 3 cortes
   independentes das prévias (que são projeções da mesma grade que o gerador construiu, então
   confiáveis), mas a *caminhabilidade* garantida (sem degrau de 2 blocos nem gargalo bloqueado
   por lodo) só um validador em nível de NBT ou um teste em jogo fecham de vez — anotado como
   próximo passo opcional.

---

## 2n. Colisão do concept set: só a escada vira colisão real; os "pisos" continuam cheios

**História completa (importante pra não repetir o erro):**

1. **Tentativa 1 (revertida):** o dono relatou que "slabs e escadas eram tratados como blocos
   inteiros". Medi os modelos com um extrator de bounding-box: os 7 pisos (`landing_slab`,
   `floor_grate`, `hazard_grated_floor`, `floor_vent`, `metal_floor_plate`, `cathedral_floor_tile`,
   `bloodstained_floor_tile`) têm modelos de 2–4px, e `cathedral_stair_block` é uma escada de 4
   degraus — todos registrados como cubo cheio. Fiz uma `HiveShapeBlocks.Plate` (colisão fina) pros
   7 pisos + `HiveStairShapeBlock` (colisão em degraus) pra escada.

2. **Regressão relatada:** os trilhos passaram a **flutuar** e o piso ficou **desnivelado**.
   Causa raiz (confirmada lendo os geradores): esses "pisos" **não são slabs** — os templates os
   colocam como a **superfície estrutural do chão** (ex.: `gen_hive_south_gate_v2.py`: base cheia
   `ASH_CR` em y=0, tile em y=1, **trilho vanilla em y=2 apoiado no tile**; e o chão mistura tile
   fino com blocos cheios `ASH` na MESMA camada). Deixar o tile fino (a) rebaixou a superfície
   ~0.87 bloco onde tile encosta em bloco cheio → chão em degraus, e (b) tirou a face de topo
   sólida → o trilho perdeu suporte e flutuou. `landing_slab` também é usado como piso
   (`gen_hive_manufactorum_v2.py:284`, xadrez com grate), mesmo problema.

3. **Correção final (ESTA ENTREGA):** revertidos os **7 pisos** para cubo cheio (estado que
   funcionava — chão nivelado, trilhos apoiados). Mantida **só** a `HiveStairShapeBlock` em
   `cathedral_stair_block`, que é uma **escada de verdade** (usada com `FACING`, subindo, nos
   geradores — nunca como piso plano) e portanto não quebra chão/trilho. `HiveShapeBlocks.Plate`
   removida (sem uso). Slabs/escadas das FASES 2–9 nunca foram afetados (já são `SlabBlock`/
   `StairBlock` reais).

**Lição:** no ambiente atual **não há Python funcional** (só o stub da Store), então **não dá pra
regenerar os templates NBT** (os geradores são Python). O visual "piso fino/rebaixado" que o dono
queria só é alcançável regenerando os templates (base cheia + superfície fina no y certo + trilho
rebaixado) — isso é trabalho do pipeline Python, não um ajuste de bloco. O mesmo vale pro **"trilho
que vai pra lugar nenhum / falta um portão de verdade"**: abrir uma passagem de trilho na muralha é
conteúdo baked no NBT do módulo de portão → precisa do gerador. Ferramenta de apoio descartável:
`ModelExtent` (extrator de bounding-box, no scratchpad).

---

## 2o. FASE 12 — População viva (primeira fatia — ESTA ENTREGA)

Primeira fatia do §12, construída sobre a fundação de marcadores persistentes (§2m). O objetivo:
a cidade deixa de ser cenário e passa a ser habitada, sem varredura global (§16) e sem quebrar as
cidades normais do overworld.

**`HivePopulationManager`** (novo, `hive/city/`, auto-registrado no event bus, só roda em
`hive_world`). Cada `TICK_INTERVAL` (40 ticks/2s), sem varrer a cidade inteira:
- **Reconcilia** cada marcador de spawn com entidade vinculada: se o chunk está carregado e a
  entidade sumiu (`getEntity(uuid)==null`), libera o marcador e inicia o cooldown de respawn
  (`RESPAWN_COOLDOWN_TICKS`, 60s). Vinculado em chunk descarregado = assumido vivo (não
  duplica).
- **Spawna** em marcadores livres que estejam (a) com chunk carregado, (b) com jogador a ≤
  `ACTIVATION_DISTANCE` (72 blocos), (c) num piso válido (pés+cabeça livres, chão sólido embaixo,
  **fora de fluido** — não nasce no lodo tóxico), (d) abaixo dos limites. Limites: global
  (`GLOBAL_CAP` 200) + por tipo (civil 120, worker 60, guardsman 60), contando marcadores
  ocupados (inclusive os de chunk descarregado, pra roaming não estourar o teto). No máximo
  `SPAWNS_PER_PASS` (6) novos por avaliação → a primeira ativação sobe em rampa, não de uma vez.
- Reusa as entidades existentes exatamente como o `ImperialPopulationManager` faz (create →
  moveTo → addFreshEntity) + `setPersistenceRequired` (o manager, não o despawn vanilla, controla
  o ciclo de vida). O UUID vai pro `HiveCityMarkerData` (persistido), então sobrevive
  save/reload e não re-spawna duplicado.

**Mapa desta fatia (conservador de propósito):** `CIVIL_SPAWN`/`WORKER_SPAWN` → `imperial_citizen`,
`GUARDSMAN_SPAWN` → `guardsman`. **Adiado para a próxima fatia** (pra manter revisável e não
mis-spawnar): `ENEMY_SPAWN` (precisa do gate de invasão/estado de setor), `COMMANDER_POINT`
(comandante único), mapeamento Skitarii/Enforcer, e as patrulhas realmente andando pelas rotas de
`PATROL_POINT`. Os NPCs desta fatia nascem sem command core (estado válido — `isUnemployed` já é
tratado) e usam a IA padrão deles; afinar comportamento por distrito é trabalho da próxima fatia.

**Comandos novos (§18):** `/fchive population status` (contagem viva por tipo + manager on/off),
`/fchive population enable|disable`, `/fchive population clear` (remove **só** os NPCs vinculados a
marcadores da Hive — nunca mobs do jogador).

**Verificado estaticamente:** compila (`BUILD SUCCESSFUL`). **Falta teste em jogo:**
`/fchive city build_full_test`, ir até um distrito, ver civis/trabalhadores/guardas nascerem perto
(e parar nos limites), matar um e ver respawn após 60s, `/fchive population status`, salvar/recarregar
e confirmar que não duplicam.

---

## 2p. Trilho pelo portão principal + Python no ambiente (ESTA ENTREGA)

**Contexto:** o fix de colisão (§2n) flutuou os trilhos (o `metal_floor_plate` que servia de leito
do trilho virou fino) — **isso foi resolvido só revertendo o bloco** (leito cheio de novo, trilho
apoiado). Sobrou a 2ª queixa do dono: *"a linha do trilho vai para lugar nenhum, não tem um portão
de verdade"* — o trilho de carga E-O corria pelo pátio e não tinha destino.

**Python instalado no ambiente:** antes só havia o stub da Microsoft Store. Instalei o Python
3.12.10 (`winget install Python.Python.3.12`, user scope) em
`%LOCALAPPDATA%\Programs\Python\Python312\python.exe` + Pillow (previews). Isso destrava a
regeneração dos templates NBT (os geradores são Python). **Verificação de segurança:** rodei o
gerador sem mudanças e comparei o conteúdo (via `NbtInfo` — tamanho/paleta/contagem/hash de
conteúdo) contra o commitado → **idêntico**; a única diferença de bytes é o timestamp do gzip. Ou
seja, regenerar é seguro e determinístico — só muda o que eu editar.

**Fix (escolha do dono: "passar pelo portão principal — rua+trilho"):** editei
`tools/gen_hive_south_gate_v2.py` — uma linha principal N-S de trilho desce pelo centro da rua
(x=95, na faixa de `metal_floor_plate`, não na grade) e **sai pela cidade atravessando o portão
principal**, cruzando as duas linhas E-O do pátio (passagem de nível). Colocada por último no
builder (depois do túnel do portão ser escavado), com guarda que só preenche células livres do
chão/túnel — nunca apaga estrutura. Como o builder vai de z=0 a 127, o slice põe o conector no
`cargo_yard_01` (z 0–63) e o trilho-através-do-portão no `south_ash_gate_01` (z 64–127).

**Verificado:** regenerado, **validação estática 49/49 costuras OK, 0 erros**; a prévia
`section_gate_x95` (o próprio plano x=95 do trilho) mostra o trilho contínuo em y=2 atravessando o
túnel do portão e saindo ao sul. Conteúdo confere: `cargo_yard_01` +62 células, `south_ash_gate_01`
+64. **Diff limpo:** só esses 2 NBTs + o gerador (os outros 4 módulos foram restaurados — só tinham
ruído de gzip). **Falta teste em jogo.**

**Ressalva (reuso de módulo):** `cargo_yard_01` também é usado pelo distrito `hive_wall_line`
(mesmo NBT). Lá, a nova linha N-S corre até a borda sul do módulo de cargo (z=63) e para no pátio
do módulo de muralha (a massa da muralha fica mais ao sul, z≈88+, então **não** encosta numa parede
sólida — termina num pátio). É um toco menor, consequência do módulo compartilhado; o dono pediu
"só o portão principal", então não abri portões de trilho nas muralhas. Se incomodar, dá pra pôr um
batente de fim de linha (buffer) ou um módulo de cargo separado pro gate — anotado.

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

- **2026-07-20 — Trilho pelo portão + Python:** Python 3.12 instalado no ambiente (destrava
  regenerar templates NBT; round-trip confirmado idêntico ao commitado exceto timestamp gzip).
  `gen_hive_south_gate_v2.py`: linha N-S de trilho desce a rua central e sai pelo portão principal
  (rua+trilho), regenerado `cargo_yard_01`+`south_ash_gate_01`, 49/49 costuras OK. Ver §2p.
- **2026-07-20 — FASE 12 (fatia 1):** `HivePopulationManager` — cidade viva a partir dos
  marcadores persistentes: civis/trabalhadores (`imperial_citizen`) e guardas (`guardsman`)
  nascem perto do jogador, com limites global+por tipo, cooldown de respawn, validação de piso
  (fora do lodo) e vínculo de UUID persistido (sem duplicar no reload). Só roda em `hive_world`,
  sem varredura global. Comandos `/fchive population status|enable|disable|clear`. Adiado:
  inimigos/comandante/patrulhas andando. Ver §2o.
- **2026-07-20 — Colisão concept set:** só `cathedral_stair_block` virou colisão real
  (`HiveStairShapeBlock`, degraus por FACING — é escada de verdade). A tentativa de afinar os 7
  "pisos" (metal_floor_plate, cathedral_floor_tile, etc.) foi **revertida**: eles são a superfície
  estrutural do chão (não slabs) e afiná-los flutuava os trilhos + desnivelava o chão. Piso fino de
  verdade exige regenerar os templates (pipeline Python, indisponível aqui). Ver §2n.
- **2026-07-20 — FASE 11:** marcadores persistentes (`HiveCityMarkerData`, `SavedData` em
  `hive_world` — antes só a última colocação sobrevivia, agora toda a cidade) + 2 comandos
  novos (`validate markers`, `debug marker <tipo>`) + 2 validadores estáticos independentes
  (`tools/hive_city_validate.py` e a porta Java `tools/HiveCityValidate.java`, usada nesta
  entrega por falta de Python no ambiente) cobrindo referências distrito→módulo→template,
  blockstate/modelo/textura e costuras de socket. Primeira execução real achou e corrigiu um
  bug genuíno: os 3 módulos de `hive_wall_line` tinham sockets uniformes copiados do
  `hive_corner_bastion` em vez de casar com a fileira de cargo estabelecida — 49/49 costuras OK
  depois da correção (JSONs + fonte do gerador). Documentação retroativa de FASE 10 (geração
  completa da cidade — dimensão/layout/fila/ticker, já existia mas nunca tinha sido registrada
  aqui) e do rebuild V2 de todos os 8 distritos (Administratum, Hab Stacks, Manufactorum,
  Underhive, perímetro, Spire — confirmado por inspeção real das prévias PNG de QA, não só por
  nome de arquivo). Ver §2k/§2l/§2m.
- **2026-07-19 — Administratum V2 rebuild:** ver nota original abaixo; contexto completo em §2l.
- **2026-07-14 — FASE 9:** distrito Underhive (3 módulos, 192×128): coletor com água tóxica
  real, ruínas colapsadas, território de gangue. 8 blocos novos. 28 marcadores. Auditoria de
  referências nos 5 distritos OK. FALTA: FASE 10 (Spire + geração automática da cidade) —
  deixada para conversa nova por ser sistema grande de mundo/geração.
- **2026-07-14 — FASE 8:** distrito Administratum + Catedral (3 módulos, 192×128): nave
  monumental com órgão de canos e altar-mor, scriptorium de 4 andares, tribunal com celas.
  43 marcadores. + teste automático de referências de distrito (auditoria dos 4 OK).
  Próximo: FASE 9 (Underhive) ou FASE 10 (Spire + geração completa).
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

### 2026-07-19 — Administratum V2 rebuild

The production Administratum templates were rebuilt as a continuous 192×64×64 district and split back into the existing module IDs. The new version adds thick stepped masses, unequal archive towers, a cross-shaped cathedral with clustered roof crown, flying buttresses, a broad tribunal palace, elevated galleries and detailed occupied interiors. Generator: `tools/gen_hive_administratum_v2.py`.
