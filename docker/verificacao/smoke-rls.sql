\set ON_ERROR_STOP on

-- RLS so vale dentro de transacao: SET LOCAL ROLE e set_config(..., TRUE)
-- devem morrer junto com ela, exatamente como no AplicadorDeContextoRls.
-- O ROLLBACK final e a limpeza do caminho de sucesso. Se qualquer verificacao
-- levantar excecao, ON_ERROR_STOP fecha o psql e o Postgres desfaz esta mesma
-- transacao, limpando tambem o caminho de falha.
BEGIN;

SELECT 'rls-smoke-' || replace(gen_random_uuid()::text, '-', '') AS smoke_prefix
\gset

INSERT INTO usuario (id, nome, email, senha_hash, papel, ativo)
VALUES (
    gen_random_uuid(),
    :'smoke_prefix' || '-atendente-a',
    :'smoke_prefix' || '-atendente-a@invalid.test',
    'nao-utilizado-pelo-smoke',
    'ATENDENTE',
    TRUE
)
RETURNING id AS smoke_atendente_a_id
\gset

INSERT INTO usuario (id, nome, email, senha_hash, papel, ativo)
VALUES (
    gen_random_uuid(),
    :'smoke_prefix' || '-atendente-b',
    :'smoke_prefix' || '-atendente-b@invalid.test',
    'nao-utilizado-pelo-smoke',
    'ATENDENTE',
    TRUE
)
RETURNING id AS smoke_atendente_b_id
\gset

INSERT INTO usuario (id, nome, email, senha_hash, papel, ativo)
VALUES (
    gen_random_uuid(),
    :'smoke_prefix' || '-gestor',
    :'smoke_prefix' || '-gestor@invalid.test',
    'nao-utilizado-pelo-smoke',
    'GESTOR',
    TRUE
)
RETURNING id AS smoke_gestor_id
\gset

INSERT INTO lead (id, nome, atendente_responsavel_id, status_basico)
VALUES
    (gen_random_uuid(), :'smoke_prefix' || '-lead-a', :'smoke_atendente_a_id', 'EM_ATENDIMENTO'),
    (gen_random_uuid(), :'smoke_prefix' || '-lead-b', :'smoke_atendente_b_id', 'EM_ATENDIMENTO');

-- A role precisa ser a sem privilegios, e nao o dono/superusuario que abriu
-- o psql. Sem isto, um RLS aparentemente ativo ainda pode ser contornado.
SET LOCAL ROLE synapse_app;

SELECT set_config('app.smoke_prefix', :'smoke_prefix', TRUE);
SELECT set_config('app.papel', 'ATENDENTE', TRUE);
SELECT set_config('app.usuario_id', :'smoke_atendente_a_id', TRUE);

DO $verificar_atendente_a$
DECLARE
    quantidade INTEGER;
    nomes TEXT;
BEGIN
    IF current_user <> 'synapse_app' THEN
        RAISE EXCEPTION 'Smoke RLS invalido: current_user e %, esperado synapse_app.', current_user;
    END IF;

    SELECT count(*), coalesce(string_agg(nome, ', ' ORDER BY nome), '<nenhum>')
      INTO quantidade, nomes
      FROM lead
     WHERE nome LIKE current_setting('app.smoke_prefix', TRUE) || '%';

    IF quantidade <> 1 THEN
        RAISE EXCEPTION
            'FALHA RLS: atendente A (%) viu % leads do smoke; esperado 1. Visiveis: %',
            current_setting('app.usuario_id', TRUE), quantidade, nomes;
    END IF;
END
$verificar_atendente_a$;

SELECT set_config('app.usuario_id', :'smoke_atendente_b_id', TRUE);

DO $verificar_atendente_b$
DECLARE
    quantidade INTEGER;
    nomes TEXT;
BEGIN
    SELECT count(*), coalesce(string_agg(nome, ', ' ORDER BY nome), '<nenhum>')
      INTO quantidade, nomes
      FROM lead
     WHERE nome LIKE current_setting('app.smoke_prefix', TRUE) || '%';

    IF quantidade <> 1 THEN
        RAISE EXCEPTION
            'FALHA RLS: atendente B (%) viu % leads do smoke; esperado 1. Visiveis: %',
            current_setting('app.usuario_id', TRUE), quantidade, nomes;
    END IF;
END
$verificar_atendente_b$;

SELECT set_config('app.papel', 'GESTOR', TRUE);
SELECT set_config('app.usuario_id', :'smoke_gestor_id', TRUE);

DO $verificar_gestor$
DECLARE
    quantidade INTEGER;
    nomes TEXT;
BEGIN
    SELECT count(*), coalesce(string_agg(nome, ', ' ORDER BY nome), '<nenhum>')
      INTO quantidade, nomes
      FROM lead
     WHERE nome LIKE current_setting('app.smoke_prefix', TRUE) || '%';

    IF quantidade <> 2 THEN
        RAISE EXCEPTION
            'FALHA RLS: gestor (%) viu % leads do smoke; esperado 2. Visiveis: %',
            current_setting('app.usuario_id', TRUE), quantidade, nomes;
    END IF;
END
$verificar_gestor$;

ROLLBACK;
\echo 'Smoke RLS passou: cada atendente viu somente o proprio lead; gestor viu os dois.'
