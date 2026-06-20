# Small Planet — terreno do "planeta" (Fase E, em iterações)

Objetivo: overworld **baixo, plano (sem montanhas), sem cavernas, sem vilas**,
fechado em 5000 blocos, sem Nether/End.

## O que o MOD já faz sozinho (qualquer mundo)
- **Worldborder 5000** no overworld.
- **Nether e The End selados** (viagem cancelada).

## O que este datapack faz (SÓ em MUNDO NOVO)
**v2 (atual):**
- **Terreno plano** + **sem cavernas de ruído**: `noise_settings` do overworld
  reescrito (densidade final = gradiente → solo sólido até ~y65, ar acima; sem
  funções de caverna). Mantém **biomas/árvores/água** (referencia o clima vanilla).
- **Altura reduzida**: `dimension_type` min_y 0, height 128.
- **Cavernas escavadas/ravinas off**: carvers com `probability 0`.

## Como testar (MUNDO NOVO) — importante
1. Criar Mundo → **Mais**:
   - **Tipo de Mundo: Default/Padrão** (mantém biomas).
   - **Gerar Estruturas: DESLIGADO** ← isso remove **vilas** e todas as estruturas
     (jeito infalível; não depende do datapack).
2. Em **Datapacks**, adicione a pasta `small_planet`.
3. Entre no mundo e confira:
   - `/datapack list` → deve listar `file/small_planet` (se NÃO listar, o datapack
     não foi aplicado — foi por isso que a v1 parecia não funcionar).
   - Terreno plano, sem montanhas; cavar não acha cavernas; sem vilas.

## ⚠️ Esta parte é experimental (não consigo testar worldgen aqui)
O `noise_settings` foi escrito à mão. **Se der erro na criação do mundo** (ou ficar
estranho), me mande a mensagem do log/tela **exata** — eu corrijo rápido. É mundo
novo, então erro não corrompe nada.

Resultado esperado: um "planeta" plano e natural (grama/terra, biomas, árvores,
lagos), raso, sem cavernas/montanhas/vilas. Se quiser **leves ondulações** em vez
de totalmente plano, dá para adicionar um ruído suave na densidade (v3).

## Roadmap deste datapack
- [x] v1: altura baixa + carvers (cavernas escavadas/ravinas) off.
- [x] v2: noise_settings plano (sem montanhas, sem cavernas de ruído) + instruções
      p/ desligar estruturas (vilas) na criação.
- [ ] v3 (opcional): leves ondulações no terreno; superfícies por bioma (areia no
      deserto, etc.) se a superfície simplificada ficar monótona.

## Ajustes
- Altura da "linha do solo": `from_y`/`to_y` em `noise_settings/overworld.json`
  (`final_density`/`initial_density_without_jaggedness`).
- Teto/fundo: `dimension_type/overworld.json` (`height`/`min_y`).
