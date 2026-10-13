-- =========================================================
-- Disbrel: desfazer UMA execucao de funil-disbrel.sql (rollback documentado do D5b).
--
-- Usa o backup que a aplicacao gravou (funil_disbrel_bkp_etapa / funil_disbrel_bkp_lead) sob o id da
-- execucao impresso no fim de funil-disbrel.sql. Em uma transacao:
--   1. recria as etapas que a execucao apagou (mesmo id, nome, cor);
--   2. devolve os leads que o remapeamento moveu — SOMENTE os que ainda estao na etapa de destino; quem
--      um atendente ja moveu depois fica onde esta e aparece na lista de "nao devolvidos";
--   3. apaga as etapas que a execucao criou, se continuarem sem leads (com leads, recusa e nada muda);
--   4. restaura ordem, cor e resultado de todas as etapas ao estado anterior.
-- Etapas criadas por outras pessoas depois da execucao nao sao tocadas (nao constam no backup).
--
-- Sem -v confirmar=REVERTER termina em ROLLBACK (simulacao). Os backups nao sao apagados.
--
-- Uso:
--   psql "$SYNAPSE_DB_URL" -v execucao=<id> -f .../funil-disbrel-reverter.sql                  (simulacao)
--   psql "$SYNAPSE_DB_URL" -v execucao=<id> -v confirmar=REVERTER -f .../funil-disbrel-reverter.sql
-- Requer psql 13 ou superior.
-- =========================================================
\set ON_ERROR_STOP on
\pset pager off
\pset border 2

\if :{?execucao}
\else
  \echo 'informe -v execucao=<id impresso por funil-disbrel.sql>. Nada foi feito.'
  \quit
\endif
\if :{?confirmar}
\else
  \set confirmar ''
\endif
SELECT :'confirmar' NOT IN ('', 'REVERTER') AS confirmar_invalido \gset
\if :confirmar_invalido
  \echo 'confirmar so aceita REVERTER (ou ausente, para simulacao). Nada foi feito.'
  \quit
\endif

BEGIN;

SET LOCAL lock_timeout = '10s';
LOCK TABLE etapa_atendimento IN SHARE ROW EXCLUSIVE MODE;
SELECT set_config('app.papel', 'SERVICO', TRUE);

DO $$
BEGIN
    IF NOT app_enxerga_todos_os_leads() THEN
        RAISE EXCEPTION 'contexto de servico nao aplicado: o script moveria leads parcialmente';
    END IF;
END $$;

SELECT set_config('funil.execucao', :'execucao', TRUE);

CREATE TEMP TABLE registro (seq SERIAL PRIMARY KEY, acao TEXT NOT NULL, detalhe TEXT NOT NULL) ON COMMIT DROP;

DO $reverter$
DECLARE
    exec_id UUID := current_setting('funil.execucao')::UUID;
    r_etapa RECORD;
    n       BIGINT;
    i       INT := 0;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM funil_disbrel_bkp_etapa WHERE execucao = exec_id) THEN
        RAISE EXCEPTION 'execucao % nao tem backup de etapas (id errado, ou a execucao nao mudou nada). Nada foi alterado', exec_id;
    END IF;

    -- 1. recria o que foi apagado (resultado provisorio EM_ANDAMENTO; o real volta no passo 4).
    FOR r_etapa IN SELECT b.* FROM funil_disbrel_bkp_etapa b
              WHERE b.execucao = exec_id AND NOT EXISTS (SELECT 1 FROM etapa_atendimento x WHERE x.id = b.id) LOOP
        i := i + 1;
        INSERT INTO etapa_atendimento (id, nome, ordem, cor_visual, resultado)
        VALUES (r_etapa.id, r_etapa.nome, 30000 + i, r_etapa.cor_visual, 'EM_ANDAMENTO');
        INSERT INTO registro (acao, detalhe) VALUES ('etapa recriada', r_etapa.nome);
    END LOOP;

    -- 2. devolve os leads que ainda estao no destino.
    WITH devolvidos AS (
        UPDATE lead l SET etapa_atendimento_id = b.etapa_anterior_id
          FROM funil_disbrel_bkp_lead b
         WHERE b.execucao = exec_id AND l.id = b.lead_id AND l.etapa_atendimento_id = b.etapa_destino_id
     RETURNING l.id
    )
    SELECT count(*) INTO n FROM devolvidos;
    INSERT INTO registro (acao, detalhe) VALUES ('leads devolvidos', n::TEXT);

    SELECT count(*) INTO n FROM funil_disbrel_bkp_lead b
      JOIN lead l ON l.id = b.lead_id
     WHERE b.execucao = exec_id AND l.etapa_atendimento_id <> b.etapa_anterior_id;
    INSERT INTO registro (acao, detalhe)
    VALUES ('leads NAO devolvidos (ja movidos depois da execucao)', n::TEXT);

    -- 3. apaga as criadas pela execucao, se continuarem vazias; com leads, recusa (nada muda).
    FOR r_etapa IN SELECT x.* FROM etapa_atendimento x
              JOIN funil_disbrel_bkp_criada c ON c.id = x.id AND c.execucao = exec_id LOOP
        IF EXISTS (SELECT 1 FROM lead l WHERE l.etapa_atendimento_id = r_etapa.id) THEN
            RAISE EXCEPTION 'a etapa "%" criada pela execucao ja tem leads; mova-os antes de reverter. Nada foi alterado', r_etapa.nome;
        END IF;
        DELETE FROM etapa_atendimento WHERE id = r_etapa.id;
        INSERT INTO registro (acao, detalhe) VALUES ('etapa criada apagada', r_etapa.nome);
    END LOOP;

    -- 4. restaura ordem, cor e resultado das etapas do backup (duas fases por causa do indice unico de ordem).
    UPDATE etapa_atendimento x SET ordem = 10000 + b.ordem
      FROM funil_disbrel_bkp_etapa b
     WHERE b.execucao = exec_id AND b.id = x.id AND x.ordem <> b.ordem;
    -- GANHO e unico: quem deixa de ser GANHO sai primeiro, quem volta a ser entra depois.
    UPDATE etapa_atendimento x SET resultado = 'EM_ANDAMENTO'
      FROM funil_disbrel_bkp_etapa b
     WHERE b.execucao = exec_id AND b.id = x.id AND x.resultado = 'GANHO' AND b.resultado <> 'GANHO';
    UPDATE etapa_atendimento x SET ordem = b.ordem, cor_visual = b.cor_visual, resultado = b.resultado
      FROM funil_disbrel_bkp_etapa b
     WHERE b.execucao = exec_id AND b.id = x.id
       AND (x.ordem <> b.ordem OR x.cor_visual IS DISTINCT FROM b.cor_visual OR x.resultado <> b.resultado);
    INSERT INTO registro (acao, detalhe) VALUES ('etapas restauradas', 'ordem, cor e resultado do backup');
END
$reverter$;

\echo ''
\echo '--- O QUE O REVERTER FEZ ---------------------------------------------------'
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

SELECT :'confirmar' = 'REVERTER' AS confirmado \gset
\if :confirmado
  COMMIT;
  \echo ''
  \echo 'REVERTIDO.'
\else
  ROLLBACK;
  \echo ''
  \echo 'SIMULACAO: nada foi gravado. Para reverter, repita com -v confirmar=REVERTER.'
\endif
