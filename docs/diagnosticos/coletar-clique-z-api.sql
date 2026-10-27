-- SOMENTE LEITURA. Janela curta de um clique controlado, em UTC.
-- psql -X -v ON_ERROR_STOP=1 -v inicio='2026-10-27T14:00:00Z' \
--   -v fim='2026-10-27T14:01:00Z' -v instancia='ID_DA_INSTANCIA' \
--   -f docs/diagnosticos/coletar-clique-z-api.sql
-- Compartilhe o resultado somente em canal privado. Este script nunca retorna payload,
-- texto escolhido, telefone, URL, token, segredo ou erro bruto.

\set ON_ERROR_STOP on
BEGIN READ ONLY;
SET LOCAL statement_timeout = '5s';
SET LOCAL TIME ZONE 'UTC';

WITH janela AS (
    SELECT :'inicio'::timestamptz AS inicio,
           :'fim'::timestamptz AS fim,
           :'instancia'::text AS instancia
), entradas AS (
    SELECT w.id_externo AS entrada_id,
           w.recebido_em,
           w.processado_em,
           w.tentativas,
           w.itens_descartados,
           w.descartes,
           w.ultimo_erro,
           w.esgotado_em,
           p.evento,
           p.evento->>'messageId' AS message_id,
           p.evento->>'instanceId' AS instancia,
           CASE WHEN p.evento->>'type' IN ('ReceivedCallback','DeliveryCallback','MessageStatusCallback')
                THEN p.evento->>'type' ELSE 'OUTRO_EVENTO' END AS tipo_evento,
           CASE
             WHEN p.evento ? 'buttonsResponseMessage' THEN 'BOTAO'
             WHEN p.evento ? 'listResponseMessage' THEN 'LISTA'
             WHEN p.evento ? 'pollVote' THEN 'ENQUETE_SEPARADA'
             WHEN p.evento ? 'text' THEN 'TEXTO'
             ELSE 'OUTRO'
           END AS tipo_recebido
      FROM webhook_entrada w
      CROSS JOIN janela j
      CROSS JOIN LATERAL (SELECT w.payload::jsonb AS evento) p
     WHERE w.provedor = 'z-api'
       AND w.recebido_em >= j.inicio AND w.recebido_em < j.fim
       AND p.evento->>'instanceId' = j.instancia
), repasses AS (
    SELECT o.id,
           o.criado_em,
           o.publicado_em,
           o.tentativas,
           o.ultimo_erro,
           o.esgotado_em,
           p.envelope
      FROM outbox_evento o
      CROSS JOIN janela j
      CROSS JOIN LATERAL (SELECT (o.payload->>'payloadCru')::jsonb AS envelope) p
     WHERE o.tipo = 'automacao.webhook.repassar'
       AND o.criado_em >= j.inicio AND o.criado_em < j.fim
       AND o.payload->>'provedorOrigem' = 'z-api'
)
SELECT e.entrada_id,
       e.recebido_em,
       e.processado_em,
       CASE WHEN e.esgotado_em IS NOT NULL THEN 'ESGOTADO'
            WHEN e.processado_em IS NULL THEN 'PENDENTE_OU_EM_PROCESSAMENTO'
            WHEN COALESCE(e.itens_descartados, 0) > 0 THEN 'PROCESSADO_COM_DESCARTE'
            ELSE 'PROCESSADO' END AS processamento,
       e.tentativas AS tentativas_entrada,
       COALESCE(e.itens_descartados, 0) AS itens_descartados,
       COALESCE((
           SELECT jsonb_agg(jsonb_build_object(
               'tipo', CASE WHEN d->>'tipo' IN ('buttonsResponseMessage','listResponseMessage','pollVote','text','image','audio','video','document','outro','status','delivery')
                            THEN d->>'tipo' ELSE 'OUTRO' END,
               'motivo', CASE WHEN d->>'motivo' IN ('TIPO_NAO_SUPORTADO','CONTEUDO_INVALIDO','SEM_IDENTIFICADOR','ITEM_MALFORMADO','GRUPO_NAO_SUPORTADO','ALVO_DESCONHECIDO','IDENTIDADE_NAO_SUPORTADA','OUTRO_CANAL','NAMESPACE_NAO_CONFIRMADO')
                              THEN d->>'motivo' ELSE 'OUTRO' END))
             FROM jsonb_array_elements(CASE WHEN jsonb_typeof(e.descartes) = 'array' THEN e.descartes ELSE '[]'::jsonb END) d
       ), '[]'::jsonb) AS descartes_sanitizados,
       (e.ultimo_erro IS NOT NULL) AS erro_entrada_registrado,
       e.tipo_evento,
       e.tipo_recebido,
       CASE WHEN e.message_id ~ '^[A-Za-z0-9_.:@-]{1,128}$' THEN e.message_id ELSE NULL END AS message_id,
       ARRAY(
           SELECT DISTINCT CASE WHEN k IN (
               'type','instanceId','messageId','referenceMessageId','momment','fromMe','isGroup',
               'phone','senderName','buttonsResponseMessage','listResponseMessage','pollVote',
               'text','image','audio','video','document','chat','status','ids'
           ) THEN k ELSE 'CAMPO_NAO_PADRONIZADO' END
             FROM jsonb_object_keys(e.evento) AS campos(k)
            ORDER BY 1
       ) AS nomes_campos_recebidos,
       CASE
         WHEN jsonb_typeof(e.evento->'buttonsResponseMessage') = 'object'
           THEN ARRAY(SELECT DISTINCT CASE WHEN k IN ('buttonId','message') THEN k ELSE 'CAMPO_NAO_PADRONIZADO' END
                       FROM jsonb_object_keys(e.evento->'buttonsResponseMessage') AS campos(k) ORDER BY 1)
         WHEN jsonb_typeof(e.evento->'listResponseMessage') = 'object'
           THEN ARRAY(SELECT DISTINCT CASE WHEN k IN ('selectedRowId','message','title') THEN k ELSE 'CAMPO_NAO_PADRONIZADO' END
                       FROM jsonb_object_keys(e.evento->'listResponseMessage') AS campos(k) ORDER BY 1)
         WHEN jsonb_typeof(e.evento->'pollVote') = 'object'
           THEN ARRAY(SELECT DISTINCT CASE WHEN k IN ('pollMessageId','options') THEN k ELSE 'CAMPO_NAO_PADRONIZADO' END
                       FROM jsonb_object_keys(e.evento->'pollVote') AS campos(k) ORDER BY 1)
         ELSE ARRAY[]::text[]
       END AS nomes_campos_interacao,
       c.mensagem_id,
       m.atendimento_id,
       m.tipo::text AS tipo_mensagem,
       m.remetente_tipo::text AS remetente_tipo,
       m.midia_metadados #>> '{respostaInterativa,tipo}' AS tipo_interacao_persistida,
       (SELECT COALESCE(jsonb_agg(jsonb_build_object(
                   'evento_id', r.id,
                   'criado_em', r.criado_em,
                   'tentativas', r.tentativas,
                   'publicado_em', r.publicado_em,
                   'resultado', CASE WHEN r.esgotado_em IS NOT NULL THEN 'ESGOTADO'
                                     WHEN r.publicado_em IS NOT NULL THEN 'HTTP_2XX_ACEITO'
                                     ELSE 'PENDENTE_OU_RETENTANDO' END,
                   'erro_registrado', r.ultimo_erro IS NOT NULL,
                   'esgotado_em', r.esgotado_em)), '[]'::jsonb)
          FROM repasses r
         WHERE r.envelope->>'instanceId' = e.instancia
           AND r.envelope->>'messageId' = e.message_id
           AND (r.envelope ? 'buttonsResponseMessage' OR r.envelope ? 'listResponseMessage')
       ) AS repasses_correlacionados
  FROM entradas e
  LEFT JOIN mensagem_correlacao_canal c
    ON c.provedor = 'z-api' AND c.instancia = e.instancia
   AND c.namespace = 'WHATSAPP' AND c.id_externo = e.message_id
  LEFT JOIN mensagem m
    ON m.id = c.mensagem_id AND m.enviado_em = c.mensagem_enviada_em
 ORDER BY e.recebido_em, e.message_id
 LIMIT 100;

ROLLBACK;
