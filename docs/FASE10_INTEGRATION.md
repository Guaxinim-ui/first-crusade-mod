# FASE 10 — Guia de integração (o que ligar no código existente)

Todo o código novo vive isolado em `com.example.examplemod.hive.city` (subpacote novo dentro
do pacote da Hive), mais os JSONs de datapack da dimensão. A ligação ao mod existente é
**mínima e explícita**: 3 pontos, todos de 1 linha.

## Arquivos entregues

**Java** (`src/main/java/com/example/examplemod/hive/city/`):
- `HiveWorld.java` — resource keys + envelope vertical da dimensão (fonte única de verdade).
- `HiveCityLayout.java` — planejador determinístico (seed → lista ordenada de distritos).
- `HiveGenerationQueue.java` — fila persistida (SavedData) de tarefas de colocação.
- `HiveCityTicker.java` — processa a fila N distritos/tick, com force-load de chunks e feedback.
- `HiveCityPlacer.java` — **seam de integração**: onde a fila chama a colocação real de distrito.
- `HiveCityCommands.java` — subárvore `/fchive city generate|status|cancel|tp`.

**Datapack** (`src/main/resources/data/firstcrusade/`):
- `dimension_type/hive_world.json` — min_y −64, height 576, dim escura coberta.
- `dimension/hive_world.json` — gerador flat com 1 camada de bedrock (terreno vazio; a cidade é o terreno).
- `worldgen/biome/hive_floor.json` — bioma plano sem spawns/features.

**Ferramenta**:
- `tools/validate_fase10.py` — validação programática (roda sem gradle/rede; 51 checagens).

## Ponto de ligação 1 — comandos (obrigatório)

No `HiveCommands` (raiz `/fchive`, permissão 2), onde as outras subárvores são penduradas,
adicione **uma linha**:

```java
root.then(com.example.examplemod.hive.city.HiveCityCommands.build());
```

(`root` = o `LiteralArgumentBuilder` de `/fchive`. Mesma técnica de todas as fases.)

## Ponto de ligação 2 — colocação real de distrito (obrigatório)

`HiveCityPlacer.place(...)` é o único lugar que precisa conhecer a assinatura real do
`HiveDistricts`. Hoje ele lança exceção de propósito (para a wiring não ser esquecida).
Substitua o corpo por uma chamada ao MESMO método que `/fchive district place` usa. Baseado
na Fase 5, deve ser algo como:

```java
public static boolean place(ServerLevel level, String districtId, BlockPos origin, int rotation) {
    return com.example.examplemod.hive.HiveDistricts.place(
            level, new ResourceLocation(districtId), origin, rotation);
}
```

Se a assinatura real usar um objeto de contexto ou o record de distrito, adapte — o mapeamento
é mecânico (level, id, origin NW-min, rotação 0..3). O importante: reusar o caminho existente
que **já** processa marcadores (`HiveMarkerProcessor`) e valida costuras por socket.

## Ponto de ligação 3 — existência do Spire (opcional)

Em `HiveCityCommands.HiveCityPlacer_DistrictExists(...)`, aponte para o lookup de existência
do `HiveDistricts`, para o Spire só entrar no plano quando estiver registrado:

```java
return com.example.examplemod.hive.HiveDistricts.exists(new ResourceLocation(districtId));
```

Enquanto retornar `false` (default), o Spire é silenciosamente omitido — o resto da cidade
gera normalmente.

## O que NÃO precisa mudar

- `ExampleMod` — nada. `HiveCityTicker` se auto-registra via `@Mod.EventBusSubscriber(modid=MODID)`.
  (Se o seu projeto usa apenas registro manual de event handlers, registre-o no MOD/FORGE bus
  como os outros; mas o annotation-based já basta no 47.x.)
- Nenhuma classe fora de `hive/` é tocada.

## Registro da dimensão

A dimensão é 100% data-driven (datapack). Nenhum código de registro de dimensão é necessário
no 1.20.1 — basta os 3 JSONs estarem em `data/firstcrusade/...`. O `/fchive city tp` acessa
`server.getLevel(HiveWorld.LEVEL)`; se vier `null`, o datapack não carregou (mensagem de erro
já trata isso).

## Nota de API (armadilha da spec §4)

- `HiveGenerationQueue.get()` usa a forma de **3 argumentos** de `computeIfAbsent(loader,
  supplier, name)` — a sobrecarga `SavedData.Factory` só existe no 1.20.2+/NeoForge e **não
  compila** no 47.x. Validado contra o Javadoc do Forge 1.20.1.
- `setChunkForced`/`getForcedChunks` confirmados no 47.x.

## Ordem de teste no jogo (segue a spec §3)

1. Compilar. `/fchive city tp` → cai numa dim plana vazia (bedrock em y=−64, céu do nether).
2. `/fchive city generate` sem seed → lê o log do plano (45 distritos no raio 2), confirma
   origens/rotações no chat ANTES de colar.
3. Observar a construção escalonada (1 distrito/tick) e as mensagens de progresso a cada 10%.
4. `/fchive city status` no meio; **quit e volte** — a fila continua de onde parou.
5. `/fchive city cancel` limpa a fila (blocos já colados permanecem).
6. Ajustar `HiveCityTicker.DISTRICTS_PER_TICK` e `HiveCityCommands.DEFAULT_RADIUS` conforme a
   performance da sua máquina antes de escalar para cidades maiores.
