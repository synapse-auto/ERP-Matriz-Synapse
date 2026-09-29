\pset pager off
SELECT id AS gestor FROM usuario WHERE email='gestor@dev.local' \gset
CREATE INDEX IF NOT EXISTS perf_experimento_lower_nome_trgm ON lead USING gin (lower(nome::text) gin_trgm_ops);
ANALYZE lead;
\echo ===== COM indice lower(nome) trgm, como DONO (sem RLS)
EXPLAIN (ANALYZE, BUFFERS, TIMING OFF, SUMMARY ON) SELECT count(l.id) FROM lead l WHERE lower(l.nome) LIKE '%maria silva 12%' ESCAPE '\';
\echo ===== COM indice lower(nome) trgm, como synapse_app GESTOR (com RLS)
BEGIN; SET LOCAL ROLE synapse_app; SELECT set_config('app.usuario_id', :'gestor', true), set_config('app.papel','GESTOR', true) \g /dev/null
EXPLAIN (ANALYZE, BUFFERS, TIMING OFF, SUMMARY ON) SELECT count(l.id) FROM lead l WHERE lower(l.nome) LIKE '%maria silva 12%' ESCAPE '\';
\echo ===== busca completa (nome OR telefone OR cpf) como synapse_app GESTOR
EXPLAIN (ANALYZE, BUFFERS, TIMING OFF, SUMMARY ON)
SELECT count(l.id) FROM lead l
 WHERE lower(l.nome) LIKE '%maria silva 12%' ESCAPE '\' OR lower(l.telefone) LIKE '%maria silva 12%' ESCAPE '\' OR lower(l.cpf) LIKE '%maria silva 12%' ESCAPE '\';
\echo ===== telefone = (texteq, leakproof) como synapse_app SERVICO
SELECT set_config('app.papel','SERVICO', true) \g /dev/null
EXPLAIN (ANALYZE, BUFFERS, TIMING OFF, SUMMARY ON) SELECT id FROM lead WHERE telefone = '5561900012345' ORDER BY criado_em LIMIT 1;
ROLLBACK;
DROP INDEX perf_experimento_lower_nome_trgm;
