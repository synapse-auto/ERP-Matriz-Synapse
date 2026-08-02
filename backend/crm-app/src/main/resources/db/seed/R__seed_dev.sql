-- =========================================================
-- Seed de DESENVOLVIMENTO.
--
-- Esta pasta (classpath:db/seed) so entra em spring.flyway.locations no
-- perfil "dev" — ver application.yml. Sem o perfil, o Flyway nem enxerga
-- este arquivo, entao ele nao tem como rodar em producao por engano.
--
-- Migration repetivel: reexecuta sempre que o conteudo muda. Todos os
-- INSERTs sao idempotentes (ids fixos + ON CONFLICT), entao rodar de novo
-- reconcilia em vez de duplicar.
--
-- As senhas sao BCrypt de valores obvios e publicos, validos so aqui.
-- =========================================================

-- --- Etapas do funil -------------------------------------------------------
INSERT INTO etapa_atendimento (id, nome, ordem, cor_visual) VALUES
    ('e1000000-0000-4000-8000-000000000001', 'Novo contato', 1, '#64748B'),
    ('e1000000-0000-4000-8000-000000000002', 'Qualificacao', 2, '#0EA5E9'),
    ('e1000000-0000-4000-8000-000000000003', 'Proposta',     3, '#6366F1'),
    ('e1000000-0000-4000-8000-000000000004', 'Negociacao',   4, '#F59E0B'),
    ('e1000000-0000-4000-8000-000000000005', 'Fechamento',   5, '#22C55E'),
    ('e1000000-0000-4000-8000-000000000006', 'Pos-venda',    6, '#14B8A6')
ON CONFLICT (id) DO UPDATE
    SET nome = EXCLUDED.nome,
        ordem = EXCLUDED.ordem,
        cor_visual = EXCLUDED.cor_visual;

-- --- Canal e credencial ----------------------------------------------------
INSERT INTO canal (id, nome, tipo, ativo) VALUES
    ('ca000000-0000-4000-8000-000000000001', 'WhatsApp Principal', 'WHATSAPP', TRUE)
ON CONFLICT (id) DO UPDATE
    SET nome = EXCLUDED.nome, tipo = EXCLUDED.tipo, ativo = EXCLUDED.ativo;

-- token_ref e uma REFERENCIA ao secret manager. Nunca um token de verdade,
-- nem em ambiente de desenvolvimento.
INSERT INTO canal_credencial
    (id, canal_id, numero, identificador_externo, token_ref, ativo) VALUES
    ('cc000000-0000-4000-8000-000000000001',
     'ca000000-0000-4000-8000-000000000001',
     '+5561900000000', 'phone-number-id-dev', 'secret://dev/whatsapp/token', TRUE)
ON CONFLICT (id) DO UPDATE
    SET numero = EXCLUDED.numero,
        identificador_externo = EXCLUDED.identificador_externo,
        token_ref = EXCLUDED.token_ref,
        ativo = EXCLUDED.ativo;

-- --- Usuarios --------------------------------------------------------------
-- admin@dev.local     / admin123
-- gestor@dev.local    / gestor123
-- subgestor@dev.local / subgestor123
-- ana@dev.local       / atendente123
-- bruno@dev.local     / atendente123
INSERT INTO usuario (id, nome, email, senha_hash, papel, ativo) VALUES
    ('11000000-0000-4000-8000-000000000001', 'Administrador', 'admin@dev.local',
     '$2a$10$WcHAhzJQHWC/Kmt0YV8onO2T9SlT.DC0xj.vixOBEkLqmlB.ZCqCS', 'ADMINISTRADOR', TRUE),
    ('11000000-0000-4000-8000-000000000002', 'Gestora', 'gestor@dev.local',
     '$2a$10$5vISVeL7I/o7K8rKLvXFDOko5iYacVlYlvxIJqTywAoLzf2eP6dPK', 'GESTOR', TRUE),
    ('11000000-0000-4000-8000-000000000003', 'Subgestora', 'subgestor@dev.local',
     '$2a$10$SJJMQ4SF5/1FJfkg9AwJiePRz3LR88QIx/k8W7VIaJBkYI7MskOhe', 'SUBGESTOR', TRUE),
    ('11000000-0000-4000-8000-000000000004', 'Ana Atendente', 'ana@dev.local',
     '$2a$10$3RQQjf4jsEx11gmaTnUkkeky8yurpHKdl5UPkuWlVe7tsphWGmj42', 'ATENDENTE', TRUE),
    ('11000000-0000-4000-8000-000000000005', 'Bruno Atendente', 'bruno@dev.local',
     '$2a$10$3RQQjf4jsEx11gmaTnUkkeky8yurpHKdl5UPkuWlVe7tsphWGmj42', 'ATENDENTE', TRUE)
ON CONFLICT (id) DO UPDATE
    SET nome = EXCLUDED.nome,
        email = EXCLUDED.email,
        senha_hash = EXCLUDED.senha_hash,
        papel = EXCLUDED.papel,
        ativo = EXCLUDED.ativo;

-- --- Tags ------------------------------------------------------------------
INSERT INTO tag (id, nome, cor, icone) VALUES
    ('7a000000-0000-4000-8000-000000000001', 'Orcamento',   '#0EA5E9', 'calculator'),
    ('7a000000-0000-4000-8000-000000000002', 'Urgente',     '#EF4444', 'alert-triangle'),
    ('7a000000-0000-4000-8000-000000000003', 'Obra grande', '#8B5CF6', 'building-2'),
    ('7a000000-0000-4000-8000-000000000004', 'Recorrente',  '#22C55E', 'repeat'),
    ('7a000000-0000-4000-8000-000000000005', 'Pos-venda',   '#14B8A6', 'life-buoy')
ON CONFLICT (id) DO UPDATE
    SET nome = EXCLUDED.nome, cor = EXCLUDED.cor, icone = EXCLUDED.icone;

-- --- Feature flags ---------------------------------------------------------
-- campanhas/relatorios/banco_arquivos: FALSE de proposito (docs/09, TOKENS.md §5) — fora
-- da primeira entrega. A aba nao e construida; e o corte por flag, nao por remocao, que
-- deixa a Base PAI ligar essas abas para um filho futuro sem deploy de codigo novo.
INSERT INTO feature_flag (chave, habilitado, descricao) VALUES
    ('campanhas',      FALSE, 'Aba de campanhas e motor de envio.'),
    ('chat_interno',   TRUE, 'Chat entre atendentes e gestores.'),
    ('fidelizacao',    TRUE, 'Regras de fidelizacao e reengajamento.'),
    ('relatorios',     FALSE, 'Aba de relatorios.'),
    ('banco_arquivos', FALSE, 'Aba de banco de arquivos.'),
    ('dashboard',      TRUE, 'Dashboard de indicadores.')
ON CONFLICT (chave) DO UPDATE
    SET habilitado = EXCLUDED.habilitado, descricao = EXCLUDED.descricao;

-- --- Parametros da automacao ----------------------------------------------
-- Faixa min/max preenchida: e o que impede o painel de aceitar um valor que
-- derrubaria a operacao sem ninguem perceber.
INSERT INTO configuracao_automacao
    (chave, valor, unidade, tipo, valor_min, valor_max, descricao) VALUES
    ('followup.primeiro.minutos', '15', 'minutos', 'INT', 1, 1440,
     'Tempo sem resposta do lead ate o primeiro follow-up automatico.'),
    ('followup.segundo.minutos', '120', 'minutos', 'INT', 1, 10080,
     'Tempo ate o segundo follow-up automatico.'),
    ('followup.tentativas.maximo', '3', 'tentativas', 'INT', 1, 10,
     'Quantidade maxima de follow-ups por lead antes de desistir.'),
    ('fidelizacao.dias_sem_contato', '30', 'dias', 'INT', 1, 365,
     'Dias sem contato ate a mensagem de reengajamento.'),
    ('ia.transferir_apos_mensagens', '8', 'mensagens', 'INT', 1, 50,
     'Mensagens trocadas com a IA antes de sugerir transferencia para humano.'),
    ('ia.resumo.a_cada_mensagens', '20', 'mensagens', 'INT', 5, 200,
     'Intervalo de mensagens para gerar o resumo por IA.'),
    ('atendimento.finalizar_apos_horas', '48', 'horas', 'INT', 1, 720,
     'Horas de inatividade ate finalizar o atendimento automaticamente.'),
    ('campanha.intervalo_padrao_dias', '3', 'dias', 'INT', 1, 7,
     'Intervalo padrao entre mensagens de uma campanha.'),
    ('automacao.habilitada', 'true', NULL, 'BOOLEAN', NULL, NULL,
     'Chave geral da automacao. Desligar pausa toda a operacao automatica.'),
    ('automacao.saudacao', 'Ola, aqui e o assistente da Estrutural Vidros.', NULL, 'TEXT',
     NULL, NULL, 'Primeira mensagem enviada pela IA.')
ON CONFLICT (chave) DO UPDATE
    SET valor = EXCLUDED.valor,
        unidade = EXCLUDED.unidade,
        tipo = EXCLUDED.tipo,
        valor_min = EXCLUDED.valor_min,
        valor_max = EXCLUDED.valor_max,
        descricao = EXCLUDED.descricao;

-- --- Singletons de configuracao -------------------------------------------
INSERT INTO configuracao_resumo_ia (id, ativo, gatilho, quantidade_mensagens)
VALUES (1, TRUE, 'AMBOS', 20)
ON CONFLICT (id) DO UPDATE
    SET ativo = EXCLUDED.ativo,
        gatilho = EXCLUDED.gatilho,
        quantidade_mensagens = EXCLUDED.quantidade_mensagens;

INSERT INTO status_automacao_telemetria (id) VALUES (1)
ON CONFLICT (id) DO NOTHING;
