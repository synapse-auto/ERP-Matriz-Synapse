-- E211 — dados sintéticos para e2e/contato-compartilhado.spec.ts. Depende de cabecalho-conversa.sql
-- (conversa da Ana onde a mensagem de contato é inserida) e do seed dev. Idempotente. Só dev/e2e.

INSERT INTO lead (id, nome, telefone, status_basico, atendente_responsavel_id, criado_em)
SELECT 'e2110000-0000-4000-8000-000000000002', 'Contato já atendido pela Ana', '5561988887777', 'EM_ATENDIMENTO', u.id, now()
  FROM usuario u WHERE u.email = 'ana@dev.local' ON CONFLICT (id) DO NOTHING;
INSERT INTO atendimento (id, lead_id, canal_id, atendente_id, status, iniciado_em)
SELECT 'e2110000-0000-4000-8000-0000000000b2', 'e2110000-0000-4000-8000-000000000002', 'ca000000-0000-4000-8000-000000000001', u.id, 'FINALIZADO', now() - interval '3 days'
  FROM usuario u WHERE u.email = 'ana@dev.local' ON CONFLICT (id) DO NOTHING;
INSERT INTO mensagem (id, atendimento_id, remetente_tipo, tipo, conteudo, midia_metadados, status_entrega, enviado_em)
SELECT gen_random_uuid(), 'e2100000-0000-4000-8000-0000000000a1', 'LEAD', 'CONTATO', 'Contato compartilhado',
 '{"contatos":[{"nome":"Contato já atendido pela Ana","telefones":[{"numero":"+55 61 98888-7777","waId":"5561988887777","tipo":"CELL"}]},{"nome":"Fornecedora Vidros Planalto Central Comércio e Representações Ltda","telefones":[{"numero":"+55 61 97777-1234","tipo":"WORK"},{"numero":"+55 61 3333-4444","tipo":"HOME"}]},{"nome":"Sem telefone","telefones":[]}]}'::jsonb,
 'ENTREGUE', now()
 WHERE NOT EXISTS (SELECT 1 FROM mensagem WHERE atendimento_id = 'e2100000-0000-4000-8000-0000000000a1' AND tipo = 'CONTATO');
