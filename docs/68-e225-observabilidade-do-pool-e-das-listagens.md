# 68. E225 (PR 5) — observabilidade do pool de conexões e das listagens do painel

Só log. Nada muda de comportamento, de consulta, de pool nem de contrato. Os números do pool e a duração das listagens
passam a aparecer no log do backend para dizer, na próxima manhã de pico, **quem segurava as conexões e qual listagem passou
de 1 s** — hoje só se sabe que o pool encheu.

## O que aparece no log

| Marcador | Nível | Quando | Campos |
|---|---|---|---|
| `[METRICA_POOL_CONEXOES]` | INFO | 1 por pool a cada `POOL_METRICAS_INTERVALO` (1 min) | `pool`, `ativas`, `ociosas`, `esperando`, `total`, `maximo` |
| `[ALERTA_POOL_SATURADO]` | WARN | pool com 100% das conexões emprestadas por mais de `POOL_SATURADO_ALERTA_APOS` (5 s); repete no máximo 1× por limiar enquanto durar | `pool`, `ativas`, `maximo`, `esperando`, `saturadoHaSegundos`, `limiarSegundos` |
| `[POOL_SATURADO_FIM]` | INFO | quando o pool alivia, só se chegou a alertar | `pool`, `duracaoSegundos` |
| `[LISTAGEM_PAINEL_LENTA]` | WARN | listagem do painel/inbox com duração ≥ `PAINEL_LISTAGEM_LENTA_LIMITE` (1 s) | `papel`, `aba`, `pagina` (`lista-simples` ou `paginada limite=N cursor=sim/nao filtroAtendente=sim/nao`), `cartoes`, `duracaoMs`, `limiteMs`, `poolAtivas`, `poolOciosas`, `poolEsperando`, `poolMaximo` |

Sem dado pessoal: só nome do pool, papel, aba, tamanhos e números. Nenhum id de usuário, lead ou atendimento, nome ou texto.

## Variáveis (todas opcionais; declaradas no `docker/dokploy-stack.yml`)

| Variável | Padrão | O que controla |
|---|---|---|
| `POOL_METRICAS_INTERVALO` | `1m` | intervalo do log INFO por pool |
| `POOL_AMOSTRA_INTERVALO` | `1s` | de quanto em quanto tempo o pool é amostrado para detectar saturação |
| `POOL_SATURADO_ALERTA_APOS` | `5s` | por quanto tempo o pool precisa ficar 100% emprestado para gerar WARN |
| `PAINEL_LISTAGEM_LENTA_LIMITE` | `1s` | duração a partir da qual uma listagem do painel gera WARN |

Mudar exige reiniciar o backend. O agendamento obedece ao mesmo interruptor dos demais (`synapse.agendamento.habilitado`).

## O que a duração da listagem mede

A duração é a da **execução da consulta**, já com a conexão em mãos (a transação do caso de uso abre antes do código medido). A
**espera por conexão** não está nela: aparece em `poolEsperando` no mesmo log e na linha por minuto do pool. Uma listagem lenta
com `poolEsperando` alto no mesmo instante indica contenção de pool; lenta com pool folgado indica a consulta.

## Como usar na manhã de pico

```bash
docker service logs <stack>_backend --since 3h 2>&1 | grep -E "METRICA_POOL_CONEXOES pool=synapse-chat"
docker service logs <stack>_backend --since 3h 2>&1 | grep -E "ALERTA_POOL_SATURADO|LISTAGEM_PAINEL_LENTA"
```

Cruzar `ALERTA_POOL_SATURADO` com `LISTAGEM_PAINEL_LENTA` (mesmo minuto) mostra se foram as listagens, e de qual aba e papel, que
seguraram as conexões.

## Testes

`ObservabilidadeDosPoolsDeConexaoTest` (9): linha por pool, pool que ainda não subiu, não alerta antes do limiar, alerta uma
vez com os campos, repetição no máximo 1× por limiar, fim com duração e reinício da contagem, saturação curta sem fim,
pools independentes, limiar inválido. `ListarAtendimentosVisiveisUseCaseTest` (+4): WARN com papel/aba/forma/duração/pool e sem
o id do usuário, listagem rápida não grava, pool indisponível, falha da consulta passa sem WARN. `ObservabilidadeDosPoolsIT`: lê o
Hikari real (nome e máximo) e loga os dois pools.
