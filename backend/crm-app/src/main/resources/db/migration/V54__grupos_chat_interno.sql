-- =========================================================
-- E122: grupos no chat interno.
--
-- tipo_conversa_chat ja tem GRUPO desde a V1; chat_interno_participante ja e n:n.
-- Faltavam: nome do grupo, bootstrap SECURITY DEFINER (ovo e galinha da RLS),
-- INSERT de participante aberto so para quem ja esta num GRUPO, e tipo SISTEMA
-- para o rastro de quem entrou/saiu/renomeou.
--
-- A V37 deixou INSERT em participante com WITH CHECK (FALSE) — so a funcao
-- direta fazia bootstrap. A gestao decidiu em 01/09 que qualquer participante
-- de grupo pode adicionar/remover: isso exige abrir o INSERT, mas so para GRUPO
-- (DIRETA continua recusando terceiro). DELETE ja era "qualquer participante".
-- =========================================================

-- Nome do grupo. DIRETA continua NULL (deriva do outro participante).
ALTER TABLE chat_interno_conversa ADD COLUMN nome TEXT;

ALTER TABLE chat_interno_conversa
    ADD CONSTRAINT ck_chat_interno_conversa_nome_grupo
    CHECK (
        (tipo = 'DIRETA' AND nome IS NULL)
        OR (tipo = 'GRUPO' AND nome IS NOT NULL AND length(btrim(nome)) > 0)
    );

COMMENT ON COLUMN chat_interno_conversa.nome IS
    'Nome exibido do grupo. NULL em conversa DIRETA. Obrigatorio quando tipo = GRUPO.';

-- Eventos de sistema (criacao, entrada, saida, rename). O remetente e o ator
-- humano que causou o evento — a RLS de mensagem (remetente = app_usuario_id)
-- continua valendo sem exception path.
ALTER TYPE tipo_mensagem ADD VALUE IF NOT EXISTS 'SISTEMA';

-- ---------------------------------------------------------
-- INSERT de participante: participante de GRUPO pode adicionar outro.
-- Bootstrap inicial continua so em app_criar_conversa_grupo / direta.
-- ---------------------------------------------------------
DROP POLICY IF EXISTS rls_chat_participante ON chat_interno_participante;

CREATE POLICY rls_chat_participante ON chat_interno_participante
    FOR INSERT
    WITH CHECK (
        app_chat_participa(conversa_id)
        AND EXISTS (
            SELECT 1 FROM chat_interno_conversa c
             WHERE c.id = conversa_id AND c.tipo = 'GRUPO'
        )
        AND EXISTS (
            SELECT 1 FROM usuario u
             WHERE u.id = usuario_id AND u.ativo
        )
    );

COMMENT ON POLICY rls_chat_participante ON chat_interno_participante IS
    'E122: quem ja participa de um GRUPO pode adicionar outro usuario ativo. '
    'DIRETA permanece fechada a INSERT direto (terceiro e recusado). '
    'Bootstrap de conversa nova continua nas funcoes SECURITY DEFINER.';

-- ---------------------------------------------------------
-- Bootstrap de grupo. Espelha app_criar_conversa_direta: SECURITY DEFINER
-- estreito, criador obrigatorio entre os iniciais, nao serve para entrar em
-- conversa ja existente.
-- ---------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'synapse_chat_rls') THEN
        CREATE ROLE synapse_chat_rls NOLOGIN BYPASSRLS;
    END IF;
    EXECUTE format('GRANT synapse_chat_rls TO %I', current_user);
END
$$;

GRANT SELECT, INSERT, UPDATE, DELETE ON chat_interno_conversa TO synapse_chat_rls;
GRANT SELECT, INSERT, DELETE ON chat_interno_participante TO synapse_chat_rls;
GRANT SELECT ON usuario TO synapse_chat_rls;

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
    INSERT INTO chat_interno_conversa(id, tipo, nome)
        VALUES (conversa, 'GRUPO', btrim(nome_grupo));
    INSERT INTO chat_interno_participante(conversa_id, usuario_id)
        SELECT conversa, unnest(vistos);
    RETURN conversa;
END;
$$;

ALTER FUNCTION app_criar_conversa_grupo(TEXT, UUID[]) OWNER TO synapse_chat_rls;

REVOKE EXECUTE ON FUNCTION app_criar_conversa_grupo(TEXT, UUID[]) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION app_criar_conversa_grupo(TEXT, UUID[]) TO synapse_app;

-- Ultimo a sair: a RLS de conversa exige app_chat_participa, que falha com zero
-- participantes. Apagar orfao precisa de SECURITY DEFINER estreito (so se vazia).
CREATE OR REPLACE FUNCTION app_apagar_conversa_chat_se_vazia(conversa UUID)
RETURNS BOOLEAN LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
    IF conversa IS NULL THEN
        RETURN FALSE;
    END IF;
    IF EXISTS (SELECT 1 FROM chat_interno_participante WHERE conversa_id = conversa) THEN
        RETURN FALSE;
    END IF;
    DELETE FROM chat_interno_conversa WHERE id = conversa;
    RETURN FOUND;
END;
$$;

ALTER FUNCTION app_apagar_conversa_chat_se_vazia(UUID) OWNER TO synapse_chat_rls;
REVOKE EXECUTE ON FUNCTION app_apagar_conversa_chat_se_vazia(UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION app_apagar_conversa_chat_se_vazia(UUID) TO synapse_app;

DO $$
BEGIN
    EXECUTE format('REVOKE synapse_chat_rls FROM %I', current_user);
END
$$;