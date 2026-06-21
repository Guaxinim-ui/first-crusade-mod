# First Crusade — STATUS (leia isto primeiro ao reorientar)

> Arquivo de orientação do agente. Quando o chat for limpo (`/clear`), **leia este arquivo
> primeiro** para retomar o contexto. **Mantenha-o atualizado** ao fim de cada bloco de trabalho
> (estado atual + metas + changelog).
>
> ⚠️ O dono do projeto também desenvolve em paralelo (commits via "da run"/"da run pfb"). Antes de
> mudanças grandes, **verifique o estado real** com `git log --oneline -8`, `git status` e um
> `Glob` em `src/main/java/com/example/examplemod/*.java` — não confie só neste arquivo.

Última atualização: **2026-06-20** · branch `main` · remoto `github.com/Guaxinim-ui/first-crusade-mod`

---

## 1. O que é o mod

Forge 1.20.1, Java 17, pacote `com.example.examplemod`, mod id `firstcrusade`. Civilização
imperial (Warhammer 40k) que vive e guerreia sozinha: cidades autônomas, cidadãos que trabalham,
tropas que patrulham/defendem, líderes (Primarca/Warboss) que marcham contra ameaças. Visão de
longo prazo: dois impérios (Imperium e WAAAGH!) donos do mundo, muitos tipos de cidade e clãs Ork,
e viagem planetária substituindo Nether/End.

Documentos de design (referência):
- `docs/DESIGN_WORLD_CITIES_FACTIONS.md` — **visão mestra**: 14 tipos de cidade, 15 clãs Ork,
  overlords, sistema de ameaça, mundo/dimensões. Roadmap em fases A–E.
- `docs/DESIGN_W40K_AUTONOMY.md` — pirâmide imperial e sistemas autônomos.

## 2. Build & teste (rede bloqueada no ambiente do agente)

O wrapper `./gradlew` falha (sem rede). Use o Gradle 8.8 em cache, com `-p` apontando para a **raiz
do projeto** (`/c/Users/hrlup/Documents/first-crusade-mod`) e `--offline`, com `dangerouslyDisableSandbox: true`.
⚠️ O projeto **NÃO** está mais em `/d/forge-mdk` (drive D: indisponível) — é autocontido aqui (tem
build.gradle/gradlew/settings.gradle). O hash do dir do Gradle em cache pode variar; descubra com
`ls -d ~/.gradle/wrapper/dists/gradle-8.8-bin/*/gradle-8.8/bin/gradle`.

```
G=/c/Users/hrlup/.gradle/wrapper/dists/gradle-8.8-bin/dl7vupf4psengwqhwktix4v1/gradle-8.8/bin/gradle
P=/c/Users/hrlup/Documents/first-crusade-mod
"$G" -p "$P" compileJava --console=plain --offline   # ~10s a 1 min
"$G" -p "$P" build --console=plain --offline         # gera o jar
"$G" -p "$P" runClient --console=plain               # abre o jogo (background)
```

**Como o dono testa (IMPORTANTE):** ele joga via **`runClient`** (ambiente dev, mapeamento `official`/
nomeado). Nesse fluxo o mod é carregado **das classes compiladas** — basta `compileJava`/`build` e
depois `runClient`. **NUNCA** colocar o jar do próprio mod em `run/mods`: o jar de `build/libs` é
**reobfuscado (nomes SRG, ex. `f_279569_`)** e no dev isso gera `NoSuchFieldError` no `<clinit>` do
`ExampleMod` (já aconteceu — crash 2026-06-18_10.59). `run/mods` é só para **outros** mods.
O jar reobf (`build/libs/firstcrusade-0.1.0.jar`) só serve para uma instância de **produção** real
(CurseForge/Prism/etc.) — nenhuma foi localizada no sistema.

Jar: `build/libs/firstcrusade-0.1.0.jar`. Warnings de `ResourceLocation` deprecado e LF→CRLF são
normais. Sempre compilar após mudanças; rodar `build` antes de pedir teste.

## 3. Estado atual (implementado)

**Recursos:** Iron, Coal, Scrap, Gold, Emerald, Crusadium, Emperor Gene Seed, Food.
Storage centralizado em `ImperialResourceStorage`.

**Estruturas (Block + BlockEntity + Manager):** Imperial Command Core, Mine, Gold Mine, Scrap
Yard, Forge, Promethium Refinery, Farm, Emerald Trade Depot, Barracks, Habitation, Ork Camp.

**UI do Core:** tela com abas (City / Build / Military / Defense / Resources), com depósito e
withdraw por recurso (`ImperialCommandCoreScreen` + `...Menu` + `...Action`/`...ActionPacket`).

**Unidades imperiais:** Imperial Citizen (jobs), Guardsman (ranks, especializações, chapters),
Space Marine (estágio Neophyte que amadurece), Custodes (guarda dourada do Core), Primarch
(gigante, aura de liderança, governa a cidade, luto), **Roboute Guilliman** (Primarca nomeado,
modelo/renderer próprios), **Skitarii Ranger** (tropa-tema da Forge City, atirador standalone com
Lasgun, **recrutado pela Forge City**), **Kasrkin** (tropa-tema elite da Fortress City, Militarum
Tempestus, hotshot lasgun, **recrutado pela Fortress City**), **Enforcer** (tropa-tema melee da Hive
City, Adeptus Arbites, shock maul/command baton, **recrutado pela Hive City** — 1º melee standalone),
**Mine Guard** (tropa-tema melee tanky da Mining City, bruiser lento), **Agri Militia** (tropa-tema
atiradora leve/ágil da Agri City), **Sister of Battle** (tropa-tema atiradora zelota da **nova Shrine
City**, Adepta Sororitas). Todas estendem `AbstractImperialTroopEntity` (vínculo ao Core, faction,
NBT, morte, guard post e goals comuns; subclasse só tem `registerCombatGoals()`). **Tipos de cidade: 10**
(CIVILISED, HIVE, FORGE, FORTRESS, AGRI, MINING, **SHRINE**, **PENAL**, **DEATH_WORLD**, **FEUDAL**).
**Só Civilised usa Guardsman baseline**; os outros 9 têm tropa-tema própria: Forge→Skitarii,
Fortress→Kasrkin, Hive→Enforcer, Mining→Mine Guard, Agri→Agri Militia, Shrine→Sister of Battle,
Penal→Penal Legionnaire, Death World→Jungle Fighter, Feudal→Feudal Knight. As tropas-tema
**recrutam, reforçam (`callImperialReinforcements`) e patrulham** (via `ImperialPatrolManager`) como
os Guardsmen. O tipo é **enviesado pelo bioma** ao fundar (`pickCityTypeForBiome`) e dá **+1 capacidade
na estrutura-tema** (`specialtyBonus`: Mining→Mina, Fortress→Barracks, Hive→Scrap Yard, Forge→Forja,
Agri→Farm).

> **Texturas:** todas as tropas-tema usam placeholder = cópia de `guardsman.png`. O dono fará a arte
> de cada uma; ao criar tropa nova, só copiar guardsman.png como placeholder (não gerar recolor).

**Unidades Ork:** Ork Boy, Ork Nob, **Warboss** (líder, espelho do Primarca; surge do camp após 3
warbands e marcha sobre a cidade).

**Sistemas/managers:** População, Workforce, WorkSite, **Moral** (`ImperialCityMoraleManager`),
**Patrulhas** (`ImperialPatrolManager`), Defesa, **Ameaça** (`ThreatAssessmentManager`, score
quantidade×qualidade, níveis 0–4, na UI), Custodes, Primarch (com **exército pessoal**/comitiva),
Ork Camp, Facções (`FirstCrusadeFactionManager`: IMPERIUM/ORKS/HOSTILE/PLAYER/NEUTRAL), Raid,
SpaceMarineUpgrade, **tipos de cidade** (`ImperialCityType`), **clãs Ork** (`OrkClan`),
`ImperialCityLevelStats`.

## 4. Metas / Roadmap

Fonte: `docs/DESIGN_WORLD_CITIES_FACTIONS.md` (fases A–E). Marque o que concluir.

- [x] **Fase A** — fundações estratégicas: tipos de cidade + clãs (sabor), Threat Score numérico,
  exército pessoal do líder.
- [x] **Fase B COMPLETA** — profundidade Ork: **Warboss** (líder), **Meganob** (elite), **Gretchin**
  (bucha), **Killa Kan** (máquina, tier 3+), **WAAAGH! Overlord** (`WaaaghOverlordData` SavedData
  global + `WaaaghOverlordManager`: cresce com a prosperidade imperial, tier 0-4 com anúncio global,
  escala todos os camps), e **warbands por clã** (Goffs/Bad Moons/Deathskulls/Evil Sunz/Snakebites).
- [x] **Fase C — essencialmente COMPLETA** (detalhes no changelog §7). Resumo: **10 tipos de cidade**,
  9 com **tropa-tema standalone própria** (sobre `AbstractImperialTroopEntity`) que recruta/reforça/
  patrulha; regimento de combate + custo de recruta + bônus de rank por tipo (no Guardsman);
  tipo enviesado pelo **bioma**; **+1 capacidade na estrutura-tema** por tipo de foco. Falta de Fase C
  (opcional, menor valor): estruturas realmente distintas por tipo (não só capacidade), e GUI mostrar
  a identidade do tipo/tropa.
- [~] **Fase D INICIADA** — overlords globais. **Feito (1ª fatia):** **Imperium Overlord** (a Cruzada
  global) — `ImperiumOverlordData` (SavedData no overworld) + `ImperiumOverlordManager`, espelho do
  WAAAGH!: cresce com a prosperidade das cidades (tier 0-4, anúncio global por chave de lang), e o
  tier **aumenta os reforços** de toda cidade (`getReinforcementCount += tier`). Agora há os dois
  overlords (Imperium × WAAAGH!) crescendo em paralelo. **2ª fatia:** **despacho de líder por ameaça** —
  no nível CRÍTICO o Primarch carrega contra o inimigo mais forte (`leadCriticalCounterCharge` +
  `ThreatAssessmentManager.findStrongestEnemy`). **3ª fatia:** Cruzada visível na GUI do Core (slot
  ContainerData 56). **4ª fatia:** simetria Ork — Warboss se ergue mais cedo conforme o tier do
  WAAAGH! (`requiredWarParties = max(1, 3 - tier)`). **5ª fatia:** território da cidade
  (`getTerritoryRadius` escala com nível; usado no counter-charge do Primarch + exibido na GUI).
  **Falta só:** **geração no worldgen** (structure features — maior risco, por último).
- [ ] **Fase E** (maior risco) — mundo achatado + menor + dimensões-planeta substituindo Nether/End
  + viagem planetária (via Spaceport). **Planetas pequenos/fechados, SEM mineração/quebra de blocos**
  (jogo vira comando, não extração) + **Mesa de Guerra** (tela do Core vira mapa tático com fichas de
  aliados/inimigos e ícones de invasão/defesa). Detalhes em `DESIGN_WORLD_CITIES_FACTIONS.md` §5/§5.1.
  Ideia do dono (2026-06-19), para o fim de tudo.
- [ ] **Transversal** — conteúdo (armas/armaduras/recursos por facção) para não ficar entediante.

### >>> HANDOFF / PRÓXIMO PASSO (retomar aqui após o /clear) <<<

**🏰 FASE F — VILAS VIVAS (nova, pedido do dono 2026-06-20):** o mundo deve ter **vilas reais**
com o **Core = castelo central**, **muralha em volta da vila toda** que **cresce** (mais casas+camas)
conforme a cidade evolui; **cidadãos dormem em camas → nascem crianças**; e **só crianças escolhidas**
viram aspirantes → estágios de **implante de órgãos** + **teste em batalha** → Space Marine.
Plano em 3 fatias:
- **F1 — Vila real (FEITO, NÃO testado em jogo):** já existia `buildCityStructure` (fundação +
  muralha em volta + torres de canto + casas + estrada + torre central, escalando por nível
  `getCityStructureRadius` 4/8/12/18/26 e `getCityWallHeight` 1/3/5/7/9), mas só rodava no **upgrade**
  do jogador e as casas **não tinham cama**. Agora: (1) `buildSimpleHouse` ganha **camas** (`RED_BED`
  foot+head, 1 por casa / 2 se largura≥6) + lampião interno; (2) novo `buildAutonomousVillage()`
  público no Core (sobe a cidade pro nível 3 e chama `buildCityStructure`); (3) o **seeder B5**
  (`WorldSettlementSeeder.foundCity`) agora coloca o Core e chama `buildAutonomousVillage` — cidades
  do mundo nascem como **vila murada nível 3** (castelo central + muralha + 2 casas com cama), em vez
  da plaza simples. A muralha do jogador **já cresce** ao dar upgrade (plate). Build/jar OK.
- **F2 — dormir → filhos (PRÓXIMO):** `ImperialCitizenEntity` precisa de goal de **dormir** (achar
  cama livre/POI `home` à noite, deitar) e o nascimento em `ImperialPopulationManager.tickCitizenGrowth`
  passar a exigir **cama livre + comida + 2 adultos** em vez de só timer+capacidade. Camas já são
  colocadas em F1 (registram POI). Spawnar **criança** (citizen com flag baby/idade) que cresce.
- **F3 — aspirante → Space Marine:** só **algumas crianças** marcadas como aspirantes; pipeline com
  estágios de implante (reusar a maturação do Neófito em `SpaceMarineEntity`) + **teste em batalha**
  antes de virar SM. Hoje é Guardsman→SM direto (`processAutomaticSpaceMarinePromotion` +
  `SpaceMarineUpgradeManager`). Repensar como child→aspirante→neófito(implantes)→prova→Space Marine.

**>>> AÇÃO IMEDIATA F1:** dono testa em **MUNDO NOVO** (git pull + da run): as 3 cidades do mundo
nasceram como **vila murada** (muralha gótica em volta, Core no centro, casas COM cama)? ficou bom o
visual/escala? deu erro (ler `run/logs/latest.log`)? Tunável: nível inicial em `AUTONOMOUS_VILLAGE_LEVEL`
(3) e geometria em `getCityStructureRadius`/`getCityWallHeight`/posições de casa em `buildCityStructure`.
Depois OK do dono, seguir pra **F2 (dormir→filhos)**.


**Estado geral (tudo compilando, em origin/main):**
- **Fases A, B, C** maduras. **Fase D essencialmente completa**: overlord Imperial (Cruzada,
  `ImperiumOverlordData`/`ImperiumOverlordManager`, tier 0-4, soma reforços) × WAAAGH! em paralelo;
  despacho de líder por ameaça (Primarch counter-charge no nível CRÍTICO); Cruzada na GUI; Warboss
  escalado pelo tier; território da cidade; **mundo se popula via propagação do WAAAGH!** (camp planta
  1 camp-filho, `OrkCampManager.seedSpreadCamp`).
- **i18n essencialmente completa**: GUI do Core + todas as mensagens + **todas as broadcasts** em
  `Component.translatable` (en/pt **sincronizados**, ~340 chaves). Validar contagem com PowerShell
  ConvertFrom-Json. Pendências só de strings cross-class passadas como `%s` (rank/clã/especialista/
  ameaça/moral) e nomes custom de entidade.
- **Armas** (todas com molde reusado + receita + lang en/pt; arte = PLACEHOLDER, dono faz):
  Imperium: Lasgun (modelo 3D+textura do dono), **Plasma Gun** (queima), **Bolter** (knockback);
  melee **Chainsword** (SM + Primarch a usam). Orks: **Choppa** (Boy/Nob), **Shoota** (dakka),
  **Power Klaw** (Warboss/Meganob).

**🌍 PLANETA (Fase E em andamento) — FUNCIONANDO e confirmado em jogo:**
- O **mod sela Nether/End** (`EntityTravelToDimensionEvent`) e aplica **worldborder 5000** no
  overworld (`onServerStarting`, `WORLD_BORDER_SIZE` em `ExampleMod`). Vale em qualquer mundo.
- **Worldgen do planeta vai EMBUTIDO no jar** em `src/main/resources/data/minecraft/`:
  `dimension_type/overworld.json` (min_y 0, height 96) e `worldgen/noise_settings/overworld.json`
  (terreno raso/plano, sem cavernas; clima temp/veg=0; continents/erosion/depth/ridges referenciam
  DFs vanilla). **NÃO há mais datapack externo** (a tela "Select Data Packs" só lista embutidos;
  por isso o externo nunca aparecia).
- **Como o dono testa:** `git pull` + **`da run`** → criar **MUNDO NOVO**, aba **World** →
  **Generate Structures: OFF** (tira vilas) + World Type Default → Create. (Mundos antigos de altura
  cheia NÃO carregam mais com o override — usar mundo novo.)
- **DEBUG de worldgen:** se a criação do mundo der erro, **ler `run/logs/latest.log`** (procurar
  "Failed to parse"/"Not a JSON object"/"Unbound"). Foi assim que achei os bugs: `temperature`/
  `vegetation` não existem como DF nomeada (usar constante); carvers exigiam `debug_settings` (foram
  removidos, redundantes); `monster_spawn_light_level` tinha que ser int, não objeto.

**>>> AÇÃO IMEDIATA ao retomar:** acabei de implementar **B5 (1ª fatia) — assentamentos no
worldgen**. **Compila e o jar builda, mas worldgen NÃO é testável aqui.** Pedir ao dono pra
`git pull` + `da run` + **MUNDO NOVO** + andar perto do spawn (até ~360 blocos) e dizer:
apareceram as cidades Imperiais (plaza de tijolo + Core central) e os camps Ork? quantos? ficaram
em terra seca (não no oceano)? deu erro (ler `run/logs/latest.log`)? Tunar contagens/raio em
`WorldSettlementSeeder` (CITY_COUNT/CAMP_COUNT/MIN_RADIUS/MAX_RADIUS) conforme o gosto.
⚠️ Só semeia **uma vez por mundo** (flag `WorldSettlementData`), no **primeiro login** — mundo já
existente não vai semear; testar em mundo novo.

**Detalhe do que B5 faz:** no 1º login (`ExampleMod.onPlayerLoggedIn`), `WorldSettlementSeeder.
seedAroundSpawn` semeia num anel ao redor do spawn (140–360 blocos): **3 cidades Imperiais** (coloca
o `IMPERIAL_COMMAND_CORE` sem dono numa fundação de tijolo/andesito + pilares com lanterna/estandarte;
o Core se autogoverna — atribui tipo pelo bioma e cresce sozinho) e **3 camps Ork** (reusa novo
`OrkCampManager.seedWorldCamp`, cada um marchando na cidade mais próxima). Filtra terra seca (sem
fluido na superfície/abaixo) e separação mínima (80 blocos). Só `setBlock`, sem mexer em chunk-gen
(reversível, não corrompe mundo). Cidade gerada é **autônoma e não-reivindicada** — o dono pode
clicar pra virar dono (`setOwner`) e abrir a GUI.

**Depois de B5 (lista de próximos, por prioridade):**
1. **B5+** (afinar): tunar contagens/raio após o teste do dono; talvez prédios iniciais reais
   (Barracks/Habitation) na fundação além do Core; marcar origem `WORLD_GENERATED` no Core se útil
   pra balanceamento (BE hoje não guarda origem; enum `ImperialSettlementOrigin` existe, é legado).
2. **A3** (opcional): restaurar variedade de clima (temperatura/umidade reais via noise válido) p/
   desertos/neve — hoje é tudo temperado (temp/veg=0).
3. **C6** (grande): planetas como **dimensões próprias** + viagem planetária (Spaceport) — Fase E real.
4. **D7**: **Mesa de Guerra** (GUI-mapa tático no Core) — ver `DESIGN_WORLD_CITIES_FACTIONS.md` §5.1.
5. Texturas (dono) + balanceamento.

(A1+A2 do terreno — ondulações/areia — ficou como está; o dono pediu pra deixar plano por hora.)

**Regras ao continuar:** reusar padrões existentes (tropa-tema, manager por sistema, lang en/pt
sincronizadas). Dono faz texturas. Compilar offline (§2) e **commitar/push a cada fatia**. Worldgen
não dá pra testar aqui → mudança pequena + dono testa + ler `run/logs/latest.log` se falhar.

## 5. Convenções de arquitetura (seguir)

1. Um **manager** por sistema; **nunca** inchar `ImperialCommandCoreBlockEntity`/`OrkCampBlockEntity`.
2. Estrutura física = Block + BlockEntity + Manager + registro em `ExampleMod` + assets (blockstate,
   modelos block/item, lang) + NBT.
3. **Reusar** `GuardsmanEntity`/`OrkBoyEntity` (ranks/variantes) antes de criar entidade nova.
4. Entidade nova precisa: classe, renderer, textura (placeholder = cópia de outra `entity/*.png` se
   não houver arte), registro (entity type + atributos + renderer + spawn egg), faction em
   `FirstCrusadeFactionManager.getFaction`, lang, NBT.
5. Métodos pequenos; sem import `main.java...`; sempre `clean`/`compile` após mudar.

## 6. Acordo de trabalho com o dono

- Falar **português**.
- Trabalhar em fatias testáveis; compilar e (quando pedido) rodar o jogo.
- **Push ao GitHub em checkpoints** e **sempre que estiver perto do limite de contexto** (`git add -A;
  commit; git push origin main`). O dono comita direto em `main`.
- Mensagens de commit em pt-BR, terminar com `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`.

## 7. Changelog (mais recente no topo)

- 2026-06-21: **Hive — tetos abertos + tier 1 muito maior + escadas 1:1 que caem certo**. Feedback do
  dono (andares fechados/escuros; escadas não conectavam). (1) **Tetos abertos:** cada piso agora usa o
  **próprio raio** (`fillDisc(baseY[i]-1, R[i])` em vez de `R[i-1]`) → o anel do andar de baixo fica
  **aberto pro céu** (terraço), não vira salão escuro. (2) **Tier 1 muito maior:** raios
  {120,93,66,39,12} (T1=120 → **240 de lado**), com folga **27** entre anéis (≥ step 25 +2). (3)
  **Escadas retas 1:1 corretas** (`buildRampStairs` reescrito): sobe 1 bloco por passo da muralha
  externa pra dentro, **patamar + porta esculpida na muralha interna** no nível exato do próximo andar
  (a matemática fecha: `startD-rise = outerR-27 = innerR`). Face leste. Build/jar OK; **não testado**.
  ⚠️ Login bem pesado (T1=240 de lado, ~250k blocos). Mundo NOVO (teto 256).

- 2026-06-21: **Hive vertical — pontes radiais + escadaria direta + 4 portões + guardas**. Dono mandou
  o plano (corte lateral + planta redonda). Ajustes no `buildVerticalHive`: (1) `buildCircleWall` abre
  **4 portões cardeais** (N/S/L/O) por muralha (era só sul). (2) **`buildRadialBridges`**: 4 catwalks
  radiais (deck blackstone + corrimão iron bars + lanternas) no nível do T3 (y50) cruzando a hive — as
  "pontes verdes" pros soldados. (3) **`buildGrandStaircase`**: escadaria monumental (3 de largura +
  corrimão) subindo **1 por degrau de fora do muro oeste até o topo** (a "escada roxa", caminhável).
  (4) **`spawnGateGuards`**: posta um guarda (tropa-tema/Guardsman) em **cada portão de cada muralha**
  (4×5). Build/jar OK; **não testado** — pedir screenshot p/ ajustar (facing das escadas, altura das
  pontes). Mundo NOVO (teto 256).

- 2026-06-20: **HIVE VERTICAL — 5 tiers redondos empilhados (cada menor e +25 de altura)**. Pedido do
  dono (referência Hive Primus). **Teto do planeta subiu p/ 256** (`dimension_type` + `noise_settings`
  overworld e planet_secundus: height/logical_height 96→256; terreno segue plano ~y51, só ganha céu) —
  ⚠️ **mundos antigos não carregam, usar mundo NOVO**. Novo `buildVerticalHive` (substitui buildHiveCity
  nas autônomas): muralhas **redondas** (`buildCircleWall` midpoint), discos de piso (`fillDisc`),
  torres aninhadas subindo (silhueta de espira central), **escadas-rampa espiral** ligando tiers
  (`buildRampStairs`). Tiers: **T1** chão (worksites+casas, muralha robusta dupla), **T2** +25
  (menos casas + **pontes** `buildTierBridges`), **T3** +50 (**bibliotecas+casas de bruxa/alquimia**
  `placeScholarHouses`/`furnishLibrary`/`furnishWitchHouse`), **T4** +75 (**bastião** fechado de comando
  `decorateBastion`), **T5** +100 (**catedral+sala do trono** `buildCathedral` + espira no topo). Raios
  {62,48,36,24,14}. Seeder: **1 capital** (CITY_COUNT=1) + camps, anel 180–520. `buildHiveCity` e
  helpers de anel ficaram **sem uso** (warnings). Build/jar OK; JSON valida; **NÃO testado em jogo**.
  ⚠️ Login pesado (1 hive colossal). Pedir screenshot p/ iterar (escadas/telhados podem precisar ajuste).

- 2026-06-20: **HIVE CITY GIGANTE — 3 tiers sociais concêntricos (pedido do dono: algo gigante)**.
  Novo `buildHiveCity` (substitui `buildCityStructure` nas cidades autônomas; player ainda usa o fort).
  Três **anéis murados concêntricos = estratos sociais**: **Underhive** (externo: habs curtos/densos/
  fumegantes, cobbled deepslate), **Hive City** (meio: habs altos + manufactorums/worksites, deepslate
  brick) e **Spire** (centro: keep + espira **colossal**, gilded blackstone). Cada tier tem muralha/
  torres/portões próprios e **paleta de blocos distinta**; piso pavimentado mais rico ao centro
  (`buildHiveFloor`); avenidas iluminadas cruzando tudo. Escala **enorme**: `hiveOuterRadius = 40+nível*6`
  (nível 4 → raio 64 = **128 de lado**), espira gigante (clampada ao teto y~94 do planeta via
  `clampHeight`). Tiers via **anéis** (não terraços) porque o Core é fixo em y e o planeta tem teto
  baixo (96) — terraço enterraria o Core. Seeder: **2 cidades** (eram 3), separação 220, anel 180–520.
  ⚠️ **Custo de login alto** (2 hives gigantes geram no 1º login) — se travar, baixar `hiveOuterRadius`
  ou `CITY_COUNT`. Build/jar OK; **não testado em jogo**. Tunável: raios/tiers/paletas em `buildHiveCity`.

- 2026-06-20: **Hive city — chão pavimentado + verticalidade + cidadãos operando**. Pedido do dono
  (referências hive 40k). (1) **Chão arrumado:** `buildFoundation` agora **força** o piso de tijolo
  em todo o interior (antes não trocava a grama → interior virava gramado; só a rua era pavimentada)
  + sub-floor de cobbled sobre buracos. (2) **Verticalidade hive:** espira central **bem mais alta**
  (`wallHeight + 26 + nível*4`, sempre construída) + **espirais secundárias** (~1/9 das células viram
  torre 12–24 de altura) + **chaminés fumegantes** (`placeSmokestack`, campfire, ~1/3 das casas) →
  silhueta industrial/vertical. (3) **Cidadãos operam a cidade:** `placeCityWorksites` coloca
  **Fazenda/Mina/Forja/Ferro-velho** na praça, atribuídos ao Core → o `ImperialWorkforceManager`
  emprega cidadãos ociosos neles e os prédios produzem recursos (que financiam o auto-recrutar/
  evoluir). Build/jar OK; não testado em jogo.

- 2026-06-20: **Cidade menos quadrada — casas góticas com telhado inclinado (estilo 40k)**. Feedback:
  muito quadrada, casas caixas. Agora `buildSimpleHouse` é um **hab-block gótico**: paredes deepslate
  com **janelas-lanceta altas** (1 de largura, do y1 ao topo), **telhado gable inclinado** de
  `DEEPSLATE_TILE_STAIRS` (`buildGableRoof` ao longo do eixo maior, com beiral e empena preenchida),
  **pináculo dourado + end_rod** no cume, lanterna interna. `buildHousingDistrict` agora **varia
  footprint (5..7 × 5..7), altura (4..5) e orientação do telhado** por casa, e abre **pátios** (~1/6
  das células vira praça com poste) → bem menos grade. ⚠️ Stairs do telhado podem precisar **inverter
  facing** se aparecerem ao contrário (1 linha em `buildGableRoof`/`placeRoofStair`). Build/jar OK;
  não testado em jogo.

- 2026-06-20: **Cidades autônomas se governam + plataforma do Spaceport melhor**. (1) Cidades
  **não-reivindicadas** (sem dono) agora rodam sozinhas: `tickAutonomousGovernance` (no serverTick,
  só se `!hasOwner()`) → **auto-recruta** guarnição até `getMilitaryCapacity` pagando ferro/ramp
  gradual (`autonomousRecruit` + `spawnGarrisonTroop`, reusa tropa-tema/Guardsman+guard post), e
  **auto-evolui de nível** quando próspera (pop no cap + recursos, sem placa) reconstruindo a
  estrutura maior (`autonomousUpgrade`, broadcast `bcast.city_grew`). Vila do mundo nasce com
  **guarnição inicial de 6** (`spawnInitialGarrison` em `buildAutonomousVillage`). Cidade reivindicada
  pelo jogador volta a ser controlada por ele (governança autônoma só p/ unowned). (2) `SpaceportBlock.
  buildLandingPad` agora limpa vegetação + plataforma **7x7** com borda/lanternas + 4 blocos de
  headroom (antes 3x3 → caía num quartinho escuro). Lang 344/344. Build/jar OK; não testado em jogo.
  ⚠️ **Planeta ainda visualmente idêntico ao overworld** (reusa o mesmo gerador) — dá impressão de
  "mesma dimensão"; tornar planetas distintos (terreno/céu) é a próxima fatia do C6.

- 2026-06-20: **C6 (1ª fatia) — planeta-dimensão + viagem via Spaceport**. Fase E real começa.
  **Novo planeta como dimensão própria** `firstcrusade:planet_secundus` (data-driven:
  `data/firstcrusade/dimension/planet_secundus.json` gerador noise reusando o `noise_settings`
  **minecraft:overworld** já achatado + biome_source preset overworld; `dimension_type/planet_secundus.json`
  cópia do planeta: min_y 0, height 96). **Bloco `SpaceportBlock`** (registro SPACEPORT + item + aba
  criativa + blockstate/model cube_column textura lodestone + loot + lang en/pt): right-click **teleporta
  ida/volta** entre o overworld e o planeta (`ServerPlayer.teleportTo` cross-dim), construindo uma
  **plataforma de pouso 3x3 + Spaceport de retorno** no destino (nunca fica preso/cai). Chave
  `ExampleMod.PLANET_SECUNDUS` (ResourceKey<Level>). Build/jar OK; **NÃO testado** (dimensão só existe em
  **mundo NOVO**). Dono testa: pega Spaceport no criativo, coloca, clica → vai pro planeta? volta? deu
  erro ao criar mundo/registrar dimensão (ler `run/logs/latest.log`)? **Falta:** worldborder no planeta,
  recurso/receita do Spaceport, planetas distintos (terreno/tema próprios), assentamentos no planeta.

- 2026-06-20: **Cidade grande/funcional/bonita — layout planejado (pedido do dono)**. Feedback: queria
  o estilo gótico antigo + muralha em volta + cidade grande. Mantido o gótico (muralha + torres de
  cúpula dourada + torre central + castelo). Melhorias de layout em `buildCityStructure`: ruas mais
  **largas** (pitch 6+3), **praça central aberta** ao redor do castelo (`plazaHalf = CENTRAL_KEEP_HALF+4`,
  casas não invadem o centro), **postes de luz** (`placeLampPost`: muro+lanterna) em cada casa e
  ladeando as avenidas (`buildCentralRoad`) → cidade **acende à noite** (combina com o sono dos
  cidadãos), **alturas de casa variadas** (3/4) pra quebrar a uniformidade. Escala maior
  (`getCityStructureRadius` 8/15/22/30/40; vila do mundo = nível 4 → raio 30, ~61 de lado). Build/jar
  OK; **não testado em jogo** — dono testa em mundo novo e diz se a escala/estética ficou boa.

- 2026-06-20: **Fase F2a — cidadãos dormem → nascem crianças (cama/comida/pais gateiam)**.
  `ImperialCitizenEntity`: campo `childhoodTicks` (NBT) → `isChild()`/`isBaby()` (criança não trabalha,
  não treina, renderiza **menor** via `ImperialCitizenRenderer.scale` 0.6), cresce em ~1.5 dia
  (`CHILDHOOD_TICKS`); flag transitória `restingAtHome`. Novo `ImperialCitizenSleepGoal` (prioridade 2):
  à **noite** (13000–23000) o cidadão acha a **cama mais próxima** (BEDS foot, raio 28 do Core, scan
  throttled) e vai pra casa descansar (`setRestingAtHome`). `updateWorkRoutine` não puxa pro trabalho à
  noite/criança. `ImperialPopulationManager.tickCitizenGrowth` reescrito: capacidade = min(tipo,
  max(**nº de camas**, 3)) → **camas (e expansão da muralha) limitam a população**; nascimento exige **2
  adultos dormindo** (`countRestingAdults`) e gera **criança**; **bootstrap** (colonos adultos) só
  enquanto há <2 cidadãos. Build/jar OK; **não testado em jogo**. Próximo: **F3 aspirante→Space Marine**
  (só crianças escolhidas → implantes + teste em batalha). Futuro F2: pose de sono real; criança vira
  adulto com aviso.

- 2026-06-20: **Corrupção Ork (sculk) — 1ª fatia + limpa sculk solto do worldgen**. Ideia do dono:
  o sculk que aparecia na superfície vira a **praga Ork** que se espalha. Novo `OrkCorruptionManager`:
  (1) **camps irradiam corrupção** — halo de sculk que cresce a cada ciclo até um raio máx escalado
  pelo **tier do WAAAGH!** (`BASE_RADIUS 4 + tier*5`), convertendo só **chão natural** (dirt/grass/
  areia/pedra/etc., nunca estruturas/árvores) em `SCULK` (+ `SCULK_VEIN` ocasional); raio guardado em
  NBT `CorruptionRadius` no `OrkCampBlockEntity`. (2) **mortes alimentam a praga** — `LivingDeathEvent`
  em `ExampleMod`: onde um Ork morre/mata, semeia um patch de sculk (`corruptDeathSite`). (3) O sculk
  **solto do worldgen** (do bioma deep_dark raso) é removido via **biome modifier** Forge
  `data/firstcrusade/forge/biome_modifier/remove_stray_sculk.json` (remove placed features
  sculk_vein/sculk_patch_deep_dark/ancient_city de `#minecraft:is_overworld`) — data-driven, reversível.
  Build/jar OK; **não testado em jogo**. Dono testa: sculk solto sumiu? corrupção cresce em volta dos
  camps e nas batalhas? Tunável (raio/tier/blocos por ciclo em `OrkCorruptionManager`). Futuro: praga
  afeta unidades imperiais / pode ser purificada / espalha sozinha entre blocos.

- 2026-06-20: **Fase F1b — vilas grandes + população + nascem no CHÃO (não em árvores)**. Feedback do
  dono: ficou fortaleza vazia em cima de árvores. Agora: (1) `buildCityStructure` reescrito —
  **bairro de casas em grade** (`buildHousingDistrict`, ~10-15 casas com cama, ruas de 2 blocos) +
  **castelo central** (`buildCentralKeep`, muralha quadrada com portão em volta do Core) + cross-road
  sempre; raios **bem maiores** (`getCityStructureRadius` 8/14/20/26/34). (2) Vila do mundo nasce
  **nível 4** (`AUTONOMOUS_VILLAGE_LEVEL`) com **12 cidadãos** já dentro (`spawnStartingPopulation`).
  (3) **Novo `WorldGenPlacement`**: `groundPlacement` acha o **chão real** (ignora troncos/folhas/
  plantas — antes plantava no topo das árvores) e `clearVegetation` **limpa o perímetro** (footprint +
  margem 4, altura wallHeight+16) de árvores/folhas/plantas/neve. Usado por cidade (no `buildCityStructure`)
  e por **camps Ork** (`OrkCampManager`: surface no chão + limpa raio+2). Build/jar OK; dono testa em
  mundo novo. **Obs:** vilas só nascem perto do spawn (anel 140-360) — espalhar pelo mundo é fatia futura.

- 2026-06-20: **Fase F1 — vilas reais no mundo (Core = castelo central + muralha + casas com cama)**.
  Já existia `buildCityStructure` (fortaleza gótica escalando por nível), mas só no upgrade do jogador
  e sem camas. Agora: `buildSimpleHouse` coloca **camas** (`RED_BED` foot+head + lampião); novo
  `ImperialCommandCoreBlockEntity.buildAutonomousVillage()` (nível→3 + `buildCityStructure`); o seeder
  B5 (`WorldSettlementSeeder.foundCity`) chama-o → cidades do mundo nascem como **vila murada nível 3**
  (substituiu a plaza simples). Separação entre assentamentos subiu p/ 96 (vilas ~25 blocos). Build/jar
  OK; **não testado em jogo** (worldgen) — dono testa em mundo novo. Próximo: **F2 dormir→filhos**.

- 2026-06-20: **Fase D/B5 (1ª fatia) — assentamentos no worldgen (planeta começa povoado)**. Novo
  `WorldSettlementSeeder` + `WorldSettlementData` (SavedData, flag `Seeded`): no **primeiro login**
  (`ExampleMod.onPlayerLoggedIn`) semeia, **uma vez por mundo**, **3 cidades Imperiais** + **3 camps
  Ork** num anel de 140–360 blocos ao redor do spawn. Cidade = `IMPERIAL_COMMAND_CORE` (sem dono,
  autônomo) sobre fundação de tijolo/andesito com pilares (lanterna+estandarte azul); camp via novo
  `OrkCampManager.seedWorldCamp` (planta no ponto via `plantCamp` dist 0), mirando a cidade mais
  próxima. Filtra terra seca (fluido na superfície/abaixo) + separação mín. 80. **Só `setBlock`, sem
  alterar chunk-gen** → reversível, não corrompe mundo (abordagem segura, sem structure features).
  Build/jar OK. **NÃO testado em jogo** (worldgen): dono testa em **mundo novo** (git pull + da run +
  andar perto do spawn) e reporta.

- 2026-06-20: **Planeta A1+A2 (ondulações + areia) — COMMITADO, NÃO TESTADO em jogo ainda**.
  `noise_settings/overworld.json`: `final_density` (e initial) = gradiente Y (from_y44/to_y64) +
  `continents`*0.4 + `erosion`*0.2 → terreno com **ondulações leves** e **oceanos** (cai abaixo do
  mar onde continents é baixo), sem montanhas. `surface_rule`: grama na terra, **areia** em praia/
  fundo d'água (via `minecraft:water` offset -1), dirt no subsolo. JSON valida, jar builda. Próximo:
  dono testa (git pull + da run + mundo novo) e afina amplitudes (mul 0.4/0.2) / from_y-to_y.

- 2026-06-20: **Planeta (B) FUNCIONANDO** ✅ (confirmado em jogo pelo dono). Overworld embutido no mod:
  **plano** (superfície ~y51), **baixo** (height 96), **sem cavernas**, biomas variados por
  continentalidade/erosão (temp/veg constantes), **worldborder 5000** ativo, **Nether/End selados**,
  vilas off via "Gerar Estruturas OFF" na criação. Mundo novo Default + estruturas off = planeta.
  Próximos: ondulações leves no terreno, superfícies por bioma (areia/etc.), geração de assentamentos
  no worldgen, e (grande) planetas como dimensões + viagem (Spaceport).

- 2026-06-20: **Planeta (B) v3 — worldgen EMBUTIDO no mod** (datapack externo removido). Movido o
  override de `data/minecraft/{dimension_type/overworld, worldgen/noise_settings/overworld,
  worldgen/configured_carver/*}` para `src/main/resources` → agora vai no jar (parte do "main"). O
  dono não precisa mais arrastar datapack: `da run` + **mundo NOVO** já nasce planeta (plano, baixo
  height 96, sem cavernas). **Vilas**: ainda criar mundo com **"Gerar Estruturas" OFF** (1 clique).
  ⚠️ **MUNDOS ANTIGOS (altura cheia) provavelmente NÃO carregam** com este override — usar mundo novo.
  ⚠️ noise_settings escrito à mão; se a criação do mundo der erro, reportar a mensagem p/ corrigir.
  (Antes: tela "Select Data Packs" não mostrava o datapack externo — só embutidos; daí o impasse.)

- 2026-06-20: **Planeta (B) v2 — noise_settings plano (sem montanhas/cavernas de ruído)**. Datapack
  `small_planet` agora sobrescreve `worldgen/noise_settings/overworld.json`: `final_density` =
  gradiente Y (solo até ~y65, ar acima) → **terreno plano, sem cavernas de ruído**; referencia o
  clima vanilla (`minecraft:overworld/temperature|vegetation|continents|erosion|depth|ridges`) p/
  manter **biomas/árvores**; aquifers/veins off; surface_rule simples (bedrock+grama+terra). JSON
  valida. **Vilas**: instruir o dono a criar mundo com **"Gerar Estruturas" OFF** (infalível).
  ⚠️ Worldgen escrito à mão e **não testável aqui** — dono cria mundo novo Default e reporta erro
  exato se houver (não corrompe: mundo novo). Antes: v1 (carvers+altura) parecia não aplicar —
  confirmar `/datapack list`.

- 2026-06-20: **Planeta (B) v1 — datapack: cavernas escavadas off + altura baixa**. No datapack opt-in
  `small_planet` (mundo NOVO): overrides dos carvers `cave`/`cave_extra_underground`/`canyon` com
  `probability 0` (sem cavernas escavadas nem ravinas) + dimension_type raso (min_y 0/height 128).
  JSONs validados. **Falta v2** (mexe em `noise_settings`, iterativo+testado): cavernas de **ruído**
  (cavernas abertas 1.18) e **achatar montanhas**. Dono testa em mundo novo Default e reporta erros.
  (Mod já sela Nether/End e aplica border 5000 — qualquer mundo.)

- 2026-06-20: **Mundo "planeta" — Nether/End selados + datapack de altura**. (1) Código (qualquer
  mundo, sem risco): `EntityTravelToDimensionEvent` cancela viagem para `Level.NETHER`/`Level.END`
  (portais não levam a lugar nenhum) — a Cruzada é só na superfície. (2) Datapack **opt-in**
  `datapacks/small_planet/` (NÃO embutido no jar, p/ não quebrar mundos): overworld raso
  (`dimension_type` min_y 0, height 128). README recomenda criar mundo **Superflat** para terreno
  plano/sem cavernas/sem montanhas (confiável, sem worldgen frágil) + este datapack p/ baixar o teto;
  o mod cuida de border 5000 e Nether/End. Terreno NATURAL achatado (noise_settings custom) fica para
  fatia futura, a testar. Compila OK.

- 2026-06-20: **Fase D — mundo se popula sozinho: propagação do WAAAGH!** (abordagem SEGURA, sem
  structure features/datapack que poderiam quebrar mundos). A maré verde se espalha: um camp
  estabelecido planta **um** camp-filho mais distante (`OrkCampManager.seedSpreadCamp` + `plantCamp`
  generalizado), com o mesmo alvo. Gatilho no tick do camp (`trySpreadWaaagh`): só com WAAAGH! global
  **tier ≥ 2**, **~1/12** por ciclo, e **no máximo 1 filho por camp** (flag `hasSpread`, em NBT). Não
  planta sobre outro camp. Assim os Orks expandem pelo mundo gradualmente, sem corromper chunks.
  Build/jar OK. **Tunável** (tier/chance/distância); sem hard-cap global — se crescer demais, fácil
  adicionar teto via `WaaaghOverlordData`.

- 2026-06-20: **i18n — notificações broadcast COMPLETAS (fatias 3 e 4)**. Migrados todos os broadcasts
  restantes para `Component.translatable`: ciclo de raid do Core (dano/ativo/dispersão/vitória+
  recompensas/derrota+perdas), recruta juntou-se, candidato/ascensão Space Marine, gene consumido,
  raid duplicado; e os de entidades/managers (Custodes surge/tomba, Primarca tomba, Neófito começa,
  Warboss morto, camp Ork erguido). **0 `notifyNearbyPlayers`/`notifyDefenseCommand` com String
  literal restante.** Chaves `msg.firstcrusade.bcast.*` (en/pt, **340 chaves**). **i18n do mod
  essencialmente completa** (Core GUI+mensagens+broadcasts). Restam só strings cross-class passadas
  como `%s` (nomes de rank/clã/especialista/ameaça/moral) e nomes custom de entidade. Build OK.

- 2026-06-20: **i18n — notificações broadcast (fatia 1, anúncios narrativos)**. `OrkRaidManager.
  notifyNearbyPlayers` ganhou sobrecarga que aceita `Component` (a de `String` delega pra ela, então
  chamadores antigos seguem funcionando). Migrados para `Component.translatable` os anúncios mais
  visíveis: início de raid (forçado/chegando/força), Primarch surge + counter-charge, Warboss surge,
  war party marcha, Neófito vira Space Marine. Chaves `msg.firstcrusade.bcast.*` (en/pt, 312 chaves).
  Faltam os broadcasts secundários (reforços, rally/fortify via `notifyDefenseCommand`, Custodes). Build OK.

- 2026-06-20: **Fase D — território da cidade (raio que escala)**. `getTerritoryRadius()` no Core
  (`64 + nível*16` → L1 80, L5 144): a área que a cidade reivindica/defende. Usado como alcance do
  **counter-charge do Primarch** (defende todo o território, não só raio fixo) e **exibido na GUI**
  ("Território: N", slot ContainerData 57). Chave `gui.firstcrusade.info.territory` (en/pt). Bounded,
  sem worldgen nem hooks de spawn. Build OK; 304 chaves.

- 2026-06-20: **Fase D — simetria Ork: Warboss despachado pelo tier do WAAAGH!**. O Warboss agora
  se ergue mais cedo conforme o WAAAGH! global cresce: `requiredWarParties = max(1, 3 - tier)` no
  `OrkCampBlockEntity` (antes era fixo em 3). Espelho do payoff imperial (Cruzada → reforços). Agora
  os dois overlords escalam o despacho do líder pelo próprio tier global. Compila OK.

- 2026-06-20: **Fase D — Cruzada na GUI do Core**. Nova linha "Cruzada: Nível X" no painel City,
  mostrando o tier do `ImperiumOverlordManager`. Sincronizado via ContainerData (slot 56;
  `DATA_COUNT` 56→57; `getCrusadeTier` no Core lê o overlord server-side; getter no menu). Chave
  `gui.firstcrusade.info.crusade` (en/pt). Agora o jogador vê o crescimento da Cruzada global. Build OK; 303.

- 2026-06-20: **Fase D — despacho de líder por ameaça (Primarch counter-charge)**. No nível de ameaça
  **CRÍTICO**, o Primarch é despachado para **carregar contra o inimigo mais forte** perto da cidade
  (em vez de só ficar passivo): `ImperialPrimarchManager.leadCriticalCounterCharge` usa o novo
  `ThreatAssessmentManager.findStrongestEnemy` (maior peso, desempate por proximidade) e dá `setTarget`
  no Primarch (a comitiva segue o alvo dele). Anúncio único por despacho (throttle de ~1 min via NBT).
  Abaixo de crítico, mantém o comportamento antigo (ataca o camp quando seguro). Compila OK.

- 2026-06-20: **Fase D iniciada — Imperium Overlord (a Cruzada global)**. `ImperiumOverlordData`
  (SavedData no overworld) + `ImperiumOverlordManager`, espelho exato do WAAAGH!: cresce com o nível
  das cidades (contribui no tick ao lado do WAAAGH!), tier 0-4, anúncio global ao cruzar tier (chaves
  `msg.firstcrusade.crusade.tier1-4`, en/pt). **Payoff:** o tier da Cruzada soma aos reforços de toda
  cidade (`getReinforcementCount`). Agora os dois overlords crescem em paralelo (arms race Imperium ×
  WAAAGH!). Build OK; 302 chaves. (IDE pode mostrar erro transitório de símbolo até reindexar.)

- 2026-06-20: **Primarch empunha a Chainsword** (antes Netherite Sword vanilla). `equipAsPrimarch`
  usa `ExampleMod.CHAINSWORD` — líder imperial com arma do mod. (Custodes mantém a lâmina dourada
  genérica; Guilliman é bare-handed pois a aparência vem do modelo custom dele.) Compila OK.

- 2026-06-20: **Conteúdo Ork — arma de elite Power Klaw**. `SwordItem` com `POWER_KLAW_TIER` forte
  (900 usos, +5 dano, nível 4, conserta com Ork Teeth), golpe muito lento/devastador (speed -2.8).
  **Warboss** (trocou o Iron Axe vanilla) e **Meganob** agora empunham a Power Klaw — elite Ork com
  arma à altura. Registro completo: tier + item + creative tab + modelo + textura placeholder + lang
  en/pt + receita cara (Blocos de Ferro + Bloco de Redstone + Dentes + Sucata). Build OK; 298 chaves.

- 2026-06-20: **Conteúdo Ork — arma Shoota** (dakka). `ShootaItem` reusa o molde do Bolter: tiro
  **rápido** (cooldown 8t), **fraco** (dano 4) e **muito impreciso** (inaccuracy 6.0 — "Ork não
  mira"), **sem munição**, durabilidade baixa (220, quebra fácil). Item usável pelo jogador (sem
  mudar IA dos Orks). Registro completo: item + creative tab + modelo + textura placeholder + lang
  en/pt + receita (Sucata + Ferro + Redstone + Dentes). Fecha o par Ork melee/ranged. Build OK; 297.

- 2026-06-20: **Conteúdo Ork — arma Choppa** (balanceia o lado Ork das armas). `SwordItem` com
  `ORK_MELEE_TIER` cru (180 usos, +2 dano, conserta com **Ork Teeth**), golpe lento/pesado
  (speed -2.4). **Ork Boy e Ork Nob agora empunham a Choppa** (no construtor, drop chance 0).
  Registro completo: tier + item + creative tab + modelo + textura placeholder + lang en/pt +
  **receita** (Sucata + Ferro + Dentes de Ork). Build/jar OK; 296 chaves.

- 2026-06-19: **Receitas das armas novas** (obteníveis em survival, não só creative). `crafting_shaped`
  em `data/firstcrusade/recipes/`: **bolter** (3 Placa + 2 Ferro + Bloco de Redstone), **plasma_gun**
  (2 Placa + Pó de Blaze + Bloco de Redstone + 2 Ferro), **chainsword** (2 Placa + 2 Ferro). Escala
  pela força e usa Placa de Crusadium como material central, no padrão das receitas existentes. Build OK.

- 2026-06-19: **Conteúdo — nova arma: Plasma Gun** (energia, entre Lasgun e Bolter). `PlasmaGunItem`
  reusa o molde do Lasgun: dispara um tiro **flamejante** (`setSecondsOnFire(100)` no projétil →
  queima o alvo), dano 10, usa Lasgun Power Cell, cooldown 28t (carrega devagar), durabilidade 400.
  Completa o trio Imperial **Lasgun/Plasma/Bolter** (planejado no design §6). Registro completo
  (item + creative tab + modelo + textura placeholder + lang en/pt). Build/jar OK; 295 chaves.

- 2026-06-19: **Space Marine empunha a Chainsword** (antes Netherite Sword vanilla). `equipAsSpaceMarine`
  agora usa `ExampleMod.CHAINSWORD` no MAINHAND — dá presença in-world à arma nova no usuário icônico
  (encaixa no `MeleeAttackGoal` existente; drop chance 0). Import `Items` removido. Build/jar OK.

- 2026-06-19: **Conteúdo — nova arma melee: Chainsword** (Adeptus Astartes). `ChainswordItem` estende
  `SwordItem` (como a Combat Knife) com `CHAINSWORD_TIER` forte (700 usos, +5 dano, nível 3, conserta
  com Placa de Crusadium); golpe pesado/lento (modifier 4, speed -2.0) que **incendeia o alvo 3s**
  (dentes serrando). Registro completo: item + tier + creative tab + modelo `item/handheld` + textura
  placeholder (cópia da faca) + lang en/pt ("Chainsword"/"Espada-Serra"). Build/jar OK; 294 chaves.

- 2026-06-19: **Conteúdo — nova arma: Bolter** (Adeptus Astartes). `BolterItem` reusa o molde do
  `LasgunItem`/`LasgunShotEntity`: tiro forte (12 dano, knockback 1), **sem munição** (Space Marine
  autossuficiente), mais lento (cooldown 24t) e menos preciso, gastando durabilidade própria (768).
  Registro completo: item + creative tab + modelo `item/handheld` + textura placeholder (cópia de
  `guardsman_combat_knife.png`) + lang en/pt (`item.firstcrusade.bolter`). Bônus i18n: a mensagem
  do `LasgunItem` ("No charged…") virou `Component.translatable` (`msg.firstcrusade.lasgun.no_charge`).
  Build/jar OK; langs 293 chaves cada. (Dono fará a arte definitiva do bolter.)

- 2026-06-19: **GUI em chaves de lang — fatia 4d (FECHA o block entity)**. Migrados os fluxos
  restantes (especialista, reforço, rally, fortify, Space Marine, raid, interface) e o resumo de
  depósito. **`Component.literal` no `ImperialCommandCoreBlockEntity`: 121 → 0.** Chaves
  `msg.firstcrusade.spec/reinf/rally/fortify/sm/raid/interface/warsupport/defense.*` em en_us+pt_br
  (**291 cada, em sincronia**). **A GUI e as mensagens do Núcleo estão 100% traduzíveis.**
  Pendências menores de i18n (fora do Core/opcionais): (a) notificações broadcast em `OrkRaidManager.
  notifyNearbyPlayers` / `ImperialDefenseManager.notifyDefenseCommand` recebem **String** (exigem
  mudar a assinatura p/ Component); (b) strings cross-class passadas como `%s` (especialista, ameaça,
  moral, nome de tropa); (c) `Component.literal` em OUTROS arquivos (Ork Camp, outras telas/itens).

- 2026-06-19: **GUI em chaves de lang — fatia 4c (recrutar + depósito/retirada)**. Migrados para
  `Component.translatable`: depósito por recurso (Iron/Coal/Scrap: não-dono/cheio/depositado/City),
  `depositAllResources` (cheio/vazio/resumo), `withdrawResource` + `withdrawFood` (não-dono/vazio/
  retirado, reusando `gui.firstcrusade.res.*` e `reason.withdraw_empty`), e `tryRecruitGuardsman`
  (não-dono/cap/quartel/cidadão/custo/atribuído/em-treino). Chaves `msg.firstcrusade.deposit.*` /
  `.withdraw.*` / `.recruit.*` em en_us+pt_br (**256 cada**). `Component.literal` no block entity:
  **69 → 39**. Falta: reforço/rally/fortify, Space Marine, especialista, raid, alguns avisos soltos.

- 2026-06-19: **GUI em chaves de lang — fatia 4b (mensagens de construção)**. Os 8 fluxos de build
  (mina/mina de ouro/fazenda/depósito/ferro-velho/refinaria/quartel/forja) migrados para
  `Component.translatable`. Chaves **compartilhadas** reduziram a duplicação: `build.not_owner`
  (8×), `build.need_3`/`need_2_is`/`need_2_ic` (custos), `build.res_3`/`res_2` (resultado), e
  `build.*_cap` / `*_level` por estrutura. `msg.firstcrusade.build.*` em en_us+pt_br (**239 cada**).
  `Component.literal` no block entity: **121 → 69**. Falta: recrutar, depositar/withdraw, reforço/
  rally/fortify, Space Marine, especialista, raid (fatias 4c+).

- 2026-06-19: **GUI em chaves de lang — fatia 4a (mensagens do block entity: upgrade + reparo)**.
  Migrados para `Component.translatable` no `ImperialCommandCoreBlockEntity` os fluxos de **evoluir
  cidade** (`tryUpgradeCity`: não-dono, nível máx, faltas de recurso, e os 7 avisos de resultado) e
  **reparar Núcleo** (`repairCity`), além do **título do menu** (reusa `block.firstcrusade.
  imperial_command_core`). Chaves `msg.firstcrusade.upgrade.*` / `.repair.*` em en_us+pt_br (**223
  cada**). **Ainda faltam ~100 `Component.literal`** no mesmo arquivo (build/recruit/depósito/withdraw/
  reforço/rally/fortify/Space Marine/raid) — fazer por fluxo nas próximas fatias (4b, 4c…). Build OK.

- 2026-06-19: **GUI em chaves de lang — fatia 3 (tooltips dos botões)**. `applyButton` agora recebe
  `Component` (título/descrição/motivo) em vez de String; motivo de bloqueio em vermelho via
  `ChatFormatting.RED`. Todos os tooltips (Build/Military/Defense/Resources/City) e os helpers de
  motivo (`getRecruitBlockReason`/`getGoldMineBlockReason`/`getTradeDepotBlockReason`/
  `getReinforcementBlockReason`/`getUpgradeCostText`) viraram `Component.translatable` com `%s` para
  custos/contadores. Chaves `gui.firstcrusade.tip.*` / `.reason.*` / `.res.*` em en_us+pt_br
  (**206 chaves cada, em sincronia**). **GUI do Core 100% traduzível**, exceto strings vindas de
  outras classes (nome de especialista, nome do nível de ameaça, rótulo de moral), passadas como `%s`.
  **Falta:** mensagens `Component.literal` no `ImperialCommandCoreBlockEntity` (chat/feedback). Build OK.

- 2026-06-19: **GUI em chaves de lang — fatia 2 (painel de status esquerdo)**. Todas as linhas dos
  painéis City/Build/Military/Defense/Resources viraram `Component.translatable(chave, valores)` com
  `%s` (Level/Integrity/Morale/Citizens/Soldiers/estruturas/recursos/etc.), e os status curtos da
  própria tela (raid ATIVO/Seguro, reforço pronto/cooldown, Space Marine) também. Chaves
  `gui.firstcrusade.info.*` e `.status.*` em en_us+pt_br (**139 chaves cada, em sincronia**).
  **Ainda em inglês (outras classes):** nome do nível de ameaça (`ThreatAssessmentManager`) e rótulo
  de moral (`ImperialCityMoraleManager`) — passam como `%s`. **Falta:** tooltips dos botões
  (`applyButton`) e mensagens no `ImperialCommandCoreBlockEntity`. Build OK.

- 2026-06-19: **GUI em chaves de lang — fatia 1 (rótulos estáticos)**. Migrados para
  `Component.translatable` na `ImperialCommandCoreScreen`: nomes das **abas**, textos de **todos os
  botões** (City/Build/Military/Defense/Resources), **cabeçalhos de seção** e o **título** da tela.
  Chaves `gui.firstcrusade.tab.*` / `.button.*` / `.section.*` em en_us **e** pt_br (mesma contagem).
  `addActionButton` agora recebe a chave; novo overload `drawLine(Component)` + helper `drawHeader`.
  **Faltam (fatias seguintes):** tooltips dos botões (`applyButton` title/cost/reason — strings longas)
  e as linhas dinâmicas do painel esquerdo (`Level: `, `Soldiers: `, etc.) que precisam de `%s`.
  Build offline OK; ambos os langs validados.

- 2026-06-18: Arte — **novo modelo 3D do Lasgun** (Blockbench) em `models/item/lasgun.json`
  (antes era sprite 2D `item/handheld`). Aponta para `firstcrusade:item/lasgun` e ganhou bloco
  `display` (mão/GUI/chão). ⚠️ O modelo foi pintado para uma textura **32×32**; a `lasgun.png`
  atual é 16×16 e plana, então as faces ficam embaralhadas até o dono enviar/instalar o PNG 32×32
  que acompanha o modelo. `texture_size` já está [32,32]. Display é um chute inicial — ajustar no
  Blockbench se a pose na mão ficar torta.
- 2026-06-18: Fase C (estruturas por tipo — 1º slice) — **bônus de capacidade na estrutura-tema**.
  Cada tipo de foco roda +1 da sua estrutura assinatura: Mining→Mina, Fortress→Barracks, Hive→Scrap
  Yard, Forge→Forja, Agri→Farm (helper `specialtyBonus` nos getters de capacidade). Tipos sem foco de
  produção (Civilised/Shrine/Penal/Death World/Feudal) não recebem bônus. Agora o tipo molda a
  economia/defesa, não só as tropas. Build OK.
- 2026-06-18: Fase C — **10º tipo de cidade: Feudal World + Feudal Knight**. `ImperialCityType.FEUDAL`
  (pop 1.1, regimento blindado +hp/+armor, lasgun -1 primitivo) recruta `FeudalKnightEntity`: muralha
  de melee (armor 16 + knockback resist 0.8, lento, dano 7) — nicho "shield wall" distinto do Mine
  Guard (tanque de vida). Fica no pool aleatório (não consome bioma). Registro + lang en/pt + wiring.
  Build OK; langs 71 chaves.
- 2026-06-18: Fase C — **tipo de cidade enviesado pelo bioma** (antes 100% aleatório). `assignCityType`
  agora recebe o `ServerLevel` e usa `pickCityTypeForBiome` (casa o path do id do bioma): deserto/
  badlands/montanha→Mining, selva/bamboo→Death World, pântano/mangue→Penal, neve/gelo/taiga→Fortress,
  planície/savana/meadow→Agri; biomas sem tema claro (floresta/oceano/…) caem no aleatório (mantém
  Hive/Forge/Shrine/Civilised em rotação). Sem deps de biome-tags (robusto entre versões do Forge).
  Build offline OK.
- 2026-06-18: Fase C — **tropas-tema patrulham a cidade** (antes só Guardsmen patrulhavam; tropas-tema
  ficavam vagando perto do spawn). Adicionado guard post à base `AbstractImperialTroopEntity` (campo +
  `assignGuardPost`/`getGuardPostPos` + NBT) e goal genérico `ImperialTroopGuardPostGoal`;
  `ImperialPatrolManager` agora atribui waypoints às tropas-tema também (mesma lógica/anel/rotação,
  exclui retinue do Primarca). **Refatoração junto:** os goals comuns (Float/LookAt/LookAround/Stroll +
  target goals + guard post) subiram para a base em `registerGoals()`; cada subclasse agora só
  implementa `registerCombatGoals()` com seu único goal de ataque — removeu a duplicação que as 8
  tropas repetiam. Build offline OK.
- 2026-06-18: Fase C — **9º tipo de cidade: Death World + Jungle Fighter**. `ImperialCityType.DEATH_WORLD`
  (veteranos: pop baixa 0.9, +1 rank, regimento ágil +hp/+dano/+lasgun/+speed, custo 5 Ferro) recruta
  `JungleFighterEntity` (skirmisher ranged ágil de elite, 34 HP, dano à distância 6.5, rápido 0.34 —
  nicho hit-and-run, distinto do Kasrkin pesado e da Agri frágil). Registro + lang en/pt + wiring.
  Build OK; langs 69 chaves. (Dono assumiu as texturas; tropas usam placeholder guardsman.png.)
- 2026-06-18: Fase C — **Penal Legionnaire (tropa-tema da Penal Colony)**. `PenalLegionnaireEntity`
  (melee charger rápido e frágil: 22 HP, armor 2, dano 6, speed 0.37, faca de combate) — glass-cannon
  que combina com a Penal (numerosa/descartável); contrasta Enforcer (equilibrado) e Mine Guard (tanky).
  Registro completo + lang en/pt + wiring (`getThemedTroopType` PENAL→Penal Legionnaire; nome já vinha
  do troopName). Agora **só Civilised usa Guardsman baseline**; os outros 7 tipos têm entidade própria.
  Build OK; langs validados (67 chaves cada).
- 2026-06-18: Fase C — **reforços respeitam a tropa-tema da cidade**. O botão Call Reinforcements
  criava sempre Guardsman; agora ramifica por `getThemedTroopType(getCityType())` (Forge→Skitarii,
  Fortress→Kasrkin, Hive→Enforcer, Mining→Mine Guard, Agri→Agri Militia, Shrine→Sister of Battle;
  Civilised/Penal→Guardsman com guard post/chapter). Helper `spawnThemedTroop` generalizado para
  `spawnThemedTroopAt(type, x,y,z, yRot,xRot)`, reusado por recrutamento e reforço. Tropas-tema
  free-roam (sem guard post). **Nota:** `ImperialPopulationManager.trainNearestCitizenAsGuardsman`
  é **código morto** (sem chamador; recruta vai pelo Barracks) — não convertido. Build offline OK.
- 2026-06-18: **Localização pt_br** — criado `assets/firstcrusade/lang/pt_br.json` espelhando o en_us
  (65 chaves). Termos genéricos traduzidos (blocos, peças de armadura, "Spawn Egg" → "Ovo de Invocação
  de…", Scrap→Sucata, etc.); nomes próprios de WH40K mantidos (Lasgun, Guardsman, Space Marine,
  Skitarii, Kasrkin, Ork*, Gretchin, Killa Kan, Crusadium, Guilliman). Ambos os langs validados e com
  a mesma contagem de chaves. Build OK.
- 2026-06-18: Fase C — **8º tipo de cidade: Penal Colony** (`ImperialCityType.PENAL`): regimento
  descartável (pop 1.5×, -1 rank, hp/armor fracos mas +1 dano, barato 2 Ferro, levemente mais rápido),
  fielda **Guardsman baseline** (como Civilised). Só 1 linha no enum (data-driven; nenhum switch sobre
  cityType é exaustivo sem default). Build/jar OK.
- 2026-06-18: **Corrigido crash de carregamento** (`NoSuchFieldError: f_279569_` no `ExampleMod.<clinit>`):
  o jar reobf (SRG) tinha sido copiado para `run/mods`, mas o dono joga via `runClient` (dev/nomeado) —
  SRG não existe lá. Solução: **jar removido de `run/mods`**; no dev o mod carrega das classes
  compiladas. Ver §2 (regra: nunca pôr o jar do próprio mod em `run/mods`).
- 2026-06-18: **Performance (dono relatou travadas)** — o Core recalculava ~19 scans de bloco/entidade
  a cada 40 ticks **por cidade, mesmo sem ninguém olhando**. Dois cortes: (1) `recomputeMenuStats` só
  roda enquanto o menu está **aberto** (`openMenuCount`, incrementado em `ImperialCommandCoreMenu`
  ctor server / decrementado em `removed`; recompute imediato ao abrir). Sem espectadores → **zero
  scans**. (2) Os ~10 scans de cidadãos (assigned + unemployed + 8× job) viraram **1 scan** via
  `ImperialPopulationManager.censusAssignedCitizens` (`CitizenCensus`, tabulação por job em uma
  passagem). Comportamento da GUI idêntico. Build/jar OK.
- 2026-06-18: Fase C — **Shrine City (7º tipo de cidade) + Sister of Battle**. Novo valor de enum
  `ImperialCityType.SHRINE` ("Shrine City", sem foco de recurso, pop 1.3, +1 rank, regimento de fé
  +2hp/+2armor/+1dano, recruta custa 6 Ferro) — entra automaticamente no spawn aleatório
  (`ImperialCityType.random`). Recruta `SisterOfBattleEntity` (Adepta Sororitas): atiradora zelota
  bem-armada (40 HP, armor 11, tiro 7 com knockback), sobre `AbstractImperialTroopEntity`. Registro
  completo (entity type + spawn egg + atributos + renderer + textura placeholder + lang) e wiring no
  Core (`getThemedTroopType`/`getFieldedUnitName`). Verificado que nenhum switch sobre cityType é
  exaustivo sem default. Build offline OK.
- 2026-06-18: Fase C — **Mine Guard (Mining) + Agri Militia (Agri): todos os 6 tipos de cidade com
  tropa-tema**. `MineGuardEntity` (melee bruiser lento/tanky, 42 HP, dano 8, armor 12, knockback
  resist) e `AgriMilitiaEntity` (skirmisher ranged leve/rápido, 24 HP, lasgun fraco, speed 0.36),
  ambas sobre `AbstractImperialTroopEntity`. Registro completo (entity type + spawn egg + atributos +
  renderer + textura placeholder + lang). **Generalização** aproveitando a base: o dispatch de recruta
  virou `getThemedTroopType(cityType)->EntityType` + `spawnThemedTroop(...)` (substituiu os helpers
  por-tropa); `FirstCrusadeFactionManager` gateia todas as tropas-tema por `instanceof
  AbstractImperialTroopEntity`; a recontagem do upgrade conta as tropas-tema numa única query da base.
  Adicionar tropa nova agora é ~1 entidade + 1 renderer + registros + 1 linha no switch. Build offline OK.
- 2026-06-18: Refatoração — **classe base `AbstractImperialTroopEntity`** para as tropas-tema
  standalone (Skitarii/Kasrkin/Enforcer). Extraído o que era idêntico nas três: vínculo ao Command
  Core (`assignToCommandCore`/`isAssignedToCommandCore`), gating de facção (`canAttack`/`setTarget`),
  `removeWhenFarAway`, NBT do Core e `die`→`onAssignedGuardsmanDeath`. Subclasses guardam só atributos,
  goals, arma e nome (e `performRangedAttack` nas atiradoras). Comportamento idêntico; ~300 linhas de
  duplicação a menos. Facilita as próximas tropas-tema. Build offline OK.
- 2026-06-18: Fase C — **Enforcer: 3ª tropa-entidade própria (Hive City / Adeptus Arbites) e 1ª de
  melee**. `EnforcerEntity` standalone (extends PathfinderMob, sem RangedAttackMob): brawler com
  shock maul (`GUARDSMAN_COMMAND_BATON` na mão), usa vanilla `MeleeAttackGoal` + target goals de
  facção (30 HP, dano 7, armor 8, rápido 0.33). Combina com a Hive (barata e numerosa, 2x pop).
  Registro completo (EntityType + spawn egg + atributos + renderer `EnforcerRenderer` + textura
  placeholder = guardsman.png + faction IMPERIUM + lang). Hive City recruta Enforcer:
  `completeRecruitTraining`/`getFieldedUnitName` agora cobrem FORGE/FORTRESS/HIVE e a recontagem do
  upgrade soma os 4 tipos de tropa. Build offline OK.
- 2026-06-18: Fase C — **Kasrkin: 2ª tropa-entidade própria (Fortress City / Militarum Tempestus)**.
  Entidade standalone elite (`KasrkinEntity`, espelha o Skitarii): atirador pesado (44 HP, 13 armor,
  hotshot lasgun dano 8 com knockback) que usa `RangedAttackGoal` + target goals de facção. Registrado
  por completo (EntityType + spawn egg + atributos + renderer `KasrkinRenderer` + textura placeholder =
  cópia de guardsman.png + faction IMPERIUM + lang). Fortress City passa a **recrutar Kasrkin** no fim
  do treino: `completeRecruitTraining` virou switch (FORGE→Skitarii, FORTRESS→Kasrkin, default→Guardsman),
  com helpers `spawnTrainedKasrkin`/`getFieldedUnitName`; a recontagem do upgrade conta os três tipos.
  Build offline OK.
- 2026-06-18: Fase C — **Forge City recruta Skitarii Ranger** (antes a entidade existia mas
  nenhuma cidade a produzia). `ImperialCommandCoreBlockEntity.completeRecruitTraining` agora ramifica:
  Forge → `SkitariiRangerEntity`, demais tipos → Guardsman (extraídos os helpers
  `spawnTrainedSkitariiRanger`/`spawnTrainedGuardsman`). O Skitarii entra no tally militar
  (`recruitedGuardsmen`): conta na recontagem do upgrade (`reorganizeExistingGuardsmen`) e decrementa
  na morte (já chamava `onAssignedGuardsmanDeath`). Mensagem de fim de treino cita o nome da tropa.
  Build offline OK. **Reforços/treino manual ainda criam Guardsman** (Skitarii não tem guard post).
- 2026-06-17: Fase C — **primeira tropa-entidade própria: Skitarii Ranger** (tropa-tema da Forge
  City / Adeptus Mechanicus). Entidade **standalone** (`SkitariiRangerEntity` extends PathfinderMob
  implements RangedAttackMob) — atirador resistente (34 HP, 10 armor) que dispara o mesmo
  `LasgunShotEntity` via `RangedAttackGoal` vanilla; reusa os target goals de facção. Registrado por
  completo: EntityType + spawn egg + atributos + renderer (`SkitariiRangerRenderer`, textura
  placeholder = cópia de guardsman.png) + faction IMPERIUM + lang. **Testável pelo spawn egg.**
  PENDENTE (próximo passo): fazer a Forge City **recrutar Skitarii** no lugar do Guardsman (hoje a
  entidade existe mas nenhuma cidade a produz; ver `ImperialCommandCoreBlockEntity.completeRecruitTraining`
  ~linha 1147 e `getCityType()==FORGE`).
- 2026-06-17: Fase C — **custo de recruta por tipo de cidade**: treinar um soldado agora consome
  Ferro (`ImperialCityType.recruitIronCost`), cobrado em `ImperialCommandCoreBlockEntity.tryPayRecruitCost`
  no início do treino (Barracks e treino manual). Hive 2, Agri 3, Civilised/Mining 4, Forge 6,
  Fortress 8 — levas baratas vs elite cara. Sem Ferro, o treino é recusado com aviso citando o custo.
- 2026-06-17: Fase C — **regimento aplicado em todos os caminhos de recruta**: treino manual
  (`ImperialPopulationManager.trainNearestCitizenAsGuardsman`) e reforços (Core) antes criavam
  Guardsman sem chapter/rank/regimento; agora todos chamam `initializeFromCity(rank, cityType)` +
  `assignRandomChapter`, e a mensagem usa o nome temático da tropa. (`populationFactor` já estava
  ligado em `getCitizenCapacity`.)
- 2026-06-17: Fase C — **regimentos de combate por tipo de cidade**: `ImperialCityType` ganhou
  modificadores de hp/armor/dano/lasgun/velocidade; Guardsman tem campo `cityType` (NBT) que soma
  esses bônus em `applyRankStats`/`getLasgunDamageWithBonuses` e exibe o nome do regimento. Core
  recruta com `initializeFromCity(rank, cityType)`. Fortress tanky / Forge equipado / Mining durão /
  Agri ágil / Hive fraco mas 2x pop. Tropas de tipos diferentes agora lutam de fato diferente.
- 2026-06-17: Fase C (início) — **identidade militar por tipo de cidade**: bônus de rank nos
  recrutas (`ImperialCityType.recruitRankBonus` + `GuardsmanRank.advance`) e nome temático de tropa.
  Fortress treina Shock Troopers (+2), Forge Forge Guards (+1), Hive Levies (-1, compensado por 2x pop).
- 2026-06-17: Fase B COMPLETA — **Killa Kan** (andador/máquina Ork, 110 HP/16 dano/16 armor, peso
  de ameaça 20, entra na warband em WAAAGH! tier 3+). Placeholder humanoide até ter modelo próprio.
- 2026-06-17: Fase B — **warbands por clã**: `OrkClan` ganhou composição (bonusBoyz/nobz/
  bonusGretchin/tactics). Cada clã marcha diferente: Goffs horda, Bad Moons Nobz, Deathskulls
  grots, Evil Sunz rápidos, Snakebites durões; mensagem cita a tática do clã.
- 2026-06-17: Fase B — **WAAAGH! Overlord**: estado global persistente (`WaaaghOverlordData`,
  SavedData no overworld) que cresce com a prosperidade imperial; tier 0-4 com aviso global;
  camps leem o tier (`WaaaghOverlordManager.getTier`) para warbands maiores e Meganobz mais cedo.
- 2026-06-17: Fase B — adicionadas unidades **Meganob** (elite Ork, peso de ameaça 12) e
  **Gretchin** (bucha, foge sozinha, peso 1). Warband do camp agora mista (Boyz + 2 Gretchin +
  Meganob quando warbossSpawned). Registro/renderer/textura placeholder/spawn egg/faction/lang +
  pesos no ThreatAssessmentManager (Warboss 30, Meganob 12, Nob 6, Boy 3, Gretchin 1).
- 2026-06-17: Criado este STATUS.md. Repo em `75dd981` (dono adicionou Guilliman, Gold Mine, Farm,
  Emerald Trade Depot, UI com abas, ImperialResourceStorage, recurso Food). Antes (agente): Fase A
  (ImperialCityType, OrkClan, ThreatAssessmentManager, exército pessoal) + Warboss (Fase B parcial).
