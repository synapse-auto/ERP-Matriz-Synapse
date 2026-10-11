-- V97 permanece intacta. A identidade tecnica nao e uma linha ficticia em usuario.
ALTER TABLE atendimento DROP CONSTRAINT ck_atendimento_origem_resultado_venda;
ALTER TABLE atendimento DROP CONSTRAINT ck_atendimento_estado_resultado_venda;

ALTER TABLE atendimento
    ADD CONSTRAINT ck_atendimento_origem_resultado_venda
        CHECK (origem_resultado_venda IS NULL OR origem_resultado_venda IN ('MANUAL', 'FINALIZACAO', 'AUTOMACAO')),
    ADD CONSTRAINT ck_atendimento_estado_resultado_venda
        CHECK ((resultado_venda IS NULL
                AND valor_venda IS NULL
                AND venda_registrada_por_id IS NULL
                AND venda_registrada_em IS NULL
                AND origem_resultado_venda IS NULL)
            OR (resultado_venda IS NOT NULL
                AND venda_registrada_em IS NOT NULL
                AND origem_resultado_venda IS NOT NULL
                AND ((origem_resultado_venda = 'AUTOMACAO' AND venda_registrada_por_id IS NULL)
                    OR (origem_resultado_venda IN ('MANUAL', 'FINALIZACAO') AND venda_registrada_por_id IS NOT NULL))));
