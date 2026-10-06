-- =====================================================================================================================
-- E225 (B5) - EXPLAIN das contagens das abas do painel: depois.
-- DEPOIS do B5: contagem filtrando direto as linhas abertas (a.status IN (...)) (texto do PR #279).
-- =====================================================================================================================
-- VARIAVEIS EXIGIDAS (-v). Sem elas o psql para no primeiro bloco (ON_ERROR_STOP):
--   papel   ATENDENTE | OPERADOR | GESTOR   (o papel da RLS; a consulta roda como esse papel, nao como superusuario)
--   usuario <uuid do usuario>
--   (este script NAO usa -v limite: as contagens nao tem LIMIT)
--
-- COMANDO EXATO (um arquivo por vez, mesma janela). <container> = o container do Postgres
-- (descubra com:  docker ps --format '{{.Names}}' | grep -i postgres ):
--
--   docker exec -i <container> psql -X -q -U matriz_app -d matriz_hml \
--     -v papel=GESTOR -v usuario=<uuid-do-gestor> \
--     < e225-explain-contagem-depois.sql > saida-contagem-depois-gestor.txt
--
--   docker exec -i <container> psql -X -q -U matriz_app -d matriz_hml \
--     -v papel=ATENDENTE -v usuario=<uuid-do-atendente> \
--     < e225-explain-contagem-depois.sql > saida-contagem-depois-atendente.txt
--
-- (repita para papel=OPERADOR se houver um operador). Rode "antes" e "depois" e cole as saidas. Para extrair os tempos:
--   grep -E "^-- |Execution Time" saida-contagem-depois-gestor.txt
--
-- O QUE E CADA BLOCO: por aba (01 ATIVOS, 02 PENDENTES atendente, 03 PENDENTES gestao, 04 POTENCIAIS, 05 TODOS) ha duas
-- medicoes: N-literal e N-generico (PREPARE + force_generic_plan), cada uma com 3 REPETICOES na mesma transacao:
-- compare o MENOR "Execution Time" (a primeira esquenta o cache e a VPS tem CPU disputada).
-- Cada bloco e BEGIN ... ROLLBACK: nada altera dados. EXPLAIN ANALYZE EXECUTA a consulta (teto de 60 s abaixo).
-- O contexto reproduz o AplicadorDeContextoRls (SET LOCAL ROLE synapse_app + app.papel + app.usuario_id).
-- O JIT ja esta desligado. Papeis diferentes dao contagens diferentes (RLS): compare o mesmo papel antes x depois.
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
