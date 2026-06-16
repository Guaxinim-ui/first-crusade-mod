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
inimigos atacam (Ork Raids)
    ↓ jogador administra defesa, produção e tropas
```

Sequência prática:
1. Jogador coloca o **Imperial Command Core** e se torna o dono.
2. O Core gera **Imperial Citizens** ao longo do tempo, até a capacidade.
3. Recursos são depositados/produzidos (Iron, Coal, Scrap Metal).
4. Constrói **postos de trabalho** (Mine, Scrap Yard, Forge) que empregam cidadãos.
5. Treina cidadãos em **Guardsmen**, que defendem postos de guarda.
6. **Faz upgrade da cidade** (1→5): expande estrutura física e melhora tudo.
7. Defende-se de **Ork Raids**; vitórias dão recompensas e **War Support**.
8. Cidade ≥ nv3: promove Guardsmen a **Space Marines** via **Emperor Gene Seed**.

> **Direção de design importante:** a produção passiva atual do Core é **temporária**.
> Já implementado o início da remoção: a produção passiva de Iron/Scrap escala para baixo
> conforme minas/scrap yards **com trabalhador** assumem (piso de 20% — `PASSIVE_PRODUCTION_FLOOR`).
> Coal e Gene Seed continuam passivos por ora. Ver `getEffectiveDailyIronProduction`/`...Scrap...`.

---

## 3. Imperial Command Core (coração do mod)

`ImperialCommandCoreBlockEntity` — armazena todo o estado da cidade (NBT). É o centro
administrativo: armazena recursos, abre interface, gera cidadãos, constrói estruturas,
treina/organiza tropas, repara integridade, controla raids/reforços/comandos, Gene Seed
e limites de estruturas. Fica no centro; construções surgem ao redor em locais livres.

> **Regra de arquitetura nº1:** NÃO colocar tudo aqui. Lógica pesada vai em *managers*.
> (O Core já está grande — ~2300 linhas — e é candidato a refatoração.)

### Estado persistido
Dono (`ownerUUID`/`ownerName`), `baseName` ("Imperial Outpost"), `cityLevel` (1–5),
`iron`/`coal`/`scrapMetal`, `recruitedGuardsmen`, `emperorGeneSeed`, estado de raid
(`lastOrkRaidDay`, `orkRaidCount`, `activeOrkRaid`, `activeOrkRaidTicks`,
`orkRaidVictories`, `cityIntegrity` 0–100, `raidPressureTicks`), `imperialWarSupport`,
cooldowns (`reinforcementCooldownTicks`, `spaceMarinePromotionCooldownTicks`),
`pendingSpaceMarineCandidateUUID`.

### Tick (server)
`serverTick` roda crescimento populacional todo tick; o resto a cada **200 ticks** (10s):
produção diária, redução de cooldowns, promoção automática a Space Marine, spawn/checagem
de Ork Raid.

### Tabelas por nível de cidade (1 → 5) — VALORES ATUAIS DO CÓDIGO

| Métrica | Nv1 | Nv2 | Nv3 | Nv4 | Nv5 |
|---------|-----|-----|-----|-----|-----|
| Armazenamento | 500 | 1.500 | 5.000 | 15.000 | 50.000 |
| Cap. militar (Guardsmen) | 5 | 12 | 25 | 50 | 100 |
| Cap. populacional | 3 | 6 | 10 | 15 | 25 |
| Iron/dia | 5 | 25 | 100 | 400 | 1.500 |
| Scrap/dia | 3 | 15 | 60 | 240 | 900 |
| Coal/dia | 2 | 10 | 40 | 160 | 600 |
| Gene Seed/dia | 0 | 0 | 1 | 2 | 4 |
| Cap. Gene Seed | 0 | 0 | 5 | 12 | 30 |
| Raio da estrutura | 4 | 8 | 12 | 18 | 26 |
| Altura da muralha | 1 | 3 | 5 | 7 | 9 |
| Cap. Imperial Mine | 1 | 2 | 3 | 4 | 5 (=nível) |
| Cap. Scrap Yard | 1 | 2 | 3 | 4 | 5 (=nível) |
| Cap. Imperial Forge | 1 | 1 | 2 | 2 | 3 ((nível+1)/2) |

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
- Produção diária automática (`produceResourcesIfNewDay`, baseada em `getDayTime`).
- `receiveProducedResource` recebe produção dos postos (Iron/Coal/Scrap; Gold/Emerald/
  Crusadium são rejeitados por aqui — ganchos para o futuro).

---

## 4. Interface do Core (GUI)

`ImperialCommandCoreMenu` (`imperial_command_core_menu`) + `ImperialCommandCoreScreen`.
Só o dono abre. Ações via packet `ImperialCommandCoreActionPacket` (canal `firstcrusade:main`).

**Enum `ImperialCommandCoreAction` (atual):**
`DEPOSIT_RESOURCES, BUILD_IMPERIAL_MINE, BUILD_SCRAP_YARD, BUILD_IMPERIAL_FORGE,
RECRUIT_GUARDSMAN, UPGRADE_CITY, REPAIR_CORE, CALL_REINFORCEMENTS, RALLY_DEFENDERS,
FORTIFY_DEFENDERS, FORCE_RAID_TEST`.

`ImperialMilitaryReportManager` mostra relatório de status no chat.

**A interface deve mostrar (alvo):** nível da cidade, integridade, cidadãos (total e
desempregados), soldados, nº de minas/scrap yards/forges, recursos armazenados, produção,
Gene Seed, raids, vitórias, cooldowns, status militar.

**Melhorias futuras da interface:**
- Tooltips mostrando custo · botões bloqueados com motivo
- Abas separadas: economia / população / guerra
- Contagem de trabalhadores por cargo · visual mais organizado

---

## 5. População — Imperial Citizen

Entidade base da civilização (`ImperialCitizenEntity`). Substitui villagers; nasce perto
do Core, recebe cargos, anda até locais de trabalho e gera produção indiretamente.
Futuramente vira Recruit/Guardsman/especialista.

**Campos importantes:** `commandCorePos`, `workSitePos`, `job`, `citizenAgeTicks`, `workTicks`.

**Manager:** `ImperialPopulationManager` — gera cidadãos a cada **1200 ticks** (60s) se
abaixo da capacidade; conta cidadãos/desempregados (raio 96); treina cidadão em Guardsman.

### Empregos (`ImperialCitizenJob`)
`UNEMPLOYED, MINER, SCRAPPER, SMITH, STOKER, FARMER, BUILDER, RECRUIT`
→ FARMER e BUILDER existem no enum mas **ainda não têm posto/lógica**.
→ STOKER trabalha na Promethium Refinery (produz Coal).
→ RECRUIT treina num Barracks e vira Guardsman ao completar o treino.

---

## 6. Estruturas de trabalho

Padrão de toda estrutura importante: **Block + BlockEntity + Manager** + registro no
`ExampleMod` + blockstate json + block model json + item model json + entrada no en_us.json.

Fluxo: botão na UI → packet → Core valida custo/limite → Manager acha local livre →
constrói → designa cidadão desempregado → cidadão anda até lá → produz.

### Imperial Mine (`ImperialWorkSiteManager`)
Custo: **20 Iron, 10 Scrap, 5 Coal**. Cap. = nível. Emprega `MINER` → produz Iron.

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

### Produção escalada por nível (yield por ciclo)
Cada ciclo de trabalho rende `getMineIronYield`/`getScrapYardScrapYield`/`getRefineryCoalYield`,
escalando com o nível da cidade (não mais fixo em 1):
| Estrutura | Nv1 | Nv2 | Nv3 | Nv4 | Nv5 |
|-----------|-----|-----|-----|-----|-----|
| Mine (Iron) | 1 | 2 | 3 | 5 | 8 |
| Scrap Yard (Scrap) | 1 | 2 | 3 | 4 | 6 |
| Refinery (Coal) | 1 | 1 | 2 | 3 | 4 |

A produção passiva de Iron/Scrap/**Coal** recua conforme as estruturas correspondentes com
trabalhador assumem (piso 20%, `getEffectiveDaily...Production`).

---

## 7. Recursos do mod

| Recurso | Estado | Função (planejada) |
|---------|--------|--------------------|
| Iron | ✅ ativo | Construção básica, estruturas, armas simples, muralhas, minas |
| Coal | ✅ ativo | Combustível: produção, forjas, indústria. Produzido pela Promethium Refinery |
| Scrap Metal | ✅ ativo | Reparos, tech improvisada, upgrades, militar, equipamentos |
| Crusadium Plate | ✅ ativo | Upgrades, reparo do Core, armaduras, estruturas avançadas |
| Emperor Gene Seed | ✅ ativo | Transformar Guardsmen em Space Marines |
| Imperial War Support | ✅ ativo | Reforços, comandos militares, suporte imperial |
| Crusadium (ingot) | item existe | Material avançado: armaduras, tech, equipamentos (sem cadeia de uso ainda) |
| Gold | 🔜 planejado | Upgrades avançados, itens do jogador, veículos |
| Emerald | 🔜 planejado | Comércio com a capital, suprimentos, reforços externos |
| Ork Teeth | item existe | Drop de Orks; "moeda" temática (uso a definir) |

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
- **Space Marine** (`SpaceMarineEntity`) — entidade separada, promovida de Guardsman via Gene Seed.
- **Orks** (`OrkBoyEntity`, `OrkNobEntity`) — inimigos das raids; Nob é a versão forte.
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

### Progressão militar
Tropas vêm da população. O botão **Recruit** agora designa o cidadão desempregado mais
próximo como **RECRUIT** num Barracks disponível (não cria Guardsman instantâneo). O Barracks
treina por 1200 ticks → `completeRecruitTraining` cria o Guardsman (com chapter aleatório e
rank inicial da cidade). Capacidade militar conta `recruitedGuardsmen + recrutas em treino`.
```text
Citizen → Recruit (treina no Barracks) → Guardsman → [rank-up por mérito] → ... → Space Marine
                                              ↘ [Promote Specialist] → Especialista
```

### Especialistas (`GuardsmanSpecialization`) — IMPLEMENTADO
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

---

## 9. Facções (`FirstCrusadeFaction` / `FirstCrusadeFactionManager`)
Facções: `IMPERIUM, ORKS, HOSTILE, PLAYER, NEUTRAL`.

Regras planejadas:
- Imperium ataca Orks e Hostiles
- Orks atacam Imperium e Player
- Hostiles atacam Imperium e Player
- Neutrals não atacam

Objetivo: evitar aliados se atacando, padronizar combate, Orks focam defensores e player,
tropas imperiais miram inimigos corretos. (`ImperiumChapter` dá "chapter" aos Guardsmen.)

---

## 10. Ork Raids (`OrkRaidManager` + lógica no Core)

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

### Ações defensivas (`ImperialDefenseManager`) — durante raid ativa
- **Call Reinforcements** — spawna Guardsmen (1–6/nível) com rank escalado; cooldown 2400→1600.
  Custa War Support (5,10,18,30,50/nível): verifica antes, deduz após o deploy.
- **Rally Defenders** — reposiciona defensores para o Core.
- **Fortify Defenders** — buff; custa War Support (5,10,18,30,45/nível).
- **Repair Core** (`repairCity`) — 1 Crusadium Plate → +20/18/15/12/10 integridade.

### Nível de ameaça
Score = nível×2 + min(raids,10) + (5 se raid ativa) − min(vitórias,8). Nomes:
Low / Rising / Dangerous / Critical / WAAAGH!.

---

## 11. Sistemas/estruturas PLANEJADOS (ainda não no código)

### Civilização própria
Substituir vilas vanilla: sem vilas comuns → Command Core gera cidade imperial → cidadãos
vivem e trabalham → cidade cresce organicamente.

### Estruturas planejadas
`Habitation, Farm, Mine, Gold Mine, Emerald Trade Depot, Scrap Yard, Forge, Barracks,
Medicae Station, Armory, Vehicle Factory, Landing Pad, Wall Gate, Defense Tower, Command Relay`.

### Muralhas e vila
Core no centro → cidade cresce ao redor → muralha + torres + portões → cidadãos trabalham
dentro/ao redor → raids atacam. Muralha construída automática/semi-auto pelo Core, possivelmente
via cargo `BUILDER`.

### Geração de mundo (adaptada ao mod)
Menos cavernas, terreno mais plano, morros leves, menos mineração manual, mais espaço para
cidades, geração de acampamentos Orks, ruínas e pontos estratégicos.

### Ork Camps
Acampamento com tendas, barricadas, sucata, spawns/eventos, chefe local, recursos roubados;
alvo para o jogador atacar; possível origem das raids.

### Veículos e naves (fase tardia — não cedo)
Vehicle Factory → produz veículo → Engineer/Guardsman opera → NPC dirige/pilota.
Tipos: caminhão de transporte, tanque leve, blindado, nave de transporte, dropship, shuttle
de fast travel. Desafios: controles, física, pathfinding, IA dirigindo, colisão, multiplayer.
**Recomendação: só começar depois que cidade, produção e guerra estiverem sólidos.**

---

## 12. Inventário atual (código)

**Itens:** crusadium_ingot, crusadium_plate, ork_teeth, scrap_metal, lasgun_power_cell,
lasgun, guardsman_combat_knife, guardsman_med_kit, guardsman_command_baton, guardsman_helmet,
guardsman_chestplate, guardsman_leggings, guardsman_boots (+ spawn eggs).
Aba criativa: `first_crusade_tab` (ícone: Command Core).

**Blocos:** imperial_command_core, imperial_mine, imperial_scrap_yard, imperial_forge,
imperial_promethium_refinery, imperial_barracks.

**Entidades:** imperial_citizen, guardsman, space_marine, ork_boy, ork_nob, lasgun_shot.

**Managers (atuais/planejados):** ImperialPopulationManager, ImperialWorkSiteManager,
ImperialScrapYardManager, ImperialForgeManager, ImperialDefenseManager, OrkRaidManager,
SpaceMarineUpgradeManager. Outros: ImperialVillageScanner, ImperialSettlementType/Origin,
ImperialResourceType (IRON, COAL, SCRAP, GOLD, EMERALD, CRUSADIUM), GuardsmanArmorEvents, Config.

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

Pendente provável: lang `pt_br`, texturas/modelos próprios (alguns ainda placeholder).

---

## 14. Roadmap

### Curto prazo
- ✅ Mostrar Mine/Scrap/Forge na interface + contagem de trabalhadores por cargo (Miners/Scrappers/Smiths)
- ✅ Tooltips/custos nos botões + motivo de bloqueio quando inativos
- ✅ Produção passiva do Core começa a ser removida conforme estruturas com trabalhador assumem
- ✅ Custo de War Support dos reforços passa a ser cobrado
- Garantir que Mine, Scrap Yard e Forge funcionam de fato (testar em jogo)
- Melhorar a busca de local livre

### Médio prazo
- ✅ Fonte de Coal (Promethium Refinery) + produção dos trabalhadores escala por nível
- ✅ Imperial Barracks + Recruit como processo real (Citizen → Recruit → Guardsman)
- ✅ Especialistas (Sniper, Heavy Gunner, Melee Trooper, Medic, Engineer, Officer) via Promote Specialist
- Estruturas: Habitation, Farm, Gold Mine, Emerald Trade Depot (dar uso a Gold/Emerald e FARMER/BUILDER)

### Longo prazo
- Sistema de muralhas com portão; geração de vila imperial completa
- Acampamentos Orks no mundo; sistema de território; mapa estratégico
- Veículos; naves/dropships; outras facções

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

*Última atualização: 2026-06-16 — mescla de planejamento + estado do código.*
