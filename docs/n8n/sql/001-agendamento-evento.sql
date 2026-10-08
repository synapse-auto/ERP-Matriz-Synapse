-- Provisionamento versionado SOMENTE no banco proprio da integracao/n8n.
-- Fora do Flyway do CRM. Nao executar em banco que contenha o schema do CRM.
-- Uma execucao por banco, em processo controlado; divergencia nao e ignorada.
BEGIN;

DO $$
BEGIN
    IF to_regclass('public.flyway_schema_history') IS NOT NULL
       OR to_regclass('public.lead') IS NOT NULL
       OR to_regclass('public.atendimento') IS NOT NULL THEN
        RAISE EXCEPTION 'Provisionamento exclusivo do banco da integracao' USING ERRCODE = '22023';
    END IF;
END;
$$;

CREATE SCHEMA automacao_agendamentos;
REVOKE ALL ON SCHEMA automacao_agendamentos FROM PUBLIC;

CREATE TABLE automacao_agendamentos.agendamento_evento (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    clinica_id INTEGER NOT NULL CHECK (clinica_id > 0),
    schedule_id TEXT NOT NULL CHECK (btrim(schedule_id) <> ''),
    lead_id UUID,
    atendimento_id UUID,
    paciente_nome TEXT,
    telefone TEXT,
    profissional_nome TEXT,
    tipo_agendamento TEXT,
    agendado_para TIMESTAMPTZ,
    acao TEXT NOT NULL CHECK (acao IN ('CONFIRMADO', 'CANCELADO', 'REAGENDAMENTO_SOLICITADO')),
    descricao TEXT,
    resumo TEXT,
    origem TEXT NOT NULL DEFAULT 'LEMBRETE_N8N',
    idempotency_key TEXT NOT NULL CHECK (btrim(idempotency_key) <> ''),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT agendamento_evento_referencia_check CHECK (atendimento_id IS NULL OR lead_id IS NOT NULL),
    CONSTRAINT agendamento_evento_idempotency_key_key UNIQUE (clinica_id, idempotency_key)
);

CREATE INDEX agendamento_evento_schedule_idx
    ON automacao_agendamentos.agendamento_evento (clinica_id, schedule_id);
CREATE INDEX agendamento_evento_acao_data_idx
    ON automacao_agendamentos.agendamento_evento (acao, criado_em DESC);
CREATE INDEX agendamento_evento_atendimento_idx
    ON automacao_agendamentos.agendamento_evento (atendimento_id) WHERE atendimento_id IS NOT NULL;
CREATE INDEX agendamento_evento_lead_idx
    ON automacao_agendamentos.agendamento_evento (lead_id) WHERE lead_id IS NOT NULL;

-- Nao e SECURITY DEFINER: nenhuma elevacao de privilegio ou acesso ao banco do CRM.
-- Reexecucao identica retorna o id original. Conflito nunca sobrescreve o historico.
CREATE FUNCTION automacao_agendamentos.registrar_evento(dados JSONB, chave TEXT)
RETURNS TABLE (evento_id BIGINT, repetido BOOLEAN)
LANGUAGE plpgsql SECURITY INVOKER
SET search_path = pg_catalog
AS $$
DECLARE
    novo automacao_agendamentos.agendamento_evento%ROWTYPE;
    existente automacao_agendamentos.agendamento_evento%ROWTYPE;
BEGIN
    IF dados IS NULL OR jsonb_typeof(dados) <> 'object' THEN
        RAISE EXCEPTION 'Campos do evento invalidos' USING ERRCODE = '22023';
    END IF;
    IF EXISTS (
           SELECT 1 FROM jsonb_object_keys(dados) AS campo(nome)
           WHERE nome NOT IN ('clinica_id', 'schedule_id', 'lead_id', 'atendimento_id',
               'paciente_nome', 'telefone', 'profissional_nome', 'tipo_agendamento',
               'agendado_para', 'acao', 'descricao', 'resumo')
       ) THEN
        RAISE EXCEPTION 'Campos do evento invalidos' USING ERRCODE = '22023';
    END IF;

    novo := jsonb_populate_record(NULL::automacao_agendamentos.agendamento_evento, dados);
    INSERT INTO automacao_agendamentos.agendamento_evento AS evento
        (clinica_id, schedule_id, lead_id, atendimento_id, paciente_nome, telefone,
         profissional_nome, tipo_agendamento, agendado_para, acao, descricao, resumo, idempotency_key)
    VALUES (novo.clinica_id, novo.schedule_id, novo.lead_id, novo.atendimento_id,
        novo.paciente_nome, novo.telefone, novo.profissional_nome, novo.tipo_agendamento,
        novo.agendado_para, novo.acao, novo.descricao, novo.resumo, chave)
    ON CONFLICT (clinica_id, idempotency_key) DO NOTHING
    RETURNING evento.* INTO existente;

    IF FOUND THEN
        RETURN QUERY SELECT existente.id, false;
        RETURN;
    END IF;

    SELECT evento.* INTO STRICT existente FROM automacao_agendamentos.agendamento_evento evento
        WHERE evento.clinica_id = novo.clinica_id AND evento.idempotency_key = chave;
    IF ROW(existente.schedule_id, existente.lead_id, existente.atendimento_id,
        existente.paciente_nome, existente.telefone, existente.profissional_nome,
        existente.tipo_agendamento, existente.agendado_para, existente.acao, existente.descricao,
        existente.resumo) IS DISTINCT FROM ROW(novo.schedule_id, novo.lead_id, novo.atendimento_id,
        novo.paciente_nome, novo.telefone, novo.profissional_nome, novo.tipo_agendamento,
        novo.agendado_para, novo.acao, novo.descricao, novo.resumo) THEN
        RAISE EXCEPTION 'Chave de evento reutilizada com dados diferentes' USING ERRCODE = '23505';
    END IF;
    RETURN QUERY SELECT existente.id, true;
END;
$$;

CREATE VIEW automacao_agendamentos.vw_cancelamentos_agendamento WITH (security_invoker = true) AS
SELECT id, clinica_id, schedule_id, lead_id, atendimento_id, paciente_nome, telefone,
       profissional_nome, tipo_agendamento, agendado_para, resumo, descricao,
       CASE WHEN NULLIF(btrim(descricao), '') IS NULL THEN 'SEM_DESCRICAO'
            ELSE 'COM_DESCRICAO' END AS classificacao, criado_em
FROM automacao_agendamentos.agendamento_evento
WHERE acao = 'CANCELADO';

REVOKE ALL ON ALL TABLES IN SCHEMA automacao_agendamentos FROM PUBLIC;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA automacao_agendamentos FROM PUBLIC;
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA automacao_agendamentos FROM PUBLIC;

COMMENT ON TABLE automacao_agendamentos.agendamento_evento IS
    'Historico privado da integracao clinica; nao cria lembrete nem altera dados do CRM.';
COMMIT;
