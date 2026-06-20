# Small Planet — terreno do "planeta" (Fase E, em iterações)

Objetivo: overworld **natural** (com biomas/árvores) porém **baixo, sem montanhas
grandes e sem cavernas**, fechado em 5000 blocos, sem Nether/End.

## O que o MOD já faz sozinho (qualquer mundo, sem risco)
- **Worldborder 5000** no overworld a cada início de servidor.
- **Nether e The End selados** (viagem cancelada; portais não levam a nada).

## O que este datapack faz (SÓ em MUNDO NOVO — é geração de chunk)
**v1 (atual) — testar primeiro:**
- **Altura reduzida**: overworld `min_y 0, height 128` (menos subsolo).
- **Cavernas escavadas e ravinas desligadas**: os carvers `cave`,
  `cave_extra_underground` e `canyon` ficam com `probability 0`.

> ⚠️ Ainda **faltam** (próxima iteração, mexe no `noise_settings`): as grandes
> **cavernas de ruído** (as cavernas abertas estilo 1.18) e o **achatamento das
> montanhas**. Isso é o passo mais delicado e será feito depois que a v1 for
> confirmada funcionando.

## Como testar (mundo NOVO)
1. Crie um mundo novo (tipo **Default/Padrão**, para manter terreno natural).
2. Em **Datapacks**, arraste a pasta `small_planet` para "selecionados".
   (Ou crie o mundo e copie `small_planet` para `<mundo>/datapacks/`.)
3. Entre no mundo. Confira:
   - `/datapack list` → deve listar `file/small_planet`.
   - Cave/ravina: cavar não deve achar cavernas escavadas/ravinas (as de ruído
     ainda podem aparecer — v2).
   - Altura: teto em y=128, subsolo raso.
4. **Se der erro na criação** (datapack não carrega), me mande a mensagem exata
   do log/tela — eu corrijo o JSON. Não corrompe nada: é mundo novo.

## Roadmap deste datapack
- [x] v1: altura baixa + carvers (cavernas escavadas/ravinas) off.
- [ ] v2: `noise_settings` custom → desligar cavernas de ruído + achatar
      montanhas (terreno suave). Iterativo, testado por você.

## Ajustes rápidos
- Altura: `data/minecraft/dimension_type/overworld.json` (`height`/`min_y`,
  múltiplos de 16).
