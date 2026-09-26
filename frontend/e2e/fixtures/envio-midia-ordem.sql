-- E217 — dados sintéticos para e2e/envio-midia-ordem.spec.ts. Só para banco de desenvolvimento já
-- semeado pelo perfil dev (usa o canal e os usuários do R__seed_dev). Idempotente.
-- A última mensagem do lead é recente para manter a janela de 24h aberta e o composer livre.
--
-- Uso: psql "$URL" -v ON_ERROR_STOP=1 -f e2e/fixtures/envio-midia-ordem.sql

INSERT INTO lead (id, nome, telefone, status_basico, atendente_responsavel_id, criado_em)
SELECT 'e2170000-0000-4000-8000-000000000001', 'Lead sintético E217 (envio de mídia)',
       '5500000002170', 'EM_ATENDIMENTO', u.id, now()
  FROM usuario u WHERE u.email = 'ana@dev.local'
ON CONFLICT (id) DO NOTHING;

INSERT INTO atendimento (id, lead_id, canal_id, atendente_id, status, iniciado_em)
SELECT 'e2170000-0000-4000-8000-0000000000a1', 'e2170000-0000-4000-8000-000000000001',
       'ca000000-0000-4000-8000-000000000001', u.id, 'EM_ATENDIMENTO', now() - interval '1 hour'
  FROM usuario u WHERE u.email = 'ana@dev.local'
ON CONFLICT (id) DO NOTHING;

INSERT INTO mensagem (id, atendimento_id, remetente_tipo, tipo, conteudo, status_entrega, enviado_em)
SELECT gen_random_uuid(), 'e2170000-0000-4000-8000-0000000000a1', 'LEAD', 'TEXTO',
       'Pode me mandar a proposta em PDF?', 'ENTREGUE', now()
 WHERE NOT EXISTS (
   SELECT 1 FROM mensagem
    WHERE atendimento_id = 'e2170000-0000-4000-8000-0000000000a1'
      AND remetente_tipo = 'LEAD'
      AND enviado_em > now() - interval '12 hours');

-- A janela de 24h lê a coluna desnormalizada (V53), não a tabela de mensagens.
UPDATE lead SET ultima_mensagem_do_lead_em = now()
 WHERE id = 'e2170000-0000-4000-8000-000000000001';
