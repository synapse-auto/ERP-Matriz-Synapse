\pset pager off
SELECT id AS gestor FROM usuario WHERE email='gestor@dev.local' \gset
SELECT id AS ana FROM usuario WHERE email='ana@dev.local' \gset
\echo ===== OUTBOX count(*) (verificador; roda fora de transacao RLS, como dono da conexao)
EXPLAIN (ANALYZE, BUFFERS, TIMING OFF, SUMMARY ON) SELECT count(*) FROM outbox_evento;
\echo ===== OUTBOX alternativa LIMIT 0
EXPLAIN (ANALYZE, BUFFERS, TIMING OFF, SUMMARY ON) SELECT 1 FROM outbox_evento LIMIT 0;
\echo ===== BUSCA leads COUNT como GESTOR (synapse_app + RLS)
BEGIN; SET LOCAL ROLE synapse_app; SELECT set_config('app.usuario_id', :'gestor', true), set_config('app.papel','GESTOR', true) \g /dev/null
EXPLAIN (ANALYZE, BUFFERS, TIMING OFF, SUMMARY ON)
SELECT count(l.id) FROM lead l
 WHERE lower(l.nome) LIKE '%maria silva 12%' ESCAPE '\' OR lower(l.telefone) LIKE '%maria silva 12%' ESCAPE '\' OR lower(l.cpf) LIKE '%maria silva 12%' ESCAPE '\';
ROLLBACK;
