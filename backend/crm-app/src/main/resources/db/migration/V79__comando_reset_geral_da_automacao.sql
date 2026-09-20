-- E195: o comando de teste que vai alem do #reset.
-- O #reset (V38) devolve o atendimento humano para a Automacao. Este devolve tambem, e
-- ainda zera a ficha do lead (etapa e resumo de IA) para repetir teste manual sem criar
-- um lead novo a cada rodada. O literal e configuravel por filho, como o do #reset.
INSERT INTO configuracao_automacao
    (chave, valor, unidade, tipo, valor_min, valor_max, descricao)
VALUES
    ('automacao.comando_reset_geral', '#resetgeral', NULL, 'TEXT', NULL, NULL,
     'Mensagem exata que devolve o atendimento para a Automacao e zera etapa e resumo do lead.')
ON CONFLICT (chave) DO NOTHING;
