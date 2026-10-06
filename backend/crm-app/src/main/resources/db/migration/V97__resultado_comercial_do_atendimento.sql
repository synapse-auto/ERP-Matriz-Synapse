-- Estado comercial pertence ao ciclo do atendimento, nao ao lead nem ao nome de uma etapa.
ALTER TABLE atendimento
    ADD COLUMN em_negociacao BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN resultado_venda VARCHAR(12),
    ADD COLUMN valor_venda NUMERIC(14, 2),
    ADD COLUMN venda_registrada_por_id UUID REFERENCES usuario(id),
    ADD COLUMN venda_registrada_em TIMESTAMPTZ,
    ADD COLUMN origem_resultado_venda VARCHAR(20);

ALTER TABLE atendimento
    ADD CONSTRAINT ck_atendimento_resultado_venda
        CHECK (resultado_venda IS NULL OR resultado_venda IN ('VENDEU', 'NAO_VENDEU')),
    ADD CONSTRAINT ck_atendimento_origem_resultado_venda
        CHECK (origem_resultado_venda IS NULL OR origem_resultado_venda IN ('MANUAL', 'FINALIZACAO')),
    ADD CONSTRAINT ck_atendimento_estado_resultado_venda
        CHECK ((resultado_venda IS NULL
                AND valor_venda IS NULL
                AND venda_registrada_por_id IS NULL
                AND venda_registrada_em IS NULL
                AND origem_resultado_venda IS NULL)
            OR (resultado_venda IS NOT NULL
                AND venda_registrada_por_id IS NOT NULL
                AND venda_registrada_em IS NOT NULL
                AND origem_resultado_venda IS NOT NULL));

COMMENT ON COLUMN atendimento.em_negociacao IS
    'Classificacao explicita recebida da Automacao para este ciclo de atendimento; nunca inferida pelo CRM.';
COMMENT ON COLUMN atendimento.resultado_venda IS
    'Resultado comercial atual do atendimento. Nulo significa que ainda nao foi registrado.';
COMMENT ON COLUMN atendimento.valor_venda IS
    'Valor opcional da venda. Permanece nulo quando nao existe valor informado.';
