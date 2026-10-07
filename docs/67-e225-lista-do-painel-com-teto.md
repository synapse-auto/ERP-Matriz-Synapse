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

## O corte não é silencioso

A fase 1 busca `teto + 1` linhas: se a `+1` existe, a lista foi cortada. Nesse caso:

- a resposta continua um **array** (o contrato do corpo não muda) e traz os cabeçalhos **`X-Lista-Truncada: true`** e
  **`X-Lista-Teto: N`** (expostos no CORS; sem corte, os cabeçalhos não aparecem);
- o backend grava um **WARN** `[LISTAGEM_PAINEL_TRUNCADA] papel=… aba=… teto=…` (sem id nem dado pessoal), para alarme/contagem de
  logs.

Testes: `ListarAtendimentosVisiveisUseCaseTest` (WARN só quando trunca, sem o id do usuário), `ListaLegadaLimitadaIT` (cabeçalhos
presentes quando passa do teto) e `ListaLegadaSemCorteIT` (teto folgado: lista idêntica à antiga).

## Quem chama e o que o frontend faz

O único chamador é `frontend/src/lib/atendimento/api.ts` (`GET /api/v1/atendimentos?visao=`): as abas PENDENTES/POTENCIAIS e os
três diálogos (via `useAtendimentosParaEscolha`). **O frontend ainda não lê os cabeçalhos**: acima do teto, essas listas mostram só os
N primeiros, sem "carregar mais" nem aviso, e o badge da aba (contagem sem teto) passa a divergir da lista. O sinal existe para o
próximo PR (faixa de aviso na UI, ou migrar as abas para a inbox paginada e os diálogos para busca no servidor).

## Decisão do responsável e risco

O valor do teto (500) é ponto de partida: hoje há ~373 atendimentos ativos no HML, então não corta nada. Se o volume crescer, o corte
agora é **visível** (cabeçalho + WARN), mas a UI ainda não o mostra ao usuário.
