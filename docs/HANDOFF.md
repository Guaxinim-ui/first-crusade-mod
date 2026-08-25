# Handoff — First Crusade

Cole o bloco abaixo como primeira mensagem numa sessão nova, depois de `/clear`.

---

Você vai continuar meu mod **First Crusade** (Warhammer 40k, Minecraft Forge 1.20.1, Java 17,
pacote `com.example.examplemod` — **NÃO renomear**). Branch `performance-layer`, tudo commitado e
pushed em `origin/performance-layer`.

## LEIA PRIMEIRO

- `docs/STATUS.md` — §4.1 tem a lista viva do que falta; o changelog (§7) está em ordem cronológica
  inversa e as entradas de **2026-08-21 a 2026-08-24** são o estado atual.
- As memórias `campaign-layer`, `necron-asset-generator`, `campaign-rcon-test-recipe` e
  `worldgen-script-ownership` carregam sozinhas e têm as decisões que não estão óbvias no código.

## ESTADO

A camada de campanha planetária, a camada `ai/`, os Necrons e os modelos Ork estão **feitos e
commitados**. O que foi medido e o que não foi está marcado entrada a entrada no changelog — a
regra da casa é não dizer que algo funciona sem ter medido.

**Verificado a correr (servidor dedicado + RCON):** as 10 frentes com layouts distintos e sem
vazamento entre planetas; pressão de assentamento a mover o mapa; ciclo Ork completo no timer sem
jogador; logística; operações; despertar Necron a tomar a zona de pouso; reanimação dos Guerreiros
(6 mortos, 4 levantaram-se); supressão a atravessar os limiares e a decair sozinha; esquadrão a
formar-se à volta de um Nob; ruína Necron a construir-se com 1 relicário, 66 de parede e 117 de piso.

**NUNCA visto (precisa de cliente):** o render dos **sete modelos novos** (3 Necrons + 4 Orks) e do
relicário; a **aba FRONTS da Mesa de Guerra**; o pacote de ordem da Mesa; a materialização de
deployment perto do jogador; a requisição de blindado; os quatro botões novos do Ork Camp; os
perigos ambientais dos planetas; os **comboios na aba Logistics** e o ramo do ESCORT com jogador
presente; e os **seis sons de planeta** e os roamers da §5 fatia 2.

## O QUE FALTA

Ver `docs/STATUS.md` §4.1. **ESCORT/comboios** e a **§5 fatia 2** ficaram feitos em 2026-08-24; a
**§18 fatia 1** (circulação vertical da Hive) e as **unidades da §19** em 2026-08-25. Sobra: o **DEEP
HIVE** e pôr o elevador nos módulos dos distritos (§18 fatia 2); um marker para o Priest, o
`COMMANDER_POINT` e as patrulhas a andar (§19). Bloqueados por coisas que não existem: RESCUE,
RECOVER, e os desbloqueios de Cadia e Ork World.

⚠️ **O que a §19 precisa a seguir é a cidade construída, não código.** As oito unidades existem e
sete estão ligadas aos markers que os distritos plantam, mas `/fchive validate markers` responde
"Nenhuma cidade persistida ainda" — o caminho marker→spawn não pode ser medido antes de
`/fchive city generate`.

⚠️ **A numeração `§` vem de uma spec que NÃO está em `docs/`** — o dono cola-a no chat. Não adivinhe o
que uma secção quer dizer a partir das citações internas do `HIVE_CITY.md`: foi assim que a avaliação
do §18-19 saiu errada em 2026-08-24. Peça a secção.

## COMO CONSTRUIR E TESTAR

O wrapper `./gradlew` falha (sem rede). Gradle 8.8 em cache, `--offline`, com
`dangerouslyDisableSandbox: true`:

```
G=$(ls -d ~/.gradle/wrapper/dists/gradle-8.8-bin/*/gradle-8.8/bin/gradle | head -1)
"$G" -p /c/Users/hrlup/Documents/first-crusade-mod build --console=plain --offline
"$G" -p /c/Users/hrlup/Documents/first-crusade-mod runData --console=plain --offline   # se mexer em bloco
```

**Servidor dedicado + RCON** alcança quase tudo sem cliente — receita completa na memória
`campaign-rcon-test-recipe`. Em resumo: `run/server.properties` com `enable-rcon=true`,
`rcon.password=fctest`, `online-mode=false`, `level-name=campaigntest` (**restaurar depois**),
`runServer` em background, e comandos pela classe `Rcon` de `tools/world_probe.py`.

**Espere pela porta 25575, não pelo log.** O `run/logs/latest.log` só roda quando o servidor novo
arranca, então um `grep "RCON running"` casa com a linha da sessão anterior e você conecta cedo
demais.

**A camada `ai/` não corre sem jogador, por desenho.** Unidades longe de qualquer jogador caem em
LOD `STRATEGIC` e o `FCStrategicBattleData` absorve a batalha inteira. Para exercitar supressão,
cobertura e esquadrões num teste headless é preciso desligar `[ai.lod] enabled` **e**
`[strategic] enabled` em `run/<mundo>/serverconfig/firstcrusade-performance-server.toml`
(e restaurar depois). Isso é a optimização a funcionar, não um bug — mas é a razão de essa camada
ter ficado 2500 linhas sem nunca ser observada.

Comandos úteis: `/fc perf`, `/fc squad`, `/fc suppression`, `/fcstrategy planet list|activate|awaken`,
`/fcstrategy sector list`, `/fcstrategy war tick`, `/fcstrategy supply list`,
`/fcstrategy convoy list|dispatch <frente>|strike <frente> <n>`, `/fcstrategy raid start`,
`/firstcrusade planet unlock|travel`, `/fchive city tp|generate|status`,
`/fchive city lift <x y z> [up|down]` (sonda o elevador sem jogador).

## REGRAS QUE VALEM (não reverter sem entender)

1. **Antes de criar sistema novo, procure o que já está lá inerte.** Rendeu mais que tudo o resto:
   `FCSquadOrder` sem escritor, `FCCombatProfile.shouldRetreat` sem chamador, troféus lidos só pela
   loot table, `launchWarParty` completo e inalcançável, `VEHICLE_FACTORY` sem consumidor, o
   despertar Necron a escrever só log.
2. **Um dono por ficheiro de worldgen.** A etapa 4 de cada bioma é do `generate_fauna_sites.py`.
   Dois geradores no mesmo ficheiro já apagaram toda a vegetação do mod uma vez.
3. **Modelos e texturas gerados por script.** `tools/generate_necron_assets.py` e
   `generate_ork_assets.py` **atribuem** as UVs em vez de as ler, e por isso modelo e textura não
   podem divergir. **Nunca editar os PNG nem os .geo.json à mão** — editar `*_bones()` e regerar.
4. `CampaignData` e `WorldWarMapData` ficam **no overworld de propósito** (a Mesa desenha Armageddon
   de Macragge). O bug antigo era ficar lá **e** ser chaveado só por posição.
5. Controle planetário é **recalculado dos setores, nunca acumulado**.
6. **O servidor não monta frases** — não sabe o idioma de quem lê. Motivo de rota é chave +
   argumento, ambos chaves de tradução, resolvidos por quem desenha.
7. **Cada recusa diz qual checagem falhou.** Vale para a Mesa de Guerra, o Ork Camp, a requisição de
   blindado e o `raid start`. "Não aconteceu nada" é o que o jogador reporta como bug.
8. Pacote de rede novo vai **no fim** de `FirstCrusadeNetwork.register()` — o id é a posição.
9. Setor usa `.` e não `/` no id: o leitor de string sem aspas do Brigadier para na barra. Pela
   mesma razão **nenhum comando de comboio aceita um id** (`agri_world>armageddon:FOOD@1234` tem
   `>`, `:` e `@`, que aquele leitor recusa) — todos nomeiam uma **frente**.
10. **Sem jar do mod em `run/mods`** — o jar de `build/libs` é reobfuscado e dá `NoSuchFieldError`
    no dev.
11. **Bioma não serve de chave por planeta.** `salt_waste` é 30% de Armageddon, 25% do Forge World e
    60% do mundo-tumba; `ash_waste` está em três mundos. Qualquer coisa que tenha de valer *por
    planeta* — perigo, som, spawn — é chaveada por **dimensão** em Java, nunca pelo bioma.
12. **`/playsound` não valida o id do som** (aceita um inventado), portanto não prova registo.
    O `/damage`, o `/setblock`, o `/summon` e o `/item replace` **validam** e por isso servem.
13. **`registerGoals()` corre dentro do `super(...)`** do `Mob`, antes de qualquer campo da subclasse
    existir. Nunca ler um campo próprio ali — derivar do `getType()`, que o construtor do `Entity`
    já preencheu. Custou um "Unable to summon entity" sem mais explicação nas unidades da Hive.

## COMO TRABALHAR

Leia antes de alterar; prefira estender a recriar. Rode `build` e corrija tudo antes de entregar;
`runData` se mexer em bloco. **Diga claramente o que mediu e o que não mediu** — e se um teste falhar,
mostre a saída em vez de suavizar.
