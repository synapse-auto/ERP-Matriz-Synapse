# 64. E224 (B1) — fase 2 do painel guiada pelas ids escolhidas

Mudança de **consulta**, sem mudança de resultado, de política de RLS, de migration nem de contrato HTTP. **Ganho de
desempenho não está declarado**: ele só vale com o número do EXPLAIN antes/depois que o responsável roda na VPS (abaixo).

## O problema (medido pelo responsável no EXPLAIN de 06/10, HML)

A listagem do painel (`PainelDeAtendimentosRepositorioJdbc.listarPaginado`, também usada pela inbox unificada) tem duas
fases no mesmo comando (E209): a fase 1 escolhe as ≤101 ids de atendimento que representam cada lead; a fase 2 monta o
cartão completo só para essas ids. A fase 2 era escrita como `WHERE a.id IN (<fase 1 com LIMIT>)`.

No plano real, esse `IN` foi planejado como **Nested Loop Semi Join dirigido pelas ~3.915 linhas** de `atendimento` já
unidas, e só depois filtrado pelas 101 ids: o `Nested Loop Left Join` externo tinha 3.915 linhas e as `LATERAL` **`ativo`**
e **`ultima`** rodaram com `loops=3915`, em vez de ~101. Ou seja, o custo do cartão (que inclui o `max(enviado_em)` por
atendimento aberto e a última mensagem, ambos atravessando as partições de `mensagem`) era pago para o histórico inteiro
da visão, não para a página.

## A mudança

`cartoesDe` agora põe as ids escolhidas como **item do `FROM`**, com `JOIN` explícito a `atendimento`:

```sql
SELECT <colunas> FROM (
  SELECT <campos do cartão>
  FROM (<fase 1: SELECT atendimento_id ... ORDER BY ... LIMIT ?>) escolhidos
  JOIN atendimento a ON a.id = escolhidos.atendimento_id
  JOIN lead l ON l.id = a.lead_id ...   -- os mesmos joins de antes
) cartoes
WHERE linha_do_lead = 1
ORDER BY <mesma ordem>
```

- A subconsulta com `LIMIT` não é achatada pelo planejador: é uma relação de no máximo `LIMIT` linhas, e o resto do
  cartão é consultado por id a partir dela.
- Os blocos de texto dos joins (`JUNCOES_DO_CARTAO`) são os mesmos de `ORIGEM`, compartilhados: a busca pontual
  (`porAtendimentoId`/`porLeadId`) continua partindo de `FROM atendimento a` e o texto dos joins não diverge.
- A ordem dos `?` no texto e a lógica de cursor/ordem/`LIMIT` da fase 1 não mudaram (só foram extraídas para
  `escolherPagina`, para o teste usar exatamente a mesma escolha).
- **Não toca RLS**: `atendimento` e `lead` são lidos pelas mesmas tabelas, sob o mesmo contexto
  (`SET LOCAL ROLE synapse_app` + `app.papel` + `app.usuario_id`), no mesmo snapshot das duas fases.

## Prova de equivalência

`PainelFase2EquivalenciaIT` recompõe a consulta **antiga** (mesmos blocos de texto, `a.id IN (...)`) e a compara com a
nova, sob a RLS real, comparando **todas as colunas** do cartão, na mesma ordem (inclui `linha_do_lead`):

- seis abas (ATIVOS, PENDENTES, POTENCIAIS, TODOS, FINALIZADOS; PENDENTES nas duas formas, atendente e gestão);
- papéis **ATENDENTE** (RLS completa: dono, `EM_IA`, `FINALIZADO`, participante ativo e convite pendente) e **GESTOR**;
- página do tamanho do painel (101) e **todas as páginas de 2 com cursor**, sem atendimento repetido entre páginas;
- FINALIZADOS com filtro por atendente (gestão);
- cenário com leads de vários ciclos, mensagens sem resposta, empate de última mensagem (desempate por id) e atendimento
  sem mensagem (`ultima_mensagem_em` nulo).

O teste foi verificado contra uma mutação deliberada da consulta nova (descartar atendimentos `EM_IA`): o IT falhou nos dois
papéis. Um teste unitário (`PainelDeAtendimentosRepositorioJdbcTest`) fixa a forma do SQL (sem `a.id IN (`, com o `JOIN`
às `escolhidos`) e que o número de `?` bate com os argumentos em todas as combinações de aba, papel e cursor.

## Como medir (o que falta para declarar ganho)

Scripts no repositório, gerados do mesmo código: `docker/operacoes/e224-explain-painel-antes.sql` (consulta anterior) e
`docker/operacoes/e224-explain-painel-depois.sql` (esta mudança). Cada um tem, por aba, uma medição **literal** (plano
customizado) e uma **genérica** (`PREPARE` + `SET LOCAL plan_cache_mode = force_generic_plan`, o plano que o driver JDBC
pode passar a usar depois de 5 execuções).

```bash
psql -d matriz_hml -v papel=ATENDENTE -v usuario=<uuid> -v limite=101 -f docker/operacoes/e224-explain-painel-antes.sql
psql -d matriz_hml -v papel=ATENDENTE -v usuario=<uuid> -v limite=101 -f docker/operacoes/e224-explain-painel-depois.sql
```

O que conferir no plano da fase 2: a junção externa com **~101 linhas** e as `LATERAL` `ativo` e `ultima` com `loops` ≈
número de ids escolhidas (≤101), e não ≈3.915. Linha de base informada pelo responsável (sem JIT, ATENDENTE, página 101):
ATIVOS 633 ms, PENDENTES 676 (atendente) / 532 (gestão), POTENCIAIS 578, TODOS 616, FINALIZADOS 1.414, contagem 390.

## O que esta mudança não resolve

A **fase 1** continua calculando a `LATERAL` da última mensagem e o `EXISTS` de atendimento aberto para **todo** atendimento
da visão antes do `ROW_NUMBER` e do `LIMIT`; em FINALIZADOS/TODOS isso cresce com o histórico. Se o EXPLAIN "depois"
mostrar que o custo restante está ali, é outro item (por exemplo índice parcial em `atendimento` por status aberto ou
coluna desnormalizada de última mensagem), a decidir com número.
