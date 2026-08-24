# First Crusade — STATUS (leia isto primeiro ao reorientar)

> Arquivo de orientação do agente. Quando o chat for limpo (`/clear`), **leia este arquivo
> primeiro** para retomar o contexto. **Mantenha-o atualizado** ao fim de cada bloco de trabalho
> (estado atual + metas + changelog).
>
> ⚠️ O dono do projeto também desenvolve em paralelo (commits via "da run"/"da run pfb"). Antes de
> mudanças grandes, **verifique o estado real** com `git log --oneline -8`, `git status` e um
> `Glob` em `src/main/java/com/example/examplemod/*.java` — não confie só neste arquivo.

Última atualização: **2026-08-24** · branch `performance-layer` (pushed) · remoto
`github.com/Guaxinim-ui/first-crusade-mod`

> **Entrada rápida:** o changelog (§7) está em ordem cronológica inversa e as entradas de 2026-08-21
> a 2026-08-24 são o estado atual. A §4 abaixo guarda o roadmap histórico das Fases A–E, que foi
> superado pela numeração `§N` do brief — o que realmente falta está em **§4.1**.

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

### 4.1 O que falta AGORA (2026-08-24)

Tudo o resto nesta secção é histórico. Isto é a lista viva.

| Item | Estado | Nota |
|---|---|---|
| **ESCORT / comboios** | destrancado, por fazer | O bloqueio era "falta logística"; a logística existe desde 2026-08-20. `StrategicDeployment` já modela força com origem, destino e tempo de viagem. |
| **§5 fatia 2 — ambiência** | por fazer | Sons e spawns por planeta. Sem arte nova. A fatia 1 (perigos ambientais) está feita. |
| **§18-19 Hive vertical** | por avaliar | A maior das que sobram e a única que ainda não olhei. |
| **RESCUE / RECOVER** | bloqueado | Esperam por esquadrões resgatáveis e por mais artefactos. O `RECOVER` ficou mais perto agora que existe um artefacto. |
| **Cadia e Ork World** | bloqueado | Continuam atrás de `TRIGGER_CAMPAIGN`, que nada dispara. O mundo-tumba saiu dessa lista em 2026-08-24. |

### 4.2 Roadmap histórico (Fases A–E)

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

**🎨 IDENTIDADE VISUAL DAS TROPAS IMPERIAIS (2026-08-07).** Nove texturas eram **o mesmo arquivo**
(md5 `2524cc91…`): agri_militia, city_commander, enforcer, feudal_knight, jungle_fighter,
mine_guard, penal_legionnaire, sister_of_battle, skitarii_ranger. Cada renderer guardava um
`static final ResourceLocation` e devolvia o mesmo para toda instância — por isso ninguém percebeu.

**Geometria (medida, não chutada):** as **11** tropas humanoides usam `ModelLayers.ZOMBIE`, ou seja
`HumanoidModel.createMesh` — UV humanoide padrão 64x64 com camada de chapéu na cabeça. Uma
geometria, onze unidades. As duas tropas GeckoLib (`guardsman_rifleman`, `guardsman_sergeant`) têm
UV própria 64x128 lida do `.geo.json`. Nenhuma UV foi alterada; o espelhamento de braços/pernas nos
modelos GeckoLib é proposital e foi mantido.

**Arquitetura:** `ImperialTroopAppearance.texture(tipo, regimento, variante, grade)` — todo
`ResourceLocation` é construído no class-load, então uma chamada de render é dois lookups e um
índice. `ImperialTroopVisuals` é a única coisa que o renderer pode perguntar. `ImperialTroopGrade`
(LINE/VETERAN/SERGEANT) é **derivado** de `GuardsmanRank`, nunca guardado — promover é um campo
mudando na entidade que já existe: mesmo UUID, nome, merit e contagem de Orks. Regimento está
cabeado com uma entrada (`default`); quando houver arte Cadian/Krieg é uma linha em `define`.
Fallback para o Guardsman com aviso **uma vez** por miss (renderer roda por frame).

**Variante:** sorteada em `defineSynchedData` (o único ponto por onde todo caminho de spawn passa —
`finalizeSpawn` não), sincronizada num int, persistida em `FirstCrusadeVisualVariant`. Save antigo
sem a tag mantém o sorteio e passa a gravá-lo: a aparência nunca muda no relog.

**29 PNGs 64x64** em `textures/entity/imperium/<tropa>/` + **8 de 64x128** para as GeckoLib.
Gerados por `tools/generate_troop_textures.py` e `tools/generate_geo_troop_textures.py`
(determinísticos — rodar de novo dá bytes idênticos). **A arte feita à mão pelo dono foi
preservada**: `guardsman.png` virou `guardsman_3.png` e `kasrkin.png` virou `kasrkin_2.png`, e o
gerador tem uma trava (`OWNER_AUTHORED`) que proíbe sobrescrevê-las.

**Conferido por script:** 29 declarados no Java = 29 no disco, 0 faltando, 0 órfãos, 0 fora de
64x64. Build verde.

**>>> FALTA VER EM JOGO (é o que fecha esta fase):** `/fctroop line` põe uma de cada tropa lado a
lado. Olhar e responder: dá para distinguir todas sem ler o nome? `/fctroop career <alvo> SERGEANT`
troca a aparência sem recriar o soldado; `/fctroop variant` e `/fctroop info` mostram qual PNG cada
um resolveu. Nada disso foi visto em jogo ainda — só o build e a conferência de arquivos.

**🐛 BUG "não consigo bater em nada nem colocar bloco" — CAUSA ACHADA E CORRIGIDA (2026-08-07).**

Não era gamemode, nem evento de bloco cancelado, nem servidor órfão. Era **dessincronia do corpo
do jogador**: `PlayerProgressionClientView.putStage(...)` **nunca era chamado por ninguém** (nem
`clear()`). Com o mapa `STAGES` sempre vazio, `PlayerProgressionSizeManager.onSize` **no cliente**
lia `ASTRA_RECRUIT` e voltava sem escalar, enquanto o **servidor** escalava de verdade. Do NEOPHYTE
para cima o servidor movia uma caixa de **0.84 × 2.30** e o cliente uma de **0.60 × 1.80**.

Consequência, verificada na fonte decompilada do Forge 47.4.10 (não é teoria):
`ServerGamePacketListenerImpl.isPlayerCollidingWithAnythingNew` usa a caixa **do servidor**; quando
o cliente (magro) anda para perto de um bloco que a caixa gorda do servidor não aceita, o servidor
chama `teleport(...)`, que seta **`awaitingPositionFromClient`** — **sem escrever nada no log**
(o "moved wrongly!" só sai fora do creative e por outro caminho). E `handleUseItemOn` começa com
`if (this.awaitingPositionFromClient == null && ...)`: **toda colocação de bloco é descartada em
silêncio** enquanto isso, e o ataque erra porque a posição do servidor foi puxada para trás.

**Correção (compila, `build` verde):** novo pacote público `SyncPlayerStagePacket` (UUID + stage),
enviado em `PlayerProgressionNetwork.sync` por `TRACKING_ENTITY_AND_SELF` e em
`PlayerEvent.StartTracking` (`syncStageTo`); cliente aplica em `progression/client/ClientStageSync`,
que faz `putStage` **e `refreshDimensions()`** (só lembrar não basta — a entidade guarda a caixa que
calculou por último) e limpa tudo em `ClientPlayerNetworkEvent.LoggingOut`.

**SEGUNDA CAUSA, achada depois (2026-08-07, "abaixo e levanto e fico num bloco invisível"):**
não era o sync, era a **altura acima de 2.0**. `Player.updatePlayerPose` pergunta
`canEnterPose(STANDING)` → `getBoundingBoxForPose` → `getDimensions(pose)`, e **o size event do
Forge não roda nesse caminho** (só dentro de `refreshDimensions`). Então o jogo decide se o jogador
cabe em pé usando a caixa **vanilla 0.60x1.80**, aprova, levanta — e só aí a caixa real de 2.30
aparece dentro do bloco do teto. A colisão resolve para cima, e ele fica um bloco acima apoiado em
nada. Repetir agachar/levantar sobe indefinidamente = "voando". O ramo de push-out do
`Entity.refreshDimensions` que existiria para isso termina em `&& !(this instanceof Player)` —
jogador está excluído, ninguém socorre.

**Correção (2ª tentativa, a que ficou — dono pediu explicitamente para NÃO encolher):** nada é
limitado. O corpo continua 2.30 e o Astartes continua não passando por porta de humano. Quem
resolve é `PlayerProgressionPose`: no `TickEvent.Phase.START` (que o Forge dispara na **primeira
linha** de `Player.tick()`, antes de `updatePlayerPose()` ler o campo no mesmo tick, nos **dois
lados**) ele faz a pergunta que o vanilla queria fazer — "cabe em pé?" — contra a caixa **escalada**,
e quando não cabe crava a pose com `Player#setForcedPose`. O jogador simplesmente **fica agachado
até ter espaço**, que é o que o vanilla já faz com qualquer um debaixo de uma laje. Nunca é levantado
para dentro da pedra, então não existe sobreposição para ejetar. Se nem agachado couber, vai para
`SWIMMING` (rastejar), o último recurso do próprio vanilla. `release()` só limpa poses que esta
classe crava (CROUCHING/SWIMMING) — pose forçada por outro mod não é pisada.

Custo: um `noCollision` por jogador por tick no caso comum, e retorno imediato para quem ainda é
`ASTRA_RECRUIT`. Build verde.

**Tentativa descartada:** houve um teto de 1.99/0.98 no size event. Funcionava, mas matava o
gigante; o dono mandou manter grande. Foi revertido — não reintroduzir.

**Boneco grande (feito logo depois, a pedido do dono):** escalar dimensões **não** deixa o jogador
visualmente maior — `PlayerRenderer` desenha o modelo num scale fixo de 0.9375 e nunca consulta as
dimensões da entidade. Antes disso o Neófito ocupava 2.30, não passava pela porta, via o mundo da
altura de um Astartes... e era desenhado do tamanho de um Guardsman.
`progression/client/PlayerProgressionRenderScale` escala o modelo no `PoseStack`:
`RenderPlayerEvent.Pre` (prioridade LOWEST, para que todo cancelamento já tenha acontecido — se
alguém cancelar depois do push, o `Post` não roda e a matriz vaza para o resto do frame, que
aparece como o mundo inteiro tortando) e `Post` (HIGHEST). Escala **uniforme** pela razão de
**altura**; usar a razão de largura em X/Z esticaria o modelo em vez de aumentá-lo. `scale()`
multiplica em torno da origem, que no render de entidade são **os pés**, então o boneco cresce para
cima sem translate.

**Dois artefatos cosméticos previstos, não vistos em jogo:** (1) o nameplate usa
`getBbHeight() + 0.5` e depois é escalado junto, então flutua ~0.8 bloco alto demais e maior — só
visível em F5/multiplayer, nunca no próprio jogador; (2) o boneco da tela de inventário também
passa pelo `RenderPlayerEvent` e pode transbordar o quadro. Se incomodar, é aqui que se mexe.

**Como o bug foi achado (para repetir):** NBT do save lido direto com um leitor Python de 60 linhas —
`run/saves/<mundo>/playerdata/*.dat` (gamemode, abilities, atributos) e
`run/saves/<mundo>/data/firstcrusade_player_progression.dat` (stage/ranks/implantes). Foi o que
mostrou `playerGameType=0`, `mayBuild=1` e `Stage=NEOPHYTE` — descartando as hipóteses fáceis.

---

**⚔️ BASE SIMPLIFICADA + RAID DO JOGADOR + COMANDO IMPERIAL (2026-08-06, pedido do dono).**
O city builder inteiro saiu de circulação. A base Imperial virou o espelho do acampamento Ork:
um Core, uma laje 9x9 escrita uma única vez, quatro soldados soltos. O jogador agora **declara**
a guerra em vez de assistir a ela: interage com o Ork Camp, aperta INICIAR RAID IMPERIAL, e a
base Imperial elegível mais próxima manda o que a árvore de Comando permitir.

**O que parou de rodar** (nada disso está escondido; as chamadas foram removidas, não desligadas):
`StrategicConstructionBuilder.tickConstruction` (era 20 em 20 ticks), `CityMilitaryManager.tickAll`
(60), a IA estratégica de construção/ataque (100), `ImperialPatrolManager.tickPatrols`,
`ImperialWorkforceManager.autoManageWorkforce`, `ImperialPopulationManager.tickCitizenGrowth`,
`ImperialCityMoraleManager.tickMorale` e toda a governança autônoma do Core. O tick estratégico que
sobrou roda **1x a cada 600 ticks** e só faz contabilidade (sincronizar mapa, renda passiva, lado
Ork, e a checagem de captura **apenas** para cidades com camp a menos de 160 blocos).

**Medido em servidor dedicado** (mundo `fctest_assault`, RCON): base nova = 1 Core + 4 tropas +
0 cidadãos + 0 obras estratégicas; raid iniciada pelo comando devolveu `ACTIVE | defensores 14`;
com Esquadra Reforçada saíram **5 tropas reais** (contagem global de tropas não mudou: 20 antes,
20 depois — nada foi duplicado); ao matar os defensores a vitória disparou sozinha, o bloco do camp
sumiu, o comandante subiu para nível 1 (+1 Ponto de Comando) e os **5 sobreviventes voltaram** para
a base. Tela K conferida por screenshot nas duas abas.

**Dois bugs achados pela medição e corrigidos:**
1. A guarnição crescia sem parar (20 tropas num teto de 10). A varredura de reconciliação de 32
   blocos não enxerga um soldado perseguindo um Ork, e o código tratava isso como baixa: o contador
   caía e a base recrutava de novo. Agora a varredura **só levanta** o contador; a morte já o baixa,
   uma vez, em `onAssignedGuardsmanDeath`. O recount exato existe num único momento — a migração.
2. O painel lateral da tela K escrevia por cima do próprio botão e do rodapé em GUI scale 3.
   Agora há um teto (`panelTextLimit`) e a descrição é a primeira coisa a ser cortada; custo e
   requisito vêm antes dela.

**>>> AÇÃO IMEDIATA:** dono testa em **mundo novo** (git pull + da run):
1. As bases do mundo nascem só com Core + laje + 4 soldados? Nenhuma casa/muralha/cidadão?
2. Os soldados ficam soltos perto do Core sem marchar em círculo?
3. No Ork Camp aparece o botão **INICIAR RAID IMPERIAL** (vermelho/dourado) para jogador Imperium?
4. Tecla K: as abas ASCENSÃO ASTARTES / COMANDO IMPERIAL trocam sem perder scroll?
5. Um save antigo carrega, mantém as construções antigas paradas e some com os cidadãos do Core?

Tunáveis num arquivo só cada: `SimpleImperialBaseBalance` (guarnição/raio/laje),
`ImperialAssaultBalance` (cadência/aproximação/abort), `PlayerCommanderBalance` (XP/pontos/limites).

---

#### Histórico: FASE F — VILAS VIVAS (SUPERSEDIDA em 2026-08-06)

A Fase F (vila murada crescendo, cidadãos dormindo, filhos, aspirantes) foi **substituída** pela
base simplificada. `buildCityStructure` e seus dezessete auxiliares foram removidos; a vila murada
de saves antigos **continua no mundo**, apenas parada. O pipeline de aspirantes (`AspirantManager`)
segue intocado, e a migração preserva qualquer aspirante em cirurgia.


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

- 2026-08-24 (5): **A relíquia Necron existe — o Mundo-Tumba deixou de ser inalcançável.**
  Build verde, `runData` rodado, **sítio verificado em servidor**.

  O dono perguntou onde se apanha a relíquia. Resposta: **em lado nenhum, ela nunca foi feita.** O
  mundo-tumba pedia `TRIGGER_CAMPAIGN`, cujo javadoc dizia "nothing fires this yet; it exists so the
  requirement can be shown honestly". Três planetas estavam nessa situação (Cadia, Ork World e o
  mundo-tumba); agora são dois.

  **Gatilho próprio, e a razão importa.** `TRIGGER_NECRON_ARTEFACT` novo em vez de reusar o
  `TRIGGER_CAMPAIGN`: os três planetas partilhavam aquele, portanto disparar-lo abriria Cadia e o Ork
  World também — uma relíquia a destrancar três mundos, dois deles sem nada a ver com ela.

  **A ruína** (`site_necron_ruin`): forma `RUIN` nova no sistema de sítios, anel de parede partida um
  degrau abaixo do terreno, escaravelhos de guarda e o relicário no centro. As falhas na parede são
  sorteadas em vez de haver uma porta — porta diz "construíram isto para entrar", parede com pedaços
  em falta diz "isto caiu", que é a leitura certa.

  **Onde:** bioma `salt_waste`, que existe em Armageddon (30%), Forge World (25%) e no próprio
  mundo-tumba (60%). As tumbas estão sob o sal em todo o lado e o mundo-tumba é só onde são mais
  densas — e resolve o ciclo fechado óbvio (precisar do artefacto para chegar ao planeta onde estão
  os Necrons). Raridade 220, alta: é uma chave, não mobília.

  **Não criei um segundo dono de worldgen.** A etapa 4 de cada bioma pertence ao
  `generate_fauna_sites.py`, e dois geradores no mesmo ficheiro já apagaram toda a vegetação uma vez
  ([[worldgen-script-ownership]]). O sítio Necron foi acrescentado **à tabela desse script**.

  **O relicário é pegado à mão, não partido**: `use()` e não loot table, bloco indestrutível, e
  substituído por deepslate ao ser esvaziado. O desbloqueio é concedido **no momento em que se tira**,
  não por ter o item — quem perder o artefacto depois não perde o mundo que já ganhou. Tirar acorda
  os escaravelhos a 24 blocos.

  **Medido:** `/place feature` construiu **1 relicário** (exactamente um, no centro), **66** blocos de
  parede e **117** de piso, com guarda vivo. (A primeira busca falhou por eu procurar em y 58-82 e o
  chão do mundo-tumba ali estar por volta de y 55 — o sítio estava certo, a minha janela é que não.)

- 2026-08-24 (4): **§29 FECHADO + a camada `ai/` finalmente OBSERVADA.** Build verde.

  **A camada `ai/` (§20-23) foi vista a funcionar pela primeira vez — 2500 linhas que nunca tinham
  sido exercidas.** O caminho para lá chegar foi o achado do dia:

  - `/fc suppression` novo. Supressão decai **na leitura** e nada tica por ela, portanto não há linha
    de log que pudesse emitir nem estado no mundo que a mostrasse. Sem este comando a única forma de
    saber se o §22 funciona era acreditar que sim.
  - **A IA não corre sem jogador, por desenho.** As 40 unidades da primeira tentativa saíram todas em
    `IA estratégica` com "1 batalha abstraída" — o `FCStrategicBattleData` absorveu-as. Foi preciso
    desligar `[ai.lod] enabled` e `[strategic] enabled` no toml do mundo de teste para a IA real
    correr. **Isto é a optimização a fazer o seu trabalho, não um bug** — mas é a razão por que
    ninguém tinha conseguido testar esta camada.

  **Medido, com muro e duas passagens, 20×20:** supressão a acumular em 16 unidades, a atravessar o
  limiar de PRESO (≥45) em 2 e o de COBERTURA (≥60) em 2, com pico de nível 60 — e a **decair sozinha
  até 0** quando a batalha acaba, sem nada a ticá-la. `/fc squad` com um Nob: esquadrão formado,
  6/16 membros, formação COLUMN, ordem FOLLOW, LOD FULL. §20, §21 e §22 confirmados vivos.

  **§29 — os 4 placeholders ganharam modelo** (`tools/generate_ork_assets.py`, que **importa** o
  empacotador do script Necron em vez de o duplicar — mesmo gesto que o `generate_geo_troop_textures`
  já faz sobre o `generate_troop_textures`). Referência de proporção: o `ork_boy.geo.json`, porque um
  Nob que não é obviamente um Boy maior é só um Boy de outra cor.
  - **Ork Nob**: 14 de largura contra os 12 do Boy, pauldrons e a power klaw — a assimetria é o que o
    torna encontrável dentro de um esquadrão dos próprios Boyz dele.
  - **Meganob**: parede que anda. Cabeça afundada entre blocos de ombro, pernas curtas.
  - **Gretchin**: cabeça e orelhas sobre um corpo que mal existe. A proporção é a piada.
  - **Killa Kan**: **não humanoide**, que era o ponto todo — caldeira sobre duas pernas de ave, sem
    cabeça nenhuma (a fenda de visão está no casco), serra num braço e canhão no outro.

  As 4 classes passaram a `GeoEntity`; os 4 renderers humanoides foram **apagados** (`HumanoidModel`,
  zero referências restantes) e substituídos por uma linha de `FCGeoRenderer` cada. Os PNG antigos,
  pintados para o layout vanilla, foram sobrescritos pelos novos — não fica textura obsoleta.

- 2026-08-24 (3): **§26 COMPLETO — os Necrons ganharam corpo. Modelos, UVs, texturas e animações.**
  Build verde. Servidor verificado; **render não visto** (precisa de cliente).

  O dono autorizou explicitamente fazer a arte e mandou duas referências (falange de Guerreiros sob
  céu verde com escaravelhos no chão; Senhor com ceptro, manto e coroa). Isto **substitui** a regra
  anterior de que os modelos eram trabalho dele — vale para os Necrons, não retroactivamente para os
  4 placeholders Ork do §29.

  **`tools/generate_necron_assets.py` — o gerador ATRIBUI as UVs, não as lê.** É a diferença que
  importa: `generate_geo_troop_textures.py` lê um modelo feito por humano e pinta as UVs que
  encontrar. Aqui ninguém fez o modelo — nasce no script. Então `pack()` deita a rede de seis faces
  de cada cubo na folha, escreve o (u,v) escolhido **no .geo.json** e entrega **os mesmos
  rectângulos** ao pintor. Não há segunda cópia do layout, logo a armadilha documentada nos Orks
  (`ork_nob.png` pintado para o layout vanilla contra um geo com UV próprio) é **impossível por
  construção**: um cubo que se mexeu não pode ser pintado no sítio antigo, porque o sítio antigo
  deixou de existir como número em lado nenhum.

  **Três unidades, porque os estágios já as nomeavam.** `NecronStage` lê SILENT → SCARABS →
  WARRIORS → TOMB_DEFENCES → OVERLORD desde que a camada de campanha foi escrita. Construir outra
  coisa primeiro seria construir para um relógio que a campanha não tem.

  - **Warrior** (30 HP, armadura 6): **protocolos de reanimação** — recusa a morte até 2× com 50% de
    hipótese. Feito no `die()` e não a cancelar o dano, para o abate continuar a contar: uma operação
    que precisa de 3 Necrons mortos não é enganada por um corpo que se levantou duas vezes.
  - **Scarab** (4 HP, veloz): fraco de propósito. A ameaça é aritmética.
  - **Overlord** (120 HP): não dá dano extra — **zera a contagem de reanimações** dos Guerreiros a 16
    blocos. Reset e não cura, porque cura seria uma segunda barra de vida invisível; reset lê-se como
    "levantaram-se outra vez", que o jogador vê e pode responder. A jogada que daí sai é a certa:
    matar a coroa primeiro.

  **Zero alterações ao `FirstCrusadeFactionManager`**: qualquer `Monster` que ele não reconhece cai em
  `HOSTILE`, o que é exactamente certo — os Necrons não são inimigos do Imperium, são de toda a gente,
  e os Orks descobrem isso sozinhos. A decisão documentada de não alargar `FirstCrusadeFaction`
  aguenta-se.

  **Sem spawn eggs, de propósito.** Todas as outras facções têm; a dos Necrons chega quando a tumba
  decide. Um ovo tornaria o relógio de 100 pontos uma coisa que se salta.
  `NecronAwakeningSpawner` põe-nos em anel de 20-32 blocos, com teto do que está **vivo** por perto
  (14), a cada 600 ticks, e só em chunk já carregado.

  **Medido em servidor:** as três invocam e os nomes resolvem; seis Guerreiros levaram 1000 de dano
  cada e **quatro sobreviveram** (50% esperado, dentro da margem). Zero exceptions.

  **Reproporcionei os dois humanoides depois de ver o preview:** torso 8×10 lê como frigorífico à
  escala do Minecraft. A correcção foi partir o tronco em peito estreito sobre cintura mais estreita
  ainda — esse estrangulamento é a única coisa que separa "esqueleto" de "robô" a vinte blocos.

- 2026-08-24 (2): **§26 — os Necrons entram na guerra sem uma única textura.**
  Build verde e **verificado em servidor** (única fatia recente que foi vista a correr).

  O despertar era 0-100 com 5 estágios, e cruzar um **escrevia uma linha de log**. Mais nada lia.
  As entidades não existem e **não podem ser inventadas aqui** (é o que o §29 proíbe) — mas uma facção
  não precisa de corpos para tomar chão, e o mapa deste planeta já tinha `NECRONS` em 6 dos 7 setores.

  Agora uma tumba a acordar empurra o próprio planeta **pelo mesmo `applyPressure`** que os
  assentamentos usam, dividido pela mesma defesa de setor. Abaixo de WARRIORS não faz nada (escaravelho
  não toma pouso); daí para cima a pressão cresce um degrau por estágio. Nada spawna: o planeta
  simplesmente deixa de ser teu.

  **O bug que isto quase criou, e que era invisível até haver um segundo inimigo:** `applyPressure`
  entregava o extremo negativo **sempre aos ORKS** — correcto enquanto eles eram o único inimigo que
  empurrava, e errado no instante em que outro passou a empurrar. A tumba teria entregado a própria
  zona de pouso a uma WAAAGH! inexistente, num mundo sem Orks. Agora o atacante é um parâmetro; a
  versão de 2 argumentos delega com ORKS, portanto **nenhum chamador existente mudou**.

  **Medido:** SCARABS não mexe; WARRIORS vira o `landing_zone` de +12 para -12; em OVERLORD o log diz
  `LANDING_PAD changed IMPERIUM -> NECRONS` (**não** ORKS) e o planeta fecha 100% Necron.

  `TOMB_CHILL` (§5) passou a ler o despertar: subterrâneo enquanto a tumba dorme, **à superfície**
  a partir de TOMB_DEFENCES. É o único retorno que o jogador tem de que o número está a mexer,
  já que os Necrons ainda não têm corpo para lho mostrar.

  Comando novo `/fcstrategy planet awaken <n>`: o despertar só sobe com jogador no mundo-tumba, o que
  fazia do único relógio de 100 pontos da campanha a coisa mais difícil de observar.

- 2026-08-24: **§14-15 — veículos ligados à economia.** Build verde. **Não testado em jogo.**

  O Battle Tank existia e só se obtinha por **spawn egg**. Ao mesmo tempo `SectorType.VEHICLE_FACTORY`
  existia, estava no blueprint do Forge World, e produzia plasteel que ninguém gastava. Duas metades
  de uma feature, cada uma completa, sem nada no meio.

  **`ArmourRequisition`** + botão **Requisitar Blindado** na aba Military do Core.

  **O gate não é um total de recurso — é a posse de um lugar.** O Imperium põe blindado em campo
  porque segura uma fábrica de veículos algures na Cruzada; se os Orks tomarem esse setor, os tanques
  param **em todo o lado**. É a primeira coisa no mod em que perder chão num planeta se sente nas mãos
  noutro, que é para o que a camada de campanha foi construída. Fábrica **disputada não conta** —
  fábrica com combate dentro não despacha.

  **Atenção, é um ponto único:** `forge_world.vehicle_factory` é o **único** VEHICLE_FACTORY do jogo.
  Consequência deliberada e dramática, mas convém saber. Consequência secundária que **não** era
  óbvia: numa save onde ninguém visitou o Forge World a frente nunca foi ativada, logo o setor **não
  existe** e não há tanque. Isso é um gate de progressão razoável, mas a mensagem de recusa foi
  reescrita para o dizer ("o Forge World tem a única — chega lá, segura...") em vez de deixar o jogador
  a achar que é bug.

  Paga em **War Support** pelo `spendWarSupport` (checa e debita numa chamada só, como as ordens da
  Mesa), custo 60 = 6× um ASSAULT, porque um tanque tem de ler como o maior compromisso que a cidade
  faz. **Teto de 2 por cidade**, e teto e não cooldown: cooldown deixa um jogador paciente acumular
  blindado sem limite.

  O tanque já era facção IMPERIUM, então nasce alinhado sem código novo. A posição vem do
  `getBlockPos()` do próprio Core e não do pacote — o servidor já tem o valor, não há por que confiar
  no cliente para ele.

- 2026-08-22: **§5 — identidade dos planetas em runtime (1ª fatia): perigos ambientais.**
  Build verde. **Não testado com jogador** (precisa de cliente num planeta).

  `worldType` e `dangerLevel` do `PlanetDefinition` eram lidos **só pelo terminal de navegação** —
  dava para voar até Valhalla, cuja própria descrição diz que a temperatura nunca sobe acima de zero,
  e sentir exactamente o que se sentia no mundo agrícola. As descrições já eram uma promessa; isto é
  a primeira coisa que a cumpre.

  **`PlanetHazard`** — 5 assinaturas, uma por mundo: **frio** (Valhalla), **cinza** (Armageddon e
  Forge World), **esporos** (Ork World), **flora tóxica** (Catachan), **as tumbas** (Sekhet).
  Macragge, Cadia e Verdanis **não têm nenhum, de propósito**: sem um sítio onde o mundo não te tenta
  matar, "hostil" deixa de significar coisa nenhuma.

  **Só o jogador sofre.** Um Valhallano não congela em Valhalla. Aplicar isto a toda entidade viva
  seria pior ficção *e* centenas de mobs a pagar por uma checagem que quase sempre não faz nada.

  **Cada perigo tem contra-medida, e elas rimam** para se aprenderem de uma vez: o que vem pelo ar
  (cinza, esporos) para-se com um **elmo**; o **frio** para-se com **luz de bloco** (fogueira ou
  interior); Catachan **não tem contra-medida** — é o que um mundo de morte é — e por isso é uma
  *chance* e não uma regra.

  Enganchado no `PlayerTickEvent` com o mesmo throttle de 40 ticks do crescimento Ork, e faz a
  pergunta barata primeiro: `PlanetHazard.of` são comparações de chave e devolve null no overworld e
  nos três planetas sem perigo, portanto nenhum bloco é lido antes disso. Config
  `planetHazardsEnabled` (padrão ligado).

  **Damage type próprio** (`firstcrusade:ash_choke`): a mensagem de morte faz parte do tipo, e
  sufocar na cinza com `dryOut` diria "ressecou" — mensagem errada no momento em que o jogador mais
  repara. **Verificado no servidor**: o `/damage` aceita `firstcrusade:ash_choke` e recusa um id
  inventado, ou seja a entrada de datapack está registada.

- 2026-08-21 (4): **§24 COMPLETO — Squig Pen e Mek Workshop.** Build verde, `runData` rodado.
  A fatia 1 foi confirmada em jogo pelo dono; estas duas peças **ainda não**.

  Dois blocos novos (`ork_squig_pen`, `ork_mek_workshop`), construídos do painel como o Loot Pit,
  **um por campo**. Cada um mexe num número que **já existia** em vez de trazer sistema novo:
  - **Squig Pen → +4 no teto de guarnição.** Squig é o que uma horda come, então o curral é o que
    deixa o campo segurar mais Boyz. Todo caminho que cria um Boy (o tick de crescimento, o botão de
    recrutar e o próprio mostrador do painel) passou a perguntar ao **mesmo** método `garrisonCap()`,
    para o bónus não valer num e não valer noutro — e o número mostrado ser o número cobrado.
  - **Mek Workshop → +2 no tamanho da war party.** Entra na mesma soma que já tinha clã, tier e nível
    de campo, logo não há segunda regra de tamanho de horda.

  **A estrutura é lida do mundo, não de uma flag** — como o Loot Pit sempre foi. É isso que faz
  derrubar o prédio significar alguma coisa: um raider imperial que explode o Curral leva o bónus de
  guarnição junto, e ninguém precisa de ser avisado.

  **`buildLootPit` não foi refatorado para dentro do helper novo** de propósito: o teste de "já
  existe" dele **conta** poços em vez de perguntar se há um, porque `produceLoot` paga por poço.
  Dobrar os dois teria transformado a contagem num booleano exactamente no sítio onde ela precisa de
  ser número.

  **Painel sem crescer:** a fila de construção virou 3 colunas (58/58/56) em vez de 3 filas novas,
  então continua em 252 — que é o que cabe no ecrã do dono à escala de GUI 4. Conferi os retângulos
  outra vez (colisão par a par + limites): 0 problemas, último widget acaba em 250.
  As duas tooltips novas tinham `%s` e o helper genérico não passa argumentos — tirado antes de
  virar `%s` cru na tela.

  Assets: modelos à mão com textura vanilla (§43, zero PNG novo); blockstate, modelo de item, loot
  table e as duas tags `mineable` vieram do **datagen rodado**. Sem receita, de propósito: como o
  Loot Pit, estes prédios levantam-se do painel e não da bancada.

- 2026-08-21 (3): **§24 fatia 1 — o Ork Camp ganhou mãos.** Build verde. **Nada testado em jogo**
  (precisa de um jogador Ork; a via RCON não alcança).

  O Camp já era grande (803 linhas: economia de loot, crescimento, Loot Pit construído no mundo,
  war parties, Warboss, espalhamento). O que faltava não era sistema, era **agência**: tudo acontecia
  no relógio dele e o jogador só olhava.

  **A descoberta que decidiu a fatia:** `launchWarParty` — 50 linhas completas, com muster da
  guarnição, tamanho por clã e tier, aviso aos jogadores e ascensão do Warboss — está **inalcançável**,
  porque `ExampleMod.ORK_WAVES_ENABLED` é `false` por pedido teu. O flag desliga o camp atacar
  **sozinho**, que é uma decisão sobre o mundo rodando à revelia; nunca foi dizer que o muster está
  errado. Então a fatia não reescreveu nada disso — abriu uma porta para ele.

  **4 ações novas** (`OrkCampActionPacket`), todas no padrão que o `buildLootPit` já usava — o cliente
  manda só "cliquei", o camp re-checa tudo, e cada recusa diz qual regra falhou:
  - **Criar um Boy**: gasta loot e pula o cooldown de crescimento (é o que pagar à mão compra), mas
    **respeita o teto de guarnição** — o teto é o que impede o campo de virar um mar de Orks.
  - **Fazer um Nob**: promove o Boy mais forte — **consome-o**. Promoção, não invocação: a contagem de
    corpos não mexe, que é o que impede este botão de ser o único furo no teto de população. Exige 4
    Boyz de pé (Nob sem quem mandar é um Boy caro) e no máximo 1 Nob por nível de camp.
  - **Escolher Alvo**: percorre as cidades imperiais **deste planeta**, lidas do mapa de guerra (logo
    funciona com chunk descarregado) e ordenadas por distância, para o ciclo ser estável entre cliques.
  - **WAAAGH!**: o jogador manda o muster agora. É a porta para o `launchWarParty`.

  **Faction check num lugar só** (`ifOrk`), não em cada método — para a quinta ação não entrar sem ele.
  A tela esconde os botões de quem não é Ork, mas esconder é cortesia; a regra é o servidor.

  **Painel**: 200×200 → 200×252 **só para o Ork**. A vista Imperial ficou byte a byte a mesma — mesmas
  posições, mesma altura. Botões acinzentam só pelo que o cliente realmente sabe (loot e um teto que
  lhe foi dito); Boyz de pé e teto de campo são do servidor, que responde com motivo.

  **Falta do §24** (fatia 2): Squig Pen e Mek Workshop — precisam de bloco novo, modelo, loot, receita
  e `runData`, ao contrário destas quatro que não pediram um único asset.

- 2026-08-21 (2): **A Mesa de Guerra foi aberta em jogo pela 1ª vez. 4 defeitos visíveis no
  screenshot, corrigidos.** Build verde; a aba Logistics foi re-verificada em servidor, a tela em si
  **ainda não** foi revista depois do conserto.

  O dono abriu a aba LOGISTICS e mandou o print. Foi a primeira vez que essa tela existiu na tela de
  alguém, e ela entregou de uma vez o que nenhuma leitura de código tinha pego:

  - **O título dizia "Imperial Strategium".** A Mesa é explicitamente *não* o Strategium (pesquisa é
    o que o Imperium constrói, a Mesa é a guerra que ele luta) — e o emaranhado era duplo, porque o
    outro bloco se chamava **"Strategium (War Table)"** em en e **"Mesa de Estratégia"** em pt. Três
    nomes para dois móveis. Agora: `screen…war_table` = "War Table"/"Mesa de Guerra",
    `block…strategium` = "Strategium" nos dois idiomas. **O nome pt do Strategium mudou — se preferes
    "Mesa de Estratégia" de volta, é uma linha.**
  - **A linha "12 supply lane(s) cut" saía cortada pela moldura.** O cabeçalho reservava 26px para um
    título mais duas linhas alinhadas à direita, e a segunda ia até y+26 enquanto o divisor era
    desenhado em `HEADER_HEIGHT - 2` = y+24 — em cima do texto. `HEADER_HEIGHT` 26 → 32. Só aparece
    quando há rota cortada, que é por que sobreviveu até um save real com doze delas.
  - **Metade da tela estava em português dentro de uma UI em inglês.** `SupplyNetwork.reasonFor`
    montava frases (`"spaceport de agri_world em mãos inimigas"`) no **servidor**, que não sabe o
    idioma de quem lê. Agora a rota guarda **chave + argumento, ambos chaves de tradução**, e quem
    desenha resolve. O truque que faz um caminho só servir para os dois casos: o argumento sempre
    passa por `Component.translatable` — chave de planeta vira o nome do planeta, e nome de recurso
    (`Food`) não tem entrada e sai como está. 3 chaves novas (`supply.firstcrusade.reason.*`).
  - **De brinde, os planetas deixaram de aparecer como id cru:** era "spaceport de **agri_world**",
    virou "spaceport de **Agri World Verdanis**", porque o argumento agora é a chave do planeta.
  - **Acentos**: as chaves pt da campanha tinham sido escritas sem acento ("Logistica", "Operacoes",
    "Forcas em movimento", "Destruida", "Deposito"). Corrigidas — acento renderiza bem, o próprio
    print provou.

  **Migração:** `SupplyRoute` ganhou `ReasonArg` no NBT. Save antigo tem a frase em português no campo
  `Reason`; ela **não é migrada de propósito** — chave sem tradução renderiza como ela mesma, então
  lê exatamente como lia, e o primeiro passe estratégico depois de carregar já a substitui.

  **O que no print NÃO era bug:** o texto fantasma esmaecido atrás do rodapé é a **HUD do jogo**
  (mensagem de actionbar) aparecendo através do escurecimento do `renderBackground` — o Minecraft
  desenha a HUD antes da tela. Acontece com qualquer GUI aberta enquanto uma actionbar some.

  **Verificado em servidor depois do conserto:** as duas formas de motivo resolvendo
  ("Agri World Verdanis spaceport in enemy hands", "origin produces no Plasteel") no
  `/fcstrategy supply list`, que passou a devolver `Component` em vez de `String` para não imprimir
  chave crua na cara de ninguém. **Não verificado:** a tela desenhada com o cabeçalho novo.

- 2026-08-21: **A camada de campanha foi finalmente EXECUTADA — servidor dedicado + RCON. 3 bugs
  achados e corrigidos.** Build verde.

  A camada inteira do dia 20 tinha sido escrita sem nunca rodar. Rodou agora, num servidor dedicado
  (`level-name=campaigntest`, RCON, `server.properties` **restaurado** ao fim), dirigido pelo cliente
  RCON de `tools/world_probe.py`. **Zero exceptions em toda a bateria.**

  **O que foi visto funcionando de verdade** (não é leitura de código):
  - As 10 frentes ativam e **cada planeta tem layout próprio** — Armageddon 11 setores (núcleo
    industrial imperial × oeste Ork, 65/35), Cadia 9 (8 obras defensivas × 1 acampamento de cerco),
    Ork World 8 (1 pad imperial × 7 Ork), Necromunda 5. **Nenhum vazamento entre planetas**: era
    exatamente o bug de arquitetura que o bloco 1 dizia ter corrigido, e está corrigido.
  - Controle recalculado dos setores, estado, intensidade, objetivo e `CrusadeScore`/`WarDominion`
    reagindo a `sector capture` na hora.
  - **A pressão de assentamento move o mapa sozinha**: com um Ork Camp posto em Armageddon, o planeta
    saiu de 65/35/0 para 51/35/14 sem ninguém tocar em nada.
  - **O ciclo Ork completo, no timer de 200 ticks e sem jogador nenhum no planeta**: WAAAGH! acumulou,
    avisou uma vez em 75/100, lançou aos 100, e o deployment percorreu MUSTERING → MOVING →
    COMMITTED → SPENT gastando a força contra o setor alvo. O `spend()` corrigido no bloco 4 segura.
  - Logística: 14 rotas, produção por setor, `income` por frente.
  - Operações automáticas por gatilho + `operation create`, e `RESCUE` recusando com o motivo certo
    (`OperationTrigger.MANUAL`).
  - **Cliente**: `runClient` carrega o mod inteiro com a Mesa de Guerra registrada — **zero** erro de
    modelo, textura ou asset. Só isso; ver "não testado" abaixo.

  **Bug 1 — `MANPOWER` não tinha produtor nenhum.** O tipo de recurso existia, 3 rotas da Colmeia
  (armageddon/cadia/catachan) o consumiam, e **nenhum `SectorType` o produzia** — as três rotas ficavam
  `DESTROYED` ("origem não produz Manpower") em todo save que existisse. Pior: o layout da própria
  Necromunda **não tinha um único setor `HIVE`**. Agora `SectorType.HIVE` produz `MANPOWER` (a tithe de
  um mundo-colmeia é gente — é o que a §10 e o comentário das rotas já diziam) e `upper_hive` é um
  `HIVE`. As 3 rotas passaram a ACTIVE (12/12, 12/12, 10/10). **Efeito colateral a revisar:** Armageddon
  não produz mais os 12 Iron que vinham da colmeia dele — nenhuma rota quebrou, e ele já *importa* iron
  de Macragge, mas é uma mudança de economia que merece o teu olho.

  **Bug 2 — `supply list <frente>` mentia no rodapé.** Listava as rotas da frente e imprimia o total de
  quebradas **da rede inteira**: "4 rota(s), 3 sem entrega" com as 4 da tela todas ACTIVE. O contador
  agora conta o que foi impresso (`brokenLanes(Collection)`; a versão global continua para a Mesa).

  **Bug 3 — `/fcstrategy raid start` dizia "passe executado" e não fazia nada.** Numa mundo sem Ork
  Camp — ou seja, **todo save novo, que é exatamente onde alguém testa** — o comando enchia o pool,
  anunciava sucesso e deixava um `raid list` vazio sem dizer por quê. Agora `OrkOffensiveManager
  .launchBlocker` nomeia qual das 4 checagens falhou, na mesma regra da Mesa de Guerra ("cada recusa
  diz qual"). Verificado: cadia → "não há nenhum Ork Camp registrado", valhalla → "a frente está
  AVAILABLE, que não é um estado engajado", armageddon → lança normalmente.

  **Observação, não bug:** ativar Macragge e Cadia já as deixa `CONQUERED` (100% e 90%) e o Crusade
  Score sobe para 26 sem ninguém lutar — são mundos imperiais na ficção, e nada é anunciado ao jogador,
  mas "conquistados 2" aparece no `planet list` e na Mesa. Decisão de balanceamento, tua.

  **NÃO testado (precisa de teclado humano):** a **tela da Mesa de Guerra** continua sem nunca ter sido
  renderizada — o cliente sobe e registra tudo sem erro, mas o `--quickPlayMultiplayer` não conectou e
  não dá para abrir a GUI por automação confiável (GLFW ignora clique sintético). A geometria foi
  conferida na mão e fecha: botões de ordem em x 120–328 dentro da margem de 334, linha das ordens em
  y+182–198 contra tabs em y+200–216 (sem sobreposição), `enableScissor` nos dois painéis, rótulos mais
  longos ("Controle Imperial" ~86px em 105px disponíveis) cabendo. Também sem teste: o pacote de ordem
  da Mesa, materialização de deployment perto do jogador, e a camada `ai/` (supressão/cover/esquadrão).

  **Como repetir a bateria:** `run/server.properties` com `enable-rcon=true`, `rcon.password=fctest`,
  `online-mode=false`, `level-name=campaigntest`, `max-tick-time=-1` (**restaurar depois**); subir
  `runServer`; mandar comandos com o `Rcon` de `tools/world_probe.py`. `/fcstrategy planet activate
  <frente>` **não precisa de jogador**, e `execute in firstcrusade:<planeta> run fcstrategy ...` resolve
  os comandos que dependem da dimensão de quem chama.

- 2026-08-20: **§29 — auditoria de modelos provisórios. NENHUM CÓDIGO MUDOU; é levantamento.**

  A integração GeckoLib já está correta: `new FCGeoModel<>("ork_nob")` resolve geo + textura +
  animação de um nome só. Trocar um renderer é **uma linha**. O que falta são os arquivos do
  Blockbench, e inventar isso em Java é exatamente o que o §29 proíbe.

  **A distinção que importa** — nem todo humanoide é placeholder:
  - **Corretos, não mexer** (11): Guardsman, Skitarii Ranger, Kasrkin, Enforcer, Mine Guard, Agri
    Militia, Sister of Battle, Penal Legionnaire, Jungle Fighter, Feudal Knight, City Commander.
    São **humanos de armadura** — modelo humanoide é a forma certa. Precisam de textura melhor
    algum dia, não de modelo.
  - **Já com modelo próprio** (GeckoLib): Ork Boy, Warboss, Space Marine, Custodes, Guardsman
    Rifleman/Sergeant, Primarch, Sentinel Walker, Valkyrie + toda a fauna (15 espécies).
  - **Placeholder de verdade** (4): **Ork Nob**, **Meganob**, **Gretchin**, **Killa Kan**.

  **A prioridade é o Ork Nob**, e não o Killa Kan que o brief cita: o Nob lidera um esquadrão de Ork
  Boyz que **têm** modelo próprio, então todo esquadrão Ork do jogo tem um humanoide estranho parado
  no meio. O Killa Kan é o mais errado em forma (é máquina, não humanoide) mas aparece muito menos.

  **A armadilha, documentada para não custar uma tarde:** `ork_boy.geo.json` usa **UV próprio**
  (cabeça 10×9×10 em [0,0], corpo 12×13×6 em [0,20]), não o layout de skin vanilla. As texturas
  atuais de `ork_nob`/`meganob`/`gretchin`/`killa_kan` são 64×64 feitas para o humanoide vanilla e
  ficariam **embaralhadas** se apontadas para esse geo. Não dá para reaproveitar sem repintar.

  **Receita para resolver:** `tools/generate_geo_troop_textures.py` já **lê o .geo.json e pinta o
  que encontrar** — foi escrito para os dois troopers imperiais justamente por isso. Estender ele
  para os Orks é o caminho, e não editar PNG à mão.

- 2026-08-20: **§27 — troféus de fauna ganharam uso.** Build verde.

  Os 5 troféus caíam e **nada os lia** fora da própria loot table. Caçar um Cthellean Cudbear por uma
  hora dava uma pilha de itens que não faziam nada — pior que não dropar, porque ensina que caçar não
  vale a pena. Nenhum drop foi removido (§27).

  - `FaunaTrophyValue`: 9 espécimes (5 troféus + ferrão, chifre, espinho, escama) valem **tempo de
    pesquisa + XP de progressão**, via `FactionResearchManager.accelerate` e
    `PlayerProgressionManager.awardXp` — nada escreve campo direto.
  - **Entrega no Strategium**, que é onde a pesquisa é financiada: espécime avança o que o Imperium
    sabe. Não paga War Support — carcaça de xenos não produz munição de artilharia.
  - **Couro/pelo/presa/carapaça/carne ficam de fora de propósito.** São matéria-prima de crafting, e
    aceitá-los aqui tornaria as receitas a pior opção para todos eles. A regra é: *entrega-se o que é
    interessante, constrói-se com o que é útil.*
  - A checagem entra **depois** dos dois depósitos existentes e **antes** do `openInterface`, então
    quem chega de mão vazia abre o banco exatamente como sempre.

- 2026-08-20: **§28 — auditoria de áudio das armas.** Build verde.

  **O que já estava certo:** os 12 sons gravados (bolter fire/impact/aim/reload, chainsword ×4,
  choppa ×2, power klaw ×2) e seus usuários — BolterItem, Custodes, GuardianSpear, SisterOfBattle,
  SpaceMarine, LasgunShotEntity (impacto). Nada disso foi tocado.

  **O que estava provisório:** 12 sítios ainda disparavam `SoundEvents.BLAZE_SHOOT` — 8 de lasgun
  (AgriMilitia, Guardsman, JungleFighter, Kasrkin, SkitariiRanger, LasgunItem, GuardsmanRifleman,
  GuardsmanSergeant), 1 de plasma, 1 de shoota, 2 de autocanhão (Sentinel, Valkyrie).

  **O que NÃO fiz, de propósito:** apontar tudo isso para `BOLTER_FIRE`, que é a única gravação de
  arma de fogo que o mod tem. Bolter dispara projétil explosivo de massa reativa; lasgun dispara luz.
  Emprestar o bolter para toda arma faria o Imperium inteiro soar como uma arma só — pior que o
  placeholder que substituiria.

  **O que fiz:** 4 ids próprios (`lasgun_fire`, `plasma_fire`, `shoota_fire`, `autocannon_fire`)
  lastreados por som vanilla no `sounds.json`, **exatamente o padrão que `PlanetSounds` já usa neste
  mod**. O id é a costura: quando existir gravação de lasgun, é 1 `.ogg` em `sounds/weapon/` e 1
  linha no `sounds.json` — zero Java. O lasgun continua com o mesmo áudio de hoje (blaze.shoot);
  plasma, shoota e autocanhão ganharam sons distintos, porque três famílias de arma soando idênticas
  era metade do problema. 8 subtítulos novos (en/pt).

  Nenhum `BLAZE_SHOOT` restante no código.

- 2026-08-20: **Bloco 5 — supressão, cobertura, liderança e ordens de esquadrão que valem.**
  Build verde. **Nada testado em jogo.**

  **Duas coisas que já existiam e não faziam nada — foi isso que mais rendeu:**
  - `FCSquadOrder` (HOLD/MOVE/ATTACK/DEFEND/RETREAT/FOLLOW) existia desde a Fase C e o javadoc dele
    dizia em voz alta que **nada escrevia nele**. `updateState` lia a ordem, mas só para regular a
    frequência de pensamento — nenhum goal movia ninguém por causa dela. Esquadrão mandado recuar
    reportava RETREATING parado no lugar, atirando. Agora `FCSquadOrderGoal` roda **no líder** e anda
    até o destino; os seguidores não mudaram nada, porque `FCFormationGoal` já os posiciona em slots
    relativos ao líder. Um goal num mob move o esquadrão inteiro.
  - `FCCombatProfile.shouldRetreat` **não tinha nenhum chamador**. Coragem e limiar de recuo estavam
    no perfil desde que foi escrito e ninguém nunca perguntou nada — tropa lutava até o último homem
    independente dos próprios números. Agora `FCLeaderGoal.reconsiderNerve` decide, **o líder pela
    esquadra toda** (1 checagem por esquadrão num tick já estrangulado, não 1 por soldado).

  **§22 supressão (`FCSuppression`) — nenhuma unidade tica por isso:**
  - Decaimento **preguiçoso**: a entrada guarda valor + timestamp, e o nível é calculado na leitura.
    300 soldados numa batalha seriam 300 decrementos por tick para mexer num número que só importa
    quando alguém lê. Nada é agendado; o único trabalho periódico é uma varredura a cada 600 ticks
    num mapa que fora de tiroteio está vazio.
  - Entrada: **acerto que conecta**, não near-miss por projétil. Perguntar a cada projétil, a cada
    tick, quem está perto seriam centenas de queries por segundo para modelar o que um acerto já
    implica. O choque se espalha para os vizinhos **do mesmo lado** (esquadrão não se suprime
    ganhando). Só dano de projétil — levar chainsword é aterrorizante mas não é supressão, e unidade
    em corpo-a-corpo que busca cobertura só morre andando.
  - Efeitos: espalhamento de tiro (aplicado nos **dois helpers centrais** de `FCProjectiles`, então
    não dá para ficar certo no Guardsman e esquecido no Ork), **para de avançar** aos 45 (não é
    modificador de atributo — modificador deixado por unidade que morreu suprimida é debuff
    permanente em nada que ninguém acha), busca cobertura aos 60, e quebra o nervo aos 80 **se já
    estiver ferida** (senão linha de tiro nenhuma se sustenta).

  **§21 cobertura (`FCCoverGoal`):** 16 raios num anel, **não** busca por pathfinding. Só roda com
  supressão acima do limiar, com cooldown, e só se a unidade ainda tem linha de visão para a ameaça.

  **§23 liderança:** Sergeant/Nob a 14 blocos cortam 45% da supressão que chega — lê a referência de
  líder que o esquadrão já mantém, então acalmar um soldado custa um null check e uma distância.

  **Bug meu, achado antes de fechar:** o goal de ordem ficava acima dos goals de combate (necessário
  para RETREAT conseguir romper contato), mas isso valia para MOVE e DEFEND também — o esquadrão
  marchava **através** de um inimigo parado no caminho sem dar um tiro. Agora só RETREAT ignora
  combate; o resto cede, mesma regra que `FCFormationGoal` já usava.

- 2026-08-20: **Bloco 4 — comandar tropas + invasões Ork estratégicas.** Build verde.
  **Nada testado em jogo.**

  **Uma peça só para os dois lados (`campaign/force/`):**
  - `StrategicDeployment` + `DeploymentState` + `DeploymentManager`. Uma ordem do jogador e uma raid
    Ork são a mesma coisa vista de dois lados: uma força sai de um lugar, demora para chegar, e
    pressiona um setor até se gastar. Escrever como um tipo só é o que impede os dois de divergirem
    — raid que resolve por regra diferente de ataque do jogador é problema de balanceamento sem
    número comum para comparar.
  - **As três distâncias em um lugar só.** Longe: só aritmética (pressão + desgaste), funciona em
    planeta sem ninguém. Perto: materializa uma fatia **com teto** (config, padrão 12) — assalto de
    40 não é 40 mobs, é uma dúzia e o resto continua conta. Voltar para longe: **nada a desfazer** —
    são mobs comuns, e o `FCStrategicBattleData` já absorve sozinho quando o jogador sai.
  - **Não faz pathfinding.** Uma força não anda: ela espera um tempo de viagem e chega. Navegação de
    20 mobs por 400 blocos em chunk descarregado era a coisa mais cara que o sistema de raid antigo
    fazia, e não comprava nada que um timer não compre.

  **Ordens (§7) — `WarTableOrder` + `WarTableOrderPacket`:**
  - Três ordens (DEFEND/ASSAULT/REINFORCE), não oito: "proteger a cidade" é defender o setor onde ela
    está, "atacar o camp" é assaltar o setor que ele pressiona. Um caminho de validação, uma tabela
    de custo. ESCORT e TRANSFER ficam **de fora** de propósito — precisam de comboio e de tropa com
    localização, que a camada estratégica ainda não modela.
  - **O cliente não afirma nada**: manda posição da mesa + ordem + setor, e o servidor re-checa
    **seis coisas** (mesa/distância, setor existe, frente existe, dono do setor combina com a ordem,
    teto de forças, Core que pague). Cada recusa diz **qual** falhou.
  - Custo em **War Support** do Core mais próximo, via `spendWarSupport` (checa e debita numa chamada
    só — ler e subtrair em passos separados deixa janela para a mesma reserva pagar duas ordens).
    Nunca gene-seed.
  - Botões **sempre habilitados**: se a ordem é legal é pergunta sobre o estado do servidor, e cliente
    que acinzenta a partir de um snapshot decide isso por uma foto de segundos atrás.

  **Invasões Ork (§9) — `OrkOffensiveManager`:**
  - O sistema antigo **não foi religado**. `ORK_WAVES_ENABLED` continua onde estava, gateando o
    caminho velho no `StrategicWarAIManager` — ligar um não pode ligar o outro em silêncio.
  - WAAAGH! acumula **por frente** (não por camp): planeta com 8 camps lança antes e mais forte, e o
    build-up inteiro é um inteiro em vez de máquina de estados por assentamento. Teto de 2× o limiar,
    para planeta esquecido por uma semana não guardar uma raid impossível.
  - Aviso em 75% com **trava** (`notePreparationWarning`) — testar o limiar direto transmitiria a
    mesma linha a cada 10s até a raid chegar.
  - Se está no teto de forças, o pool **fica cheio**: pressão adiada, nunca perdida.
  - Respeita `TEST_WARRIOR_CAP` no mundo de teste.

  **Bugs meus, achados antes de fechar:** `spend()` exigia `materialisedStrength == 0` para marcar
  SPENT, e esse número só sobe — toda raid que pusesse um esquadrão no chão ficaria nos livros para
  sempre, pressionando zero. E os 3 botões de ordem passavam por cima do "Atualizar" (x 162-318 vs
  264-334); agora são duas fileiras.

  Novos comandos: `/fcstrategy raid list|start`. Config: 6 valores novos em `[campaign]`.

- 2026-08-20: **Bloco 3 — logística + Mesa de Guerra.** Build verde e datagen rodado.
  **Nada testado em jogo.**

  **Logística (`campaign/supply/`):**
  - `SupplyRoute` (origem/destino/recurso/`amount`/`delivered`/estado/motivo) + `SupplyState`
    (ACTIVE/DISRUPTED/BLOCKED/DESTROYED, cada um com o multiplicador de vazão que **é** o significado
    dele). Nada viaja fisicamente: uma rota é uma afirmação sobre a guerra, não um caminhão.
  - `SupplyNetwork`: **produção de uma frente é exatamente o que os setores imperiais dela produzem**,
    nada mais — é isso que faz perder a refinaria de Armageddon ser sentido nos outros planetas.
    14 rotas fixas (Verdanis alimenta, Forge World arma, Armageddon abastece de prometium, a Colmeia
    manda gente). **Uma regra de bloqueio só**: spaceport de qualquer ponta em mãos inimigas corta a
    rota; frente sob combate pesado reduz pela metade. Uma regra que o jogador aprende vence cinco
    que ele tem que descobrir.
  - `StrategicResourceType` ganhou `MANPOWER` (a Colmeia produz corpos, e a §10 pede isso).
  - Comandos `/fcstrategy supply list [frente]` e `supply income [frente]`.

  **Mesa de Guerra (`campaign/wartable/`) — bloco novo `firstcrusade:war_table`:**
  - **Não substitui o Strategium.** O banco de pesquisa continua igual: pesquisa é o que o Imperium
    *constrói*, a mesa é a guerra que ele *luta*.
  - `WarTableSnapshot`: a guerra inteira num pacote (frentes, setores, operações, rotas, renda). A
    tela **desenha o retrato e mais nada** — não calcula percentual, não decide dono, não valida. O
    único pacote que ela manda é `WarTableRequestPacket` ("manda de novo"), e o servidor
    **re-valida a posição do bloco e a distância** em cada pedido: entre abrir a tela e apertar
    atualizar dá tempo de sair da sala.
  - `WarTableScreen` (340×220, 2 abas): FRONTS com a lista de mundos + barra de controle de 3 partes,
    e o detalhe da frente selecionada (estado, intensidade, %, bases, objetivo, operações, setores,
    renda, último evento); LOGISTICS com todas as rotas e o motivo de cada corte.
    Ambas as listas cortam com **`enableScissor`**, não só desenhando as linhas de dentro — ver
    `docs/` sobre `GuiGraphics` enfileirar `drawString`.
  - Assets sem PNG novo (§43): modelo usa `cartography_table_top` + `polished_blackstone_bricks` +
    `gilded_blackstone`. Receita, loot e tag `mineable/pickaxe` vieram do **datagen** (rodado).
  - Pacotes registrados **no fim** de `FirstCrusadeNetwork.register()` de propósito: o id é a posição
    na lista, e inserir no meio renumeraria todos os seguintes.
  - **Ainda não faz**: comandar tropas pela mesa (§7). Fica para a próxima fatia, como ação própria e
    validada individualmente — botão que muda a guerra sem o servidor conferir o que o jogador tem
    para mandar não entra.

- 2026-08-20: **Camada de campanha planetária — blocos 1 e 2** (pacote `campaign/`). Build verde,
  **nada testado em jogo**.

  **Três bugs de arquitetura multiplanetária, corrigidos primeiro:**
  - `WorldWarMapData` resolvia sempre no `overworld.getDataStorage()` e guardava `BlockPos` empacotado
    **sem dimensão**. Os 9 planetas escreviam no mesmo balde: cidade em Macragge e camp em Armageddon
    na mesma coordenada eram a mesma chave, e "o camp mais próximo" podia estar em outro planeta.
    Agora é `Map<ResourceLocation, PlanetEntry>` (formato 3), **todo acessor exige a dimensão**, e
    `territoryRevision` é por planeta (antes uma cidade em Cadia invalidava chunk decorado em
    Catachan). Save antigo (formato 2) entra no balde de `FCPlanets.DEFAULT`; `pruneOrphans` (só
    chunk carregado) limpa o que sobrar, e assentamento vivo se re-registra sozinho.
  - `WorldSettlementData.planetSeeded` era **um booleano só**. O primeiro planeta visitado marcava
    tudo como povoado e nenhum outro gerava assentamento nunca. Virou `Set<String>` de dimensões;
    `PlanetSeeded=true` de save antigo migra para Macragge.
  - `FactionResearchManager.tick` era chamado **dentro do laço por planeta**. Dimensões compartilham
    o game time, então a pesquisa descontava 9× por segundo — 4 minutos viravam 27 segundos, e
    acelerava com mais planetas carregados. Agora roda uma vez por tick do servidor.
  - `StrategicWarAIData` também resolvia no overworld com chave = posição: cada planeta apagava os
    registros dos outros em `syncWithWorldMap`. Passou a ser por nível.
  - `/fcstrategy status|projects|tick|reset` procuravam `Level.OVERWORLD` pelo nome — reportavam um
    overworld vanilla vazio. Agora usam o nível de quem chamou.

  **Bloco 1 — multiplaneta + estado de guerra + setores:**
  - `StrategicLocation` (dimensão + BlockPos). `distanceTo` entre dimensões devolve `MAX_VALUE`, não
    a distância euclidiana — a resposta errada fica indisponível, não só improvável.
  - `CampaignFront`/`CampaignFrontType` (PLANET/HIVE/VOID): a unidade da campanha é a *frente*, não o
    planeta, para o Hive World e o Space Hulk futuro caberem sem sistema paralelo. Fronts vêm de
    `FCPlanets.ALL` + Hive World.
  - `PlanetWarState` por frente: controle Imperium/Orks/Necrons/contestado **recalculado dos setores**
    (nunca acumulado), `PlanetCampaignState` (8), `WarIntensity` (5), objetivo, último evento,
    `necronAwakening` 0-100 com 5 estágios (arquitetura só — nada spawna).
  - `StrategicSector` + `SectorType` (36) + `PlanetSectorBlueprints`: **13 layouts, um por mundo**, e
    é isso que dá identidade aos planetas — Cadia é 6 obras defensivas todas imperiais, Ork World é
    1 pad imperial contra um Warboss, Verdanis tem 4 de 7 setores produzindo FOOD. Captura por
    `contest` em [-100,100] dividido pela defesa do setor: sem essa banda a linha de frente pisca a
    cada passe. Nada é construído no mundo.
  - `CampaignData` (SavedData global no overworld, de propósito: a Mesa de Guerra precisa ler
    Armageddon de Macragge sem carregar a dimensão). `CrusadeScore` recalcula o `WarDominion` global
    a partir das frentes — o número virou leitura, não registro; `shift()` continua funcionando.
  - `PlanetCampaignManager`: 1 passe a cada 200 ticks, **sem query de entidade, sem chunk, sem
    pathfinding**. Pressão de assentamento move os setores sozinha.

  **Bloco 2 — Operations + integração de captura:**
  - `OperationType` (10) × `OperationTrigger`: **7 gatilhos ligados**, 3 (`RESCUE`/`ESCORT`/`RECOVER`)
    declaram `MANUAL` e **nunca são gerados** — melhor do que aparecer na lista e ser impossível.
  - Ordens saem da guerra: frente perdendo → DEFEND; sede inimiga intacta → ASSASSINATION; mundo
    tranquilo → RECON. Pagam em economia existente (Iron/Scrap no Core, War Support, pesquisa via
    `FactionResearchManager.accelerate`, XP, dominion, controle do setor). **Nunca gene-seed.**
  - `LivingDeathEvent` só custa um `instanceof` + lookup num `Set` vazio quando não há ordem de kill.
  - `CampaignIntegration`: camp arrasado, cidade perdida e kill entram por **uma porta só**.
  - Novos: `ImperialCommandCoreBlockEntity.addWarSupport`, `FactionResearchManager.accelerate`.
  - Comandos: `/fcstrategy planet list|status|activate|reseed|reset`, `sector list|capture`,
    `operation list|create|complete`, `war tick|score|reset`.
  - Config em `firstcrusade-server.toml`, seção `[campaign]` (9 valores).
  - 85 chaves de tradução novas nos 2 idiomas.

- 2026-08-11: **Progressão ORK do jogador terminada (Fases B, C, D)** — pacote `progression/ork/`,
  **38 nós** (o número 34 em qualquer comentário antigo está errado). Ver
  `docs/ORK_PLAYER_PROGRESSION.md` para o detalhe.
  - **Fase B — todo nó faz alguma coisa.** Regra: nó que não faz nada não pode cobrar Dentu. Dois
    helpers centrais: `PlayerOrkCombatModifiers` (dakka, melee, redução, fúria — responde nos **dois
    lados**, porque o cooldown de arma é aplicado no cliente também) e `PlayerOrkRewardModifiers`
    (Dentu e loot, só servidor). `ShootaItem` perdeu suas três constantes e **pergunta**.
    `PlayerOrkWorldEvents` liga a destruição do Core Imperial (`BlockEvent.BreakEvent`, prioridade
    `LOWEST`, recusa evento cancelado e criativo) a Krumpagem/Dentu/vitória — `countCoreDestroyed()` e
    `countMajorVictory()` **não tinham chamador nenhum** e o portão do Warboss pede duas vitórias, ou
    seja, Warboss era inalcançável sem comando. É também o único caminho do jogador até o WAAAGH
    global (`WaaaghOverlordManager.contributeFromGreenskinVictory`); golpe e kill nunca tocam a maré.
  - **Fase C — quatro habilidades** (`PlayerOrkAbility` + `PlayerOrkAbilityManager`): 'EADBUTT,
    WAAAAAAAAAGH!, I'Z DA BOSS e a investida. Clip contra bloco antes de procurar entidade; a
    investida testa a caixa de destino e **recusa** em vez de clipar; nenhuma adiciona velocidade
    vertical. O grito exige a barra cheia e faz **um** scan. A ordem sem alvo cai em BOYZ, OVER 'ERE,
    que captura os Boyz **por UUID** e re-pathfinda de 40 em 40 ticks, saindo na primeira linha
    quando ninguém gritou. Teclas **H, X, J, Z** (K/O/V/B/G/R já tinham dono), só enviam se `isOrk()`.
  - **Fase C — a Fúria parou de mandar o perfil inteiro.** Ela se move a cada golpe dado e recebido e
    cada ganho terminava em `PlayerProgressionNetwork.sync()` — os dois ramos, todas as contagens e o
    corpo transmitido para todo cliente que enxerga o jogador, **por espadada**. Agora é o
    `SyncOrkFuryPacket`: dois campos, um jogador, no máximo a cada 10 ticks. Carrega o par
    (valor, gameTime), não o valor, porque a decadência é aritmética sobre "quanto tinha e quando".
    `isValidFuryTarget` reaproveita `krumpFor(...) > 0` (uma definição de "isso foi briga", não duas)
    e `isValidFurySource` exige atacante vivo — cacto, lava e queda não enchem mais a barra.
  - **Fase D — tela própria** (`progression/ork/client/`): `PlayerOrkProgressionScreen` +
    `PlayerOrkTreeLayout`, **fora** da `PlayerProgressionScreen`. Cinco páginas discretas (a árvore
    Ork é travada por tamanho, então a quebra por degrau é a forma da coisa), roda = 1 página por
    entalhe com trava de 150 ms, sem zoom/arrasto/rolagem. 19 ícones de
    `tools/generate_ork_progression_icons.py`. Portão de evolução aparece como **checklist**, nunca
    como preço; o checklist e a recusa do servidor leem a mesma tabela (`...Requirements.Gate`) e o
    cinza dos nós vem do mesmo `checkBuyRules` que o servidor usa. Conferida em jogo.
  - **Armadilha de render que vale para qualquer tela:** `GuiGraphics` **não desenha texto quando
    mandado** — enfileira e esvazia por tipo de render no fim do quadro, então o rótulo da árvore sai
    *depois* de qualquer `fill`, por mais tarde que ele seja pintado. `flush()` explícito **não
    resolve**; **scissor resolve**, porque `applyScissor` esvazia e só então recorta. Modal que engole
    todo clique também não deve desenhar nada atrás de si.

- 2026-08-11: **Voo ao crescer corrigido de vez** (`PlayerProgressionSizeManager.refresh`). O teste
  era "a caixa está encostando em alguma coisa". Todo corpo do mod passa de 2.0 de altura e um vão
  interno padrão tem 2 blocos, então quem crescia dentro de casa sempre tinha alguns centímetros de
  couro cabeludo no teto — e isso contava. `makeRoom` então **teleportava o jogador um bloco para
  cima**, atravessando o piso de cima, por um encostar de 5 cm. Nunca foi a física ejetando; era o
  mod decidindo que ele precisava de resgate. Agora só age quando **nenhuma pose cabe**
  (`PlayerProgressionPose.anyPoseFits`) — estar debaixo de algo não é estar preso dentro de algo.
  Medido: Big Nob (0.92x2.38) emparedado em vão de 2 blocos, **144 amostras com `y=86.0` constante**,
  `forced=CROUCHING`, `eyeY=87.68` sob teto em 88.0 — sem teleporte e sem sufocar.

- 2026-08-06: **Base Imperial simplificada, raid iniciada pelo jogador, aba de Comando Imperial.**
  Saíram de circulação: construção estratégica por tick, gerente militar de cidade, IA de
  construção/ataque, patrulhas, mão de obra, crescimento populacional, moral e governança autônoma.
  A base agora é Core + laje 9x9 (escrita uma vez) + 4 soldados soltos, com reposição de no máximo
  1 soldado por minuto até 4/6/8/10/12 por nível (`SimpleImperialBaseBalance`). Upgrade do Core é
  puramente abstrato — nenhum bloco é colocado — e escreve a Era estratégica direto no
  `StrategicSettlementRecord`. Aba Build removida da GUI e as ações de construção passam a ser
  **recusadas no servidor**, não só escondidas. Novo pacote `assault` (8 classes) com raid
  persistente em SavedData: valida no servidor, escolhe a base elegível mais próxima pelo mapa de
  guerra, empresta **soldados reais** (sem duplicar, sem mexer no contador), teleporta a ~100 blocos
  (70 com Inserção Avançada) em terreno conferido, marcha, vence quando os defensores do camp
  acabam — inclusive para um jogador sozinho — e devolve os sobreviventes na hora. Nova árvore de
  Comando Imperial (9 nós, moeda própria: Commander XP / Command Points) numa segunda aba da tela K,
  com 9 ícones em pixel art (`tools/generate_commander_icons.py`). Comandos `/fcassault` e
  `/fccommand`. 89 chaves novas nos dois idiomas. **Medido em servidor dedicado**; dois bugs achados
  e corrigidos na medição (guarnição infinita; painel escrevendo por cima do botão).

- 2026-07-03 (2): **Vilas mais bonitas/completas + fim do friendly fire (pedido do dono)**.
  **(1) Anti-friendly-fire em 2 camadas:** novo `FriendlyFireGuard` — `hasClearShot` (checado SÓ na
  hora do disparo: aliado a ≤0.9 blocos da linha de tiro bloqueia; `strafeForClearShot` dá um passo
  lateral de 2 blocos alternando o lado pelo id → o bolo vira linha de tiro) aplicado no
  `GuardsmanLasgunAttackGoal` + nas 5 tropas-tema atiradoras (Kasrkin/Skitarii/Sister/Agri/Jungle);
  e `LasgunShotEntity.canHitEntity` — **o tiro ATRAVESSA aliados da facção do atirador** (e cidadãos
  imperiais, que são NEUTRAL) e segue até o inimigo; substituiu a proteção antiga que era só
  Guardsman→Guardsman e descartava o projétil. Vale simetricamente pros Orks. Tiros não quebram
  blocos (AbstractArrow) — nada a proteger nas estruturas. **(2) Cidades mais ricas:** muralha r24→26
  com alvenaria **desgastada determinística** (`weatheredWall`: rachada/telha por hash de posição),
  **portcullis de barras de ferro** sobre os portões + soleira gilded, **lumens pendurados em
  correntes na face interna** entre contrafortes; hab pequeno reformado (plinto blackstone, vigas de
  dark oak nos cantos, janelas de VIDRO, **porta de dark oak funcional**, barril, lumen no teto,
  **telhado zigurate escalonado** com lanterna) + **hab alto novo** (2 fileiras de janelas, cinta de
  blackstone, 3 camas); **Depósito** (stone bricks desgastado, vigas spruce, telhado com beiral de
  laje, barris) e **Campo de Treino** (piso gravel/andesito, mureta com vão, alvos de feno, postes —
  centro registrado como patrol/rally point) novos; **Quartel na fundação** (treina recrutas desde o
  dia 1); fallback de zona → subúrbio fora das muralhas quando o anel lota. **(3) Portas funcionais
  sem prender ninguém:** templates fechados do planner (Habitação/Quartel/Industrial) ganharam porta
  real (`addDoor`), e Cidadãos/Guardsmen/tropas-tema ganharam `OpenDoorGoal` + `setCanOpenDoors/
  PassDoors` na navegação. Proteção de estruturas existentes segue pelo pipeline da fatia anterior
  (footprints + validador + safeSet que não sobrescreve block entities). Build/jar OK; **não testado
  em jogo**. Tunáveis: `FIRE_LANE_RADIUS` no guard, contagens/paleta no `CityArchitect`.

- 2026-07-03: **Modelo físico das cidades refeito — layout planejado por zonas, anti-colisão e
  segurança de entidades (pedido grande do dono; W40k/brutalista)**. **(1) Fundação do sistema:**
  `CityStructureFootprint` (área reservada: origem, meia-largura/profundidade, altura, margem,
  entrada, NBT), `CityLayoutPlan` (memória espacial da cidade dentro do `StrategicSettlementRecord`:
  praça, avenidas cardeais 3-wide geométricas, muralha quadrada, zonas CIVIC/INNER/OUTER/DEFENSE/
  EXPANSION, footprints, portões/torres/pontos de patrulha; `findSlot` gera candidatos em anéis
  regulares), `CityPlacementValidator` (nunca sobre praça/avenida/Core/faixa da muralha; sem overlap
  de footprint; chão sólido; volume só de blocos substituíveis; jogador dentro = rejeita) e
  `SafeEntityRelocator` (NPCs são teleportados para fora do canteiro para um ponto seguro com chão +
  2 de ar; jogador nunca é teleportado). **(2) `CityArchitect`** — assentamento fundador W40k no
  lugar do anel de casinhas de madeira: praça xadrez de blackstone com anel dourado no Core +
  braseiros, avenidas iluminadas com postes, **muralha de deepslate com contrafortes, ameias, 4
  portões fortificados (vão 3×3 + lintel) e 4 torres de vigia de canto** (ocas, porta pra dentro da
  cidade, seteiras, topo ameado), **santuário imperial** (capela gótica com contrafortes, altar
  dourado, flecha com End Rod), 4 hab-blocks sombrios (camas, lumen interno, porta 2-alta, telhado
  com parapeito) e worksites por zona (indústria no anel externo, **fazenda fora dos portões**).
  Tudo registra footprint; caminho pavimentado da porta de cada prédio até a avenida. **(3) Pipeline
  estratégico seguro:** `reserveConstructionSite` substitui o site aleatório (zona por tipo:
  HABITATION/TRADE_DEPOT→INNER, COMMAND_BASTION→CIVIC, WALL_BASTION→DEFENSE, FARM→EXPANSION,
  indústria→OUTER; fallback EXPANSION), footprint reservado no plano ao enfileirar e liberado se a
  cidade morrer; **todos os templates ganharam porta 2-alta virada pro centro** (eram caixas seladas!)
  + paleta deepslate/blackstone; o builder progressivo **nunca sobrescreve Core/block entities e
  nunca coloca bloco em cima de ser vivo** (NPC é afastado; se ainda ocupado — ex. jogador — espera o
  próximo ciclo sem perder progresso). Core registra a cidade no war map antes de criar o plano (o
  sync podava o record). Build/jar OK; **não testado em jogo**. Tunáveis: raios/zonas em
  `CityLayoutPlan.zoneBand`, paleta/medidas em `CityArchitect`. `buildSimpleSettlement` mantido sem
  uso (código morto, igual aos hive builders).

- 2026-07-02 (2): **Vilas para o teste do Comandante — par de guerra no mundo novo + comandos de
  semeadura**. Pedido do dono ("adicione as vilas para que eu consiga realizar o teste"). O layout de
  teste (`TEST_FIXED_WORLD`) plantava só 1 cidade Ork e NENHUMA vila imperial (pedido de 2026-06-23) —
  impossível testar o Comandante. **(1) Mundo novo:** `seedTestLayout` agora planta um **par de
  guerra** — vila imperial murada ao SUL (+Z 140) e cidade Ork nível 4 ao NORTE (−Z 140), frente a
  frente (~280 blocos; distância mantém o threat ≤ +3 para a IA imperial ainda tentar ofensivas por
  chance). **(2) Mundo existente (sem recriar):** novos comandos **`/fcstrategy seedcity`** (funda vila
  imperial autônoma ~48 blocos na direção do olhar) e **`/fcstrategy seedcamp`** (planta cidade Ork
  nível 3 idem, mirando a cidade imperial mais próxima) — ignoram a flag de semeadura única por mundo.
  `WorldSettlementSeeder.foundCity` e `StrategicWarAIManager.findNearestCity` viraram públicos.
  Build/jar OK; **não testado em jogo**.

- 2026-07-02: **Lorde Comandante por cidade + esquadrões militares (estilo AoE) — tropas EXISTENTES
  marcham sob um líder físico**. Pedido do dono (prompt grande de cidades/AoE; o gap real eram líder +
  squads — o resto já existia na família `Strategic*`). **(1) Lorde Comandante** (`CityCommanderEntity`
  + `CityCommanderRenderer`, entidade `city_commander`, melee com Chainsword, 70 HP/16 armadura): 1 por
  cidade imperial, surge de graça quando o assentamento chega à era **Assentamento Fortificado**;
  se morre, a cidade fica sem ofensivas até um substituto surgir (**respawn 3 min**, via
  `onCommanderDied` no `StrategicSettlementRecord`). Novo hook `countsTowardGarrisonTally()` no
  `AbstractImperialTroopEntity` para a morte dele não liberar vaga de recruta. **(2) Squads**
  (`CitySquad` + `CitySquadType` + `CitySquadOrder`): grupo de UUIDs com ordem (Seguir o Comandante /
  Reagrupar / Atacar Posição / Defender o Núcleo / Voltar à Base); o squad de ataque persiste em NBT
  dentro do `StrategicSettlementRecord` (exército em marcha sobrevive a save/load). **(3) Novo
  `CityMilitaryManager`** (tick a cada **60t** via `StrategicWarAIEvents`): ergue o comandante, avalia a
  postura — raid ativa OU threat ≥ 25 → **guarnição inteira recolhe num anel de guard posts no Core**
  (e squad em campo é chamado de volta); em marcha mantém **formação** (guard posts reapontados no
  comandante; se alguém fica >28 blocos o squad REAGRUPA e o comandante espera; a ≤24 blocos do alvo
  vira ATACAR POSIÇÃO) — reusa os guard-post goals existentes, **zero goals novos por entidade**.
  Vitória (camp arrasado) → Voltar à Base; derrota (comandante morto OU squad reduzido a <25%) →
  recua + **postura defensiva por 8 min** + dominion −2. **(4) Ofensiva estratégica agora MOBILIZA a
  guarnição existente** (a cidade esvazia ao atacar e precisa retreinar — espelho das war parties Ork):
  `maybeLaunchImperialAttack` delega a `CityMilitaryManager.tryLaunchAttack` (exige comandante vivo,
  guarnição ≥ 4 além de 3 que ficam de guarda, **cooldown de ataque 5 min**) e só gasta os recursos se
  o squad formar; `spawnImperialMarcher` (spawnar Guardsman do nada) foi REMOVIDO. Consequência: cidade
  na era Outpost não ataca (sem comandante) — combina com o design de estágios. **(5) Patrulhas**
  (`ImperialPatrolManager`) pulam tropas sob ordem de squad (`CityMilitaryManager.isSquadded`).
  `/fcstrategy status` agora mostra Comandante (ativo/ausente) + esquadrão de ataque por cidade.
  Registro completo em ExampleMod (entity + egg + atributos + renderer + creative tab); lang en/pt
  **425/425**; textura placeholder = cópia de guardsman.png (dono faz a arte). Build/jar OK; **não
  testado em jogo**. Tunáveis: constantes no topo de `CityMilitaryManager` (cooldowns, distâncias de
  formação, tamanhos mínimos). ⚠️ `TEST_FIXED_WORLD` continua true e `ORK_WAVES_ENABLED` false.

- 2026-06-22: **Acampamento Ork vira CIDADE Ork (sem tendas/fogueiras/paliçada)**. Pedido do dono
  (print: "nao quero esses acampamentos"). `OrkCampManager`: removidas **tendas** (terracotta),
  **fogueiras** (fumaça) e a **paliçada de madeira**. Agora `buildCampStructure` (nível 1) faz uma
  praça pavimentada (coarse dirt/cobble) com **cabanas Ork** (`buildOrkHut`: log+plank+barra de ferro
  na janela+telhado), totem e estandartes. `fortifyCamp` (nível 2+) faz uma **cidade murada**: muralha
  baixa de `cobbled_deepslate` com espinhos de `iron_bars` + portão, **torres de pedra**
  (`buildWatchtower` agora deepslate), e mais cabanas em anel escalando com o nível. `scatterScrap` sem
  campfires (só bigorna/caldeirão/sucata). Núcleo Ork no centro. Build OK; **não testado em jogo**.

- 2026-06-22: **Bloco do acampamento Ork vira "Núcleo Ork" (não parece mais spawner)**. Pedido do dono
  (print do cubo de tijolo vermelho). O bloco `ORK_CAMP` deixou de ser cubo `red_nether_bricks`: novo
  modelo `models/block/ork_camp.json` = **altar/núcleo em camadas** (base polished_blackstone → pilar
  nether_bricks → núcleo `magma` brilhante). Bloco agora emite luz 8 + `noOcclusion` + som NETHER_BRICKS +
  strength 4/8. Renomeado pra **"Núcleo Ork (Waaagh!)"** (en/pt) e broadcasts ajustadas (en feito; dono
  ajusta o texto pt). Funcionalmente já era o "core" da cidade Ork (governa populace/economia/crescimento).
  Build/lang OK; **não testado em jogo** (textura é placeholder vanilla; dono faz a arte final).

- 2026-06-22: **Fix casas debaixo da terra (superflat) + cidades Orks crescem devagar (sem fountain)**.
  **(1) Underground:** `WorldGenPlacement.groundPlacement` agora força `level.getChunk(x>>4,z>>4)` ANTES
  de ler o heightmap — sem isso, assentamento semeado num chunk ainda não gerado (distante) lia uma
  superfície baixa falsa e era construído enterrado (bem visível no superflat). **(2) Orks deixam de ser
  spawner:** `OrkCampBlockEntity` não mantém-mais-até-o-cap. Agora `foundIfNeeded` spawna o grupo inicial
  UMA vez (lvl1 = 2 grots + 6 boyz; cidades semeadas lvl3 = 4 grots + 8 boyz), e `growSlowly` adiciona **no
  máx 1 Ork a cada ~30s** (`GROWTH_INTERVAL_CYCLES=3`), grots primeiro e Boyz pagos com loot — nunca um
  refill instantâneo. `seedAsCity` deixou de dar loot pra encher a guarnição de uma vez (loot=60). War
  parties já mobilizam Boyz existentes (a cidade esvazia ao atacar e reconstrói nesse ritmo lento). NBT
  `Founded`/`GrowthCooldown`. Build OK; **não testado em jogo** (testar em mundo NOVO superflat: as casas
  ficam na superfície? os Orks aparecem aos poucos em vez de jorrar?).

- 2026-06-22: **Mesa de Estratégia (pesquisa paga) + economia das cidades Orks + MODO TESTE (sandbox)**.
  **(1) Pesquisa AoE/MineColonies:** novo bloco **`StrategiumBlock`** ("Mesa de Estratégia") + BE/Menu/
  Screen/ActionPacket + `FactionResearchData` (SavedData) + `FactionResearchManager`. Abastece a mesa
  com Ferro/Sucata (click direito) e paga p/ **pesquisar a próxima Era** (timer); ao concluir, sobe o
  tier da Cruzada (`ImperiumOverlordData.ensureAtLeastTier`). As **boss bars do topo foram removidas**
  (escolha do dono: progresso só na mesa). GUI desenhada com `fill()` (sem textura; bloco usa
  cartography_table placeholder). Registrado em ExampleMod (block/item/BE/menu/creative/packet/
  MenuScreens). Lang `gui/msg.firstcrusade.strategium.*` + `research.*`. **(2) Cidades Orks = economia
  de verdade (deixaram de ser spawner):** `OrkCampBlockEntity` agora roda **Grots (populace) → Loot →
  Boyz**. `maintainPopulace` (Gretchin grátis por nível), `produceLoot` (grots geram loot), `recruitWarriors`
  (Boyz **custam loot**, até o cap), `launchWarParty` **mobiliza Boyz EXISTENTES** (não cria do nada — a
  guarnição esvazia ao atacar e precisa ser refeita pela economia), crescimento de nível gasta loot +
  exige populace cheia. NBT `Loot`. `seedAsCity` dá loot inicial. **(3) MODO TESTE** (`ExampleMod.
  TEST_FIXED_WORLD = true`, `TEST_WARRIOR_CAP = 50`): **sem mobs vanilla** (handler em `onEntityJoinLevel`
  cancela todo Mob namespace `minecraft` — passivo E hostil); seeder planta **5 cidades Imperiais ao SUL
  (+Z) e 5 cidades Orks ao NORTE (-Z)** apontadas uma na outra (`seedTestLayout`); cidades Imperiais **não**
  semeiam camps/raids próprios; ambas **capadas em 50 guerreiros**; Orks nascem nível 3. Imperializador de
  vila vanilla desligado no modo teste. **Mundo: dono cria como SUPERFLAT** (sem mexer no worldgen).
  ⚠️ Pra voltar ao jogo normal, **`TEST_FIXED_WORLD = false`**. Build/jar OK; **não testado em jogo**.
  ⚠️ Perf: 5+5 cidades a 50 = muitas entidades (centenas) — baixar `TEST_COUNT_PER_SIDE`/caps se travar.

- 2026-06-22: **Balanceamento estilo AoE: Orks mais lentos + barras de Era + Império mais rápido**
  (pedido do dono via print: orcs spawnando rápido demais; quer barra de pesquisa/progresso AoE/
  MineColonies p/ os dois lados; Império começar com mais aldeões e evoluir mais rápido). **(1)
  Orks contidos:** `OrkCampBlockEntity` — guarnição 4→3, `WAAAGH_PER_CYCLE` 8→5, `WAAAGH_THRESHOLD`
  100→150, war party 4→3, aceleração por tier `*3`→`*2`, spread `SPREAD_MIN_TIER` 2→3 + `SPREAD_CHANCE`
  12→24. **Teto de enxame** novo (`WAR_FIELD_ORK_CAP` 20 em raio 56): antes de soltar war party o camp
  conta Orks vivos perto da cidade-alvo e **segura o ataque** se já estiver saturado (orcs são
  persistentes e empilhavam num exército infinito — causa do print). Raids da cidade também reduzidas
  (`getRaidChance` ~−40%, `getRaidCooldownDays` +1~2 dias). **(2) Barra de Era (research bar):** novo
  `FactionProgressBars` — duas **ServerBossEvent** no topo da tela (Cruzada=azul, WAAAGH!=verde) que
  enchem rumo à próxima Era (tier 0-4), lendo `getProgressToNextTier()` (novo) dos overlords. Sem GUI/
  pacote (boss bar sincroniza sozinha); driven em `StrategicWarAIEvents`. Lang `bar.firstcrusade.*`
  (en/pt). **(3) Império mais rápido:** thresholds da Cruzada baixados (300/1100/3000/7000) e do WAAAGH!
  **subidos** (900/3000/9000/22000) → Império "pesquisa"/sobe de Era na frente; `AUTONOMOUS_START_POPULATION`
  12→18, `AUTONOMOUS_GARRISON` 6→8, `recruitGate` base 3→2, `upgradeGate` base 4→3. **A IA-bot estilo AoE
  já existia** (Governador autônomo: economia via worksites → recruta → evolui de nível → marcha no camp)
  — só foi acelerada/balanceada. Build/jar OK; lang válida; **NÃO testado em jogo** (dono testa em mundo
  novo). Tunáveis: constantes em `OrkCampBlockEntity`, thresholds nos `*OverlordData`, `AUTONOMOUS_*`.

- 2026-06-22: **Fações se atacam de verdade + Mapa de Guerra do mundo todo** (pedido do dono via print:
  Orks e tropas paradas a ~50 blocos sem se enfrentar). **(#1 — guerra):** a causa era o **FOLLOW_RANGE**
  curtíssimo (Ork Boy 28, Guardsman 32) = só enxergavam inimigo a <30 blocos. Novo handler central
  `ExampleMod.onEntityJoinLevel` põe **FOLLOW_RANGE 96** em toda unidade de combate Imperium/Ork ao
  nascer (exceto Cidadãos e Custodes, que guardam o Core) → elas detectam o inimigo do outro lado do
  campo, **largam o posto e marcham pra cima** (a IA de ataque navega até o alvo, sobrepondo patrulha/
  guarda). Investida autônoma das cidades mais frequente (`OFFENSIVE_MIN_TROOPS` 6→4, `OFFENSIVE_CHANCE`
  6→3). **(#3 — mapa mundial):** novo `WorldWarMapData` (SavedData no overworld) registra **toda cidade
  (Core) e todo camp** do mundo — auto-registro no tick (cobre existentes/novos) + remoção no
  `onRemove` dos blocos (raze/quebra). `computeWarTable` reescrito: plota **todos os assentamentos do
  mundo** (cidade=azul, camp=verde) com **escala auto-ajustável** (`statMapRange` = distância do
  assentamento mais longe, teto 2600 = world border) → o minimapa agora **abrange o planeta**, não só
  raio 96. Blips 8→**32** (DATA_COUNT 89→**162**, governador realocado p/ 157-160 + range 161). Removida a
  varredura de unidades por raio (mapa agora é estratégico/barato). Lang en/pt **386/386**. Build/jar OK;
  **não testado em jogo.** Tunáveis: `WAR_FOLLOW_RANGE`, `WAR_MAP_MAX/MIN_RANGE`. ⚠️ FOLLOW_RANGE 96 faz
  a guarnição inteira sortir contra Orks a até 96 blocos (deixa a cidade mais exposta — é o pedido).

- 2026-06-22: **Facção do jogador LIGADA no combate (amigo/inimigo)**. Dono mandou "pode ligar". O efeito
  base é a matriz de alvos: `FirstCrusadeFactionManager.getFaction(Player)` agora devolve a **facção
  escolhida** (novo `getPlayerFaction`, lê `PlayerFactionData`). Consequência (via `canAttack`, já
  existente): jogador **IMPERIUM** = aliado das tropas Imperiais, caçado pelos Orks (como antes);
  jogador **ORKS** = **ignorado pelos Orks** e **atacado pelas tropas Imperiais**; UNCHOSEN/cliente =
  PLAYER (comportamento antigo). Sem quebrar nada: `ThreatAssessmentManager` só conta `Mob` (player não
  conta); `OrkCampBlockEntity.countFaction` passa a contar o player na facção dele (luta junto = efeito
  desejado). **Nota/efeito colateral:** com a matriz atual, `HOSTILE` (mobs vanilla) não atacam `ORKS`,
  então um jogador-Ork também é ignorado por zumbis/etc. (deixado assim de propósito — Orks são temidos;
  fácil mudar se o dono quiser). Build/jar OK; **não testado em jogo**. Outros efeitos (GUI/tropas/spawn
  por facção) seguem em aberto pro dono definir.

- 2026-06-22: **Escolha de facção no 1º login (estilo Origins) — Imperium × Orks**. Pedido do dono: ao
  entrar no mundo, uma tela obriga o jogador a escolher um lado. **O que faz HOJE:** persiste a escolha
  por jogador; **os efeitos no gameplay o dono vai definir depois** (gancho pronto). Novos: `PlayerFaction`
  (UNCHOSEN/IMPERIUM/ORKS), `PlayerFactionData` (SavedData no overworld, UUID→facção, padrão WaaaghOverlordData),
  `OpenFactionSelectPacket` (S→C, abre a tela via `DistExecutor`+`FactionSelectClient`), `SelectFactionPacket`
  (C→S, grava 1×/jogador + msg de confirmação), `FactionSelectScreen` (tela não-fechável: Esc não fecha,
  `isPauseScreen`, 2 botões Imperium/Orks). Gatilho em `ExampleMod.onPlayerLoggedIn`: se `!hasChosen` →
  manda `OpenFactionSelectPacket` pro player. 3 pacotes no `NETWORK_CHANNEL` (commonSetup). Lang en/pt **385/385**.
  **Acessor pra ligar efeitos depois:** `PlayerFactionData.get(overworld).getFaction(player)`. Build/jar OK;
  **não testado em jogo** — dono testa: mundo NOVO + `da run`, a tela aparece ao entrar? escolhe, fecha e
  manda a msg? relog não pergunta de novo? **Próximo (a definir pelo dono):** o que cada facção muda
  (ex.: Orks miram o player, GUI/tropas próprias, spawn perto de cidade vs camp, etc.).

- 2026-06-22: **Governador da cidade (persona) + fronteira de construção que cresce (Slice 1, pedido do
  dono — estilo AoE IV / MineColonies)**. (1) **Governador = persona** (sem entidade): cada Core nasce
  com um `ImperialGovernorPersonality` (WARMONGER/ADMINISTRATOR/ARCHITECT/ZEALOT, enviesado pelo tipo de
  cidade) + um nome de um pool compartilhado (`ImperialGovernorManager`, id sincronizado → GUI mostra o
  nome sem rede de string). Campos/NBT no Core (`governorPersonality`/`governorNameId`/
  `governanceDelegated`), inicializados em `ensureGovernor` (1º tick, cobre cidades novas e antigas).
  (2) **Delegação:** `tickAutonomousGovernance` agora roda se `isGoverned()` = **sem dono OU dono delegou**.
  Cidade de NPC: Governador toca sozinho (como já era). Cidade do player: botão **Nomear/Destituir
  Governador** liga/desliga a governança autônoma (recruta/constrói/evolui) enquanto o dono está fora.
  Personalidade enviesa os gates de `autonomousRecruit`/`autonomousUpgrade` e dá +raio de fronteira
  (Architect). (3) **Fronteira de construção** (`getBuildBorderRadius` 16/24/34/46/60 +bônus): limite
  **rígido que cresce por nível**; as **8 estruturas** (Mine/GoldMine/Scrap/Forge/Refinery/Farm/Depot/
  Barracks) agora só constroem **dentro da fronteira** (clamp do maxRadius em cada manager). Botão **Ver
  Fronteira** pinta um anel de partículas pro dono. (4) **GUI:** aba City mostra Governador (nome+
  personalidade), Governança (Governador/Manual/Delegada) e Fronteira; 2 botões novos. Menu DATA_COUNT
  85→**89** (slots 85 personalidade, 86 nameId, 87 estado de governança, 88 raio da fronteira). Ações
  novas: `TOGGLE_GOVERNANCE`, `SURVEY_BORDER`. Lang en/pt **375/375**. **compileJava + build OK; NÃO
  testado em jogo.** Tunáveis: raios da fronteira e biases em `ImperialGovernorPersonality`/`getBuildBorderRadius`.
  **Próximo (Slice 2):** progressão estilo AoE IV (marcos/landmarks que destravam o upgrade) e, se o dono
  quiser, colocação manual de lotes (BUILDER) dentro da fronteira.

- 2026-06-21: **Fase D7 — Mesa de Guerra (minimapa tático estilo Age of Empires)**. Nova aba **War** no
  Core: minimapa centrado na cidade (anel de território azul) com **blips** — azul = unidades imperiais,
  vermelho = Orks, vermelho-escuro grande = camp Ork; + coluna de status (domínio, ameaça, tier Cruzada/
  WAAAGH!, território) e uma **barra de domínio** Imperium(azul)×WAAAGH!(verde) no rodapé. Dados: o Core
  cacheia `computeWarTable` (scan de LivingEntity num raio 96 + camp conhecido, até 8 blips) em
  `recomputeMenuStats` (só com menu aberto); sincronizado via ContainerData (DATA_COUNT 58→**85**: 58
  domínio, 59 WAAAGH! tier, 60 contagem, 61..84 = 8 blips dx/dz/kind). Abas reduzidas (48px/51) p/ caber
  6. `WarDominionData.getDominion` lido server-side. Build/jar OK; lang 354/354. **GUI não testada em
  jogo** — pedir screenshot.

- 2026-06-21: **Vitória global — placar de domínio do planeta (Imperium × WAAAGH!)**. Novo
  `WarDominionData` (SavedData, score −100..100 + `announcedSide`) + `WarDominionManager.shift`: eventos
  da guerra nudge o placar — camp arrasado **+6**, war party **−2**, tier-up da Cruzada **+10**, tier-up
  do WAAAGH! **−10**. Ao cruzar **±50** dispara **anúncio global** de triunfo (`broadcastSystemMessage`):
  `victory.imperium` / `victory.waaagh`; ao voltar pra perto de 0 (|d|<15) declara `victory.contested`
  e re-arma (guerra pode virar e ser re-vencida). Hooks em `OrkCampBlockEntity` (checkOverrun/
  launchWarParty) e `Imperium/WaaaghOverlordManager.announce`. Build/jar OK; lang 351/351; só lógica.

- 2026-06-21: **Modelo do Guardsman (Blockbench do dono) — corpo integrado**. Novo `GuardsmanModel`
  (humanoide padrão p/ manter animação + lasgun na mão + camadas de armadura, mas com os **UV offsets**
  do export do dono: head 0,0 / body 0,16 / right_arm 32,0 / left_arm 16,32 / right_leg 24,16 /
  left_leg 0,32; "hat" vazio p/ satisfazer HumanoidModel). `GuardsmanRenderer` usa a layer
  `GuardsmanModel.LAYER` (registrada em `registerLayerDefinitions`). Textura: o dono salva a PNG dele
  em `assets/firstcrusade/textures/entity/guardsman.png` (placeholder atual). **Falta (fase 2):**
  capacete + peitoral como **render layers** (modelos já recebidos do dono). Build/jar OK.

- 2026-06-21: **Corrupção v3 — creep bloco-a-bloco + dano + atrapalha produção**. (1) **Creep:**
  `OrkCorruptionManager.creepSpread` (no tick do camp, raio+6, 10 tentativas) corrompe blocos que
  **fazem fronteira com sculk existente** → a praga cresce borda a borda, não só pelo halo do camp.
  (2) **Dano:** no `onLivingTick`, unidade Imperium sobre sculk leva **1 de dano** (magic) além da
  lentidão (corrosivo). (3) **Produção:** `productionMultiplier` (amostra 12 pontos no território)
  reduz a produção diária da cidade até **-60%** conforme a corrupção dentro do território
  (`produceResourcesIfNewDay`). Build/jar OK; não testado. Tunável (atenção/chance/dano/penalidade).

- 2026-06-21: **Planeta Nether — lava mais alta + pouso seco do Spaceport**. Dono confirmou o planeta
  vermelho/lava em jogo. Ajustes: (1) `sea_level` da lava 50→**54** (mais mares de lava; tunável se
  inundar demais). (2) `SpaceportBlock.findDryLanding`: ao chegar, **busca em espiral (até 48 blocos)
  uma superfície SECA** (sem lava na superfície/abaixo) e constrói o pad + Spaceport de retorno lá —
  evita pousar/afundar na lava e garante o Spaceport **na superfície**. Build/jar OK; JSON valida; não
  testado. (Se com 54 inundar muito, baixar pra 52.)

- 2026-06-21: **C6 #2 — planeta-dimensão DISTINTO (estilo Nether) + receita Spaceport + assentamentos**.
  `planet_secundus` deixou de clonar o overworld: agora é um **mundo infernal**. **noise_settings próprio**
  (`firstcrusade:planet_secundus`): `default_block` **netherrack**, `default_fluid` **lava** (mares de
  lava no sea_level 50), superfície netherrack (densidade reusa a do overworld). **dimension_type**:
  `effects` the_nether (céu/névoa vermelha), `has_skylight` false, `ultrawarm` true, `bed_works` false,
  infiniburn nether. **dimension**: biome_source fixed **nether_wastes**. **Receita** do Spaceport
  (`recipes/spaceport.json`: 4 Placa Crusadium + 4 Bloco de Ferro + 1 Pérola do Ender). **Assentamentos
  no planeta**: `WorldSettlementSeeder` refatorado (`seedRing` genérico + `seedPlanet` com flag
  `planetSeeded` em `WorldSettlementData`); `SpaceportBlock.travel` semeia o planeta na **1ª chegada**
  (vilas+camps ao redor do pouso). Build/jar OK; JSON valida; **NÃO testado** (mundo NOVO; teto 256).

- 2026-06-21: **Cidades viraram vilas simples (estilo aldeia MC) p/ testar os sistemas**. Dono: a hive
  gigante ficou ruim/pesada pra testar; vai mandar um modelo de cidade depois. Por hora `buildAutonomousVillage`
  e `autonomousUpgrade` chamam o novo **`buildSimpleSettlement`** (anel de 8 casas de madeira leves —
  `buildVillagerHouse`: cobble + oak planks + log + janela de vidro + porta aberta + telhado de tábua +
  cama + tochas — + os 4 worksites perto do Core). **Todo o construtor da hive vertical foi MANTIDO**
  (buildVerticalHive/buildHiveCity/circular helpers/spawnGateGuards) mas **não é mais chamado** (código
  morto, warnings) — reusar quando o dono mandar o modelo. Seeder voltou a **3 vilas** (sep. 110, anel
  120–360). Teto do planeta segue 256 (inofensivo). Build/jar OK; lang 348/348. Sistemas (F3, corrupção,
  guerra autônoma, economia) intactos — só a aparência da cidade mudou pra leve.

- 2026-06-21: **Guerra autônoma — camps destrutíveis + cidades despacham expedições**. Fecha o loop da
  guerra sem o jogador. (1) `OrkCampBlockEntity.checkOverrun` (no serverTick): conta facções na
  `garrisonBox`; se **Imperiais ≥3 e superam os Orks**, o camp é **arrasado** (bloco→ar, broadcast
  `camp_razed_imperial`) — agora o lado Imperial pode tomar território (antes camp era imortal).
  (2) `ImperialCommandCoreBlockEntity.autonomousOffensive` (em `tickAutonomousGovernance`, só cidades
  sem dono): se há camp conhecido (`orkCampPos`) e a guarnição é forte (≥6), **mobiliza metade das
  tropas** e marcha sobre o camp (`getNavigation().moveTo`), broadcast `expedition`; combate via target
  goals na chegada → overrun → camp arrasado. Camps atacam cidades (já existia) × cidades atacam camps
  (novo) = fronteira viva. Build/jar OK; lang 348/348; não testado.

- 2026-06-21: **Corrupção Ork mais profunda — afeta unidades + cidades purificam**. A praga sculk ficou
  dinâmica/contestada: (1) `ExampleMod.onLivingTick` (`LivingTickEvent`, a cada 40 ticks/unidade):
  quem está **sobre SCULK** sofre efeito por facção — **Imperium = Lentidão**, **Orks = Regeneração**
  (a maré verde prospera na corrupção). (2) `OrkCorruptionManager.purifyAround`: a cidade **limpa**
  sculk de volta pra grama dentro do **território** (`getTerritoryRadius`), chamado no serverTick do
  Core (6 blocos/ciclo) → fronteira corrupção×Imperium se move com a guerra (camps espalham, cidades
  purificam). Build/jar OK; não testado. (Próximo possível: auto-espalhar bloco-a-bloco, dano além de
  lentidão.)

- 2026-06-21: **Fase F3 — Aspirante → Space Marine (só crianças escolhidas)**. Fecha o pedido original
  das 3 partes. Pipeline novo: **criança escolhida** (`ImperialPopulationManager`: ~1/4 das crianças,
  só cidade nível 3+ que colhe gene-seed, `markAsAspirant`) → cresce em **Aspirante** (campos
  `aspirant`/`implantStage`/`implantCooldown` no `ImperialCitizenEntity`; não trabalha, espera no Core)
  → **estágios de implante de órgãos** (`AspirantManager.tickAspirants` no serverTick: 3 estágios, cada
  um consome 1 Emperor Gene Seed + cooldown ~1min, broadcast `bcast.implant`) → vira **Neófito**
  (`SpaceMarineEntity.beginAsNeophyte`, `bcast.neophyte_made`) → **teste em batalha** real: o Neófito só
  ascende a Space Marine completo depois de **matar** um inimigo (`battleProven` via `LivingDeathEvent`
  em `ExampleMod`; min ~1min após o abate, fallback ~20min sem guerra) → **Space Marine**. **Promoção
  automática de Guardsman→SM DESLIGADA** (era `processAutomaticSpaceMarinePromotion`, agora não chamada)
  — só crianças viram SM. Aspirantes/crianças excluídos do trabalho (`ImperialWorkforceManager`). Tally
  militar via `registerAscendedMarine`. Build/jar OK; lang 346/346. **Não testado em jogo.** (Manual
  Guardsman→SM via item ainda existe se o dono quiser.)

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
- 2026-08-06: **Arvore virou rolagem continua** (a pedido do dono). Saiu a paginacao inteira
  (`currentPage`, `turnPage`, marcador de entrada, "continua na proxima pagina"); entrou uma faixa
  unica de 27 linhas com barra de rolagem, Page Up/Down, Home/End e setas. Nos fixos em 42/52/48/60
  em toda resolucao. Cabecalho passou a medir e quebrar os indicadores antes de desenhar — o
  "Implants: X/12" cortado na borda direita era falta disso. `ProgressionPageLayout` substituido por
  `ProgressionTreeLayout`. Medido fora do jogo: 27 combinacoes, zero estouro.

- 2026-08-06: **Layout da arvore corrigido.** Nos de 52/68/80 para 42/52/60 (piso 36/46/52);
  cabecalho em 2-3 linhas em vez de tudo numa; rodape com area propria e texto medido antes de
  desenhar; "Continua na proxima pagina" abaixo do segundo implante; rotulos reduzidos a nome curto
  + rank, e so o rank (dentro do no) quando falta linha. Geometria extraida para
  `ProgressionPageLayout`, um record de aritmetica pura que roda **fora do jogo**: 54 combinacoes de
  resolucao x GUI scale x pagina medidas, zero estouro. A primeira tentativa (laco de encolhimento)
  falhava em 44 delas — foi a medicao que pegou.

- 2026-08-06: **Interface da progressao reformulada** — 19 icones em pixel art
  (`tools/generate_progression_icons.py`, 40x40 RGBA) substituem os retangulos desenhados em
  codigo; cada um dos 12 implantes tem icone proprio. Canvas continuo com zoom/arrasto virou **6
  paginas discretas** de 2 ciclos cada, com scroll = Page Up/Down (trava de 160 ms) e as teclas
  fisicas. Nos passaram de 9/13 px de raio para 52/68/80 px, com hitbox igual ao tamanho visual,
  nome e rank sob o no. Gameplay intocado: 50 nos, 12 implantes, XP, Doutrina, cirurgias,
  habilidades, Prova de Sangue, tamanhos e persistencia iguais. **Nao testado em jogo.**

- 2026-08-06: **Progressao Imperial do jogador** (pacote `progression`, 21 classes). Arvore de 50
  nos em 12 ciclos de 3 habilidades + 1 cirurgia; 12 implantes de gene-seed; Prova de Sangue;
  ascensao a Space Marine. Jogador continua `ServerPlayer` — a transformacao e dado persistente,
  atributo, tamanho (`EntityEvent.Size`, sem Pehkui: 1,80 -> 2,35) e habilidade ativa. Quatro
  habilidades com tecla (oracao, rolamento, acido, estase) validadas no servidor. Persistencia por
  UUID em SavedData no overworld, como faccao e planetas. Comandos `/fcprogress`. Teclas K/O/V/B/G.
  247 chaves de traducao nos 2 idiomas. `AspirantManager` dos NPCs intocado.
  **Nao testado em jogo** — build limpo verde, nada visto rodando.

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
