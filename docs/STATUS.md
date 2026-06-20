# First Crusade — STATUS (leia isto primeiro ao reorientar)

> Arquivo de orientação do agente. Quando o chat for limpo (`/clear`), **leia este arquivo
> primeiro** para retomar o contexto. **Mantenha-o atualizado** ao fim de cada bloco de trabalho
> (estado atual + metas + changelog).
>
> ⚠️ O dono do projeto também desenvolve em paralelo (commits via "da run"/"da run pfb"). Antes de
> mudanças grandes, **verifique o estado real** com `git log --oneline -8`, `git status` e um
> `Glob` em `src/main/java/com/example/examplemod/*.java` — não confie só neste arquivo.

Última atualização: **2026-06-18** · branch `main` · remoto `github.com/Guaxinim-ui/first-crusade-mod`

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

### >>> PRÓXIMO PASSO (retomar aqui após o /clear) <<<
O dono testou em jogo e **está tudo OK** até o commit `1db7d1f`. Fase C está madura. Escolher UM:

1. **GUI em chaves de lang — CONCLUÍDA para o Núcleo** (fatias 1–4). Tela e mensagens do Core 100%
   traduzíveis (en/pt, 291 chaves sincronizadas). Restam só pendências menores/opcionais: notificações
   broadcast que recebem String (mudar assinatura p/ Component), strings cross-class passadas como `%s`
   (especialista/ameaça/moral/tropa), e `Component.literal` em **outros** arquivos (Ork Camp, etc.).
   Próximo foco sugerido: **Fase D** (overlords/worldgen) OU **conteúdo transversal** (arma nova).
2. **Fase D — Overlords/worldgen** (item de roadmap grande): começar pelo mais bounded — geração de
   assentamentos no worldgen (structure feature) OU um `WorldFactionOverlordManager` que despacha
   líder quando uma cidade atinge ameaça nível 4. Alto risco técnico (worldgen). Fazer slice por slice.
3. **Conteúdo transversal** — armas novas (bolter/chainsword) reusando `LasgunItem`/`LasgunShotEntity`
   como molde; bounded e visível.

**Regras ao continuar:** reusar o padrão de tropa-tema (entidade + renderer + placeholder guardsman.png
+ registro em ExampleMod + faction via base + lang en/pt + 1 linha em `getThemedTroopType`/enum).
Dono faz as texturas. Compilar offline (ver §2) e commitar/push a cada slice.

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
