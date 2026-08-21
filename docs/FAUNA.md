# Fauna do First Crusade

Integração dos modelos aprovados no Blockbench como fauna funcional: 15 espécies, 13 sítios de
worldgen, habilidades, sons, loot e distribuição ecológica.

Este documento é a referência da **camada de fauna**. O que ele não repete: o contexto geral do mod
(`MOD_CONTEXT.md`) e a camada de performance (`PERFORMANCE.md`).

---

## 1. O pipeline da arte — e a regra que não pode ser quebrada

Os modelos são do dono, feitos no Blockbench. **Nada neste repositório os reescreve.**

```
~/Downloads/<especie>.bbmodel        (fonte de verdade, do dono)
        │
        │  tools/bbmodel_to_geckolib.py
        ▼
assets/firstcrusade/geo/<especie>.geo.json
assets/firstcrusade/animations/<especie>.animation.json
assets/firstcrusade/textures/entity/<especie>.png
```

O conversor **não cria arte**: não mexe em UV, não repinta textura, não simplifica cubo. Toda a
geometria e todas as animações saem como o dono deixou. Se a silhueta estiver errada no jogo, o erro
está no `.bbmodel` e se corrige no Blockbench.

### As três regras de conversão que ninguém acerta de cabeça

Foram lidas do `app.asar` da instalação do Blockbench (codec `bedrock`), não escritas de memória:

1. **Bedrock espelha o eixo X.** `origin.x = -(from.x + size.x)`; pivô de bone e de cubo com x
   negado; rotação com x e y negados.
2. **As faces `up`/`down` têm o UV invertido** — o canto anda `uv + uv_size` e o tamanho fica
   negativo. Sem isso o topo e a barriga de todo cubo saem espelhados.
3. **Keyframe de animação também espelha:** `position` nega x; `rotation` nega x e y; `scale` não
   muda.

### Autosave comprimido

O Ambull nunca foi exportado para o Downloads — existe só como autosave do Blockbench, que vem
comprimido com prefixo `<lz>`. Apesar do nome, **não é LZString**: é
`LZUTF8.compress(json, {outputEncoding: 'StorageBinaryString'})`. `tools/bbmodel_lib.py` lê os dois
formatos.

### Um dono por arquivo

`tools/generate_animal_assets.py` (o gerador paramétrico da Fase E) **não escreve mais** geo,
animação nem textura de `grox`, `squig`, `cyber_mastiff`, `ambull` e `ash_strider` — a lista está em
`OWNED_BY_BLOCKBENCH`. Sem essa guarda, rodá-lo substituiria a arte aprovada por modelos gerados,
sem erro nenhum, e o sintoma seria "os bichos voltaram a ser blocos".

---

## 2. As 15 espécies

| espécie | ambiente | raridade | habilidades |
|---|---|---|---|
| Grox | estepe, morro | comum | charge, gore, threat display, graze |
| Squig | cinzas (Ork) | comum | leap attack, roar |
| Cyber-Mastiff | estepe, morro | incomum | auspex scan, pounce, combat lock |
| Sump Rat | pântano | comum | — |
| Ash Strider | cinzas, sal | incomum | — |
| Ambull | morro, cinzas | muito raro | burrow, emerge, ground slam |
| Fenrisian Wolf | tundra, ferrofuste | incomum | pounce, howl (buff de matilha) |
| Arthromite Duneskuttler | cinzas, sal | raro | burrow, emerge, ambush charge, mandíbulas |
| Dustback Helamite | cinzas, sal | incomum | mighty leap, rear kick, threat rear |
| Cthellean Cudbear | matas, selva | raro | territorial roar, maul |
| Duskhorn | estepe, mato escuro | raro | charge, gore, trample |
| Knarloc | mato escuro, estepe | incomum | leap attack, rear kick, threat display |
| Greater Malkavan Constrictor | selva, pântano | raro | strike, constrict, coil |
| Catachan Barking Toad | **só estrutura** | apex | toxic burst charge → toxic burst |
| Catachan Devil | **só estrutura** | apex | tail sting, pincers, camuflagem |

**Os dois apex não têm spawn natural nenhum.** Eles existem apenas nas estruturas deles. Peso de
spawn, por menor que fosse, produziria Catachan Devils aleatórios pela selva — e um apex que aparece
sem o ninho dele perde a única coisa que o torna um evento.

### Registros: por que são dois

`FCAnimals` (pacote `animal`) continua dono das seis espécies da Fase E; `FirstCrusadeFaunaRegistry`
(pacote `fauna`) é dono das nove novas. Mover as antigas renomearia entidades que já existem em
saves, em loot tables e nos JSON de bioma, em troca de nada que o jogador possa ver.

A **infraestrutura** é uma só e serve às quinze: `FaunaEntity`, `FaunaSpawnRules`,
`FaunaSoundEvents`, `effect/FaunaVisualEffects`.

---

## 3. Habilidades: três fases, e só uma é salva

`FaunaAbility` é um record `(animation, windup, active, cooldown)`. A máquina de estados vive em
`FaunaEntity`:

```
preparação (windup)  o bicho se arma: abaixa a cabeça, raspa o chão, infla a garganta
ativa (active)       o golpe acontece; o dano sai aqui, e sempre no servidor
descanso (cooldown)  o tempo em que a habilidade não pode voltar
```

Preparação e fase ativa **não são salvas** — duram menos de dois segundos. O **cooldown** é o único
que persiste: sem ele, sair e voltar do mundo devolve uma carga de Duskhorn imediata.

**O nome da animação pertence à espécie, não à habilidade.** O salto é `leap_attack` no Squig e no
Knarloc, `pounce` no lobo, `pounce_attack` no Cyber-Mastiff. Um enum global teria de escolher um nome
e estaria errado em três modelos — e animação que não existe no arquivo não dá erro, só não toca.

### A regra de proximidade

`FaunaEntity.abilitiesAwake()` é a porta de todo comportamento especial: **32 blocos**, medidos pela
lista de jogadores do nível (curta, sem alocar iterador de AABB). Bicho em chunk carregado sem
ninguém em volta paga um comparativo de inteiro por tick e mais nada.

Trinta e dois não é gosto: é o mesmo alcance em que o vanilla para de entregar pacote de partícula.

---

## 4. Enterrado é estado, não escavação

Ambull e Duneskuttler ficam sob o chão. **Nenhum bloco muda.** Um Ambull que escavasse de verdade
deixaria buraco permanente em todo lugar por onde passou, e depois de uma hora o deserto seria um
queijo.

- servidor: `isBurrowed()` sincronizado por `SynchedEntityData`; sem colisão, sem empurrão;
- cliente: `FaunaGeoRenderer.shouldRender()` devolve false — **e não `render()`**, porque a sombra é
  desenhada pelo `EntityRenderDispatcher` *antes* de chamar o renderer, e cortar dentro do `render`
  deixaria uma mancha de sombra redonda exatamente sobre o bicho escondido;
- o que o jogador vê: poeira, pedras saltando e um tremor.

---

## 5. Efeitos e tremor de tela

Tudo em `fauna/effect/FaunaVisualEffects` passa pelo orçamento de partícula de **servidor**, no canal
próprio `FCServerParticles.Channel.FAUNA` (`particles.faunaDensity`, padrão 100).

Canal próprio porque escala com outra coisa: partícula de combate cresce com o tamanho da batalha;
esta cresce com o quão perto um jogador está de **um** animal. Baixar o custo de uma guerra não pode
apagar o Ambull que o jogador atravessou um deserto para encontrar.

**A poeira usa o bloco do chão**, não uma cor fixa: o mesmo bicho emerge em cinza de Armageddon, em
crosta de sal e em cascalho de Macragge, e uma cor fixa de areia estaria errada em dois dos três.

O tremor entra por `ViewportEvent.ComputeCameraAngles` (`FaunaTremorClient`) — puramente visual, a
rotação real do jogador não muda. As duas alternativas óbvias não servem: mexer em `setYRot` briga
com o mouse, e empurrar com `setDeltaMovement` vira teleporte quando o servidor corrige.

Magnitude **já atenuada pela distância** viaja no pacote; o cliente não recalcula nada. Dois eventos
próximos não somam — o maior ganha.

---

## 6. Os 13 sítios de worldgen

Uma `Feature` em Java (`FaunaSiteFeature`, seis formas), treze sítios em datapack. Dono dos arquivos:
`tools/generate_fauna_sites.py`.

**Feature e não Structure**, de propósito: os sítios são pequenos (raio 5 a 9), não têm salas nem
peças que precisem casar, e não precisam de `/locate`. Uma `Structure` custaria referência salva por
chunk e um `structure_set` por sítio, em troca de nada visível.

**Roda uma vez, na geração do chunk, e nunca mais.** Não há tick, não há varredura, não há "verificar
se já gerei aqui" — a própria geração de chunk é o registro de que já foi feito.

| forma | sítios |
|---|---|
| `pen` | Grox Ranch, Squig Pen, Imperial Kennel, Kroot Knarloc Pen |
| `burrow` | Ambull Burrow |
| `den` | Cudbear Den, Fenrisian Wolf Den |
| `nest` | Duneskuttler Nest, Constrictor Nest, Catachan Devil Nest |
| `clearing` | Barking Toad Clearing, Duskhorn Herd Area |
| `camp` | Ash Nomad Helamite Post |

Os moradores nascem com `markFromStructure()`: persistentes, fora do despawn. O Ambull do Ambull
Burrow tem de estar lá quando o jogador voltar — uma estrutura que conta uma história que o mundo
desmente é pior do que nenhuma estrutura.

### O chão, e o erro que custou meia sessão

`surfaceAt` **não** pode usar `getHeight(WORLD_SURFACE_WG)` direto: uma folha de carvalho conta, e
numa floresta isso dá uma altura dez blocos acima do solo. Medido: **92% de recusa em terreno seco e
plano**, o que tornaria a toca do Cudbear quase impossível justamente nos biomas de floresta que são
os dela — e os ossos seriam colocados em cima da copa. Depois de a sonda descer atravessando tronco,
folha e neve: **47–53% de aceitação**.

Trocar para `OCEAN_FLOOR_WG` não resolve: o predicado dele é `blocksMotion()`, e tronco e folha
bloqueiam movimento. Ele resolve água, não vegetação.

---

## 7. Onde a raridade realmente vive

**Não está em Java.** Peso de spawn, tamanho de grupo e probabilidade por chunk são **datapack**:

- fauna natural → lista `spawners` de cada bioma, em `tools/generate_biomes.py`;
- sítios → `rarity_filter` do placed feature, em `tools/generate_fauna_sites.py`.

`FaunaSpawnRules` só responde "este bloco serve?" quando o peso já sorteou a espécie. Procurar
raridade em Java é o erro que faz alguém passar uma tarde ajustando código sem nada mudar no mundo.

### Ownership da lista de features do bioma

| etapa | dono |
|---|---|
| 1 (lakes), 9 (vegetal_decoration) | `generate_worldgen_features.py` |
| 4 (surface_structures) | `generate_fauna_sites.py` |
| o resto | `generate_biomes.py` |

A etapa 4 entrou em `FOREIGN_STAGES` de `generate_biomes.py` **antes** de custar uma tarde: sem ela,
rodar esse script depois apagaria os treze sítios, e o sintoma seria "os bichos aparecem mas nunca há
uma toca".

---

## 8. Sons

São sons **do vanilla**, e isso é escolha declarada, não omissão. O dono entregou modelos, texturas e
animações; não entregou áudio. Registrar `SoundEvent` próprios apontando para arquivos inexistentes
daria fauna **muda** com um log de asset faltando por som.

Um Grox com voz de Ravager é um Grox; um Grox com `SoundEvent` vazio é um bug.

Quando houver áudio próprio, `FaunaSoundEvents` é o único arquivo que muda — as entidades pedem
`FaunaSoundEvents.ambient(...)`, nunca `SoundEvents.X` direto.

---

## 9. Comando de teste

```
/fauna list                      as 15 espécies
/fauna count                     quantas de cada existem no mundo carregado
/fauna spawn <espécie> [1..32]   cria à frente de quem chamou
```

Nível 2 de permissão. Não imprime nada sozinho.

---

## 10. O que foi verificado, e o que não foi

**Verificado em servidor dedicado** (mundo novo, RCON):

- os 13 `configured_feature` passam pelo codec — os registries carregam sem erro;
- as 15 espécies instanciam e sobrevivem (`/fauna spawn` × 15, `/fauna count` = 30);
- os 13 sítios colocam, e o número de moradores bate **exatamente** com as faixas declaradas
  (curral 3–8, matilha 2–5, apex 1…);
- as seis formas escrevem geometria: curral com 40 postes + cocho + 53 de chão batido; acampamento
  com tenda, barril e fogueira; toca com poço de 10 blocos; boca de toca com dois troncos;
- a guarda de terreno aceita ~50% das posições em terreno vanilla variado.

**NÃO verificado** (exige cliente conectado, e é o maior risco):

- se os modelos, texturas e animações aparecem corretos no jogo;
- se as hitboxes batem com os corpos e se os pés encostam no chão — as dimensões vieram da
  geometria medida do `.bbmodel` (mínimo em Y = 0 em todos os 14), mas medida não é visto;
- o tremor de tela, a camuflagem escurecida e o corte de renderização do enterrado;
- qualquer partícula: sem jogador conectado `anyPlayerWithin` corta antes de tudo.
