-- =========================================================
-- Chat interno entre atendentes e gestores.
-- Pertence ao modulo crm-equipe (ver CLAUDE.md).
-- =========================================================

CREATE TABLE chat_interno_conversa (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo      tipo_conversa_chat NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE chat_interno_participante (
    conversa_id UUID NOT NULL REFERENCES chat_interno_conversa(id) ON DELETE CASCADE,
    usuario_id  UUID NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    PRIMARY KEY (conversa_id, usuario_id)
);

CREATE TABLE chat_interno_mensagem (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversa_id     UUID NOT NULL REFERENCES chat_interno_conversa(id) ON DELETE CASCADE,
    remetente_id    UUID NOT NULL REFERENCES usuario(id),
    tipo            tipo_mensagem NOT NULL,
    conteudo        TEXT,
    midia_url       TEXT,
    midia_metadados JSONB,
    enviado_em      TIMESTAMPTZ NOT NULL DEFAULT now()
);
