-- Finalizacao em massa de atendimentos por atendentes e periodo.
--
-- Uma operacao congela, no momento do pedido e sob a visibilidade (RLS) de quem pediu, a lista de
-- atendimentos elegiveis. O worker processa essa lista em lotes curtos e cada item e uma transacao
-- propria: falha parcial nunca desfaz o que ja foi finalizado, e reexecutar o worker retoma de onde
-- parou. Nada e apagado: finalizar e a mesma transicao de estado do botao individual.

CREATE TABLE finalizacao_em_massa (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    solicitante_id      UUID NOT NULL REFERENCES usuario(id),
    chave_idempotencia  VARCHAR(80) NOT NULL,
    -- Impressao digital dos filtros normalizados: a mesma chave com outros filtros e conflito.
    impressao_filtros   VARCHAR(64) NOT NULL,
    atendente_ids       UUID[] NOT NULL,
    periodo_inicio      TIMESTAMPTZ NOT NULL,
    -- Exclusivo: o instante em que o periodo ja terminou (fim inclusivo do ultimo minuto/dia).
    periodo_fim         TIMESTAMPTZ NOT NULL,
    fuso                VARCHAR(60) NOT NULL,
    data_de             DATE NOT NULL,
    data_ate            DATE NOT NULL,
    hora_inicio         TIME,
    hora_fim            TIME,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    encontrados         INT NOT NULL,
    finalizados         INT NOT NULL DEFAULT 0,
    ignorados           INT NOT NULL DEFAULT 0,
    falhas              INT NOT NULL DEFAULT 0,
    criada_em           TIMESTAMPTZ NOT NULL DEFAULT now(),
    iniciada_em         TIMESTAMPTZ,
    concluida_em        TIMESTAMPTZ,
    lease_ate           TIMESTAMPTZ,
    CONSTRAINT ck_finalizacao_em_massa_status
        CHECK (status IN ('PENDENTE', 'EM_ANDAMENTO', 'CONCLUIDA')),
    CONSTRAINT ck_finalizacao_em_massa_periodo CHECK (periodo_fim > periodo_inicio),
    CONSTRAINT ck_finalizacao_em_massa_atendentes CHECK (cardinality(atendente_ids) >= 1),
    CONSTRAINT uq_finalizacao_em_massa_chave UNIQUE (solicitante_id, chave_idempotencia)
);

-- Duas operacoes ativas ao mesmo tempo disputariam os mesmos atendimentos e a mesma fila de
-- processamento: so existe uma ativa por instancia. A expressao constante torna o indice unico
-- sobre "todas as linhas ativas".
CREATE UNIQUE INDEX uq_finalizacao_em_massa_ativa
    ON finalizacao_em_massa ((TRUE)) WHERE status IN ('PENDENTE', 'EM_ANDAMENTO');
CREATE INDEX idx_finalizacao_em_massa_solicitante
    ON finalizacao_em_massa (solicitante_id, criada_em DESC);

CREATE TABLE finalizacao_em_massa_item (
    operacao_id     UUID NOT NULL REFERENCES finalizacao_em_massa(id) ON DELETE CASCADE,
    atendimento_id  UUID NOT NULL REFERENCES atendimento(id),
    -- Dono do atendimento quando a lista foi congelada: e quem sera avisado se for finalizado.
    atendente_id    UUID NOT NULL REFERENCES usuario(id),
    status          VARCHAR(12) NOT NULL DEFAULT 'PENDENTE',
    motivo          VARCHAR(40),
    processado_em   TIMESTAMPTZ,
    PRIMARY KEY (operacao_id, atendimento_id),
    CONSTRAINT ck_finalizacao_em_massa_item_status
        CHECK (status IN ('PENDENTE', 'FINALIZADO', 'IGNORADO', 'FALHA'))
);
-- Fila de trabalho do worker e consulta de resultado por status.
CREATE INDEX idx_finalizacao_em_massa_item_pendente
    ON finalizacao_em_massa_item (operacao_id) WHERE status = 'PENDENTE';
CREATE INDEX idx_finalizacao_em_massa_item_status
    ON finalizacao_em_massa_item (operacao_id, status);
CREATE INDEX idx_finalizacao_em_massa_item_atendimento
    ON finalizacao_em_massa_item (atendimento_id);
CREATE INDEX idx_finalizacao_em_massa_item_atendente
    ON finalizacao_em_massa_item (atendente_id);

-- Outbox do aviso: uma linha por usuario realmente afetado (ao menos um atendimento dele finalizado),
-- gravada na mesma transacao que conclui a operacao. A chave primaria e o que impede aviso duplicado
-- em retry do worker; o publicador marca ENVIADO depois de entregar.
CREATE TABLE finalizacao_em_massa_aviso (
    operacao_id   UUID NOT NULL REFERENCES finalizacao_em_massa(id) ON DELETE CASCADE,
    usuario_id    UUID NOT NULL REFERENCES usuario(id),
    finalizados   INT NOT NULL,
    estado        VARCHAR(10) NOT NULL DEFAULT 'PENDENTE',
    tentativas    INT NOT NULL DEFAULT 0,
    tentar_apos   TIMESTAMPTZ NOT NULL DEFAULT now(),
    criado_em     TIMESTAMPTZ NOT NULL DEFAULT now(),
    enviado_em    TIMESTAMPTZ,
    ultimo_erro   VARCHAR(200),
    PRIMARY KEY (operacao_id, usuario_id),
    CONSTRAINT ck_finalizacao_em_massa_aviso_estado CHECK (estado IN ('PENDENTE', 'ENVIADO', 'ESGOTADO'))
);
CREATE INDEX idx_finalizacao_em_massa_aviso_pendente
    ON finalizacao_em_massa_aviso (tentar_apos) WHERE estado = 'PENDENTE';
CREATE INDEX idx_finalizacao_em_massa_aviso_usuario ON finalizacao_em_massa_aviso (usuario_id);

-- RLS (padrao V91). A operacao e de quem a pediu e de quem enxerga tudo (gestao e servico). O item
-- herda a visibilidade da operacao. O aviso e so do publicador (servico): usuario nunca le esta tabela.
ALTER TABLE finalizacao_em_massa ENABLE ROW LEVEL SECURITY;
ALTER TABLE finalizacao_em_massa FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_finalizacao_em_massa ON finalizacao_em_massa
    FOR ALL
    USING (app_enxerga_todos_os_leads() OR solicitante_id = app_usuario_id())
    WITH CHECK (app_enxerga_todos_os_leads() OR solicitante_id = app_usuario_id());

ALTER TABLE finalizacao_em_massa_item ENABLE ROW LEVEL SECURITY;
ALTER TABLE finalizacao_em_massa_item FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_finalizacao_em_massa_item ON finalizacao_em_massa_item
    FOR ALL
    USING (app_enxerga_todos_os_leads() OR EXISTS (
        SELECT 1 FROM finalizacao_em_massa o
         WHERE o.id = operacao_id AND o.solicitante_id = app_usuario_id()))
    WITH CHECK (app_enxerga_todos_os_leads() OR EXISTS (
        SELECT 1 FROM finalizacao_em_massa o
         WHERE o.id = operacao_id AND o.solicitante_id = app_usuario_id()));

ALTER TABLE finalizacao_em_massa_aviso ENABLE ROW LEVEL SECURITY;
ALTER TABLE finalizacao_em_massa_aviso FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_finalizacao_em_massa_aviso ON finalizacao_em_massa_aviso
    FOR ALL USING (app_e_servico()) WITH CHECK (app_e_servico());

-- Parametros (configuracao_automacao, padrao V72). Limites conservadores; quem decide e o responsavel.
INSERT INTO configuracao_automacao
    (chave, valor, unidade, tipo, valor_min, valor_max, descricao)
VALUES
    ('atendimento.finalizacao_em_massa.limite_por_operacao', '5000', 'atendimentos', 'INT', 1, 100000,
     'Maximo de atendimentos que uma finalizacao em massa pode congelar. Acima disso o pedido e recusado e o filtro precisa ser reduzido.'),
    ('atendimento.finalizacao_em_massa.periodo_maximo_dias', '31', 'dias', 'INT', 1, 366,
     'Maior janela (data inicial a data final, inclusivas) aceita por uma finalizacao em massa.'),
    ('atendimento.finalizacao_em_massa.lote', '50', 'atendimentos', 'INT', 1, 500,
     'Quantos atendimentos o worker finaliza por rodada (cada um em transacao propria). Rodadas curtas mantem o chat livre.');
