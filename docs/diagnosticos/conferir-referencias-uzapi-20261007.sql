-- Run only against fmnaprod. No changes, no full payload, no customer phone/content.
-- Replace the two placeholders with the incident entry IDs through a private channel.
BEGIN READ ONLY;
SET LOCAL statement_timeout = '5s';
SET LOCAL TIME ZONE 'UTC';

SELECT w.id_externo AS entrada_id,
       w.recebido_em,
       w.processado_em,
       w.tentativas,
       w.esgotado_em,
       m->>'id' AS mensagem_id,
       m->>'type' AS tipo,
       m->'document'->>'id' AS document_id,
       m->'document'->>'media_id' AS document_media_id,
       m->'document'->>'mediaId' AS document_media_id_alias,
       COALESCE(m->'document'->>'mime_type',
                m->'document'->>'mimeType',
                m->'document'->>'mimetype') AS mime_documento
  FROM webhook_entrada w
 CROSS JOIN LATERAL jsonb_array_elements(w.payload::jsonb->'entry') e
 CROSS JOIN LATERAL jsonb_array_elements(e->'changes') c
 CROSS JOIN LATERAL jsonb_array_elements(c->'value'->'messages') m
 WHERE w.id_externo IN ('SUBSTITUIR_ENTRADA_1', 'SUBSTITUIR_ENTRADA_2')
   AND m->>'id' = w.id_externo
 ORDER BY w.recebido_em;

ROLLBACK;
