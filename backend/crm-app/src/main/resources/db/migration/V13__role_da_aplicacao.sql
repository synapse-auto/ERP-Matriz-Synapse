-- =========================================================
-- Role sem privilegio de dono, para que as politicas RLS valham.
--
-- Descoberto pelos testes da E02b: a V12 sozinha nao protegia nada. O usuario
-- da aplicacao e dono das tabelas (foi ele quem rodou as migrations) e, no
-- ambiente de teste, tambem e superusuario. Nas duas condicoes o Postgres
-- ignora RLS:
--   - dono da tabela: contornado por FORCE ROW LEVEL SECURITY (a V12 ja faz);
--   - SUPERUSER: contorna SEMPRE, e nem FORCE alcanca.
--
-- A saida e a transacao deixar de rodar como dono: o aplicador de contexto
-- executa SET LOCAL ROLE synapse_app no inicio de toda transacao. Assim a
-- protecao nao depende de como a string de conexao foi provisionada — mesmo
-- um deploy que aponte para um superusuario continua sujeito as politicas.
-- =========================================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'synapse_app') THEN
        CREATE ROLE synapse_app NOLOGIN;
    END IF;
END
$$;

-- A role e assumida com SET ROLE, nunca usada para conectar: por isso NOLOGIN.
-- O usuario que conecta precisa ser membro dela.
DO $$
BEGIN
    EXECUTE format('GRANT synapse_app TO %I', current_user);
END
$$;

GRANT USAGE ON SCHEMA public TO synapse_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO synapse_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO synapse_app;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO synapse_app;

-- Tabelas criadas por migrations futuras herdam os mesmos privilegios, senao
-- a proxima etapa quebraria com "permission denied" sem motivo aparente.
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO synapse_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO synapse_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT EXECUTE ON FUNCTIONS TO synapse_app;

COMMENT ON ROLE synapse_app IS
    'Role assumida por SET LOCAL ROLE a cada transacao da aplicacao. Nao e dona de nada, '
    'e por isso as politicas RLS se aplicam a ela. Migrations continuam rodando como o dono.';
