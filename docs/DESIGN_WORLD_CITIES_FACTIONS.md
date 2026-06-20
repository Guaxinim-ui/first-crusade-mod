# First Crusade — Mundo, Cidades e Facções (Visão Mestra)

Este documento consolida a visão de longo prazo: dois impérios que controlam o mundo (Imperium
e WAAAGH!), assentamentos autônomos de muitos tipos que nascem e se governam sozinhos, líderes
que marcham contra ameaças, e um mundo redesenhado para viagem planetária. É a referência que
orienta todo o desenvolvimento futuro.

Relaciona-se com:
- [DESIGN_W40K_AUTONOMY.md](DESIGN_W40K_AUTONOMY.md) — pirâmide imperial e sistemas autônomos já parcialmente implementados.
- Código atual: `ImperialCommandCoreBlockEntity` (assentamento imperial), `OrkCampBlockEntity`
  (assentamento Ork), `FirstCrusadeFactionManager` (facções), `ImperialPrimarchManager`,
  `ImperialCustodesManager`, `OrkCampManager`.

---

## 0. Princípio central

O mundo tem **dois donos**: o **Imperium** (o Imperador) e o **WAAAGH!** (o líder Ork). Ambos:

- têm controle total do mundo e disputam territórios;
- fazem **assentamentos nascerem sozinhos**, cada um com comando independente (estende o
  `ImperialCommandCore` / `OrkCamp` atuais);
- enviam **Primarcas / Comandantes** para dominar assentamentos;
- **só lutam pessoalmente quando a ameaça é grande** — medida por **quantidade e qualidade**
  do exército inimigo. Acima de um limiar, o líder **marcha até a ameaça com seu exército
  pessoal** para resolvê-la.

Tudo deve girar sem o jogador. O jogador é mais um ator num mundo vivo, não o motor dele.

---

## 1. Sistema de ameaça (núcleo de toda a IA estratégica)

Cada assentamento e cada líder avalia ameaças por um **Threat Score** = função de:

```
threat = (soma do "poder" de cada unidade inimiga próxima)
poder_da_unidade = peso_por_tipo (qualidade) * 1 (quantidade)
```

Pesos de qualidade (exemplo a calibrar):

```
Gretchin/Milícia .......... 1
Boy/Guardsman ............. 3
Nob/Veterano/Skitarii ..... 6
Meganob/Space Marine ...... 12
Killa Kan/Dreadnought ..... 20
Warboss/Custodes .......... 30
Primarca/Gargante ......... 80
```

Níveis de resposta (por assentamento):

```
0  Calmo        → produção e vida normal
1  Vigilância   → patrulhas reforçadas, recruta mais tropas
2  Alerta       → tropas se concentram, estruturas defensivas ativam
3  Cerco        → todas as tropas defendem; pede reforço ao overlord
4  Crítico      → o overlord envia um Comandante/Primarca + exército pessoal
```

**Comportamento de líder**: um Primarca/Warboss só sai do seu trono quando o threat de um alvo
ultrapassa o nível 4 (ou quando ele é quem ataca). Aí ele **caminha até a ameaça acompanhado
de seu exército pessoal** (squad de elite que o segue). Isso já existe em embrião:
`PrimarchEntity.leadNearbyTroops()` + `ImperialPrimarchManager.leadSortieAgainstCamp()`.

> Já implementado parcialmente: threat textual do Core (`getThreatScore`), liderança do Primarca,
> sortie contra camp. Falta o sistema numérico unificado por qualidade/quantidade e o "exército
> pessoal" que acompanha o líder.

---

## 2. Facção Imperial — tipos de cidade

Cada tipo é uma **variante do Command Core** (mesmo motor: população, recursos, defesa, threat),
diferindo em: estruturas que constrói, tropas que recruta, recurso-foco e bônus.

Implementação sugerida: um enum `ImperialCityType` + tabela de dados (tropas, estruturas,
recurso-foco) que o Core lê. As cidades nascem via um `ImperialWorldManager` que escolhe o tipo
conforme o bioma/terreno.

| # | Tipo | Papel | Tropas características | Foco |
|---|------|-------|------------------------|------|
| 1 | **Hive City** | Megacidade vertical industrial, superpopulosa | PDF, Adeptus Arbites, Enforcers, Guardsmen, gangues, milícias, bounty hunters, security servitors | População massiva + indústria |
| 2 | **Underhive** | Subníveis abandonados da Hive: túneis, ruínas, gangues | Gangues, mutantes, bandidos, mercenários, caçadores de recompensa, cultistas, milícias, patrulhas Arbites, servitors abandonados | Zona cinzenta (semi-hostil) |
| 3 | **Forge City** | Cidade-fábrica do Adeptus Mechanicus | Skitarii Rangers/Vanguard, Tech-Priests, Combat/War Servitors, Kataphron, Secutarii, apoio de Titan/Knight | Produção avançada (armas/veículos) |
| 4 | **Fortress City** | Bastião militar (estilo Cadia) | PDF pesado, Guardsmen profissionais, Shock Troops, artilharia, tanques, Heavy Weapon Squads, Comissars, Officers, Kasrkin | Defesa máxima |
| 5 | **Civilised World City** | Cidade imperial "normal", autossustentável | PDF local, polícia, Arbites, milícia urbana, Guardsmen, guardas nobres, oficiais | Equilíbrio geral |
| 6 | **Agri City** | Cidade agrícola que alimenta o setor | PDF rural, milícia agrícola, guardas de silo, patrulhas mecanizadas, Arbites, bastiões | Produção de comida |
| 7 | **Mining City** | Cidade mineradora perto de minas/refinarias | PDF, Mine Guards, Industrial Enforcers, mining servitors, mercenários, Arbites | Matéria-prima |
| 8 | **Shrine City** | Cidade religiosa de templos e peregrinos | Adeptus Ministorum, Frateris Militia, Sisters of Battle, pilgrim mobs, zealots, Relic Guards, PDF | Fé / buffs |
| 9 | **Penal City** | Colônia penal violenta | Prison Guards, Penal Legion, Arbites, PDF de segurança, containment servitors, criminosos armados, rebeldes | Tropas descartáveis |
| 10 | **Death World Settlement** | Mundo letal (selva/veneno/clima) — estilo Catachan | Jungle Fighters, survivalists, PDF endurecido, caçadores, Guardsmen veteranos, patrulheiros | Tropas de elite veteranas |
| 11 | **Feudal City** | Mundo medieval, castelos e cavaleiros | Milícia feudal, cavaleiros, guardas de castelo, arqueiros/lanceiros, PDF treinado, missionários | Tropas primitivas baratas |
| 12 | **Frontier Outpost** | Posto pequeno em mundo distante | PDF pequeno, scouts, Guardsmen destacados, mercenários, exploradores, Arbites isolados, patrulhas de comboio | Expansão / vanguarda |
| 13 | **Spaceport City** | Cidade-porto orbital/terrestre | Port Guards, Astra Militarum em trânsito, Navy Armsmen, Arbites, Customs Enforcers, cargo servitors, pilotos, Enginseers | Logística / viagem planetária |
| 14 | **Imperial Capital City** | Capital planetária administrativa | Governor's Guard, PDF de elite, Arbites, Astra Militarum, Commissars, Officers, Noble House Guards, Relic Guards, Inquisição ocasional | Comando do planeta |

Notas de implementação:
- Reaproveitar `GuardsmanEntity` com **ranks/especializações** já existentes para cobrir muitas
  dessas tropas (PDF, Veterano, Sniper, etc.) antes de criar entidades novas.
- Tropas realmente distintas (Skitarii, Sisters of Battle, Arbites, Enforcers) viram entidades
  próprias só quando o tipo de cidade correspondente for implementado.
- Capital e Fortress são "tipos finais" (alto threat, atraem ataques Ork grandes).

---

## 3. Facção Ork — clãs do WAAAGH!

Cada clã é uma **variante do Ork Camp** (mesmo motor: garrison + WAAAGH! + warband), diferindo
em tropas, tema mecânico e tipo de pressão. Enum `OrkClan` + tabela de dados.

| # | Clã | Tema mecânico | Tropas características |
|---|-----|---------------|------------------------|
| 1 | **Goffs** | Combate corpo a corpo, hordas | Ork Boyz, Nobz, Meganobz, Warboss, Stormboyz, Gretchin |
| 2 | **Evil Sunz** | Velocidade e veículos | Warbikers, Deffkoptas, Buggies, Trukks, Battlewagons, Speed Freeks |
| 3 | **Bad Moons** | Ricos, "more dakka", melhor equipamento | Shoota Boyz, Lootas, Flash Gitz, Mek Gunz, Big Meks, Nobz equipados |
| 4 | **Deathskulls** | Saque e sucata (combina com o scrap do mod!) | Lootas, Meks, Big Meks, Gretchin, Killa Kans, Deff Dreads, Boyz com equipamento roubado |
| 5 | **Blood Axes** | Táticas, emboscadas, camuflagem | Kommandos, Sneaky Boyz, Scouts, Mercenary Orks, Boyz camuflados, veículos capturados |
| 6 | **Snakebites** | Primitivos, bestas e squigs | Beast Snagga Boyz, Squighog Boyz, Squigs, Beastboss, Wurrboy, Savage Boyz |
| 7 | **Freebooterz** | Piratas mercenários independentes | Flash Gitz, Kaptin, Pirate Boyz, Lootas, Mercenary Orks |
| 8 | **Speed Freeks / Kult of Speed** | Tudo em velocidade extrema | Warbikers, Deffkoptas, Buggies, Trukks, Battlewagons, Speed Boss |
| 9 | **Beast Snaggas** | Caçadores de monstros/máquinas, montarias | Beast Snagga Boyz, Squighog Boyz, Nob on Smasha Squig, Beastboss, Kill Rig, Wurrboy |
| 10 | **Dread Mob** | Máquinas e robôs improvisados | Deff Dreads, Killa Kans, Meka-Dreads, Big Meks, Mekboyz, Gretchin mecânicos |
| 11 | **Green Tide** | Horda clássica em massa | Ork Boyz, Gretchin, Nobz, Warboss, Painboy, Weirdboy |
| 12 | **Bully Boyz** | Orks grandes e brutais | Nobz, Meganobz, Warboss, Big Nob, Boss Nob |
| 13 | **Tankbusta Mob** | Destruir veículos/estruturas | Tankbustas, Bomb Squigs, Rokkit Boyz, Nob com rokkit |
| 14 | **Kommando Mob** | Furtividade e sabotagem | Kommandos, Boss Kommando, Sneaky Boyz, Explosive Boyz |
| 15 | **Gretchin Mob** | Mão de obra / bucha de canhão | Gretchin, Runtherd, Killa Kans, Grot crew |

Notas de implementação:
- Reaproveitar `OrkBoyEntity`/`OrkNobEntity` como base; clãs trocam **stats, equipamento e
  tipo de warband**. Entidades novas (Meganob, Killa Kan, Stormboy, Gretchin) entram por fase.
- **Deathskulls** liga-se diretamente ao sistema de **Scrap Metal** já existente — clã ideal
  para o primeiro clã "especial".
- Cada clã alimenta o **WAAAGH! global** do líder Ork (overlord), que escala a pressão.

---

## 4. Os Overlords (Imperador e Líder do WAAAGH!)

Camada acima dos assentamentos: dois "donos do mundo" como gerentes globais (sem entidade
física, ou com avatar opcional). Um `WorldFactionOverlordManager` (ou um por facção):

- **Geração de assentamentos**: decide onde e que tipo de cidade/camp nasce, conforme bioma e
  território controlado.
- **Território**: o mundo é dividido em zonas; cada zona pertence a um overlord.
- **Resposta a ameaça**: quando um assentamento atinge nível de ameaça 4, o overlord despacha um
  **Comandante/Primarca + exército pessoal** (entidade líder que marcha até o ponto).
- **Economia estratégica**: o overlord acumula recursos dos seus assentamentos e os investe em
  reforços, novos assentamentos e líderes.

Mapeia para o canon: Imperador envia Primarcas/Custodes; líder do WAAAGH! envia Warbosses/Big
Meks. Já temos Primarca e Custodes do lado imperial — falta o lado Ork (Warboss-líder) e a
camada de overlord global.

---

## 5. Mundo: viagem planetária e terreno

Mudanças estruturais de mundo (fase dedicada, alto risco técnico — fazer por último):

- **Substituir Nether e The End** por **dimensões-planeta**, cada uma com clima/bioma próprio e
  tipos de cidade característicos (ex.: planeta-colmeia, mundo-forja, mundo-morte). Viagem
  planetária via **Spaceport City** (#13) ou um portal/dropship.
- **Mundos menores**: reduzir o tamanho/raida de mundo (worldborder pequena por dimensão) para
  tornar a viagem planetária significativa.
- **Menos cavernas + altura reduzida**: customizar o `noise settings`/worldgen para terreno mais
  raso e achatado. O "orçamento" de blocos economizado vira **estruturas de superfície mais
  detalhadas** (cidades grandes, fortalezas, camps).
- **Geração das cidades/camps** integrada ao worldgen (structure features) em vez de spawn só
  pelo Core — para o mundo já nascer povoado pelas duas facções.
- **Planetas pequenos e "fechados"**: cada dimensão-planeta tem x/y/z **bem reduzidos** (worldborder
  apertada + altura baixa) e é tratada como um **tabuleiro**, não um mundo de sobrevivência. O jogo
  deixa de ser sobre extrair blocos: **sem mineração / sem quebrar blocos** (ou bloqueio total de
  break, ou planetas de material indestrutível). A economia vem das **cidades autônomas** (produção
  por tipo), não do jogador picaretando. O foco do jogador é **comandar**, não minerar.

Ordem técnica recomendada: terreno achatado/sem cavernas → worldborder menor → dimensões-planeta
→ viagem planetária. Cada passo é testável isolado.

### 5.1 Mesa de Guerra (War Table) — interface estratégica do Core

Visão de **longo prazo** (depois de tudo, junto com os planetas reduzidos): a tela do
`ImperialCommandCoreBlockEntity` evolui de painel de gestão para uma **"Mesa de Guerra"** — um
**mapa tático em escala reduzida** do planeta (grade x/y/z limitada, estilo tabuleiro Warhammer).
Em vez de listar números, mostra **fichas/ícones** sobre o mapa:
- **Aliados** (cidades, tropas, líderes) e **inimigos** (camps, warbands, Warboss) como peças.
- **Ícones de evento**: invasões em curso, cidades sob cerco, defesas ativas, despacho de líder —
  acompanhando o `ThreatAssessmentManager` e o futuro `WorldFactionOverlordManager`.
- Linhas de movimento/ameaça entre pontos (como rotas no tabuleiro da referência).
Serve como o **HUD estratégico** da facção: o jogador comanda olhando a mesa, sem precisar viajar
até cada cidade. Depende do mundo já estar reduzido/territorializado (Fase D/E), por isso vem por
último. Implementação provável: render custom no `ImperialCommandCoreScreen` (camada de mapa +
sprites de ícone), alimentado por dados agregados do overlord global.

---

## 6. Conteúdo (armas, armaduras, recursos — "não entediante")

Para sustentar tantas tropas e tipos, expandir conteúdo por **temas**, não item a item:

- **Armaduras**: já temos Guardsman e Space Marine (placeholder). Adicionar por facção/tropa:
  Skitarii, Sisters, Arbites/Enforcer, Cadian/Kasrkin, Custodes (textura dourada própria),
  Primarca. Orks: Boy/Nob/Meganob, Mek, Kommando.
- **Armas**: lasgun (existe) → bolter, plasma, melta, chainsword, power weapon (Imperium);
  shoota, choppa, rokkit, big shoota, kustom mek weapons (Orks). Cada uma com efeito distinto
  (não só dano: knockback, fogo, perfuração, AoE) para variedade.
- **Recursos/produção**: além de Iron/Coal/Scrap/Gold/Emerald/Crusadium/GeneSeed (já existem),
  ligar cada **tipo de cidade** a um recurso-foco (Agri→comida, Forge→componentes, Mining→
  minério, Shrine→fé/relíquias). Orks: **Teef** (dentes) como moeda + Scrap. Cadeias de produção
  com etapas (minério→componente→arma) para manter o loop interessante.
- **Sistemas anti-tédio**: eventos (invasões de clã específico), recompensas por destruir camps,
  promoções visíveis, relíquias/buffs de Shrine, mercado entre cidades, contratos de mercenário.

---

## 7. Mapeamento para a arquitetura atual

| Visão | Já existe | A criar |
|-------|-----------|---------|
| Assentamento imperial | `ImperialCommandCoreBlockEntity` + managers | `ImperialCityType` (enum + dados), variação de tropas/estruturas |
| Assentamento Ork | `OrkCampBlockEntity` + `OrkCampManager` | `OrkClan` (enum + dados), warbands por clã |
| Facções/combate | `FirstCrusadeFactionManager` | subfacções (clãs/ordens), regras finas |
| Líder que marcha | `PrimarchEntity` + `ImperialPrimarchManager` | "exército pessoal" que segue; lado Ork (Warboss) |
| Ameaça | `getThreatScore` textual | Threat Score numérico por qualidade×quantidade (§1) |
| Overlord global | — | `WorldFactionOverlordManager` (geração, território, despacho de líderes) |
| Mundo | worldgen vanilla | terreno achatado, mundos menores/fechados, sem mineração, dimensões-planeta |
| HUD estratégico | tela de gestão do Core | **Mesa de Guerra**: mapa tático com fichas/ícones (§5.1) |

Regra de ouro mantida: **um manager por sistema**, nada inchando o Core/Camp; tropas reusam
`GuardsmanEntity`/`OrkBoyEntity` (ranks/variantes) antes de virar entidades novas.

---

## 8. Roadmap faseado (proposto)

Cada fase é testável e fecha um loop. Ordenadas por dependência e risco crescente.

**Fase A — Fundação estratégica (baixo risco, alto valor)**
1. **Threat Score numérico** (§1): peso por qualidade × quantidade, níveis 0–4. Base de tudo.
2. **`ImperialCityType` + `OrkClan`** como enums com dados (tropas, estrutura, recurso-foco),
   ainda usando entidades atuais (variar stats/equipamento). Cidades/camps passam a ter "sabor".
3. **Exército pessoal do líder**: squad de elite que acompanha Primarca/Warboss ao marchar.

**Fase B — Facção Ork completa**
4. Entidades-chave Ork por fase: Meganob, Stormboy, Gretchin, Killa Kan.
5. 3–4 clãs jogáveis primeiro (Goffs, Bad Moons, Deathskulls, Evil Sunz) com warbands distintas.
6. **Warboss-líder** Ork (espelho do Primarca) + overlord do WAAAGH!.

**Fase C — Facção Imperial ampliada**
7. Tropas-tema novas conforme tipos de cidade: Skitarii (Forge), Cadian/Kasrkin (Fortress),
   Sisters (Shrine), Arbites/Enforcer (Hive/Civilised).
8. 4–5 tipos de cidade implementados de fato (Hive, Forge, Fortress, Agri, Mining).

**Fase D — Overlords e mundo vivo**
9. `WorldFactionOverlordManager`: território, geração de assentamentos no worldgen, despacho de
   líderes por threat.

**Fase E — Mundo e viagem planetária (maior risco técnico)**
10. Terreno achatado + menos cavernas + altura reduzida.
11. Worldborder menor por mundo → **planetas pequenos/fechados, sem mineração nem quebra de blocos**
    (§5): o jogo vira comando estratégico, não extração.
12. Dimensões-planeta substituindo Nether/End + viagem via Spaceport/dropship.
13. **Mesa de Guerra** (§5.1): tela do Core vira mapa tático com fichas de aliados/inimigos e ícones
    de invasão/defesa. Vem por último — depende de mundo reduzido + overlord global (Fase D).

**Transversal (sempre): conteúdo (§6)** — armas/armaduras/recursos acompanham cada fase para não
ficar entediante.

---

## 9. Decisões abertas (para alinhar antes de implementar)

- **Entidades novas vs. variantes**: até onde reusar Guardsman/OrkBoy antes de criar entidade
  nova? (Recomendo reusar ao máximo na Fase A/B.)
- **Escopo do "mundo menor"**: worldborder configurável vs. worldgen totalmente custom?
- **Quantos tipos/clãs no MVP** de cada facção antes de ir para overlords/mundo?
- **Viagem planetária**: dimensões reais (Nether/End substituídos) ou "mundos" via teleporte
  dentro de uma dimensão? (Dimensões reais é o correto, mas mais caro.)
