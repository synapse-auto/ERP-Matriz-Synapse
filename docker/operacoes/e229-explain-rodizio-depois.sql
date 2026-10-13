-- =====================================================================================================================
-- E229 (D3a/D3b) - EXPLAIN do rodizio da IA e do vinculo: depois.
-- DEPOIS: as consultas que o D3a/D3b ACRESCENTAM quando a capacidade esta ligada (setor, exclusao de setor que nao recebe, usuario por id, vinculado).
-- =====================================================================================================================
-- VARIAVEIS EXIGIDAS (-v). Sem elas o psql para no primeiro bloco (ON_ERROR_STOP):
--   setor        <uuid de um setor com recebe_da_triagem = true>
--   usuario      <uuid de um atendente ativo, para a busca por chave primaria>
--   atendimento  <uuid de um atendimento, para a busca do vinculado>
--
-- COMANDO EXATO (um arquivo por vez, mesma janela). <container> = o container do Postgres
-- (descubra com:  docker ps --format '{{.Names}}' | grep -i postgres ):
--
--   docker exec -i <container> psql -X -q -U matriz_app -d matriz_hml \
--     -v setor=<uuid-do-setor> -v usuario=<uuid-do-atendente> -v atendimento=<uuid-do-atendimento> \
--     < e229-explain-rodizio-depois.sql > saida-rodizio-depois.txt
--
-- Para extrair os tempos:   grep -E "^-- |Execution Time" saida-rodizio-depois.txt
--
-- O QUE E CADA BLOCO: por consulta ha medicoes N-literal e (quando ha parametro) N-generico (PREPARE + force_generic_plan),
-- cada uma com 3 REPETICOES na mesma transacao: compare o MENOR "Execution Time" (a primeira esquenta o cache e a VPS
-- tem CPU disputada). Cada bloco e BEGIN ... ROLLBACK: nada altera dados. EXPLAIN ANALYZE EXECUTA a consulta (teto de 60 s).
-- O contexto reproduz o que o rodizio usa: SET LOCAL ROLE synapse_app + app.papel = SERVICO (lead/atendimento tem RLS FORCE;
-- o rodizio roda como servico). O JIT ja esta desligado. Os textos SQL abaixo sao extraidos do codigo-fonte do commit.
\set ON_ERROR_STOP on
\pset pager off

-- =========================================================================================================
-- 01 MENOR CARGA + SETOR + EXCLUSAO - DEPOIS
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
 AND EXISTS (SELECT 1 FROM usuario_setor us WHERE us.usuario_id = u.id AND us.setor_id = :'setor')AND NOT EXISTS (SELECT 1 FROM usuario_setor us JOIN setor s ON s.id = us.setor_id
                 WHERE us.usuario_id = u.id AND (s.recebe_da_triagem = FALSE OR s.ativo = FALSE))
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
 AND EXISTS (SELECT 1 FROM usuario_setor us WHERE us.usuario_id = u.id AND us.setor_id = :'setor')AND NOT EXISTS (SELECT 1 FROM usuario_setor us JOIN setor s ON s.id = us.setor_id
                 WHERE us.usuario_id = u.id AND (s.recebe_da_triagem = FALSE OR s.ativo = FALSE))
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
 AND EXISTS (SELECT 1 FROM usuario_setor us WHERE us.usuario_id = u.id AND us.setor_id = :'setor')AND NOT EXISTS (SELECT 1 FROM usuario_setor us JOIN setor s ON s.id = us.setor_id
                 WHERE us.usuario_id = u.id AND (s.recebe_da_triagem = FALSE OR s.ativo = FALSE))
ORDER BY COALESCE(c.atendimentos_abertos, 0),
          r.ultimo_recebido_em NULLS FIRST,
          u.id;
ROLLBACK;

-- 01-generico (PREPARE + plan_cache_mode = force_generic_plan, 3 repeticoes)
BEGIN;
SET LOCAL ROLE synapse_app;
SET LOCAL statement_timeout = '60s';
SELECT set_config('app.papel', 'SERVICO', TRUE), set_config('app.usuario_id', '', TRUE), set_config('app.contexto_agenda', '', TRUE);
PREPARE e229_01(uuid) AS
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
 AND EXISTS (SELECT 1 FROM usuario_setor us WHERE us.usuario_id = u.id AND us.setor_id = $1)AND NOT EXISTS (SELECT 1 FROM usuario_setor us JOIN setor s ON s.id = us.setor_id
                 WHERE us.usuario_id = u.id AND (s.recebe_da_triagem = FALSE OR s.ativo = FALSE))
ORDER BY COALESCE(c.atendimentos_abertos, 0),
          r.ultimo_recebido_em NULLS FIRST,
          u.id;
SET LOCAL plan_cache_mode = force_generic_plan;
-- repeticao 1
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE e229_01(:'setor');
-- repeticao 2
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE e229_01(:'setor');
-- repeticao 3
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE e229_01(:'setor');
ROLLBACK;
DEALLOCATE e229_01;

-- =========================================================================================================
-- 02 SEQUENCIAL + SETOR + EXCLUSAO - DEPOIS
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
 AND EXISTS (SELECT 1 FROM usuario_setor us WHERE us.usuario_id = u.id AND us.setor_id = :'setor')AND NOT EXISTS (SELECT 1 FROM usuario_setor us JOIN setor s ON s.id = us.setor_id
                 WHERE us.usuario_id = u.id AND (s.recebe_da_triagem = FALSE OR s.ativo = FALSE))
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
 AND EXISTS (SELECT 1 FROM usuario_setor us WHERE us.usuario_id = u.id AND us.setor_id = :'setor')AND NOT EXISTS (SELECT 1 FROM usuario_setor us JOIN setor s ON s.id = us.setor_id
                 WHERE us.usuario_id = u.id AND (s.recebe_da_triagem = FALSE OR s.ativo = FALSE))
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
 AND EXISTS (SELECT 1 FROM usuario_setor us WHERE us.usuario_id = u.id AND us.setor_id = :'setor')AND NOT EXISTS (SELECT 1 FROM usuario_setor us JOIN setor s ON s.id = us.setor_id
                 WHERE us.usuario_id = u.id AND (s.recebe_da_triagem = FALSE OR s.ativo = FALSE))
ORDER BY r.ultimo_recebido_em NULLS FIRST,
          u.id;
ROLLBACK;

-- 02-generico (PREPARE + plan_cache_mode = force_generic_plan, 3 repeticoes)
BEGIN;
SET LOCAL ROLE synapse_app;
SET LOCAL statement_timeout = '60s';
SELECT set_config('app.papel', 'SERVICO', TRUE), set_config('app.usuario_id', '', TRUE), set_config('app.contexto_agenda', '', TRUE);
PREPARE e229_02(uuid) AS
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
 AND EXISTS (SELECT 1 FROM usuario_setor us WHERE us.usuario_id = u.id AND us.setor_id = $1)AND NOT EXISTS (SELECT 1 FROM usuario_setor us JOIN setor s ON s.id = us.setor_id
                 WHERE us.usuario_id = u.id AND (s.recebe_da_triagem = FALSE OR s.ativo = FALSE))
ORDER BY r.ultimo_recebido_em NULLS FIRST,
          u.id;
SET LOCAL plan_cache_mode = force_generic_plan;
-- repeticao 1
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE e229_02(:'setor');
-- repeticao 2
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE e229_02(:'setor');
-- repeticao 3
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE e229_02(:'setor');
ROLLBACK;
DEALLOCATE e229_02;

-- =========================================================================================================
-- 03 MENOR CARGA + SO EXCLUSAO (setores ligado, sem setor no corpo) - DEPOIS
-- =========================================================================================================
-- 03-literal (3 repeticoes na mesma transacao)
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
 AND NOT EXISTS (SELECT 1 FROM usuario_setor us JOIN setor s ON s.id = us.setor_id
                 WHERE us.usuario_id = u.id AND (s.recebe_da_triagem = FALSE OR s.ativo = FALSE))
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
 AND NOT EXISTS (SELECT 1 FROM usuario_setor us JOIN setor s ON s.id = us.setor_id
                 WHERE us.usuario_id = u.id AND (s.recebe_da_triagem = FALSE OR s.ativo = FALSE))
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
 AND NOT EXISTS (SELECT 1 FROM usuario_setor us JOIN setor s ON s.id = us.setor_id
                 WHERE us.usuario_id = u.id AND (s.recebe_da_triagem = FALSE OR s.ativo = FALSE))
ORDER BY COALESCE(c.atendimentos_abertos, 0),
          r.ultimo_recebido_em NULLS FIRST,
          u.id;
ROLLBACK;

-- =========================================================================================================
-- 04 SEQUENCIAL + SO EXCLUSAO - DEPOIS
-- =========================================================================================================
-- 04-literal (3 repeticoes na mesma transacao)
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
 AND NOT EXISTS (SELECT 1 FROM usuario_setor us JOIN setor s ON s.id = us.setor_id
                 WHERE us.usuario_id = u.id AND (s.recebe_da_triagem = FALSE OR s.ativo = FALSE))
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
 AND NOT EXISTS (SELECT 1 FROM usuario_setor us JOIN setor s ON s.id = us.setor_id
                 WHERE us.usuario_id = u.id AND (s.recebe_da_triagem = FALSE OR s.ativo = FALSE))
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
 AND NOT EXISTS (SELECT 1 FROM usuario_setor us JOIN setor s ON s.id = us.setor_id
                 WHERE us.usuario_id = u.id AND (s.recebe_da_triagem = FALSE OR s.ativo = FALSE))
ORDER BY r.ultimo_recebido_em NULLS FIRST,
          u.id;
ROLLBACK;

-- =========================================================================================================
-- 05 USUARIO POR ID (honrar o vinculado) - DEPOIS
-- =========================================================================================================
-- 05-literal (3 repeticoes na mesma transacao)
BEGIN;
SET LOCAL ROLE synapse_app;
SET LOCAL statement_timeout = '60s';
SELECT set_config('app.papel', 'SERVICO', TRUE), set_config('app.usuario_id', '', TRUE), set_config('app.contexto_agenda', '', TRUE);
-- repeticao 1
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT u.id, u.nome, u.email
  FROM disponibilidade_atendente_ia d
  JOIN usuario u ON u.id = d.atendente_id
 WHERE u.id = :'usuario'
   AND d.disponivel_para_ia = TRUE AND u.ativo = TRUE
   -- Espelha PapelUsuario.recebeAtendimento()
   AND u.papel IN ('ATENDENTE', 'SUBGESTOR') AND u.status_presenca = 'ONLINE'
AND NOT EXISTS (SELECT 1 FROM usuario_setor us JOIN setor s ON s.id = us.setor_id
                 WHERE us.usuario_id = u.id AND (s.recebe_da_triagem = FALSE OR s.ativo = FALSE));
-- repeticao 2
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT u.id, u.nome, u.email
  FROM disponibilidade_atendente_ia d
  JOIN usuario u ON u.id = d.atendente_id
 WHERE u.id = :'usuario'
   AND d.disponivel_para_ia = TRUE AND u.ativo = TRUE
   -- Espelha PapelUsuario.recebeAtendimento()
   AND u.papel IN ('ATENDENTE', 'SUBGESTOR') AND u.status_presenca = 'ONLINE'
AND NOT EXISTS (SELECT 1 FROM usuario_setor us JOIN setor s ON s.id = us.setor_id
                 WHERE us.usuario_id = u.id AND (s.recebe_da_triagem = FALSE OR s.ativo = FALSE));
-- repeticao 3
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT u.id, u.nome, u.email
  FROM disponibilidade_atendente_ia d
  JOIN usuario u ON u.id = d.atendente_id
 WHERE u.id = :'usuario'
   AND d.disponivel_para_ia = TRUE AND u.ativo = TRUE
   -- Espelha PapelUsuario.recebeAtendimento()
   AND u.papel IN ('ATENDENTE', 'SUBGESTOR') AND u.status_presenca = 'ONLINE'
AND NOT EXISTS (SELECT 1 FROM usuario_setor us JOIN setor s ON s.id = us.setor_id
                 WHERE us.usuario_id = u.id AND (s.recebe_da_triagem = FALSE OR s.ativo = FALSE));
ROLLBACK;

-- 05-generico (PREPARE + plan_cache_mode = force_generic_plan, 3 repeticoes)
BEGIN;
SET LOCAL ROLE synapse_app;
SET LOCAL statement_timeout = '60s';
SELECT set_config('app.papel', 'SERVICO', TRUE), set_config('app.usuario_id', '', TRUE), set_config('app.contexto_agenda', '', TRUE);
PREPARE e229_05(uuid) AS
SELECT u.id, u.nome, u.email
  FROM disponibilidade_atendente_ia d
  JOIN usuario u ON u.id = d.atendente_id
 WHERE u.id = $1
   AND d.disponivel_para_ia = TRUE AND u.ativo = TRUE
   -- Espelha PapelUsuario.recebeAtendimento()
   AND u.papel IN ('ATENDENTE', 'SUBGESTOR') AND u.status_presenca = 'ONLINE'
AND NOT EXISTS (SELECT 1 FROM usuario_setor us JOIN setor s ON s.id = us.setor_id
                 WHERE us.usuario_id = u.id AND (s.recebe_da_triagem = FALSE OR s.ativo = FALSE));
SET LOCAL plan_cache_mode = force_generic_plan;
-- repeticao 1
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE e229_05(:'usuario');
-- repeticao 2
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE e229_05(:'usuario');
-- repeticao 3
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE e229_05(:'usuario');
ROLLBACK;
DEALLOCATE e229_05;

-- =========================================================================================================
-- 06 VINCULADO DO ATENDIMENTO (chave + vinculo) - DEPOIS
-- =========================================================================================================
-- 06-literal (3 repeticoes na mesma transacao)
BEGIN;
SET LOCAL ROLE synapse_app;
SET LOCAL statement_timeout = '60s';
SELECT set_config('app.papel', 'SERVICO', TRUE), set_config('app.usuario_id', '', TRUE), set_config('app.contexto_agenda', '', TRUE);
-- repeticao 1
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT v.atendente_id
  FROM configuracao_automacao c
  JOIN atendimento a ON a.id = :'atendimento'
  JOIN lead_atendente_vinculado v ON v.lead_id = a.lead_id
 WHERE c.chave = 'ia.distribuicao.atendente_vinculado' AND lower(btrim(c.valor)) = 'true';
-- repeticao 2
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT v.atendente_id
  FROM configuracao_automacao c
  JOIN atendimento a ON a.id = :'atendimento'
  JOIN lead_atendente_vinculado v ON v.lead_id = a.lead_id
 WHERE c.chave = 'ia.distribuicao.atendente_vinculado' AND lower(btrim(c.valor)) = 'true';
-- repeticao 3
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT v.atendente_id
  FROM configuracao_automacao c
  JOIN atendimento a ON a.id = :'atendimento'
  JOIN lead_atendente_vinculado v ON v.lead_id = a.lead_id
 WHERE c.chave = 'ia.distribuicao.atendente_vinculado' AND lower(btrim(c.valor)) = 'true';
ROLLBACK;

-- 06-generico (PREPARE + plan_cache_mode = force_generic_plan, 3 repeticoes)
BEGIN;
SET LOCAL ROLE synapse_app;
SET LOCAL statement_timeout = '60s';
SELECT set_config('app.papel', 'SERVICO', TRUE), set_config('app.usuario_id', '', TRUE), set_config('app.contexto_agenda', '', TRUE);
PREPARE e229_06(uuid,text) AS
SELECT v.atendente_id
  FROM configuracao_automacao c
  JOIN atendimento a ON a.id = $1
  JOIN lead_atendente_vinculado v ON v.lead_id = a.lead_id
 WHERE c.chave = $2 AND lower(btrim(c.valor)) = 'true';
SET LOCAL plan_cache_mode = force_generic_plan;
-- repeticao 1
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE e229_06(:'atendimento','ia.distribuicao.atendente_vinculado');
-- repeticao 2
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE e229_06(:'atendimento','ia.distribuicao.atendente_vinculado');
-- repeticao 3
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE e229_06(:'atendimento','ia.distribuicao.atendente_vinculado');
ROLLBACK;
DEALLOCATE e229_06;
