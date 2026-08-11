CREATE TYPE resultado_etapa AS ENUM ('EM_ANDAMENTO', 'GANHO', 'PERDIDO');

ALTER TABLE etapa_atendimento
    ADD COLUMN resultado resultado_etapa NOT NULL DEFAULT 'EM_ANDAMENTO';

-- Uma segunda etapa GANHO tornaria vendas e comissoes ambiguas. O indice
-- parcial garante a regra mesmo com duas instancias concorrentes da aplicacao.
CREATE UNIQUE INDEX idx_etapa_unica_ganha
    ON etapa_atendimento (resultado)
    WHERE resultado = 'GANHO';

COMMENT ON COLUMN etapa_atendimento.resultado IS
    'Significado comercial estavel da etapa, independente do nome configurado pelo cliente.';
