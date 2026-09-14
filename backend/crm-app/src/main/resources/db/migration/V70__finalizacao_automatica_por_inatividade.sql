-- E177: parâmetro de negócio para finalização automática de atendimentos humanos inativos.
-- A linha é criada em produção pela migration; o seed de desenvolvimento mantém o mesmo default.
INSERT INTO configuracao_automacao
    (chave, valor, unidade, tipo, valor_min, valor_max, descricao)
VALUES
    ('atendimento.finalizar_apos_horas', '24', 'horas', 'INT', 1, 720,
     'Horas de inatividade até finalizar o atendimento humano automaticamente.')
ON CONFLICT (chave) DO NOTHING;
