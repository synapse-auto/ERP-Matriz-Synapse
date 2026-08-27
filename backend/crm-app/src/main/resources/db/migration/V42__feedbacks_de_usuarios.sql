CREATE TYPE tipo_feedback AS ENUM ('SUGESTAO', 'ERRO');

CREATE TABLE feedback_usuario (
    id          UUID PRIMARY KEY,
    autor_id    UUID NOT NULL REFERENCES usuario(id),
    tipo        tipo_feedback NOT NULL,
    area_chave  VARCHAR(40) NOT NULL,
    descricao   TEXT NOT NULL,
    criado_em   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_feedback_descricao
        CHECK (length(btrim(descricao)) BETWEEN 1 AND 2000),
    CONSTRAINT chk_feedback_area
        CHECK (area_chave IN ('GERAL', 'ATENDIMENTOS', 'AGENDA', 'DASHBOARD', 'EQUIPE',
            'AUTOMACAO', 'MENSAGENS_PROGRAMADAS', 'LEMBRETES', 'TAGS', 'CONFIGURACOES'))
);

CREATE INDEX idx_feedback_criacao
    ON feedback_usuario (criado_em DESC, id DESC);
CREATE INDEX idx_feedback_tipo_criacao
    ON feedback_usuario (tipo, criado_em DESC, id DESC);

ALTER TABLE feedback_usuario ENABLE ROW LEVEL SECURITY;
ALTER TABLE feedback_usuario FORCE ROW LEVEL SECURITY;

CREATE POLICY rls_feedback_leitura ON feedback_usuario
    FOR SELECT
    USING (current_setting('app.papel', TRUE) = 'ADMINISTRADOR'
        OR autor_id = app_usuario_id());

CREATE POLICY rls_feedback_insercao ON feedback_usuario
    FOR INSERT
    WITH CHECK (autor_id = app_usuario_id());

GRANT SELECT, INSERT ON feedback_usuario TO synapse_app;

COMMENT ON TABLE feedback_usuario IS
    'Feedback textual de usuário interno; autoria sempre deriva da sessão autenticada.';
