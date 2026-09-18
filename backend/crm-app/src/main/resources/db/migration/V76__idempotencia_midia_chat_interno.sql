-- Uma tentativa de upload pode chegar novamente quando a resposta multipart se perde no navegador.
-- A reserva fica fora da mensagem para garantir unicidade antes de persistir o objeto no storage;
-- conversa, remetente e impressão impedem que uma chave seja reutilizada para outro conteúdo.
CREATE TABLE chat_interno_midia_idempotencia (
    chave_idempotencia   VARCHAR(255) PRIMARY KEY,
    remetente_id         UUID NOT NULL REFERENCES usuario(id),
    conversa_id          UUID NOT NULL REFERENCES chat_interno_conversa(id) ON DELETE CASCADE,
    impressao_requisicao CHAR(64) NOT NULL,
    mensagem_id          UUID REFERENCES chat_interno_mensagem(id) ON DELETE SET NULL,
    criado_em            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_chat_interno_midia_idempotencia_conversa
    ON chat_interno_midia_idempotencia (conversa_id, criado_em);

COMMENT ON TABLE chat_interno_midia_idempotencia IS
    'Reserva de upload do chat interno: repetir a mesma chave devolve a mensagem original sem novo objeto ou evento.';
