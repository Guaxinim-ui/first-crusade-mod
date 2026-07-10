# ROADMAP — First Crusade

> Programação do que falta no mod. Atualizado em 2026-07-10.
> Legenda: **[DONO]** = modelos/animações/estruturas/testes no runClient · **[CLAUDE]** = código, integração, texturas por script.
> Regra combinada: **estruturas** o dono ainda vai fazer e mandar o modelo; **animações de mobs e armas** são do dono.

---

## Visão geral das trilhas

| Trilha | O quê | Depende de |
|---|---|---|
| 1 | Testar o que já está pronto (e corrigir) | nada — pode começar já |
| 2 | Ferramenta de Construção (ghost MineColonies) | nada — plano já travado |
| 3 | Estruturas novas das cidades | modelos do dono |
| 4 | Fila de mobs GeckoLib (modelos + anims + texturas) | modelos/anims do dono |
| 5 | Armas animadas + sons | anims do dono |
| 6 | Facção do jogador (efeitos) + lado Ork jogável | decisões do dono |
| 7 | Polimento, balance e limpeza | tudo acima |

---

## Trilha 1 — Testar o que está pronto ✅ APROVADA (2026-07-10)

Vista e aprovada pelo dono em jogo:

- [x] **[DONO]** Lorde Comandante + squads (ataque mobiliza guarnição, formação, cooldowns)
- [x] **[DONO]** Elites novos em jogo: Space Marine / Custodes / Primarch (proporções, animações, ataque)
- [x] **[DONO]** Warboss e Ork Boy com os modelos/UVs novos
- [x] **[DONO]** Lança Guardiã na mão do jogador (botão direito atira, esquerdo bate) e Custodes atirando de longe
- [x] **[DONO]** Vilas "par de guerra" em mundo novo, morale, tela de facção no 1º login
- [x] **[CLAUDE]** Ajustes pós-teste: sprites 32×32 das armas (lança = réplica da arma do modelo do Custodes; power sword maior e mais detalhada)

## Trilha 2 — Ferramenta de Construção ✅ CÓDIGO PRONTO (2026-07-10)

Implementado — **[DONO]** só falta validar o ghost em jogo:

- [x] `CityStructureType` (enum das 8 estruturas: custo iron/scrap/coal, nível, footprint, placer)
- [x] `CityBuilderToolItem` (sneak+usar alterna estrutura; usar = posiciona; NBT vinculado ao Core)
- [x] Core: `giveBuilderTool` + `placeStructureWithTool` (dono/nível/fronteira/área livre/custo)
- [x] `buildAt(...)` nos 8 managers (reusa placement/staff/bind do auto-build)
- [x] Aba Build do Core vira 1 botão "Pegar Ferramenta"
- [x] Ghost translúcido (`BuilderToolGhostRenderer`, verde=ok / vermelho=bloqueado)
- [x] Registro: item + criativa + model + textura + lang pt/en; build OK
- [ ] **[DONO]** validar em jogo: pega a ferramenta na aba Build, mira no chão (ghost aparece?), sneak troca estrutura, clica constrói dentro da fronteira

## Trilha 3 — Estruturas das cidades (aguardando seus modelos)

- [ ] **[DONO]** Fazer os modelos das estruturas e mandar (ver **contrato de entrega** abaixo)
- [ ] **[CLAUDE]** Trocar os templates procedurais do `CityArchitect` pelos seus modelos, por tipo de cidade e era (anti-colisão/zonas/portas já existem)
- [ ] **[CLAUDE]** Ligar as estruturas novas na Ferramenta de Construção (Trilha 2)
- [ ] Sugestão de ordem: Command Core → Barracks → Habitation → Forge → Farm/Mine → especiais (Strategium, Spaceport, prédios Ork)

**Contrato de entrega — estrutura:** o ideal é **structure block (.nbt)** do próprio Minecraft (constrói no mundo, salva com structure block). Se preferir schematic/litematica, me avisa o formato que eu adapto o loader. Junto do arquivo: 1 linha dizendo qual prédio é e onde fica a "porta/frente".

## Trilha 4 — Fila de mobs GeckoLib

Processo por mob (já rodado 5×): **[DONO]** geo + animações → **[CLAUDE]** textura (por script ou integro a sua), entidade `GeoEntity`, renderer, registro. Se preferir, eu também faço o geo por código (como fiz nos elites) e você só anima.

Ordem sugerida (por presença em jogo):

- [ ] **Guardsman** (mob mais comum; já tem seam de variantes por regimento via `GeoVariantTextured`)
- [ ] **Ork Nob / Gretchin / Meganob** (war parties; Meganob de mega-armadura)
- [ ] **Killa Kan** — prioridade visual: é um WALKER e hoje usa placeholder humanoide
- [ ] **Kasrkin / Skitarii Ranger / Sister of Battle** (tropas temáticas por tipo de cidade)
- [ ] **City Commander** (Lorde Comandante — hoje textura = guardsman)
- [ ] **Roboute Guilliman** (converter o modelo à mão para GeckoLib, padrão do Primarch)
- [ ] Enforcer / Mine Guard / Agri Militia / Penal Legionnaire / Jungle Fighter / Feudal Knight
- [ ] Imperial Citizen (civis; variantes de textura)

**Contrato de entrega — mob:** `geo/<nome>.geo.json` (Blockbench → Export Bedrock Geometry) + `animations/<nome>.animation.json` com `animation.<nome>.idle/walk/attack`; bones `body/head/right_arm/left_arm/right_leg/left_leg` (+ `arma_*` para armas). Coloca direto em `src/main/resources/assets/firstcrusade/` (NÃO em `src/src/`) ou me manda o zip que eu instalo.

## Trilha 5 — Armas animadas + sons

- [ ] **[DONO]** Animações das armas (Blockbench): chainsword (serra girando), lança guardiã, bolter, power sword, choppa/shoota
- [ ] **[CLAUDE]** Converter os itens para itens animados GeckoLib quando as anims chegarem (hoje são sprites 2D `item/handheld`)
- [ ] **[CLAUDE]** Sons próprios: hoje TODO tiro usa `BLAZE_SHOOT`. Registrar sound events + .ogg (bolter, lasgun, plasma, serra). **[DONO]** consegue os .ogg (ou eu gero placeholders sintéticos)
- [ ] **[CLAUDE]** Render de arma na mão dos mobs GeckoLib que ainda vão chegar (arma como bone, padrão atual)

## Trilha 6 — Facção do jogador + lado Ork

A tela de escolha (Imperium × Orks) existe e a hostilidade já respeita a escolha. Efeitos pendentes — **[DONO] define, [CLAUDE] implementa**:

- [ ] Spawn inicial por facção (cidade imperial vs camp Ork)?
- [ ] Player IMPERIUM: reivindicar/gerenciar Core (governador/fronteira já existem)
- [ ] Player ORKS: o que ele ganha? (GUI própria? WAAAGH! pessoal? recrutar Boyz? lootear vira recurso?)
- [ ] Economia/estruturas Ork jogáveis (hoje o lado Ork é só IA: camp + loot pit)
- [ ] Itens/armas restritos por facção?

## Trilha 7 — Polimento, balance e limpeza

- [ ] Balance das armas novas (lança 11/12 de dano, power sword ~13, tiers) após teste
- [ ] Backlog pequeno do STATUS: dormir→filhos (F2), aspirante→Space Marine (F3, partes), corrupção auto-espalhar, squads de reforço/patrulha explícitos, comandante na GUI do Core
- [ ] Partículas/efeitos (plasma, power field da espada)
- [ ] Limpeza do repo: apagar `src/src/` e `aniumações.zip` (sobra de extração), commitar assets novos
- [ ] Passada de lang pt/en nas coisas novas

---

## Ordem sugerida (blocos)

1. **Bloco A ✅:** Trilha 1 (testes) aprovada.
2. **Bloco B ✅ (código):** Trilha 2 (Ferramenta de Construção) implementada — falta só o teste em jogo do ghost.
3. **Bloco C:** Trilha 3 quando os primeiros modelos de estrutura chegarem (Core/Barracks/Habitation primeiro). Quando os modelos chegarem, a Ferramenta de Construção já os posiciona (é só trocar o que cada `buildXStructure`/`CityStructureType` coloca pelo modelo do dono).
4. **Bloco D:** Trilha 4 em fatias — 1 a 3 mobs por vez, na ordem da lista (Guardsman e Killa Kan primeiro).
5. **Bloco E:** Trilha 5 quando tiver as anims de arma.
6. **Bloco F:** Trilha 6 quando você definir os efeitos de facção; Trilha 7 corre por fora, sempre que sobrar espaço.
