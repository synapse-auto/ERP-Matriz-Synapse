-- Cada comando de escrita da Automacao recebe uma chave persistida.
-- A reserva e os efeitos do comando vivem na mesma transacao: uma falha faz
-- rollback da reserva e o n8n pode repetir a chamada sem efeitos parciais.
CREATE TABLE comando_automacao_idempotencia (
    idempotency_key VARCHAR(255) PRIMARY KEY,
    operacao        VARCHAR(80) NOT NULL,
    atendimento_id  UUID NOT NULL REFERENCES atendimento(id) ON DELETE CASCADE,
    requisicao_hash CHAR(64) NOT NULL,
    resposta        JSONB,
    criado_em       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_comando_automacao_atendimento
    ON comando_automacao_idempotencia (atendimento_id, criado_em);

COMMENT ON TABLE comando_automacao_idempotencia IS
    'Reserva duravel de Idempotency-Key dos comandos internos da Automacao; resposta preenchida na mesma transacao dos efeitos.';
