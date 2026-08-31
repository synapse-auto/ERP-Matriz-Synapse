-- E98: qual modo de transferencia a Automacao deve usar. Recurso opcional,
-- configuravel aqui e executado pelo n8n — RN-CRM-07: o CRM configura a
-- automacao, nao a executa. Nao ha scheduler nem rotina no backend, e o CRM
-- nao muda de comportamento por causa desta chave: ele so publica a escolha
-- em /internal/v1/automation-config.
--
-- Nasce 'false' porque 'false' e o comportamento de hoje. Chave que nasce
-- ligada muda a instancia no instante do deploy sem ninguem ter pedido (E52).
-- Quem liga e a gestao, pela tela, quando o fluxo do outro lado estiver pronto.
--
-- BOOLEAN e nao TEXT: sao dois estados fechados. TEXT nao valida nada
-- (ConfiguracaoAutomacao.validar trata TEXT como texto livre) e a tela
-- renderiza <Textarea>; um espaco sobrando quebraria a transferencia em
-- producao sem erro nenhum. BOOLEAN vira caixa de selecao.
INSERT INTO configuracao_automacao
    (chave, valor, unidade, tipo, valor_min, valor_max, descricao)
VALUES
    ('transferencia.por_lista', 'false', NULL, 'BOOLEAN', NULL, NULL,
     'Modo de transferencia usado pela Automacao: ligado, ela usa o modo por lista; desligado, mantem o modo padrao de hoje. Quem executa a transferencia e a Automacao; o CRM apenas publica a escolha.')
ON CONFLICT (chave) DO NOTHING;
