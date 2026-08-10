-- =========================================================
-- Seed de DEMONSTRACAO (E17b §Bloco 0).
--
-- NAO e dado mockado no sentido que o AGENTS.md proibe. Dado mockado e a
-- aplicacao fingindo ter informacao que nao tem (numero de exemplo escrito
-- direto no componente React). Isto aqui e o oposto: registros reais,
-- gravados no banco, que toda tela le pelo caminho normal (filtro modular,
-- RLS, contadores denormalizados). A diferenca e que a origem do dado e
-- este script, nao um cliente de verdade — e e por isso que todo nome e
-- obviamente falso.
--
-- NUNCA rode isto contra uma instancia com dado de cliente real. E para
-- homologacao e demonstracao, apenas. `limpar-demonstracao.sql` desfaz
-- exatamente o que este script cria, e e obrigatorio antes do go-live.
--
-- Idempotente: ids fixos com o prefixo "de"/"da"/"dm"/"db"/"dp" (leads,
-- atendimentos, mensagens, lembretes, mensagens programadas) + ON CONFLICT.
-- Rodar de novo reconcilia, nunca duplica.
--
-- RLS (V12) tem WITH CHECK (TRUE) em lead/atendimento/lembrete/
-- mensagem_programada — INSERT nao exige app.papel/app.usuario_id de
-- sessao. So SELECT/UPDATE/DELETE exigem contexto, e este script so insere.
-- =========================================================

\set ON_ERROR_STOP on

BEGIN;

-- --- Leads -------------------------------------------------------------
-- Etapas e usuarios vem do R__seed_dev.sql (mesmos ids fixos). Se este
-- script rodar antes do seed de dev, os INSERTs abaixo falham por FK —
-- falha alta e imediata, nunca lead orfao.
INSERT INTO lead (
    id, nome, telefone, email, empresa, localizacao, canal_origem_id,
    status_basico, etapa_atendimento_id, atendente_responsavel_id,
    num_atendimentos, num_mensagens, ultima_interacao_em
) VALUES
    ('de000000-0000-4000-8000-000000000001', 'Cliente Teste 1', '+5561999990001',
     'cliente.teste1@exemplo.invalido', NULL, 'Brasília · DF',
     'ca000000-0000-4000-8000-000000000001', 'IA',
     'e1000000-0000-4000-8000-000000000001', NULL, 0, 0, NULL),
    ('de000000-0000-4000-8000-000000000002', 'Cliente Teste 2', '+5561999990002',
     'cliente.teste2@exemplo.invalido', 'Comércio Exemplo Ltda', 'Taguatinga · DF',
     'ca000000-0000-4000-8000-000000000001', 'EM_ATENDIMENTO',
     'e1000000-0000-4000-8000-000000000002', '11000000-0000-4000-8000-000000000004',
     1, 3, '2026-08-05T14:20:00Z'),
    ('de000000-0000-4000-8000-000000000003', 'Obra Exemplo — Asa Norte', '+5561999990003',
     'obra.exemplo@exemplo.invalido', 'Construtora Exemplo S.A.', 'Asa Norte · DF',
     'ca000000-0000-4000-8000-000000000001', 'EM_ATENDIMENTO',
     'e1000000-0000-4000-8000-000000000003', '11000000-0000-4000-8000-000000000005',
     1, 3, '2026-08-06T10:05:00Z'),
    ('de000000-0000-4000-8000-000000000004', 'Cliente Teste 4', '+5561999990004',
     'cliente.teste4@exemplo.invalido', NULL, 'Águas Claras · DF',
     'ca000000-0000-4000-8000-000000000001', 'EM_ATENDIMENTO',
     'e1000000-0000-4000-8000-000000000004', '11000000-0000-4000-8000-000000000004',
     1, 3, '2026-08-07T09:40:00Z'),
    ('de000000-0000-4000-8000-000000000005', 'Cliente Teste 5 — Pós-venda', '+5561999990005',
     'cliente.teste5@exemplo.invalido', 'Vidraçaria Exemplo', 'Sobradinho · DF',
     'ca000000-0000-4000-8000-000000000001', 'FINALIZADO',
     'e1000000-0000-4000-8000-000000000006', '11000000-0000-4000-8000-000000000005',
     1, 2, '2026-08-02T16:00:00Z'),
    ('de000000-0000-4000-8000-000000000006', 'Cliente Teste 6', '+5561999990006',
     'cliente.teste6@exemplo.invalido', NULL, 'Ceilândia · DF',
     'ca000000-0000-4000-8000-000000000001', 'IA',
     'e1000000-0000-4000-8000-000000000001', NULL, 0, 0, NULL)
ON CONFLICT (id) DO UPDATE
    SET nome = EXCLUDED.nome,
        telefone = EXCLUDED.telefone,
        email = EXCLUDED.email,
        empresa = EXCLUDED.empresa,
        localizacao = EXCLUDED.localizacao,
        canal_origem_id = EXCLUDED.canal_origem_id,
        status_basico = EXCLUDED.status_basico,
        etapa_atendimento_id = EXCLUDED.etapa_atendimento_id,
        atendente_responsavel_id = EXCLUDED.atendente_responsavel_id,
        num_atendimentos = EXCLUDED.num_atendimentos,
        num_mensagens = EXCLUDED.num_mensagens,
        ultima_interacao_em = EXCLUDED.ultima_interacao_em;

-- --- Tags dos leads ------------------------------------------------------
INSERT INTO lead_tag (lead_id, tag_id) VALUES
    ('de000000-0000-4000-8000-000000000002', '7a000000-0000-4000-8000-000000000001'), -- Cliente Teste 2: Orcamento
    ('de000000-0000-4000-8000-000000000003', '7a000000-0000-4000-8000-000000000003'), -- Obra Exemplo: Obra grande
    ('de000000-0000-4000-8000-000000000003', '7a000000-0000-4000-8000-000000000002'), -- Obra Exemplo: Urgente
    ('de000000-0000-4000-8000-000000000004', '7a000000-0000-4000-8000-000000000002'), -- Cliente Teste 4: Urgente
    ('de000000-0000-4000-8000-000000000005', '7a000000-0000-4000-8000-000000000004'), -- Cliente Teste 5: Recorrente
    ('de000000-0000-4000-8000-000000000005', '7a000000-0000-4000-8000-000000000005')  -- Cliente Teste 5: Pos-venda
ON CONFLICT (lead_id, tag_id) DO NOTHING;

-- --- Atendimentos --------------------------------------------------------
INSERT INTO atendimento (id, lead_id, canal_id, canal_credencial_id, atendente_id, status, iniciado_em, finalizado_em) VALUES
    ('da000000-0000-4000-8000-000000000002', 'de000000-0000-4000-8000-000000000002',
     'ca000000-0000-4000-8000-000000000001', 'cc000000-0000-4000-8000-000000000001',
     '11000000-0000-4000-8000-000000000004', 'EM_ATENDIMENTO', '2026-08-05T14:00:00Z', NULL),
    ('da000000-0000-4000-8000-000000000003', 'de000000-0000-4000-8000-000000000003',
     'ca000000-0000-4000-8000-000000000001', 'cc000000-0000-4000-8000-000000000001',
     '11000000-0000-4000-8000-000000000005', 'EM_ATENDIMENTO', '2026-08-06T09:50:00Z', NULL),
    ('da000000-0000-4000-8000-000000000004', 'de000000-0000-4000-8000-000000000004',
     'ca000000-0000-4000-8000-000000000001', 'cc000000-0000-4000-8000-000000000001',
     '11000000-0000-4000-8000-000000000004', 'EM_ATENDIMENTO', '2026-08-07T09:30:00Z', NULL),
    ('da000000-0000-4000-8000-000000000005', 'de000000-0000-4000-8000-000000000005',
     'ca000000-0000-4000-8000-000000000001', 'cc000000-0000-4000-8000-000000000001',
     '11000000-0000-4000-8000-000000000005', 'FINALIZADO', '2026-08-02T15:30:00Z', '2026-08-02T16:00:00Z')
ON CONFLICT (id) DO UPDATE
    SET status = EXCLUDED.status,
        atendente_id = EXCLUDED.atendente_id,
        finalizado_em = EXCLUDED.finalizado_em;

-- --- Mensagens -------------------------------------------------------------
-- PRIMARY KEY (id, enviado_em) porque a tabela e particionada por enviado_em
-- (V5); ON CONFLICT precisa citar as duas colunas da chave composta.
INSERT INTO mensagem (id, atendimento_id, remetente_tipo, remetente_id, tipo, conteudo, status_entrega, enviado_em) VALUES
    ('dm000000-0000-4000-8000-000000000201', 'da000000-0000-4000-8000-000000000002', 'LEAD', NULL, 'TEXTO',
     'Bom dia! Gostaria de um orçamento para box de banheiro.', 'LIDO', '2026-08-05T14:00:00Z'),
    ('dm000000-0000-4000-8000-000000000202', 'da000000-0000-4000-8000-000000000002', 'ATENDENTE',
     '11000000-0000-4000-8000-000000000004', 'TEXTO',
     'Bom dia! Claro, pode me passar as medidas do vão?', 'LIDO', '2026-08-05T14:05:00Z'),
    ('dm000000-0000-4000-8000-000000000203', 'da000000-0000-4000-8000-000000000002', 'LEAD', NULL, 'TEXTO',
     'Vou medir e te mando ainda hoje, obrigado!', 'ENTREGUE', '2026-08-05T14:20:00Z'),

    ('dm000000-0000-4000-8000-000000000301', 'da000000-0000-4000-8000-000000000003', 'LEAD', NULL, 'TEXTO',
     'Precisamos de fachada de vidro para uma obra na Asa Norte, é urgente.', 'LIDO', '2026-08-06T09:50:00Z'),
    ('dm000000-0000-4000-8000-000000000302', 'da000000-0000-4000-8000-000000000003', 'ATENDENTE',
     '11000000-0000-4000-8000-000000000005', 'TEXTO',
     'Entendido. Vou preparar a proposta e te retorno ainda pela manhã.', 'LIDO', '2026-08-06T09:58:00Z'),
    ('dm000000-0000-4000-8000-000000000303', 'da000000-0000-4000-8000-000000000003', 'LEAD', NULL, 'TEXTO',
     'Perfeito, aguardo.', 'ENTREGUE', '2026-08-06T10:05:00Z'),

    ('dm000000-0000-4000-8000-000000000401', 'da000000-0000-4000-8000-000000000004', 'LEAD', NULL, 'TEXTO',
     'Olá, ainda tenho interesse no espelho que conversamos semana passada.', 'LIDO', '2026-08-07T09:30:00Z'),
    ('dm000000-0000-4000-8000-000000000402', 'da000000-0000-4000-8000-000000000004', 'ATENDENTE',
     '11000000-0000-4000-8000-000000000004', 'TEXTO',
     'Oi! Sim, o valor que passei continua válido até o fim do mês.', 'LIDO', '2026-08-07T09:35:00Z'),
    ('dm000000-0000-4000-8000-000000000403', 'da000000-0000-4000-8000-000000000004', 'LEAD', NULL, 'TEXTO',
     'Ótimo, vou fechar então. Pode me mandar a forma de pagamento?', 'ENTREGUE', '2026-08-07T09:40:00Z'),

    ('dm000000-0000-4000-8000-000000000501', 'da000000-0000-4000-8000-000000000005', 'ATENDENTE',
     '11000000-0000-4000-8000-000000000005', 'TEXTO',
     'Instalação concluída! Qualquer problema é só chamar.', 'LIDO', '2026-08-02T15:55:00Z'),
    ('dm000000-0000-4000-8000-000000000502', 'da000000-0000-4000-8000-000000000005', 'LEAD', NULL, 'TEXTO',
     'Ficou ótimo, muito obrigado pelo atendimento!', 'LIDO', '2026-08-02T16:00:00Z')
ON CONFLICT (id, enviado_em) DO UPDATE
    SET conteudo = EXCLUDED.conteudo,
        status_entrega = EXCLUDED.status_entrega;

-- --- Lembretes ---------------------------------------------------------
INSERT INTO lembrete (id, lead_id, atendente_id, texto, data_hora, origem_automatica, status) VALUES
    ('db000000-0000-4000-8000-000000000001', 'de000000-0000-4000-8000-000000000002',
     '11000000-0000-4000-8000-000000000004', 'Ligar para confirmar as medidas do box.',
     '2026-08-12T13:00:00Z', FALSE, 'PENDENTE'),
    ('db000000-0000-4000-8000-000000000002', 'de000000-0000-4000-8000-000000000004',
     '11000000-0000-4000-8000-000000000004', 'Enviar forma de pagamento do espelho.',
     '2026-08-08T10:00:00Z', FALSE, 'PENDENTE')
ON CONFLICT (id) DO UPDATE
    SET texto = EXCLUDED.texto,
        data_hora = EXCLUDED.data_hora,
        status = EXCLUDED.status;

-- --- Mensagens programadas -----------------------------------------------
INSERT INTO mensagem_programada (id, lead_id, atendente_id, conteudo, data_envio, status) VALUES
    ('dp000000-0000-4000-8000-000000000001', 'de000000-0000-4000-8000-000000000003',
     '11000000-0000-4000-8000-000000000005',
     'Bom dia! Segue a proposta da fachada conforme combinamos.', '2026-08-10T09:00:00Z', 'AGENDADA'),
    ('dp000000-0000-4000-8000-000000000002', 'de000000-0000-4000-8000-000000000005',
     '11000000-0000-4000-8000-000000000005',
     'Passando para saber se está tudo bem com a instalação.', '2026-09-02T10:00:00Z', 'AGENDADA')
ON CONFLICT (id) DO UPDATE
    SET conteudo = EXCLUDED.conteudo,
        data_envio = EXCLUDED.data_envio,
        status = EXCLUDED.status;

COMMIT;
\echo 'Seed de demonstracao concluido: 6 leads, 4 atendimentos, 11 mensagens, 2 lembretes, 2 mensagens programadas.'
