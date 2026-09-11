-- Permite editar mensagens textuais do chat interno sem alterar a identidade
-- da mensagem. O timestamp fica separado para a UI distinguir edições.
ALTER TABLE chat_interno_mensagem
    ADD COLUMN editado_em TIMESTAMPTZ;

COMMENT ON COLUMN chat_interno_mensagem.editado_em IS
    'Instante da ultima edicao textual pelo autor; nulo para mensagens nunca editadas.';

-- Citações podem estar em outra conversa. Atualizar a prévia aqui, com o owner
-- de RLS, mantém a referência sincronizada sem abrir uma segunda leitura no caso de uso.
CREATE OR REPLACE FUNCTION app_atualizar_citacoes_chat_editadas()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF OLD.conteudo IS DISTINCT FROM NEW.conteudo
       AND NEW.tipo = 'TEXTO'::tipo_mensagem
       AND NEW.removida_em IS NULL THEN
        UPDATE chat_interno_mensagem
           SET referencia_previa = left(
               regexp_replace(regexp_replace(coalesce(NEW.conteudo, ''), E'[\\n\\r\\t]+', ' ', 'g'),
                              ' +', ' ', 'g'), 120)
         WHERE referencia_origem_id = NEW.id
           AND referencia_origem_removida = FALSE;
    END IF;
    RETURN NEW;
END;
$$;

ALTER FUNCTION app_atualizar_citacoes_chat_editadas() OWNER TO synapse_chat_rls;
REVOKE EXECUTE ON FUNCTION app_atualizar_citacoes_chat_editadas() FROM PUBLIC;

CREATE TRIGGER trg_chat_interno_citacoes_editadas
AFTER UPDATE OF conteudo ON chat_interno_mensagem
FOR EACH ROW EXECUTE FUNCTION app_atualizar_citacoes_chat_editadas();
