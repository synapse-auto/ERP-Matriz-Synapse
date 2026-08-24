-- E44: leitura individual e isolamento do chat interno.
-- O indice ja existe em V10 nas bases novas; IF NOT EXISTS torna a migration segura
-- para ambientes que receberam V8 sem V10 ou foram provisionados parcialmente.
ALTER TABLE chat_interno_participante ADD COLUMN IF NOT EXISTS lido_ate TIMESTAMPTZ;
COMMENT ON COLUMN chat_interno_participante.lido_ate IS
    'Instante ate o qual este participante leu a conversa; leitura e individual.';

CREATE INDEX IF NOT EXISTS idx_chat_interno_msg_conversa
    ON chat_interno_mensagem (conversa_id, enviado_em);
COMMENT ON INDEX idx_chat_interno_msg_conversa IS
    'Pagina historico do chat interno por conversa e cursor temporal (E44).';

ALTER TABLE chat_interno_conversa ENABLE ROW LEVEL SECURITY;
ALTER TABLE chat_interno_conversa FORCE ROW LEVEL SECURITY;
ALTER TABLE chat_interno_participante ENABLE ROW LEVEL SECURITY;
ALTER TABLE chat_interno_participante FORCE ROW LEVEL SECURITY;
ALTER TABLE chat_interno_mensagem ENABLE ROW LEVEL SECURITY;
ALTER TABLE chat_interno_mensagem FORCE ROW LEVEL SECURITY;

-- Função SECURITY DEFINER evita recursão da política ao perguntar se o usuário
-- participa da própria conversa. Ela não retorna dados, apenas um booleano.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'synapse_chat_rls') THEN
        CREATE ROLE synapse_chat_rls NOLOGIN BYPASSRLS;
    END IF;
    EXECUTE format('GRANT synapse_chat_rls TO %I', current_user);
END
$$;

GRANT SELECT, INSERT ON chat_interno_conversa, chat_interno_participante TO synapse_chat_rls;
GRANT SELECT ON usuario TO synapse_chat_rls;

CREATE OR REPLACE FUNCTION app_chat_participa(conversa UUID)
RETURNS BOOLEAN STABLE LANGUAGE SQL SECURITY DEFINER SET search_path = public AS $$
    SELECT EXISTS (SELECT 1 FROM chat_interno_participante
                   WHERE conversa_id = $1 AND usuario_id = app_usuario_id());
$$;
ALTER FUNCTION app_chat_participa(UUID) OWNER TO synapse_chat_rls;

-- A criação direta é a única operação que insere os dois participantes de uma
-- vez. SECURITY DEFINER mantém o bootstrap fora da política genérica de INSERT:
-- ela não pode ser usada para entrar numa conversa já iniciada.
CREATE OR REPLACE FUNCTION app_criar_conversa_direta(primeiro UUID, segundo UUID)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    conversa UUID;
BEGIN
    IF app_usuario_id() IS NULL OR (app_usuario_id() <> primeiro AND app_usuario_id() <> segundo) THEN
        RAISE EXCEPTION 'usuario corrente nao pertence ao par da conversa';
    END IF;
    IF primeiro = segundo OR NOT EXISTS (SELECT 1 FROM usuario WHERE id = primeiro AND ativo)
            OR NOT EXISTS (SELECT 1 FROM usuario WHERE id = segundo AND ativo) THEN
        RAISE EXCEPTION 'par de usuarios invalido';
    END IF;

    PERFORM pg_advisory_xact_lock(hashtext('synapse:chat-interno:direta'));
    SELECT c.id INTO conversa
      FROM chat_interno_conversa c
      JOIN chat_interno_participante p1 ON p1.conversa_id = c.id AND p1.usuario_id = primeiro
      JOIN chat_interno_participante p2 ON p2.conversa_id = c.id AND p2.usuario_id = segundo
     WHERE c.tipo = 'DIRETA' LIMIT 1;
    IF conversa IS NOT NULL THEN
        RETURN conversa;
    END IF;

    conversa := gen_random_uuid();
    INSERT INTO chat_interno_conversa(id, tipo) VALUES (conversa, 'DIRETA');
    INSERT INTO chat_interno_participante(conversa_id, usuario_id)
        VALUES (conversa, primeiro), (conversa, segundo);
    RETURN conversa;
END;
$$;
ALTER FUNCTION app_criar_conversa_direta(UUID, UUID) OWNER TO synapse_chat_rls;

CREATE POLICY rls_chat_conversa ON chat_interno_conversa
    FOR ALL
    USING (app_chat_participa(id))
    WITH CHECK (app_usuario_id() IS NOT NULL);

CREATE POLICY rls_chat_participante ON chat_interno_participante
    FOR INSERT
    -- Participantes só entram pela função SECURITY DEFINER acima, que valida o
    -- par e insere os dois lados atomicamente. INSERT direto nunca faz bootstrap.
    WITH CHECK (FALSE);

CREATE POLICY rls_chat_participante_leitura ON chat_interno_participante
    FOR SELECT
    USING (usuario_id = app_usuario_id() OR app_chat_participa(conversa_id));

CREATE POLICY rls_chat_participante_atualizacao ON chat_interno_participante
    FOR UPDATE
    USING (usuario_id = app_usuario_id() OR app_chat_participa(conversa_id))
    WITH CHECK (usuario_id = app_usuario_id() AND app_chat_participa(conversa_id));

CREATE POLICY rls_chat_participante_remocao ON chat_interno_participante
    FOR DELETE
    USING (usuario_id = app_usuario_id() OR app_chat_participa(conversa_id));

CREATE POLICY rls_chat_mensagem ON chat_interno_mensagem
    FOR ALL
    USING (app_chat_participa(conversa_id))
    WITH CHECK (remetente_id = app_usuario_id() AND app_chat_participa(conversa_id));

REVOKE EXECUTE ON FUNCTION app_chat_participa(UUID) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION app_criar_conversa_direta(UUID, UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION app_chat_participa(UUID) TO synapse_app;
GRANT EXECUTE ON FUNCTION app_criar_conversa_direta(UUID, UUID) TO synapse_app;

-- A função já tem o owner correto; não deixe o usuário da migration manter
-- membership em uma role BYPASSRLS depois que o deploy terminar.
DO $$
BEGIN
    EXECUTE format('REVOKE synapse_chat_rls FROM %I', current_user);
END
$$;
