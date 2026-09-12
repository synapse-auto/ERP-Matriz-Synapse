-- EV-05: marcos duraveis usados pelo cron do n8n para evitar reprocessamento
-- desnecessario. O n8n continua sem acesso direto ao banco; estas colunas sao
-- atualizadas somente pelos casos de uso internos autenticados.
ALTER TABLE lead ADD COLUMN resumo_ia_atualizado_em TIMESTAMPTZ;
ALTER TABLE lead ADD COLUMN preenchimento_automatico_avaliado_em TIMESTAMPTZ;

COMMENT ON COLUMN lead.resumo_ia_atualizado_em IS
    'Instante da ultima escrita do resumo pela Automacao (EV-05).';
COMMENT ON COLUMN lead.preenchimento_automatico_avaliado_em IS
    'Instante da ultima avaliacao de preenchimento automatico, mesmo sem campo aplicavel (EV-05).';

-- Intervalos sao parametros da instancia, com faixa validada pelo mesmo
-- mecanismo de configuracao dos demais tempos da Automacao.
INSERT INTO configuracao_automacao
    (chave, valor, unidade, tipo, valor_min, valor_max, descricao)
VALUES
    ('ia.resumo.intervalo_horas', '24', 'horas', 'INT', 1, 720,
     'Intervalo minimo entre ciclos de resumo da IA por lead.'),
    ('ia.preenchimento_automatico.intervalo_horas', '24', 'horas', 'INT', 1, 720,
     'Intervalo minimo entre avaliacoes de preenchimento automatico por lead.')
ON CONFLICT (chave) DO NOTHING;
