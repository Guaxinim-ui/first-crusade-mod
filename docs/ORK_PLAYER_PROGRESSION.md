# Progressão do jogador ORK — WAAAGH!

> Estado: **fases B, C e D entregues** (2026-08-11). Efeitos reais em todos os 38 nós, quatro
> habilidades ativas, HUD de Fúria, e a tela própria com cinco páginas e ícones. Build verde;
> a tela foi conferida em jogo (páginas, painel do nó, modal de Klan). Falta a fase E: atualizar
> `MOD_CONTEXT.md` e `STATUS.md`.
>
> **A árvore tem 38 nós**, não 34 — qualquer comentário que ainda diga 34 está errado.
> **A conversão de Dentu já existe**: é o botão "GUARDA OS DENTU" no Ork Camp.

## A filosofia, e por que ela não é a Imperial pintada de verde

O Imperial progride estudando: XP de progressão, Pontos de Doutrina, cirurgias, gene-seed. O Ork
não. Ele briga, pega dentu, fica grandão e manda nos outros. Se a progressão Ork fosse uma barra de
XP com skin verde, as duas facções contariam a mesma história com nomes diferentes.

Por isso **nenhum** conceito Imperial foi reaproveitado: não há Doctrine Points, não há cirurgia,
não há implante, não há Blood Trial. Há três valores próprios.

## Os três valores — e por que são três

| | O que é | Gasta? | Onde é lido |
|---|---|---|---|
| **Krumpagem** (`krumpScore`) | Reputação. "Quanto esse git já provou." | **Nunca** | Portões de evolução |
| **Dentu** (`teef`) | Moeda pessoal. A única coisa que a árvore custa. | Sim | Compra de nós |
| **Fúria** (`waaaghFury`) | Humor de combate, 0–100. Temporário. | Consumido pelo grito | Habilidade WAAAGH |

Krumpagem **não é XP**: não tem níveis, não é gasta, e só sobe. É a resposta honesta para "esse
jogador fez alguma coisa?".

## Os três WAAAGH que não podem se misturar

Esta é a armadilha mais fácil deste sistema. O mod tem **três** valores chamados WAAAGH:

- `WaaaghOverlordData.waaagh` — a **maré global**, que já existia. O portão de Warboss lê o tier
  dela, mas nunca escreve nela por golpe.
- `OrkCampBlockEntity.waaagh` — o humor **local de um acampamento**. Intocado.
- `PlayerOrkProgressionProfile.waaaghFury` — a **fúria pessoal em combate**. Nova.

A única defesa contra confundi-los é que **nunca compartilham uma classe**. Mesma regra para
`teef`: o `StrategicResourceType.TEEF` é o cofre de guerra da IA Ork e não tem relação com o bolso
do jogador.

## Fúria sem tick

A decadência **não é decrementada por tick**. O perfil guarda o par "quanto ele tinha quando levou o
último golpe, e quando foi isso"; `fury(gameTime)` calcula o resto por aritmética. Consequência: um
Ork parado com fúria caindo custa zero, e um mundo sem Ork em combate não roda código de fúria
nenhum.

## Estágios e tamanho

| Estágio | Largura | Altura | Referência |
|---|---|---|---|
| ORK_BOY | 0.70 | 2.05 | `OrkBoyEntity` |
| BIG_BOY | 0.76 | 2.12 | — |
| ORK_NOB | 0.85 | 2.25 | `OrkNobEntity` |
| BIG_NOB | 0.92 | 2.38 | acima do Meganob |
| WARBOSS | 1.00 | 2.60 | `WarbossEntity` |

O Warboss ultrapassa de propósito qualquer silhueta humana do mod — um Space Marine para em 2.35.
É o que faz a frase "olha o tamanhu desse git" funcionar sem precisar de texto.

## Onde os dados moram

`PlayerOrkProgressionProfile` fica **dentro** do `PlayerProgressionProfile`, na tag `Ork` — mesmo
arranjo do `PlayerCommanderProfile`. Assim continua havendo um SavedData, um pacote de sync e um
escritor.

**Com um mapa de ranks próprio.** Ids de nós Ork nunca entram no mapa `ranks` da árvore Astartes; um
erro de digitação ali deixaria um Boy comprar um Multi-lung.

## Migração

Save antigo não tem a tag `Ork`. `getCompound` devolve vazio, que carrega como Boy com nada — que é
onde um Ork começa de qualquer jeito. Facção, inventário, posição e progressão Imperial ficam
intocados.

---

## Corpo por facção (fatia 2)

O que viaja na rede agora é o **corpo resolvido** (`PlayerBody`: largura e altura), não o nome do
estágio. Há duas escadas que mudam o tamanho de um jogador — implantes Astartes e krumpagens Ork — e
o cliente não tem por que saber qual delas se aplicou. Mandar a resposta em vez da pergunta elimina
a chance de os dois lados calcularem diferente, que aqui não dá bug cosmético: dá jogador que não
coloca bloco e é ejetado pelo teto.

`PlayerProgressionSizeManager.serverBody()` pergunta a **facção primeiro**, escada depois. Os dois
perfis existem em todo jogador (um Imperial carrega um perfil Ork vazio e vice-versa), então ler o
estágio sem perguntar a facção entregaria a um Ork o corpo de um recruta da Guarda. Facção não
escolhida = humano comum, que é o correto.

Os três leitores agora falam a mesma língua: o size event, o `PlayerProgressionPose` (que segura a
pose quando o corpo não cabe em pé) e o `PlayerProgressionRenderScale` (que escala o modelo) todos
leem `PlayerBody`. Nenhum deles sabe o que é uma facção.

`refresh()` também deixou de receber o estágio Astartes: quem decide se precisa abrir espaço é o
corpo. Um Ork que vira Nob debaixo de um teto baixo tinha exatamente o mesmo problema, e chavear
isso na escada humana o teria deixado dentro da pedra.

Largura e altura são **limitadas na chegada** (0.1–4.0): esses dois floats viram caixa de colisão, e
caixa lida direto do fio é caixa que um pacote malformado escolhe.

## Como se ganha (fatia 3)

`PlayerOrkProgressionCombat` guarda a tabela — Guardsman 3, Kasrkin/Skitarii 8, Sister 10, Space
Marine 20, Primarca/Custodes 40. Zero significa "isso não foi briga que se conte", e o chamador não
dá nada: nem Krump, nem Fúria, nem tally. **Ork nunca é pago por matar Ork** — WAAAGH que paga por
matar Boy é WAAAGH que se come.

`PlayerOrkProgressionEvents` roda em dois eventos e mais nada:
- `LivingDeathEvent` → Krumpagem, tallies e Fúria.
- `LivingHurtEvent` → Fúria por dano dado e recebido, em prioridade `LOWEST` para que golpe que
  outro mod cancelou não conte.

**O anti-farm é o Imperial, não uma cópia.** `PlayerProgressionEvents.countsAsRealKill` virou
público. O estado dele mora no perfil externo, que todo jogador tem, então Ork e Astartes são
limitados pelo **mesmo contador** — não dois que dariam para jogar um contra o outro.

Fúria por dano dado é limitada (10 ticks entre prêmios) e recusa alvo invulnerável ou já morto; sem
isso dava para segurar 100 batendo num armor stand. Dano **recebido** não é limitado de propósito:
apanhar muito e rápido é exatamente quando um Ork devia estar ficando bravo mais rápido.

`/fcorkprogress status | add_krump | add_teef | set_fury | set_stage | set_clan | reset`. As portas
de evolução ficam em 40/150/400/900 Krumpagem — esperar isso para descobrir se um portão lê o campo
certo não é testar, é torcer.

## O que já existe (fatia 1)

- `PlayerOrkEvolutionStage` — cinco estágios com os tamanhos acima.
- `PlayerOrkProgressionBalance` — todos os números num arquivo só: Krumpagem por alvo, custos de
  nó (2/4/7 e 8/12/15/20), fúria, portões de evolução, bônus de estágio.
- `PlayerOrkProgressionProfile` — os três valores, tallies, clã, ranks, cooldowns, save/load.
- Aninhamento no `PlayerProgressionProfile` + migração.

## Arvore e atributos (fatias 4 e parte da 5)

`PlayerOrkProgressionTree` — 38 nos, cinco ramos, validador que grita no load. Ranks vao ate **3**,
nao 5. Os ramos se cruzam de verdade (`krump_first` exige KUNNIN + BRUTAL; `run_and_hit` exige
`krump_first` + `sneaky_git`; `kunnin_but_brutal` exige `im_da_boss` + `got_it_first`), entao nao
existe estrada unica ate o topo.

**Evolucao nao se compra:** os cinco nos de estagio custam zero e sao recusados explicitamente pelo
verbo de compra — rotear uma evolucao por ali seria pular todos os portoes.

O portao do Warboss le o **WAAAGH global existente**, e o tier e confirmacao e nao substituto: os
requisitos pessoais continuam obrigatorios, entao um jogador que encheu a mare global nao coroa
outro. Destruir Cores entrou na contagem pessoal e nao como requisito duro — mundo sem Core ao
alcance seria mundo onde ninguem nunca vira Warboss.

Verbos `ORK_UNLOCK` / `ORK_EVOLVE` / `ORK_SELECT_CLAN` / `ORK_ABILITY`, pelo mesmo motivo que
`COMMAND_UNLOCK` existe. O cliente manda **so um id**.

`PlayerOrkProgressionAttributes` tem **UUIDs proprios**, separados do passe Imperial: modificador e
chaveado por UUID, e compartilhar um faria os dois se sobrescreverem. `recalculate` roteia por
faccao e **limpa o passe que nao se aplica**. Os bonus de klan sao a tabela pequena daqui —
`OrkClan.applyTo` nao e chamado, os multiplicadores dele sao de mob e grandes demais para jogador.

## Fase B — todo nó faz alguma coisa

A regra que fecha esta fase: **nó que não faz nada não pode cobrar Dentu.** Os dezenove que ainda
eram só um nome ganharam efeito, e o cálculo saiu dos lugares onde estava espalhado.

`PlayerOrkCombatModifiers` é a única aritmética de dakka, melee, redução e fúria. Ele **responde nos
dois lados**: `ServerPlayer` cai no perfil real, qualquer outro cai no `PlayerProgressionClientView`.
Isso não é conveniência — o cooldown de uma arma é aplicado no cliente também (é o que desenha a
varredura sobre o ícone e o que impede o cliente de mandar um tiro que o servidor recusaria), então
se os dois lados calculassem números diferentes o MOAR DAKKA seria um nó pago e não sentido. O que
importa continua decidido no servidor: o projétil, o dano, a dispersão e os tiros extras.

`PlayerOrkRewardModifiers` é o outro: Dentu e loot, só servidor. `scaleTeef` é o **único** caminho de
Dentu no mod — guardar dentes no acampamento, matar um elite, derrubar um Core. Escrever o bônus em
cada um desses três lugares seria escrever um bônus que falta em dois deles.

`ShootaItem` perdeu `FIRE_COOLDOWN_TICKS`, `SHOT_DAMAGE` e `INACCURACY`. Ele pergunta. Um item que
sabe o que é uma árvore de progressão é um item de onde a próxima arma Ork copia a consulta.

**O Core Imperial finalmente conta.** `countCoreDestroyed()` e `countMajorVictory()` não tinham
chamador nenhum, e o portão do Warboss pede duas vitórias grandes — Warboss era inalcançável sem
comando. `PlayerOrkWorldEvents` escuta `BlockEvent.BreakEvent` em prioridade `LOWEST`, recusa evento
cancelado e recusa criativo (quem pode pôr e quebrar um Core se daria o portão em um minuto). Paga
Krumpagem, Dentu, a contagem e a vitória — e é o **único** caminho do jogador até o WAAAGH global,
por um método novo dentro do `WaaaghOverlordManager`. Golpe e kill nunca tocam a maré: se tocassem,
ela deixaria de dizer "como vai a guerra" e passaria a dizer "há quanto tempo esse jogador farma".

### NOT DEAD YET mora no LivingDamageEvent, e isso importa

`LivingHurtEvent` dispara **antes** de armadura, encantamento e absorção. O `amount` dele não é o
dano que vai sair da vida, então comparar com `getHealth()` ali dispara o nó em golpes que a
armadura teria aguentado — queimando um cooldown de cinco minutos num arranhão, e podendo *reduzir* a
vida de quem estava cheio. `LivingDamageEvent` é a última parada antes do `setHealth`: ali a
pergunta "isso mata?" é honesta. Só ali.

## Fase C — as quatro habilidades, e a rede da Fúria

`PlayerOrkAbility` + `PlayerOrkAbilityManager`. Nada fica rodando: uma habilidade Ork acontece,
acerta, e acabou.

- **'EADBUTT** e **KRUMP FIRST** (a investida) fazem clip contra bloco *antes* de procurar entidade,
  e a investida testa a caixa de destino e **recusa** em vez de clipar — dar velocidade e torcer para
  a colisão alcançar é como um avanço vira atravessar parede num servidor cheio. Nenhuma das duas
  adiciona velocidade vertical, então nenhuma é um jeito de voar.
- **WAAAAAAAAAGH!** exige a barra cheia, consome tudo, faz **um** scan e aplica efeito com duração.
  Exigir 100 e não escalar com o que houver na barra é de propósito: um grito a 40 de Fúria que dá um
  quarenta avos de bônus é um botão que se aperta o tempo todo e nunca se percebe.
- **BOSS ORDER** faz um scan no aperto. Sem alvo válido cai em BOYZ, OVER 'ERE, que é outro nó: os
  Boyz são capturados **por UUID** naquele instante e nunca mais procurados. O `tick` só re-pathfinda
  de 40 em 40 ticks, e sai na primeira linha com o mapa vazio — que é o estado de todo mundo que não
  acabou de gritar.

**A Fúria parou de mandar o perfil inteiro.** Ela se move a cada golpe dado e recebido, e cada ganho
terminava em `PlayerProgressionNetwork.sync()` — os dois ramos, todas as contagens, o klan e o corpo
transmitido para todo cliente que enxerga o jogador, por espadada. Agora é o `SyncOrkFuryPacket`:
dois campos, para um jogador, no máximo a cada dez ticks. Ele carrega o **par** (valor, gameTime) em
vez do valor, porque a decadência é aritmética sobre "quanto tinha e quando" — mandar só o número
deixaria a barra parada entre pacotes. O cliente roda o mesmo `fury()` sobre as mesmas entradas, e o
piso do DA GREENEST sai certo de graça.

`isValidFuryTarget` reaproveita `krumpFor(...) > 0`: uma definição de "isso foi briga", não duas.
Ork aliado, vaca e armor stand já valem zero ali. `isValidFurySource` exige atacante vivo, então
cacto, lava e queda não enchem mais a barra.

Teclas **H, X, J, Z** — K, O, V, B, G e R já tinham dono. Só enviam se `isOrk()`.

## Fase D — a tela

`PlayerOrkProgressionScreen` + `PlayerOrkTreeLayout`, **fora** da `PlayerProgressionScreen`. Aquela
já carrega duas arquiteturas e mil e quatrocentas linhas; uma terceira gramática ali significaria um
ramo a mais em cada método dela, e cada um desses ramos teria que lembrar de não mostrar Gene Seed a
um Ork. Uma tela separada não tem esse ramo para esquecer.

**Cinco páginas discretas**, sem zoom, sem arrasto, sem rolagem contínua. A razão não é estética: a
árvore Ork é travada por **tamanho** — um Boy não compra nada da página do Nob por mais que role até
lá —, então a quebra em cada degrau é a forma da coisa e não um corte arbitrário. A roda anda
exatamente uma página por entalhe com trava de 150 ms, porque um trackpad reporta um gesto como uma
dúzia de entalhes e as cinco páginas passariam num movimento só.

O layout **divide as linhas pela altura que existe** em vez de usar um passo fixo e correr para fora.
A GUI em escala 4 num monitor 1080p dá uma tela de 480x270, e sobram menos de duzentos pixels depois
do cabeçalho e do rodapé.

Um portão de evolução aparece como **checklist**, nunca como preço — não há o que juntar, a subida se
paga com coisa já feita. O checklist e a recusa do servidor leem a mesma tabela
(`PlayerOrkProgressionRequirements.Gate`), e o cinza dos nós vem do mesmo
`checkBuyRules` que o servidor usa: a placa da loja e o caixa são o mesmo código.

### O que a tela ensinou em jogo

Três coisas só apareceram quando a tela rodou de verdade:

1. **Reservar a largura do painel quando ele é sobreposição** espremeu cinco colunas em meia tela: o
   passo caiu para quarenta pixels e todo nome virou seis caracteres — "HIT 'EM 'ARDER" lia
   "HIT 'EM". Sobreposição não custa nada até abrir, então não deve custar nada no layout.
2. **A raiz na coluna do WAAAGH** ficava na mesma linha dos quatro filhos que a nomeiam como pai, e
   cada ligação descia, andava de lado e voltava **para cima** — um trilho horizontal atravessando a
   fileira de rótulos. Pai tem que estar acima do filho para um cotovelo significar alguma coisa.
3. **`GuiGraphics` não desenha texto quando mandado**; enfileira, e esvazia por tipo de render no fim
   do quadro. Então o rótulo da árvore sai *depois* de qualquer preenchimento, por mais tarde que ele
   seja pintado — "RUN TA KRUMP" e um "0/3" avulso apareciam impressos por dentro do painel e por
   cima do texto dele. `flush()` explícito não resolveu. **Scissor resolve**, porque `applyScissor`
   esvazia e só então recorta. O modal de Klan tem o mesmo problema e a mesma cura pela raiz: ele
   engole todo clique, então também não desenha nada atrás de si.

## O que falta

**Fase E.** `MOD_CONTEXT.md` e `STATUS.md` ainda não mencionam nada disto.
