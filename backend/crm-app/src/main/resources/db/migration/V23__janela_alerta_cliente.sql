-- A janela decide quando um alerta CRITICO tambem pode acordar o grupo do
-- cliente. Ela e dado editavel no CRUD de configuracao_automacao, nao constante
-- Java. A Synapse continua recebendo alertas em qualquer horario.
INSERT INTO configuracao_automacao
    (chave, valor, unidade, tipo, valor_min, valor_max, descricao)
VALUES
    ('alerta.horario_cliente.inicio', '08:00', 'HH:mm', 'TEXT', NULL, NULL,
     'Inicio inclusivo da janela em que alertas criticos tambem vao ao cliente.'),
    ('alerta.horario_cliente.fim', '18:30', 'HH:mm', 'TEXT', NULL, NULL,
     'Fim exclusivo da janela em que alertas criticos tambem vao ao cliente.')
ON CONFLICT (chave) DO NOTHING;
