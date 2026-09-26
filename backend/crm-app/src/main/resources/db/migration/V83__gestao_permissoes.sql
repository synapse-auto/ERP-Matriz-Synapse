-- Gestao: permissoes por perfil, excecoes por usuario e historico (docs/47).
--
-- Modelo:
--   * O catalogo de capacidades vive no codigo (Capacidade.java). O banco guarda so o que foi
--     configurado; chave ausente = padrao do catalogo (perfil) ou herdar (excecao). Por isso esta
--     migration nao semeia valores: o padrao do catalogo reproduz exatamente o acesso operacional
--     de antes, e a instancia sobe sem nenhuma revogacao.
--   * GESTOR e ADMINISTRADOR tem acesso fixo ao teto do papel e nao tem linha de perfil.
--   * Cada escopo (perfil ou usuario) tem revisao propria para controle otimista de concorrencia:
--     salvar exige a revisao lida; outra revisao = 409, nunca sobrescrita silenciosa.
--   * permissao_politica.revisao e a revisao global: sobe em toda mudanca que altera acesso
--     efetivo (permissao, papel, desativacao). Os nos da aplicacao a consultam para invalidar o
--     cache de permissoes efetivas com atraso maximo configurado (SYNAPSE_PERMISSOES_REVALIDACAO).
--   * permissao_historico e gravado na MESMA transacao da mudanca: nao existe alteracao de
--     permissao sem autor, alvo, antes/depois e revisao.
--
-- Sem RLS de proposito: nenhuma destas tabelas tem dado de lead ou de conversa; qualquer usuario
-- autenticado precisa ler o proprio efetivo, e toda escrita passa por caso de uso com
-- @PreAuthorize e validacao de alcada. Sem tenant_id: isolamento continua fisico (modelo Silo).

CREATE TABLE permissao_politica (
    id            SMALLINT PRIMARY KEY CHECK (id = 1),
    revisao       BIGINT NOT NULL DEFAULT 0,
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO permissao_politica (id) VALUES (1);

COMMENT ON TABLE permissao_politica IS
    'Revisao global das permissoes efetivas. Sobe a cada alteracao de acesso; invalida caches.';

CREATE TABLE permissao_perfil (
    papel          papel_usuario PRIMARY KEY CHECK (papel IN ('SUBGESTOR', 'ATENDENTE')),
    revisao        BIGINT NOT NULL DEFAULT 0,
    atualizado_em  TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_por UUID REFERENCES usuario (id)
);
INSERT INTO permissao_perfil (papel) VALUES ('SUBGESTOR'), ('ATENDENTE');

COMMENT ON TABLE permissao_perfil IS
    'Perfis configuraveis. GESTOR/ADMINISTRADOR sao fixos e nao tem linha.';

CREATE TABLE permissao_perfil_item (
    papel papel_usuario NOT NULL REFERENCES permissao_perfil (papel),
    tipo  VARCHAR(5)  NOT NULL CHECK (tipo IN ('NIVEL', 'ACAO')),
    alvo  VARCHAR(80) NOT NULL,
    valor VARCHAR(10) NOT NULL,
    PRIMARY KEY (papel, tipo, alvo),
    CONSTRAINT permissao_perfil_item_valor CHECK (
        (tipo = 'NIVEL' AND valor IN ('SEM_ACESSO', 'VER', 'EDITAR', 'GERENCIAR'))
        OR (tipo = 'ACAO' AND valor IN ('PERMITIR', 'NEGAR')))
);

COMMENT ON COLUMN permissao_perfil_item.alvo IS
    'Id estavel do modulo (tipo NIVEL) ou da capacidade (tipo ACAO), ex.: tags / tags.criar.';

CREATE TABLE permissao_usuario (
    usuario_id     UUID PRIMARY KEY REFERENCES usuario (id),
    revisao        BIGINT NOT NULL DEFAULT 0,
    atualizado_em  TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_por UUID REFERENCES usuario (id)
);

COMMENT ON TABLE permissao_usuario IS
    'Revisao das excecoes de cada usuario. Linha criada na primeira gravacao.';

CREATE TABLE permissao_usuario_excecao (
    usuario_id UUID NOT NULL REFERENCES permissao_usuario (usuario_id) ON DELETE CASCADE,
    tipo       VARCHAR(5)  NOT NULL CHECK (tipo IN ('NIVEL', 'ACAO')),
    alvo       VARCHAR(80) NOT NULL,
    valor      VARCHAR(10) NOT NULL,
    PRIMARY KEY (usuario_id, tipo, alvo),
    CONSTRAINT permissao_usuario_excecao_valor CHECK (
        (tipo = 'NIVEL' AND valor IN ('SEM_ACESSO', 'VER', 'EDITAR', 'GERENCIAR'))
        OR (tipo = 'ACAO' AND valor IN ('PERMITIR', 'NEGAR')))
);

COMMENT ON TABLE permissao_usuario_excecao IS
    'Excecao explicita sobre o perfil. Ausencia = herdar. Restaurar = apagar a linha.';

CREATE TABLE permissao_historico (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    escopo            VARCHAR(7)  NOT NULL CHECK (escopo IN ('PERFIL', 'USUARIO')),
    papel             papel_usuario,
    usuario_id        UUID REFERENCES usuario (id),
    operacao          VARCHAR(30) NOT NULL,
    revisao_anterior  BIGINT NOT NULL,
    revisao_nova      BIGINT NOT NULL,
    antes             JSONB NOT NULL,
    depois            JSONB NOT NULL,
    origem_papel      papel_usuario,
    origem_usuario_id UUID REFERENCES usuario (id),
    autor_id          UUID REFERENCES usuario (id),
    criado_em         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT permissao_historico_alvo CHECK (
        (escopo = 'PERFIL' AND papel IS NOT NULL AND usuario_id IS NULL)
        OR (escopo = 'USUARIO' AND usuario_id IS NOT NULL))
);

COMMENT ON TABLE permissao_historico IS
    'Historico atomico de alteracoes de permissao: autor, alvo, antes/depois, revisao e operacao. Sem senha, token ou conteudo de conversa.';

CREATE INDEX idx_permissao_historico_usuario ON permissao_historico (usuario_id, criado_em DESC)
    WHERE usuario_id IS NOT NULL;
CREATE INDEX idx_permissao_historico_perfil ON permissao_historico (papel, criado_em DESC)
    WHERE escopo = 'PERFIL';

-- A revisao global tambem sobe quando papel/situacao de um usuario muda ou quando alguem mexe nas
-- tabelas de permissao direto pelo psql. Sem isto, uma correcao manual no banco nunca invalidaria o
-- cache dos nos: o JWT antigo continuaria aceito com o papel antigo ate expirar e o cache nunca
-- recalcularia. A aplicacao tambem incrementa explicitamente (para devolver a revisao nova); a
-- dupla contagem nao tem efeito algum alem de numeros maiores.
CREATE OR REPLACE FUNCTION permissao_incrementar_revisao()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    UPDATE permissao_politica SET revisao = revisao + 1, atualizado_em = now() WHERE id = 1;
    RETURN NULL;
END;
$$;

CREATE TRIGGER trg_usuario_acesso_alterado
    AFTER UPDATE OF papel, ativo ON usuario
    FOR EACH ROW
    WHEN (OLD.papel IS DISTINCT FROM NEW.papel OR OLD.ativo IS DISTINCT FROM NEW.ativo)
    EXECUTE FUNCTION permissao_incrementar_revisao();

CREATE TRIGGER trg_permissao_perfil_item_alterado
    AFTER INSERT OR UPDATE OR DELETE ON permissao_perfil_item
    FOR EACH STATEMENT EXECUTE FUNCTION permissao_incrementar_revisao();

CREATE TRIGGER trg_permissao_usuario_excecao_alterada
    AFTER INSERT OR UPDATE OR DELETE ON permissao_usuario_excecao
    FOR EACH STATEMENT EXECUTE FUNCTION permissao_incrementar_revisao();

-- Ultimo responsavel administrativo ativo (GESTOR ou ADMINISTRADOR) nunca pode ser desativado nem
-- rebaixado — nem por operacoes concorrentes, nem pelo psql. A API ja nao oferece essas operacoes
-- sobre GESTOR/ADMINISTRADOR; esta trava cobre o que a API nao alcanca. O advisory lock serializa
-- as alteracoes concorrentes: em READ COMMITTED, a contagem feita depois de obter o lock ja enxerga
-- o commit de quem o obteve antes, entao duas transacoes nao rebaixam "o outro" ao mesmo tempo.
CREATE OR REPLACE FUNCTION usuario_garantir_responsavel_administrativo()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    restantes INTEGER;
BEGIN
    IF OLD.ativo AND OLD.papel IN ('GESTOR', 'ADMINISTRADOR')
       AND (NOT NEW.ativo OR NEW.papel NOT IN ('GESTOR', 'ADMINISTRADOR')) THEN
        PERFORM pg_advisory_xact_lock(hashtext('synapse:responsavel-administrativo'));
        SELECT count(*) INTO restantes
          FROM usuario
         WHERE ativo = TRUE AND papel IN ('GESTOR', 'ADMINISTRADOR') AND id <> OLD.id;
        IF restantes = 0 THEN
            RAISE EXCEPTION 'ultimo responsavel administrativo ativo nao pode ser desativado ou rebaixado'
                USING ERRCODE = 'check_violation';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_usuario_responsavel_administrativo
    BEFORE UPDATE OF papel, ativo ON usuario
    FOR EACH ROW EXECUTE FUNCTION usuario_garantir_responsavel_administrativo();
