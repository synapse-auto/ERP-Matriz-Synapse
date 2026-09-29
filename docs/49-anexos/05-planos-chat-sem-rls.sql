\pset pager off
SELECT id AS u FROM usuario WHERE email='ana@dev.local' \gset
SELECT count(*) AS conversas_da_ana FROM chat_interno_participante WHERE usuario_id = :'u';
SELECT indexdef FROM pg_indexes WHERE tablename IN ('chat_interno_mensagem','chat_interno_participante');
\echo ===== listarConversas (synapse_app, ATENDENTE)
BEGIN; SELECT set_config('app.usuario_id', :'u', true), set_config('app.papel','ATENDENTE', true) \g /dev/null
EXPLAIN (ANALYZE, BUFFERS, SUMMARY ON) 
            SELECT c.id, c.tipo::text,
                   CASE WHEN c.tipo = 'GRUPO' THEN c.nome
                        ELSE COALESCE(string_agg(DISTINCT u.nome, ', ' ORDER BY u.nome), '')
                   END AS participantes,
                   CASE WHEN ultima.removida_em IS NULL THEN ultima.conteudo END AS ultima_mensagem,
                   ultima.enviado_em AS ultima_mensagem_em,
                   COALESCE((SELECT count(*) FROM chat_interno_mensagem nova
                       WHERE nova.conversa_id = c.id AND nova.remetente_id <> :'u'::uuid
                         AND nova.enviado_em > COALESCE(cp.lido_ate, TIMESTAMPTZ 'epoch')), 0) AS nao_lidas,
                   CASE WHEN c.tipo = 'DIRETA' AND MAX(u.foto_referencia) IS NOT NULL
                        THEN '/api/v1/me/foto/' || MAX(u.id::text) END AS foto_url
              FROM chat_interno_conversa c
              JOIN chat_interno_participante cp ON cp.conversa_id = c.id AND cp.usuario_id = :'u'::uuid
              LEFT JOIN chat_interno_participante outros ON outros.conversa_id = c.id
                AND outros.usuario_id <> :'u'::uuid
              LEFT JOIN usuario u ON u.id = outros.usuario_id
              LEFT JOIN LATERAL (SELECT m.conteudo, m.enviado_em, m.removida_em FROM chat_interno_mensagem m
                WHERE m.conversa_id = c.id ORDER BY m.enviado_em DESC LIMIT 1) ultima ON TRUE
             GROUP BY c.id, c.tipo, c.nome, ultima.conteudo, ultima.enviado_em, ultima.removida_em, cp.lido_ate
             ORDER BY COALESCE(ultima.enviado_em, c.criado_em) DESC;
ROLLBACK;
