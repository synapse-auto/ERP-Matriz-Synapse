-- E87: citacao de resposta/encaminhamento e wamid para context.message_id da Meta.
-- A PK de mensagem e composta (id, enviado_em); FKs seguem o precedente da V45.
-- citacao_* e denormalizada de proposito: se a origem sumir, a bolha ainda mostra
-- um resumo autorizado, sem telefone, token ou payload do provedor.

CREATE TABLE mensagem_id_externo (
    wamid                   TEXT PRIMARY KEY,
    mensagem_id             UUID NOT NULL,
    mensagem_enviada_em     TIMESTAMPTZ NOT NULL,
    atendimento_id          UUID NOT NULL REFERENCES atendimento(id),
    criado_em               TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (mensagem_id, mensagem_enviada_em)
        REFERENCES mensagem (id, enviado_em) ON DELETE CASCADE,
    CONSTRAINT uq_mensagem_id_externo_mensagem
        UNIQUE (mensagem_id, mensagem_enviada_em)
);

COMMENT ON TABLE mensagem_id_externo IS
    'Identificador externo (wamid) da mensagem no provedor. Necessario para responder no WhatsApp.';

CREATE INDEX idx_mensagem_id_externo_atendimento
    ON mensagem_id_externo (atendimento_id);

CREATE TABLE mensagem_referencia (
    mensagem_id                 UUID NOT NULL,
    mensagem_enviada_em         TIMESTAMPTZ NOT NULL,
    tipo                        TEXT NOT NULL,
    origem_mensagem_id          UUID NOT NULL,
    origem_enviada_em           TIMESTAMPTZ NOT NULL,
    origem_atendimento_id       UUID NOT NULL,
    citacao_autor               TEXT NOT NULL,
    citacao_tipo                TEXT NOT NULL,
    citacao_previa              TEXT NOT NULL,
    PRIMARY KEY (mensagem_id, mensagem_enviada_em),
    FOREIGN KEY (mensagem_id, mensagem_enviada_em)
        REFERENCES mensagem (id, enviado_em) ON DELETE CASCADE,
    CONSTRAINT ck_mensagem_referencia_tipo
        CHECK (tipo IN ('RESPOSTA', 'ENCAMINHAMENTO'))
);

COMMENT ON TABLE mensagem_referencia IS
    'Vinculo de resposta ou encaminhamento. A origem nao e FK: a citacao sobrevive se a mensagem original nao puder mais ser lida.';
COMMENT ON COLUMN mensagem_referencia.citacao_previa IS
    'Trecho sanitizado para a bolha; nunca telefone, token ou payload do provedor.';

GRANT SELECT, INSERT, UPDATE, DELETE ON mensagem_id_externo TO synapse_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON mensagem_referencia TO synapse_app;

-- Mensagens ja enviadas pela Automacao ja tinham wamid; sem este recopie, responder a elas
-- falharia ate o proximo envio da IA.
INSERT INTO mensagem_id_externo (wamid, mensagem_id, mensagem_enviada_em, atendimento_id)
SELECT wamid, mensagem_id, enviado_em, atendimento_id
  FROM mensagem_automacao_idempotencia
ON CONFLICT (wamid) DO NOTHING;
