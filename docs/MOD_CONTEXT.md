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

### Tick (server)
`serverTick` roda **a cada tick**: atribuição do tipo de cidade (se nulo) e crescimento
populacional. O resto roda a cada **200 ticks** (10s), nesta ordem:
moral (`ImperialCityMoraleManager.tickMorale`), patrulhas (`ImperialPatrolManager.tickPatrols`),
gestão de mão de obra (`ImperialWorkforceManager.autoManageWorkforce`), produção diária,
redução de cooldowns, promoção automática a Space Marine, Custodes (`tickCustodes`),
mourning do Primarch, Primarch (`tickPrimarch`), seed de Ork Camp (`trySeedOrkCamp`),
spawn/checagem de Ork Raid.

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

Upgrade também constrói/expande a estrutura física (fundação, muralha, torres de canto,
casas a partir do nv3, ruas no nv5) e reorganiza os Guardsmen em novos postos de guarda.

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
