-- =========================================================
-- E141 — preenchimento seguro do endereco usado pelo provedor.
--
-- O script usa somente o "from" cru guardado em webhook_entrada. Ele nao tenta adivinhar o
-- endereco removendo o nono digito: app_telefone_canonico apenas casa o identificador observado
-- com o telefone canonico que ja existe no CRM.
--
-- Uso:
--   psql "$SYNAPSE_DB_URL" -v ddi=55 -f docker/provisionamento/preencher-endereco-de-envio-do-provedor.sql
--
-- A consulta de conferencia (secao 1) roda antes do UPDATE (secao 2). Leads com mais de um
-- wa_id distinto ficam explicitamente de fora para decisao manual.
-- =========================================================
\set ON_ERROR_STOP on
\pset pager off
\pset border 2

\if :{?ddi}
\else
  \set ddi 55
\endif

BEGIN;

-- lead tem FORCE ROW LEVEL SECURITY. O backfill precisa do mesmo contexto de servico usado pelo
-- consumidor do webhook; o terceiro argumento TRUE limita a configuracao a esta transacao.
SELECT set_config('app.papel', 'SERVICO', TRUE);

DO $$
BEGIN
    IF NOT app_enxerga_todos_os_leads() THEN
        RAISE EXCEPTION 'contexto de servico nao aplicado: backfill enxergaria zero leads';
    END IF;
END $$;

CREATE TEMP TABLE e141_remetentes ON COMMIT DROP AS
SELECT DISTINCT NULLIF(mensagem->>'from', '') AS telefone_provedor
  FROM webhook_entrada w
 CROSS JOIN LATERAL jsonb_path_query(
     w.payload,
     '$.entry[*].changes[*].value.messages[*]') AS mensagens(mensagem)
 WHERE NULLIF(mensagem->>'from', '') IS NOT NULL;

CREATE TEMP TABLE e141_casamentos ON COMMIT DROP AS
SELECT DISTINCT r.telefone_provedor, l.id AS lead_id
  FROM e141_remetentes r
  JOIN lead l
    ON l.telefone = app_telefone_canonico(r.telefone_provedor, :'ddi')
 WHERE app_telefone_canonico(r.telefone_provedor, :'ddi') IS NOT NULL;

\echo ''
\echo '--- 1. CONFERENCIA (antes de qualquer escrita) --------------------------'
\echo ''

SELECT count(*) FILTER (WHERE quantos_enderecos = 1) AS leads_a_preencher,
       count(*) FILTER (WHERE quantos_enderecos > 1) AS leads_bloqueados_por_multiplos_wa_id,
       count(*) AS leads_com_from_casado
  FROM (
        SELECT lead_id, count(*) AS quantos_enderecos
          FROM e141_casamentos
         GROUP BY lead_id
       ) casamentos;

\echo 'Leads bloqueados (mais de um wa_id distinto; nenhum sera atualizado):'
SELECT lead_id,
       string_agg(telefone_provedor, ', ' ORDER BY telefone_provedor) AS wa_ids,
       count(*) AS quantos_wa_id
  FROM e141_casamentos
 GROUP BY lead_id
HAVING count(*) > 1
 ORDER BY lead_id;

\echo ''
\echo '--- 2. PREENCHIMENTO: somente um wa_id por lead -------------------------'
\echo ''

WITH candidatos AS (
    SELECT lead_id, min(telefone_provedor) AS telefone_provedor
      FROM e141_casamentos
     GROUP BY lead_id
    HAVING count(*) = 1
), atualizados AS (
    UPDATE lead l
       SET telefone_provedor = c.telefone_provedor
      FROM candidatos c
     WHERE l.id = c.lead_id
     RETURNING l.id
)
SELECT count(*) AS leads_atualizados FROM atualizados;

COMMIT;
