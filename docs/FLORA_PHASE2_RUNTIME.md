# First Crusade — Vegetação Fase 2 + decorador por chunk (runtime)

Este documento descreve o sistema de vegetação que roda **em tempo de execução, chunk a chunk**.
Ele não usa `PlacedFeature`, biome modifier, bioma customizado nem dimensão nova. A identidade da
vegetação é decidida pelo estado atual do território, não pelo bioma.

Pacotes:

- `com.example.examplemod.flora` — os 52 blocos, as tags e a config (base da Fase 2).
- `com.example.examplemod.flora.runtime` — todo o sistema de distribuição descrito aqui.

---

## 1. Visão geral

```
ChunkEvent.Load ─► FloraChunkQueue.offer()          (só empilha uma coordenada)
                          │
ServerTickEvent ─► FloraChunkQueue.tick()
                          ├─ drainIntake()  ─► FloraChunkSavedData  (já decorado? desatualizado?)
                          │                  └─ FloraRegionResolver (qual paleta?)
                          └─ processReady() ─► FloraChunkDecorator.decorate()
                                                 ├─ FloraRegionResolver.buildContext()
                                                 ├─ FloraExclusionZones.forChunk()
                                                 ├─ FloraChunkContext.paletteAt(x,z,y)   (por coluna)
                                                 └─ FloraPlacementRules.place()
```

O chunk é a unidade de **classificação, enfileiramento, orçamento, decoração e persistência**.
Ele **não** é a unidade de aparência — isso é resolvido coluna a coluna (secção 5).

---

## 2. Arquivos

| Arquivo | Responsabilidade |
|---|---|
| `FloraPalette` | As 15 paletas e **todas** as listas de plantas (peso, densidade, grupo, regra ambiental). Único lugar que nomeia blocos. |
| `FloraTreeSpec` | Interface-costura da Fase 3 para árvores. Nada implementa hoje. |
| `FloraNoise` | Ruído 2D determinístico, sem estado e sem alocação. |
| `FloraRegionResolver` | A escada de prioridades: quem controla este terreno. |
| `FloraChunkContext` | Contexto resolvido **uma vez por tarefa**; depois só aritmética. |
| `FloraChunkSavedData` | Persistência esparsa por chunk + marcas de campo de batalha e Caos. |
| `FloraExclusionZones` | Onde a vegetação não pode nascer (geometria da cidade). |
| `FloraPlacementRules` | Regras e colocação por bloco; remoção limitada à tag. |
| `FloraChunkDecorator` | O trabalho de um chunk, com semente determinística. |
| `FloraChunkQueue` | Fila, orçamento por tick e estatísticas. |
| `FloraTransitionManager` | Mudança de controle territorial → chunks sujos. |
| `FloraEvents` | Chunk load, server tick, unload, morte. |
| `FloraCommands` | `/firstcrusade flora …` |

---

## 3. Como a paleta do chunk é resolvida

`FloraRegionResolver` recolhe *claims* de todos os sistemas territoriais que o mod já tem e
empilha por prioridade (**menor número vence**):

| # | Fonte | Paleta |
|---|---|---|
| 1 | Interior de estrutura especial | **exclusão**, não paleta — ver nota abaixo |
| 2 | Distrito de Hive City (`HiveCityMarkerData`) + faixa de altura | `HIVE_UPPER` / `HIVE_INDUSTRIAL` / `UNDERHIVE` |
| 3 | Assentamento imperial (`WorldWarMapData.CityInfo` → `ImperialCityType`) | `FORGE` / `AGRI` / `IMPERIAL_MEMORIAL` / `DEATH_WORLD` / `HIVE_UPPER` / `IMPERIAL` |
| 4 | Acampamento Ork (raio = halo de corrupção real) | `ORK` |
| 5 | Corrupção do Caos | `CHAOS` |
| 6 | Campo de batalha (com idade) | `BATTLEFIELD_FRESH` → `BATTLEFIELD_OLD` |
| 7 | Território da facção (halo largo do assentamento) | `IMPERIAL` / `ORK` |
| 8 | Neutro, a partir do bioma vanilla | `NEUTRAL_DARK` / `DEATH_WORLD` / `BURNT` |

**Nota sobre o degrau 1.** A única geometria de estrutura que o projeto registra é
`CityStructureFootprint`, e ela descreve **construções** — terreno que precisa ficar limpo, não
terreno que quer plantas próprias. Por isso o degrau 1 é honrado como *exclusão* em
`FloraExclusionZones`, não como paleta. Quando uma fase futura criar uma estrutura que realmente
queira vegetação própria (ruína tomada pelo mato, jardim de santuário), ela entra acima do degrau 2
sem mexer em nada abaixo.

**Nada disso carrega chunk.** Todas as fontes são dados persistidos (mapa da guerra, marcadores da
Hive, registro de flora), então um assentamento em chunk descarregado continua projetando território
corretamente.

**Nada disso duplica facção.** A paleta de uma cidade vem do `ImperialCityType` da própria cidade; o
alcance de um acampamento é o raio de corrupção que o próprio acampamento cresceu. Nenhum enum novo
de cidade ou de facção foi inventado. As duas únicas coisas que este sistema guarda por conta própria
são as marcas que **nenhum outro sistema do mod rastreia**: campo de batalha e corrupção do Caos.

### Atributos territoriais em `WorldWarMapData`

`WorldWarMapData` já era o registro global de posições de cidades e acampamentos. Ele passou a
guardar também os poucos **atributos** que descrevem que tipo de território cada assentamento
projeta (`CityInfo`, `CampInfo`), publicados pelo **mesmo tick** que já chamava `recordCity` /
`recordCamp`. Escrever o mesmo valor duas vezes é gratuito e **não** marca o mapa como sujo.

`getTerritoryRevision()` conta toda mudança no quadro territorial. O decorador grava a revisão sob a
qual decorou cada chunk; quando o contador passa dela, o chunk é reavaliado.

---

## 4. SavedData

`FloraChunkSavedData` é ligado ao **level** consultado (não forçado à Overworld), o que permite a
Hive ter seu próprio registro.

**Esparso por construção**: um chunk só aparece quando é efetivamente decorado ou marcado. Chunk
nunca visitado custa zero byte. Chaves são `ChunkPos.toLong()`.

**O que é guardado por chunk** (quatro números pequenos):

- paleta aplicada (o `id()` numérico estável, não o nome);
- versão do decorador;
- revisão territorial aplicada;
- flags `DECORATED` / `INCOMPLETE` / `DIRTY` + transição pendente.

**O que deliberadamente não é guardado**: a posição de cada planta. Não é necessário, porque a
colocação é função pura de (seed do mundo, chunk, paleta, versão) — o decorador sempre consegue
re-derivar exatamente o que colocou.

**Forma em disco**: três arrays paralelos (`long[] Keys`, `int[] Packed`, `int[] Revisions`) em vez
de uma lista de compounds. Medido na prática: **542 chunks em 8.813 bytes ≈ 16 bytes/chunk**.

`FORMAT_VERSION` é verificado na carga; formato desconhecido é descartado em vez de mal interpretado
(o pior caso é redecorar, o que é idempotente). Todo mutador chama `setDirty()`.

**A fila não é persistida.** Ao recarregar, chunks marcados incompletos ou sujos voltam à fila quando
forem carregados.

---

## 5. Fronteiras sem linhas retas

O chunk decide **qual trabalho acontece**. Ele não decide **como o terreno fica**.

Cada coluna candidata chama `FloraChunkContext.paletteAt(x, z, y)`, que:

1. deforma o ponto de amostragem com `FloraNoise.warp` em **coordenadas do mundo**;
2. testa os claims (círculos e quadrados em torno de posições de assentamento);
3. devolve o de maior prioridade.

Como os claims são geometria contínua em torno de posições lidas de dados persistidos, a matemática
simplesmente continua além da borda do chunk. **Nenhum chunk vizinho é carregado ou consultado.** Um
chunk decorado hoje e o vizinho decorado semana que vem encaixam sem costura, porque ambos avaliam a
mesma função contínua.

A densidade (`densityAt`) multiplica: caráter da paleta × config × ruído de duas oitavas (`patches`)
× atenuação na borda do claim. O termo de ruído é o que cria clareiras e moitas; sem ele a região
seria um tapete uniforme.

---

## 6. Fila e orçamentos

Dois estágios, de propósito:

- **`offer()`** (do `ChunkEvent.Load`) faz o mínimo absoluto: joga a coordenada numa fila
  concorrente e retorna. Carregamento de chunk nem sempre é na thread do servidor e acontece em
  rajadas.
- **`tick()`** (do `ServerTickEvent`, fase END) é onde tudo que toca dados do mundo acontece.

Tetos por tick, todos configuráveis:

| Config | Padrão | Efeito |
|---|---|---|
| `runtime.chunksProcessedPerTick` | 2 | Chunks decorados por tick |
| `runtime.placementAttemptsPerTick` | 512 | Teto global de tentativas por tick |
| `runtime.maximumCustomFloraPerChunk` | 220 | Blocos por passagem |
| `runtime.maximumLichenPerChunk` | 28 | Líquens por chunk |
| `runtime.maximumTallPlantsPerChunk` | 34 | Plantas altas (2 escritas cada) |
| `runtime.borderBlendWidth` | 12 | Largura da mistura entre paletas |
| `runtime.queueCapacity` | 4096 | Teto da fila |
| `runtime.structureMargin` | 2 | Folga extra em portas/estradas/trilhos |
| `runtime.chunkDecorationEnabled` | true | Chave mestra |
| `runtime.dynamicRedecorationEnabled` | true | Redecoração ao mudar de dono |
| `runtime.neutralChunkDecorationEnabled` | true | Decorar terreno de ninguém |
| `runtime.settlementVegetationCleanupEnabled` | true | Limpeza vanilla na fundação |

`placementAttemptsPerTick` é o que importa quando cinquenta chunks carregam de uma vez: a fila
simplesmente drena nos ticks seguintes. Um chunk que fica sem orçamento no meio é marcado
`INCOMPLETE` e recolocado — e como a colocação é determinística, recomeçar reproduz as mesmas
plantas em vez de somar novas.

Consumo real das densidades já existentes: `vegetationDensity` e `hiveDecorationDensity` entram na
densidade global do contexto; `smallPlantDensity` escala o número de tentativas;
`orkFungusFrequency` escala a densidade dos claims Ork.

---

## 7. Determinismo e não-acúmulo

A semente vem de `FloraChunkDecorator.placementSeed(floraSeed, chunkPos, palette)`, misturando seed
do mundo, X e Z do chunk, id da paleta e `DECORATOR_VERSION`. **Nunca** `level.random`.

Consequências:

- O mesmo chunk, mesma paleta, mesma versão ⇒ **exatamente** as mesmas posições.
- Redecorar com a mesma paleta reescreve as mesmas plantas nos mesmos blocos ⇒ **não acumula**.
- Uma passagem interrompida pode recomeçar do zero sem persistir progresso.
- Quando a paleta **muda**, a flora antiga é removida primeiro — e a remoção é limitada à tag
  `firstcrusade:flora`. Construções, blocos do jogador, plantações vanilla e árvores plantadas pelo
  jogador são invisíveis para a remoção.

---

## 8. Transformações após mudança de facção

`FloraTransitionManager` quase não faz nada na hora: descobre os chunks afetados, marca **só esses**
em `FloraChunkSavedData` e enfileira **apenas os que já estão carregados**. Os demais transformam
quando forem carregados. A transformação em si é distribuída pelo orçamento da fila — nenhum chunk
inteiro é convertido num tick.

Pontos de integração (nos caminhos que já existiam):

| Gatilho | Chamada |
|---|---|
| `CityArchitect.buildFoundingSettlement` | `onTerritoryCaptured(centro, WALL_RADIUS + 48)` |
| `ImperialCommandCoreBlockEntity.buildCityStructure` (expansão) | `onTerritoryCaptured(centro, raio + 48)` |
| `OrkCampManager.buildCampStructure` / `fortifyCamp` | `onTerritoryCaptured(centro, r + 48)` |
| `StrategicWarAIManager.captureCityForOrks` | `onTerritoryCaptured(cidade, 160)` |
| `FloraEvents.onLivingDeath` | `markBattlefield(...)` |

Exemplos de resultado:

- **Imperial → Ork**: sai grama imperial / flor memorial / cardo, entram `trampled_grass`,
  `ork_fungus`, `ork_spore_cap`, `gob_moss`, `oil_stained_grass`.
- **Agri → queimado**: `BURNT` — quase só `burnt_stubble` e `ash_layer`.
- **Ork → Imperial recuperando**: `RECOVERING` — fino de propósito: `withered_scrub`,
  `imperial_grass`, `memorial_bloom` ocasional.
- **Campo de batalha**: `BATTLEFIELD_FRESH` por 3 dias de jogo, `BATTLEFIELD_OLD` por mais 12 (menos
  cinza, grama escura voltando, flor memorial muito rara), depois o território retoma o terreno.
  Sem tick, sem tarefa agendada — só um timestamp e uma comparação.

Uma batalha longa marca o chunk uma vez a cada 600 ticks, não uma vez por baixa.

---

## 9. Proteção de cidades

`FloraExclusionZones` monta, **uma vez por tarefa**, os retângulos proibidos a partir do
`CityLayoutPlan` da própria cidade — o mesmo registro que o `CityPlacementValidator` usa para decidir
onde uma construção pode ir, de modo que vegetação e arquitetura concordam sobre o que é a cidade:

- toda `CityStructureFootprint` + margem própria + `structureMargin`;
- a porta de cada construção e o terreno à frente dela;
- portões (folga maior), torres;
- praça e avenidas (geométricas — precisam ser perguntadas, ou uma rua vira grama);
- faixa da muralha;
- canteiros de obra (`StrategicConstructionProject.getSitePos`);
- veículos estacionados (**uma** consulta de entidades por chunk, nunca por planta).

Regras por bloco ficam em `FloraPlacementRules`: trilhos, placas de pressão, camas, portas, botões,
alavancas, blocos com block entity (máquinas), e **farmland nunca** — terra arada quase sempre é
campo de jogador esperando semeadura.

---

## 10. Comandos

| Comando | Permissão | Efeito |
|---|---|---|
| `/firstcrusade flora inspect` | todos | Chunk, paleta atual, paleta aplicada, versão, revisão, flags, claims, fila |
| `/firstcrusade flora stats` | todos | Enfileirados, processados, ignorados, colocados, removidos, falhas |
| `/firstcrusade flora decorate` | op (2) | Enfileira o chunk atual |
| `/firstcrusade flora redecorate` | op (2) | Marca e redecora o chunk atual |
| `/firstcrusade flora redecorate radius <0-16>` | op (2) | Marca uma área limitada |
| `/firstcrusade flora clearcustom` | op (2) | Remove **só** blocos da tag `firstcrusade:flora` |
| `/firstcrusade flora cleartrees [radius <0-16>]` | op (2) | Remove **só** blocos da tag `firstcrusade:flora_tree` e esquece os chunks, que replantam do zero |
| `/firstcrusade flora transition <burn\|recover\|chaos\|uncorrupt> [chunks]` | op (2) | Aplica à mão uma transformação da guerra |

`clearcustom` e `cleartrees` são separados de propósito, e é uma diferença de promessa: o primeiro varre
grama e detalhe, com que ninguém constrói; o segundo remove troncos e copas — blocos sólidos que um
jogador pode ter usado numa construção — e por isso nunca faz parte da redecoração comum, só acontece
quando pedido pelo nome.

`transition chaos` é hoje o **único** caminho para a paleta CHAOS: o mod ainda não tem facção do Caos,
então nada no jogo a produz sozinho.

Nada é transmitido para todos os jogadores; a saída vai só para quem chamou.

---

## 11. Árvores (Fase 3)

Seis espécies, cada uma um tronco e (quase sempre) uma copa. Registradas em
`flora/tree/FCFloraTrees.java`, desenhadas por `flora/tree/FCTree.java`, e a tabela de qual região
planta o quê está em `flora/tree/FCTrees.java`.

| Espécie | Blocos | Forma | Paletas |
|---|---|---|---|
| Pinheiro imperial | `imperial_pine_log` + `imperial_pine_leaves` | conífera | IMPERIAL, IMPERIAL_MEMORIAL, NEUTRAL_DARK, RECOVERING, BATTLEFIELD_OLD |
| Tronco morto de cinzas | `ash_snag_log` | tronco morto, **sem copa** | FORGE |
| Torre fúngica Ork | `ork_fungal_stalk` + `ork_fungal_cap` | chapéu chato | ORK |
| Ramo venenoso | `venom_bough_log` + `venom_bough_leaves` | copa redonda larga | DEATH_WORLD |
| Pomar | `orchard_log` + `orchard_leaves` | copa pequena | AGRI |
| Ramo deturpado | `warped_bough_log` + `warped_bough_leaves` | copa redonda | CHAOS |

**Sem árvore, de propósito:** `HIVE_UPPER`, `HIVE_INDUSTRIAL`, `UNDERHIVE` (não há céu),
`BATTLEFIELD_FRESH` e `BURNT` (o terreno acabou de ser arrasado/queimado). `treeEntry()` devolver
`null` é uma resposta suportada, não uma lacuna.

Não há mudas nem crescimento: estas árvores são colocadas pelo decorador como parte da identidade de
uma região. Uma muda implicaria um sistema de plantio que esta fase não tem.

### O plano como valor (`FloraTreePlan`)

`FCTree.plan(x, z, random)` devolve os blocos de uma árvore **sem olhar o mundo uma vez sequer**.
Y é relativo à base do tronco, então o mesmo plano cai em qualquer altura de terreno. Isso não é
elegância gratuita — três propriedades dependem disso:

1. **O gerador avança igual sempre.** Uma árvore recusada por falta de altura consome exatamente a
   mesma aleatoriedade de uma plantada, então uma recusa não desloca todas as árvores seguintes.
2. **Uma passagem posterior consegue reconstituir a anterior.** É assim que a região conquistada
   perde as árvores do dono antigo (abaixo).
3. **Replantar é no-op.** O mesmo chunk decorado duas vezes planeja as mesmas árvores, encontra os
   próprios troncos no caminho e não acrescenta nada.

> **Bug real encontrado no teste.** Na primeira versão, o passo de árvores compartilhava o
> `RandomSource` do laço de plantas — que roda um número **variável** de vezes, porque um chunk que
> estoura o orçamento para no meio e retoma depois. O gerador chegava ao passo de árvores em estado
> diferente a cada passagem, as posições saíam outras, e o resultado era uma segunda floresta ao
> lado da primeira. Medido: 3.343 → 4.833 troncos num reinício que só gerou 21 chunks novos.
> Corrigido com semente própria (`TREE_SEED_SALT`) e com o plano independente do mundo.

### Como uma conquista remove as árvores do dono anterior

A limpeza comum é limitada à tag `firstcrusade:flora`, e árvore é `minecraft:logs` +
`minecraft:leaves` — ou seja, invisível para ela. Sem tratamento, um pinheiro imperial continuaria de
pé no meio das torres fúngicas Ork.

`FloraChunkDecorator.clearPreviousTrees` resolve isso **reproduzindo a semente da passagem anterior**
(paleta anterior + versão anterior, ambas guardadas no SavedData). O plano resultante é exatamente o
que aquela passagem plantou, e a remoção é duplamente específica: só posições que o plano cobre, e só
quando o bloco ali é mesmo daquela espécie. A cabana que o jogador construiu com os mesmos troncos
três blocos ao lado não corre risco, porque aquelas posições não estão no plano.

### Outros dois detalhes

**Contenção no chunk.** Uma copa tem vários blocos de largura, então o decorador **recua** as
posições candidatas em `canopyRadius()` a partir da borda do chunk. Uma árvore cuja copa cruzaria a
borda simplesmente não é plantada ali — escrever lá carregaria o chunk vizinho, a única coisa que o
decorador nunca pode fazer. Árvores são esparsas o bastante para a faixa perdida ser invisível; uma
copa cortada reta na linha do chunk não seria.

**As folhas não apodrecem.** A decoração escreve com `UPDATE_CLIENTS` e não dispara atualização de
vizinhos, então a propagação de distância das folhas do vanilla nunca roda. Folhas na distância
padrão (7) desapareceriam no primeiro random tick. Por isso a distância é calculada aqui, por uma
busca em largura saindo do tronco através da copa planejada — a mesma regra a que o `LeavesBlock`
chegaria, só que antecipada. Folha a mais de 6 passos da madeira não é colocada.

**Versão do decorador.** Está em `3`. A versão entra na semente de colocação, o que significa que o
layout antigo deixa de ser reproduzível — e por isso a fila trata *mudança de versão* como motivo
para limpar antes de redecorar, exatamente como trata mudança de paleta. Sem isso, uma atualização do
decorador dobraria silenciosamente a vegetação de todo chunk já salvo.

---

## 12. Texturas

As 56 texturas de planta e as 17 de árvore são geradas por script, não desenhadas à mão:

- `tools/generate_flora_textures.py` — plantas (`--sheet` gera folha de contato)
- `tools/generate_tree_assets.py` — árvores: texturas **e** blockstates/modelos/loot/lang
- `tools/preview_flora_field.py` — prévia de como uma paleta fica no chão

As primeiras texturas da Fase 2 tinham 2–3 cores e lâminas retas de largura constante, o que no jogo
aparece como barras sólidas. As atuais usam rampa de 5 tons, lâminas em arco que afinam para 1px na
ponta e variação de tom por lâmina (média de 5,1 cores por textura). Regeneração é determinística
(a semente vem do nome do arquivo), então rodar de novo dá diff limpo.

As tags **não** saem desses scripts: `minecraft:logs`, `minecraft:leaves` e `mineable/*` pertencem ao
datagen (`FCBlockTagsProvider`), que já é dono desses arquivos em `src/generated`.

---

## 13. Fase C — os quatro biomas novos

Quatro biomas naturais entraram inteiramente pela **camada de worldgen**: quando o chunk nasce
já tem solo próprio, capim, árvore e (na marsh) água. Nada aqui depende da fila em runtime.

| Bioma | Clima | Solo | Árvores | Identidade |
|---|---|---|---|---|
| `ironwood_forest` | frio, úmido (T 0.35 / D 0.8) | podzol | Ferrofuste, Ferrofuste Antigo, Ferrofuste Resinoso | a única mata com dossel fechado de verdade |
| `sump_marsh` | quente, encharcado (0.8 / 0.9) | `sump_mud` + poças | Mangue de Sump, Salgueiro Tóxico, Mangue Podre | água preta, gás, névoa baixa pelo fog |
| `ossuary_tundra` | congelado (0.0 / 0.5) | grama + neve + gelo | Pinheiro Gelanoz, Morto Congelado | osso e líquen sobre guerras antigas |
| `salt_waste` | tórrido, seco (1.6 / 0.0) | `salt_crust` sobre areia | Espinho de Sal, Tronco Fossilizado | o bioma mais vazio do mod, de propósito |

**Uma espécie, várias features.** Ferrofuste comum, antigo e resinoso compartilham
`ironwood_leaves`; o mangue vivo e o mangue podre compartilham `sump_mangrove_log`. O que muda é
a silhueta, não a paleta — é isso que faz um bosque ler como um bosque só em vez de três.

### Solo é material, não tintura

Cor de grama é propriedade de bioma; **material não é**. Um ermo de sal cujo chão continua sendo
`grass_block` lê como campina desbotada por mais pálida que seja a tinta. Por isso existem dois
blocos de solo (`FCFloraGround`: `sump_mud`, `salt_crust`) e três regras de superfície por bioma,
inseridas no `surface_rule` do `noise_settings/overworld.json`. Os dois entram em
`minecraft:dirt`, e como `firstcrusade:flora_ground_natural` inclui `#minecraft:dirt`, as plantas
da região pegam neles sem nenhuma tag nova. Sem isso o bioma nasceria pelado: todo
`would_survive` falharia.

A tundra **não** tem regra própria: `grass_block` + `freeze_top_layer` já dá neve, e trocar o topo
por `snow_block` impediria qualquer planta de pegar.

### Distribuição

O `multi_noise` passou de 7 para 15 entradas, organizadas como uma matriz clima → bioma
(5 faixas de temperatura × 3 de umidade) em `CLIMATE_MATRIX`. As fronteiras subdividem
exatamente os limites que a versão de quatro biomas já provou alcançáveis (-0.15 / 0.05 / 0.25);
nenhuma faixa foi inventada fora do intervalo medido.

### Um dono por arquivo — e o bug que isso custou

`generate_overworld_biomes.py` escrevia a lista de `features` inteira do bioma, e
`generate_worldgen_features.py` preenchia as etapas 1 (lakes) e 9 (vegetal_decoration) depois.
Rodar os dois na ordem errada apagava **toda a vegetação de todos os biomas**, e o sintoma é
cruel: o mundo gera, os biomas aparecem, as cores estão certas, o solo está certo, e não existe
uma planta em lugar nenhum. Custou um mundo de teste inteiro para localizar. Hoje
`merge_features()` preserva as etapas de outro dono, e os dois scripts rodam em qualquer ordem.

O mesmo princípio corrigiu um segundo problema no gerador de features: um *placed feature*
carrega a **contagem**, então dois biomas que querem a mesma planta em densidades diferentes
precisam de dois arquivos. Antes o nome era o da configured feature, o arquivo era reescrito uma
vez por bioma e o último a escrever ganhava — na prática todos os biomas herdavam a densidade de
um só. Agora o nome leva sufixo (`patch_withered_scrub_n2`, `_n9`) quando existe mais de uma
variante, e o script remove órfãos do diretório que ele mesmo possui.

### Medido em servidor dedicado

Mundo novo, 3.411 chunks completos amostrados em 81 pontos num raio de 4.800 blocos:

| Bioma | chunks | % | conferido |
|---|---|---|---|
| `pale_steppe` | 647 | 19,0% | — |
| `dark_wilds` | 565 | 16,6% | — |
| `ash_waste` | 533 | 15,6% | — |
| `ossuary_tundra` | 522 | 15,3% | neve em 1.083, gelo em 210 |
| `ironwood_forest` | 420 | 12,3% | podzol em 421, 2.264 troncos |
| `death_jungle` | 359 | 10,5% | — |
| `sump_marsh` | 197 | 5,8% | `sump_mud` em 200, água em 65 |
| `salt_waste` | 168 | 4,9% | `salt_crust` em 168, 86 espinhos |

Todo bioma nasce com a vegetação da sua paleta — a contagem de entradas de palette por planta
bate com o número de chunks do bioma, ou seja **não existe chunk pelado esperando fila**.

E o número que fecha a arquitetura híbrida: `firstcrusade flora stats` reportou **`placed: 0
blocks`** depois de 404 chunks processados pela fila. O decorador em runtime viu cada chunk,
reconheceu a paleta como natural e não colocou nada — as duas camadas não se somam.

---

## 14. Dois defeitos que só a medição encontrou

Depois da Fase C o dono relatou três coisas em jogo: **chão todo cavucado**, **bioma fechado
embaixo** e **árvores voando sem folhas**. As três vinham de dois bugs, e vale registrar o método:
cada um foi localizado medindo o mundo salvo (leitor de região próprio, ver
`docs`/memória), não olhando o código.

### 14.1 As manchas de planta escavavam o chão

`SimpleBlockFeature` **não verifica se a posição de destino está vazia** — ele só chama
`canSurvive`. O filtro interno das nossas manchas era `would_survive`, que pergunta apenas se o
bloco de baixo serve. Com `y_spread: 3`, o `random_patch` sorteia posições **dentro do terreno**,
onde a terra de baixo serve, e a feature **trocava um bloco de chão por uma planta**. Cada planta
abria uma cova de um bloco; com dezenas de tentativas por chunk, o chão virava um xadrez de covas.

O vanilla usa `matching_blocks: minecraft:air` em `patch_grass` — o alvo tem de estar vazio. Nosso
`plant_filter()` agora usa `all_of[air, would_survive]`: `air` impede a escavação, `would_survive`
impede planta em chão que não a sustenta (as regras de solo do mod são mais estreitas que as do
vanilla).

Medido em 20.480 colunas, antes → depois:

| | antes | depois |
|---|---|---|
| bloco de sub-superfície exposto no topo | 47,9% | **4,1%** (resto = veio de minério e pico de rocha) |
| degrau de altura entre colunas vizinhas | 62–70% | **5%** |
| `dirt` cru no topo | 8.317 colunas | **10 colunas** |

Atingia **todos** os biomas, inclusive os quatro antigos, porque todos passam pelo mesmo helper.

> **Hipótese descartada, registrada de propósito.** Antes disso suspeitei dos carvers: o
> `minecraft:cave` sorteia o centro em `uniform(above_bottom 8, absolute 180)`, e num mundo
> `min_y=0` com a densidade cruzando zero em y=54 sobram ~45 blocos de rocha para o volume que o
> vanilla espalha por ~128. Cheguei a escrever carvers próprios com o teto abaixo da superfície; o
> número medido não mudou, e a mudança foi revertida — cortar o teto das cavernas custaria toda
> entrada de caverna na superfície em troca de nenhum ganho.

### 14.2 As copas desciam abaixo da base do tronco

Medido antes: a folha mais baixa perto de um tronco ficava numa **mediana de −3 blocos** na
dark_wilds (mínimo −7) e −1 no ferrofuste. Folha dentro do chão, nenhuma passagem. Duas causas:

* **`fancy_trunk_placer` nas árvores comuns.** Ele ramifica a partir de ~0,618 da altura e pendura
  folhagem em cada galho, o que fecha o sub-bosque de um jeito que nenhum ajuste de offset resolve.
  Passou a ser reservado aos exemplares raros e altos (`ironwood_ancient`); as comuns usam
  `straight`/`forking`, cuja folha mais baixa é aritmética: `altura + offset − altura_da_folhagem`.
* **`FCTree` (runtime) pendurava a copa redonda do topo do tronco** sem exigir tronco alto. Agora
  vale a invariante `minHeight >= 2 * canopyRadius + MIN_CLEARANCE`, e a copa é truncada por baixo
  em vez de ser levantada — levantar abriria vão entre copa e tronco.

**Raio máximo 3.** Uma folha apodrece quando nenhum tronco está a 6 passos de Manhattan; numa copa
de raio 4 sobre tronco único a quina dá 7. Só `ironwood_ancient` vai a 4, porque `fancy` põe galho
dentro da copa.

Medido depois, por coluna de terreno (a pergunta do jogador é "consigo andar aqui?"):

| bioma | colunas com folha em y+1..3 | folha órfã | copa mais baixa (mediana) |
|---|---|---|---|
| `dark_wilds` | 4,7% | 0,01% | 9 |
| `ironwood_forest` | 2,5% | 0,13% | 7 |
| `death_jungle` | 0,6% | 0,17% | 12 |
| `sump_marsh` | 0,0% | 0,07% | 11 |
| `pale_steppe` | 0,1% | 0,00% | 8 |
| `ossuary_tundra` | 0,0% | 0,00% | 7 |
| `ash_waste` / `salt_waste` | 0,0% | 0,00% | — (só tronco morto) |

Os 4,7% da dark_wilds são o sub-bosque de `scrub_oak`, que é proposital: alguns arbustos, não uma
parede.

> **Duas métricas erradas no caminho, porque medir mal é pior que não medir.** A primeira contava
> folha órfã dentro de **um** chunk, então toda copa que cruzava a borda contava como órfã —
> inflava de 0,1% para 6–8%. A segunda media a altura livre **por árvore**, pegando a copa do
> vizinho de cima numa encosta. As duas versões corrigidas estão descritas acima.

---

## 15. "É tudo cinzas até eu passar pelo lugar"

Relato do dono, com duas causas somadas — uma de distribuição, outra de arquitetura.

### 15.1 Um bioma por horizonte

A distribuição particionava só **temperatura × umidade**, deixando `weirdness`, `erosion` e
`continentalness` em faixa cheia. Mas temperatura e umidade são ruídos de **onda muito longa** — é
para isso que servem, dar clima coerente por milhares de blocos. Particionar só por eles significa
que *uma região de temperatura constante é uma região de bioma constante*: o dono voou sobre um
deserto de cinzas que ia até a borda da tela em todas as direções, e estava certo.

`weirdness` (o `ridges` do vanilla) tem onda muito mais curta, e é exatamente assim que o vanilla
quebra suas regiões. Cada célula de clima passou a render **dois** biomas alternados por faixa de
weirdness, sempre um par ecologicamente vizinho para a troca ler como transição. O `multi_noise`
foi de 15 para 30 entradas.

Medido em 12.659 chunks, dois transectos de 9.400 blocos cruzando a origem:

| | antes | depois |
|---|---|---|
| faixa de bioma único (mediana) | horizonte inteiro | **96 blocos** |
| média | — | 120 blocos |
| p90 | — | 224 blocos |

### 15.2 As faixas de clima não estavam nos percentis reais

Nem temperatura nem umidade deste mundo são simétricas em torno de zero, e a partição precisou de
**três iterações medidas** para parar de pé:

| cortes de temperatura | cinzas | tundra | ferrofuste | selva |
|---|---|---|---|---|
| −0,22 / −0,08 / 0,06 / 0,22 | 32,6% | 7,1% | 4,8% | 12,4% |
| −0,16 / −0,02 / 0,16 / 0,30 | 22,9% | 24,4% | 21,1% | 2,1% |
| **−0,19 / −0,05 / 0,11 / 0,26** | **11,2%** | **21,8%** | **14,0%** | **3,3%** |

Deslocar as fronteiras em 0,08 virou 17 pontos de área: o ruído é concentrado perto de zero, então
a fronteira é muito sensível. A umidade tem o mesmo viés (a banda úmida desceu de 0,10 para 0,02,
senão selva e pântano ficavam em 2% cada).

Distribuição final: estepe 25,0 · tundra 21,8 · ferrofuste 14,0 · mata escura 13,5 · cinzas 11,2 ·
sal 5,8 · pântano 5,3 · selva 3,3.

### 15.3 O verde que aparecia atrás do jogador

A outra metade do relato — manchas verdes surgindo onde ele passava — não era distribuição, era o
**halo territorial**. Um assentamento projetava `settlementRadius * 4 + 64`, ou seja **~320 blocos
de raio**, vestidos com a paleta `IMPERIAL` **completa, árvores incluídas**. Dois estragos, ambos
visíveis do ar: um pinheiral imperial crescia no meio de um deserto de cinzas, apagando o bioma que
a worldgen acabara de construir; e como a fila em runtime pinta chunk a chunk, o verde chegava
**atrás do jogador**.

Duas correções, e a segunda é a que vale como regra:

* O halo virou `settlementRadius + 40`, e o do acampamento Ork `campRadius + 40` (era `× 3 + 32`).
* O halo passou a usar paletas próprias — `IMPERIAL_FRINGE` e `ORK_FRINGE` — **sem árvore nenhuma**
  e com densidade baixa.

> **A regra:** a camada em runtime pode **acentuar** a paisagem, nunca **substituí-la**. O que
> substitui pertence à worldgen, onde existe no instante em que o chunk existe. Um halo pequeno e
> esparso torna a lentidão da fila invisível — que é a única forma honesta de uma camada em runtime
> se comportar.

### 15.4 Isto exige mundo novo

O `biome_source` é serializado no `level.dat` **na criação do mundo**. Um save existente continua
com a distribuição antiga por mais que o datapack mude; só o conteúdo dos biomas (features) vale
para chunks ainda não gerados. Para ver 15.1 e 15.2 é preciso criar um mundo novo.

---

## 16. Fase D — árvores frutíferas

Cinco frutas, cinco árvores, e **três madeiras novas** — as outras duas emprestam o tronco de
espécies que já crescem onde a fruta delas cresce. Uma silhueta nova não merece uma espécie nova.

| fruta | árvore | onde | função |
|---|---|---|---|
| `ration_fruit` | Fruta-Ração (madeira nova) | estepe 1/3, mata escura 1/8 | ração de campo: nutrição 4, sem efeito |
| `grox_feed_pod` | Vagem de Ração (tronco do pomar) | estepe 1/6 | ração **animal** — comível, e desagradável de propósito |
| `lumenfruit` | Fruta-Lúmen (madeira nova) | pântano 1/3 | brilho 20 s + visão noturna ocasional; a copa já é luminosa |
| `frostnut` | Pinheiro Gelanoz **carregado** | tundra 1/4 | saturação alta, consumo rápido: sobrevivência no frio |
| `venom_pear` | Ramo Venenoso, copa própria | selva mortal 1/3 | Veneno II — o item existe para ser ingrediente |

### O nó de fruto

`FruitNodeBlock`: quatro estágios de `AGE`, pendurado na **face de baixo** da copa, colhido com
botão direito. Colher devolve o nó ao estágio 0 em vez de removê-lo — é a diferença entre um pomar
e uma colheita única.

**Por que nó e não folha que dropa fruta.** A alternativa óbvia — toda folha às vezes dropa — é a
que o escopo proíbe, e com razão: torna a colheita função do *volume da copa* (árvore grande
inunda o jogador, pequena não dá nada), põe random tick com carga útil em **toda** folha do mundo,
e não dá nada para o jogador olhar, porque a árvore carregada fica igual a qualquer outra.

O nó é o oposto nos três pontos. São 1 a 4 por árvore, colocados na geração pelo decorador
`minecraft:attached_to_leaves` — o mesmo que o vanilla usa para o propágulo de mangue —, então a
produção é propriedade da **árvore** e é visível de fora dela.

Esse decorador resolve sozinho as quatro exigências do escopo, sem contar nada em código: só em
folha (nunca no tronco), só virado para baixo (nunca fruto flutuando), `required_empty_blocks`
garante espaço livre, e o raio de exclusão limita o total. Medido em 3.000 chunks, 743 nós:

| exigência | medido |
|---|---|
| apoiado em folha | **100%** |
| flutuando | **0%** |
| espaço livre embaixo | 99,1% |
| nós por árvore | mediana **2**, média 2,4 |

**Sem block entity e sem tick agendado.** Crescimento é random tick — o mesmo orçamento do trigo —
e é barato porque há poucos nós: um chunk de floresta tem dezenas deles, não os milhares de blocos
de folha que a outra abordagem exigiria. O nó checa suporte do jeito comum, então derrubar a árvore
faz a fruta cair em vez de ficar no ar.

### Configurações

Duas, e só duas: `fruitRegrowthEnabled` e `fruitRegrowthChance`. As outras três que o escopo lista
(`maximumFruitNodesPerTree`, `fruitTreeFrequency`, `wildFruitTreeFrequency`) são decididas **na
geração do chunk**, pelo decorador e pela lista de features do bioma. Um config não as move sem
regerar o mundo, então declará-las aqui seria um botão que não faz nada. Elas vivem no datapack,
onde são honestas.

---

## 17. Planetas — o mod sai do overworld

Mudança de arquitetura, e a maior desde o começo: **o mod não sobrescreve mais o overworld.**

O jogador começa num Minecraft normal — com oceano, estrutura, aldeia e Nether do jogo base — e a
Cruzada começa quando ele usa a nave. Tudo que o mod gera vive nas dimensões dele, onde não colide
com nada.

Isso resolveu de uma vez um conjunto de sintomas que vinham desde o commit `7dcc271`: oceano
flutuante, naufrágio de ponta-cabeça e `sea_level` descasado. Todos eram a mesma coisa — estrutura
vanilla posicionada em relação a um nível do mar que o gerador sequestrado não tinha mais. E, de
brinde, um save feito antes de instalar o mod deixa de ser alterado.

**Consequência de código:** nenhum sistema pode mais se ancorar em `Level.OVERWORLD`. "O mundo onde
a guerra acontece" virou um conjunto, e esse conjunto é `FCPlanets`. Três sistemas mudaram de alvo:
a borda de mundo (era o overworld — encolher o mundo vanilla do jogador seria o mod estragando um
save que não lhe pertence), a IA estratégica de guerra e o decorador de flora.

O Nether e o End deixaram de ser selados por padrão. Enquanto o mod era dono do overworld, fechar
os dois era coerente; um Minecraft normal sem Nether não é normal. A config continua lá para quem
quiser o mundo fechado.

### Os quatro planetas

Cada um declara a sua composição, e **só** os biomas dessa lista existem naquele mundo. As escolhas
seguem a lore:

| planeta | composição pedida | medida em jogo | lore |
|---|---|---|---|
| **Macragge** | rochoso 60 / estepe 25 / ferrofuste 15 | **59,1 / 22,1 / 18,8** | +75% da massa de terra é montanha rochosa quase sem vida; a população vive nas terras baixas |
| **Armageddon** | cinzas 55 / sal 30 / vulcânico 15 | **53,7 / 27,5 / 18,9** | ermos poluídos que matam em um dia; as Sreya Rock Mountains são vulcânicas ativas |
| **Catachan** | selva 75 / pântano 25 | **75,9 / 24,1** | mundo-morte quase inteiramente coberto de selva densa |
| **Valhalla** | tundra 70 / ferrofuste 30 | **63,5 / 36,5** | bola de gelo; a temperatura nunca sobe de zero |

Dois biomas novos (`rocky_highland`, `volcanic_highland`) e **nenhum bloco novo** — são paleta,
regra de superfície e uma lista de features montadas com o kit que já existia.

A nave (`SpaceportBlock`) ganhou escolha de destino: agachar + clicar troca de planeta, clicar
lança. Sair de um planeta sempre volta para casa.

### Duas coisas que custaram medição

**As 16 células de clima não têm a mesma área.** Distribuir células por *contagem* deu Catachan com
56% de pântano onde a composição pedia 25%. As áreas reais foram medidas com dois planetas
descartáveis (`--calibrate`) que pintam um bioma por faixa; a alocação passou a escolher o corte
contíguo de menor erro **de área**, por busca exaustiva (16 células, ≤4 biomas — o ótimo exato sai
de graça).

> A primeira calibração cobriu ±2.400 blocos e deu números completamente diferentes da segunda:
> o ruído de clima tem onda de milhares de blocos, e a amostra inteira caiu dentro de uma única
> região fria. **Medir clima num quadrado pequeno é medir o próprio quadrado.** A boa cobre
> 192.000 blocos de lado.

**O `xz_scale` do clima subiu de 0,25 (vanilla) para 0,75.** Aritmética, não gosto: um planeta tem
borda de 5.000 blocos, então o jogador só vê um quadrado de 10.000 de lado. Com a onda do vanilla
esse quadrado cabe **dentro** de uma única região de clima, e a composição prometida vira sorteio de
seed — uma semente dá um mundo todo frio, a seguinte um mundo todo quente. Com a onda mais curta o
quadrado contém dezenas de regiões e a composição converge para o declarado. Faixa de bioma único
medida: **mediana 128 blocos**.

---

## 18. Limitações conhecidas

1. **Caos** — a paleta, o degrau do resolver, a transição e a árvore existem e funcionam, mas
   **nenhum sistema do mod produz corrupção do Caos**; só o comando e a API a alcançam. É honesto: o
   mod ainda não tem facção do Caos.
2. **Hive multi-andar** — o decorador é baseado em superfície. Dentro da Hive ele resolve o distrito
   pelo XZ e a faixa de altura (`UNDERHIVE` / `HIVE_INDUSTRIAL` / `HIVE_UPPER`), mas não decora cada
   andar empilhado separadamente.
3. **Efeitos** — `particleFrequency`, `ambientSoundFrequency`, `ambienceQuality` e
   `dangerousFloraEnabled` continuam reservados para a Fase 5, conforme o escopo.
4. **Líquen em borda de chunk** — uma face de apoio que caia no chunk vizinho é ignorada em vez de
   ler o vizinho. Na prática o líquen cresce a partir do outro lado quando aquele chunk for decorado.
5. **Árvores não atravessam borda de chunk** — consequência da regra de contenção acima. Uma
   floresta fecha, mas nenhuma copa individual cruza a linha do chunk.
6. **Verificação em jogo** — validado em servidor dedicado. O comportamento visual dentro de uma
   cidade viva e a troca de facção em jogo real ainda não foram observados numa sessão de cliente.
