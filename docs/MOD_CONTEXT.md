# First Crusade — Contexto e Planejamento do Mod

> Documento de referência para desenvolvimento. Mescla **a visão/planejamento**
> (conversa de design) com **o estado atual do código** (fonte de verdade para números
> e o que já existe). Quando código e visão divergem, está sinalizado.
> Mantenha este arquivo atualizado conforme os sistemas evoluem.

---

## 1. Visão geral

**First Crusade** é um mod de Minecraft com tema **Warhammer 40.000 (Imperium of Man)**.
A meta não é só adicionar itens/mobs, mas transformar o jogo numa mistura de
**Minecraft + RTS + defesa de base + city builder**. O jogador é o **comandante de uma
civilização imperial**: funda e administra uma cidade que cresce com cidadãos, estruturas
de trabalho, produção, tropas, muralhas e raids inimigas — e, no futuro, veículos, naves
e outras facções.

**Filosofia central:** o jogo **não deve depender de mineração manual comum**. A cidade
funcional substitui a mineração. Os recursos **não aparecem magicamente** no Core — devem
vir de locais reais, com cidadãos trabalhando. A cidade precisa parecer **viva**.

- **Mod ID:** `firstcrusade` · **Nome:** First Crusade · **Versão:** 0.1.0
- **Minecraft:** 1.20.1 · **Forge:** 47.4.10 (range `[47,)`) · **Java:** 17
- **Pacote Java:** `com.example.examplemod` (classe principal `ExampleMod`)
- **Canal de rede:** `firstcrusade:main` (SimpleChannel, protocolo "1")

> ⚠️ O pacote/classe ainda usa o nome do template (`examplemod`/`ExampleMod`).
> Funciona, mas é candidato a renomeação futura.

---

## 2. Loop de gameplay principal

```text
Imperial Command Core
    ↓ gera cidadãos
cidadãos trabalham em estruturas reais
    ↓ estruturas produzem recursos
recursos → construir, treinar, evoluir
    ↓ cidade cresce
inimigos atacam (Ork Raids + Ork Camps que crescem WAAAGH!)
    ↓ jogador administra defesa, produção e tropas
```

Sequência prática:
1. Jogador coloca o **Imperial Command Core** e se torna o dono. A cidade nasce com um
   **tipo aleatório** (`ImperialCityType`) que define nome, foco de produção e fator de população.
2. O Core gera **Imperial Citizens** ao longo do tempo, até a capacidade (modulada por moral).
3. Recursos são depositados/produzidos (Iron, Coal, Scrap Metal; +Gold/Emerald/Crusadium armazenáveis).
4. Constrói **postos de trabalho** (Mine, Scrap Yard, Forge, Refinery, Barracks, Habitation)
   que empregam cidadãos.
5. Treina cidadãos em **Guardsmen** (via Barracks), que patrulham e defendem a cidade.
6. **Faz upgrade da cidade** (1→5): expande estrutura física e melhora tudo.
7. Defende-se de **Ork Raids** e de **war parties** de Ork Camps próximos; vitórias dão
   recompensas e **War Support**.
8. Cidade ≥ nv3: promove Guardsmen a **Space Marines** via **Emperor Gene Seed**.
9. Endgame (cidade nv5 + Gene Seed + vitórias): surgem **Custodes** (guardam o Core) e o
   **Primarch** (lidera saídas e governa passivamente).

> **Direção de design importante:** a produção passiva atual do Core é **temporária**.
> Já implementado o início da remoção: a produção passiva de Iron/Scrap/**Coal** escala para
> baixo conforme minas/scrap yards/refinarias **com trabalhador** assumem (piso de 20% —
> `PASSIVE_PRODUCTION_FLOOR`). Gene Seed continua passivo por ora. Ver
> `getEffectiveDailyIronProduction`/`...Scrap...`/`...Coal...`.
>
> A produção diária final também é multiplicada pela **moral** (0.5×–1.25×) e pelo **foco do
> tipo de cidade** (1.5× no recurso-foco). Ver §2.1, §6.1 e §17.

---

## 3. Imperial Command Core (coração do mod)

`ImperialCommandCoreBlockEntity` — armazena todo o estado da cidade (NBT). É o centro
administrativo: armazena recursos, abre interface, gera cidadãos, constrói estruturas,
treina/organiza tropas, repara integridade, controla raids/reforços/comandos, Gene Seed
e limites de estruturas. Fica no centro; construções surgem ao redor em locais livres.

> **Regra de arquitetura nº1:** NÃO colocar tudo aqui. Lógica pesada vai em *managers*.
> ⚠️ **O Core ainda está MUITO grande — ~3170 linhas** (de longe o maior arquivo do projeto).
> Refatoração EM ANDAMENTO: as tabelas por nível já saíram para `ImperialCityLevelStats`
> (§14). Próximos alvos: custos de upgrade/recrutamento e o estado de recursos
> (→ `ImperialResourceStorage`).

### Estado persistido (NBT)
Dono (`ownerUUID`/`ownerName`), `baseName`, `cityType` (`ImperialCityType`), `cityLevel` (1–5),
`cityMorale` (0–100), recursos `iron`/`coal`/`scrapMetal`/`gold`/`emerald`/`crusadium`,
`recruitedGuardsmen`, `emperorGeneSeed`, estado de raid (`lastOrkRaidDay`, `orkRaidCount`,
`activeOrkRaid`, `activeOrkRaidTicks`, `orkRaidVictories`, `cityIntegrity` 0–100,
`raidPressureTicks`), `imperialWarSupport`, cooldowns (`reinforcementCooldownTicks`,
`spaceMarinePromotionCooldownTicks`, `primarchMourningCooldownTicks`),
`pendingSpaceMarineCandidateUUID`, `selectedSpecialistOrdinal`, estado de Ork Camp
(`orkCampSeeded`, `orkCampPos`).

### Tick (server) — reduzido em 2026-08-06
`serverTick` roda **a cada tick** só para atribuir tipo/governador e (quando alguém tem o menu
aberto) refrescar as contagens da GUI. O resto roda a cada **200 ticks** (10s):
registro no mapa de guerra, **`SimpleImperialBaseManager.tickBase`** (migração uma vez + reposição
da guarnição no máximo 1x por minuto), produção diária, redução de cooldowns, aspirantes,
Custodes, mourning do Primarch, Primarch, seed de Ork Camp, spawn/checagem de Ork Raid.

**Saíram do tick** (chamadas removidas, não desligadas): `ImperialPopulationManager.tickCitizenGrowth`,
`ImperialCityMoraleManager.tickMorale`, `ImperialPatrolManager.tickPatrols`,
`ImperialWorkforceManager.autoManageWorkforce` e `tickAutonomousGovernance`
(`autonomousRecruit`/`autonomousUpgrade`/`autonomousOffensive`).

### Tabelas por nível de cidade (1 → 5) — VALORES ATUAIS DO CÓDIGO

| Métrica | Nv1 | Nv2 | Nv3 | Nv4 | Nv5 |
|---------|-----|-----|-----|-----|-----|
| Armazenamento | 500 | 1.500 | 5.000 | 15.000 | 50.000 |
| Cap. militar (Guardsmen) | 5 | 12 | 25 | 50 | 100 |
| Cap. populacional (base) | 3 | 6 | 10 | 15 | 25 |
| Iron/dia | 5 | 25 | 100 | 400 | 1.500 |
| Scrap/dia | 3 | 15 | 60 | 240 | 900 |
| Coal/dia | 2 | 10 | 40 | 160 | 600 |
| Gene Seed/dia | 0 | 0 | 1 | 2 | 4 |
| Cap. Gene Seed | 0 | 0 | 5 | 12 | 30 |
| Raio da estrutura | 4 | 8 | 12 | 18 | 26 |
| Altura da muralha | 1 | 3 | 5 | 7 | 9 |
| Cap. Imperial Mine | 1 | 2 | 3 | 4 | 5 (=nível) |
| Cap. Gold Mine | — | 1 | 1 | 2 | 2 (≥nv2) |
| Cap. Imperial Farm | 1 | 2 | 3 | 4 | 5 (=nível) |
| Cap. Trade Depot | — | — | 1 | 2 | 2 (≥nv3) |
| Cap. Scrap Yard | 1 | 2 | 3 | 4 | 5 (=nível) |
| Cap. Imperial Forge | 1 | 1 | 2 | 2 | 3 ((nível+1)/2) |
| Cap. Promethium Refinery | 1 | 2 | 3 | 4 | 5 (=nível) |
| Cap. Barracks | 1 | 2 | 3 | 4 | 5 (=nível) |

> A **capacidade populacional efetiva** é a base acima × `getPopulationFactor()` do tipo de
> cidade (ex.: Hive 2.0×, Agri 1.4×, Fortress 0.8×). Crescimento só ocorre se moral ≥ 35.

### Custos de upgrade de cidade (nível atual → próximo)
| De | Iron | Scrap | Coal | Crusadium Plate |
|----|------|-------|------|-----------------|
| 1→2 | 100 | 60 | 30 | 2 |
| 2→3 | 500 | 300 | 150 | 6 |
| 3→4 | 2.500 | 1.500 | 750 | 18 |
| 4→5 | 12.000 | 7.200 | 3.600 | 54 |

Upgrade também constrói/expande a estrutura física e reorganiza os Guardsmen em novos postos.
**Estilo gótico imperial 40K** (`buildCityStructure` e helpers): fundação de lajota escura
(deepslate/blackstone xadrez), muralha de Deepslate Bricks com **contrafortes de Polished
Blackstone** (lanternas no topo), janelas de Iron Bars e **portão em arco**; torres de canto
altas (wallHeight+8) com battlement, flecha cônica e **domo de ouro + finial (End Rod)**;
hab-blocks escuros com pináculo dourado; estrada de Polished Blackstone com inlay de Gilded
Blackstone; no nv5 uma **flecha-catedral central** (`buildCentralSpire`).

### Rank inicial de recruta por nível
RECRUIT → GUARDSMAN → VETERAN → SERGEANT → LIEUTENANT (níveis 1–5).

### Recursos
- Depósito manual: `depositIron/Coal/ScrapMetal` e `depositAllResources`.
- Produção diária automática (`produceResourcesIfNewDay`, baseada em `getDayTime`), modulada
  por moral e foco do tipo de cidade.
- `receiveProducedResource` recebe produção dos postos (Iron/Coal/Scrap). Gold/Emerald/
  Crusadium **têm armazenamento e exibição**, mas ainda sem cadeia de produção própria.
- Consumo: `consumeEmperorGeneSeed`, `consumeCrusadium`,
  `consumeResourcesForCrusadiumPlateProduction`.

---

## 2.1 / 4. Interface do Core (GUI)

`ImperialCommandCoreMenu` (`imperial_command_core_menu`, **55 data slots**) +
`ImperialCommandCoreScreen` (320×240, **em abas**). Só o dono abre. Ações via packet
`ImperialCommandCoreActionPacket` (canal `firstcrusade:main`).

**Interface em abas** (`activeTab`, reconstruída via `rebuildWidgets`): coluna de info à
esquerda + botões de ação à direita, alternando por aba:
- **City** — tipo/nível/integridade/moral/cidadãos/soldados/ameaça/raid + Upgrade City.
- **Build** — contagem de estruturas + 8 botões de construção (Mine, Gold Mine, Scrap, Forge,
  Refinery, Farm, Trade Depot, Barracks).
- **Military** — soldados/recrutas/especialista/Gene/SM + Recruit, Cycle/Promote Specialist.
- **Defense** — integridade/ameaça/raid/cooldowns + Repair Core, Reinforcements, Rally, Fortify, Force Raid.
- **Resources** — recursos armazenados + Deposit All e **Withdraw** (Iron/Coal/Scrap/Gold/Emerald/Crusadium).

**Withdraw:** o dono pode **sacar até 1 stack (64)** de cada recurso do Core para o inventário
(vira item: Iron→iron_ingot, Coal→coal, Scrap→scrap_metal, Gold→gold_ingot, Emerald→emerald,
Crusadium→crusadium_ingot). Método no Core: `withdrawResource(player, type, amount)`. Deposit
aceita os 6 recursos. Permite usar os recursos da cidade em crafting normal.

**Enum `ImperialCommandCoreAction` (atual):**
`DEPOSIT_RESOURCES, WITHDRAW_IRON/COAL/SCRAP/GOLD/EMERALD/CRUSADIUM, BUILD_IMPERIAL_MINE,
BUILD_GOLD_MINE, BUILD_SCRAP_YARD, BUILD_IMPERIAL_FORGE, BUILD_PROMETHIUM_REFINERY, BUILD_FARM,
BUILD_EMERALD_TRADE_DEPOT, BUILD_BARRACKS, RECRUIT_GUARDSMAN, CYCLE_SPECIALIST,
PROMOTE_SPECIALIST, UPGRADE_CITY, REPAIR_CORE, CALL_REINFORCEMENTS, RALLY_DEFENDERS,
FORTIFY_DEFENDERS, FORCE_RAID_TEST`.

`ImperialMilitaryReportManager` mostra relatório de status no chat.

**A interface JÁ mostra (implementado):** tipo de cidade, nível, integridade (colorida),
moral (com rótulo: Jubilant/Content/Uneasy/Discontent/Rebellious), cidadãos (total/cap) e
desempregados, soldados, contagem e capacidade de Minas/Scrap Yards/Forges/Refinarias/Barracks
**com trabalhadores por cargo** (Miners/Scrappers/Smiths/Stokers/Training), recursos
(Iron/Scrap/Coal/Gold/Emerald/Crusadium /cap), especialista selecionado, Gene Seed +produção/dia,
status de Space Marine, **nível de ameaça** (colorido), status de raid + segundos, raids/vitórias,
cooldown de reforços.

**Tooltips/custos:** cada botão tem tooltip com título + custo, fica **desabilitado com motivo
em vermelho** quando não pode ser usado (`applyButton`/`getRecruitBlockReason`/etc.).

**Melhorias futuras da interface:**
- Texturas/arte própria (hoje é desenhada com `fill`/`drawString`, sem PNG de fundo)
- Campo de quantidade no withdraw (hoje saca 1 stack por clique)

---

## 5. População — Imperial Citizen

Entidade base da civilização (`ImperialCitizenEntity`). Substitui villagers; nasce perto
do Core, recebe cargos, anda até locais de trabalho e gera produção indiretamente.
Futuramente vira Recruit/Guardsman/especialista.

**Campos importantes:** `commandCorePos`, `workSitePos`, `job`, `citizenAgeTicks`, `workTicks`.

**Manager:** `ImperialPopulationManager` — gera cidadãos a cada **1200 ticks** (60s) se
abaixo da capacidade **e** moral permitir (≥35); conta cidadãos/desempregados (raio 96);
expõe `getCitizenCapacity` (base × fator do tipo de cidade); treina cidadão em Guardsman.

**Gestão automática:** `ImperialWorkforceManager.autoManageWorkforce` (a cada 200 ticks)
libera trabalhadores cujo posto sumiu e atribui cidadãos ociosos a postos vagos próximos
(Mine→MINER, Scrap Yard→SCRAPPER, Forge→SMITH, Refinery→STOKER). É o que faz a cidade se
auto-organizar sem microgerência.

### Empregos (`ImperialCitizenJob`)
`UNEMPLOYED, MINER, GOLD_MINER, SCRAPPER, SMITH, STOKER, FARMER, TRADER, BUILDER, RECRUIT`
→ GOLD_MINER trabalha na Imperial Gold Mine (produz Gold).
→ TRADER trabalha no Emerald Trade Depot (converte Gold em Emerald).
→ FARMER trabalha na Imperial Farm (alimenta a cidade → moral/crescimento).
→ BUILDER existe no enum mas **ainda não tem posto/lógica**.
→ STOKER trabalha na Promethium Refinery (produz Coal).
→ RECRUIT treina num Barracks e vira Guardsman ao completar o treino.

---

## 6. Estruturas de trabalho

Padrão de toda estrutura importante: **Block + BlockEntity + Manager** + registro no
`ExampleMod` + blockstate json + block model json + item model json + entrada no en_us.json.

Fluxo: botão na UI → packet → Core valida custo/limite → Manager acha local livre →
constrói → designa cidadão desempregado → cidadão anda até lá → produz. (Reabastecimento de
postos vagos é feito pelo `ImperialWorkforceManager`.)

### Imperial Mine (`ImperialWorkSiteManager`)
Custo: **20 Iron, 10 Scrap, 5 Coal**. Cap. = nível. Emprega `MINER` → produz Iron.

### Imperial Gold Mine (`ImperialGoldMineManager` + `ImperialGoldMineBlockEntity`)
Custo: **40 Iron, 25 Scrap, 15 Coal**. **Requer cidade ≥ nv2.** Cap. = `max(1, nível/2)`
(nv2–3 → 1; nv4–5 → 2). Emprega `GOLD_MINER` → produz **Gold** (premium, ciclo de **800 ticks**,
yield 1/1/1/2/3 por nível). Estrutura: variante da Mine com acentos de bloco de ouro.

### Imperial Scrap Yard (`ImperialScrapYardManager`)
Custo: **15 Iron, 5 Coal**. Cap. = nível. Emprega `SCRAPPER` → produz Scrap Metal.
Estrutura: bloco central + iron bars, anvil, cauldron, cobblestone nos cantos.

### Imperial Forge (`ImperialForgeManager` + `ImperialForgeBlockEntity`)
Custo: **30 Iron, 20 Scrap, 10 Coal**. Cap. = (nível+1)/2. Emprega `SMITH`.
Estrutura: blast furnace, anvil, smithing table, furnace, pilares de deepslate, iron block.
**Produção:** a cada 40 ticks checa se o Smith trabalhou ≥ **900 ticks** (`REQUIRED_WORK_TICKS`);
se sim, consome do Core **4 Iron, 3 Scrap, 2 Coal** → produz **1 Crusadium Plate** (dropa
como item). Conta `totalPlatesProduced`. Método no Core:
`consumeResourcesForCrusadiumPlateProduction(...)`.

### Imperial Promethium Refinery (`ImperialPromethiumRefineryManager` + `...BlockEntity`)
Custo: **18 Iron, 8 Scrap**. Cap. = nível. Emprega `STOKER` → produz **Coal**.
Estrutura: 2 furnaces, cauldron, iron bars, campfire no topo. Ciclo de **650 ticks**.

### Imperial Barracks (`ImperialBarracksManager` + `ImperialBarracksBlockEntity`)
Custo: **25 Iron, 15 Scrap, 5 Coal**. Cap. = nível. **Não** tem trabalhador fixo: treina
**Recruits**. Cada Barracks treina 1 recruta por vez (ciclo de **1200 ticks**). Estrutura:
barrel, crafting table, grindstone, fletching table, cantos de polished andesite.
Ao concluir, chama `commandCore.completeRecruitTraining(...)` → vira Guardsman.

### Imperial Farm (`ImperialFarmManager` + `ImperialFarmBlockEntity`)
Custo: **15 Iron, 5 Scrap**. Cap. = nível. Emprega `FARMER`. **Produz Food** para o estoque
(ciclo de **700 ticks**, yield 2/3/5/7/10 por nível; `receiveProducedFood`). Uma Farm com
trabalhador também conta no `foodFactor` da **moral** (bem-alimentado +15; faminto até −20).
Estrutura: campo de farmland + trigo, postes de cerca e fardos de feno.

> **Food** (`food` no Core, capacidade = storage) é um recurso **separado dos 6** (como o Gene
> Seed). É exibido na aba Resources, sacável como **Wheat**, e o **crescimento popular consome 4
> Food por cidadão** (`FOOD_PER_NEW_CITIZEN`, consumo suave — não bloqueia o início sem Farm).

### Imperial Emerald Trade Depot (`ImperialEmeraldTradeDepotManager` + `...BlockEntity`)
Custo: **30 Iron, 15 Scrap, 10 Coal**. **Requer cidade ≥ nv3.** Cap. = `max(1, nível/2)`.
Emprega `TRADER`. A cada ciclo (**800 ticks**) o Core converte **4 Gold → 1 Emerald**
(yield 1/1/1/2/3 por nível; `tradeGoldForEmeraldAtDepot`). Cria a cadeia Gold→Emerald.
Estrutura: barracas de mercado (barrels, chests, lanterns) com topo de bloco de esmeralda.

### Imperial Habitation (`ImperialHabitationBlock` + `ImperialHabitationBlockEntity`)
Bloco de moradia. **Não emprega ninguém**; cada Habitation hospeda **3 cidadãos**
(`CITIZENS_PER_HABITATION`) e é o principal fator positivo de **moral** (ver §6.2).
Construível/colocável (tem item próprio). Raio de scan da moral: 96.

### 6.1 Produção escalada por nível (yield por ciclo)
Cada ciclo de trabalho rende `getMineIronYield`/`getScrapYardScrapYield`/`getRefineryCoalYield`,
escalando com o nível da cidade (não mais fixo em 1):
| Estrutura | Nv1 | Nv2 | Nv3 | Nv4 | Nv5 |
|-----------|-----|-----|-----|-----|-----|
| Mine (Iron) | 1 | 2 | 3 | 5 | 8 |
| Scrap Yard (Scrap) | 1 | 2 | 3 | 4 | 6 |
| Refinery (Coal) | 1 | 1 | 2 | 3 | 4 |

A produção passiva de Iron/Scrap/**Coal** recua conforme as estruturas correspondentes com
trabalhador assumem (piso 20%, `getEffectiveDaily...Production`).

### 6.2 Tipos de cidade (`ImperialCityType`)
Atribuído **aleatoriamente** ao colocar o Core. Define nome, **foco de produção** (1.5× num
recurso) e **fator de população**. Semente para o roster completo em
`docs/DESIGN_WORLD_CITIES_FACTIONS.md`.

| Tipo | Nome exibido | Foco (×1.5) | Fator pop. |
|------|--------------|-------------|------------|
| CIVILISED | Civilised World City | — | 1.0 |
| HIVE | Hive City | Scrap | 2.0 |
| FORGE | Forge City | Scrap | 1.2 |
| FORTRESS | Fortress City | Iron | 0.8 |
| AGRI | Agri City | Coal | 1.4 |
| MINING | Mining City | Iron | 1.1 |

### 6.3 Moral civil (`ImperialCityMoraleManager`)
Valor 0–100 (default 50) que **eased** (±2/tick lento) em direção a um alvo computado de:
habitação (bem alojado +20; sem-teto até −30), segurança (integridade do Core ±10, raid ativa
−15, vitórias até +5), aglomeração (no cap −10; ≤metade do cap +5) e **presença do Primarch (+15)**.
- **Multiplicador de produção:** 0.5× (moral 0) → 1.0× (50) → 1.25× (100).
- **Crescimento populacional** estagna abaixo de **35** (`allowsGrowth`).
- Rótulos: Jubilant (≥80) / Content (≥60) / Uneasy (≥40) / Discontent (≥20) / Rebellious.
- **Comida:** Farms com trabalhador alimentam 4 cidadãos cada (`foodFactor`: +15 se bem
  alimentado; até −20 se faminto).

---

## 7. Recursos do mod

| Recurso | Estado | Função (planejada) |
|---------|--------|--------------------|
| Iron | ✅ ativo | Construção básica, estruturas, armas simples, muralhas, minas |
| Coal | ✅ ativo | Combustível: produção, forjas, indústria. Produzido pela Promethium Refinery |
| Scrap Metal | ✅ ativo | Reparos, tech improvisada, upgrades, militar, equipamentos |
| Crusadium Plate | ✅ ativo | Upgrades, reparo do Core, armaduras, estruturas avançadas |
| Emperor Gene Seed | ✅ ativo | Space Marines, Custodes (10) e Primarch (15) |
| Imperial War Support | ✅ ativo | Reforços, comandos militares, suporte imperial |
| Crusadium | ✅ armazenável | Custo do Primarch (32); item ingot existe; sem cadeia de produção própria ainda |
| Gold | ✅ ativo | Produzido pela Imperial Gold Mine (GOLD_MINER). Uso final (upgrades/itens) ainda a definir |
| Emerald | ✅ ativo | Obtido no Emerald Trade Depot (TRADER) trocando Gold. Uso final (comércio/reforços) a definir |
| Food | ✅ ativo | Produzido pela Imperial Farm; sustenta crescimento populacional. Separado dos 6 (saca como Wheat) |
| Ork Teeth | item existe | Drop de Orks; "moeda" temática (uso a definir) |

`ImperialResourceType`: `IRON, COAL, SCRAP, GOLD, EMERALD, CRUSADIUM`.

---

## 8. Sistema militar

### Ranks de Guardsman (`GuardsmanRank`) — valores do código
| Rank | Vida | Dano | Armadura | Lasgun | Mérito | Comando | Tier |
|------|------|------|----------|--------|--------|---------|------|
| RECRUIT | 24 | 4.0 | 5 | 5.0 | 0 | 0 | 1 |
| GUARDSMAN | 26 | 4.5 | 6 | 5.5 | 3 | 0 | 1 |
| VETERAN | 32 | 5.5 | 8 | 6.5 | 10 | 2 | 2 |
| CORPORAL | 36 | 6.0 | 9 | 7.0 | 20 | 4 | 2 |
| SERGEANT | 42 | 7.0 | 11 | 8.0 | 40 | 8 | 3 |
| LIEUTENANT | 50 | 8.5 | 14 | 9.5 | 80 | 16 | 4 |
| CAPTAIN | 65 | 10.0 | 18 | 11.0 | 150 | 32 | 5 |
| COMMANDER | 80 | 12.0 | 22 | 13.0 | 300 | 64 | 6 |

### Unidades atuais
- **Guardsman** (`GuardsmanEntity`) — soldado base; usa Lasgun, defende, ataca à distância
  e corpo a corpo. IA: `GuardsmanGuardPostGoal`, `GuardsmanKnifeAttackGoal`,
  `GuardsmanLasgunAttackGoal`, `FirstCrusadeHurtByTargetGoal`, `FirstCrusadeNearestEnemyTargetGoal`.
  Em paz, patrulha o perímetro (ver §8.3).
- **Space Marine** (`SpaceMarineEntity`) — entidade separada, promovida de Guardsman via Gene Seed.
- **Adeptus Custodes** (`CustodesEntity`) — guarda de elite do Core; só "ganho", nunca recrutado (§8.4).
- **Primarch** (`PrimarchEntity`) — unidade única e demigod; lidera saídas e governa (§8.5).
- **Orks** (`OrkBoyEntity`, `OrkNobEntity`) — inimigos das raids; Nob é a versão forte.
- **Warboss** (`WarbossEntity`) — chefe Ork que surge de um Ork Camp após várias war parties (§9).
- **Lasgun** (`LasgunItem` + `LasgunShotEntity` + `LasgunShotRenderer`) — usa Power Cell, projétil próprio.

### Space Marines (`SpaceMarineUpgradeManager`) — requer cidade ≥ nv3
- **Automático:** com Gene Seed e sem cooldown, melhor candidato é chamado ao Core; ao chegar,
  consome 1 Gene Seed, vira Space Marine, inicia cooldown de **24000 ticks** (1 dia).
- **Manual** (`upgradeNearestGuardsmanToSpaceMarine`): recursos + War Support + 1 Netherite Ingot.
  | Nível | Iron | Scrap | Coal | War Support |
  |-------|------|-------|------|-------------|
  | 3 | 750 | 450 | 200 | 25 |
  | 4 | 1.500 | 900 | 400 | 50 |
  | 5 | 3.000 | 1.800 | 800 | 100 |
- `countSpaceMarines` é usado como pré-requisito para Custodes.

### 8.1 Progressão militar
Tropas vêm da população. O botão **Recruit** designa o cidadão desempregado mais próximo
como **RECRUIT** num Barracks disponível (não cria Guardsman instantâneo). O Barracks treina
por 1200 ticks → `completeRecruitTraining` cria o Guardsman (com chapter aleatório e rank
inicial da cidade). Capacidade militar conta `recruitedGuardsmen + recrutas em treino`.
```text
Citizen → Recruit (treina no Barracks) → Guardsman → [rank-up por mérito] → ... → Space Marine → Custodes
                                              ↘ [Promote Specialist] → Especialista
```

### 8.2 Especialistas (`GuardsmanSpecialization`) — IMPLEMENTADO
Especialização é um **campo no GuardsmanEntity** (igual rank/chapter): modifica stats em
`applyRankStats`/`getLasgunDamageWithBonuses`, aparece no nome (`{tag}`) e é salvo em NBT.
Bônus por tipo (vida / dano / armadura / lasgun / vel.):

| Especialista | Vida | Dano | Arm | Lasgun | Vel | Comportamento ativo (a cada 40 ticks) |
|--------------|------|------|-----|--------|-----|----------------------------------------|
| Sniper | 0 | -1 | 0 | +6 | 0 | — (stat puro: atirador) |
| Heavy Gunner | +15 | +2 | +3 | +3 | -0.05 | — (tanque lento) |
| Melee Trooper | +8 | +4 | +4 | -2 | +0.03 | — (brawler rápido) |
| Medic | +4 | -1 | 0 | 0 | 0 | cura Guardsmen aliados num raio de 8 |
| Officer | +10 | +2 | +2 | +1 | 0 | dá Strength I a aliados num raio de 10 |
| Engineer | +6 | 0 | +1 | 0 | 0 | repara +1 integridade do Core (≤12 blocos) |

**Criação (via Command Core, owner):** botão **Cycle Specialist** seleciona o tipo
(persistido em `selectedSpecialistOrdinal`); **Promote Specialist** aplica ao Guardsman
sem especialização mais próximo. Requer cidade **nível ≥ 2** e custa Iron/Scrap/War Support
(escala por nível). Métodos no Core: `cycleSelectedSpecialist`, `promoteSpecialist`,
`engineerRepair`.

### 8.3 Patrulhas (`ImperialPatrolManager`)
Em paz, Guardsmen não ficam parados: circulam por um anel de **8 waypoints** ao redor da
cidade (raio por nível: 5/9/13/19/27), com rotação derivada do tempo do mundo (sem estado por
entidade). **Durante raid ativa o manager se desliga** e o combate/ordens do Core assumem.
A retinue do Primarch é excluída das patrulhas.

### 8.4 Adeptus Custodes (`ImperialCustodesManager`)
Nunca recrutado, só conquistado. Surge quando: cidade **nv5**, **≥3 Space Marines** já em
campo, e Gene Seed ≥ **10** (custo por Custodes). Máximo **2** por cidade. Guardam o Core e
não saem do perímetro interno. (`CustodesEntity` se vincula ao Core via `assignToCommandCore`.)

### 8.5 Primarch (`ImperialPrimarchManager`)
Unidade única e mais cara do jogo. Ascensão requer: cidade **nv5**, **≥5 vitórias** de raid,
Gene Seed ≥ **15** e Crusadium ≥ **32**, e sem cooldown de luto. Enquanto vivo:
- repara o Core passivamente (`engineerRepair(1)`/tick lento);
- **reúne uma retinue** dos 6 Guardsmen mais próximos (raio 32) que o seguem por 300 ticks
  e atacam o alvo dele (saem das patrulhas);
- quando a cidade está segura (sem raid e ameaça < Alert), **marcha sobre o Ork Camp** para
  destruí-lo na fonte;
- presença dá **+15 de moral** à cidade.

Se morrer: `onPrimarchDeath` aplica **luto** (cooldown 24000 ticks ≈ 20 min antes de outro
surgir) e **−25 de moral**.

---

## 9. Inimigos: Ork Raids, Ork Camps, Clãs

### 9.1 Ork Raids (`OrkRaidManager` + lógica no Core)
- **Disparo:** após `currentDay ≥ 1`, cooldown por nível (4,3,3,2,2 dias), chance por nível
  (20%, 28%, 36%, 45%, 55%). Só dispara com Guardsmen ou cidade ≥ nv2.
- **Composição:** Ork Boys (base 3,5,8,12,18 + min(orkRaidCount,10)); Ork Nobs (base
  0,1,1,2,3 + min(orkRaidCount/3,3)). Spawnam num anel (raio 28→80).
- **Pressão:** Orks ≤12 blocos acumulam `raidPressureTicks`; a cada 600 ticks dão dano à
  **Core Integrity** (4–8 por onda × nº de orks próximos).
- **Vitória** (sem raiders num raio 160, após ≥400 ticks): recompensa Iron/Scrap/Coal/War
  Support escalados + reparo de integridade; +1 `orkRaidVictories`.
- **Derrota** (Integrity = 0): perde metade dos recursos, penalidade de War Support,
  integridade volta a 25.
- **Timeout:** raid se dispersa após 12000 ticks. `forceOrkRaid` força para teste.
- `notifyNearbyPlayers` manda avisos de raid/eventos no chat.

### 9.2 Ork Camps (`OrkCampManager` + `OrkCampBlockEntity`) — IMPLEMENTADO
- **Seed:** quando a cidade chega ao **nv2**, o Core planta **um** camp (`trySeedOrkCamp`,
  flag `orkCampSeeded`) a 64–96 blocos, com um **clã aleatório**; guarda `orkCampPos`.
  Estrutura tosca: anel de cerca + coarse dirt.
- **Camp vivo (a cada 200 ticks):** mantém uma **guarnição de 4 Boyz** (respawna se morrem)
  e acumula **WAAAGH! +8/ciclo**. Ao atingir **100**, lança uma **war party de 4 Ork Boys**
  que marcham sobre o Core e zera o WAAAGH!.
- **Warboss:** após **3 war parties**, surge **1 Warboss** do camp (uma vez), com os modifs
  do clã, marchando contra a cidade.
- **Objetivo do jogador:** destruir o bloco do camp silencia a ameaça na fonte (alvo natural
  de saída — o Primarch faz isso sozinho quando a cidade está segura). `isCampStillThere`.

### 9.3 Clãs Ork (`OrkClan`)
Cada camp pertence a um clã que modula vida/dano/velocidade dos seus Orks (e do Warboss):
| Clã | Vida | Dano | Vel | Tema |
|-----|------|------|-----|------|
| GOFFS | 1.35 | 1.25 | 1.0 | melee brutal, resistente |
| BAD_MOONS | 1.1 | 1.45 | 1.0 | ricos, mais dakka/dano |
| DEATHSKULLS | 1.0 | 1.0 | 1.0 | saqueadores (tema scrap) |
| EVIL_SUNZ | 0.9 | 1.1 | 1.35 | speed freaks |
| SNAKEBITES | 1.45 | 1.0 | 0.95 | primitivos, muito resistentes |

### 9.4 Nível de ameaça (`ThreatAssessmentManager`)
Score = soma do peso de cada inimigo do Imperium num raio (default 96): **Ork Nob = 6**,
**Ork Boy = 3**, hostil genérico = 2. Níveis (0–4) e nomes:
| Score | Nível | Nome |
|-------|-------|------|
| ≤0 | 0 | Calm |
| <10 | 1 | Vigilant |
| <25 | 2 | Alert |
| <50 | 3 | Siege |
| ≥50 | 4 | Critical |

O Core expõe `getLiveThreatScore`/`getLiveThreatLevel`; a GUI mostra colorido. A ameaça é o
gatilho estratégico (ex.: Primarch só sai em saída se ameaça < Alert).

### 9.5 Progressão do jogador ORK (`progression/ork/`)
Um jogador que escolhe os Orks joga uma progressão **própria**, sem nada reaproveitado da Imperial:
não há XP, Pontos de Doutrina, cirurgia, implante nem gene-seed. Detalhe completo em
`docs/ORK_PLAYER_PROGRESSION.md`; o essencial:

**Três valores, e não se misturam.** `krumpScore` (reputação, nunca gasta, é o que os portões de
evolução leem), `teef` (a moeda, a única coisa que a árvore custa) e `waaaghFury` (0–100, temporário,
só o grito consome). ⚠️ O mod tem **três** coisas chamadas WAAAGH — a maré global
(`WaaaghOverlordData`), o humor local de um camp (`OrkCampBlockEntity`) e a fúria pessoal — e a única
defesa contra confundi-las é que **nunca compartilham uma classe**. Mesma regra para `teef`:
`StrategicResourceType.TEEF` é o cofre da IA Ork e não tem relação com o bolso do jogador.

**Árvore com 38 nós**, cinco ramos (BRUTAL/TUFF/DAKKA/KUNNIN/WAAAGH), ranks até **3** (não 5), com
mapa de ranks próprio dentro do `PlayerProgressionProfile` na tag `Ork`. Evolução **não se compra**:
os cinco nós de estágio custam zero e são recusados pelo verbo de compra.

**Cinco estágios, e o tamanho é a história:** Boy 2.05 → Big Boy 2.12 → Nob 2.25 → Big Nob 2.38 →
Warboss 2.60. O Warboss ultrapassa de propósito qualquer silhueta humana do mod (um Space Marine
para em 2.35). **Consequência a saber:** todos passam de 2.0, e um vão interno padrão tem 2 blocos —
então de Nob para cima o jogador anda agachado dentro de casa. Isso é o guardião de pose funcionando
(`PlayerProgressionPose`), não um bug.

**Klan é obrigatório a partir de Nob:** o servidor recusa **qualquer** compra de um Nob sem klan, e a
tela abre um modal que engole todo clique até escolher. GOFFS/EVIL_SUNZ/SNAKEBITES pagam em atributo,
BAD_MOONS em dakka e Dentu, DEATHSKULLS em saque. ⚠️ `OrkClan.applyTo` **não** é chamado para
jogador — aqueles multiplicadores são de mob e são grandes demais.

**Onde entra Dentu no jogo:** botão "GUARDA OS DENTU" no Ork Camp (o servidor conta e remove o
inventário; o cliente nunca diz quanto), elites krumpados (BIG TEEF) e Core Imperial destruído. Todos
passam por `PlayerOrkRewardModifiers.scaleTeef`.

**Quatro habilidades** nas teclas H/X/J/Z, mais a tela em K (roteada por facção). Nada roda por tick:
o grito faz um scan ao apertar, e a única coisa que persiste é a ordem "BOYZ, OVER 'ERE", que guarda
os Boyz por UUID e re-pathfinda de 40 em 40 ticks.

---

## 10. Facções (`FirstCrusadeFaction` / `FirstCrusadeFactionManager`)
Facções: `IMPERIUM, ORKS, HOSTILE, PLAYER, NEUTRAL`.

Regras:
- Imperium ataca Orks e Hostiles
- Orks atacam Imperium e Player
- Hostiles atacam Imperium e Player
- Neutrals não atacam

Objetivo: evitar aliados se atacando, padronizar combate, Orks focam defensores e player,
tropas imperiais miram inimigos corretos. `FirstCrusadeFactionManager.canAttack` é o gate de
alvo. `ImperiumChapter` dá "chapter" aos Guardsmen/Space Marines.

### Ações defensivas (`ImperialDefenseManager`) — durante raid ativa
- **Call Reinforcements** — spawna Guardsmen (1–6/nível) com rank escalado; cooldown 2400→1600.
  Custa War Support (5,10,18,30,50/nível): verifica antes, deduz após o deploy.
- **Rally Defenders** — reposiciona defensores para o Core.
- **Fortify Defenders** — buff; custa War Support (5,10,18,30,45/nível).
- **Repair Core** (`repairCity`) — 1 Crusadium Plate → +20/18/15/12/10 integridade.

---

## 11. Sistemas/estruturas PLANEJADOS (ainda não no código)

### Estruturas planejadas (faltam)
`Farm, Gold Mine, Emerald Trade Depot, Medicae Station, Armory, Vehicle Factory, Landing Pad,
Wall Gate, Defense Tower, Command Relay`.
→ Já implementados: Mine, Scrap Yard, Forge, Promethium Refinery, Barracks, **Habitation**.

### Muralhas e vila
O upgrade já constrói fundação/muralha/torres/casas/ruas. Falta **portão funcional (Wall Gate)**
e geração de vila imperial mais rica. Possível uso do cargo `BUILDER`.

### Geração de mundo (adaptada ao mod)
Menos cavernas, terreno mais plano, morros leves, menos mineração manual, mais espaço para
cidades, geração de ruínas e pontos estratégicos. (Ork Camps já são plantados dinamicamente
pelo Core, mas não há worldgen passivo ainda.)

### Veículos e naves (fase tardia — não cedo)
Vehicle Factory → produz veículo → Engineer/Guardsman opera → NPC dirige/pilota.
Tipos: caminhão de transporte, tanque leve, blindado, nave de transporte, dropship, shuttle
de fast travel. Desafios: controles, física, pathfinding, IA dirigindo, colisão, multiplayer.
**Recomendação: só começar depois que cidade, produção e guerra estiverem sólidos.**

---

## 12. Inventário atual (código)

**Itens (materiais/combate):** crusadium_ingot, crusadium_plate, ork_teeth, scrap_metal,
lasgun_power_cell, lasgun, guardsman_combat_knife, guardsman_med_kit, guardsman_command_baton.
**Armadura Guardsman:** guardsman_helmet/chestplate/leggings/boots.
**Armadura Space Marine:** space_marine_helmet/chestplate/leggings/boots (power armor; texturas
de camada ainda placeholder).
**Spawn eggs:** imperial_citizen, guardsman, space_marine, custodes, primarch, ork_boy,
ork_nob, warboss.
Aba criativa: `first_crusade_tab` (ícone: Command Core).

**Blocos:** imperial_command_core, imperial_mine, imperial_gold_mine, imperial_scrap_yard,
imperial_forge, imperial_promethium_refinery, imperial_farm, imperial_emerald_trade_depot,
imperial_barracks, imperial_habitation, ork_camp.

**Entidades:** imperial_citizen, guardsman, space_marine, custodes, primarch, ork_boy,
ork_nob, warboss, lasgun_shot.

**Managers (atuais):** ImperialPopulationManager, ImperialWorkSiteManager, ImperialGoldMineManager,
ImperialScrapYardManager, ImperialForgeManager, ImperialPromethiumRefineryManager, ImperialFarmManager,
ImperialEmeraldTradeDepotManager,
ImperialBarracksManager, ImperialWorkforceManager, ImperialDefenseManager, OrkRaidManager,
OrkCampManager, SpaceMarineUpgradeManager, ImperialCustodesManager, ImperialPrimarchManager,
ImperialPatrolManager, ImperialCityMoraleManager, ThreatAssessmentManager,
ImperialMilitaryReportManager, ImperialVillageScanner.
**Outros:** ImperialCityLevelStats (tabelas puras por nível, extraídas do Core),
ImperialSettlementType/Origin, ImperialCityType, OrkClan, ImperialResourceType,
GuardsmanRank, GuardsmanSpecialization, ImperiumChapter, GuardsmanArmorEvents, Config.

---

## 13. Assets — caminhos

**Corretos:**
```text
src/main/resources/assets/firstcrusade/textures/item/
src/main/resources/assets/firstcrusade/textures/entity/
src/main/resources/assets/firstcrusade/textures/models/armor/
src/main/resources/assets/firstcrusade/models/item/
src/main/resources/assets/firstcrusade/models/block/
src/main/resources/assets/firstcrusade/blockstates/
src/main/resources/assets/firstcrusade/lang/en_us.json
```
**ERRADO (evitar):** `src/main/resources/assets/assets/firstcrusade/` (assets duplicado).

### Data Generators (`gradlew runData`)
Pacote `com.example.examplemod.datagen` (`FCDataGenerators` escuta `GatherDataEvent`):
- `FCBlockStateProvider` — blockstates + modelos de bloco simples + item models de bloco
  (ork_camp/ork_loot_pit mantêm modelo custom handwritten em `assets/models/block/`).
- `FCItemModelProvider` — os 22 spawn eggs (template vanilla; cores vêm do `ForgeSpawnEggItem`).
- `FCBlockLootProvider` — dropSelf para **todos** os blocos (enumera o DeferredRegister:
  bloco novo sem loot faz o datagen falhar de propósito).
- `FCEntityLootProvider` — loot dos Orks (teeth/scrap; Killa Kan dropa scrap+iron).
- `FCBlockTagsProvider` — tags `mineable/*` (necessárias p/ blocos com `requiresCorrectToolForDrops`).
- `FCRecipeProvider` — todas as receitas (com advancements de unlock no recipe book).

Saída em `src/generated/resources/` (source set no build.gradle; `.cache/` fica fora do jar e do
git). **Os arquivos gerados devem ser commitados** — o build normal não roda o datagen. Ao criar
bloco/item/mob novo: adicionar no provider certo e rodar `gradlew runData`. Itens 2D/handheld
(Blockbench) e lang continuam handwritten em `assets/`.

### Hive City — Fase 2 (blocos)
Pacote `com.example.examplemod.hive` (registro autocontido, `HiveBlocks.register` no construtor
do ExampleMod): **29 blocos decorativos/estruturais** sem BlockEntity/tick (regra de performance
da Hive) — ashcrete reforçado (+ cracked/stairs/slab/wall), aço rebitado (+ rusted/stairs/slab),
blindagem (armored_hive_plating, exige picareta de ferro), grating/catwalk/railing industriais,
canos auto-conectáveis (large_hive_pipe/pipe_junction/pressure_valve, tag
`firstcrusade:pipe_connectable` em `HiveTags`), carcaça de máquina/vent, arquitetura gótica
(gothic_arch, imperial_column, cathedral_wall, relevos skull/aquila), lumens (strip 15,
yellow 15, green 13, red 10) e decoração (hazard_stripe_panel, cargo_container). Aba criativa
própria `hive_tab`. Blockstates/modelos/texturas handwritten em `assets/` (multipart complexo);
loot + tags vêm do datagen. Spec e checklist de teste: `docs/HIVE_CITY.md`. Fase 3 planejada:
módulo protótipo 64×64 de rua industrial.

Modelos de elite (`EliteModelLayers`): Space Marine/Custodes/Primarch usam layers customizadas
(esqueleto humanoide + ombreiras, mochila/vents, crista do Custodes, capa+auréola do Primarch),
texturas 128×128 geradas por `tools/TextureGen.java` no esquema de cor de cada facção. Renderers
ainda aplicam escala (1.3×/1.45×/2.0×). Detalhe fino (filigranas, espada) pede Blockbench.

Pendente provável: lang `pt_br`; texturas mais detalhadas (hoje cores de facção, sem arte fina).

---

## 14. Roadmap

### Curto prazo
- ✅ Mostrar Mine/Scrap/Forge/Refinery/Barracks na interface + trabalhadores por cargo
- ✅ Tooltips/custos nos botões + motivo de bloqueio quando inativos
- ✅ Produção passiva do Core recua conforme estruturas com trabalhador assumem
- ✅ Custo de War Support dos reforços passa a ser cobrado
- ✅ Gestão automática de mão de obra (`ImperialWorkforceManager`)
- Garantir em jogo que todo o fluxo funciona (build → cidadão → recurso → defesa)
- Melhorar a busca de local livre para estruturas

### Médio prazo
- ✅ Fonte de Coal (Promethium Refinery) + produção dos trabalhadores escala por nível
- ✅ Imperial Barracks + Recruit como processo real (Citizen → Recruit → Guardsman)
- ✅ Especialistas (Sniper, Heavy Gunner, Melee Trooper, Medic, Engineer, Officer)
- ✅ Habitation + sistema de moral
- ✅ Tipos de cidade (foco de produção + fator de população)
- ✅ Ork Camps vivos (WAAAGH!, war parties, Warboss) + clãs Ork
- ✅ Custodes e Primarch (endgame)
- ✅ Fonte de Gold (Imperial Gold Mine, GOLD_MINER)
- ✅ Imperial Farm (FARMER) + fator comida na moral
- ✅ Emerald Trade Depot (TRADER, Gold→Emerald)
- Dar **uso final** a Gold e Emerald (comércio/reforços/itens); posto para BUILDER

### Longo prazo
- Portão funcional (Wall Gate); geração de vila imperial mais rica
- Worldgen próprio; sistema de território; mapa estratégico
- Veículos; naves/dropships; outras facções
- 🔶 **Refatorar o `ImperialCommandCoreBlockEntity`** — EM ANDAMENTO: extraídos para
  `ImperialCityLevelStats` as tabelas por nível (capacidades, produção, yields, custos de
  upgrade/reforço/fortify/especialista/Space Marine) e para `ImperialResourceStorage` o estado
  dos 6 recursos (iron/coal/scrap/gold/emerald/crusadium) com a lógica de capacidade/add/spend/
  save/load (chaves NBT preservadas). O Core delega. Próximo: extrair lógica de raid/defesa.

### Documentos de design relacionados
- `docs/DESIGN_W40K_AUTONOMY.md` — autonomia/IA das unidades imperiais e inimigas
- `docs/DESIGN_WORLD_CITIES_FACTIONS.md` — roster completo de tipos de cidade e clãs/facções

---

## 15. Regras de arquitetura
1. Não colocar tudo dentro de `ImperialCommandCoreBlockEntity`.
2. Usar **managers separados**.
3. Cada estrutura importante: **Block + BlockEntity + Manager**.
4. Evitar classes gigantes.
5. Métodos pequenos e claros.
6. Sempre testar com **clean build**.
7. Nunca usar import `main.java...`.
8. Manter sistemas grandes separados.

---

## 16. Armadilhas comuns (gotchas)

**Import errado do VS Code** — nunca:
```java
import main.java.com.example.examplemod.ImperialScrapYardManager; // ERRADO
```
Se estiver no mesmo package, não precisa importar.

**Gradle wrapper apontando para D:** — em `gradle/wrapper/gradle-wrapper.properties`:
```text
# ERRADO: distributionUrl=file:/D:/gradle-8.8-bin.zip
# CERTO:
distributionUrl=https\://services.gradle.org/distributions/gradle-8.8-bin.zip
```

**Tick static** — em `serverTick(Level, BlockPos, BlockState, ...BlockEntity blockEntity)`
não usar `this`; usar o parâmetro `blockEntity`.

---

## 17. Preferências de trabalho do autor
- Manter o código organizado em **managers**, evitando inflar o Core.
- Ao fazer mudanças grandes, **enviar arquivos completos**.
- **Nunca usar code fences com id.**

---

*Última atualização: 2026-06-17 — sincronizado com o estado real do código (Custodes,
Primarch, Warboss, Ork Camps/Clãs, moral, tipos de cidade, ameaça, patrulhas, workforce).*

---

## 18. Progressão Imperial do jogador (pacote `progression`)

Árvore individual do jogador Imperial, do recruta da Astra Militarum ao Adeptus Astartes. É um
sistema **separado** do pipeline de aspirantes NPC (`AspirantManager`), que continua intocado: o
NPC tem 3 estágios de implante no Core; o jogador tem 12 órgãos, 36 habilidades e uma prova.

### Forma da árvore
50 nós: 1 raiz gratuita + 36 habilidades (5 ranks cada) + 12 implantes + 1 ascensão, em 12 ciclos
de **3 habilidades → 1 cirurgia**. A regra "não pular implante" **não é código**: as habilidades do
ciclo N listam o órgão do ciclo N−1 como pré-requisito, e o órgão lista suas três habilidades. Como
nenhum nó de fora aponta para dentro de um ciclo, não existe caminho que contorne uma cirurgia.
`PlayerProgressionTree.validate()` roda na carga e falha alto se a contagem sair da forma.

### Arquivos
- **Dados:** `PlayerEvolutionStage` (17 estágios, cada um dono da própria altura/largura),
  `PlayerSkillBranch`, `PlayerProgressionEffect`, `PlayerSkillNodeDefinition`,
  `PlayerEvolutionNodeDefinition`, `PlayerProgressionTree`, `PlayerProgressionBalance`.
- **Estado:** `PlayerProgressionProfile` (NBT), `PlayerProgressionData` (SavedData no overworld,
  por UUID — mesma escolha de `PlayerFactionData` e `PlanetUnlockData`).
- **Regras:** `PlayerProgressionRequirements` (todo veredito carrega o motivo),
  `PlayerProgressionEquipment` (tabela de gear por estágio).
- **Efeito:** `PlayerProgressionManager` (agrega tudo numa passada → `Totals`),
  `PlayerProgressionAttributes` (UUIDs fixos, remove-then-add),
  `PlayerProgressionSizeManager` (`EntityEvent.Size`, sem Pehkui),
  `PlayerProgressionAbilityManager`, `PlayerProgressionCombat`, `PlayerProgressionEvents`.
- **Rede:** `ProgressionActionPacket` (C2S, um verbo em enum) e `SyncPlayerProgressionPacket`
  (S2C, o perfil em NBT). `PlayerProgressionNetwork` é a única porta.
- **Cliente:** `progression/client/` — `PlayerProgressionKeys`, `PlayerProgressionScreen`,
  `PlayerProgressionHud`, `PlayerProgressionClientView`.

### Tamanho do jogador
De 1,80 a 2,35 blocos, **sem Pehkui**, por `EntityEvent.Size`: o evento é o único gancho que
alcança hitbox, colisão, altura dos olhos, câmera, sombra, nameplate e as poses de agachar/nadar de
uma vez. Toda pose é escalada pelo mesmo fator, senão o Astartes encolhe ao agachar. Cirurgia em
teto baixo não cresce dentro da pedra: `hasHeadroom` recusa antes de gastar gene-seed, e
`makeRoom` reposiciona.

### Ganho
XP de progressão só de Ork morto pelo jogador (Gretchin 2 → Warboss 50); aliado, cidadão, animal e
entidade invocada valem zero. Guarda anti-farm: invocado não conta, mesmo tipo tem teto por janela,
e morte longe demais não conta. Cada nível dá 1 Ponto de Doutrina.

### Teclas
`K` árvore · `O` oração · `V` rolamento · `B` glândula de Betcher · `G` estase Sus-an.

### Interface da árvore: ícones e páginas (2026-08-06, segunda passada)

A tela deixou de ser um canvas contínuo com zoom e arrasto. Agora são **6 páginas verticais
discretas**, cada uma com 2 ciclos completos (3 habilidades → implante → 3 habilidades → implante).
O scroll do mouse é Page Up/Page Down — uma página por vez, com trava de 160 ms para que um gesto
de trackpad não atravesse a árvore inteira. `PAGE UP`/`PAGE DOWN` fazem o mesmo. Não existe mais
`panX`, `panY`, `zoom`, `dragging` nem `mouseDragged`.

A página de abertura é escolhida pelo estado do jogador, nesta ordem: cirurgia em andamento →
Prova de Sangue (página 6) → implante pronto para ser tomado → nó mais profundo comprado → página 1.

**Ícones:** `PlayerProgressionIcon` (19 entradas) é o único lugar onde caminho de textura aparece;
`PlayerSkillNodeDefinition.icon()` resolve por campo, com o ícone da categoria como piso — por isso
uma habilidade nova não precisa de desenho nenhum. Os 12 implantes, a raiz e a ascensão carregam
ícone próprio, atribuído em `PlayerProgressionTree`. O antigo `PlayerSkillBranch.Glyph`
(retângulos desenhados em código) foi removido.

**Texturas:** geradas por `tools/generate_progression_icons.py` (Pillow), 40×40 RGBA, fundo
transparente, sem antialiasing, contorno derivado da própria silhueta. O script possui **apenas**
`textures/gui/progression/icons/`.

### Layout da árvore: regiões fixas e degraus (2026-08-06, terceira passada)

`ProgressionPageLayout` é um **record de aritmética pura, sem um único tipo do Minecraft** — e é essa
separação que o torna mensurável: dá para rodá-lo fora do jogo contra toda resolução e GUI scale e
conferir a resposta. A primeira versão calculava a geometria dentro do `render()`, e o único jeito de
descobrir que o rodapé estava sendo escrito por cima era olhar.

O tamanho vem em **degraus** (`Tier`), do mais folgado ao mais apertado, e `compute()` devolve o
primeiro que **cabe**. Um laço que decrementa vãos até acabar o que encolher simplesmente para e
devolve um layout que não cabe — medido, esse era o caso em 44 de 54 combinações. Os quatro
primeiros degraus mantêm os tamanhos do briefing (42/52/48/60, piso 36/46/52) e cedem só espaçamento
e rótulos; os três últimos vão abaixo disso e compactam cabeçalho e rodapé, porque num canvas de
240 px a alternativa não é "nós menores", é "sem o segundo ciclo".

Três regiões: cabeçalho (64, ou 72 em janela estreita, ou 34 compacto), conteúdo, rodapé (40 ou 26).
O scissor cobre exatamente o conteúdo e é **desligado antes** de cabeçalho e rodapé — é seguro, não
o plano: o layout já garante que a última coisa da página termina acima do rodapé
(`fitsInsideContentArea`).

**Medido:** 54 combinações (1280×720, 1366×768, 1600×900, 1920×1080 × escalas que o jogo oferece ×
6 páginas), zero falhas.

Sob os nós vai só o essencial: nome curto (`node.firstcrusade.<id>.short`) e rank quando há linha
para isso; quando não há, **o rank vai para o canto de dentro do nó** — o nome completo fica no
painel lateral.

### Árvore: rolagem contínua (2026-08-06, quarta passada)

As 6 páginas viraram **uma faixa única rolável**. Motivo: as páginas faziam a árvore parecer seis
árvores — o leitor tinha de reconstruir o fio a cada quebra, e as emendas precisavam de um marcador
de entrada no topo e de um "continua" no pé para disfarçar. Esse marcador de entrada era, aliás, o
que ficava por cima do primeiro nó da linha.

`ProgressionTreeLayout` (aritmética pura, sem tipo do Minecraft) calcula a faixa: 27 linhas
(raiz + 2 por ciclo + ascensão), `ROW_STEP` 78, altura total 2144. Com rolagem, **altura deixou de
ser restrição**: os nós ficam fixos em 42/52/48/60 em qualquer resolução, sem degraus de encolhimento.

Rolagem: roda do mouse contínua (26 px por entalhe), `Page Up`/`Page Down` saltam uma janela,
`Home`/`End` vão às pontas, setas ↑↓ movem devagar, e há barra arrastável à direita.

**Cabeçalho medido antes de desenhar:** os indicadores são quebrados em linhas que cabem no espaço
à esquerda do painel, e um indicador que não caiba nem sozinho é cortado com reticências. Era isso
que fazia "Implants: 3/12" sumir na borda direita.

**Medido:** 27 combinações (4 resoluções × escalas do jogo × cabeçalho de 1 a 3 linhas), zero falhas.


---

## 19. Base Imperial simplificada (2026-08-06)

O city builder foi retirado de circulação a pedido do dono. Uma base Imperial é agora o espelho do
acampamento Ork: **um Core, uma laje e alguns soldados soltos**.

### O que deixou de rodar
| Sistema | Cadência antiga | Estado |
|---------|-----------------|--------|
| `StrategicConstructionBuilder.tickConstruction` | 20 ticks | não é mais chamado |
| `CityMilitaryManager.tickAll` | 60 ticks | não é mais chamado |
| IA estratégica (construir/atacar/avançar Era) | 100 ticks | removida do manager |
| `ImperialPatrolManager.tickPatrols` | 200 ticks por Core | não é mais chamado |
| `ImperialWorkforceManager.autoManageWorkforce` | 200 ticks por Core | não é mais chamado |
| `ImperialPopulationManager.tickCitizenGrowth` | todo tick por Core | não é mais chamado |
| `ImperialCityMoraleManager.tickMorale` | 200 ticks por Core | não é mais chamado |
| governança autônoma do Core | 200 ticks por Core | métodos removidos |
| `buildCityStructure` + 17 auxiliares | a cada upgrade | classes removidas do Core |

O que sobrou de estratégico roda **1x a cada 600 ticks** (`StrategicWarAIManager.lightStrategicTick`):
sincroniza o mapa de guerra, paga a renda passiva, tica o lado Ork e resolve captura de cidade —
essa última **só** para cidades com um camp a menos de 160 blocos, medido pelo mapa, sem varredura
de entidade nas outras.

### A base
- Fundação: laje 9x9 (`SimpleImperialBaseManager.foundBase`), quatro postes com lanterna nos cantos
  e um pouco de tralha. **Escrita uma vez.** Nenhum `setBlock` de base acontece depois disso —
  inclusive o Rally deixou de erguer o anel de postos que erguia.
- Guarnição por nível do Core: **4 / 6 / 8 / 10 / 12** (`SimpleImperialBaseBalance`).
- Reposição: uma olhada a cada **1200 ticks**, no máximo **1 soldado**, e só quando o contador *e*
  uma varredura local de 32 blocos concordam que falta gente. A varredura **só levanta** o contador
  (um soldado perseguindo um Ork não é uma baixa); quem o baixa é a morte, em
  `onAssignedGuardsmanDeath`. Medido: sem essa regra a guarnição ia a 20 num teto de 10.
- Soldados soltos: `restrictTo(Core, 24)` + `RandomStrollGoal` com intervalo de 140 ticks +
  `LightweightReturnToBaseGoal` (só age acima de 32 blocos, e não repathiza um caminho válido).
  `GuardsmanGuardPostGoal`/`ImperialTroopGuardPostGoal` deixaram de ser registrados.
- Upgrade do Core: só o nível lógico, capacidade, armazenamento e a **Era estratégica**, escrita
  direto no `StrategicSettlementRecord` (`SimpleImperialBaseBalance.ageForCoreLevel`:
  1 OUTPOST · 2 FORTIFIED_SETTLEMENT · 3 MANUFACTORUM_AGE · 4 ASTARTES_AGE · 5 PLANETARY_WAR).
- Migração de save antigo: campo `SimplifiedBaseMigrated` no Core. Na primeira atualização cancela
  os projetos estratégicos daquela cidade, tira os guard posts, recontagem exata da guarnição e
  **descarta os `ImperialCitizenEntity` ligados àquele Core** — menos aspirantes, que só perdem o
  emprego. Muralhas, casas e ruas antigas **continuam no mundo**, apenas paradas.

## 20. Raid iniciada pelo jogador (pacote `assault`, 2026-08-06)

`ImperialAssaultManager` · `ImperialAssaultData` (SavedData) · `ImperialAssaultRecord` ·
`ImperialAssaultPhase` · `ExpeditionTroopData` · `ImperialAssaultBalance` · `ImperialAssaultEvents` ·
`ImperialExpeditionTags`.

- **Como começa:** o jogador Imperium abre o Ork Camp e aperta **INICIAR RAID IMPERIAL**
  (`OrkCampActionPacket.Action.START_IMPERIAL_RAID`). O servidor valida tudo: facção, 8 blocos de
  distância, o bloco ainda ser um camp, não haver outra raid ali, o jogador não liderar outra, e o
  cooldown. O botão só é desenhado para quem o servidor disse ser Imperial — e é cortesia, não regra.
- **Quem atende:** a base elegível mais próxima, tirada do `WorldWarMapData` (nunca varredura de
  blocos): mesma dimensão, Core válido, com soldados, sem raid Ork ativa, sem outra expedição, e
  **do jogador ou sem dono** — base de outro jogador nunca é usada.
- **Quantos:** o maior limite desbloqueado na árvore de Comando (0/3/5/7/10), limitado pelos
  soldados elegíveis menos **1 que fica em casa**.
- **Como chegam:** capturam posição/rotação/Core/raio de casa em `ExpeditionTroopData`, ganham as
  tags de expedição, perdem a coleira de casa e são teleportados a ~**100 blocos** (70 com Inserção
  Avançada) do lado da base de origem, em grupos de 3, em chão conferido (sólido embaixo, dois
  blocos livres, sem fluido, nunca a menos de 40 do camp). Depois marcham; alvo novo só quando o
  atual morre ou a cada 40 ticks.
- **Vitória:** quando os defensores válidos do camp (Orks *marcados para aquele camp*) acabam, ou o
  bloco some. Durante a raid o `checkOverrun` do camp fica de lado — a regra dele exige 3 Imperiais,
  e um comandante sozinho tem que poder vencer. Recompensa uma vez só (flag persistida): camp
  arrasado, War Dominion, Commander XP, XP de progressão e **contagem para a Prova de Sangue**.
- **Retorno:** imediato, na próxima atualização do gerente — inclusive quando o jogador vence antes
  de a tropa chegar. Volta para a posição original se ainda for segura, senão para um anel de 4-12
  blocos do Core; restaura vínculo e coleira, limpa tags e alvo, **não** restaura guard post.
- **Abortar:** jogador offline/morto/fora da dimensão/a mais de 256 blocos por 600 ticks, ou 24000
  ticks de raid. Sem recompensa, tropas voltam, cooldown curto.
- **Custo em CPU:** `tick` retorna num `isEmpty()` quando não há raid; com raid, 1 atualização a
  cada 20 ticks e retarget a cada 40. Sem varredura mundial, sem chunk ticket, sem comando de texto.
- **Comandos:** `/fcassault status | start | victory | abort | return_all | clear_orphans`.

## 21. Comando Imperial (segunda aba da tela K, 2026-08-06)

Moeda **separada** da Doutrina: `Commander XP` → `Commander Level` → `Command Points`. Melhorar o
comandante nunca atrasa a transformação em Space Marine.

- **Dados:** `PlayerCommanderProfile`, guardado **dentro** de `PlayerProgressionProfile` (tag
  `Commander`), então viaja no mesmo pacote de sync e é salvo pelo mesmo escritor. `DATA_VERSION`
  subiu para 2 — save antigo simplesmente não tem a tag e começa do zero.
- **Árvore (`PlayerCommanderTree`, 9 nós):** Autoridade Imperial (grátis) → Vox de Esquadra (1 PC, 3
  soldados) → Esquadra Reforçada (2 PC, 5) → Seção de Combate (2 PC, 7, exige 2 vitórias) → Pelotão
  de Assalto (3 PC, 10, exige 4 vitórias). Ramo tático: Sargento de Campo (1 PC), Vox Prioritário
  (1 PC, −25% de cooldown), Inserção Avançada (1 PC, 100→70 blocos), Ataque Coordenado (2 PC,
  Speed I + firmeza por 20s ao chegar).
- **XP:** 1ª raid 5 · vencer 30 · sem perder ninguém +10 · camp destruído +20 · Nob 4 · Meganob 6 ·
  Warboss 15 (só durante a própria raid e a menos de 64 blocos do camp). Cada nível dá 1 PC; chegar
  a ASTRA_VETERAN dá 1 PC inicial, uma vez.
- **Tela:** duas abas desenhadas no cabeçalho (`ProgressionTab`), scroll e seleção **independentes**,
  troca sem pacote nenhum. Ícones em `textures/gui/progression/commander/`.
- **Rede:** `ProgressionActionPacket.Action.COMMAND_UNLOCK` — verbo novo, nunca `UNLOCK` com id de
  comando, para que um id de comando jamais seja procurado na árvore Astartes.
- **Comandos:** `/fccommand status [player] | add_xp | add_points | unlock | reset`.

## 22. O corpo do jogador viaja na rede (2026-08-07)

O tamanho do jogador é decidido em `PlayerProgressionSizeManager.onSize`, que roda **nos dois
lados**: o servidor lê o stage do próprio save, o cliente lê `PlayerProgressionClientView`. Só que
o mapa `STAGES` do cliente **não tinha produtor** — `putStage` e `clear` existiam sem nenhum
chamador. O cliente respondia `ASTRA_RECRUIT` para todo mundo e não escalava ninguém, enquanto o
servidor escalava. Do NEOPHYTE para cima isso era servidor com caixa 0.84×2.30 contra cliente com
0.60×1.80.

O que uma dessincronia de caixa faz (Forge 47.4.10, fonte decompilada):
`ServerGamePacketListenerImpl.isPlayerCollidingWithAnythingNew` testa a caixa **do servidor** na
posição que o cliente pediu; se ela toca um bloco que não tocava antes, o servidor chama
`teleport(...)`, que seta `awaitingPositionFromClient` **sem log nenhum** (o `moved wrongly!` sai
por outro ramo e nem roda em creative). E `handleUseItemOn` só age
`if (this.awaitingPositionFromClient == null && ...)` — ou seja, colocar bloco vira no-op
silencioso, e bater erra porque a posição do servidor foi puxada de volta. O sintoma que chega ao
jogador é "não consigo bater em nada nem colocar bloco", sem uma linha de erro em lugar nenhum.

**Como ficou:**
- `SyncPlayerStagePacket` (UUID + nome do stage) — pacote **público**, separado de
  `SyncPlayerProgressionPacket`, que continua privado do dono. Duas audiências, dois pacotes.
- `PlayerProgressionNetwork.sync` manda o stage por `TRACKING_ENTITY_AND_SELF` (todo caminho que
  muda stage termina em `recalculate`, que termina em `sync`).
- `PlayerProgressionEvents.onStartTracking` → `syncStageTo`: quem entra na render distance depois
  também recebe.
- `progression/client/ClientStageSync`: `putStage` **+ `refreshDimensions()`** — só lembrar não
  basta, a entidade guarda a caixa que calculou por último. Limpa em
  `ClientPlayerNetworkEvent.LoggingOut` para o stage não vazar de um mundo para o outro.

**Consequência ainda aberta:** `PlayerEvolutionStage` passa de 2.0 blocos de altura já no
`IMPLANT_STAGE_4` (2.02) e chega a 2.30 no NEOPHYTE / 2.35 no SPACE_MARINE. Acima de 2.0 o jogador
não atravessa porta nem corredor de 2 blocos — agora de forma **consistente** (parede, não
teleporte). É a decisão de design do dono ("escalar só o modelo daria um gigante que passa por
porta de humano"); o teto, se um dia for querido, mora só em `PlayerEvolutionStage`.

## 23. Identidade visual das tropas Imperiais (2026-08-07)

Nove tropas partilhavam **o mesmo arquivo de textura** (md5 `2524cc91…`). A causa não era falta de
arte, era o lugar onde a arte era escolhida: cada renderer tinha um `static final ResourceLocation`
e devolvia o mesmo objeto para toda instância, então acrescentar um PNG novo exigia mexer no
renderer e ninguém nunca via que dois deles apontavam para o mesmo lugar.

### A geometria que já existia
As **11** tropas humanoides (Guardsman, Kasrkin, Skitarii Ranger, Sister of Battle, Penal
Legionnaire, Jungle Fighter, Mine Guard, Feudal Knight, Agri Militia, Enforcer, City Commander)
constroem o modelo com `ModelLayers.ZOMBIE` — `HumanoidModel.createMesh`, UV humanoide padrão
64x64, com a camada de chapéu na cabeça. **Uma geometria compartilhada, onze texturas
independentes**, que é exatamente o arranjo pedido. A camada de chapéu é o sinal de silhueta mais
forte que o modelo oferece e é usada como tal: opaca e fechada no Kasrkin e no Enforcer, aberta no
Guardsman, só a copa no Jungle Fighter (bandana) e no Agri (chapéu de palha), ausente no Penal
(coleira) e na Sister (cabelo).

As duas tropas GeckoLib (`guardsman_rifleman`, `guardsman_sergeant`) têm UV própria 64x128, com
~22 cubos, lida direto do `.geo.json` pelo gerador. Braços/pernas esquerdo e direito apontam para a
**mesma UV de propósito** — isso é espelhamento de uniforme, não defeito, e foi mantido.
Nenhuma UV foi alterada em lugar nenhum.

### As três perguntas
`ImperialTroopAppearance.texture(troopKey, regiment, variant, grade)`:
- **troopKey** — o path do registro da entidade, que também é o nome da pasta. Tropa nova não
  precisa de código: basta a arte e uma linha em `define`.
- **regiment** — cabeado, com uma entrada hoje (`default`). Um Cadian e um Krieg partilham modelo e
  UV e diferem só no arquivo que este método devolve.
- **variant** — qual indivíduo. Sorteado uma vez, persistido.
- **grade** — `ImperialTroopGrade` (LINE/VETERAN/SERGEANT), **derivado** de `GuardsmanRank`. Oito
  patentes, três guarda-roupas: oito graus de "um pouco mais enfeitado" não se leem de longe.
  Derivar em vez de guardar evita uma segunda cópia do mesmo fato, livre para divergir.

Custo: todo `ResourceLocation` é montado no class-load, num array por (tropa, regimento, grade).
Uma chamada de render é dois lookups e um índice — não fica mais cara com o campo cheio.

Nada renderiza roxo: tropa/regimento/grade desconhecidos caem no Guardsman de linha e avisam no log
**uma vez** por miss distinto (um renderer roda por entidade por frame).

### Persistência
A variante é sorteada em `defineSynchedData` — o único ponto por onde **todo** caminho de spawn
passa (quartel, raid, spawn egg, `/summon`); `finalizeSpawn` não passa. O cliente sorteia o seu por
um frame e é sobrescrito pelo do servidor; `readAdditionalSaveData` fixa de vez. Save antigo sem a
tag `FirstCrusadeVisualVariant` mantém o sorteio e passa a gravá-lo — a aparência nunca muda no
relog.

A promoção muda só a figura: `refreshVisualGrade()` é chamado nos três lugares que atribuem patente
(`initializeFromCity`, `setRank`, carga do NBT). O soldado é o mesmo objeto — UUID, nome, merit,
contagem de Orks, Command Core e equipamento atravessam a promoção intactos.

### Arte
29 PNGs 64x64 em `textures/entity/imperium/<tropa>/` e 8 de 64x128 para as GeckoLib, gerados por
`tools/generate_troop_textures.py` e `tools/generate_geo_troop_textures.py`. Os geradores são
**determinísticos** (semente = unidade + variante, nunca o relógio), então rodar de novo não produz
diff espúrio. Cada tropa é uma receita: paleta, placas, cintos, bolsas, insígnia, desgaste e sujeira
que se acumula embaixo, não espalhada por igual.

**A arte feita à mão pelo dono foi preservada, não substituída:** `guardsman.png` virou
`guardsman_3.png` e `kasrkin.png` virou `kasrkin_2.png` (bytes idênticos), no fim do conjunto para
que uma variante gerada nova não os renumere. `OWNER_AUTHORED` no gerador proíbe sobrescrevê-los.

### `/fctroop` (nível 2)
`line` põe uma de cada tropa lado a lado — o teste visual inteiro num comando. `spawn <tipo>`,
`variant <alvo> <n>`, `career <alvo> <patente>` (passa por `setRank`, o mesmo caminho de uma
promoção real, então o que se confere é o comportamento e não uma prévia dele) e `info <alvo>`, que
imprime chave, regimento, variante, grade e o PNG que aquilo resolveu.

## 24. Fase 2 da Cruzada: regimentos e soldados persistentes (2026-08-09)

Pacote novo `com.example.examplemod.crusade`. Nenhuma classe aqui tica.

### Onde os dados moram
`ImperialCrusadeData` (SavedData no overworld) guarda um `ImperialSoldierRoster` por Core, chaveado
pela posição do Core. **Não** foi para dentro do `ImperialCommandCoreBlockEntity` por duas razões: o
NBT de um block entity vive no chunk dele, então a ficha só seria legível com aquele chunk carregado
— e o Registro da Cruzada, o memorial e o Spaceport querem ler bases longe do jogador; e o Core já é
a classe de 3000 linhas que o projeto tem regra para não inflar. Overworld sempre, mesmo vindo de
outro planeta: uma Cruzada atravessa planetas.

### O soldado
`ImperialSoldierRecord` — UUID, nome, regimento, grade, Orks/elites/Warbosses, raids, alistamento,
queda e destino. **A ficha existe fora da entidade de propósito:** quando o soldado morre a entidade
some naquele tick, e o único motivo de a campanha ainda saber o nome dele é que isto nunca foi
guardado nele.

`ImperialSoldierNames` deriva o nome do **UUID**, não sorteia e guarda: custo zero de armazenamento,
impossível divergir entre cliente e servidor, sobrevive a qualquer mudança de formato. A ficha guarda
uma cópia porque um morto não tem mais UUID para consultar. Listas originais (40 nomes × 40
sobrenomes).

### Regimento
`ImperialRegimentType`: CRUSADE_GENERIC, CADIAN_LINE, CATACHAN_JUNGLE, FORGE_AUXILIA, PENAL. Só os
que o mod consegue **de fato** montar com tropas registradas — Krieg e Valhallan ficaram de fora
porque um regimento cujas tropas não existem seria uma mentira contada na UI. Decidido uma vez, na
fundação (`forCityType`), e governa **quem** preenche as vagas, nunca **quantas**: o teto continua no
`SimpleImperialBaseBalance`. PENAL é o único sem carreira.

### Carreira
`ImperialSoldierCareerManager` é o único lugar que promove. O mérito e as patentes continuam no
`GuardsmanEntity`/`GuardsmanRank` intocados; o que mudou é que `tryPromoteFromMerit` agora pergunta
se a base **tem vaga**. Cota: 1 Sergeant por 5 soldados, mínimo de 3 na guarnição. Antes, todo
sobrevivente virava Sergeant e depois Commander — patente que todo mundo alcança não informa nada.
Mérito que não pode ser gasto **não se perde**: fica esperando abrir vaga, que é o que faz a morte
de um Sargento importar para o homem atrás dele.

### Alistamento e morte
`SimpleImperialBaseManager.bindToBase` é o ponto único por onde um soldado entra numa base —
guarnição de fundação, reforço, migração de save antigo e sobrevivente voltando de raid passam todos
por ali. Alistar é **idempotente**: veterano que volta para casa não volta recruta.

`CrusadeEvents` usa o `LivingDeathEvent` que já existe — sem listener próprio, sem varredura. Custo
normal: dois `instanceof`. Escreve **só a ficha**; mérito e patente continuam no caminho antigo
(`recordOrkKill` no `die()` de cada Ork), então os dois nunca contam duas vezes: a entidade é o que
ele **pode fazer**, a ficha é o que ele **fez**. Morte é permanente (`markFallen` é guardado) e o
anúncio em chat só sai para quem tinha feito por merecer — anunciar todo recruta seria spam durante
uma raid e barataria a única linha que deveria doer.

Memorial limitado a 64 nomes por base (`MAX_REMEMBERED_FALLEN`), mas o **contador** de mortos não é
truncado — o Registro continua verdadeiro depois que os nomes rolam.

### Fatia 2b: regimento no spawn + visibilidade (2026-08-09)

`spawnGarrisonTroop` (o ponto único que levanta um soldado de guarnição) agora pergunta ao
**regimento**, não ao tipo de cidade. Regra: `CRUSADE_GENERIC` cai no tema antigo por tipo de
cidade — é ali que a compatibilidade com bases fundadas antes dos regimentos mora; qualquer
regimento **nomeado** é autoritativo, e um roll nulo dele significa Guardsman comum. Deixar o tema da
cidade responder no lugar transformaria silenciosamente todo roll Catachan em Jungle Fighter e
apagaria justamente a mistura que o roll existe para produzir. O teto de guarnição não é tocado: o
chamador já conferiu a capacidade antes de chegar aqui.

Visibilidade, enquanto o menu do Core não existe:
- `/fccrusade roster <core> | fallen <core> | bases` (nível 2, só leitura). Uma ficha que ninguém
  consegue ver é uma ficha que ninguém percebe estar errada.
- **Clique direito num soldado** (`SoldierInspectEvents`): quatro linhas no chat — nome com título,
  regimento, tempo de serviço, Orks/elites/raids, e o estado da guarnição. Sem GUI de propósito
  (a Parte 21 do briefing permite): um menu custa container, par de pacotes e tela, e interrompe o
  jogador para mostrar seis números.

## 25. Parte 2 do briefing: o Core como sala de operações (2026-08-10)

O Core deixou de ser prefeitura e virou centro operacional. Oito abas:
VISÃO GERAL · GUARNIÇÃO · MILITAR · VOX/OPS · APOTHECARION · REGISTRO · RECURSOS · GUERRA.

**City e Defense não foram reescritos — foram renomeados.** Os painéis por trás já diziam as coisas
certas, só estavam arquivados sob as palavras erradas (City→VISÃO GERAL, Defense→VOX/OPS, que é
onde reforço, rally e raid já moravam). Reescrever painel que funciona para trocar o título seria
risco sem ganho.

`imageWidth` foi de 320 para **420**: oito abas a 51px precisam de 416. Tudo é posicionado a partir
de `leftPos` e o fundo é `fill`, então nada mais se moveu.

### Como a ficha chega ao cliente
O menu do Core já sincroniza 162 inteiros por `ContainerData` — ferramenta certa para contadores que
mudam com a tela aberta, e **incapaz de carregar uma string**. A guarnição é uma lista de nomes de
tamanho variável, então foi para um par de pacotes próprio:

- `CrusadePanelRequestPacket` (C2S) — o cliente **pede** ao abrir GUARNIÇÃO ou REGISTRO. Pull, não
  push: quem nunca abre a aba nunca paga. O servidor **não confia** na posição que veio do cliente —
  confere chunk carregado, que o bloco é mesmo um Core, e distância de 64 blocos. Sem isso o pacote
  seria um jeito de ler qualquer base do mapa de qualquer lugar, que em multiplayer é a guarnição de
  outro jogador.
- `CrusadePanelPacket` (S2C) — achatado em `Row`, listas limitadas a 32 (limitadas na escrita **e**
  na leitura: a contagem vem do fio, e uma malformada deve custar lista truncada, não alocação do
  tamanho que mandaram). Mortos vêm do mais recente para trás.
- `client/CrusadeClientView` — **um slot só**, não cache. Um mapa de toda base já aberta ficaria
  velho no instante em que um soldado morresse em outro lugar, e velho é pior que vazio aqui: painel
  vazio diz "perguntando", painel velho mente com confiança. Pedido limitado a 1 a cada 40 ticks (a
  tela redesenha 60x/s e pediria 60x/s). `null` enquanto não chega, e a tela desenha "Abrindo
  vox-link..." — base sem soldados e base que ainda não respondeu não podem parecer a mesma coisa.

### Apothecarion
Painel de apresentação puro: os números já vinham como inteiros no menu. Operacional a partir do
Core nível 3, nada construído fisicamente. As cirurgias continuam na árvore de Ascensão (K).
