# Handoff — camada de campanha planetária

Cole isto como primeira mensagem numa sessão nova.

---

Você vai continuar meu mod **First Crusade** (Warhammer 40k, Minecraft Forge 1.20.1, Java 17,
pacote `com.example.examplemod` — **NÃO renomear**). Branch `performance-layer`.

## ESTADO

Uma sessão anterior implementou a camada de campanha planetária inteira. **Build verde
(`.\gradlew build`) e datagen rodado, mas NADA foi testado em jogo.** O trabalho está **sem commit**
na working tree.

Leia antes de mexer:
- `docs/STATUS.md`, changelog de 2026-08-20 — tem o registro completo, blocos 1 a 5 + auditorias.
- A memória `campaign-layer` — tem as decisões que não estão óbvias no código.

## O QUE JÁ FOI FEITO

**Correções de arquitetura multiplanetária (eram bugs reais, confirmados no código):**
- `WorldWarMapData` resolvia sempre no overworld e guardava `BlockPos` sem dimensão — os 9 planetas
  escreviam no mesmo balde. Agora é bucketed por dimensão (formato 3), todo acessor exige a dimensão.
- `WorldSettlementData.planetSeeded` era **um booleano só**: o primeiro planeta visitado marcava
  tudo como povoado e nenhum outro gerava assentamento. Virou `Set` de dimensões.
- `FactionResearchManager.tick` era chamado dentro do laço por planeta e as dimensões compartilham
  o game time — a pesquisa descontava **9× por segundo**. Agora roda uma vez por tick do servidor.
- `StrategicWarAIData` tinha o mesmo bug do mapa; passou a ser por nível.
- `/fcstrategy status|projects|tick|reset` procuravam `Level.OVERWORLD` pelo nome e reportavam um
  overworld vanilla vazio. Agora usam o nível de quem chamou.

**Pacote `campaign/` (40 classes):** `StrategicLocation`, fronts, `PlanetWarState`, setores
(36 tipos, 13 layouts — é isso que dá identidade aos planetas), `CampaignData`, `CrusadeScore`,
Operations (10 tipos × gatilhos), logística (`SupplyRoute`/`SupplyNetwork`, 14 rotas), Mesa de Guerra
(bloco `firstcrusade:war_table` + snapshot + tela de 2 abas), ordens do jogador, `StrategicDeployment`
+ `DeploymentManager`, `OrkOffensiveManager`.

**Pacote `ai/` (13 classes):** supressão (§22, decaimento preguiçoso — nenhuma unidade tica),
`FCCoverGoal` (§21, 16 raios, sem pathfinding), `FCSquadOrderGoal` (§20), liderança (§23).

**Auditorias:** §28 áudio (4 ids novos, zero `BLAZE_SHOOT` restante), §27 troféus de fauna
(entrega no Strategium por pesquisa + XP), §29 modelos (levantamento escrito, sem código).

## REGRAS QUE VALEM (não reverter sem entender)

1. `CampaignData` e `WorldWarMapData` ficam **no overworld de propósito** — a Mesa de Guerra tem que
   desenhar Armageddon com o jogador em Macragge. O bug antes era ficar no overworld **e** ser
   chaveado só por posição.
2. Controle planetário é **recalculado dos setores, nunca acumulado**.
3. `WarDominion` virou **leitura**, não registro (`CrusadeScore` recalcula).
4. `OperationTrigger.MANUAL` existe para não mentir: RESCUE/ESCORT/RECOVER são definidos e **nunca
   gerados**, porque nada no mundo os completa.
5. Deployment **não anda** — espera um timer. Materializa com teto perto do jogador; o
   `FCStrategicBattleData` já absorve de volta sozinho.
6. Botão da Mesa de Guerra **nunca é acinzentado pelo cliente**; o servidor recusa dizendo qual das
   6 checagens falhou.
7. Pacote novo vai **no fim** de `FirstCrusadeNetwork.register()` — o id é a posição na lista.
8. Setor usa `.` e não `/` no id: o leitor de string sem aspas do Brigadier para na barra.
9. **Antes de criar sistema novo, procure o que já está lá inerte.** Rendeu mais que tudo:
   `FCSquadOrder` não tinha escritor, `FCCombatProfile.shouldRetreat` não tinha chamador, os troféus
   de fauna eram lidos só pela loot table que os criava.

## O QUE FALTA (ordem sugerida)

1. **TESTAR** — nada foi visto rodando. Ver "Como testar" abaixo.
2. §5 identidade dos planetas em runtime (hoje só os layouts de setor diferem).
3. §24 Ork Camp expandido: recrutar Boyz, promover Nob, Squig Pen, Mek Workshop, escolher alvo.
4. §14-15 veículos ligados à economia (Forge World já tem setor `VEHICLE_FACTORY` e produção de
   PLASTEEL/PROMETHIUM — a costura está pronta).
5. §26 Necrons — a arquitetura já guarda o despertar (0-100, 5 estágios); faltam as entidades.
6. §29 modelos: 4 mobs placeholder (Ork Nob, Meganob, Gretchin, Killa Kan). **Cuidado com a
   armadilha de UV** — ver a memória `ork-model-placeholders`.
7. §18-19 Hive vertical, ESCORT/TRANSFER na Mesa de Guerra (precisam de comboio).

## COMO TESTAR

`.\gradlew runClient`, e **sem jar do mod em `run/mods`** (jar reobf ali dá `NoSuchFieldError`).

**No overworld a campanha não faz nada, e isso está certo** — as frentes só ativam com jogador no
planeta. Viaje primeiro (as aspas importam, o id tem `:`):

```
/firstcrusade planet travel @s "firstcrusade:armageddon"
/fcstrategy planet status
/fcstrategy sector list
/fcstrategy war tick
/fcstrategy raid start
/give @s firstcrusade:war_table
/fc squad
```

Maior risco visual: a tela da Mesa de Guerra, que nunca foi renderizada — texto vazando da moldura,
chave de tradução crua, botões sobrepostos.

## COMO TRABALHAR

Leia antes de alterar. Não recrie sistema que já existe, prefira estender. Rode `.\gradlew build` e
corrija todos os erros antes de me entregar. Se mexer em bloco, rode `runData` também. Diga
claramente o que mudou — e o que **não** foi testado.
