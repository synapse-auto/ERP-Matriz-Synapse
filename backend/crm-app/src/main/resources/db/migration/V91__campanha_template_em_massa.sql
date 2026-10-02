-- E220: campanhas de template em massa (canal Meta), com limite diario, ondas e conferencia.
--
-- Nomes novos (campanha_template*) e nao a tabela `campanha` da V6: aquela e do modelo original de
-- campanhas por intervalo/filtro, nunca teve uso, e o contrato daqui e outro. As tabelas da V6 ficam
-- como estao (regra de schema completo). A tabela `mensagem` (particionada) nao e alterada.
--
-- Contadores sao INCREMENTAIS na propria campanha: a tela nunca faz COUNT(*) sobre mensagem nem sobre
-- os destinatarios (licao do incidente de CPU do Postgres, docs/42). Sao FUNIL ACUMULADO: uma
-- mensagem lida conta em enviados, entregues e lidos.

-- 1) Tipo CAMPANHA na politica proativa da E219 (reserva e origem).
ALTER TABLE mensagem_origem_automacao DROP CONSTRAINT ck_mensagem_origem_automacao_tipo;
ALTER TABLE mensagem_origem_automacao ADD CONSTRAINT ck_mensagem_origem_automacao_tipo CHECK (tipo IN (
    'RESPOSTA_IA', 'FOLLOW_UP', 'FIDELIZACAO', 'FESTIVA', 'ANIVERSARIO', 'AVALIACAO', 'LEMBRETE', 'OUTRO',
    'PROGRAMADA', 'NAO_INFORMADA', 'CAMPANHA'));

ALTER TABLE envio_proativo_reserva DROP CONSTRAINT ck_envio_proativo_reserva_tipo;
ALTER TABLE envio_proativo_reserva ADD CONSTRAINT ck_envio_proativo_reserva_tipo CHECK (tipo IN (
    'FOLLOW_UP', 'FIDELIZACAO', 'FESTIVA', 'ANIVERSARIO', 'AVALIACAO', 'LEMBRETE', 'OUTRO', 'CAMPANHA'));

-- 2) A campanha.
CREATE TABLE campanha_template (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome                 VARCHAR(150) NOT NULL,
    -- Snapshot do template na hora de criar: renomear/editar no provedor nao muda uma campanha em curso.
    template_id          VARCHAR(100),
    template_nome        VARCHAR(512) NOT NULL,
    template_idioma      VARCHAR(20) NOT NULL,
    template_categoria   VARCHAR(20) NOT NULL,
    template_corpo       TEXT NOT NULL,
    template_parametros  INT NOT NULL DEFAULT 0 CHECK (template_parametros BETWEEN 0 AND 20),
    -- [{"posicao":1,"campo":"PRIMEIRO_NOME","reserva":"cliente"}]
    mapeamento_variaveis JSONB NOT NULL DEFAULT '[]'::jsonb,
    -- {"tagIds":[],"etapaId":null,"atendenteId":null,"cadastroDesde":null,"cadastroAte":null,
    --  "nuncaConversou":false,"busca":null}
    filtro_publico       JSONB NOT NULL DEFAULT '{}'::jsonb,
    status               VARCHAR(30) NOT NULL DEFAULT 'RASCUNHO',
    -- Interruptor por campanha: para o envio no proximo ciclo, sem mudar o status.
    desligada            BOOLEAN NOT NULL DEFAULT FALSE,
    limite_diario        INT NOT NULL CHECK (limite_diario > 0),
    janela_inicio        TIME NOT NULL DEFAULT '09:00',
    janela_fim           TIME NOT NULL DEFAULT '18:00',
    -- Bit 0 = segunda ... bit 6 = domingo.
    dias_da_semana       SMALLINT NOT NULL DEFAULT 31 CHECK (dias_da_semana BETWEEN 1 AND 127),
    ritmo_por_minuto     INT NOT NULL DEFAULT 20 CHECK (ritmo_por_minuto BETWEEN 1 AND 600),
    rampa_incremento     INT CHECK (rampa_incremento IS NULL OR rampa_incremento > 0),
    rampa_teto           INT CHECK (rampa_teto IS NULL OR rampa_teto > 0),
    agendada_para        TIMESTAMPTZ,
    -- Funil acumulado.
    qtd_total            INT NOT NULL DEFAULT 0,
    qtd_pendentes        INT NOT NULL DEFAULT 0,
    qtd_enfileirados     INT NOT NULL DEFAULT 0,
    qtd_enviados         INT NOT NULL DEFAULT 0,
    qtd_entregues        INT NOT NULL DEFAULT 0,
    qtd_lidos            INT NOT NULL DEFAULT 0,
    qtd_respondidos      INT NOT NULL DEFAULT 0,
    qtd_falhas           INT NOT NULL DEFAULT 0,
    qtd_ignorados        INT NOT NULL DEFAULT 0,
    qtd_conferencia      INT NOT NULL DEFAULT 0,
    motivo_pausa         TEXT,
    pausada_em           TIMESTAMPTZ,
    iniciada_em          TIMESTAMPTZ,
    concluida_em         TIMESTAMPTZ,
    -- Trava de worker: so um ciclo por campanha. Expira sozinha se o processo morrer.
    lease_ate            TIMESTAMPTZ,
    ultimo_ciclo_em      TIMESTAMPTZ,
    criada_por           UUID REFERENCES usuario (id),
    criada_em            TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizada_em        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_campanha_template_status CHECK (status IN (
        'RASCUNHO', 'AGENDADA', 'EM_ANDAMENTO', 'PAUSADA', 'CONCLUIDA', 'CANCELADA',
        'PAUSADA_AUTOMATICAMENTE')),
    CONSTRAINT ck_campanha_template_rampa CHECK (
        (rampa_incremento IS NULL) = (rampa_teto IS NULL)),
    CONSTRAINT ck_campanha_template_janela CHECK (janela_inicio < janela_fim)
);

COMMENT ON TABLE campanha_template IS
    'Campanha de template em massa (E220). Contadores qtd_* sao incrementais e acumulados; nao recontar.';

-- O worker so olha o que esta ativo; a lista da tela, por data.
CREATE INDEX idx_campanha_template_ativas ON campanha_template (status)
    WHERE status IN ('AGENDADA', 'EM_ANDAMENTO');
CREATE INDEX idx_campanha_template_criada ON campanha_template (criada_em DESC);

-- 3) Destinatarios: um por lead por campanha.
CREATE TABLE campanha_template_destinatario (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campanha_id         UUID NOT NULL REFERENCES campanha_template (id) ON DELETE CASCADE,
    lead_id             UUID NOT NULL REFERENCES lead (id) ON DELETE CASCADE,
    telefone            VARCHAR(30),
    status              VARCHAR(15) NOT NULL DEFAULT 'PENDENTE',
    motivo              VARCHAR(60),
    erro_codigo         INT,
    mensagem_id         UUID,
    mensagem_enviada_em TIMESTAMPTZ,
    atendimento_id      UUID,
    enfileirado_em      TIMESTAMPTZ,
    enviado_em          TIMESTAMPTZ,
    entregue_em         TIMESTAMPTZ,
    lido_em             TIMESTAMPTZ,
    respondeu_em        TIMESTAMPTZ,
    conferencia_em      TIMESTAMPTZ,
    atualizado_em       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_campanha_template_destinatario UNIQUE (campanha_id, lead_id),
    CONSTRAINT ck_campanha_template_destinatario_status CHECK (status IN (
        'PENDENTE', 'ENFILEIRADO', 'ENVIADO', 'ENTREGUE', 'LIDO', 'FALHA', 'IGNORADO'))
);

COMMENT ON TABLE campanha_template_destinatario IS
    'RESERVADO nao existe como estado persistido: reserva E219, mensagem, outbox e esta linha mudam na MESMA transacao.';

-- Pegada do worker: proximos pendentes da campanha, em ordem estavel.
CREATE INDEX idx_campanha_destinatario_pendente
    ON campanha_template_destinatario (campanha_id, id) WHERE status = 'PENDENTE';
-- Tela: filtro por status/motivo.
CREATE INDEX idx_campanha_destinatario_status
    ON campanha_template_destinatario (campanha_id, status);
-- Atualizacao por evento de entrega.
CREATE UNIQUE INDEX uq_campanha_destinatario_mensagem
    ON campanha_template_destinatario (mensagem_id) WHERE mensagem_id IS NOT NULL;
-- "Respondeu" e opt-out: ultimo disparo para o lead.
CREATE INDEX idx_campanha_destinatario_lead
    ON campanha_template_destinatario (lead_id, enviado_em DESC) WHERE enviado_em IS NOT NULL;
-- Conferencia manual: so as poucas linhas sinalizadas.
CREATE INDEX idx_campanha_destinatario_conferencia
    ON campanha_template_destinatario (campanha_id) WHERE conferencia_em IS NOT NULL;
-- Reconciliacao e conferencia: so os ENFILEIRADO parados, em qualquer campanha.
CREATE INDEX idx_campanha_destinatario_enfileirado
    ON campanha_template_destinatario (enfileirado_em) WHERE status = 'ENFILEIRADO';
-- Pausa automatica olha os ultimos desfechos da campanha.
CREATE INDEX idx_campanha_destinatario_desfecho
    ON campanha_template_destinatario (campanha_id, enfileirado_em DESC) WHERE enfileirado_em IS NOT NULL;

-- 4) Contadores diarios (limite por dia no fuso da instancia, somando todas as campanhas).
CREATE TABLE campanha_template_dia (
    campanha_id  UUID NOT NULL REFERENCES campanha_template (id) ON DELETE CASCADE,
    dia          DATE NOT NULL,
    enfileiradas INT NOT NULL DEFAULT 0,
    PRIMARY KEY (campanha_id, dia)
);

CREATE TABLE campanha_envio_dia (
    dia          DATE PRIMARY KEY,
    enfileiradas INT NOT NULL DEFAULT 0
);

-- 5) Opt-out: um lead que pediu para parar nao recebe NENHUMA campanha.
CREATE TABLE contato_optout (
    lead_id        UUID PRIMARY KEY REFERENCES lead (id) ON DELETE CASCADE,
    desde          TIMESTAMPTZ NOT NULL DEFAULT now(),
    origem         VARCHAR(30) NOT NULL DEFAULT 'MANUAL',
    motivo         TEXT,
    registrado_por UUID REFERENCES usuario (id),
    CONSTRAINT ck_contato_optout_origem CHECK (origem IN ('MANUAL', 'RESPOSTA_DO_CLIENTE', 'IMPORTACAO'))
);

-- 6) RLS: campanha nao e de nenhum atendente. Somente quem enxerga a base inteira (e o servico).
ALTER TABLE campanha_template ENABLE ROW LEVEL SECURITY;
ALTER TABLE campanha_template FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_campanha_template ON campanha_template
    FOR ALL USING (app_enxerga_todos_os_leads()) WITH CHECK (app_enxerga_todos_os_leads());

ALTER TABLE campanha_template_destinatario ENABLE ROW LEVEL SECURITY;
ALTER TABLE campanha_template_destinatario FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_campanha_template_destinatario ON campanha_template_destinatario
    FOR ALL USING (app_enxerga_todos_os_leads()) WITH CHECK (app_enxerga_todos_os_leads());

ALTER TABLE campanha_template_dia ENABLE ROW LEVEL SECURITY;
ALTER TABLE campanha_template_dia FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_campanha_template_dia ON campanha_template_dia
    FOR ALL USING (app_enxerga_todos_os_leads()) WITH CHECK (app_enxerga_todos_os_leads());

ALTER TABLE campanha_envio_dia ENABLE ROW LEVEL SECURITY;
ALTER TABLE campanha_envio_dia FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_campanha_envio_dia ON campanha_envio_dia
    FOR ALL USING (app_enxerga_todos_os_leads()) WITH CHECK (app_enxerga_todos_os_leads());

ALTER TABLE contato_optout ENABLE ROW LEVEL SECURITY;
ALTER TABLE contato_optout FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_contato_optout ON contato_optout
    FOR ALL USING (app_enxerga_todos_os_leads()) WITH CHECK (app_enxerga_todos_os_leads());

-- 7) Parametros (configuracao_automacao, padrao V72). Valores conservadores; quem decide e o responsavel.
INSERT INTO configuracao_automacao
    (chave, valor, unidade, tipo, valor_min, valor_max, descricao)
VALUES
    ('automacao_proativa.campanha.habilitada', 'true', NULL, 'BOOLEAN', NULL, NULL,
     'Permite reservas proativas do tipo CAMPANHA (E220). A campanha tambem tem o proprio interruptor.'),
    ('campanhas.envio_habilitado', 'true', NULL, 'BOOLEAN', NULL, NULL,
     'Interruptor global do envio de campanhas. Desligado, nenhum ciclo envia e nada e perdido; ao religar o envio continua.'),
    ('campanhas.teto_diario_instancia', '200', 'mensagens', 'INT', 1, 100000,
     'Maximo de mensagens de campanha por dia (fuso da instancia), somando todas as campanhas. Nenhuma campanha ultrapassa este valor.'),
    ('campanhas.limite_diario_padrao', '100', 'mensagens', 'INT', 1, 100000,
     'Limite diario sugerido ao criar uma campanha. Sempre limitado pelo teto da instancia.'),
    ('campanhas.limite_meta_informado', '0', 'contatos', 'INT', 0, 10000000,
     'Limite de contatos unicos por 24h da Meta, informado pelo administrador (a API da Meta expoe whatsapp_business_manager_messaging_limit, ainda nao lida pelo CRM). 0 = nao informado.'),
    ('campanhas.pausa.limiar_falha_pct', '20', '%', 'INT', 1, 100,
     'Pausa automatica: percentual de falhas nos ultimos envios que pausa a campanha.'),
    ('campanhas.pausa.janela_envios', '50', 'envios', 'INT', 10, 500,
     'Pausa automatica: quantos desfechos recentes entram na taxa de falha.'),
    ('campanhas.pausa.minimo_amostra', '20', 'envios', 'INT', 5, 500,
     'Pausa automatica: desfechos minimos antes de a taxa de falha valer.'),
    ('campanhas.conferencia_apos_minutos', '30', 'minutos', 'INT', 5, 1440,
     'Destinatario ENFILEIRADO ha mais que isto sem confirmacao vai para a conferencia manual. Nunca e reenviado sozinho.'),
    ('campanhas.respondeu_janela_dias', '7', 'dias', 'INT', 1, 60,
     'Uma resposta do cliente conta como resposta a campanha ate este numero de dias apos o envio.')
ON CONFLICT (chave) DO NOTHING;
