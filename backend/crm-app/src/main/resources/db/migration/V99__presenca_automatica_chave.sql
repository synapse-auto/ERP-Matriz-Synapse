-- E223 (PR B): chave por instancia da presenca automatica. DESLIGADA por padrao: com `false` o comportamento e
-- exatamente o do PR A (presenca so por clique, com historico). Ligar e desligar nao pede deploy:
--   UPDATE configuracao_automacao SET valor = 'true' WHERE chave = 'presenca.automatica';
-- BOOLEAN reaproveita a validacao do CRUD de configuracao, como ia.distribuicao.sequencial (V71).
INSERT INTO configuracao_automacao
    (chave, valor, unidade, tipo, valor_min, valor_max, descricao)
VALUES
    ('presenca.automatica', 'false', NULL, 'BOOLEAN', NULL, NULL,
     'Presenca automatica: true marca ONLINE ao conectar e OFFLINE depois da tolerancia sem nenhuma conexao; false so por clique.')
ON CONFLICT (chave) DO NOTHING;
