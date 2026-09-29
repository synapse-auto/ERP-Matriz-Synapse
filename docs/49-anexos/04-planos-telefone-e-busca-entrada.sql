\pset pager off
SELECT id AS gestor FROM usuario WHERE email='gestor@dev.local' \gset
SELECT id AS ana FROM usuario WHERE email='ana@dev.local' \gset
\echo ===== telefone = ? ORDER BY criado_em LIMIT 1 (synapse_app SERVICO)
BEGIN; SET LOCAL ROLE synapse_app; SELECT set_config('app.papel','SERVICO', true) \g /dev/null
EXPLAIN (ANALYZE, BUFFERS, TIMING OFF, SUMMARY ON) SELECT id FROM lead WHERE telefone = '5561900012345' ORDER BY criado_em LIMIT 1;
\echo ===== telefone inexistente
EXPLAIN (ANALYZE, BUFFERS, TIMING OFF, SUMMARY ON) SELECT id FROM lead WHERE telefone = '5561999999999' ORDER BY criado_em LIMIT 1;
ROLLBACK;
\echo ===== app_buscar_lead_para_entrada('Maria') como ana
BEGIN; SET LOCAL ROLE synapse_app; SELECT set_config('app.usuario_id', :'ana', true), set_config('app.papel','ATENDENTE', true) \g /dev/null
EXPLAIN (ANALYZE, BUFFERS, TIMING OFF, SUMMARY ON) SELECT * FROM app_buscar_lead_para_entrada('Maria Silva 12', :'ana'::uuid);
\echo ===== corpo da funcao inline (mesmo SQL), para ver o plano interno
EXPLAIN (ANALYZE, BUFFERS, TIMING OFF, SUMMARY ON)
SELECT l.id FROM lead l JOIN usuario u ON u.id=l.atendente_responsavel_id JOIN atendimento a ON a.lead_id=l.id AND a.status <> 'FINALIZADO'
 WHERE l.atendente_responsavel_id IS NOT NULL AND l.atendente_responsavel_id <> :'ana'::uuid
   AND (l.nome ILIKE '%Maria Silva 12%' OR regexp_replace(COALESCE(l.telefone,''),'[^0-9]','','g') LIKE '%' || regexp_replace('Maria Silva 12','[^0-9]','','g') || '%')
 ORDER BY l.nome LIMIT 10;
ROLLBACK;
