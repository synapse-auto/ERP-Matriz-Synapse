-- =========================================================
-- E180 — simulacao da limpeza de prefixo de discagem. SOMENTE LEITURA.
--
-- Lista, sem alterar dados, cada telefone fora do formato canonico, o resultado calculado e a
-- decisao que a V73 tomaria: UPDATE, FUSAO ou REVISAO MANUAL. Rode e guarde a saida antes de
-- autorizar o deploy da migration. A transacao termina em ROLLBACK.
--
-- Uso:
--   psql "$SYNAPSE_DB_URL" -v ddi="${TELEFONE_DDI_PADRAO:-55}" \
--     -f docker/provisionamento/simular-limpeza-prefixo-discagem.sql
-- =========================================================
\set ON_ERROR_STOP on
\pset pager off
\pset border 2

\if :{?ddi}
\else
  \set ddi 55
\endif

BEGIN;
SELECT set_config('app.papel', 'SERVICO', TRUE);

DO $$
BEGIN
    IF NOT app_enxerga_todos_os_leads() THEN
        RAISE EXCEPTION 'contexto de servico nao aplicado: simulacao enxergaria zero leads';
    END IF;
END $$;

CREATE OR REPLACE FUNCTION app_telefone_com_ddi(entrada TEXT, ddi_padrao TEXT)
RETURNS TEXT LANGUAGE sql IMMUTABLE AS $$
    WITH limpo AS (
        SELECT regexp_replace(entrada, '[^0-9]', '', 'g') AS digitos
    ), sem_prefixo AS (
        SELECT CASE
                   WHEN length(digitos) >= 4
                        AND left(digitos, 4) IN ('0300', '0400', '0500', '0800', '0900')
                       THEN digitos
                   WHEN left(digitos, 1) = '0' AND length(digitos) - 1 IN (10, 11)
                       THEN substr(digitos, 2)
                   WHEN left(digitos, 1) = '0' AND length(digitos) - 3 IN (10, 11)
                       THEN substr(digitos, 4)
                   ELSE digitos
               END AS digitos
          FROM limpo
    )
    SELECT CASE
               WHEN digitos IS NULL THEN NULL
               WHEN length(digitos) >= 4
                    AND left(digitos, 4) IN ('0300', '0400', '0500', '0800', '0900')
                   THEN CASE WHEN length(digitos) < 10 THEN NULL ELSE digitos END
               WHEN length(digitos) < 10 THEN NULL
               WHEN length(digitos) IN (10, 11) THEN ddi_padrao || digitos
               ELSE digitos
           END
      FROM sem_prefixo;
$$;

CREATE OR REPLACE FUNCTION app_telefone_canonico(entrada TEXT, ddi_padrao TEXT)
RETURNS TEXT LANGUAGE sql IMMUTABLE AS $$
    SELECT CASE
               WHEN com_ddi IS NULL THEN NULL
               WHEN length(com_ddi) = 12
                    AND left(com_ddi, 2) = '55'
                    AND substr(com_ddi, 5, 1) BETWEEN '6' AND '9'
                   THEN substr(com_ddi, 1, 4) || '9' || substr(com_ddi, 5)
               ELSE com_ddi
           END
      FROM (SELECT app_telefone_com_ddi(entrada, ddi_padrao) AS com_ddi) AS base;
$$;

CREATE TEMP TABLE sim_prefixo AS
SELECT l.id,
       l.nome,
       l.telefone,
       l.telefone_provedor,
       app_telefone_canonico(l.telefone, :'ddi') AS canonico,
       (SELECT count(*) FROM atendimento a WHERE a.lead_id = l.id) AS atendimentos,
       (SELECT count(*)
          FROM mensagem m
          JOIN atendimento a ON a.id = m.atendimento_id
         WHERE a.lead_id = l.id) AS mensagens,
       EXISTS (SELECT 1 FROM atendimento a WHERE a.lead_id = l.id) AS tem_conversa
  FROM lead l
 WHERE l.telefone IS NOT NULL;

\echo ''
\echo '--- LEADS MALFORMADOS E DECISAO CALCULADA (nenhuma alteracao e gravada) ---'
SELECT s.id,
       s.nome,
       s.telefone,
       s.canonico,
       CASE
           WHEN s.canonico ~ '^55[1-9][0-9]{9,10}$'
                AND NOT EXISTS (
                    SELECT 1 FROM sim_prefixo outro
                     WHERE outro.id <> s.id AND outro.canonico = s.canonico)
             THEN 'UPDATE'
           WHEN s.canonico ~ '^55[1-9][0-9]{9,10}$'
                AND (s.telefone_provedor IS NULL OR btrim(s.telefone_provedor) = '')
                AND s.mensagens = 0
                AND EXISTS (SELECT 1 FROM sim_prefixo outro
                              WHERE outro.id <> s.id AND outro.canonico = s.canonico
                                AND outro.tem_conversa)
                AND (SELECT count(*) FROM sim_prefixo outro WHERE outro.canonico = s.canonico) = 2
             THEN 'FUSAO'
           ELSE 'REVISAO MANUAL'
       END AS decisao
   FROM sim_prefixo s
  WHERE s.telefone !~ '^55[1-9][0-9]{9,10}$'
  ORDER BY s.id;

\echo ''
\echo '--- FUSOES: sobrevivente, perdedor e FKs que serao movidas ---'
WITH pares AS (
    SELECT bad.id AS perdedor,
           bad.nome AS nome_perdedor,
           bad.telefone AS telefone_perdedor,
           bad.canonico,
           survivor.id AS sobrevivente,
           survivor.nome AS nome_sobrevivente
      FROM sim_prefixo bad
      JOIN sim_prefixo survivor
        ON survivor.id <> bad.id
       AND survivor.canonico = bad.canonico
       AND survivor.tem_conversa
     WHERE bad.telefone !~ '^55[1-9][0-9]{9,10}$'
       AND bad.canonico ~ '^55[1-9][0-9]{9,10}$'
       AND (bad.telefone_provedor IS NULL OR btrim(bad.telefone_provedor) = '')
       AND bad.mensagens = 0
       AND (SELECT count(*) FROM sim_prefixo c WHERE c.canonico = bad.canonico) = 2
)
SELECT p.canonico,
       p.sobrevivente,
       p.nome_sobrevivente,
       p.perdedor,
       p.nome_perdedor,
       p.telefone_perdedor,
       f.tabela,
       f.linhas
  FROM pares p
  CROSS JOIN LATERAL (VALUES
        ('atendimento', (SELECT count(*) FROM atendimento x WHERE x.lead_id = p.perdedor)),
        ('evento_timeline', (SELECT count(*) FROM evento_timeline x WHERE x.lead_id = p.perdedor)),
        ('lembrete', (SELECT count(*) FROM lembrete x WHERE x.lead_id = p.perdedor)),
        ('mensagem_programada', (SELECT count(*) FROM mensagem_programada x WHERE x.lead_id = p.perdedor)),
        ('lead_tag', (SELECT count(*) FROM lead_tag x WHERE x.lead_id = p.perdedor)),
        ('campanha_mensagem_metrica', (SELECT count(*) FROM campanha_mensagem_metrica x WHERE x.lead_id = p.perdedor)),
        ('mensagem_envio_idempotencia', (SELECT count(*) FROM mensagem_envio_idempotencia x WHERE x.lead_id = p.perdedor)),
        ('audit_log', (SELECT count(*) FROM audit_log x WHERE x.lead_id = p.perdedor))
    ) AS f(tabela, linhas)
 WHERE f.linhas > 0
 ORDER BY p.canonico, p.perdedor, f.tabela;

\echo ''
\echo '--- REVISAO MANUAL: ids que permanecem intactos ---'
SELECT id, nome, telefone, canonico
  FROM sim_prefixo s
 WHERE s.telefone !~ '^55[1-9][0-9]{9,10}$'
   AND NOT EXISTS (
       SELECT 1
         FROM sim_prefixo elegivel
        WHERE elegivel.id = s.id
          AND elegivel.canonico ~ '^55[1-9][0-9]{9,10}$'
          AND NOT EXISTS (SELECT 1 FROM sim_prefixo outro
                             WHERE outro.id <> s.id AND outro.canonico = s.canonico))
   AND NOT (
       s.canonico ~ '^55[1-9][0-9]{9,10}$'
       AND (s.telefone_provedor IS NULL OR btrim(s.telefone_provedor) = '')
       AND s.mensagens = 0
       AND EXISTS (SELECT 1 FROM sim_prefixo outro
                     WHERE outro.id <> s.id AND outro.canonico = s.canonico
                       AND outro.tem_conversa)
        AND (SELECT count(*) FROM sim_prefixo outro WHERE outro.canonico = s.canonico) = 2)
 ORDER BY id;

\echo ''
\echo '--- RESUMO ---'
SELECT count(*) FILTER (WHERE telefone !~ '^55[1-9][0-9]{9,10}$'
                         AND canonico ~ '^55[1-9][0-9]{9,10}$'
                         AND NOT EXISTS (SELECT 1 FROM sim_prefixo outro
                                           WHERE outro.id <> sim_prefixo.id
                                             AND outro.canonico = sim_prefixo.canonico)) AS updates,
       count(*) FILTER (WHERE telefone !~ '^55[1-9][0-9]{9,10}$'
                          AND canonico ~ '^55[1-9][0-9]{9,10}$'
                          AND (telefone_provedor IS NULL OR btrim(telefone_provedor) = '')
                          AND mensagens = 0
                          AND EXISTS (SELECT 1 FROM sim_prefixo outro
                                        WHERE outro.id <> sim_prefixo.id
                                          AND outro.canonico = sim_prefixo.canonico
                                          AND outro.tem_conversa)
                          AND (SELECT count(*) FROM sim_prefixo outro
                                 WHERE outro.canonico = sim_prefixo.canonico) = 2) AS fusoes,
       count(*) FILTER (WHERE telefone !~ '^55[1-9][0-9]{9,10}$'
                          AND (canonico !~ '^55[1-9][0-9]{9,10}$'
                               OR EXISTS (SELECT 1 FROM sim_prefixo outro
                                           WHERE outro.id <> sim_prefixo.id
                                             AND outro.canonico = sim_prefixo.canonico))) AS revisao_manual,
       count(*) FILTER (WHERE telefone !~ '^55[1-9][0-9]{9,10}$') AS total_malformados
  FROM sim_prefixo;

ROLLBACK;
