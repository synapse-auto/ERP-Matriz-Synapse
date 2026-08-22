-- Recurso opcional da automacao: configuravel, mas executado pelo n8n.
-- Nao ha scheduler nem rotina no backend; a ausencia da chave equivale a desligado.
INSERT INTO configuracao_automacao
    (chave, valor, unidade, tipo, valor_min, valor_max, descricao)
VALUES
    ('ia.preenchimento_automatico', 'false', NULL, 'BOOLEAN', NULL, NULL,
     'Permite que a automacao preencha dados do cliente a partir da conversa.')
ON CONFLICT (chave) DO NOTHING;
