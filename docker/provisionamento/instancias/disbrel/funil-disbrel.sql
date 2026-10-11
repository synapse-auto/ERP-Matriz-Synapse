-- =========================================================
-- Disbrel: funil de cinco etapas (D5b do docs/71).
--
-- Desenho: 1 Novo Contato, 2 Aguardando Medidas, 3 Negociação, 4 Fechado (resultado GANHO), 5 Pós-venda.
-- Nao ha etapa de Orcamento. "Sem Retorno" NAO e etapa: e o filtro semRetornoDias que ja existe.
--
-- Diferente de funil-padrao.sql, este script NAO usa ON CONFLICT (ordem) DO UPDATE e portanto nunca
-- renomeia uma etapa. Regras:
--   * a etapa do desenho que ja existe (mesmo nome, sem caixa/acento) e mantida; so a posicao/cor sao ajustadas;
--   * a que falta e criada;
--   * se uma etapa existente tem o resultado diferente do desenho (ex.: "Fechado" que nao e GANHO), recusa;
--   * etapas fora do desenho (inclusive PERDIDO) sao MANTIDAS, com seus leads, depois das cinco;
--   * etapa com leads so deixa de existir por remapeamento explicito (-v remapear), que move os leads e apaga
--     a etapa de origem na mesma transacao; etapa vazia fora do desenho so e apagada com -v remover_vazias=SIM;
--   * mover leads para uma etapa GANHO so e aceito vindo de outra GANHO (nao fabrica venda sem registro) e
--     todo remapeamento exige o mesmo resultado (EM_ANDAMENTO->EM_ANDAMENTO, GANHO->GANHO, PERDIDO->PERDIDO).
--
-- Seguranca:
--   * sem -v confirmar=APLICAR o script roda tudo e termina em ROLLBACK (simulacao, nada e gravado);
--   * antes de alterar, grava o estado anterior em funil_disbrel_bkp_etapa / _criada / _lead sob um
--     id de execucao impresso no fim (so quando algo muda), e funil-disbrel-reverter.sql usa esse id;
--   * idempotente: repetir depois de aplicado nao muda nada e nao grava backup;
--   * recusa se o papel nao enxerga todos os leads (FORCE ROW LEVEL SECURITY), em vez de contar/mover parcial.
--
-- Uso:
--   1. diagnostico:  psql "$SYNAPSE_DB_URL" -f .../funil-disbrel-diagnostico.sql        (guarde a saida)
--   2. simulacao:    psql "$SYNAPSE_DB_URL" -f .../funil-disbrel.sql
--   3. aplicacao:    psql "$SYNAPSE_DB_URL" -v confirmar=APLICAR -o funil-disbrel-$(date +%Y%m%d-%H%M%S).txt \
--                         -f .../funil-disbrel.sql
--   Remapeamento (exemplo; chaves e valores sao nomes de etapa, comparados sem caixa/acento):
--                    -v remapear='{"Orçamento enviado":"Negociação","Venda fechada":"Fechado"}'
--   Remover etapas vazias fora do desenho: -v remover_vazias=SIM
--
-- Desfazer: funil-disbrel-reverter.sql -v execucao=<id impresso na aplicacao> -v confirmar=REVERTER.
-- Requer psql 13 ou superior. Nao e migration Flyway e nenhum job a chama.
-- =========================================================
\set ON_ERROR_STOP on
\pset pager off
\pset border 2

\if :{?confirmar}
\else
  \set confirmar ''
\endif
\if :{?remapear}
\else
  \set remapear '{}'
\endif
\if :{?remover_vazias}
\else
  \set remover_vazias ''
\endif

SELECT :'confirmar' NOT IN ('', 'APLICAR') AS confirmar_invalido \gset
\if :confirmar_invalido
  \echo 'confirmar so aceita APLICAR (ou ausente, para simulacao). Nada foi feito.'
  \quit
\endif

BEGIN;

-- Falha em vez de esperar para sempre se alguem estiver editando etapas.
SET LOCAL lock_timeout = '10s';
LOCK TABLE etapa_atendimento IN SHARE ROW EXCLUSIVE MODE;

SELECT set_config('app.papel', 'SERVICO', TRUE);

DO $$
BEGIN
    IF NOT app_enxerga_todos_os_leads() THEN
        RAISE EXCEPTION 'contexto de servico nao aplicado: o script enxergaria leads parcialmente';
    END IF;
END $$;

SELECT gen_random_uuid() AS execucao \gset
SELECT set_config('funil.execucao', :'execucao', TRUE);
SELECT set_config('funil.remapear', :'remapear', TRUE);
SELECT set_config('funil.remover_vazias', :'remover_vazias', TRUE);

CREATE TABLE IF NOT EXISTS funil_disbrel_bkp_etapa (
    execucao   UUID        NOT NULL,
    gravado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    id         UUID        NOT NULL,
    nome       VARCHAR(80) NOT NULL,
    ordem      SMALLINT    NOT NULL,
    cor_visual VARCHAR(20),
    resultado  resultado_etapa NOT NULL,
    PRIMARY KEY (execucao, id)
);
CREATE TABLE IF NOT EXISTS funil_disbrel_bkp_criada (
    execucao UUID NOT NULL,
    id       UUID NOT NULL,
    PRIMARY KEY (execucao, id)
);
CREATE TABLE IF NOT EXISTS funil_disbrel_bkp_lead (
    execucao           UUID NOT NULL,
    lead_id            UUID NOT NULL,
    etapa_anterior_id  UUID NOT NULL,
    etapa_destino_id   UUID NOT NULL,
    PRIMARY KEY (execucao, lead_id)
);

CREATE FUNCTION pg_temp.chave(nome TEXT) RETURNS TEXT LANGUAGE sql IMMUTABLE AS $f$
    SELECT lower(btrim(regexp_replace(
        translate(nome, 'ÁÀÂÃÄáàâãäÉÈÊËéèêëÍÌÎÏíìîïÓÒÔÕÖóòôõöÚÙÛÜúùûüÇç',
                        'AAAAAaaaaaEEEEeeeeIIIIiiiiOOOOOoooooUUUUuuuuCc'),
        '\s+', ' ', 'g')))
$f$;

CREATE TEMP TABLE desenho (ordem SMALLINT PRIMARY KEY, nome TEXT NOT NULL, cor TEXT NOT NULL, resultado TEXT NOT NULL)
    ON COMMIT DROP;
INSERT INTO desenho VALUES
    (1, 'Novo Contato',       '#64748B', 'EM_ANDAMENTO'),
    (2, 'Aguardando Medidas', '#F59E0B', 'EM_ANDAMENTO'),
    (3, 'Negociação',         '#8B5CF6', 'EM_ANDAMENTO'),
    (4, 'Fechado',            '#22C55E', 'GANHO'),
    (5, 'Pós-venda',          '#3B86E6', 'EM_ANDAMENTO');

CREATE TEMP TABLE registro (seq SERIAL PRIMARY KEY, acao TEXT NOT NULL, detalhe TEXT NOT NULL) ON COMMIT DROP;

DO $funil$
DECLARE
    exec_id     UUID    := current_setting('funil.execucao')::UUID;
    remapas     JSONB   := COALESCE(NULLIF(current_setting('funil.remapear', TRUE), ''), '{}')::JSONB;
    vazias      BOOLEAN := upper(COALESCE(current_setting('funil.remover_vazias', TRUE), '')) = 'SIM';
    mudou       BOOLEAN := FALSE;
    r_alvo      RECORD;
    r_etapa     RECORD;
    origem      RECORD;
    destino     RECORD;
    chave_orig  TEXT;
    chave_dest  TEXT;
    n_movidos   BIGINT;
BEGIN
    IF jsonb_typeof(remapas) <> 'object' THEN
        RAISE EXCEPTION 'remapear deve ser um objeto JSON {"origem":"destino"}; nada foi alterado';
    END IF;
    IF EXISTS (SELECT 1 FROM etapa_atendimento WHERE ordem >= 1000) THEN
        RAISE EXCEPTION 'ha etapa com ordem >= 1000; o script reordena usando essa faixa. Nada foi alterado';
    END IF;
    IF EXISTS (SELECT 1 FROM etapa_atendimento GROUP BY pg_temp.chave(nome) HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'duas etapas com o mesmo nome (sem caixa/acento): resolva antes. Nada foi alterado';
    END IF;

    INSERT INTO funil_disbrel_bkp_etapa (execucao, id, nome, ordem, cor_visual, resultado)
    SELECT exec_id, id, nome, ordem, cor_visual, resultado FROM etapa_atendimento;

    -- 1. etapas do desenho: manter a que existe, criar a que falta.
    FOR r_alvo IN SELECT * FROM desenho ORDER BY ordem LOOP
        SELECT * INTO r_etapa FROM etapa_atendimento WHERE pg_temp.chave(nome) = pg_temp.chave(r_alvo.nome);
        IF FOUND THEN
            IF r_etapa.resultado::TEXT <> r_alvo.resultado THEN
                RAISE EXCEPTION 'a etapa "%" existe com resultado % e o desenho pede %; mudar o resultado de uma etapa com historico '
                                'muda o que conta como venda. Decida (remapear para outra etapa) e rode de novo. Nada foi alterado',
                                r_etapa.nome, r_etapa.resultado, r_alvo.resultado;
            END IF;
            INSERT INTO registro (acao, detalhe) VALUES ('mantida', r_etapa.nome);
        ELSE
            -- Posicao e resultado provisorios (20000+ e EM_ANDAMENTO): a ordem final e o GANHO entram depois,
            -- quando um eventual remapeamento ja liberou o GANHO unico.
            INSERT INTO etapa_atendimento (id, nome, ordem, cor_visual, resultado)
            VALUES (gen_random_uuid(), r_alvo.nome, 20000 + r_alvo.ordem, r_alvo.cor, 'EM_ANDAMENTO')
         RETURNING * INTO r_etapa;
            INSERT INTO funil_disbrel_bkp_criada (execucao, id) VALUES (exec_id, r_etapa.id);
            mudou := TRUE;
            INSERT INTO registro (acao, detalhe) VALUES ('criada', r_alvo.nome);
        END IF;
    END LOOP;

    -- 2. remapeamentos explicitos: move os leads e apaga a etapa de origem, atomicamente.
    FOR chave_orig, chave_dest IN SELECT key, value FROM jsonb_each_text(remapas) LOOP
        SELECT * INTO origem FROM etapa_atendimento WHERE pg_temp.chave(nome) = pg_temp.chave(chave_orig);
        IF NOT FOUND THEN
            RAISE EXCEPTION 'remapear: a etapa de origem "%" nao existe. Nada foi alterado', chave_orig;
        END IF;
        SELECT e2.id, e2.nome, d2.resultado AS resultado_final INTO destino
          FROM etapa_atendimento e2
          JOIN desenho d2 ON pg_temp.chave(d2.nome) = pg_temp.chave(e2.nome)
         WHERE pg_temp.chave(e2.nome) = pg_temp.chave(chave_dest);
        IF NOT FOUND THEN
            RAISE EXCEPTION 'remapear: o destino "%" nao e uma etapa do desenho. Nada foi alterado', chave_dest;
        END IF;
        IF origem.id = destino.id THEN
            RAISE EXCEPTION 'remapear: origem e destino sao a mesma etapa ("%")', origem.nome;
        END IF;
        IF origem.resultado::TEXT <> destino.resultado_final THEN
            RAISE EXCEPTION 'remapear: "%" (%) -> "%" (%): o resultado precisa ser o mesmo; mover leads entre resultados '
                            'diferentes criaria ou apagaria venda sem registro. Nada foi alterado',
                            origem.nome, origem.resultado, destino.nome, destino.resultado_final;
        END IF;

        WITH movidos AS (
            UPDATE lead SET etapa_atendimento_id = destino.id
             WHERE etapa_atendimento_id = origem.id
         RETURNING id
        ), guardados AS (
            INSERT INTO funil_disbrel_bkp_lead (execucao, lead_id, etapa_anterior_id, etapa_destino_id)
            SELECT exec_id, id, origem.id, destino.id FROM movidos
         RETURNING 1
        )
        SELECT count(*) INTO n_movidos FROM guardados;
        DELETE FROM etapa_atendimento WHERE id = origem.id;
        mudou := TRUE;
        INSERT INTO registro (acao, detalhe)
        VALUES ('remapeada', format('%s -> %s (%s leads movidos; etapa de origem apagada)', origem.nome, destino.nome, n_movidos));
    END LOOP;

    -- 3. resultado final das etapas criadas (GANHO unico: se outra etapa ainda e GANHO, recusa com a causa).
    IF EXISTS (SELECT 1 FROM etapa_atendimento x
                WHERE x.resultado = 'GANHO'
                  AND pg_temp.chave(x.nome) NOT IN (SELECT pg_temp.chave(nome) FROM desenho WHERE resultado = 'GANHO')) THEN
        RAISE EXCEPTION 'a etapa "%" ja e GANHO e so pode haver uma. Remapeie-a para "Fechado" (-v remapear) ou decida o que fazer. Nada foi alterado',
            (SELECT x.nome FROM etapa_atendimento x WHERE x.resultado = 'GANHO'
                AND pg_temp.chave(x.nome) NOT IN (SELECT pg_temp.chave(nome) FROM desenho WHERE resultado = 'GANHO') LIMIT 1);
    END IF;
    UPDATE etapa_atendimento x
       SET resultado = d.resultado::resultado_etapa
      FROM desenho d
     WHERE pg_temp.chave(x.nome) = pg_temp.chave(d.nome)
       AND x.resultado::TEXT <> d.resultado;

    -- 4. etapas vazias fora do desenho (opt-in). PERDIDO e GANHO nunca sao apagadas aqui.
    IF vazias THEN
        FOR r_etapa IN SELECT x.* FROM etapa_atendimento x
                  WHERE pg_temp.chave(x.nome) NOT IN (SELECT pg_temp.chave(nome) FROM desenho)
                    AND x.resultado = 'EM_ANDAMENTO'
                    AND NOT EXISTS (SELECT 1 FROM lead l WHERE l.etapa_atendimento_id = x.id) LOOP
            DELETE FROM etapa_atendimento WHERE id = r_etapa.id;
            mudou := TRUE;
            INSERT INTO registro (acao, detalhe) VALUES ('apagada (vazia)', r_etapa.nome);
        END LOOP;
    END IF;

    -- 5. posicoes: as cinco do desenho em 1..5; as demais, na mesma ordem relativa, a partir de 6.
    CREATE TEMP TABLE ordem_final ON COMMIT DROP AS
    SELECT x.id, x.nome, x.ordem AS atual,
           COALESCE(d.ordem, 5 + row_number() OVER (PARTITION BY (d.ordem IS NULL) ORDER BY x.ordem))::SMALLINT AS final
      FROM etapa_atendimento x
      LEFT JOIN desenho d ON pg_temp.chave(d.nome) = pg_temp.chave(x.nome);

    IF EXISTS (SELECT 1 FROM ordem_final WHERE atual <> final) THEN
        UPDATE etapa_atendimento x SET ordem = 10000 + o.final FROM ordem_final o WHERE o.id = x.id AND o.atual <> o.final;
        UPDATE etapa_atendimento x SET ordem = o.final FROM ordem_final o WHERE o.id = x.id AND x.ordem <> o.final;
        mudou := TRUE;
        INSERT INTO registro (acao, detalhe)
        SELECT 'reposicionada', format('%s: %s -> %s', nome, atual, final) FROM ordem_final WHERE atual <> final AND atual < 20000;
    END IF;

    UPDATE etapa_atendimento x SET cor_visual = d.cor
      FROM desenho d
     WHERE pg_temp.chave(x.nome) = pg_temp.chave(d.nome) AND x.cor_visual IS NULL;

    -- 6. verificacao: o desenho inteiro existe, nas posicoes e com os resultados esperados.
    IF (SELECT count(*) FROM desenho d
          JOIN etapa_atendimento x ON pg_temp.chave(x.nome) = pg_temp.chave(d.nome)
                                  AND x.ordem = d.ordem AND x.resultado::TEXT = d.resultado) <> (SELECT count(*) FROM desenho) THEN
        RAISE EXCEPTION 'verificacao final falhou: o funil nao ficou como o desenho. Transacao revertida';
    END IF;
    IF (SELECT count(*) FROM etapa_atendimento WHERE resultado = 'GANHO') <> 1 THEN
        RAISE EXCEPTION 'verificacao final falhou: deve haver exatamente uma etapa GANHO. Transacao revertida';
    END IF;

    IF NOT mudou THEN
        DELETE FROM funil_disbrel_bkp_etapa WHERE execucao = exec_id;
        DELETE FROM funil_disbrel_bkp_criada WHERE execucao = exec_id;
        INSERT INTO registro (acao, detalhe) VALUES ('sem mudanca', 'o funil ja esta como o desenho; nenhum backup gravado');
    END IF;
END
$funil$;

\echo ''
\echo '--- O QUE ESTE SCRIPT FEZ NESTA EXECUCAO -----------------------------------'
\echo ''
SELECT acao, detalhe FROM registro ORDER BY seq;

\echo ''
\echo '--- FUNIL RESULTANTE (com leads) -------------------------------------------'
\echo ''
SELECT e.ordem, e.nome, e.resultado, e.cor_visual, count(l.id) AS leads
  FROM etapa_atendimento e
  LEFT JOIN lead l ON l.etapa_atendimento_id = e.id
 GROUP BY e.id, e.ordem, e.nome, e.resultado, e.cor_visual
 ORDER BY e.ordem;

SELECT :'confirmar' = 'APLICAR' AS confirmado \gset
\if :confirmado
  COMMIT;
  \echo ''
  \echo 'APLICADO. Id da execucao (guarde para desfazer):'
  \echo :execucao
\else
  ROLLBACK;
  \echo ''
  \echo 'SIMULACAO: nada foi gravado. Para aplicar, repita com -v confirmar=APLICAR.'
\endif
