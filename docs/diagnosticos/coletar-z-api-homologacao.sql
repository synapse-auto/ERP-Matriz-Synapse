-- SOMENTE LEITURA. Executar por psql: -v inicio='2026-10-25T12:00:00Z' -v fim='2026-10-25T12:10:00Z'
-- -v instancia='ID_DA_INSTANCIA' -f docs/diagnosticos/coletar-z-api-homologacao.sql
-- Compartilhar somente o resultado em canal privado. Nunca exportar payload/URL/env/log bruto.
BEGIN READ ONLY;
SET LOCAL statement_timeout = '5s';
SET LOCAL TIME ZONE 'UTC';
SELECT w.id_externo AS entrada_crm,
       w.recebido_em, w.processado_em, w.tentativas,
       CASE WHEN w.esgotado_em IS NOT NULL THEN 'ESGOTADO'
            WHEN w.processado_em IS NOT NULL THEN 'PROCESSADO' ELSE 'PENDENTE' END AS estado_crm,
       CASE WHEN w.payload::jsonb->>'type' IN ('ReceivedCallback','DeliveryCallback','MessageStatusCallback')
            THEN w.payload::jsonb->>'type' ELSE 'NAO_SUPORTADO' END AS tipo_evento,
       CASE WHEN w.payload::jsonb->>'status' IN ('PENDING','SENT','RECEIVED','READ','READ_BY_ME','PLAYED')
            THEN w.payload::jsonb->>'status' ELSE 'AUSENTE_OU_DESCONHECIDO' END AS status_evento,
       ARRAY(SELECT campo FROM unnest(ARRAY['instanceId','messageId','zaapId','ids','momment','fromMe','image','audio','video','document']) campo
             WHERE w.payload::jsonb ? campo) AS campos_relevantes_presentes,
       CASE WHEN w.payload::jsonb->>'messageId' ~ '^[A-Za-z0-9_.:@-]{1,128}$' THEN w.payload::jsonb->>'messageId' END AS message_id,
       CASE WHEN w.payload::jsonb->>'zaapId' ~ '^[A-Za-z0-9_.:@-]{1,128}$' THEN w.payload::jsonb->>'zaapId' END AS zaap_id,
       ARRAY(SELECT i #>> '{}' FROM jsonb_array_elements(
             CASE WHEN jsonb_typeof(w.payload::jsonb->'ids')='array' THEN w.payload::jsonb->'ids' ELSE '[]'::jsonb END) i
             WHERE jsonb_typeof(i)='string' AND i #>> '{}' ~ '^[A-Za-z0-9_.:@-]{1,128}$' LIMIT 100) AS ids_status
FROM webhook_entrada w
WHERE w.provedor='z-api' AND w.payload::jsonb->>'instanceId'=:'instancia'
  AND w.recebido_em >= :'inicio'::timestamptz AND w.recebido_em < :'fim'::timestamptz
ORDER BY w.recebido_em LIMIT 100;

-- Aliases nao sao equiparados por aparencia. NULL = ainda sem vinculo; conflitante nunca aplica.
SELECT provedor, instancia, namespace, id_externo, mensagem_id, atendimento_id,
       status_observado, conflitante
FROM mensagem_correlacao_canal
WHERE provedor='z-api' AND instancia=:'instancia'
  AND id_externo IN (
      SELECT w.payload::jsonb->>'messageId' FROM webhook_entrada w
      WHERE w.provedor='z-api' AND w.recebido_em >= :'inicio'::timestamptz AND w.recebido_em < :'fim'::timestamptz
        AND w.payload::jsonb->>'instanceId'=:'instancia'
      UNION ALL
      SELECT w.payload::jsonb->>'zaapId' FROM webhook_entrada w
      WHERE w.provedor='z-api' AND w.recebido_em >= :'inicio'::timestamptz AND w.recebido_em < :'fim'::timestamptz
        AND w.payload::jsonb->>'instanceId'=:'instancia'
      UNION ALL
      SELECT i #>> '{}' FROM webhook_entrada w
      CROSS JOIN LATERAL jsonb_array_elements(CASE WHEN jsonb_typeof(w.payload::jsonb->'ids')='array'
                         THEN w.payload::jsonb->'ids' ELSE '[]'::jsonb END) i
      WHERE w.provedor='z-api' AND w.recebido_em >= :'inicio'::timestamptz AND w.recebido_em < :'fim'::timestamptz
        AND w.payload::jsonb->>'instanceId'=:'instancia')
ORDER BY namespace, id_externo LIMIT 100;

-- Somente host HTTPS; nunca URL/caminho/query. Serve para revisar a allowlist com o operador,
-- nao para autorizar automaticamente qualquer host presente em um payload.
SELECT DISTINCT tipo, substring(url FROM '^https://([A-Za-z0-9.-]+)(?::443)?/') AS host_https
FROM webhook_entrada w
CROSS JOIN LATERAL (VALUES
    ('IMAGEM', w.payload::jsonb->'image'->>'imageUrl'),
    ('AUDIO', w.payload::jsonb->'audio'->>'audioUrl'),
    ('VIDEO', w.payload::jsonb->'video'->>'videoUrl'),
    ('DOCUMENTO', w.payload::jsonb->'document'->>'documentUrl')) midia(tipo, url)
WHERE w.provedor='z-api' AND w.payload::jsonb->>'instanceId'=:'instancia'
  AND w.recebido_em >= :'inicio'::timestamptz AND w.recebido_em < :'fim'::timestamptz
  AND url IS NOT NULL
LIMIT 100;
ROLLBACK;
