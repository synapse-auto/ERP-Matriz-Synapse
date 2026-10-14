-- V105 ocupada pelo snapshot do resumo em origin/main; limite aprovado, configurável na Automação.
-- Máximo técnico do tipo INT lido pelo contrato, não um prazo de negócio adicional.
INSERT INTO configuracao_automacao (chave, valor, unidade, tipo, valor_min, valor_max, descricao)
VALUES ('atendimentos.limite_sem_resposta_minutos', '20', 'minutos', 'INT', 1, 2147483647,
        'Limite de espera por resposta válida no painel operacional de Atendimentos.')
ON CONFLICT (chave) DO NOTHING;
