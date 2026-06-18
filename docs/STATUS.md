# First Crusade — STATUS (leia isto primeiro ao reorientar)

> Arquivo de orientação do agente. Quando o chat for limpo (`/clear`), **leia este arquivo
> primeiro** para retomar o contexto. **Mantenha-o atualizado** ao fim de cada bloco de trabalho
> (estado atual + metas + changelog).
>
> ⚠️ O dono do projeto também desenvolve em paralelo (commits via "da run"/"da run pfb"). Antes de
> mudanças grandes, **verifique o estado real** com `git log --oneline -8`, `git status` e um
> `Glob` em `src/main/java/com/example/examplemod/*.java` — não confie só neste arquivo.

Última atualização: **2026-06-17** · branch `main` · remoto `github.com/Guaxinim-ui/first-crusade-mod`

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

O wrapper `./gradlew` falha (sem rede). Use o Gradle 8.8 em cache, com `-p` na raiz e `--offline`,
e `dangerouslyDisableSandbox: true`:

```
G=~/.gradle/wrapper/dists/gradle-8.8-bin/8szhhswteo6aqkq0cvol8b0hg/gradle-8.8/bin/gradle
"$G" -p /d/forge-1.20.1-47.4.10-mdk compileJava --console=plain --offline   # ~1 min
"$G" -p /d/forge-1.20.1-47.4.10-mdk build --console=plain --offline         # gera o jar
"$G" -p /d/forge-1.20.1-47.4.10-mdk runClient --console=plain               # abre o jogo (background)
```

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
Tempestus, hotshot lasgun, **recrutado pela Fortress City**).

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
- [~] **Fase C** — tipos de cidade com tropas-tema. **Em andamento**: (1) bônus de rank por tipo
  (`ImperialCityType.getRecruitRankBonus` em `getStartingGuardsmanRank` via `GuardsmanRank.advance`) —
  Fortress +2 (Shock Trooper), Forge +1, Hive -1 (Hive Levy, mas 2x pop); nome temático
  (`getTroopName`). (2) **Regimento de combate por tipo**: cada `ImperialCityType` traz modificadores
  reais de hp/armor/dano/lasgun/velocidade (espelhando `ImperiumChapter`), aplicados no Guardsman
  via campo `cityType` (NBT "CityType") em `applyRankStats`/`getLasgunDamageWithBonuses`, e o nome
  mostra o regimento. Recruta marcado no Core (`initializeFromCity(rank, cityType)`). Fortress tanky,
  Forge bem-equipado, Mining durão, Agri ágil, Hive fraco-mas-numeroso. (3) **Custo de recruta por
  tipo** em Ferro (`recruitIronCost`, cobrado em `tryPayRecruitCost`): Hive 2 … Fortress 8.
  (4) **Skitarii Ranger** — 1ª tropa-entidade própria (Forge/Mechanicus), standalone com Lasgun,
  registrada e testável por spawn egg. (5) **Forge City recruta Skitarii**: `completeRecruitTraining`
  ramifica por `getCityType()==FORGE` → cria `SkitariiRangerEntity` (helpers `spawnTrainedSkitariiRanger`/
  `spawnTrainedGuardsman`); o tally `recruitedGuardsmen` (e `reorganizeExistingGuardsmen` no upgrade)
  conta Skitarii. (6) **Kasrkin** — 2ª tropa-entidade própria (Fortress/Militarum Tempestus),
  elite standalone com hotshot Lasgun (44 HP, 13 armor, dano 8); `completeRecruitTraining` agora é
  um switch por tipo (FORGE→Skitarii, FORTRESS→Kasrkin, default→Guardsman) e a recontagem do upgrade
  conta os três. **Próximo**: mais tropas (Sisters/Shrine, Arbites/Enforcer/Hive) e estruturas por tipo.
- [ ] **Fase D** — overlords globais: território, geração de assentamentos no worldgen, despacho de
  líderes por nível de ameaça.
- [ ] **Fase E** (maior risco) — mundo achatado + menor + dimensões-planeta substituindo Nether/End
  + viagem planetária (via Spaceport).
- [ ] **Transversal** — conteúdo (armas/armaduras/recursos por facção) para não ficar entediante.

**Foco atual recomendado:** Fase C — fazer cada tipo de cidade recrutar tropas-tema próprias
(começar reusando GuardsmanEntity com ranks/equipamentos distintos por tipo antes de criar
entidades novas como Skitarii/Sisters).

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
