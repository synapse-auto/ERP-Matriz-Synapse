\timing on
INSERT INTO usuario (id, nome, email, senha_hash, papel, status_presenca, ativo)
SELECT gen_random_uuid(), 'Atendente '||g, 'at'||g||'@perf.local', 'x', 'ATENDENTE', 'ONLINE', true FROM generate_series(1,25) g;

CREATE TEMP TABLE atendentes AS SELECT id, row_number() over () AS n FROM usuario WHERE papel='ATENDENTE';
INSERT INTO lead (id, nome, telefone, cpf, status_basico, atendente_responsavel_id, criado_em, ultima_interacao_em)
SELECT gen_random_uuid(),
       (ARRAY['Maria','Joao','Ana','Pedro','Lucas','Julia','Carla','Rafael','Bruna','Diego'])[1+(g%10)]||' '||
       (ARRAY['Silva','Souza','Oliveira','Santos','Lima','Costa','Pereira','Alves','Rocha','Gomes'])[1+((g/10)%10)]||' '||g,
       '5561'||lpad((900000000+g)::text,9,'0'),
       lpad((g*7919 % 100000000000)::text, 11, '0'),
       (ARRAY['IA','EM_ATENDIMENTO','FINALIZADO','FINALIZADO','FINALIZADO'])[1+(g%5)]::status_basico_lead,
       CASE WHEN g%5=0 THEN NULL ELSE (SELECT id FROM atendentes WHERE n = 1+(g % (SELECT count(*) FROM atendentes))) END,
       now() - (g||' minutes')::interval, now() - ((g%5000)||' minutes')::interval
  FROM generate_series(1,200000) g;
INSERT INTO atendimento (id, lead_id, atendente_id, status, iniciado_em, finalizado_em)
SELECT gen_random_uuid(), l.id, l.atendente_responsavel_id,
       CASE l.status_basico WHEN 'FINALIZADO' THEN 'FINALIZADO' WHEN 'IA' THEN 'EM_IA' ELSE 'EM_ATENDIMENTO' END::status_atendimento,
       l.criado_em, CASE WHEN l.status_basico='FINALIZADO' THEN l.criado_em + interval '1 hour' END
  FROM lead l WHERE l.nome LIKE '% %';

INSERT INTO outbox_evento (id, tipo, payload, criado_em, publicado_em, tentativas)
SELECT gen_random_uuid(), 'canal.mensagem.enviar',
       jsonb_build_object('atendimentoId', gen_random_uuid(), 'mensagemId', gen_random_uuid(), 'conteudo', repeat('x', 40)),
       now() - (g||' seconds')::interval, now() - (g||' seconds')::interval, 1
  FROM generate_series(1,1500000) g;

-- chat: uma conversa direta por par de usuarios e 20 grupos com 10 participantes
CREATE TEMP TABLE us AS SELECT id, row_number() over (order by id) n FROM usuario;
CREATE TEMP TABLE pares AS SELECT a.id a, b.id b, gen_random_uuid() conversa FROM us a JOIN us b ON a.n < b.n;
INSERT INTO chat_interno_conversa (id, tipo, criado_em) SELECT conversa, 'DIRETA', now() - interval '60 days' FROM pares;
INSERT INTO chat_interno_participante (conversa_id, usuario_id, lido_ate) SELECT conversa, a, now() - interval '1 day' FROM pares UNION ALL SELECT conversa, b, now() - interval '1 day' FROM pares;
CREATE TEMP TABLE grupos AS SELECT gen_random_uuid() id, g FROM generate_series(1,20) g;
INSERT INTO chat_interno_conversa (id, tipo, criado_em, nome) SELECT id, 'GRUPO', now() - interval '60 days', 'Grupo '||g FROM grupos;
INSERT INTO chat_interno_participante (conversa_id, usuario_id, lido_ate)
SELECT gr.id, us.id, now() - interval '1 day' FROM grupos gr JOIN us ON ((us.n + gr.g) % 3) = 0;
CREATE TEMP TABLE convs AS SELECT conversa_id, array_agg(usuario_id) membros, row_number() over () n FROM chat_interno_participante GROUP BY conversa_id;
INSERT INTO chat_interno_mensagem (id, conversa_id, remetente_id, tipo, conteudo, enviado_em)
SELECT gen_random_uuid(), c.conversa_id, c.membros[1 + (g % array_length(c.membros,1))], 'TEXTO', 'mensagem '||g,
       now() - ((g % 86400)||' minutes')::interval
  FROM generate_series(1,300000) g JOIN convs c ON c.n = 1 + (g % (SELECT count(*) FROM convs));
ANALYZE;
SELECT relname, n_live_tup, pg_size_pretty(pg_total_relation_size(relid)) FROM pg_stat_user_tables
 WHERE relname IN ('lead','atendimento','outbox_evento','chat_interno_mensagem','chat_interno_conversa','chat_interno_participante') ORDER BY 1;
