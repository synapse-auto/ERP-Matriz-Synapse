-- E178: a ordem da distribuicao da IA e configuravel por instancia.
-- BOOLEAN fecha os dois estados suportados e reaproveita a validacao existente
-- do CRUD de configuracao (true = sequencial, false = menor carga).
INSERT INTO configuracao_automacao
    (chave, valor, unidade, tipo, valor_min, valor_max, descricao)
VALUES
    ('ia.distribuicao.sequencial', 'false', NULL, 'BOOLEAN', NULL, NULL,
     'Distribuicao da IA: true gira sequencialmente por quem recebeu ha mais tempo; false prioriza menor carga.')
ON CONFLICT (chave) DO NOTHING;
