# Small Planet — terreno do "planeta" (altura reduzida do overworld)

Parte da visão de "planetas pequenos/fechados". O que **o mod já faz sozinho**
(qualquer mundo, sem risco):
- **Worldborder 5000** no overworld a cada início de servidor.
- **Nether e The End selados**: viajar para eles é cancelado (portais não levam
  a lugar nenhum). A Cruzada acontece só no mundo de superfície.

Este datapack cobre a parte que **só dá para mudar em mundo novo** (geração):
- **Altura reduzida**: overworld de `min_y 0, height 128` (antes −64 a 320) →
  menos subsolo, mundo mais raso.

## ⚠️ Só em MUNDO NOVO
Altura/geração afetam os chunks. Aplicar num mundo já gerado pode corromper/crashar.
Por isso este datapack **não** vem embutido no mod — use só num mundo novo.

## Como ter "sem cavernas, sem montanhas, plano e baixo" (recomendado)
A forma **confiável** (sem worldgen frágil escrito à mão) é criar o mundo como
**Superflat (Mundo Plano)**:
1. Criar Mundo → Mais → **Tipo de Mundo: Superflat**. Superflat já não tem
   **cavernas nem montanhas** e é plano.
2. Em **Datapacks**, adicionar a pasta `small_planet` (para baixar o teto/altura).
3. O mod cuida do resto (border 5000, Nether/End selados).

Assim você obtém exatamente: plano, sem cavernas, sem montanhas, baixo, sem
Nether/End, e fechado em 5000 blocos — sem geração frágil e sem quebrar nada.

## E se eu quiser terreno NATURAL (com biomas/árvores) mas baixo, sem montanhas e
sem cavernas?
Isso exige um `noise_settings` próprio do overworld (achatado + cavernas
desligadas) — é o trabalho maior e mais delicado da Fase E, que precisa ser
escrito e **testado em mundo novo** com cuidado. Dá para fazer numa próxima
etapa; me avise que eu monto e a gente testa por partes.

## Ajustes
- Altura: edite `height`/`logical_height`/`min_y` em
  `data/minecraft/dimension_type/overworld.json` (múltiplos de 16).
