-- Dados sintéticos para reproduzir o convite colaborativo (A = Ana, B = Bruno, C = Caio).
-- Só para banco de desenvolvimento semeado pelo perfil dev. Recria o cenário a cada execução.
--
-- Uso: psql "$URL" -v ON_ERROR_STOP=1 -f e2e/fixtures/convite-colaborativo.sql

INSERT INTO usuario (id, nome, email, senha_hash, papel, senha_alterada_em)
SELECT 'c0c00000-0000-4000-8000-0000000000c3', 'Caio Atendente', 'caio.convite@dev.local', u.senha_hash, 'ATENDENTE', now()
  FROM usuario u WHERE u.email = 'ana@dev.local'
ON CONFLICT (id) DO UPDATE SET senha_alterada_em = EXCLUDED.senha_alterada_em;

DELETE FROM mensagem_envio_idempotencia WHERE lead_id = 'c0c00000-0000-4000-8000-000000000001';
DELETE FROM mensagem WHERE atendimento_id = 'c0c00000-0000-4000-8000-0000000000a1';
DELETE FROM pedido_entrada_atendimento WHERE atendimento_id = 'c0c00000-0000-4000-8000-0000000000a1';
DELETE FROM atendimento_participante WHERE atendimento_id = 'c0c00000-0000-4000-8000-0000000000a1';
DELETE FROM atendimento WHERE lead_id = 'c0c00000-0000-4000-8000-000000000001';
DELETE FROM lead WHERE id = 'c0c00000-0000-4000-8000-000000000001';

INSERT INTO lead (id, nome, telefone, status_basico, atendente_responsavel_id, ultima_mensagem_do_lead_em, criado_em)
SELECT 'c0c00000-0000-4000-8000-000000000001', 'Cliente Convite Colaborativo', '5561999990777',
       'EM_ATENDIMENTO', u.id, now(), now()
  FROM usuario u WHERE u.email = 'ana@dev.local';

INSERT INTO atendimento (id, lead_id, canal_id, atendente_id, status, iniciado_em)
SELECT 'c0c00000-0000-4000-8000-0000000000a1', 'c0c00000-0000-4000-8000-000000000001',
       'ca000000-0000-4000-8000-000000000001', u.id, 'EM_ATENDIMENTO', now() - interval '1 hour'
  FROM usuario u WHERE u.email = 'ana@dev.local';

INSERT INTO mensagem (id, atendimento_id, remetente_tipo, remetente_id, tipo, conteudo, status_entrega, enviado_em)
VALUES (gen_random_uuid(), 'c0c00000-0000-4000-8000-0000000000a1', 'LEAD', NULL, 'TEXTO',
        'Bom dia, preciso de um orçamento.', 'ENTREGUE', now() - interval '5 minutes');

-- Atendente com nome longo: o seletor precisa manter nome e ação legíveis em tela estreita.
INSERT INTO usuario (id, nome, email, senha_hash, papel, senha_alterada_em)
SELECT 'c0c00000-0000-4000-8000-0000000000c4', 'Maria Aparecida dos Santos Vasconcelos Albuquerque de Oliveira',
       'maria.convite@dev.local', u.senha_hash, 'ATENDENTE', now()
  FROM usuario u WHERE u.email = 'ana@dev.local'
ON CONFLICT (id) DO UPDATE SET senha_alterada_em = EXCLUDED.senha_alterada_em;
