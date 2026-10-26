-- Somente leitura. Compartilhar resultado por canal privado: contem IDs tecnicos.
-- psql -X -v ON_ERROR_STOP=1 -v inicio='2026-10-26T13:30:00Z' \
--   -v fim='2026-10-26T13:45:00Z' -v atendimento='' -f diagnosticar-citacao-z-api.sql
-- Preencher atendimento com UUID conhecido, ou vazio para a janela curta informada.
BEGIN READ ONLY;
SET LOCAL statement_timeout = '5s';
SET LOCAL TIME ZONE 'UTC';
SELECT m.id AS mensagem_id, m.enviado_em, m.atendimento_id, m.remetente_tipo,
       m.status_entrega, c.provedor, c.instancia, c.namespace,
       c.id_externo, c.conflitante,
       x.wamid LIKE 'canal:%' AS usa_chave_interna,
       r.origem_mensagem_id AS citada_id, r.origem_enviada_em AS citada_em,
       CASE WHEN m.erro_entrega->>'titulo' = 'Z_API_CITACAO_SEM_CORRELACAO'
            THEN 'Z_API_CITACAO_SEM_CORRELACAO' END AS falha_de_citacao
FROM mensagem m
LEFT JOIN mensagem_id_externo x ON x.mensagem_id=m.id AND x.mensagem_enviada_em=m.enviado_em
LEFT JOIN mensagem_correlacao_canal c ON c.mensagem_id=m.id AND c.mensagem_enviada_em=m.enviado_em
LEFT JOIN mensagem_referencia r ON r.mensagem_id=m.id AND r.mensagem_enviada_em=m.enviado_em
WHERE m.enviado_em >= :'inicio'::timestamptz AND m.enviado_em < :'fim'::timestamptz
  AND (NULLIF(:'atendimento','') IS NULL OR m.atendimento_id=NULLIF(:'atendimento','')::uuid)
ORDER BY m.enviado_em,m.id,c.namespace
LIMIT 100;
ROLLBACK;
-- Ausencia de correlacao nao prova origem Uzapi. Nao altera/reprocessa nenhuma mensagem.
