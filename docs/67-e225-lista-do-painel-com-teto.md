# 67. E225 — lista do painel (`GET /api/v1/atendimentos?visao=`) com teto

Antes, esse endpoint devolvia **todos** os cartões da visão, sem `LIMIT`. Quem o usa no frontend: as abas PENDENTES e POTENCIAIS
(ainda na lista simples) e três diálogos (encaminhar, lembrete, mensagem programada) que listam TODOS inteiro. O custo cresce com
o número de atendimentos abertos (a fase 2 monta o cartão de cada um) e se repete a cada recarga.

## A mudança

A primeira fase da consulta (as ids escolhidas) ganha a **mesma ordem** da paginação e um `LIMIT`; a fase 2 monta só esses
cartões. O teto é `synapse.painel.listagem-maxima`, variável **`PAINEL_LISTAGEM_MAXIMA`** (padrão **500**; declarada no
`docker/dokploy-stack.yml`; mudar exige reiniciar o backend; valor menor que 1 recusa a subida).

- Com o total **menor ou igual** ao teto, o resultado é **idêntico** ao de antes (mesmas linhas, mesma ordem).
- Com o total **maior**, devolve os **N primeiros**, na mesma ordem: o que fica de fora é o mais antigo (menor atividade).
- Quem precisa de mais usa a inbox paginada (`/api/v1/atendimentos/inbox`).

## Teste

`ListaLegadaLimitadaIT` (teto 3) compara o endpoint com a consulta **antiga** (sem `LIMIT`, recomposta por reflexão com os mesmos
blocos de texto) sob a RLS real: POTENCIAIS com mais de 3 cartões devolve o **prefixo de 3** da lista antiga, na mesma ordem; ATIVOS
da Ana (2 cartões, cabem) devolve **exatamente** a lista antiga; teto inválido é recusado. Validado contra uma mutação deliberada
da ordem (falhou).

## Decisão do responsável e risco

O valor do teto (500) é ponto de partida: hoje há ~373 atendimentos ativos no HML, então não corta nada, mas o recorte passa a ser
silencioso se o volume crescer (a lista não avisa que foi cortada). Se isso for um problema para os diálogos (lista de leads para
escolher), o caminho é trocá-los por busca/paginação no servidor, em outro PR.
