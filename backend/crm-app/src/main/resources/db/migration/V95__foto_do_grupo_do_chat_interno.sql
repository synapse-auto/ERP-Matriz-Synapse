-- Foto do grupo do Chat Interno.
--
-- chat_interno_conversa nao guardava quem criou o grupo, e a foto precisa de um dono: a gestao
-- decidiu em 01/09 (V54) que qualquer participante adiciona, remove e renomeia, mas trocar a imagem
-- do grupo e restrito a quem o criou. Por isso entram tres colunas:
--
--   criado_por_id       quem criou o grupo (NULL em conversa DIRETA e em grupo cujo criador foi
--                       apagado; nesse caso ninguem altera a foto, e a leitura segue valendo)
--   foto_referencia     chave do objeto no storage de avatares (prefixo grupo/); NULL = sem foto
--   foto_atualizada_em  versao da foto: entra na URL (?v=) para o navegador nunca mostrar a antiga
--
-- Tabela pequena e nao particionada: ADD COLUMN sem DEFAULT e so metadado, e o backfill e um UPDATE
-- unico. Nenhuma linha de mensagem e tocada.

ALTER TABLE chat_interno_conversa
    ADD COLUMN criado_por_id      UUID REFERENCES usuario (id) ON DELETE SET NULL,
    ADD COLUMN foto_referencia    TEXT,
    ADD COLUMN foto_atualizada_em TIMESTAMPTZ;

ALTER TABLE chat_interno_conversa
    ADD CONSTRAINT ck_chat_interno_conversa_foto_so_grupo
        CHECK (foto_referencia IS NULL OR tipo = 'GRUPO'),
    ADD CONSTRAINT ck_chat_interno_conversa_foto_versao
        CHECK ((foto_referencia IS NULL) = (foto_atualizada_em IS NULL));

COMMENT ON COLUMN chat_interno_conversa.criado_por_id IS
    'Criador do grupo; unico autorizado a trocar ou remover a foto. NULL em DIRETA ou se o usuario foi apagado.';
COMMENT ON COLUMN chat_interno_conversa.foto_referencia IS
    'Referencia opaca no storage de avatares (prefixo grupo/). Nunca vai ao navegador; a entrega passa pelo backend.';
COMMENT ON COLUMN chat_interno_conversa.foto_atualizada_em IS
    'Versao da foto, usada em ?v= para invalidar o cache do navegador quando a imagem muda.';

-- FK com ON DELETE SET NULL: sem indice, apagar um usuario varre a tabela inteira.
CREATE INDEX idx_chat_interno_conversa_criado_por
    ON chat_interno_conversa (criado_por_id)
    WHERE criado_por_id IS NOT NULL;

-- ---------------------------------------------------------
-- Backfill: o criador dos grupos existentes e o autor da mensagem SISTEMA GRUPO_CRIADO, gravada na
-- mesma transacao da criacao (CriarGrupoChatUseCase) e com remetente = quem criou (a RLS de
-- mensagem exige remetente = app_usuario_id()).
--
-- chat_interno_* tem FORCE ROW LEVEL SECURITY e a politica depende do USUARIO participante, nao do
-- papel SERVICO: sem trocar de papel, o dono da tabela (quem roda a migration) enxergaria zero
-- linhas e o backfill viraria um no-op silencioso. Mesmo padrao da V54: o papel
-- synapse_chat_rls (BYPASSRLS) e concedido so durante esta migration e revogado no fim.
-- ---------------------------------------------------------
DO $$
BEGIN
    EXECUTE format('GRANT synapse_chat_rls TO %I', current_user);
END
$$;

-- Nenhum GRANT/REVOKE em chat_interno_mensagem aqui: a V65 ja concedeu SELECT e UPDATE a
-- synapse_chat_rls em carater permanente (o trigger de citacoes e SECURITY DEFINER e depende deles).
-- Revogar no fim desta migration quebraria a exclusao de mensagens do chat.
SET LOCAL ROLE synapse_chat_rls;

UPDATE chat_interno_conversa c
   SET criado_por_id = origem.remetente_id
  FROM (
        SELECT DISTINCT ON (m.conversa_id) m.conversa_id, m.remetente_id
          FROM chat_interno_mensagem m
         WHERE m.tipo = 'SISTEMA'
           AND m.conteudo LIKE '{"evento":"GRUPO_CRIADO"%'
         ORDER BY m.conversa_id, m.enviado_em, m.id
       ) origem
 WHERE c.id = origem.conversa_id
   AND c.tipo = 'GRUPO'
   AND c.criado_por_id IS NULL
   AND EXISTS (SELECT 1 FROM usuario u WHERE u.id = origem.remetente_id);

RESET ROLE;

DO $$
BEGIN
    EXECUTE format('REVOKE synapse_chat_rls FROM %I', current_user);
END
$$;

-- ---------------------------------------------------------
-- Bootstrap de grupo: passa a registrar o criador. Mesma assinatura e mesmas validacoes da V54;
-- CREATE OR REPLACE preserva dono (synapse_chat_rls) e GRANT EXECUTE a synapse_app.
-- ---------------------------------------------------------
CREATE OR REPLACE FUNCTION app_criar_conversa_grupo(nome_grupo TEXT, participantes UUID[])
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    conversa UUID;
    criador UUID := app_usuario_id();
    membro UUID;
    vistos UUID[] := ARRAY[]::UUID[];
BEGIN
    IF criador IS NULL THEN
        RAISE EXCEPTION 'usuario corrente ausente';
    END IF;
    IF nome_grupo IS NULL OR length(btrim(nome_grupo)) = 0 THEN
        RAISE EXCEPTION 'nome do grupo e obrigatorio';
    END IF;
    IF participantes IS NULL OR cardinality(participantes) < 2 THEN
        RAISE EXCEPTION 'grupo exige ao menos dois participantes';
    END IF;
    IF NOT (criador = ANY (participantes)) THEN
        RAISE EXCEPTION 'criador precisa estar entre os participantes iniciais';
    END IF;

    FOREACH membro IN ARRAY participantes LOOP
        IF membro = ANY (vistos) THEN
            RAISE EXCEPTION 'participantes duplicados';
        END IF;
        IF NOT EXISTS (SELECT 1 FROM usuario WHERE id = membro AND ativo) THEN
            RAISE EXCEPTION 'participante invalido ou inativo';
        END IF;
        vistos := array_append(vistos, membro);
    END LOOP;

    conversa := gen_random_uuid();
    INSERT INTO chat_interno_conversa(id, tipo, nome, criado_por_id)
        VALUES (conversa, 'GRUPO', btrim(nome_grupo), criador);
    INSERT INTO chat_interno_participante(conversa_id, usuario_id)
        SELECT conversa, unnest(vistos);
    RETURN conversa;
END;
$$;
