-- E84: reacoes reais em atendimento e chat interno.
-- Tabelas separadas de proposito: mensagem e particionada com PK composta
-- (id, enviado_em); chat_interno_mensagem tem PK simples. Uma tabela
-- polimorfica nao teria FK para nenhum dos dois lados.

CREATE TABLE mensagem_reacao (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mensagem_id             UUID NOT NULL,
    mensagem_enviada_em     TIMESTAMPTZ NOT NULL,
    usuario_id              UUID NOT NULL REFERENCES usuario(id),
    emoji                   VARCHAR(32) NOT NULL,
    criado_em               TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (mensagem_id, mensagem_enviada_em)
        REFERENCES mensagem (id, enviado_em) ON DELETE CASCADE,
    CONSTRAINT uq_mensagem_reacao_usuario
        UNIQUE (mensagem_id, mensagem_enviada_em, usuario_id)
);

COMMENT ON TABLE mensagem_reacao IS
    'Uma reacao por usuario por mensagem de atendimento. A FK composta ancora a particao de mensagem.';
COMMENT ON CONSTRAINT uq_mensagem_reacao_usuario ON mensagem_reacao IS
    'Substitui a reacao propria; duas pessoas podem usar o mesmo emoji.';

-- O UNIQUE (mensagem_id, mensagem_enviada_em, usuario_id) ja cobre o caminho
-- de leitura por mensagem sem varrer particoes da tabela pai.

CREATE TABLE chat_interno_mensagem_reacao (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mensagem_id  UUID NOT NULL REFERENCES chat_interno_mensagem(id) ON DELETE CASCADE,
    usuario_id   UUID NOT NULL REFERENCES usuario(id),
    emoji        VARCHAR(32) NOT NULL,
    criado_em    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_chat_interno_mensagem_reacao_usuario UNIQUE (mensagem_id, usuario_id)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON mensagem_reacao TO synapse_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON chat_interno_mensagem_reacao TO synapse_app;

COMMENT ON TABLE chat_interno_mensagem_reacao IS
    'Uma reacao por usuario por mensagem do chat interno. Isolada de mensagem_reacao.';

ALTER TABLE chat_interno_mensagem_reacao ENABLE ROW LEVEL SECURITY;
ALTER TABLE chat_interno_mensagem_reacao FORCE ROW LEVEL SECURITY;

CREATE POLICY rls_chat_reacao_leitura ON chat_interno_mensagem_reacao
    FOR SELECT
    USING (EXISTS (
        SELECT 1 FROM chat_interno_mensagem m
         WHERE m.id = mensagem_id AND app_chat_participa(m.conversa_id)
    ));

CREATE POLICY rls_chat_reacao_insercao ON chat_interno_mensagem_reacao
    FOR INSERT
    WITH CHECK (
        usuario_id = app_usuario_id()
        AND EXISTS (
            SELECT 1 FROM chat_interno_mensagem m
             WHERE m.id = mensagem_id AND app_chat_participa(m.conversa_id)
        )
    );

CREATE POLICY rls_chat_reacao_atualizacao ON chat_interno_mensagem_reacao
    FOR UPDATE
    USING (
        usuario_id = app_usuario_id()
        AND EXISTS (
            SELECT 1 FROM chat_interno_mensagem m
             WHERE m.id = mensagem_id AND app_chat_participa(m.conversa_id)
        )
    )
    WITH CHECK (
        usuario_id = app_usuario_id()
        AND EXISTS (
            SELECT 1 FROM chat_interno_mensagem m
             WHERE m.id = mensagem_id AND app_chat_participa(m.conversa_id)
        )
    );

CREATE POLICY rls_chat_reacao_remocao ON chat_interno_mensagem_reacao
    FOR DELETE
    USING (
        usuario_id = app_usuario_id()
        AND EXISTS (
            SELECT 1 FROM chat_interno_mensagem m
             WHERE m.id = mensagem_id AND app_chat_participa(m.conversa_id)
        )
    );
