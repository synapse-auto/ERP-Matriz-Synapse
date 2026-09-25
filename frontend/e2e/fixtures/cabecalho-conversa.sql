-- E210 — dados sintéticos para e2e/cabecalho-conversa.spec.ts. Só para banco de desenvolvimento
-- já semeado pelo perfil dev (usa as tags, o canal e os usuários do R__seed_dev). Idempotente.
--
-- Uso: psql "$URL" -v ON_ERROR_STOP=1 -f e2e/fixtures/cabecalho-conversa.sql

INSERT INTO lead (id, nome, telefone, empresa, status_basico, atendente_responsavel_id, criado_em)
SELECT 'e2100000-0000-4000-8000-000000000001',
       'Maria Aparecida dos Santos Vasconcelos Albuquerque de Oliveira',
       '5561999990000', 'Construtora Horizonte Planalto Central', 'EM_ATENDIMENTO', u.id, now()
  FROM usuario u WHERE u.email = 'ana@dev.local'
ON CONFLICT (id) DO NOTHING;

INSERT INTO atendimento (id, lead_id, canal_id, atendente_id, status, iniciado_em)
SELECT 'e2100000-0000-4000-8000-0000000000a1', 'e2100000-0000-4000-8000-000000000001',
       'ca000000-0000-4000-8000-000000000001', u.id, 'EM_ATENDIMENTO', now() - interval '1 hour'
  FROM usuario u WHERE u.email = 'ana@dev.local'
ON CONFLICT (id) DO NOTHING;

INSERT INTO mensagem (id, atendimento_id, remetente_tipo, remetente_id, tipo, conteudo, status_entrega, enviado_em)
SELECT gen_random_uuid(), 'e2100000-0000-4000-8000-0000000000a1', r.tipo::remetente_tipo,
       CASE WHEN r.tipo = 'ATENDENTE' THEN u.id END, 'TEXTO', r.conteudo, 'ENTREGUE',
       now() - (r.ordem || ' minutes')::interval
  FROM usuario u,
       (VALUES (3, 'LEAD', 'Bom dia! Preciso de um orçamento de vidro temperado para a fachada.'),
               (2, 'ATENDENTE', 'Bom dia, Maria! Pode me passar as medidas do vão?'),
               (1, 'LEAD', 'São 4 painéis de 1,20 x 2,40 m.')) AS r(ordem, tipo, conteudo)
 WHERE u.email = 'ana@dev.local'
   AND NOT EXISTS (SELECT 1 FROM mensagem m WHERE m.atendimento_id = 'e2100000-0000-4000-8000-0000000000a1');

INSERT INTO lead_tag (lead_id, tag_id)
SELECT 'e2100000-0000-4000-8000-000000000001', t.id FROM tag t
 WHERE t.id::text LIKE '7a000000-0000-4000-8000-00000000000%'
ON CONFLICT DO NOTHING;
