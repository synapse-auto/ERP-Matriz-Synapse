-- SOMENTE LEITURA. Executar no banco da instancia com papel de manutencao autorizado
-- que enxergue todas as linhas. IDs tecnicos so em canal privado. Nao retorna conteudo/telefone.
\set ON_ERROR_STOP on
BEGIN READ ONLY;
SET LOCAL statement_timeout = '5s';
SET LOCAL TIME ZONE 'UTC';
SELECT current_database() AS banco, current_user AS executor, now() AS coletado_em;
-- Sem visibilidade integral, zero filas/atendimentos seria uma falsa garantia.
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = current_user AND (rolsuper OR rolbypassrls)) THEN
        RAISE EXCEPTION 'Inventario exige executor de manutencao com BYPASSRLS';
    END IF;
END $$;
SELECT version, success, installed_on FROM flyway_schema_history
WHERE version IS NOT NULL ORDER BY installed_rank DESC LIMIT 5;
SELECT c.id AS canal_id, c.tipo, c.ativo AS canal_ativo, cc.id AS credencial_id,
       cc.ativo AS credencial_ativa, cc.vigente_desde, cc.vigente_ate,
       cc.token_ref = 'env://Z_API_TOKEN' AS referencia_z_api
FROM canal c LEFT JOIN canal_credencial cc ON cc.canal_id = c.id
ORDER BY c.id, cc.vigente_desde;
SELECT canal_id, canal_credencial_id, status, count(*) AS quantidade,
       min(iniciado_em) AS mais_antigo FROM atendimento
WHERE finalizado_em IS NULL GROUP BY canal_id, canal_credencial_id, status;
SELECT tipo, COALESCE(payload->>'provedorOrigem', 'LEGADO_SEM_ORIGEM') AS origem,
       count(*) AS nao_publicados,
       count(*) FILTER (WHERE esgotado_em IS NOT NULL) AS esgotados,
       count(*) FILTER (WHERE despacho_ambiguo_em IS NOT NULL) AS ambiguos,
       count(*) FILTER (WHERE despachado_em IS NOT NULL) AS com_despacho
FROM outbox_evento WHERE publicado_em IS NULL
  AND tipo IN ('canal.mensagem.enviar', 'automacao.webhook.repassar') GROUP BY 1, 2;
SELECT provedor, count(*) AS nao_processados,
       count(*) FILTER (WHERE esgotado_em IS NOT NULL) AS esgotados,
       min(recebido_em) AS mais_antigo
FROM webhook_entrada WHERE processado_em IS NULL GROUP BY provedor;
SELECT status, count(*) AS quantidade, min(data_envio) AS primeiro_envio
FROM mensagem_programada WHERE status = 'AGENDADA' GROUP BY status;
-- Lista limitada, nao total. Pagine por data_envio,id se houver mais de 100; nao exporte conteudo.
SELECT id AS programada_id, lead_id, atendente_id, data_envio, status
FROM mensagem_programada WHERE status = 'AGENDADA' ORDER BY data_envio, id LIMIT 100;
ROLLBACK;
