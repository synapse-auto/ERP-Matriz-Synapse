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
-- Limpa o GANHO antes da reconciliacao para permitir trocar qual etapa representa
-- venda sem conflito transitorio com o indice unico parcial.
UPDATE etapa_atendimento SET resultado = 'EM_ANDAMENTO' WHERE resultado = 'GANHO';

INSERT INTO etapa_atendimento (id, nome, ordem, cor_visual, resultado) VALUES
    ('e1000000-0000-4000-8000-000000000001', 'Novo contato', 1, '#64748B', 'EM_ANDAMENTO'),
    ('e1000000-0000-4000-8000-000000000002', 'Qualificacao', 2, '#0EA5E9', 'EM_ANDAMENTO'),
    ('e1000000-0000-4000-8000-000000000003', 'Proposta',     3, '#6366F1', 'EM_ANDAMENTO'),
    ('e1000000-0000-4000-8000-000000000004', 'Negociacao',   4, '#F59E0B', 'EM_ANDAMENTO'),
    ('e1000000-0000-4000-8000-000000000005', 'Fechamento',   5, '#22C55E', 'GANHO'),
    ('e1000000-0000-4000-8000-000000000006', 'Pos-venda',    6, '#14B8A6', 'EM_ANDAMENTO'),
    ('e1000000-0000-4000-8000-000000000007', 'Perdido',      7, '#EF4444', 'PERDIDO')
ON CONFLICT (id) DO UPDATE
    SET nome = EXCLUDED.nome,
        ordem = EXCLUDED.ordem,
        cor_visual = EXCLUDED.cor_visual,
        resultado = EXCLUDED.resultado;

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
     '5561900000000', '999999999999999', 'secret://dev/whatsapp/token', TRUE)
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
--
-- senha_alterada_em = now(): os usuarios do seed representam contas que ja
-- passaram pelo primeiro acesso, nao o go-live real (E29). Deixar NULL aqui
-- forcaria toda a suite de integracao a passar pela troca de senha antes de
-- qualquer chamada — nenhum dos testes de integracao existentes espera isso.
-- Quem quer testar o fluxo de primeiro acesso cria seu proprio usuario ad-hoc
-- com a coluna NULL, como ja se faz para usuario inativo em AutenticacaoIT.
INSERT INTO usuario (id, nome, email, senha_hash, papel, ativo, senha_alterada_em) VALUES
    ('11000000-0000-4000-8000-000000000001', 'Administrador', 'admin@dev.local',
     '$2a$10$WcHAhzJQHWC/Kmt0YV8onO2T9SlT.DC0xj.vixOBEkLqmlB.ZCqCS', 'ADMINISTRADOR', TRUE, now()),
    ('11000000-0000-4000-8000-000000000002', 'Gestora', 'gestor@dev.local',
     '$2a$10$5vISVeL7I/o7K8rKLvXFDOko5iYacVlYlvxIJqTywAoLzf2eP6dPK', 'GESTOR', TRUE, now()),
    ('11000000-0000-4000-8000-000000000003', 'Subgestora', 'subgestor@dev.local',
     '$2a$10$SJJMQ4SF5/1FJfkg9AwJiePRz3LR88QIx/k8W7VIaJBkYI7MskOhe', 'SUBGESTOR', TRUE, now()),
    ('11000000-0000-4000-8000-000000000004', 'Ana Atendente', 'ana@dev.local',
     '$2a$10$3RQQjf4jsEx11gmaTnUkkeky8yurpHKdl5UPkuWlVe7tsphWGmj42', 'ATENDENTE', TRUE, now()),
    ('11000000-0000-4000-8000-000000000005', 'Bruno Atendente', 'bruno@dev.local',
     '$2a$10$3RQQjf4jsEx11gmaTnUkkeky8yurpHKdl5UPkuWlVe7tsphWGmj42', 'ATENDENTE', TRUE, now())
ON CONFLICT (id) DO UPDATE
    SET nome = EXCLUDED.nome,
        email = EXCLUDED.email,
        senha_hash = EXCLUDED.senha_hash,
        papel = EXCLUDED.papel,
        ativo = EXCLUDED.ativo,
        senha_alterada_em = EXCLUDED.senha_alterada_em;

-- V34 backfills tenants that already exist at deploy time. In a fresh
-- development database the repeatable seed runs after versioned migrations,
-- so keep the same default for the demonstration attendants as well.
INSERT INTO disponibilidade_atendente_ia (atendente_id, disponivel_para_ia)
SELECT id, TRUE
FROM usuario
WHERE ativo = TRUE
  AND papel = 'ATENDENTE'
ON CONFLICT (atendente_id) DO UPDATE
SET disponivel_para_ia = TRUE,
    atualizado_em = now();

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
    ('campanhas',        FALSE, 'Aba de campanhas e motor de envio.'),
    ('chat_interno',     TRUE, 'Chat direto de texto entre integrantes da equipe.'),
    ('fidelizacao',      TRUE, 'Regras de fidelizacao e reengajamento.'),
    ('relatorios',       FALSE, 'Aba de relatorios.'),
    ('banco_arquivos',   FALSE, 'Aba de banco de arquivos.'),
    -- E20: a Visao Geral voltou ao escopo por decisao do cliente e agora possui
    -- read model, autorizacao e tela reais. As demais abas continuam desabilitadas.
    ('dashboard',        TRUE, 'Dashboard de indicadores.'),
    -- E15b §1: schema existe (regra_follow_up, regra_fidelizacao, mensagem_festiva,
    -- configuracao_resumo_ia) mas nenhum caso de uso de escrita foi construido ainda —
    -- nao e sub-aba visivel no menu, e sim secao dentro de /automacao que so aparece
    -- quando essa camada existir.
    ('automacao_regras', FALSE, 'CRUD de regras de follow-up/fidelizacao/festivas e telemetria dentro de Automacao.'),
    -- E15b §2: horario_trabalho e rotina_disponibilidade so tem migration, zero codigo
    -- de aplicacao. Disponibilidade do atendente e manual (presenca) na 1a entrega.
    ('horarios',         FALSE, 'Aba de horarios de atendimento por dia da semana.')
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
    ('ia.preenchimento_automatico', 'false', NULL, 'BOOLEAN', NULL, NULL,
     'Permite que a automacao preencha dados do cliente a partir da conversa.'),
                 ('automacao.saudacao', 'Ola, aqui e o assistente da Estrutural Vidros.', NULL, 'TEXT',
                  NULL, NULL, 'Primeira mensagem enviada pela IA.'),
                 ('automacao.comando_reset', '#reset', NULL, 'TEXT',
                  NULL, NULL, 'Mensagem exata que devolve o atendimento humano para a Automacao.'),
    -- Teto da propria Meta Cloud API como valor_max: o admin pode apertar,
    -- nunca alargar alem do que o provedor aceita (E11b). Confirmar estes
    -- numeros contra a documentacao atual da Meta antes de producao.
    ('anexo.tamanho_maximo_imagem_mb', '5', 'MB', 'INT', 1, 5,
     'Tamanho maximo de imagem anexada no chat.'),
    ('anexo.tamanho_maximo_audio_mb', '16', 'MB', 'INT', 1, 16,
     'Tamanho maximo de audio anexado no chat.'),
    ('gravacao_audio.duracao_maxima_segundos', '120', 'segundos', 'INT', 10, 600,
     'Duracao maxima de uma gravacao de audio feita no composer.'),
    ('anexo.tamanho_maximo_documento_mb', '100', 'MB', 'INT', 1, 100,
     'Tamanho maximo de documento anexado no chat.')
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
