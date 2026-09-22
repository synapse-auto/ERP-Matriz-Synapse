-- =========================================================
-- Liberar o responsavel de leads FINALIZADO gravados antes da correcao.
--
-- Contexto: ate esta correcao, finalizar um atendimento marcava o lead como FINALIZADO mas
-- mantinha lead.atendente_responsavel_id. Quando o cliente voltava, o primeiro atendente a
-- assumir esbarrava no dono antigo e o cliente voltava para quem o finalizou, sem rodizio.
-- O codigo novo ja finaliza limpando o responsavel; este script corrige apenas os registros
-- antigos.
--
-- O que o script faz:
--   1. conta os leads FINALIZADO com responsavel preenchido;
--   2. separa os que tem atendimento aberto (EM_IA ou EM_ATENDIMENTO) — esses NAO sao tocados;
--   3. lista lead e responsavel anterior de cada registro elegivel (guarde essa saida: e o
--      unico registro para desfazer);
--   4. so limpa o responsavel quando executado com -v confirmar=LIBERAR. Sem isso, termina em
--      ROLLBACK e nada e gravado.
--
-- O que o script NAO faz:
--   - nao altera atendimento.atendente_id (historico, avaliacao e comissao do ciclo encerrado);
--   - nao altera status_basico, etapa, notas, tags nem contadores;
--   - nao roda sozinho: nao ha migration nem job chamando este arquivo.
--
-- Uso (conferencia, sem escrita):
--   psql "$SYNAPSE_DB_URL" -f docker/provisionamento/liberar-responsavel-de-leads-finalizados.sql
--
-- Uso (execucao, depois de conferir a saida e ter autorizacao explicita):
--   psql "$SYNAPSE_DB_URL" -v confirmar=LIBERAR \
--        -o liberar-responsavel-$(date +%Y%m%d-%H%M%S).txt \
--        -f docker/provisionamento/liberar-responsavel-de-leads-finalizados.sql
--
-- Para desfazer um lead especifico, use a lista da secao 3:
--   UPDATE lead SET atendente_responsavel_id = '<responsavel_anterior>'
--    WHERE id = '<lead_id>' AND status_basico = 'FINALIZADO' AND atendente_responsavel_id IS NULL;
-- =========================================================
\set ON_ERROR_STOP on
\pset pager off
\pset border 2

\if :{?confirmar}
\else
  \set confirmar ''
\endif

BEGIN;

-- lead e atendimento tem FORCE ROW LEVEL SECURITY. Sem contexto, a RLS esconde tudo e o
-- script "nao encontraria nada" em silencio. TRUE limita a configuracao a esta transacao.
SELECT set_config('app.papel', 'SERVICO', TRUE);

DO $$
BEGIN
    IF NOT app_enxerga_todos_os_leads() THEN
        RAISE EXCEPTION 'contexto de servico nao aplicado: o script enxergaria zero leads';
    END IF;
END $$;

-- FOR UPDATE: a lista conferida e a lista gravada sao a mesma. Um atendente que reabra um
-- desses leads durante a execucao espera este script terminar.
CREATE TEMP TABLE liberacao_candidatos ON COMMIT DROP AS
SELECT l.id AS lead_id,
       l.atendente_responsavel_id AS responsavel_anterior,
       EXISTS (
           SELECT 1
             FROM atendimento a
            WHERE a.lead_id = l.id
              AND a.status <> 'FINALIZADO'
       ) AS tem_atendimento_aberto
  FROM lead l
 WHERE l.status_basico = 'FINALIZADO'
   AND l.atendente_responsavel_id IS NOT NULL
   FOR UPDATE OF l;

\echo ''
\echo '--- 1. CONTAGEM ------------------------------------------------------------'
\echo ''

SELECT count(*) AS finalizados_com_responsavel,
       count(*) FILTER (WHERE tem_atendimento_aberto) AS excluidos_por_atendimento_aberto,
       count(*) FILTER (WHERE NOT tem_atendimento_aberto) AS elegiveis
  FROM liberacao_candidatos;

\echo ''
\echo '--- 2. ELEGIVEIS POR RESPONSAVEL ANTERIOR ----------------------------------'
\echo ''

SELECT u.nome AS responsavel_anterior,
       c.responsavel_anterior AS responsavel_anterior_id,
       count(*) AS leads
  FROM liberacao_candidatos c
  LEFT JOIN usuario u ON u.id = c.responsavel_anterior
 WHERE NOT c.tem_atendimento_aberto
 GROUP BY u.nome, c.responsavel_anterior
 ORDER BY leads DESC, u.nome;

\echo ''
\echo '--- 3. LISTA PARA DESFAZER (guarde esta saida) -----------------------------'
\echo ''

SELECT c.lead_id, c.responsavel_anterior
  FROM liberacao_candidatos c
 WHERE NOT c.tem_atendimento_aberto
 ORDER BY c.responsavel_anterior, c.lead_id;

\echo ''
\echo '--- 4. EXCLUIDOS (FINALIZADO com atendimento aberto: inconsistencia a investigar) --'
\echo ''

SELECT c.lead_id, c.responsavel_anterior
  FROM liberacao_candidatos c
 WHERE c.tem_atendimento_aberto
 ORDER BY c.lead_id;

SELECT :'confirmar' = 'LIBERAR' AS confirmado \gset

\if :confirmado
  \echo ''
  \echo '--- 5. EXECUCAO ------------------------------------------------------------'
  \echo ''

  -- As condicoes se repetem no UPDATE em vez de confiar so na lista: se algo mudou desde a
  -- selecao, a linha fica de fora. atendimento nao e tocado.
  WITH liberados AS (
      UPDATE lead l
         SET atendente_responsavel_id = NULL
        FROM liberacao_candidatos c
       WHERE l.id = c.lead_id
         AND NOT c.tem_atendimento_aberto
         AND l.status_basico = 'FINALIZADO'
         AND l.atendente_responsavel_id = c.responsavel_anterior
         AND NOT EXISTS (
             SELECT 1 FROM atendimento a
              WHERE a.lead_id = l.id AND a.status <> 'FINALIZADO')
      RETURNING l.id
  )
  SELECT count(*) AS leads_liberados FROM liberados;

  -- Verificacao na mesma transacao: nenhum elegivel pode continuar com responsavel.
  DO $$
  DECLARE
      restantes BIGINT;
  BEGIN
      SELECT count(*) INTO restantes
        FROM lead l
        JOIN liberacao_candidatos c ON c.lead_id = l.id
       WHERE NOT c.tem_atendimento_aberto
         AND l.atendente_responsavel_id IS NOT NULL;
      IF restantes > 0 THEN
          RAISE EXCEPTION '% lead(s) elegiveis ainda com responsavel; nada foi gravado', restantes;
      END IF;
  END $$;

  COMMIT;
  \echo 'COMMIT feito. atendimento.atendente_id nao foi alterado.'
\else
  ROLLBACK;
  \echo ''
  \echo 'Somente conferencia: ROLLBACK, nada foi gravado.'
  \echo 'Para executar, rode novamente com -v confirmar=LIBERAR (exige autorizacao explicita).'
\endif
