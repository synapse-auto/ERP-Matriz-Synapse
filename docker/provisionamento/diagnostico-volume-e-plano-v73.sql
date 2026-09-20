-- =========================================================
-- E192 — diagnostico de volume e plano de execucao antes da V73. SOMENTE LEITURA.
--
-- Mede o tamanho real das tabelas que a V73 percorre e mostra o plano de execucao das duas
-- consultas centrais do runner em lotes (buscarPares, da fase FUSAO, e buscarNormalizacoes, da
-- fase NORMALIZACAO, em V73__NormalizarPrefixoDiscagemLeads.java).
--
-- COMO USAR A SAIDA: rode este mesmo arquivo nos DOIS bancos — primeiro na Estrutural, onde a V73
-- ja concluiu com sucesso, depois na Femina — e compare contagens e planos lado a lado. A
-- comparacao e o criterio objetivo para decidir se o volume da Femina e compativel com o que ja
-- rodou: ordens de grandeza parecidas e os mesmos tipos de no no plano liberam a tentativa;
-- divergencia grande (uma ordem de grandeza a mais de leads ou de candidatos, ou Seq Scan repetido
-- onde a Estrutural usa indice) e parada para analise antes de qualquer execucao.
--
-- CONFIDENCIALIDADE: a saida pode conter telefones e nomes. Guarde em local operacional restrito;
-- nao copie para logs publicos ou tickets sem protecao.
--
-- NENHUMA ESCRITA DE DADOS: nao ha UPDATE, DELETE nem INSERT. O EXPLAIN usa ANALYZE apenas sobre
-- consultas de leitura. As funcoes app_telefone_* sao recriadas dentro da transacao porque em um
-- banco ainda no schema 72 elas nao existem (quem as cria e a propria V73); a transacao termina em
-- ROLLBACK, entao nem os dados nem o catalogo ficam alterados.
--
-- Uso:
--   psql "$SYNAPSE_DB_URL" -v ddi="${TELEFONE_DDI_PADRAO:-55}" \
--     -v lote="${SYNAPSE_MIGRATION_BATCH_SIZE:-25}" \
--     -f docker/provisionamento/diagnostico-volume-e-plano-v73.sql
-- =========================================================
\set ON_ERROR_STOP on
\pset pager off
\pset border 2

\if :{?ddi}
\else
  \set ddi 55
\endif

\if :{?lote}
\else
  \set lote 25
\endif

BEGIN;
SELECT set_config('app.papel', 'SERVICO', TRUE);

DO $$
BEGIN
    IF NOT app_enxerga_todos_os_leads() THEN
        RAISE EXCEPTION 'contexto de servico nao aplicado: o diagnostico enxergaria zero leads';
    END IF;
END $$;

-- Copia fiel das funcoes que a V73 instala (aplicarFuncoes em
-- V73__NormalizarPrefixoDiscagemLeads.java). Recriadas aqui para que o diagnostico rode tambem no
-- banco que ainda esta em 72; o ROLLBACK no fim desfaz a recriacao.
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

\echo ''
\echo '--- 1. ESTADO DO HISTORICO FLYWAY (contexto da medicao) ---'
SELECT installed_rank, version, description, success, installed_on
  FROM flyway_schema_history
 ORDER BY installed_rank DESC
 LIMIT 5;

\echo ''
\echo '--- 2. VOLUME DAS TABELAS QUE A V73 PERCORRE OU ATUALIZA ---'
-- lead, atendimento e mensagem entram nas consultas de selecao; as demais recebem UPDATE ou DELETE
-- de lead_id durante a fusao (executarAtualizacoesDaFusao). O tamanho em disco acompanha a
-- contagem porque e ele que determina o custo de um Seq Scan.
SELECT tabela,
       linhas,
       pg_size_pretty(pg_total_relation_size(tabela::regclass)) AS tamanho_total
  FROM (
        SELECT 'lead' AS tabela, count(*) AS linhas FROM lead
        UNION ALL SELECT 'atendimento', count(*) FROM atendimento
        UNION ALL SELECT 'mensagem', count(*) FROM mensagem
        UNION ALL SELECT 'atendimento_participante', count(*) FROM atendimento_participante
        UNION ALL SELECT 'lead_tag', count(*) FROM lead_tag
        UNION ALL SELECT 'lembrete', count(*) FROM lembrete
        UNION ALL SELECT 'mensagem_programada', count(*) FROM mensagem_programada
        UNION ALL SELECT 'campanha_mensagem_metrica', count(*) FROM campanha_mensagem_metrica
        UNION ALL SELECT 'mensagem_envio_idempotencia', count(*) FROM mensagem_envio_idempotencia
        UNION ALL SELECT 'evento_timeline', count(*) FROM evento_timeline
        UNION ALL SELECT 'audit_log', count(*) FROM audit_log
       ) volumes
 ORDER BY linhas DESC;

\echo ''
\echo '--- 3. TRABALHO QUE A V73 TERIA PELA FRENTE ---'
-- Mesmos criterios das consultas do runner. Os numeros sao totais; o runner os consome em lotes de
-- :lote itens, entao a quantidade de lotes e aproximadamente total dividido por :lote.
SELECT
    (SELECT count(*) FROM lead
      WHERE telefone IS NOT NULL
        AND telefone !~ '^55[1-9][0-9]{9,10}$') AS telefones_fora_do_canonico,
    (SELECT count(*)
       FROM lead l
      WHERE l.telefone IS NOT NULL
        AND l.telefone !~ '^55[1-9][0-9]{9,10}$'
        AND app_telefone_canonico(l.telefone, :'ddi') ~ '^55[1-9][0-9]{9,10}$'
        AND NOT EXISTS (
            SELECT 1 FROM lead outro
             WHERE outro.id <> l.id
               AND app_telefone_canonico(outro.telefone, :'ddi')
                   = app_telefone_canonico(l.telefone, :'ddi'))) AS candidatos_normalizacao,
    (SELECT count(*)
       FROM (
             SELECT app_telefone_canonico(l.telefone, :'ddi') AS canonico
               FROM lead l
              WHERE l.telefone IS NOT NULL
                AND l.telefone !~ '^55[1-9][0-9]{9,10}$'
                AND app_telefone_canonico(l.telefone, :'ddi') ~ '^55[1-9][0-9]{9,10}$'
              GROUP BY 1
             HAVING count(*) > 2
            ) grupos) AS grupos_com_mais_de_dois_candidatos;

\echo ''
\echo '--- 3b. PARES CANDIDATOS A FUSAO (criterios de buscarPares, sem o LIMIT do lote) ---'
WITH candidatos AS (
    SELECT l.id,
           l.telefone,
           l.telefone_provedor,
           app_telefone_canonico(l.telefone, :'ddi') AS canonico,
           EXISTS (SELECT 1 FROM atendimento a WHERE a.lead_id = l.id) AS tem_conversa,
           (SELECT count(*)
              FROM mensagem m
              JOIN atendimento a ON a.id = m.atendimento_id
             WHERE a.lead_id = l.id) AS mensagens
      FROM lead l
     WHERE l.telefone IS NOT NULL
), pares AS (
    SELECT bad.id AS perdedor, survivor.id AS sobrevivente, bad.canonico
      FROM candidatos bad
      JOIN candidatos survivor
        ON survivor.id <> bad.id
       AND survivor.canonico = bad.canonico
     WHERE bad.telefone !~ '^55[1-9][0-9]{9,10}$'
       AND bad.canonico ~ '^55[1-9][0-9]{9,10}$'
       AND (bad.telefone_provedor IS NULL OR btrim(bad.telefone_provedor) = '')
       AND bad.mensagens = 0
       AND survivor.tem_conversa
)
SELECT count(*) AS pares_candidatos_fusao
  FROM pares p
 WHERE (SELECT count(*) FROM candidatos c WHERE c.canonico = p.canonico) = 2;

\echo ''
\echo '--- 4. PLANO: buscarNormalizacoes (um lote da fase NORMALIZACAO) ---'
-- Copia de buscarNormalizacoes, sem o NOT EXISTS sobre synapse_v73_runner_checkpoint: essa tabela
-- so existe durante a execucao do runner e comeca vazia, entao o plano medido aqui corresponde ao
-- do primeiro lote. O ORDER BY e o LIMIT sao mantidos porque mudam o plano.
EXPLAIN (ANALYZE, BUFFERS)
SELECT l.id
  FROM lead l
 WHERE l.telefone IS NOT NULL
   AND l.telefone !~ '^55[1-9][0-9]{9,10}$'
   AND app_telefone_canonico(l.telefone, :'ddi') ~ '^55[1-9][0-9]{9,10}$'
   AND NOT EXISTS (
       SELECT 1 FROM lead outro
        WHERE outro.id <> l.id
          AND app_telefone_canonico(outro.telefone, :'ddi')
              = app_telefone_canonico(l.telefone, :'ddi'))
 ORDER BY l.id
 LIMIT :lote;

\echo ''
\echo '--- 5. PLANO: buscarPares (um lote da fase FUSAO) ---'
-- Copia de buscarPares, com a mesma ressalva sobre o checkpoint do bloco anterior.
EXPLAIN (ANALYZE, BUFFERS)
WITH candidatos AS (
    SELECT l.id,
           l.telefone,
           l.telefone_provedor,
           app_telefone_canonico(l.telefone, :'ddi') AS canonico,
           EXISTS (SELECT 1 FROM atendimento a WHERE a.lead_id = l.id) AS tem_conversa,
           (SELECT count(*)
              FROM mensagem m
              JOIN atendimento a ON a.id = m.atendimento_id
             WHERE a.lead_id = l.id) AS mensagens
      FROM lead l
     WHERE l.telefone IS NOT NULL
), pares AS (
    SELECT bad.id AS perdedor, survivor.id AS sobrevivente, bad.canonico
      FROM candidatos bad
      JOIN candidatos survivor
        ON survivor.id <> bad.id
       AND survivor.canonico = bad.canonico
     WHERE bad.telefone !~ '^55[1-9][0-9]{9,10}$'
       AND bad.canonico ~ '^55[1-9][0-9]{9,10}$'
       AND (bad.telefone_provedor IS NULL OR btrim(bad.telefone_provedor) = '')
       AND bad.mensagens = 0
       AND survivor.tem_conversa
)
SELECT perdedor, sobrevivente, canonico
  FROM pares p
 WHERE (SELECT count(*) FROM candidatos c WHERE c.canonico = p.canonico) = 2
 ORDER BY canonico, perdedor
 LIMIT :lote;

ROLLBACK;
