-- E219: origem de toda mensagem automatica e reserva com politica de frequencia para as proativas.
--
-- 1) mensagem_origem_automacao
--    Tabela lateral, uma linha por mensagem automatica gravada. NAO e coluna nova em `mensagem`:
--    `mensagem` e particionada por enviado_em e tem FORCE ROW LEVEL SECURITY (V5/V12); um ALTER nela
--    pega lock em todas as particoes (licao da V73) e nada aqui precisa disso. Sem FK para mensagem,
--    pelo mesmo motivo de mensagem_automacao_idempotencia (V29): a PK de mensagem e (id, enviado_em).
--    Mensagens ja gravadas nao ganham linha: nada antigo e tocado.
--
-- 2) envio_proativo_reserva
--    Reserva ANTES de um envio proativo (follow-up, fidelizacao, festiva, aniversario, avaliacao...).
--    A PK na chave deduplica reexecucoes; o indice unico por (lead, tipo, regra, ocorrencia) impede a
--    mesma ocorrencia com outra chave. Decisoes "nao envie" nao viram linha: so reservas concedidas.
--
-- 3) Parametros da politica em configuracao_automacao (editaveis em tempo de execucao, padrao V72).
--    Os padroes NAO mudam o comportamento atual: tudo ligado, cooldown e teto desligados (0). Os
--    valores reais ficam para o responsavel decidir antes do deploy.

CREATE TABLE mensagem_origem_automacao (
    mensagem_id     UUID PRIMARY KEY,
    atendimento_id  UUID NOT NULL,
    lead_id         UUID NOT NULL,
    enviado_em      TIMESTAMPTZ NOT NULL,
    tipo            VARCHAR(20) NOT NULL,
    regra_id        VARCHAR(100),
    execucao_id     VARCHAR(200),
    criado_em       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_mensagem_origem_automacao_tipo CHECK (tipo IN (
        'RESPOSTA_IA', 'FOLLOW_UP', 'FIDELIZACAO', 'FESTIVA', 'ANIVERSARIO', 'AVALIACAO', 'LEMBRETE', 'OUTRO',
        'PROGRAMADA', 'NAO_INFORMADA'))
);

COMMENT ON TABLE mensagem_origem_automacao IS
    'Origem (tipo, regra, execucao do n8n) de cada mensagem automatica gravada a partir da V85.';

-- Visao por origem/dia e auditoria de um periodo.
CREATE INDEX idx_mensagem_origem_automacao_enviado ON mensagem_origem_automacao (enviado_em);
-- Cooldown por (lead, tipo) e teto diario por lead.
CREATE INDEX idx_mensagem_origem_automacao_lead ON mensagem_origem_automacao (lead_id, enviado_em);

GRANT SELECT, INSERT ON mensagem_origem_automacao TO synapse_app;

CREATE TABLE envio_proativo_reserva (
    chave            VARCHAR(200) PRIMARY KEY,
    lead_id          UUID NOT NULL REFERENCES lead (id) ON DELETE CASCADE,
    tipo             VARCHAR(20) NOT NULL,
    regra_id         VARCHAR(100),
    ocorrencia       VARCHAR(200) NOT NULL,
    execucao_id      VARCHAR(200),
    requisicao_hash  CHAR(64) NOT NULL,
    estado           VARCHAR(10) NOT NULL DEFAULT 'RESERVADO',
    mensagem_id      UUID,
    wamid_saida      TEXT,
    reservado_em     TIMESTAMPTZ NOT NULL DEFAULT now(),
    enviado_em       TIMESTAMPTZ,
    CONSTRAINT ck_envio_proativo_reserva_tipo CHECK (tipo IN (
        'FOLLOW_UP', 'FIDELIZACAO', 'FESTIVA', 'ANIVERSARIO', 'AVALIACAO', 'LEMBRETE', 'OUTRO')),
    CONSTRAINT ck_envio_proativo_reserva_estado CHECK (estado IN ('RESERVADO', 'ENVIADO')),
    CONSTRAINT ck_envio_proativo_reserva_enviado
        CHECK ((estado = 'ENVIADO') = (mensagem_id IS NOT NULL AND enviado_em IS NOT NULL))
);

COMMENT ON TABLE envio_proativo_reserva IS
    'Reserva de um envio proativo da Automacao, concedida pela politica de frequencia (E219).';

-- Uma ocorrencia (ex.: follow-up de 3 dias do lead X, festiva de Natal/2026) so e reservada uma vez,
-- qualquer que seja a chave. regra_id e opcional: COALESCE para o NULL nao escapar da unicidade.
CREATE UNIQUE INDEX uq_envio_proativo_reserva_ocorrencia
    ON envio_proativo_reserva (lead_id, tipo, COALESCE(regra_id, ''), ocorrencia);

-- Cooldown e teto contam as reservas ainda sem resultado (o envio pode ter saido).
CREATE INDEX idx_envio_proativo_reserva_lead ON envio_proativo_reserva (lead_id, reservado_em);

-- Conferencia: so as reservas sem resultado importam, e devem ser poucas.
CREATE INDEX idx_envio_proativo_reserva_pendente
    ON envio_proativo_reserva (reservado_em)
    WHERE estado = 'RESERVADO';

GRANT SELECT, INSERT, UPDATE ON envio_proativo_reserva TO synapse_app;

INSERT INTO configuracao_automacao
    (chave, valor, unidade, tipo, valor_min, valor_max, descricao)
VALUES
    ('automacao_proativa.habilitada', 'true', NULL, 'BOOLEAN', NULL, NULL,
     'Chave geral das mensagens proativas da Automacao (follow-up, fidelizacao, festivas, aniversario, avaliacao, lembrete). Desligada, toda reserva proativa responde "nao envie".'),
    ('automacao_proativa.follow_up.habilitada', 'true', NULL, 'BOOLEAN', NULL, NULL,
     'Permite reservas proativas do tipo FOLLOW_UP.'),
    ('automacao_proativa.fidelizacao.habilitada', 'true', NULL, 'BOOLEAN', NULL, NULL,
     'Permite reservas proativas do tipo FIDELIZACAO.'),
    ('automacao_proativa.festiva.habilitada', 'true', NULL, 'BOOLEAN', NULL, NULL,
     'Permite reservas proativas do tipo FESTIVA (datas comemorativas).'),
    ('automacao_proativa.aniversario.habilitada', 'true', NULL, 'BOOLEAN', NULL, NULL,
     'Permite reservas proativas do tipo ANIVERSARIO.'),
    ('automacao_proativa.avaliacao.habilitada', 'true', NULL, 'BOOLEAN', NULL, NULL,
     'Permite reservas proativas do tipo AVALIACAO (pesquisa pos-atendimento).'),
    ('automacao_proativa.lembrete.habilitada', 'true', NULL, 'BOOLEAN', NULL, NULL,
     'Permite reservas proativas do tipo LEMBRETE (lembrete enviado ao lead).'),
    ('automacao_proativa.outro.habilitada', 'true', NULL, 'BOOLEAN', NULL, NULL,
     'Permite reservas proativas do tipo OUTRO.'),
    ('automacao_proativa.cooldown_horas', '0', 'horas', 'INT', 0, 720,
     'Intervalo minimo entre duas mensagens proativas do mesmo tipo para o mesmo lead. 0 desliga.'),
    ('automacao_proativa.teto_diario_por_lead', '0', 'mensagens', 'INT', 0, 50,
     'Maximo de mensagens proativas por lead por dia (fuso da instancia), somando todos os tipos. 0 desliga.')
ON CONFLICT (chave) DO NOTHING;
