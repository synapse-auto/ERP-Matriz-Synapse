-- Reserva de Idempotency-Key dos comandos da Automacao sobre lead que nao passam pelo
-- ciclo EV-05 (etapa do atendimento, data de nascimento — E196): mesmo desenho de
-- comando_automacao_idempotencia (V32), com o escopo trocado de atendimento_id para
-- lead_id, porque estes comandos nao exigem um atendimento EM_ATENDIMENTO em curso.
CREATE TABLE comando_automacao_lead_idempotencia (
    idempotency_key VARCHAR(255) PRIMARY KEY,
    operacao        VARCHAR(80) NOT NULL,
    lead_id         UUID NOT NULL REFERENCES lead(id) ON DELETE CASCADE,
    requisicao_hash CHAR(64) NOT NULL,
    resposta        JSONB,
    criado_em       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_comando_automacao_lead
    ON comando_automacao_lead_idempotencia (lead_id, criado_em);

COMMENT ON TABLE comando_automacao_lead_idempotencia IS
    'Reserva duravel de Idempotency-Key dos comandos internos da Automacao sobre o lead (etapa, data de nascimento); resposta preenchida na mesma transacao dos efeitos.';
