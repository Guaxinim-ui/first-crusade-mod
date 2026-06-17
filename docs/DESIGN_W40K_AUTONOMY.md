# First Crusade — Design: Império Vivo e Autônomo

Objetivo central: **tudo vive por conta própria**. O jogador é o Comandante, mas a cidade,
os cidadãos, os soldados, os Space Marines, os Custodes e o Primarca devem agir sozinhos —
trabalhar, treinar, defender, promover e reagir sem microgerência. O jogador dá *direção*,
não *cliques*.

Este documento mapeia a hierarquia canônica de Warhammer 40k sobre as mecânicas do mod e
propõe sistemas novos. Cada item indica como encaixa na arquitetura atual (Core + managers).

---

## 1. A pirâmide imperial (canônica → gameplay)

Hierarquia real do Imperium, do topo à base:

```
Imperador / Primarca (Regente)      → liderança única, lendária, 1 por império
        ↓
Adeptus Custodes (Guarda do Trono)  → guarda de elite do Core/Primarca, pouquíssimos
        ↓
Adeptus Astartes (Space Marines)    → elite, Capítulos, descendem do Primarca, gene-seed
        ↓
Astra Militarum (Guardas)           → o grosso da força, humanos comuns, vêm da população
        ↓
Cidadãos imperiais                  → população civil, trabalho, base de tudo
```

Princípio de design: **cada tier nasce do tier de baixo**. Custodes não se compram; emergem
de uma cidade próspera e de Marines veteranos. Isso cria uma progressão orgânica e dá sentido
ao "viver por conta própria".

---

## 2. Camada civil — a cidade que respira

A base de tudo. Hoje o `ImperialCitizen` já trabalha; a proposta aprofunda a *vida*.

### 2.1 Necessidades e moral (autônomo)
- Cidadãos passam a ter **moral/contentamento** (0–100), calculado por:
  - comida disponível (Farm), moradia (Habitation), segurança (raids recentes), lotação.
- Moral alta → mais produção e crescimento populacional. Moral baixa → fuga/parada.
- Gerido por um novo `ImperialCityMoraleManager` (tick lento, já temos o padrão de tick 200).

### 2.2 Ciclo de vida civil
- Cidadãos têm **rotina diária**: trabalham de dia, recolhem-se às Habitations à noite.
- **Nascimento** já existe (`ImperialPopulationManager`); adicionar **envelhecimento leve**
  e substituição (um cidadão velho "se aposenta", um novo nasce) para sensação de vida.
- Tipos civis novos além de jobs de produção: `HAULER` (carrega recursos visíveis entre
  estrutura e Core), `PREACHER` (buffa moral), `MEDICAE` civil.

### 2.3 Mercado/economia viva
- `Emerald Trade Depot`: um cidadão `TRADER` leva excedente de recursos e volta com
  Emerald/War Support, representando comércio com a Capital. Conecta Gold/Emerald (hoje
  inertes no Core — ver §6).

---

## 3. Astra Militarum — a guarda autônoma

Hoje o Guardsman já patrulha e defende. Propostas para dar autonomia real:

- **Patrulhas dinâmicas**: em vez de postos fixos, esquadrões rotacionam entre muralha,
  portões e pontos de interesse (`ImperialPatrolManager`).
- **Esquadrões (Squads)**: agrupar 5–10 Guardsmen sob um `Sergeant` (já existe o rank).
  O Sergeant dá buff de coesão e lidera o movimento; o esquadrão se move junto.
- **Reação a ameaças sem ordem do jogador**: ao detectar Orks, o oficial mais próximo
  emite "rally" automático localmente (reusa `ImperialDefenseManager`).
- **Logística**: soldados consomem munição/Power Cell produzida na Forge; sem suprimento,
  recuam para reabastecer. Liga a guerra à economia.

---

## 4. Adeptus Astartes — recrutamento canônico de gene-seed

Substituir "comprar Marine" por um **processo vivo** fiel à lore
(Aspirante → Neófito → Battle-Brother):

```
Guardsman Veterano (sobreviveu a N raids)
        ↓  selecionado como Aspirante
Aspirante  → vai à Apothecarion / Barracks especial, passa por "trials"
        ↓  implantes de gene-seed (consome Emperor Gene Seed)
Neophyte   → versão jovem, mais fraca que Marine, treina em combate
        ↓  implante final + tempo
Space Marine (Battle-Brother)
```

- Já existe `SpaceMarineUpgradeManager` e `emperorGeneSeed`. A proposta adiciona o **estágio
  Neophyte intermediário** (entidade ou estado do Guardsman) e o critério de elegibilidade
  por *veterania* (raids sobrevividas), não só por recurso.
- **Capítulos** (`ImperiumChapter` já existe!): cada cidade adota um Capítulo com cor/bônus
  próprio. Marines herdam identidade visual e um traço (ex.: defensivo, agressivo, ranged).
- **Apothecary** recolhe gene-seed de Marines mortos no campo (recupera recurso) — mecânica
  canônica e cria loop econômico-militar.

---

## 5. Topo da pirâmide — Custodes e Primarca (conteúdo de "endgame")

### 5.1 Adeptus Custodes — a Guarda do Trono
- **Não recrutável diretamente.** Surgem só em cidade nível 5 com excedente de Gene Seed
  "puro" (novo recurso raro) e vários Space Marines veteranos vivos.
- Função autônoma: **guardam permanentemente o Imperial Command Core**. Nunca saem do
  perímetro interno; são a última linha. Individualmente brutais (acima do Space Marine).
- Visual: armadura dourada (variante de textura do Space Marine), porte maior.
- 1–3 no máximo por império. Raros por design.

### 5.2 O Primarca — herói único do império
- **Um por save/Core.** Emerge de um ritual de altíssimo custo (Gene Seed + Crusadium Plate +
  vitórias acumuladas em raids). É o ápice de "a cidade gerou seu líder".
- Comportamento autônomo de **general**: aura de liderança que buffa toda tropa imperial
  num raio, foca os inimigos mais perigosos (Ork Nob/Warboss), e durante raids assume a
  linha de frente sozinho.
- Se cair, **luto**: penalidade temporária de moral/produção e cooldown longo até poder
  re-emergir. Dá peso real à unidade.
- Gerido por `ImperialPrimarchManager` (spawn, aura, estado de luto).

---

## 6. Recursos que faltam fechar (dívida do plano atual)

Hoje `receiveProducedResource` descarta GOLD, EMERALD, CRUSADIUM (`accepted = 0`) e o Core
não tem campos para eles. Para sustentar §2.3, §4 e §5:

- Adicionar campos e storage no Core: `gold`, `emerald`, `crusadium`, `pureGeneSeed`.
- Fontes: Gold Mine, Emerald Trade Depot, Forge (Crusadium), e drop de elite Ork.
- Sinks: upgrades avançados (Gold), comércio/War Support (Emerald), Custodes/Primarca
  (pure Gene Seed + Crusadium).

---

## 7. Inimigos vivos — para a defesa fazer sentido

Para "tudo viver por conta própria" valer também do lado inimigo:

- **Ork Camps** no mundo (origem das raids), com Warboss que cresce ("more dakka") quanto
  mais a cidade prospera — pressão escalável e autônoma.
- Raids deixam de ser só timer: um Camp "decide" atacar quando junta WAAAGH! suficiente.
- Atacar o Camp proativamente vira objetivo do jogador (e dos Marines/Primarca em sortie).

---

## 8. Ordem de implementação sugerida

Fatias pequenas, testáveis, cada uma fecha um loop:

1. **Fechar recursos** (§6): campos Gold/Emerald/Crusadium/pureGeneSeed no Core + UI. Base de tudo.
2. **Moral civil** (§2.1): `ImperialCityMoraleManager` ligando comida/moradia → produção.
3. **Squads + patrulhas** (§3): autonomia da guarda sem novas entidades.
4. **Recrutamento Astartes canônico** (§4): estágio Neophyte + veterania + Apothecary.
5. **Custodes** (§5.1): entidade de elite defensiva, gate por nível 5.
6. **Primarca** (§5.2): herói único + aura + luto.
7. **Ork Camps vivos** (§7): geração no mundo + WAAAGH! escalável.

Cada fatia segue a regra de arquitetura: Block/BlockEntity quando físico, sempre um Manager,
nunca inchar o `ImperialCommandCoreBlockEntity`.

---

## Fontes de lore
- Adeptus Custodes — Warhammer 40k Wiki (Fandom)
- Primarch — Warhammer 40k Wiki (Fandom)
- Creation of a Space Marine / Neophyte — Lexicanum & Fandom
- Astra Militarum, Imperium of Man — Warhammer 40k Wiki (Fandom)
