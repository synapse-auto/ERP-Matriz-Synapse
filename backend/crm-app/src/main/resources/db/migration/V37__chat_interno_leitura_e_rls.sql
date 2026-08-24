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

CREATE OR REPLACE FUNCTION app_chat_participa(conversa UUID)
RETURNS BOOLEAN LANGUAGE SQL SECURITY DEFINER SET search_path = public AS $$
    SELECT EXISTS (SELECT 1 FROM chat_interno_participante
                   WHERE conversa_id = $1 AND usuario_id = app_usuario_id());
$$;
ALTER FUNCTION app_chat_participa(UUID) OWNER TO synapse_chat_rls;

CREATE POLICY rls_chat_conversa ON chat_interno_conversa
    FOR ALL
    USING (app_chat_participa(id))
    WITH CHECK (app_usuario_id() IS NOT NULL);

CREATE POLICY rls_chat_participante ON chat_interno_participante
    FOR ALL
    USING (usuario_id = app_usuario_id() OR app_chat_participa(conversa_id))
    WITH CHECK (usuario_id = app_usuario_id() OR app_chat_participa(conversa_id));

CREATE POLICY rls_chat_mensagem ON chat_interno_mensagem
    FOR ALL
    USING (app_chat_participa(conversa_id))
    WITH CHECK (remetente_id = app_usuario_id() AND app_chat_participa(conversa_id));
