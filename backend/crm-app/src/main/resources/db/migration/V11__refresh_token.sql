-- =========================================================
-- Refresh tokens com rotacao.
--
-- A aplicacao e stateless (nada em memoria de instancia), mas rotacao de
-- refresh exige saber qual token ja foi usado. Esse estado mora no banco,
-- que todas as instancias enxergam — e o que permite escalar horizontalmente
-- sem sticky session.
--
-- Guardamos o SHA-256 do token, nunca o token: vazamento de backup nao vira
-- sessao valida. Mesmo principio de canal_credencial.token_ref.
-- =========================================================

CREATE TABLE refresh_token (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id  UUID NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    -- Todos os tokens descendentes de um mesmo login. Reuso de token revogado
    -- e sinal de roubo: derruba a familia inteira, nao so aquele token.
    familia     UUID NOT NULL,
    expira_em   TIMESTAMPTZ NOT NULL,
    revogado_em TIMESTAMPTZ,
    criado_em   TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON COLUMN refresh_token.token_hash IS
    'SHA-256 do token em hexadecimal. O token em si nunca e persistido.';
COMMENT ON COLUMN refresh_token.familia IS
    'Cadeia de rotacao de um login. Reuso de token revogado revoga a familia inteira.';

-- O caminho quente e "achar por hash": e o que toda renovacao faz.
-- O UNIQUE em token_hash ja cobre essa busca.
CREATE INDEX idx_refresh_token_usuario ON refresh_token (usuario_id);
CREATE INDEX idx_refresh_token_familia_ativa
    ON refresh_token (familia) WHERE revogado_em IS NULL;
