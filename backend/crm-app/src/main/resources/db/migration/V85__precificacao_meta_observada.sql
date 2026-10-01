-- Classificacao informada pela Meta, nunca estimativa monetaria do CRM.
-- A fila estreita desacopla a apuracao da transacao do webhook e da conversa.
CREATE TABLE meta_precificacao_entrada (
    id_evento CHAR(64) PRIMARY KEY,
    wamid TEXT NOT NULL,
    ocorrido_em TIMESTAMPTZ NOT NULL,
    cobravel BOOLEAN,
    categoria VARCHAR(80),
    tipo VARCHAR(80),
    modelo VARCHAR(80),
    recebido_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    processado_em TIMESTAMPTZ,
    proxima_tentativa_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    tentativas SMALLINT NOT NULL DEFAULT 0,
    esgotado_em TIMESTAMPTZ
);

CREATE INDEX idx_meta_precificacao_entrada_pendente
    ON meta_precificacao_entrada (proxima_tentativa_em, recebido_em)
    WHERE processado_em IS NULL AND esgotado_em IS NULL;

CREATE TABLE meta_precificacao_observada (
    wamid TEXT PRIMARY KEY,
    entregue_em TIMESTAMPTZ NOT NULL,
    classificacao_em TIMESTAMPTZ,
    cobravel BOOLEAN,
    categoria VARCHAR(80),
    tipo VARCHAR(80),
    modelo VARCHAR(80),
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE meta_precificacao_observada IS
    'Classificacao do webhook Meta por wamid; valores monetarios e estimativas nao sao armazenados.';
COMMENT ON COLUMN meta_precificacao_observada.cobravel IS
    'NULL = entrega sem pricing.billable valido; TRUE/FALSE = informacao observada no webhook Meta.';

GRANT SELECT, INSERT, UPDATE ON meta_precificacao_entrada TO synapse_app;
GRANT SELECT, INSERT, UPDATE ON meta_precificacao_observada TO synapse_app;
