-- E48: cada filho pode escolher o literal que reinicia o contexto da conversa.
-- O CRM somente devolve o atendimento para a IA; a limpeza do contexto continua sendo
-- responsabilidade da Automacao, que recebe a mesma mensagem no n8n.
INSERT INTO configuracao_automacao
    (chave, valor, unidade, tipo, valor_min, valor_max, descricao)
VALUES
    ('automacao.comando_reset', '#reset', NULL, 'TEXT', NULL, NULL,
     'Mensagem exata que devolve o atendimento humano para a Automacao.')
ON CONFLICT (chave) DO NOTHING;
