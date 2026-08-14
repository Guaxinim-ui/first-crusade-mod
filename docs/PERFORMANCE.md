# First Crusade — Performance

Este documento cobre três coisas: quais mods de performance usar, como configurar os shaders, e como
diagnosticar quando algo estiver lento.

**Regra que vale para o documento inteiro:** o First Crusade **funciona sozinho**. Nenhum mod desta
página é dependência. Se algum deles faltar, o jogo abre, roda e joga igual — só mais devagar.

---

## 1. A stack recomendada

Rode `/fc compat` dentro do jogo para ver quais destes você tem instalados.

| Mod | Recomendação | Lado | Por que, neste mod especificamente |
|---|---|---|---|
| **Embeddium** | RECOMENDADO | Cliente | Maior ganho de FPS da lista. **Não afeta TPS** — uma batalha que derruba o servidor continua derrubando. |
| **ModernFix** | RECOMENDADO | Cliente + Servidor | Corta trabalho de inicialização e memória. Dos poucos aqui que ajudam de verdade num servidor dedicado. |
| **FerriteCore** | RECOMENDADO | Cliente + Servidor | Reduz memória de blockstates. Interessa mais a este mod que à média, porque o kit decorativo do Hive registra centenas de blocos. |
| **Chunky** | RECOMENDADO | Servidor | Pré-geração. Importante por causa dos planetas: gerar terreno no instante em que o jogador viaja pelo Spaceport é o pior momento possível. |
| **ImmediatelyFast** | OPCIONAL | Cliente | Acelera renderização imediata. Verificar as GUIs do mod (Command Core, Strategium, Ork Camp, terminal de navegação). |
| **Entity Culling** | OPCIONAL | Cliente | Onde mais rende aqui: Hive, corredores, bunkers, hangares. Precisa de teste com os mobs GeckoLib. |
| **Oculus** | OPCIONAL | Cliente | Só necessário se você quiser shader. |
| **FastSuite** | OPCIONAL | Servidor | Ainda não vale a pena. Renderá quando o mod tiver muitas receitas de arma, armadura, veículo e munição. |
| **spark** | DEV | Cliente + Servidor | Profiler. É a única ferramenta que atribui tempo por seção do tick. |

**Nada é OBRIGATÓRIO, de propósito.** Um mod que precisa estar instalado é uma dependência, e a
decisão de projeto foi não ter nenhuma.

### Para servidor dedicado

Embeddium, Oculus, ImmediatelyFast e Entity Culling são **client-side** — não instale num servidor.
O que ajuda um servidor é: **ModernFix, FerriteCore, Chunky** e, para diagnosticar, **spark**.

---

## 2. Perfis gráficos do próprio mod

Independente de shader, o First Crusade tem quatro perfis próprios, em
`config/firstcrusade-graphics-client.toml`:

```toml
[graphics]
    preset = "GRIMDARK"   # PERFORMANCE | GRIMDARK | EXTERMINATUS | CUSTOM
```

| Preset | Para quem | O que faz |
|---|---|---|
| `PERFORMANCE` | Máquina modesta, ou batalha muito grande | Partículas ~40%, alcance visual curto |
| `GRIMDARK` | **Padrão recomendado** | Partículas 70%, tracers e clarões ligados |
| `EXTERMINATUS` | Máquina forte | Tudo em 100% — idêntico ao comportamento original |
| `CUSTOM` | Ajuste fino | Usa os valores individuais da seção `[visuals]` |

O arquivo **nunca é reescrito pelo mod**, então experimentar presets não destrói a sua tunagem em
`[visuals]`.

**Garantia de projeto:** preset é só aparência. Um tiro causa exatamente o mesmo dano em
`PERFORMANCE` e em `EXTERMINATUS`. Se você observar diferença de dano entre presets, é bug — reporte.

### 2.1 Partículas que o **servidor** transmite

O preset acima só governa o que o seu cliente desenha por conta própria — hoje isso é só o rastro do
las-bolt. Quase todo efeito do mod não é desenhado pelo cliente: é o **servidor** que monta um pacote
e empurra para todo jogador num raio de 32 blocos. Nenhum config de cliente pode mandar nisso, porque
em multiplayer existe uma resposta só por mundo.

Esses efeitos ficam em `<mundo>/serverconfig/firstcrusade-performance-server.toml`, seção
`[particles]`:

| Dial | Governa | Frequência |
|---|---|---|
| `masterDensity` | Mestre sobre todos os canais abaixo | — |
| `muzzleFlashDensity` | Clarão no cano da arma | **1 por tiro, por soldado** |
| `debrisDensity` | Cápsula ejetada, estilhaços | **1 por tiro, por soldado** |
| `explosionDensity` | Micro-detonação do bolter, impacto do míssil, pisão do Sentinel | por impacto |
| `smokeDensity` | Fumaça de detonação, pisão, e o rastro do míssil | rastro = por tick de voo |
| `maxSendDistance` | Não transmite efeito sem jogador nesse raio | — |
| `maxSendsPerTick` | Teto duro por tick do servidor | válvula de segurança |

Os dois primeiros são os que importam: **duas transmissões por tiro por soldado**. Duzentos
Guardsmen numa troca de tiros são algumas centenas de pacotes por segundo, por jogador no alcance.

**Todos os padrões são 100** — recém-instalado, o mod transmite exatamente o que transmitia antes
desta seção existir. Para uma batalha grande, o que vale tentar primeiro:

```toml
[particles]
    muzzleFlashDensity = 50
    debrisDensity = 30
```

Mestre e canal se combinam por **mínimo, não por produto**: mestre 70 com canal 70 dá 70%, não 49%.

`maxSendDistance` tem teto 32 de propósito, não por arbitrariedade: o próprio vanilla se recusa a
entregar partícula não-forçada além de 32 blocos, então número maior seria dial incapaz de fazer
nada. Em 32 ele não muda nada visível — só evita montar pacote destinado a ninguém. Abaixo de 32
vira corte de alcance visual de verdade.

**O que deliberadamente NÃO passa por esses dials:** os marcadores de debug do `/fchive`, o marcador
de posição do governador, e o feedback de habilidade da progressão (Imperium e Ork). Visualização que
você pediu digitando um comando tem que aparecer como pedida, e feedback de habilidade dispara no seu
próprio input algumas vezes por segundo no máximo — nenhum dos dois cresce com o tamanho do exército,
então nenhum dos dois merece dial.

**Garantia de projeto, igual à dos presets:** isto é puramente visual. Uma partícula que não foi
transmitida não muda dano, raio de explosão nem registro de acerto.

---

## 3. Shaders (Oculus)

O mod não empacota shader nenhum e não redistribui arquivos de terceiros. As recomendações abaixo são
sobre packs que você baixa por conta própria.

### Perfil PERFORMANCE — *MakeUp Ultra Fast*

Iluminação, sombra e atmosfera decentes com impacto baixo. Combine com o preset `PERFORMANCE` ou
`GRIMDARK` do mod.

### Perfil FIRST CRUSADE — GRIMDARK — *Complementary*

Direção visual pretendida: guerra, indústria, fumaça, metal, ruína, escuridão, luz artificial forte,
poluição. Nada de fantasia limpa e colorida.

Ajustes dentro do Complementary que aproximam esse resultado:

| Ajuste | Direção |
|---|---|
| Saturação | levemente reduzida |
| Contraste | forte |
| Sombras | mais profundas |
| Fog | moderado, presente |
| Volumetric light | ligado |
| Bloom | moderado — o suficiente para luz artificial "sangrar" |
| Noite | escura de verdade |

O objetivo é que uma Hive City pareça enorme, industrial e opressiva.

### Confirmado funcionando

| Shader | Versão | Resultado | Data |
|---|---|---|---|
| **Hysteria Shaders Universal** | v1.2.2 | Funciona; visual escuro com volumétrica, coerente com a direção grimdark | 2026-08-13 |

As duas recomendações acima (MakeUp, Complementary) continuam **não testadas** neste projeto.

### Sem shader

É um caminho de primeira classe, não um consolo. `Sem shader + preset PERFORMANCE` é a configuração
para máquina modesta, e o mod foi desenhado para ficar bom assim.

| Máquina | Combinação |
|---|---|
| Modesta | Sem shader + `PERFORMANCE` |
| Média | MakeUp Ultra Fast + `GRIMDARK` |
| Forte | Complementary + `EXTERMINATUS` |

---

## 4. Onde testar a stack — **não é no `gradlew runClient`**

Isto foi verificado em 2026-08-13 e é uma limitação do ambiente, não um bug de nenhum mod:

> **Mods de produção baseados em mixin não carregam no workspace do Gradle.**
>
> O `runClient` roda com mapeamentos **oficiais (Mojang)**, e o refmap de um mod publicado aponta
> para nomes **SRG**. O resultado é `@Shadow field f_117950_ was not located in the target class`.
> Verificado com Oculus e com Embeddium; vale igual para ImmediatelyFast, Entity Culling e qualquer
> outro que use mixin.

Consequência prática:

| Para que serve | Onde rodar |
|---|---|
| Desenvolver o mod | `gradlew runClient` — **mantenha `run/mods` vazia** |
| Testar a stack de performance | Instalação **real** do Minecraft com Forge 1.20.1 |

### Montando a instalação real

1. `gradlew build` — o jar sai em `build/libs/`
2. Instale o **Forge 1.20.1** com o instalador oficial e abra o perfil uma vez
3. Copie para `.minecraft/mods/`: o jar do First Crusade, o **GeckoLib** (Forge 1.20.1) e os mods de
   performance que quiser
4. Shaders vão em `.minecraft/shaderpacks/`, como `.zip`, sem descompactar

Os jars de performance já baixados estão em `run/perf-mods/` (fora do `run/mods` de propósito, para
não quebrar o `runClient`). Basta copiá-los.

**FastSuite exige o mod Placebo** e derruba o boot sem ele. Como está classificado como "ainda não
vale a pena", o mais simples é não instalar nenhum dos dois por enquanto.

---

## 5. Matriz de compatibilidade — **NÃO TESTADA**

> **Esta tabela está vazia de propósito.**
>
> Afirmar que uma combinação funciona sem tê-la executado seria mentira, e o briefing proíbe
> explicitamente. Nenhuma das combinações abaixo foi testada. Preencha conforme for verificando.

Ordem de teste recomendada — um mod por vez, porque é assim que se descobre qual deles quebrou:

| # | Combinação | Versões | Resultado | Data |
|---|---|---|---|---|
| 1 | Forge sozinho | | | |
| 2 | Forge + First Crusade | | | |
| 3 | \+ Embeddium | | | |
| 4 | \+ ModernFix | | | |
| 5 | \+ FerriteCore | | | |
| 6 | \+ ImmediatelyFast | | | |
| 7 | \+ Entity Culling | | | |
| 8 | \+ Oculus | | | |
| 9 | \+ Shader | | | |

Em cada passo, verifique especificamente os mobs GeckoLib e os renderers próprios:

- Guardsmen, Orks, Space Marines
- Sentinel, tanques, Valkyrie
- armas na mão (a Chainsword anima os dentes por predicado de item)
- GUIs: Command Core, Strategium, Ork Camp, terminal de navegação planetária

**Suspeitas conhecidas, ainda não confirmadas:** Entity Culling com mob GeckoLib é a combinação com
maior chance de problema visual (unidade sumindo quando deveria aparecer). ImmediatelyFast é a com
maior chance de afetar as GUIs do mod.

---

## 6. Diagnóstico

### Comandos do mod

```
/fc perf              unidades por nível de IA, projéteis, batalhas abstraídas, partículas
/fc perf entities     contagem por facção e por tipo
/fc squad             esquadrões: líder, membros, alvo compartilhado, LOD
/fc strategic         batalhas resolvidas como número
/fc strategic sweep   força uma varredura e diz por que absorveu ou não
/fc compat            quais mods de performance estão instalados
/forge tps            MSPT por dimensão
```

### FPS baixo mas TPS bom

Problema de cliente. Nessa ordem: baixe o preset gráfico do mod, instale Embeddium, desligue o
shader, considere Entity Culling.

Se o FPS só cai **durante tiroteio**, o preset gráfico não vai resolver: os clarões e as cápsulas
chegam do servidor, e quem os corta é `[particles]` no config de servidor (seção 2.1). Num mundo
single-player esse arquivo é seu também — está em `saves/<mundo>/serverconfig/`.

### TPS baixo (o servidor engasgando)

Problema de simulação, e nenhum mod de cliente resolve. Verifique com `/fc perf` quantas unidades
existem e em que nível de IA. Dials em
`<mundo>/serverconfig/firstcrusade-performance-server.toml`:

| Dial | Efeito |
|---|---|
| `ai.followRangeCap` | O maior de todos. Paga duas vezes: aquisição de alvo **e** tamanho da região que cada pathfind copia |
| `ai.targetScanIntervalTicks` | Frequência com que cada unidade procura alvo |
| `ai.lod.*` | Distâncias e multiplicadores do nível de detalhe |
| `strategic.*` | Quando uma batalha distante vira aritmética |
| `particles.*` | Quanto o servidor transmite de efeito visual — ver seção 2.1. Custo de rede, não de simulação |

### Como medir de verdade

Duas armadilhas que já produziram números falsos neste projeto:

1. **Descarte a primeira medição depois de subir o mundo.** JIT ainda compilando e chunks carregando:
   sai 40-60% mais cara que as seguintes. Meça pelo menos 3 vezes.
2. **Campo aberto não mede custo de aquisição de alvo.** Sem geometria bloqueando a visão, o raycast
   de linha de visão morre em poucos blocos de ar e não custa nada. Para comparar, ponha um muro
   entre as duas linhas.

O benchmark automatizado está em `tools/battle_bench.py` (servidor dedicado + RCON).

Para atribuição por seção do tick — saber se o próximo milissegundo está em pathfinding, em IA ou em
outro lugar — só com **spark**:

```
/spark profiler start --timeout 60
```

durante uma batalha.
