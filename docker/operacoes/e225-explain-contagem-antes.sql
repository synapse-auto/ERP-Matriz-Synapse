-- E225 / B5 - EXPLAIN das contagens das abas do painel: antes.
-- ANTES do B5: contagem com EXISTS (atendimento aberto do mesmo lead) por linha de atendimento.
--
-- Nada altera dados: cada bloco e BEGIN ... ROLLBACK. EXPLAIN ANALYZE EXECUTA a consulta (teto de 60 s abaixo).
-- Cada medicao roda 3 vezes em seguida: compare o MENOR "Execution Time" de cada aba (a primeira esquenta o cache e a
-- VPS tem CPU disputada).
--
-- Uso (psql, superusuario ou dono do banco, no banco matriz_hml; JIT ja desligado):
--   psql -d matriz_hml -v papel=GESTOR    -v usuario=<uuid do gestor>    -f <este arquivo> > saida-gestor-antes.txt
--   psql -d matriz_hml -v papel=ATENDENTE -v usuario=<uuid do atendente> -f <este arquivo> > saida-atendente-antes.txt
--   psql -d matriz_hml -v papel=OPERADOR  -v usuario=<uuid do operador>  -f <este arquivo> > saida-operador-antes.txt
-- e extraia os tempos:
--   grep -E "^-- |Execution Time" saida-gestor-antes.txt
--
-- Cada aba tem duas medicoes: N-literal (plano customizado) e N-generico (PREPARE + force_generic_plan, o plano que o
-- driver JDBC pode passar a usar depois de 5 execucoes).
-- O contexto reproduz o AplicadorDeContextoRls (SET LOCAL ROLE synapse_app + app.papel + app.usuario_id).
\set ON_ERROR_STOP on
\pset pager off

-- =========================================================================================================
-- 01 ATIVOS - ANTES
-- =========================================================================================================
-- 1-literal (3 repeticoes na mesma transacao)
BEGIN;
SET LOCAL ROLE synapse_app;
SET LOCAL statement_timeout = '60s';
SELECT set_config('app.papel', :'papel', TRUE), set_config('app.usuario_id', :'usuario', TRUE), set_config('app.contexto_agenda', '', TRUE);
-- repeticao 1
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT COUNT(DISTINCT a.lead_id) FROM atendimento a JOIN lead l ON l.id = a.lead_id WHERE EXISTS (SELECT 1 FROM atendimento visivel WHERE visivel.lead_id = a.lead_id AND visivel.status = 'EM_ATENDIMENTO' AND visivel.atendente_id = :'usuario');
-- repeticao 2
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT COUNT(DISTINCT a.lead_id) FROM atendimento a JOIN lead l ON l.id = a.lead_id WHERE EXISTS (SELECT 1 FROM atendimento visivel WHERE visivel.lead_id = a.lead_id AND visivel.status = 'EM_ATENDIMENTO' AND visivel.atendente_id = :'usuario');
-- repeticao 3
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT COUNT(DISTINCT a.lead_id) FROM atendimento a JOIN lead l ON l.id = a.lead_id WHERE EXISTS (SELECT 1 FROM atendimento visivel WHERE visivel.lead_id = a.lead_id AND visivel.status = 'EM_ATENDIMENTO' AND visivel.atendente_id = :'usuario');
ROLLBACK;

-- 1-generico (PREPARE + plan_cache_mode = force_generic_plan, 3 repeticoes)
BEGIN;
SET LOCAL ROLE synapse_app;
SET LOCAL statement_timeout = '60s';
SELECT set_config('app.papel', :'papel', TRUE), set_config('app.usuario_id', :'usuario', TRUE), set_config('app.contexto_agenda', '', TRUE);
PREPARE c_antes_1(uuid) AS
SELECT COUNT(DISTINCT a.lead_id) FROM atendimento a JOIN lead l ON l.id = a.lead_id WHERE EXISTS (SELECT 1 FROM atendimento visivel WHERE visivel.lead_id = a.lead_id AND visivel.status = 'EM_ATENDIMENTO' AND visivel.atendente_id = $1);
SET LOCAL plan_cache_mode = force_generic_plan;
-- repeticao 1
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE c_antes_1(:'usuario');
-- repeticao 2
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE c_antes_1(:'usuario');
-- repeticao 3
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE c_antes_1(:'usuario');
ROLLBACK;
DEALLOCATE c_antes_1;

-- =========================================================================================================
-- 02 PENDENTES, atendente (restrito ao proprio) - ANTES
-- =========================================================================================================
-- 2-literal (3 repeticoes na mesma transacao)
BEGIN;
SET LOCAL ROLE synapse_app;
SET LOCAL statement_timeout = '60s';
SELECT set_config('app.papel', :'papel', TRUE), set_config('app.usuario_id', :'usuario', TRUE), set_config('app.contexto_agenda', '', TRUE);
-- repeticao 1
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT COUNT(DISTINCT a.lead_id) FROM atendimento a JOIN lead l ON l.id = a.lead_id WHERE EXISTS (SELECT 1 FROM atendimento visivel LEFT JOIN LATERAL (SELECT remetente_tipo FROM mensagem m_visivel WHERE m_visivel.atendimento_id = visivel.id AND m_visivel.remetente_tipo IN ('LEAD','ATENDENTE') ORDER BY m_visivel.enviado_em DESC LIMIT 1) ultima_visivel ON true WHERE visivel.lead_id = a.lead_id AND visivel.status = 'EM_ATENDIMENTO' AND ((visivel.atendente_id = :'usuario' AND ultima_visivel.remetente_tipo = 'LEAD') OR EXISTS (SELECT 1 FROM pedido_entrada_atendimento convite WHERE convite.atendimento_id = visivel.id AND convite.solicitante_id = :'usuario' AND convite.tipo = 'CONVITE' AND convite.status = 'PENDENTE' AND convite.solicitado_em > now() - app_validade_pedido_entrada())) );
-- repeticao 2
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT COUNT(DISTINCT a.lead_id) FROM atendimento a JOIN lead l ON l.id = a.lead_id WHERE EXISTS (SELECT 1 FROM atendimento visivel LEFT JOIN LATERAL (SELECT remetente_tipo FROM mensagem m_visivel WHERE m_visivel.atendimento_id = visivel.id AND m_visivel.remetente_tipo IN ('LEAD','ATENDENTE') ORDER BY m_visivel.enviado_em DESC LIMIT 1) ultima_visivel ON true WHERE visivel.lead_id = a.lead_id AND visivel.status = 'EM_ATENDIMENTO' AND ((visivel.atendente_id = :'usuario' AND ultima_visivel.remetente_tipo = 'LEAD') OR EXISTS (SELECT 1 FROM pedido_entrada_atendimento convite WHERE convite.atendimento_id = visivel.id AND convite.solicitante_id = :'usuario' AND convite.tipo = 'CONVITE' AND convite.status = 'PENDENTE' AND convite.solicitado_em > now() - app_validade_pedido_entrada())) );
-- repeticao 3
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT COUNT(DISTINCT a.lead_id) FROM atendimento a JOIN lead l ON l.id = a.lead_id WHERE EXISTS (SELECT 1 FROM atendimento visivel LEFT JOIN LATERAL (SELECT remetente_tipo FROM mensagem m_visivel WHERE m_visivel.atendimento_id = visivel.id AND m_visivel.remetente_tipo IN ('LEAD','ATENDENTE') ORDER BY m_visivel.enviado_em DESC LIMIT 1) ultima_visivel ON true WHERE visivel.lead_id = a.lead_id AND visivel.status = 'EM_ATENDIMENTO' AND ((visivel.atendente_id = :'usuario' AND ultima_visivel.remetente_tipo = 'LEAD') OR EXISTS (SELECT 1 FROM pedido_entrada_atendimento convite WHERE convite.atendimento_id = visivel.id AND convite.solicitante_id = :'usuario' AND convite.tipo = 'CONVITE' AND convite.status = 'PENDENTE' AND convite.solicitado_em > now() - app_validade_pedido_entrada())) );
ROLLBACK;

-- 2-generico (PREPARE + plan_cache_mode = force_generic_plan, 3 repeticoes)
BEGIN;
SET LOCAL ROLE synapse_app;
SET LOCAL statement_timeout = '60s';
SELECT set_config('app.papel', :'papel', TRUE), set_config('app.usuario_id', :'usuario', TRUE), set_config('app.contexto_agenda', '', TRUE);
PREPARE c_antes_2(uuid, uuid) AS
SELECT COUNT(DISTINCT a.lead_id) FROM atendimento a JOIN lead l ON l.id = a.lead_id WHERE EXISTS (SELECT 1 FROM atendimento visivel LEFT JOIN LATERAL (SELECT remetente_tipo FROM mensagem m_visivel WHERE m_visivel.atendimento_id = visivel.id AND m_visivel.remetente_tipo IN ('LEAD','ATENDENTE') ORDER BY m_visivel.enviado_em DESC LIMIT 1) ultima_visivel ON true WHERE visivel.lead_id = a.lead_id AND visivel.status = 'EM_ATENDIMENTO' AND ((visivel.atendente_id = $1 AND ultima_visivel.remetente_tipo = 'LEAD') OR EXISTS (SELECT 1 FROM pedido_entrada_atendimento convite WHERE convite.atendimento_id = visivel.id AND convite.solicitante_id = $2 AND convite.tipo = 'CONVITE' AND convite.status = 'PENDENTE' AND convite.solicitado_em > now() - app_validade_pedido_entrada())) );
SET LOCAL plan_cache_mode = force_generic_plan;
-- repeticao 1
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE c_antes_2(:'usuario', :'usuario');
-- repeticao 2
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE c_antes_2(:'usuario', :'usuario');
-- repeticao 3
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE c_antes_2(:'usuario', :'usuario');
ROLLBACK;
DEALLOCATE c_antes_2;

-- =========================================================================================================
-- 03 PENDENTES, gestao (todos) - ANTES
-- =========================================================================================================
-- 3-literal (3 repeticoes na mesma transacao)
BEGIN;
SET LOCAL ROLE synapse_app;
SET LOCAL statement_timeout = '60s';
SELECT set_config('app.papel', :'papel', TRUE), set_config('app.usuario_id', :'usuario', TRUE), set_config('app.contexto_agenda', '', TRUE);
-- repeticao 1
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT COUNT(DISTINCT a.lead_id) FROM atendimento a JOIN lead l ON l.id = a.lead_id WHERE EXISTS (SELECT 1 FROM atendimento visivel LEFT JOIN LATERAL (SELECT remetente_tipo FROM mensagem m_visivel WHERE m_visivel.atendimento_id = visivel.id AND m_visivel.remetente_tipo IN ('LEAD','ATENDENTE') ORDER BY m_visivel.enviado_em DESC LIMIT 1) ultima_visivel ON true WHERE visivel.lead_id = a.lead_id AND visivel.status = 'EM_ATENDIMENTO' AND ultima_visivel.remetente_tipo = 'LEAD');
-- repeticao 2
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT COUNT(DISTINCT a.lead_id) FROM atendimento a JOIN lead l ON l.id = a.lead_id WHERE EXISTS (SELECT 1 FROM atendimento visivel LEFT JOIN LATERAL (SELECT remetente_tipo FROM mensagem m_visivel WHERE m_visivel.atendimento_id = visivel.id AND m_visivel.remetente_tipo IN ('LEAD','ATENDENTE') ORDER BY m_visivel.enviado_em DESC LIMIT 1) ultima_visivel ON true WHERE visivel.lead_id = a.lead_id AND visivel.status = 'EM_ATENDIMENTO' AND ultima_visivel.remetente_tipo = 'LEAD');
-- repeticao 3
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT COUNT(DISTINCT a.lead_id) FROM atendimento a JOIN lead l ON l.id = a.lead_id WHERE EXISTS (SELECT 1 FROM atendimento visivel LEFT JOIN LATERAL (SELECT remetente_tipo FROM mensagem m_visivel WHERE m_visivel.atendimento_id = visivel.id AND m_visivel.remetente_tipo IN ('LEAD','ATENDENTE') ORDER BY m_visivel.enviado_em DESC LIMIT 1) ultima_visivel ON true WHERE visivel.lead_id = a.lead_id AND visivel.status = 'EM_ATENDIMENTO' AND ultima_visivel.remetente_tipo = 'LEAD');
ROLLBACK;

-- 3-generico (PREPARE + plan_cache_mode = force_generic_plan, 3 repeticoes)
BEGIN;
SET LOCAL ROLE synapse_app;
SET LOCAL statement_timeout = '60s';
SELECT set_config('app.papel', :'papel', TRUE), set_config('app.usuario_id', :'usuario', TRUE), set_config('app.contexto_agenda', '', TRUE);
PREPARE c_antes_3 AS
SELECT COUNT(DISTINCT a.lead_id) FROM atendimento a JOIN lead l ON l.id = a.lead_id WHERE EXISTS (SELECT 1 FROM atendimento visivel LEFT JOIN LATERAL (SELECT remetente_tipo FROM mensagem m_visivel WHERE m_visivel.atendimento_id = visivel.id AND m_visivel.remetente_tipo IN ('LEAD','ATENDENTE') ORDER BY m_visivel.enviado_em DESC LIMIT 1) ultima_visivel ON true WHERE visivel.lead_id = a.lead_id AND visivel.status = 'EM_ATENDIMENTO' AND ultima_visivel.remetente_tipo = 'LEAD');
SET LOCAL plan_cache_mode = force_generic_plan;
-- repeticao 1
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE c_antes_3;
-- repeticao 2
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE c_antes_3;
-- repeticao 3
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE c_antes_3;
ROLLBACK;
DEALLOCATE c_antes_3;

-- =========================================================================================================
-- 04 POTENCIAIS - ANTES
-- =========================================================================================================
-- 4-literal (3 repeticoes na mesma transacao)
BEGIN;
SET LOCAL ROLE synapse_app;
SET LOCAL statement_timeout = '60s';
SELECT set_config('app.papel', :'papel', TRUE), set_config('app.usuario_id', :'usuario', TRUE), set_config('app.contexto_agenda', '', TRUE);
-- repeticao 1
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT COUNT(DISTINCT a.lead_id) FROM atendimento a JOIN lead l ON l.id = a.lead_id WHERE EXISTS (SELECT 1 FROM atendimento visivel WHERE visivel.lead_id = a.lead_id AND visivel.status = 'EM_IA');
-- repeticao 2
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT COUNT(DISTINCT a.lead_id) FROM atendimento a JOIN lead l ON l.id = a.lead_id WHERE EXISTS (SELECT 1 FROM atendimento visivel WHERE visivel.lead_id = a.lead_id AND visivel.status = 'EM_IA');
-- repeticao 3
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT COUNT(DISTINCT a.lead_id) FROM atendimento a JOIN lead l ON l.id = a.lead_id WHERE EXISTS (SELECT 1 FROM atendimento visivel WHERE visivel.lead_id = a.lead_id AND visivel.status = 'EM_IA');
ROLLBACK;

-- 4-generico (PREPARE + plan_cache_mode = force_generic_plan, 3 repeticoes)
BEGIN;
SET LOCAL ROLE synapse_app;
SET LOCAL statement_timeout = '60s';
SELECT set_config('app.papel', :'papel', TRUE), set_config('app.usuario_id', :'usuario', TRUE), set_config('app.contexto_agenda', '', TRUE);
PREPARE c_antes_4 AS
SELECT COUNT(DISTINCT a.lead_id) FROM atendimento a JOIN lead l ON l.id = a.lead_id WHERE EXISTS (SELECT 1 FROM atendimento visivel WHERE visivel.lead_id = a.lead_id AND visivel.status = 'EM_IA');
SET LOCAL plan_cache_mode = force_generic_plan;
-- repeticao 1
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE c_antes_4;
-- repeticao 2
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE c_antes_4;
-- repeticao 3
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE c_antes_4;
ROLLBACK;
DEALLOCATE c_antes_4;

-- =========================================================================================================
-- 05 TODOS (a mais pesada na VPS: 68% do tempo das contagens) - ANTES
-- =========================================================================================================
-- 5-literal (3 repeticoes na mesma transacao)
BEGIN;
SET LOCAL ROLE synapse_app;
SET LOCAL statement_timeout = '60s';
SELECT set_config('app.papel', :'papel', TRUE), set_config('app.usuario_id', :'usuario', TRUE), set_config('app.contexto_agenda', '', TRUE);
-- repeticao 1
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT COUNT(DISTINCT a.lead_id) FROM atendimento a JOIN lead l ON l.id = a.lead_id WHERE EXISTS (SELECT 1 FROM atendimento aberto WHERE aberto.lead_id = a.lead_id AND aberto.status IN ('EM_ATENDIMENTO', 'EM_IA'));
-- repeticao 2
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT COUNT(DISTINCT a.lead_id) FROM atendimento a JOIN lead l ON l.id = a.lead_id WHERE EXISTS (SELECT 1 FROM atendimento aberto WHERE aberto.lead_id = a.lead_id AND aberto.status IN ('EM_ATENDIMENTO', 'EM_IA'));
-- repeticao 3
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT COUNT(DISTINCT a.lead_id) FROM atendimento a JOIN lead l ON l.id = a.lead_id WHERE EXISTS (SELECT 1 FROM atendimento aberto WHERE aberto.lead_id = a.lead_id AND aberto.status IN ('EM_ATENDIMENTO', 'EM_IA'));
ROLLBACK;

-- 5-generico (PREPARE + plan_cache_mode = force_generic_plan, 3 repeticoes)
BEGIN;
SET LOCAL ROLE synapse_app;
SET LOCAL statement_timeout = '60s';
SELECT set_config('app.papel', :'papel', TRUE), set_config('app.usuario_id', :'usuario', TRUE), set_config('app.contexto_agenda', '', TRUE);
PREPARE c_antes_5 AS
SELECT COUNT(DISTINCT a.lead_id) FROM atendimento a JOIN lead l ON l.id = a.lead_id WHERE EXISTS (SELECT 1 FROM atendimento aberto WHERE aberto.lead_id = a.lead_id AND aberto.status IN ('EM_ATENDIMENTO', 'EM_IA'));
SET LOCAL plan_cache_mode = force_generic_plan;
-- repeticao 1
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE c_antes_5;
-- repeticao 2
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE c_antes_5;
-- repeticao 3
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE c_antes_5;
ROLLBACK;
DEALLOCATE c_antes_5;
