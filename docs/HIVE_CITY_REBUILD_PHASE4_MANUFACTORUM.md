# Hive City Rebuild — Fase 4: Manufactorum V2

Esta fase substitui os três módulos industriais antigos por um distrito contínuo de 192 x 64 x 64 blocos, posteriormente dividido nos mesmos IDs já utilizados pelo sistema:

- `industrial/foundry_01`
- `industrial/assembly_hall_01`
- `industrial/generator_hall_01`

## Direção visual

O conjunto foi remodelado para evitar a aparência de três caixas iguais. A nova silhueta possui massas escalonadas, coberturas grossas, anexos deslocados, torres de máquinas, chaminés com alturas diferentes, contrafortes profundos, galerias contínuas e infraestrutura atravessando as emendas.

## Fundição

- Corpo principal chanfrado e anexos assimétricos.
- Duas torres de fornalha integradas ao telhado.
- Cinco grupos de chaminés com alturas variadas.
- Fornalhas, cadinhos, calha de metal, ponte rolante e sala de controle.
- Rede de tubulações e ventilação nas fachadas.

## Nave de montagem

- Três grandes vãos de cobertura em vez de uma laje plana.
- Duas linhas de esteira, prensas e linha transversal.
- Pontes rolantes e peças suspensas.
- Galeria de supervisão, escada e trilho de acesso.
- Anexo destruído e assimétrico na lateral.

## Salão de geração

- Basílica industrial chanfrada com anexos de transformadores.
- Coroa superior larga em dois estágios.
- Quatro pináculos curtos e integrados.
- Reator central espesso com galerias em anel.
- Bancos de caldeiras, tanques de refrigeração e sala de controle.
- Cabos e tubulações em vários níveis.

## Conexões

- Galeria de processo contínua no térreo.
- Passarelas contínuas nos níveis Y=16 e Y=29.
- Arcos abertos nas emendas entre os três módulos.
- Canopy de serviço compartilhado no telhado.
- Três acessos norte/sul mantidos para compatibilidade com os sockets atuais.

## Teste

Execute:

```bat
gradlew runClient
```

No jogo, coloque o distrito:

```text
/fchive district place firstcrusade:manufactorum
```

Reserve uma área de pelo menos 192 x 64 x 64 blocos. Verifique passagem pelas entradas, escadas, passarelas, iluminação, rotação dos blocos e desempenho durante a colocação.
