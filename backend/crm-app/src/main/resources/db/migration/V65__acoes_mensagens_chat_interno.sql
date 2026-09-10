-- E176: paridade de ações de mensagem no chat interno.
--
-- Referências são resumidas e desnormalizadas de propósito: a bolha continua
-- explicando uma resposta/encaminhamento mesmo quando a mensagem de origem
-- foi removida. O conteúdo da origem nunca é copiado para uma mensagem removida.
-- A exclusão é tombstone para preservar auditoria e impedir que a mídia apagada
-- permaneça acessível pela mensagem histórica.

ALTER TABLE chat_interno_mensagem
    ADD COLUMN removida_em TIMESTAMPTZ,
    ADD COLUMN referencia_origem_id UUID REFERENCES chat_interno_mensagem(id) ON DELETE SET NULL,
    ADD COLUMN referencia_tipo TEXT,
    ADD COLUMN referencia_autor TEXT,
    ADD COLUMN referencia_tipo_conteudo TEXT,
    ADD COLUMN referencia_previa TEXT,
    ADD COLUMN referencia_origem_removida BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE chat_interno_mensagem
    ADD CONSTRAINT ck_chat_interno_referencia
    CHECK (
        (referencia_origem_id IS NULL AND referencia_tipo IS NULL
            AND referencia_autor IS NULL AND referencia_tipo_conteudo IS NULL
            AND referencia_previa IS NULL AND referencia_origem_removida = FALSE)
        OR (referencia_origem_id IS NOT NULL AND referencia_tipo IN ('RESPOSTA', 'ENCAMINHAMENTO')
            AND referencia_autor IS NOT NULL AND referencia_tipo_conteudo IS NOT NULL
            AND referencia_previa IS NOT NULL AND referencia_origem_removida IN (FALSE, TRUE))
        OR (referencia_origem_id IS NULL AND referencia_origem_removida = TRUE
            AND referencia_tipo IN ('RESPOSTA', 'ENCAMINHAMENTO')
            AND referencia_autor IS NOT NULL AND referencia_tipo_conteudo IS NOT NULL
            AND referencia_previa IS NOT NULL)
    );

CREATE INDEX idx_chat_interno_mensagem_referencia
    ON chat_interno_mensagem (referencia_origem_id)
    WHERE referencia_origem_id IS NOT NULL;

-- A origem e a citação podem estar em conversas diferentes. O trigger roda com o
-- privilégio do owner para atualizar as citações fora do escopo de RLS do autor.
CREATE OR REPLACE FUNCTION app_marcar_citacoes_chat_removidas()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        UPDATE chat_interno_mensagem
           SET referencia_origem_id = NULL, referencia_origem_removida = TRUE, referencia_previa = ''
         WHERE referencia_origem_id = OLD.id;
        RETURN OLD;
    END IF;
    IF OLD.removida_em IS NULL AND NEW.removida_em IS NOT NULL THEN
        UPDATE chat_interno_mensagem
           SET referencia_origem_removida = TRUE, referencia_previa = ''
         WHERE referencia_origem_id = NEW.id;
    END IF;
    RETURN NEW;
END;
$$;

ALTER FUNCTION app_marcar_citacoes_chat_removidas() OWNER TO synapse_chat_rls;

CREATE TRIGGER trg_chat_interno_citacoes_removidas
AFTER UPDATE OF removida_em ON chat_interno_mensagem
FOR EACH ROW EXECUTE FUNCTION app_marcar_citacoes_chat_removidas();

CREATE TRIGGER trg_chat_interno_citacoes_origem_excluida
BEFORE DELETE ON chat_interno_mensagem
FOR EACH ROW EXECUTE FUNCTION app_marcar_citacoes_chat_removidas();

REVOKE EXECUTE ON FUNCTION app_marcar_citacoes_chat_removidas() FROM PUBLIC;

COMMENT ON COLUMN chat_interno_mensagem.removida_em IS
    'Tombstone de exclusao pelo autor; conteudo e referencia da midia ficam inacessiveis na leitura.';
COMMENT ON COLUMN chat_interno_mensagem.referencia_previa IS
    'Resumo sanitizado da origem para responder/encaminhar, sem payload do provedor ou conteudo de midia.';

GRANT SELECT, UPDATE ON chat_interno_mensagem TO synapse_app;
