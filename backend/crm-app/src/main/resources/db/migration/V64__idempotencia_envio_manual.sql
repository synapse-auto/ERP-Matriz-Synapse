-- A chave gerada pelo navegador identifica uma tentativa humana, não o conteúdo.
-- A tabela fica fora da mensagem particionada para garantir unicidade entre partições.
CREATE TABLE mensagem_envio_idempotencia (
    chave_idempotencia VARCHAR(255) PRIMARY KEY,
    usuario_id        UUID NOT NULL REFERENCES usuario(id),
    lead_id           UUID NOT NULL REFERENCES lead(id),
    atendimento_id    UUID NOT NULL REFERENCES atendimento(id),
    mensagem_id       UUID,
    mensagem_enviada_em TIMESTAMPTZ,
    transferiu_lead   BOOLEAN NOT NULL DEFAULT FALSE,
    criado_em         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_mensagem_envio_idempotencia_atendimento
    ON mensagem_envio_idempotencia (atendimento_id, criado_em);

COMMENT ON TABLE mensagem_envio_idempotencia IS
    'Chaves do envio humano: uma mesma chave, usuario, lead e atendimento retornam a mensagem original sem duplicar outbox.';
