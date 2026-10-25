-- psql -X -v banco_esperado=... -v canal_nome=... -v instancia_id=... -v telefone=...
-- Simula por padrao. -v confirmar=CADASTRAR confirma somente o cadastro INATIVO.
-- Nao recebe token/UUID, nao ativa canal e nao altera credenciais anteriores.
\set ON_ERROR_STOP on
\set VERBOSITY terse
\if :{?banco_esperado}
\else
  \set banco_esperado ''
\endif
\if :{?canal_nome}
\else
  \set canal_nome ''
\endif
\if :{?instancia_id}
\else
  \set instancia_id ''
\endif
\if :{?telefone}
\else
  \set telefone ''
\endif
\if :{?confirmar}
\else
  \set confirmar SIMULAR
\endif
BEGIN;
SET LOCAL lock_timeout = '3s';
SET LOCAL statement_timeout = '5s';
CREATE TEMP TABLE entrada_z_api ON COMMIT DROP AS
SELECT :'banco_esperado'::text AS banco, :'canal_nome'::text AS nome,
       :'instancia_id'::text AS instancia, :'telefone'::text AS telefone,
       :'confirmar'::text AS confirmacao;
CREATE TEMP TABLE resultado_z_api (canal_id uuid, credencial_id uuid, criado boolean) ON COMMIT DROP;
DO $$
DECLARE
    entrada record;
    canal_atual canal%ROWTYPE;
    credencial_atual canal_credencial%ROWTYPE;
    novo_canal uuid;
    nova_credencial uuid;
BEGIN
    SELECT * INTO entrada FROM entrada_z_api;
    IF entrada.banco = '' OR entrada.banco <> current_database() THEN
        RAISE EXCEPTION 'Banco diferente do banco_esperado; cadastro recusado';
    END IF;
    IF entrada.nome <> btrim(entrada.nome) OR length(entrada.nome) NOT BETWEEN 1 AND 50
       OR entrada.nome ~ '[[:cntrl:]]' THEN
        RAISE EXCEPTION 'canal_nome deve ter de 1 a 50 caracteres sem controles/espacos nas extremidades';
    END IF;
    IF entrada.instancia !~ '^[A-Za-z0-9_-]{1,120}$' THEN
        RAISE EXCEPTION 'instancia_id invalido';
    END IF;
    IF entrada.telefone !~ '^[1-9][0-9]{7,14}$' THEN
        RAISE EXCEPTION 'telefone deve estar no formato internacional numerico (8 a 15 digitos)';
    END IF;
    IF entrada.confirmacao NOT IN ('SIMULAR', 'CADASTRAR') THEN
        RAISE EXCEPTION 'confirmar deve ser SIMULAR ou CADASTRAR';
    END IF;
    -- Serializa os operadores deste procedimento. A unique de canal.nome continua protegendo
    -- contra outros escritores; um conflito aborta, nunca adota uma identidade anterior.
    PERFORM pg_advisory_xact_lock(hashtextextended('synapse:cadastro-canal-z-api', 0));
    SELECT * INTO canal_atual FROM canal WHERE nome = entrada.nome FOR UPDATE;
    IF FOUND THEN
        IF canal_atual.tipo <> 'WHATSAPP'
           OR (SELECT count(*) FROM canal_credencial WHERE canal_id = canal_atual.id) <> 1 THEN
            RAISE EXCEPTION 'Nome de canal ocupado; nao reutilizar canal de outro provedor';
        END IF;
        SELECT * INTO credencial_atual FROM canal_credencial WHERE canal_id = canal_atual.id FOR UPDATE;
        IF credencial_atual.token_ref IS DISTINCT FROM 'env://Z_API_TOKEN'
           OR credencial_atual.identificador_externo IS DISTINCT FROM entrada.instancia
           OR credencial_atual.numero IS DISTINCT FROM entrada.telefone
           OR credencial_atual.vigente_ate IS NOT NULL THEN
            RAISE EXCEPTION 'Canal/credencial divergente ou historica; nenhuma alteracao aplicada';
        END IF;
        IF EXISTS (SELECT 1 FROM canal_credencial WHERE identificador_externo = entrada.instancia
                   AND id <> credencial_atual.id) THEN
            RAISE EXCEPTION 'Identificador externo ja usado por outra credencial';
        END IF;
        INSERT INTO resultado_z_api VALUES (canal_atual.id, credencial_atual.id, false);
        RETURN;
    END IF;
    IF EXISTS (SELECT 1 FROM canal_credencial WHERE identificador_externo = entrada.instancia) THEN
        RAISE EXCEPTION 'Identificador externo ja usado; nao reutilizar identidade da Uzapi/Meta';
    END IF;
    INSERT INTO canal(nome, tipo, ativo) VALUES (entrada.nome, 'WHATSAPP', false)
    RETURNING id INTO novo_canal;
    INSERT INTO canal_credencial(canal_id, numero, identificador_externo, token_ref, ativo)
    VALUES (novo_canal, entrada.telefone, entrada.instancia, 'env://Z_API_TOKEN', false)
    RETURNING id INTO nova_credencial;
    INSERT INTO audit_log(ator_tipo, acao, entidade_tipo, entidade_id, dados_depois)
    VALUES ('SISTEMA', 'CADASTRAR_CANAL_Z_API', 'CANAL_CREDENCIAL', nova_credencial,
            jsonb_build_object('canalId', novo_canal, 'ativo', false, 'executorSql', current_user));
    INSERT INTO resultado_z_api VALUES (novo_canal, nova_credencial, true);
END $$;
SELECT canal_id, credencial_id, criado, :'confirmar' = 'CADASTRAR' AS confirmado
FROM resultado_z_api;
SELECT :'confirmar' = 'CADASTRAR' AS aplicar_z_api \gset
\if :aplicar_z_api
  COMMIT;
\else
  ROLLBACK;
  \echo 'SIMULACAO: UUIDs acima nao foram persistidos. Use somente o UUID da execucao CADASTRAR.'
\endif
