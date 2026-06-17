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
modelo/renderer próprios).

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
- [~] **Fase B** — profundidade Ork: **Warboss, Meganob, Gretchin e WAAAGH! Overlord feitos**.
  O Overlord (`WaaaghOverlordData` SavedData global + `WaaaghOverlordManager`) cresce com a
  prosperidade imperial (cada cidade alimenta), tem tier 0-4 com anúncio global, e escala todos
  os camps (warbands maiores + Meganobz mais cedo). Faltando: Killa Kan, warbands distintas por clã.
- [ ] **Fase C** — tipos de cidade implementados de fato com tropas-tema (Skitarii/Forge,
  Cadian-Kasrkin/Fortress, Sisters/Shrine, Arbites-Enforcer/Hive). Hoje os tipos só variam
  nome/produção/população — falta variar as tropas recrutadas.
- [ ] **Fase D** — overlords globais: território, geração de assentamentos no worldgen, despacho de
  líderes por nível de ameaça.
- [ ] **Fase E** (maior risco) — mundo achatado + menor + dimensões-planeta substituindo Nether/End
  + viagem planetária (via Spaceport).
- [ ] **Transversal** — conteúdo (armas/armaduras/recursos por facção) para não ficar entediante.

**Foco atual recomendado:** terminar a Fase B (WAAAGH! overlord + Meganob/Gretchin), depois Fase C.

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
