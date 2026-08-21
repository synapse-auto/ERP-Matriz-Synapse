-- A mesma mensagem da Meta pode aparecer em POSTs diferentes quando o provedor reagrupa eventos.
-- `webhook_entrada` continua sendo uma linha por POST e preserva o payload cru; esta tabela estreita
-- fornece a unicidade global por id da mensagem sem tocar na tabela mensagem particionada.
CREATE TABLE mensagem_recebida_idempotencia (
    wamid      TEXT PRIMARY KEY,
    criado_em  TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE mensagem_recebida_idempotencia IS
    'Índice global dos IDs de mensagens recebidas da Meta; reentrega ou reagrupamento não duplica a conversa.';
