-- EXCLUSIVO para banco ISOLADO de homologacao SEM leads, atendimentos, filas ou programadas.
-- Nao e procedimento de corte/rollback de producao. Nao altera credenciais de outros canais.
-- psql -X -v banco_esperado=... -v credencial_id=UUID_RETORNADO -v confirmar=HOMOLOGACAO_ISOLADA
\set ON_ERROR_STOP on
\set VERBOSITY terse
\if :{?banco_esperado}
\else
  \set banco_esperado ''
\endif
\if :{?credencial_id}
\else
  \set credencial_id ''
\endif
\if :{?confirmar}
\else
  \set confirmar ''
\endif
BEGIN;
SET LOCAL lock_timeout = '3s';
SET LOCAL statement_timeout = '5s';
SET LOCAL app.papel = 'SERVICO';
CREATE TEMP TABLE ativacao_z_api ON COMMIT DROP AS
SELECT :'banco_esperado'::text AS banco, :'credencial_id'::text AS credencial,
       :'confirmar'::text AS confirmacao;
DO $$
DECLARE
    entrada record;
    credencial canal_credencial%ROWTYPE;
    anterior jsonb;
BEGIN
    SELECT * INTO entrada FROM ativacao_z_api;
    IF entrada.banco = '' OR entrada.banco <> current_database()
       OR entrada.confirmacao <> 'HOMOLOGACAO_ISOLADA' THEN
        RAISE EXCEPTION 'Exige banco_esperado e confirmar=HOMOLOGACAO_ISOLADA';
    END IF;
    -- Operador precisa enxergar TODAS as linhas: RLS nao pode esconder pendencias.
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = current_user AND (rolsuper OR rolbypassrls)) THEN
        RAISE EXCEPTION 'Exige executor com BYPASSRLS para conferir banco isolado integralmente';
    END IF;
    PERFORM pg_advisory_xact_lock(hashtextextended('synapse:cadastro-canal-z-api', 0));
    -- Evita criacao concorrente durante as verificacoes/ativacao, somente no banco vazio HML.
    LOCK TABLE lead, atendimento, mensagem_programada, outbox_evento, webhook_entrada,
               canal, canal_credencial IN SHARE ROW EXCLUSIVE MODE;
    IF EXISTS (SELECT 1 FROM lead) OR EXISTS (SELECT 1 FROM atendimento)
       OR EXISTS (SELECT 1 FROM mensagem_programada) OR EXISTS (SELECT 1 FROM outbox_evento)
       OR EXISTS (SELECT 1 FROM webhook_entrada) THEN
        RAISE EXCEPTION 'Banco possui dados operacionais; ativacao HML recusada (nao apagar dados para contornar)';
    END IF;
    SELECT * INTO credencial FROM canal_credencial WHERE id = entrada.credencial::uuid FOR UPDATE;
    IF NOT FOUND OR credencial.token_ref IS DISTINCT FROM 'env://Z_API_TOKEN'
       OR credencial.identificador_externo !~ '^[A-Za-z0-9_-]{1,120}$'
       OR credencial.vigente_ate IS NOT NULL OR credencial.vigente_desde > now()
       OR NOT EXISTS (SELECT 1 FROM canal WHERE id = credencial.canal_id AND tipo = 'WHATSAPP') THEN
        RAISE EXCEPTION 'Credencial Z-API vigente nao encontrada';
    END IF;
    SELECT jsonb_agg(jsonb_build_object('canalId', id, 'ativo', ativo)) INTO anterior FROM canal;
    UPDATE canal SET ativo = false WHERE id <> credencial.canal_id AND ativo;
    UPDATE canal SET ativo = true WHERE id = credencial.canal_id AND NOT ativo;
    UPDATE canal_credencial SET ativo = true WHERE id = credencial.id AND NOT ativo;
    INSERT INTO audit_log(ator_tipo, acao, entidade_tipo, entidade_id, dados_antes, dados_depois)
    VALUES ('SISTEMA', 'ATIVAR_CANAL_Z_API_HML', 'CANAL_CREDENCIAL', credencial.id, anterior,
            jsonb_build_object('canalId', credencial.canal_id, 'ativo', true, 'executorSql', current_user));
END $$;
SELECT c.id AS canal_id, cc.id AS credencial_id, c.ativo AS canal_ativo, cc.ativo AS credencial_ativa
FROM canal c JOIN canal_credencial cc ON cc.canal_id = c.id WHERE cc.id = :'credencial_id'::uuid;
COMMIT;
