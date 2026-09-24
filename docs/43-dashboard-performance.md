# Auditoria de custo da Visão Geral do Dashboard (22/09/2026)

## Escopo e caminho de execução

A tela `PaginaDashboard` usa uma consulta TanStack Query por combinação de filtros. A abertura
faz um `GET /api/v1/dashboard/visao-geral`, sem polling configurado. O controller chama
`ObterVisaoGeralDashboardUseCase`, que valida o período e consulta o read model
`DashboardVisaoGeralRepositorioJdbc`; as vendas vêm da projeção
`AgregacaoDeVendasRepositorioJdbc`. Não há listagem da inbox, entidade de atendimento completa,
chamada por card ou N+1 no frontend. Alterar o ano, meses, intervalo diário ou coorte faz nova
requisição, como antes.

As consultas mais amplas por estrutura são as agregações de vendas com `DISTINCT ON` em
`evento_timeline`, a resolução por IA com `NOT EXISTS` correlacionado por atendimento e o
agrupamento de mensagens por hora. Sem `pg_stat_statements` em produção ou massa representativa
no banco de teste, esta é uma classificação de risco, não um ranking medido de CPU. Nenhum índice
foi adicionado sem plano e cardinalidade representativos.

## Baseline local, antes da alteração

No `origin/main` em `2e636cd`, o caminho do GET executava 23 leituras SQL: 18 para agregados
do período e 5 para o status ao vivo. A contagem vem dos pontos de execução do read model e
da projeção de vendas. Duas contagens de `lead.criado_em` repetiam as mesmas janelas usadas em
`novosLeads`; com coorte, a mesma coorte era contada duas vezes. Os estados `EM_IA` e
`EM_ATENDIMENTO` faziam duas leituras separadas da tabela `atendimento`.

O teste `DashboardCustoIT` exercitou o GET autenticado no PostgreSQL 15 e Redis 7 de
Testcontainers, com o seed de desenvolvimento e o filtro `ano=2040&meses=8`. Duas execuções
consecutivas marcaram 437 ms e 248 ms em uma JVM; uma segunda execução em nova JVM marcou
916 ms e 413 ms. Esses tempos incluem HTTP, autenticação e aquecimento da JVM e variam muito:
não representam latência de produção nem permitem atribuir ganho percentual. A instrumentação
inicial do `JdbcTemplate` contou 102 e depois 51 chamadas de métodos sobrecarregados por
requisição; **esses números não são consultas SQL** e foram descartados como medida de queries.
O número de SQL acima é a contagem dos pontos de execução da implementação.

## Alteração e estratégia de cache

O read model reaproveita `novosLeads` para o denominador da conversão, calcula a coorte uma
vez e conta os dois status de atendimento em uma leitura agregada. Em uma leitura fria, são
esperadas 20 consultas SQL. Na leitura equivalente com cache aquecido, os agregados não são
consultados; as quatro leituras do `statusAoVivo` continuam no PostgreSQL.

No teste HTTP com o mesmo seed e filtro do baseline, um `JdbcTemplate` observado no ponto de
execução SQL contou 20 consultas na leitura fria, 4 na segunda leitura e 20 após expirar a
entrada Redis. A consulta com coorte executou 21. Uma execução local registrou, nessa ordem,
278 ms, 152 ms e 215 ms; outra registrou 126 ms e 40 ms nas duas primeiras leituras. A
variação de JVM/Testcontainers é alta: a redução de consultas é reprodutível, mas esses tempos
não fundamentam percentual de ganho de latência ou CPU. A execução concorrente de dois GETs
equivalentes contou 24 consultas no total (um cálculo completo e duas leituras ao vivo).

O Redis existente armazena o resultado dos agregados por `DASHBOARD_CACHE_TTL` (padrão
`30s`). A chave usa hash de usuário autenticado, autoridades, intervalos do período, coorte e
fuso. O caso de uso mantém `@PreAuthorize` antes do acesso ao cache; atendente recebe 403.
O status ao vivo é substituído por uma consulta nova em cada hit. Erros de banco não são gravados;
falha de Redis degrada para leitura normal do banco. A resposta JSON possui teto de
`DASHBOARD_CACHE_MAX_BYTES` (padrão `131072`) por entrada; respostas maiores são servidas,
mas não cacheadas. Cada chave expira sozinha, sem limpeza de entradas históricas no boot.

Uma reserva Redis com prazo impede que réplicas calculem ao mesmo tempo a mesma entrada fria.
Quem perde a reserva espera até `DASHBOARD_CACHE_WAIT` (padrão `1s`) pelo valor; se a
consulta líder falhar, expirar ou demorar mais, a requisição lê diretamente o banco. A reserva
é liberada apenas pelo seu dono. O caminho de mensagem não publica invalidação de cache:
comportamento histórico pode ficar até 30 segundos atrasado, preservando o isolamento do pool
do chat e evitando escrita Redis por mensagem. A faixa ao vivo não tem essa janela.

## Reproduzir e interpretar

Rode `cd backend && ./mvnw clean verify -Dmaven.compiler.release=21`. Para o teste focado,
use `./mvnw -pl crm-app -am test-compile failsafe:integration-test failsafe:verify
-Dit.test=DashboardCustoIT -Dfailsafe.failIfNoSpecifiedTests=false`. O teste mede duas
chamadas HTTP idênticas, com Docker/Testcontainers. Compare a quantidade de leituras SQL
contadas pelo teste, mas interprete a latência apenas como amostra local em base pequena.

Não foi executado `EXPLAIN ANALYZE` nem carga nas instâncias `matriz_hml` ou `fmnaprod`.
`pg_stat_statements` continua dependente de janela operacional e restart do PostgreSQL;
quando estiver ativo, use `calls`, `total_exec_time`, `mean_exec_time` e `rows` por queryid
em uma janela comparável para priorizar qualquer otimização adicional de SQL.

## Medição da série mensal na PR #207 (24/09/2026)

Antes da série, o mesmo `DashboardCustoIT` mediu 20 leituras SQL frias e 4 quentes
(amostras de 120 ms e 41 ms, respectivamente). Após agrupar totais e meses na mesma
leitura por `ROLLUP` e consolidar ranking/série de vendas, o teste do GET real voltou a
**20 frias, 4 quentes e 20 após expiração**. Nesta execução as amostras foram 117 ms,
28 ms e 106 ms; a variação de JVM e seed impede afirmar ganho de latência ou CPU.
Uma coorte explícita usa 21 consultas, como antes. O teste concorrente mantém um só
cálculo frio para duas requisições equivalentes (24 consultas ao todo, incluindo os
dois status ao vivo).

`ROLLUP` e o agrupamento mensal podem consumir mais CPU dentro de cada SQL mesmo sem
aumentar a quantidade de comandos. O seed local possui poucos registros; nenhuma
medição de cardinalidade representativa ou `EXPLAIN (ANALYZE, BUFFERS)` em base
equivalente à operação foi obtida. Portanto, a paridade P1 permanece em draft e não
está autorizada para deploy com base somente nesses números. Não foi proposto índice
nem migration sem evidência de plano e impacto na escrita.
