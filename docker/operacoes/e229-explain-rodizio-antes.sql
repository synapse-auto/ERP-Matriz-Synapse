-- =====================================================================================================================
-- E229 (D3a/D3b) - EXPLAIN do rodizio da IA e do vinculo: antes.
-- ANTES: o rodizio de sempre (escopo GLOBAL). E, byte a byte, o que o caminho com todas as chaves desligadas continua executando.
-- =====================================================================================================================
-- VARIAVEIS EXIGIDAS (-v). Sem elas o psql para no primeiro bloco (ON_ERROR_STOP):
--   (este script NAO usa variaveis: sao as consultas SEM parametro, exatamente o que roda com a capacidade "setores" desligada)
--
-- COMANDO EXATO (um arquivo por vez, mesma janela). <container> = o container do Postgres
-- (descubra com:  docker ps --format '{{.Names}}' | grep -i postgres ):
--
--   docker exec -i <container> psql -X -q -U matriz_app -d matriz_hml \
--     < e229-explain-rodizio-antes.sql > saida-rodizio-antes.txt
--
-- Para extrair os tempos:   grep -E "^-- |Execution Time" saida-rodizio-antes.txt
--
-- O QUE E CADA BLOCO: por consulta ha medicoes N-literal e (quando ha parametro) N-generico (PREPARE + force_generic_plan),
-- cada uma com 3 REPETICOES na mesma transacao: compare o MENOR "Execution Time" (a primeira esquenta o cache e a VPS
-- tem CPU disputada). Cada bloco e BEGIN ... ROLLBACK: nada altera dados. EXPLAIN ANALYZE EXECUTA a consulta (teto de 60 s).
-- O contexto reproduz o que o rodizio usa: SET LOCAL ROLE synapse_app + app.papel = SERVICO (lead/atendimento tem RLS FORCE;
-- o rodizio roda como servico). O JIT ja esta desligado. Os textos SQL abaixo sao extraidos do codigo-fonte do commit.
\set ON_ERROR_STOP on
\pset pager off

-- =========================================================================================================
-- 01 MENOR CARGA - ANTES (GLOBAL)
-- =========================================================================================================
-- 01-literal (3 repeticoes na mesma transacao)
BEGIN;
SET LOCAL ROLE synapse_app;
SET LOCAL statement_timeout = '60s';
SELECT set_config('app.papel', 'SERVICO', TRUE), set_config('app.usuario_id', '', TRUE), set_config('app.contexto_agenda', '', TRUE);
-- repeticao 1
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
WITH carga AS (
    SELECT a.atendente_id,
           count(*) FILTER (WHERE a.status = 'EM_ATENDIMENTO') AS atendimentos_abertos
      FROM atendimento a
     WHERE a.atendente_id IS NOT NULL
     GROUP BY a.atendente_id
), recebimentos AS (
    SELECT atendente_id, max(recebido_em) AS ultimo_recebido_em
      FROM (
            SELECT a.atendente_id, a.iniciado_em AS recebido_em
              FROM atendimento a
             WHERE a.atendente_id IS NOT NULL
            UNION ALL
            SELECT (e.dados ->> 'paraAtendenteId')::uuid, e.criado_em
              FROM evento_timeline e
             WHERE e.tipo = 'ATENDIMENTO_TRANSFERIDO'
               AND e.dados ->> 'paraAtendenteId' IS NOT NULL
            UNION ALL
            SELECT e.ator_id, e.criado_em
              FROM evento_timeline e
             WHERE e.tipo = 'LEAD_TRANSFERIDO_POR_ENVIO'
               AND e.ator_id IS NOT NULL
      ) recebimento
     GROUP BY atendente_id
)
SELECT u.id, u.nome, u.email
  FROM disponibilidade_atendente_ia d
  JOIN usuario u ON u.id = d.atendente_id
  LEFT JOIN carga c ON c.atendente_id = u.id
  LEFT JOIN recebimentos r ON r.atendente_id = u.id
 WHERE d.disponivel_para_ia = TRUE AND u.ativo = TRUE
   -- Espelha PapelUsuario.recebeAtendimento()
   AND u.papel IN ('ATENDENTE', 'SUBGESTOR') AND u.status_presenca = 'ONLINE'
 ORDER BY COALESCE(c.atendimentos_abertos, 0),
          r.ultimo_recebido_em NULLS FIRST,
          u.id;
-- repeticao 2
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
WITH carga AS (
    SELECT a.atendente_id,
           count(*) FILTER (WHERE a.status = 'EM_ATENDIMENTO') AS atendimentos_abertos
      FROM atendimento a
     WHERE a.atendente_id IS NOT NULL
     GROUP BY a.atendente_id
), recebimentos AS (
    SELECT atendente_id, max(recebido_em) AS ultimo_recebido_em
      FROM (
            SELECT a.atendente_id, a.iniciado_em AS recebido_em
              FROM atendimento a
             WHERE a.atendente_id IS NOT NULL
            UNION ALL
            SELECT (e.dados ->> 'paraAtendenteId')::uuid, e.criado_em
              FROM evento_timeline e
             WHERE e.tipo = 'ATENDIMENTO_TRANSFERIDO'
               AND e.dados ->> 'paraAtendenteId' IS NOT NULL
            UNION ALL
            SELECT e.ator_id, e.criado_em
              FROM evento_timeline e
             WHERE e.tipo = 'LEAD_TRANSFERIDO_POR_ENVIO'
               AND e.ator_id IS NOT NULL
      ) recebimento
     GROUP BY atendente_id
)
SELECT u.id, u.nome, u.email
  FROM disponibilidade_atendente_ia d
  JOIN usuario u ON u.id = d.atendente_id
  LEFT JOIN carga c ON c.atendente_id = u.id
  LEFT JOIN recebimentos r ON r.atendente_id = u.id
 WHERE d.disponivel_para_ia = TRUE AND u.ativo = TRUE
   -- Espelha PapelUsuario.recebeAtendimento()
   AND u.papel IN ('ATENDENTE', 'SUBGESTOR') AND u.status_presenca = 'ONLINE'
 ORDER BY COALESCE(c.atendimentos_abertos, 0),
          r.ultimo_recebido_em NULLS FIRST,
          u.id;
-- repeticao 3
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
WITH carga AS (
    SELECT a.atendente_id,
           count(*) FILTER (WHERE a.status = 'EM_ATENDIMENTO') AS atendimentos_abertos
      FROM atendimento a
     WHERE a.atendente_id IS NOT NULL
     GROUP BY a.atendente_id
), recebimentos AS (
    SELECT atendente_id, max(recebido_em) AS ultimo_recebido_em
      FROM (
            SELECT a.atendente_id, a.iniciado_em AS recebido_em
              FROM atendimento a
             WHERE a.atendente_id IS NOT NULL
            UNION ALL
            SELECT (e.dados ->> 'paraAtendenteId')::uuid, e.criado_em
              FROM evento_timeline e
             WHERE e.tipo = 'ATENDIMENTO_TRANSFERIDO'
               AND e.dados ->> 'paraAtendenteId' IS NOT NULL
            UNION ALL
            SELECT e.ator_id, e.criado_em
              FROM evento_timeline e
             WHERE e.tipo = 'LEAD_TRANSFERIDO_POR_ENVIO'
               AND e.ator_id IS NOT NULL
      ) recebimento
     GROUP BY atendente_id
)
SELECT u.id, u.nome, u.email
  FROM disponibilidade_atendente_ia d
  JOIN usuario u ON u.id = d.atendente_id
  LEFT JOIN carga c ON c.atendente_id = u.id
  LEFT JOIN recebimentos r ON r.atendente_id = u.id
 WHERE d.disponivel_para_ia = TRUE AND u.ativo = TRUE
   -- Espelha PapelUsuario.recebeAtendimento()
   AND u.papel IN ('ATENDENTE', 'SUBGESTOR') AND u.status_presenca = 'ONLINE'
 ORDER BY COALESCE(c.atendimentos_abertos, 0),
          r.ultimo_recebido_em NULLS FIRST,
          u.id;
ROLLBACK;

-- =========================================================================================================
-- 02 SEQUENCIAL - ANTES (GLOBAL)
-- =========================================================================================================
-- 02-literal (3 repeticoes na mesma transacao)
BEGIN;
SET LOCAL ROLE synapse_app;
SET LOCAL statement_timeout = '60s';
SELECT set_config('app.papel', 'SERVICO', TRUE), set_config('app.usuario_id', '', TRUE), set_config('app.contexto_agenda', '', TRUE);
-- repeticao 1
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
WITH recebimentos AS (
    SELECT atendente_id, max(recebido_em) AS ultimo_recebido_em
      FROM (
            SELECT a.atendente_id, a.iniciado_em AS recebido_em
              FROM atendimento a
             WHERE a.atendente_id IS NOT NULL
            UNION ALL
            SELECT (e.dados ->> 'paraAtendenteId')::uuid, e.criado_em
              FROM evento_timeline e
             WHERE e.tipo = 'ATENDIMENTO_TRANSFERIDO'
               AND e.dados ->> 'paraAtendenteId' IS NOT NULL
            UNION ALL
            SELECT e.ator_id, e.criado_em
              FROM evento_timeline e
             WHERE e.tipo = 'LEAD_TRANSFERIDO_POR_ENVIO'
               AND e.ator_id IS NOT NULL
      ) recebimento
     GROUP BY atendente_id
)
SELECT u.id, u.nome, u.email
  FROM disponibilidade_atendente_ia d
  JOIN usuario u ON u.id = d.atendente_id
  LEFT JOIN recebimentos r ON r.atendente_id = u.id
 WHERE d.disponivel_para_ia = TRUE AND u.ativo = TRUE
   -- Espelha PapelUsuario.recebeAtendimento()
   AND u.papel IN ('ATENDENTE', 'SUBGESTOR') AND u.status_presenca = 'ONLINE'
 ORDER BY r.ultimo_recebido_em NULLS FIRST,
          u.id;
-- repeticao 2
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
WITH recebimentos AS (
    SELECT atendente_id, max(recebido_em) AS ultimo_recebido_em
      FROM (
            SELECT a.atendente_id, a.iniciado_em AS recebido_em
              FROM atendimento a
             WHERE a.atendente_id IS NOT NULL
            UNION ALL
            SELECT (e.dados ->> 'paraAtendenteId')::uuid, e.criado_em
              FROM evento_timeline e
             WHERE e.tipo = 'ATENDIMENTO_TRANSFERIDO'
               AND e.dados ->> 'paraAtendenteId' IS NOT NULL
            UNION ALL
            SELECT e.ator_id, e.criado_em
              FROM evento_timeline e
             WHERE e.tipo = 'LEAD_TRANSFERIDO_POR_ENVIO'
               AND e.ator_id IS NOT NULL
      ) recebimento
     GROUP BY atendente_id
)
SELECT u.id, u.nome, u.email
  FROM disponibilidade_atendente_ia d
  JOIN usuario u ON u.id = d.atendente_id
  LEFT JOIN recebimentos r ON r.atendente_id = u.id
 WHERE d.disponivel_para_ia = TRUE AND u.ativo = TRUE
   -- Espelha PapelUsuario.recebeAtendimento()
   AND u.papel IN ('ATENDENTE', 'SUBGESTOR') AND u.status_presenca = 'ONLINE'
 ORDER BY r.ultimo_recebido_em NULLS FIRST,
          u.id;
-- repeticao 3
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
WITH recebimentos AS (
    SELECT atendente_id, max(recebido_em) AS ultimo_recebido_em
      FROM (
            SELECT a.atendente_id, a.iniciado_em AS recebido_em
              FROM atendimento a
             WHERE a.atendente_id IS NOT NULL
            UNION ALL
            SELECT (e.dados ->> 'paraAtendenteId')::uuid, e.criado_em
              FROM evento_timeline e
             WHERE e.tipo = 'ATENDIMENTO_TRANSFERIDO'
               AND e.dados ->> 'paraAtendenteId' IS NOT NULL
            UNION ALL
            SELECT e.ator_id, e.criado_em
              FROM evento_timeline e
             WHERE e.tipo = 'LEAD_TRANSFERIDO_POR_ENVIO'
               AND e.ator_id IS NOT NULL
      ) recebimento
     GROUP BY atendente_id
)
SELECT u.id, u.nome, u.email
  FROM disponibilidade_atendente_ia d
  JOIN usuario u ON u.id = d.atendente_id
  LEFT JOIN recebimentos r ON r.atendente_id = u.id
 WHERE d.disponivel_para_ia = TRUE AND u.ativo = TRUE
   -- Espelha PapelUsuario.recebeAtendimento()
   AND u.papel IN ('ATENDENTE', 'SUBGESTOR') AND u.status_presenca = 'ONLINE'
 ORDER BY r.ultimo_recebido_em NULLS FIRST,
          u.id;
ROLLBACK;

-- =========================================================================================================
-- 03 FUNIL (diagnostico quando o rodizio fica vazio) - ANTES (GLOBAL)
-- =========================================================================================================
-- 03-literal (3 repeticoes na mesma transacao)
BEGIN;
SET LOCAL ROLE synapse_app;
SET LOCAL statement_timeout = '60s';
SELECT set_config('app.papel', 'SERVICO', TRUE), set_config('app.usuario_id', '', TRUE), set_config('app.contexto_agenda', '', TRUE);
-- repeticao 1
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT count(*) FILTER (WHERE u.ativo = TRUE) AS ativos,
       count(*) FILTER (WHERE u.ativo = TRUE
                          AND u.papel IN ('ATENDENTE', 'SUBGESTOR')) AS com_papel_permitido,
       count(*) FILTER (WHERE u.ativo = TRUE
                          AND u.papel IN ('ATENDENTE', 'SUBGESTOR')
                          AND d.disponivel_para_ia = TRUE) AS disponiveis_para_ia,
       count(*) FILTER (WHERE u.ativo = TRUE
                          AND u.papel IN ('ATENDENTE', 'SUBGESTOR')
                          AND d.disponivel_para_ia = TRUE
                          AND u.status_presenca = 'ONLINE') AS online,
       count(*) FILTER (WHERE u.ativo = TRUE
                          AND u.papel IN ('ATENDENTE', 'SUBGESTOR')
                          AND d.atendente_id IS NULL) AS sem_registro
  FROM usuario u
  LEFT JOIN disponibilidade_atendente_ia d ON d.atendente_id = u.id;
-- repeticao 2
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT count(*) FILTER (WHERE u.ativo = TRUE) AS ativos,
       count(*) FILTER (WHERE u.ativo = TRUE
                          AND u.papel IN ('ATENDENTE', 'SUBGESTOR')) AS com_papel_permitido,
       count(*) FILTER (WHERE u.ativo = TRUE
                          AND u.papel IN ('ATENDENTE', 'SUBGESTOR')
                          AND d.disponivel_para_ia = TRUE) AS disponiveis_para_ia,
       count(*) FILTER (WHERE u.ativo = TRUE
                          AND u.papel IN ('ATENDENTE', 'SUBGESTOR')
                          AND d.disponivel_para_ia = TRUE
                          AND u.status_presenca = 'ONLINE') AS online,
       count(*) FILTER (WHERE u.ativo = TRUE
                          AND u.papel IN ('ATENDENTE', 'SUBGESTOR')
                          AND d.atendente_id IS NULL) AS sem_registro
  FROM usuario u
  LEFT JOIN disponibilidade_atendente_ia d ON d.atendente_id = u.id;
-- repeticao 3
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT count(*) FILTER (WHERE u.ativo = TRUE) AS ativos,
       count(*) FILTER (WHERE u.ativo = TRUE
                          AND u.papel IN ('ATENDENTE', 'SUBGESTOR')) AS com_papel_permitido,
       count(*) FILTER (WHERE u.ativo = TRUE
                          AND u.papel IN ('ATENDENTE', 'SUBGESTOR')
                          AND d.disponivel_para_ia = TRUE) AS disponiveis_para_ia,
       count(*) FILTER (WHERE u.ativo = TRUE
                          AND u.papel IN ('ATENDENTE', 'SUBGESTOR')
                          AND d.disponivel_para_ia = TRUE
                          AND u.status_presenca = 'ONLINE') AS online,
       count(*) FILTER (WHERE u.ativo = TRUE
                          AND u.papel IN ('ATENDENTE', 'SUBGESTOR')
                          AND d.atendente_id IS NULL) AS sem_registro
  FROM usuario u
  LEFT JOIN disponibilidade_atendente_ia d ON d.atendente_id = u.id;
ROLLBACK;
