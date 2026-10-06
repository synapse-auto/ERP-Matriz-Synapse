-- E225 / B5 - EXPLAIN das contagens das abas do painel: depois.
-- DEPOIS do B5: contagem filtrando direto as linhas abertas (a.status IN (...)).
--
-- Nada altera dados: cada bloco e BEGIN ... ROLLBACK. EXPLAIN ANALYZE EXECUTA a consulta (teto de 60 s abaixo).
-- Cada medicao roda 3 vezes em seguida: compare o MENOR "Execution Time" de cada aba (a primeira esquenta o cache e a
-- VPS tem CPU disputada).
--
-- Uso (psql, superusuario ou dono do banco, no banco matriz_hml; JIT ja desligado):
--   psql -d matriz_hml -v papel=GESTOR    -v usuario=<uuid do gestor>    -f <este arquivo> > saida-gestor-depois.txt
--   psql -d matriz_hml -v papel=ATENDENTE -v usuario=<uuid do atendente> -f <este arquivo> > saida-atendente-depois.txt
--   psql -d matriz_hml -v papel=OPERADOR  -v usuario=<uuid do operador>  -f <este arquivo> > saida-operador-depois.txt
-- e extraia os tempos:
--   grep -E "^-- |Execution Time" saida-gestor-depois.txt
--
-- Cada aba tem duas medicoes: N-literal (plano customizado) e N-generico (PREPARE + force_generic_plan, o plano que o
-- driver JDBC pode passar a usar depois de 5 execucoes).
-- O contexto reproduz o AplicadorDeContextoRls (SET LOCAL ROLE synapse_app + app.papel + app.usuario_id).
\set ON_ERROR_STOP on
\pset pager off

-- =========================================================================================================
-- 01 ATIVOS - DEPOIS
-- =========================================================================================================
-- 1-literal (3 repeticoes na mesma transacao)
BEGIN;
SET LOCAL ROLE synapse_app;
SET LOCAL statement_timeout = '60s';
SELECT set_config('app.papel', :'papel', TRUE), set_config('app.usuario_id', :'usuario', TRUE), set_config('app.contexto_agenda', '', TRUE);
-- repeticao 1
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT COUNT(DISTINCT a.lead_id) FROM atendimento a JOIN lead l ON l.id = a.lead_id WHERE a.status = 'EM_ATENDIMENTO' AND a.atendente_id = :'usuario';
-- repeticao 2
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT COUNT(DISTINCT a.lead_id) FROM atendimento a JOIN lead l ON l.id = a.lead_id WHERE a.status = 'EM_ATENDIMENTO' AND a.atendente_id = :'usuario';
-- repeticao 3
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT COUNT(DISTINCT a.lead_id) FROM atendimento a JOIN lead l ON l.id = a.lead_id WHERE a.status = 'EM_ATENDIMENTO' AND a.atendente_id = :'usuario';
ROLLBACK;

-- 1-generico (PREPARE + plan_cache_mode = force_generic_plan, 3 repeticoes)
BEGIN;
SET LOCAL ROLE synapse_app;
SET LOCAL statement_timeout = '60s';
SELECT set_config('app.papel', :'papel', TRUE), set_config('app.usuario_id', :'usuario', TRUE), set_config('app.contexto_agenda', '', TRUE);
PREPARE c_depois_1(uuid) AS
SELECT COUNT(DISTINCT a.lead_id) FROM atendimento a JOIN lead l ON l.id = a.lead_id WHERE a.status = 'EM_ATENDIMENTO' AND a.atendente_id = $1;
SET LOCAL plan_cache_mode = force_generic_plan;
-- repeticao 1
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE c_depois_1(:'usuario');
-- repeticao 2
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE c_depois_1(:'usuario');
-- repeticao 3
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE c_depois_1(:'usuario');
ROLLBACK;
DEALLOCATE c_depois_1;

-- =========================================================================================================
-- 02 PENDENTES, atendente (restrito ao proprio) - DEPOIS
-- =========================================================================================================
-- 2-literal (3 repeticoes na mesma transacao)
BEGIN;
SET LOCAL ROLE synapse_app;
SET LOCAL statement_timeout = '60s';
SELECT set_config('app.papel', :'papel', TRUE), set_config('app.usuario_id', :'usuario', TRUE), set_config('app.contexto_agenda', '', TRUE);
-- repeticao 1
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT COUNT(DISTINCT a.lead_id) FROM atendimento a JOIN lead l ON l.id = a.lead_id LEFT JOIN LATERAL (SELECT remetente_tipo FROM mensagem m_visivel WHERE m_visivel.atendimento_id = a.id AND m_visivel.remetente_tipo IN ('LEAD','ATENDENTE') ORDER BY m_visivel.enviado_em DESC LIMIT 1) ultima_visivel ON true WHERE a.status = 'EM_ATENDIMENTO' AND ((a.atendente_id = :'usuario' AND ultima_visivel.remetente_tipo = 'LEAD') OR EXISTS (SELECT 1 FROM pedido_entrada_atendimento convite WHERE convite.atendimento_id = a.id AND convite.solicitante_id = :'usuario' AND convite.tipo = 'CONVITE' AND convite.status = 'PENDENTE' AND convite.solicitado_em > now() - app_validade_pedido_entrada()));
-- repeticao 2
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT COUNT(DISTINCT a.lead_id) FROM atendimento a JOIN lead l ON l.id = a.lead_id LEFT JOIN LATERAL (SELECT remetente_tipo FROM mensagem m_visivel WHERE m_visivel.atendimento_id = a.id AND m_visivel.remetente_tipo IN ('LEAD','ATENDENTE') ORDER BY m_visivel.enviado_em DESC LIMIT 1) ultima_visivel ON true WHERE a.status = 'EM_ATENDIMENTO' AND ((a.atendente_id = :'usuario' AND ultima_visivel.remetente_tipo = 'LEAD') OR EXISTS (SELECT 1 FROM pedido_entrada_atendimento convite WHERE convite.atendimento_id = a.id AND convite.solicitante_id = :'usuario' AND convite.tipo = 'CONVITE' AND convite.status = 'PENDENTE' AND convite.solicitado_em > now() - app_validade_pedido_entrada()));
-- repeticao 3
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT COUNT(DISTINCT a.lead_id) FROM atendimento a JOIN lead l ON l.id = a.lead_id LEFT JOIN LATERAL (SELECT remetente_tipo FROM mensagem m_visivel WHERE m_visivel.atendimento_id = a.id AND m_visivel.remetente_tipo IN ('LEAD','ATENDENTE') ORDER BY m_visivel.enviado_em DESC LIMIT 1) ultima_visivel ON true WHERE a.status = 'EM_ATENDIMENTO' AND ((a.atendente_id = :'usuario' AND ultima_visivel.remetente_tipo = 'LEAD') OR EXISTS (SELECT 1 FROM pedido_entrada_atendimento convite WHERE convite.atendimento_id = a.id AND convite.solicitante_id = :'usuario' AND convite.tipo = 'CONVITE' AND convite.status = 'PENDENTE' AND convite.solicitado_em > now() - app_validade_pedido_entrada()));
ROLLBACK;

-- 2-generico (PREPARE + plan_cache_mode = force_generic_plan, 3 repeticoes)
BEGIN;
SET LOCAL ROLE synapse_app;
SET LOCAL statement_timeout = '60s';
SELECT set_config('app.papel', :'papel', TRUE), set_config('app.usuario_id', :'usuario', TRUE), set_config('app.contexto_agenda', '', TRUE);
PREPARE c_depois_2(uuid, uuid) AS
SELECT COUNT(DISTINCT a.lead_id) FROM atendimento a JOIN lead l ON l.id = a.lead_id LEFT JOIN LATERAL (SELECT remetente_tipo FROM mensagem m_visivel WHERE m_visivel.atendimento_id = a.id AND m_visivel.remetente_tipo IN ('LEAD','ATENDENTE') ORDER BY m_visivel.enviado_em DESC LIMIT 1) ultima_visivel ON true WHERE a.status = 'EM_ATENDIMENTO' AND ((a.atendente_id = $1 AND ultima_visivel.remetente_tipo = 'LEAD') OR EXISTS (SELECT 1 FROM pedido_entrada_atendimento convite WHERE convite.atendimento_id = a.id AND convite.solicitante_id = $2 AND convite.tipo = 'CONVITE' AND convite.status = 'PENDENTE' AND convite.solicitado_em > now() - app_validade_pedido_entrada()));
SET LOCAL plan_cache_mode = force_generic_plan;
-- repeticao 1
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE c_depois_2(:'usuario', :'usuario');
-- repeticao 2
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE c_depois_2(:'usuario', :'usuario');
-- repeticao 3
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE c_depois_2(:'usuario', :'usuario');
ROLLBACK;
DEALLOCATE c_depois_2;

-- =========================================================================================================
-- 03 PENDENTES, gestao (todos) - DEPOIS
-- =========================================================================================================
-- 3-literal (3 repeticoes na mesma transacao)
BEGIN;
SET LOCAL ROLE synapse_app;
SET LOCAL statement_timeout = '60s';
SELECT set_config('app.papel', :'papel', TRUE), set_config('app.usuario_id', :'usuario', TRUE), set_config('app.contexto_agenda', '', TRUE);
-- repeticao 1
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT COUNT(DISTINCT a.lead_id) FROM atendimento a JOIN lead l ON l.id = a.lead_id LEFT JOIN LATERAL (SELECT remetente_tipo FROM mensagem m_visivel WHERE m_visivel.atendimento_id = a.id AND m_visivel.remetente_tipo IN ('LEAD','ATENDENTE') ORDER BY m_visivel.enviado_em DESC LIMIT 1) ultima_visivel ON true WHERE a.status = 'EM_ATENDIMENTO' AND ultima_visivel.remetente_tipo = 'LEAD';
-- repeticao 2
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT COUNT(DISTINCT a.lead_id) FROM atendimento a JOIN lead l ON l.id = a.lead_id LEFT JOIN LATERAL (SELECT remetente_tipo FROM mensagem m_visivel WHERE m_visivel.atendimento_id = a.id AND m_visivel.remetente_tipo IN ('LEAD','ATENDENTE') ORDER BY m_visivel.enviado_em DESC LIMIT 1) ultima_visivel ON true WHERE a.status = 'EM_ATENDIMENTO' AND ultima_visivel.remetente_tipo = 'LEAD';
-- repeticao 3
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT COUNT(DISTINCT a.lead_id) FROM atendimento a JOIN lead l ON l.id = a.lead_id LEFT JOIN LATERAL (SELECT remetente_tipo FROM mensagem m_visivel WHERE m_visivel.atendimento_id = a.id AND m_visivel.remetente_tipo IN ('LEAD','ATENDENTE') ORDER BY m_visivel.enviado_em DESC LIMIT 1) ultima_visivel ON true WHERE a.status = 'EM_ATENDIMENTO' AND ultima_visivel.remetente_tipo = 'LEAD';
ROLLBACK;

-- 3-generico (PREPARE + plan_cache_mode = force_generic_plan, 3 repeticoes)
BEGIN;
SET LOCAL ROLE synapse_app;
SET LOCAL statement_timeout = '60s';
SELECT set_config('app.papel', :'papel', TRUE), set_config('app.usuario_id', :'usuario', TRUE), set_config('app.contexto_agenda', '', TRUE);
PREPARE c_depois_3 AS
SELECT COUNT(DISTINCT a.lead_id) FROM atendimento a JOIN lead l ON l.id = a.lead_id LEFT JOIN LATERAL (SELECT remetente_tipo FROM mensagem m_visivel WHERE m_visivel.atendimento_id = a.id AND m_visivel.remetente_tipo IN ('LEAD','ATENDENTE') ORDER BY m_visivel.enviado_em DESC LIMIT 1) ultima_visivel ON true WHERE a.status = 'EM_ATENDIMENTO' AND ultima_visivel.remetente_tipo = 'LEAD';
SET LOCAL plan_cache_mode = force_generic_plan;
-- repeticao 1
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE c_depois_3;
-- repeticao 2
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE c_depois_3;
-- repeticao 3
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE c_depois_3;
ROLLBACK;
DEALLOCATE c_depois_3;

-- =========================================================================================================
-- 04 POTENCIAIS - DEPOIS
-- =========================================================================================================
-- 4-literal (3 repeticoes na mesma transacao)
BEGIN;
SET LOCAL ROLE synapse_app;
SET LOCAL statement_timeout = '60s';
SELECT set_config('app.papel', :'papel', TRUE), set_config('app.usuario_id', :'usuario', TRUE), set_config('app.contexto_agenda', '', TRUE);
-- repeticao 1
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT COUNT(DISTINCT a.lead_id) FROM atendimento a JOIN lead l ON l.id = a.lead_id WHERE a.status = 'EM_IA';
-- repeticao 2
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT COUNT(DISTINCT a.lead_id) FROM atendimento a JOIN lead l ON l.id = a.lead_id WHERE a.status = 'EM_IA';
-- repeticao 3
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT COUNT(DISTINCT a.lead_id) FROM atendimento a JOIN lead l ON l.id = a.lead_id WHERE a.status = 'EM_IA';
ROLLBACK;

-- 4-generico (PREPARE + plan_cache_mode = force_generic_plan, 3 repeticoes)
BEGIN;
SET LOCAL ROLE synapse_app;
SET LOCAL statement_timeout = '60s';
SELECT set_config('app.papel', :'papel', TRUE), set_config('app.usuario_id', :'usuario', TRUE), set_config('app.contexto_agenda', '', TRUE);
PREPARE c_depois_4 AS
SELECT COUNT(DISTINCT a.lead_id) FROM atendimento a JOIN lead l ON l.id = a.lead_id WHERE a.status = 'EM_IA';
SET LOCAL plan_cache_mode = force_generic_plan;
-- repeticao 1
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE c_depois_4;
-- repeticao 2
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE c_depois_4;
-- repeticao 3
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE c_depois_4;
ROLLBACK;
DEALLOCATE c_depois_4;

-- =========================================================================================================
-- 05 TODOS (a mais pesada na VPS: 68% do tempo das contagens) - DEPOIS
-- =========================================================================================================
-- 5-literal (3 repeticoes na mesma transacao)
BEGIN;
SET LOCAL ROLE synapse_app;
SET LOCAL statement_timeout = '60s';
SELECT set_config('app.papel', :'papel', TRUE), set_config('app.usuario_id', :'usuario', TRUE), set_config('app.contexto_agenda', '', TRUE);
-- repeticao 1
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT COUNT(DISTINCT a.lead_id) FROM atendimento a JOIN lead l ON l.id = a.lead_id WHERE a.status IN ('EM_ATENDIMENTO', 'EM_IA');
-- repeticao 2
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT COUNT(DISTINCT a.lead_id) FROM atendimento a JOIN lead l ON l.id = a.lead_id WHERE a.status IN ('EM_ATENDIMENTO', 'EM_IA');
-- repeticao 3
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT COUNT(DISTINCT a.lead_id) FROM atendimento a JOIN lead l ON l.id = a.lead_id WHERE a.status IN ('EM_ATENDIMENTO', 'EM_IA');
ROLLBACK;

-- 5-generico (PREPARE + plan_cache_mode = force_generic_plan, 3 repeticoes)
BEGIN;
SET LOCAL ROLE synapse_app;
SET LOCAL statement_timeout = '60s';
SELECT set_config('app.papel', :'papel', TRUE), set_config('app.usuario_id', :'usuario', TRUE), set_config('app.contexto_agenda', '', TRUE);
PREPARE c_depois_5 AS
SELECT COUNT(DISTINCT a.lead_id) FROM atendimento a JOIN lead l ON l.id = a.lead_id WHERE a.status IN ('EM_ATENDIMENTO', 'EM_IA');
SET LOCAL plan_cache_mode = force_generic_plan;
-- repeticao 1
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE c_depois_5;
-- repeticao 2
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE c_depois_5;
-- repeticao 3
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE c_depois_5;
ROLLBACK;
DEALLOCATE c_depois_5;
