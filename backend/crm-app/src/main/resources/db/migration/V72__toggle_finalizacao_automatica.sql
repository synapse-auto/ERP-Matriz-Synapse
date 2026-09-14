-- E177.1: trava por instância para a finalização automática de atendimentos inativos.
-- BOOLEAN reaproveita o tipo já suportado pelo schema e pelo CRUD de configuração.
-- O default seguro é desligado; a gestão precisa optar explicitamente por true.
INSERT INTO configuracao_automacao
    (chave, valor, unidade, tipo, valor_min, valor_max, descricao)
VALUES
    ('atendimento.finalizar_inativos.habilitado', 'false', NULL, 'BOOLEAN', NULL, NULL,
     'Habilita a finalização automática de atendimentos humanos inativos nesta instância.')
ON CONFLICT (chave) DO NOTHING;
