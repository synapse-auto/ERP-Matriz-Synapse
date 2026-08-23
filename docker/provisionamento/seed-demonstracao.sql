-- =========================================================
-- Seed de DEMONSTRACAO para homologacao.
--
-- Idempotente: ids fixos validos + ON CONFLICT. Rodar novamente reconcilia
-- os registros deste seed e nao duplica dados.
--
-- NUNCA rode contra uma instancia com dados de cliente real. Este script
-- apenas acrescenta dados; nao apaga nem altera leads/conversas preexistentes.
-- `limpar-demonstracao.sql` remove somente os ids reservados abaixo.
--
-- Senha publica das quatro contas: atendente123. Uso exclusivo em demo.
-- =========================================================

\set ON_ERROR_STOP on

BEGIN;

-- O canal real vem do provisionamento. Resolver por estado, e nao pelo UUID do
-- seed de desenvolvimento, faz este script funcionar no perfil padrao.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM canal c
          JOIN canal_credencial cc ON cc.canal_id = c.id
         WHERE c.ativo
           AND cc.ativo
           AND COALESCE(btrim(cc.identificador_externo), '') <> ''
    ) THEN
        RAISE EXCEPTION 'canal ativo sem phone_number_id; execute o provisionamento antes do seed';
    END IF;

    IF (SELECT count(*) FROM etapa_atendimento) < 5 THEN
        RAISE EXCEPTION 'funil incompleto; provisione pelo menos cinco etapas antes do seed';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM etapa_atendimento WHERE resultado = 'GANHO') THEN
        RAISE EXCEPTION 'funil sem etapa GANHO; corrija o provisionamento antes do seed';
    END IF;
END $$;

SELECT c.id AS demo_canal_id,
       cc.id AS demo_credencial_id
  FROM canal c
  JOIN canal_credencial cc ON cc.canal_id = c.id
 WHERE c.ativo
   AND cc.ativo
   AND COALESCE(btrim(cc.identificador_externo), '') <> ''
 ORDER BY cc.vigente_desde DESC, cc.id
 LIMIT 1
\gset

SELECT id AS demo_etapa_1_id
  FROM etapa_atendimento ORDER BY ordem, id LIMIT 1 OFFSET 0
\gset
SELECT id AS demo_etapa_2_id
  FROM etapa_atendimento ORDER BY ordem, id LIMIT 1 OFFSET 1
\gset
SELECT id AS demo_etapa_3_id
  FROM etapa_atendimento ORDER BY ordem, id LIMIT 1 OFFSET 2
\gset
SELECT id AS demo_etapa_4_id
  FROM etapa_atendimento ORDER BY ordem, id LIMIT 1 OFFSET 3
\gset
SELECT id AS demo_etapa_ganho_id, nome AS demo_etapa_ganho_nome
  FROM etapa_atendimento WHERE resultado = 'GANHO' ORDER BY ordem, id LIMIT 1
\gset

-- --- Atendentes ---------------------------------------------------------
-- A ordem alfabetica (Bruno, Caio, Nina, Zelia) e diferente da ordem dos ids
-- (Zelia, Caio, Nina, Bruno). Assim um ORDER BY nome indevido nao fica invisivel.
INSERT INTO usuario
    (id, nome, email, senha_hash, papel, status_presenca, ativo, senha_alterada_em)
VALUES
    ('d4000000-0000-4000-8000-000000000001', 'Zelia Demonstracao',
     'zelia.demo@dev.local',
     '$2a$10$3RQQjf4jsEx11gmaTnUkkeky8yurpHKdl5UPkuWlVe7tsphWGmj42',
     'ATENDENTE', 'ONLINE', TRUE, now()),
    ('d4000000-0000-4000-8000-000000000002', 'Caio Demonstracao',
     'caio.demo@dev.local',
     '$2a$10$3RQQjf4jsEx11gmaTnUkkeky8yurpHKdl5UPkuWlVe7tsphWGmj42',
     'ATENDENTE', 'ONLINE', TRUE, now()),
    ('d4000000-0000-4000-8000-000000000003', 'Nina Demonstracao',
     'nina.demo@dev.local',
     '$2a$10$3RQQjf4jsEx11gmaTnUkkeky8yurpHKdl5UPkuWlVe7tsphWGmj42',
     'ATENDENTE', 'ONLINE', TRUE, now()),
    ('d4000000-0000-4000-8000-000000000004', 'Bruno Demonstracao',
     'bruno.demo@dev.local',
     '$2a$10$3RQQjf4jsEx11gmaTnUkkeky8yurpHKdl5UPkuWlVe7tsphWGmj42',
     'ATENDENTE', 'ONLINE', TRUE, now())
ON CONFLICT (id) DO UPDATE
    SET nome = EXCLUDED.nome,
        email = EXCLUDED.email,
        senha_hash = EXCLUDED.senha_hash,
        papel = EXCLUDED.papel,
        status_presenca = EXCLUDED.status_presenca,
        ativo = EXCLUDED.ativo,
        senha_alterada_em = EXCLUDED.senha_alterada_em;

-- Mesmo default adotado pela V34/E36b: todo atendente ativo com permissao para
-- receber da IA; ONLINE continua sendo um criterio separado de elegibilidade.
INSERT INTO disponibilidade_atendente_ia (atendente_id, disponivel_para_ia)
SELECT id, TRUE
  FROM usuario
 WHERE id::text LIKE 'd4000000-%'
ON CONFLICT (atendente_id) DO UPDATE
    SET disponivel_para_ia = TRUE,
        atualizado_em = now();

-- --- Tags ---------------------------------------------------------------
-- Reutiliza tags de mesmo nome ja provisionadas. Se nao existirem, cria ids
-- reservados que a limpeza consegue remover sem tocar em configuracao real.
INSERT INTO tag (id, nome, cor, icone) VALUES
    ('d5000000-0000-4000-8000-000000000001', 'Orcamento',   '#0EA5E9', 'calculator'),
    ('d5000000-0000-4000-8000-000000000002', 'Urgente',     '#EF4444', 'alert-triangle'),
    ('d5000000-0000-4000-8000-000000000003', 'Obra grande', '#8B5CF6', 'building-2'),
    ('d5000000-0000-4000-8000-000000000004', 'Recorrente',  '#22C55E', 'repeat'),
    ('d5000000-0000-4000-8000-000000000005', 'Pos-venda',   '#14B8A6', 'life-buoy')
ON CONFLICT DO NOTHING;

SELECT id AS demo_tag_orcamento_id FROM tag WHERE nome = 'Orcamento' LIMIT 1
\gset
SELECT id AS demo_tag_urgente_id FROM tag WHERE nome = 'Urgente' LIMIT 1
\gset
SELECT id AS demo_tag_obra_id FROM tag WHERE nome = 'Obra grande' LIMIT 1
\gset
SELECT id AS demo_tag_recorrente_id FROM tag WHERE nome = 'Recorrente' LIMIT 1
\gset
SELECT id AS demo_tag_pos_venda_id FROM tag WHERE nome = 'Pos-venda' LIMIT 1
\gset

-- --- Leads --------------------------------------------------------------
INSERT INTO lead (
    id, nome, telefone, email, empresa, localizacao, canal_origem_id,
    status_basico, etapa_atendimento_id, atendente_responsavel_id,
    num_atendimentos, num_mensagens, ultima_interacao_em
) VALUES
    ('de000000-0000-4000-8000-000000000001', 'Cliente Teste 1', '5561999990001',
     'cliente.teste1@exemplo.invalido', NULL, 'Brasilia - DF',
     :'demo_canal_id'::uuid, 'IA', :'demo_etapa_1_id'::uuid, NULL, 0, 0, NULL),
    ('de000000-0000-4000-8000-000000000002', 'Cliente Teste 2', '5561999990002',
     'cliente.teste2@exemplo.invalido', 'Comercio Exemplo Ltda', 'Taguatinga - DF',
     :'demo_canal_id'::uuid, 'EM_ATENDIMENTO', :'demo_etapa_2_id'::uuid,
     'd4000000-0000-4000-8000-000000000001', 1, 3, '2026-08-21T14:20:00Z'),
    ('de000000-0000-4000-8000-000000000003', 'Obra Exemplo - Asa Norte', '5561999990003',
     'obra.exemplo@exemplo.invalido', 'Construtora Exemplo S.A.', 'Asa Norte - DF',
     :'demo_canal_id'::uuid, 'EM_ATENDIMENTO', :'demo_etapa_3_id'::uuid,
     'd4000000-0000-4000-8000-000000000002', 1, 3, '2026-08-22T10:05:00Z'),
    ('de000000-0000-4000-8000-000000000004', 'Cliente Teste 4', '5561999990004',
     'cliente.teste4@exemplo.invalido', NULL, 'Aguas Claras - DF',
     :'demo_canal_id'::uuid, 'EM_ATENDIMENTO', :'demo_etapa_4_id'::uuid,
     'd4000000-0000-4000-8000-000000000001', 1, 3, '2026-08-23T09:40:00Z'),
    ('de000000-0000-4000-8000-000000000005', 'Cliente Teste 5 - Venda', '5561999990005',
     'cliente.teste5@exemplo.invalido', 'Vidraçaria Exemplo', 'Sobradinho - DF',
     :'demo_canal_id'::uuid, 'FINALIZADO', :'demo_etapa_ganho_id'::uuid,
     'd4000000-0000-4000-8000-000000000003', 1, 2, '2026-08-20T16:00:00Z'),
    ('de000000-0000-4000-8000-000000000006', 'Cliente Teste 6', '5561999990006',
     'cliente.teste6@exemplo.invalido', NULL, 'Ceilandia - DF',
     :'demo_canal_id'::uuid, 'IA', :'demo_etapa_1_id'::uuid, NULL, 0, 0, NULL)
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

INSERT INTO lead_tag (lead_id, tag_id) VALUES
    ('de000000-0000-4000-8000-000000000002', :'demo_tag_orcamento_id'::uuid),
    ('de000000-0000-4000-8000-000000000003', :'demo_tag_obra_id'::uuid),
    ('de000000-0000-4000-8000-000000000003', :'demo_tag_urgente_id'::uuid),
    ('de000000-0000-4000-8000-000000000004', :'demo_tag_urgente_id'::uuid),
    ('de000000-0000-4000-8000-000000000005', :'demo_tag_recorrente_id'::uuid),
    ('de000000-0000-4000-8000-000000000005', :'demo_tag_pos_venda_id'::uuid)
ON CONFLICT (lead_id, tag_id) DO NOTHING;

-- --- Atendimentos -------------------------------------------------------
-- Carga aberta por atendente: Zelia=2, Caio=1, Nina=0, Bruno=0.
INSERT INTO atendimento
    (id, lead_id, canal_id, canal_credencial_id, atendente_id, status, iniciado_em, finalizado_em)
VALUES
    ('da000000-0000-4000-8000-000000000002', 'de000000-0000-4000-8000-000000000002',
     :'demo_canal_id'::uuid, :'demo_credencial_id'::uuid,
     'd4000000-0000-4000-8000-000000000001', 'EM_ATENDIMENTO', '2026-08-21T14:00:00Z', NULL),
    ('da000000-0000-4000-8000-000000000003', 'de000000-0000-4000-8000-000000000003',
     :'demo_canal_id'::uuid, :'demo_credencial_id'::uuid,
     'd4000000-0000-4000-8000-000000000002', 'EM_ATENDIMENTO', '2026-08-22T09:50:00Z', NULL),
    ('da000000-0000-4000-8000-000000000004', 'de000000-0000-4000-8000-000000000004',
     :'demo_canal_id'::uuid, :'demo_credencial_id'::uuid,
     'd4000000-0000-4000-8000-000000000001', 'EM_ATENDIMENTO', '2026-08-23T09:30:00Z', NULL),
    ('da000000-0000-4000-8000-000000000005', 'de000000-0000-4000-8000-000000000005',
     :'demo_canal_id'::uuid, :'demo_credencial_id'::uuid,
     'd4000000-0000-4000-8000-000000000003', 'FINALIZADO',
     '2026-08-20T15:30:00Z', '2026-08-20T16:00:00Z')
ON CONFLICT (id) DO UPDATE
    SET canal_id = EXCLUDED.canal_id,
        canal_credencial_id = EXCLUDED.canal_credencial_id,
        status = EXCLUDED.status,
        atendente_id = EXCLUDED.atendente_id,
        finalizado_em = EXCLUDED.finalizado_em;

-- --- Mensagens ----------------------------------------------------------
-- d1 e um prefixo hexadecimal valido; o antigo "dm" nao era UUID valido.
INSERT INTO mensagem
    (id, atendimento_id, remetente_tipo, remetente_id, tipo, conteudo, status_entrega, enviado_em)
VALUES
    ('d1000000-0000-4000-8000-000000000201', 'da000000-0000-4000-8000-000000000002',
     'LEAD', NULL, 'TEXTO', 'Bom dia! Gostaria de um orcamento para box de banheiro.',
     'LIDO', '2026-08-21T14:00:00Z'),
    ('d1000000-0000-4000-8000-000000000202', 'da000000-0000-4000-8000-000000000002',
     'ATENDENTE', 'd4000000-0000-4000-8000-000000000001', 'TEXTO',
     'Bom dia! Claro, pode me passar as medidas do vao?', 'LIDO', '2026-08-21T14:05:00Z'),
    ('d1000000-0000-4000-8000-000000000203', 'da000000-0000-4000-8000-000000000002',
     'LEAD', NULL, 'TEXTO', 'Vou medir e te mando ainda hoje, obrigado!',
     'ENTREGUE', '2026-08-21T14:20:00Z'),
    ('d1000000-0000-4000-8000-000000000301', 'da000000-0000-4000-8000-000000000003',
     'LEAD', NULL, 'TEXTO', 'Precisamos de fachada de vidro para uma obra, e urgente.',
     'LIDO', '2026-08-22T09:50:00Z'),
    ('d1000000-0000-4000-8000-000000000302', 'da000000-0000-4000-8000-000000000003',
     'ATENDENTE', 'd4000000-0000-4000-8000-000000000002', 'TEXTO',
     'Entendido. Vou preparar a proposta e retorno ainda pela manha.',
     'LIDO', '2026-08-22T09:58:00Z'),
    ('d1000000-0000-4000-8000-000000000303', 'da000000-0000-4000-8000-000000000003',
     'LEAD', NULL, 'TEXTO', 'Perfeito, aguardo.', 'ENTREGUE', '2026-08-22T10:05:00Z'),
    ('d1000000-0000-4000-8000-000000000401', 'da000000-0000-4000-8000-000000000004',
     'LEAD', NULL, 'TEXTO', 'Ola, ainda tenho interesse no espelho que conversamos.',
     'LIDO', '2026-08-23T09:30:00Z'),
    ('d1000000-0000-4000-8000-000000000402', 'da000000-0000-4000-8000-000000000004',
     'ATENDENTE', 'd4000000-0000-4000-8000-000000000001', 'TEXTO',
     'O valor continua valido ate o fim do mes.', 'LIDO', '2026-08-23T09:35:00Z'),
    ('d1000000-0000-4000-8000-000000000403', 'da000000-0000-4000-8000-000000000004',
     'LEAD', NULL, 'TEXTO', 'Otimo, vou fechar. Pode mandar a forma de pagamento?',
     'ENTREGUE', '2026-08-23T09:40:00Z'),
    ('d1000000-0000-4000-8000-000000000501', 'da000000-0000-4000-8000-000000000005',
     'ATENDENTE', 'd4000000-0000-4000-8000-000000000003', 'TEXTO',
     'Instalacao concluida! Qualquer problema e so chamar.', 'LIDO', '2026-08-20T15:55:00Z'),
    ('d1000000-0000-4000-8000-000000000502', 'da000000-0000-4000-8000-000000000005',
     'LEAD', NULL, 'TEXTO', 'Ficou otimo, muito obrigado pelo atendimento!',
     'LIDO', '2026-08-20T16:00:00Z')
ON CONFLICT (id, enviado_em) DO UPDATE
    SET atendimento_id = EXCLUDED.atendimento_id,
        remetente_tipo = EXCLUDED.remetente_tipo,
        remetente_id = EXCLUDED.remetente_id,
        conteudo = EXCLUDED.conteudo,
        status_entrega = EXCLUDED.status_entrega;

-- Uma venda real no read model deixa Dashboard e ranking da Equipe observaveis.
INSERT INTO evento_timeline
    (id, lead_id, atendimento_id, tipo, descricao, origem, ator_id, dados, criado_em)
VALUES
    ('d6000000-0000-4000-8000-000000000001',
     'de000000-0000-4000-8000-000000000005',
     'da000000-0000-4000-8000-000000000005',
     'ETAPA_ALTERADA', 'Nina Demonstracao moveu o lead para a etapa de venda.',
     'USUARIO', 'd4000000-0000-4000-8000-000000000003',
     jsonb_build_object(
         'etapa_anterior_id', :'demo_etapa_4_id',
         'etapa_nova_id', :'demo_etapa_ganho_id',
         'etapa_nova_nome', :'demo_etapa_ganho_nome',
         'resultado_novo', 'GANHO',
         'responsavel_id', 'd4000000-0000-4000-8000-000000000003'),
     '2026-08-20T15:45:00Z')
ON CONFLICT (id) DO UPDATE
    SET dados = EXCLUDED.dados,
        descricao = EXCLUDED.descricao,
        criado_em = EXCLUDED.criado_em;

-- --- Lembretes e mensagens programadas ---------------------------------
INSERT INTO lembrete
    (id, lead_id, atendente_id, texto, data_hora, origem_automatica, status)
VALUES
    ('db000000-0000-4000-8000-000000000001', 'de000000-0000-4000-8000-000000000002',
     'd4000000-0000-4000-8000-000000000001', 'Ligar para confirmar as medidas do box.',
     '2026-08-25T13:00:00Z', FALSE, 'PENDENTE'),
    ('db000000-0000-4000-8000-000000000002', 'de000000-0000-4000-8000-000000000004',
     'd4000000-0000-4000-8000-000000000001', 'Enviar forma de pagamento do espelho.',
     '2026-08-24T14:00:00Z', FALSE, 'PENDENTE')
ON CONFLICT (id) DO UPDATE
    SET texto = EXCLUDED.texto,
        data_hora = EXCLUDED.data_hora,
        status = EXCLUDED.status;

-- d3 substitui o prefixo antigo "dp", que tambem nao era UUID valido.
INSERT INTO mensagem_programada
    (id, lead_id, atendente_id, conteudo, data_envio, status)
VALUES
    ('d3000000-0000-4000-8000-000000000001', 'de000000-0000-4000-8000-000000000003',
     'd4000000-0000-4000-8000-000000000002',
     'Bom dia! Segue a proposta da fachada conforme combinamos.',
     '2026-08-25T12:00:00Z', 'AGENDADA'),
    ('d3000000-0000-4000-8000-000000000002', 'de000000-0000-4000-8000-000000000005',
     'd4000000-0000-4000-8000-000000000003',
     'Passando para saber se esta tudo bem com a instalacao.',
     '2026-09-02T13:00:00Z', 'AGENDADA')
ON CONFLICT (id) DO UPDATE
    SET conteudo = EXCLUDED.conteudo,
        data_envio = EXCLUDED.data_envio,
        status = EXCLUDED.status;

COMMIT;
\echo 'Seed concluido: 4 atendentes, 6 leads, 4 atendimentos, 11 mensagens, 2 lembretes e 2 mensagens programadas.'
