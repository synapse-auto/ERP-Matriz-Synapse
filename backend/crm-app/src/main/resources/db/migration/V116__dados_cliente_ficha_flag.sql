-- Apresentacao opcional de dados locais. Nenhum dado pessoal ou cadastro externo e importado.
INSERT INTO feature_flag (chave, habilitado, descricao)
VALUES ('dados_cliente_ficha', false, 'Dados cadastrais locais na ficha lateral de Atendimentos.')
ON CONFLICT (chave) DO NOTHING;
