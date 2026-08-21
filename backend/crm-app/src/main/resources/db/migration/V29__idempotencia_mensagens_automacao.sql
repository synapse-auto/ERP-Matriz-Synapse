-- A Automação envia direto ao provedor e depois registra no CRM. O wamid é a identidade
-- global da mensagem e precisa continuar único quando enviado_em atravessar a virada do mês.
--
-- Não há UNIQUE(wamid) na tabela mensagem: ela é particionada por enviado_em, e o PostgreSQL
-- exige que toda chave única de uma tabela particionada contenha a coluna de partição. Esta
-- tabela estreita, não particionada, é o índice global de idempotência; a reserva e a mensagem
-- são gravadas na mesma transação, sem alterar o particionamento do caminho crítico.
CREATE TABLE mensagem_automacao_idempotencia (
    wamid          TEXT PRIMARY KEY,
    atendimento_id UUID NOT NULL REFERENCES atendimento(id) ON DELETE CASCADE,
    mensagem_id    UUID NOT NULL,
    enviado_em     TIMESTAMPTZ NOT NULL,
    criado_em      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_mensagem_automacao_atendimento
    ON mensagem_automacao_idempotencia (atendimento_id, criado_em);

COMMENT ON TABLE mensagem_automacao_idempotencia IS
    'Índice global de wamid para registrar sem duplicidade mensagens já enviadas pela Automação.';
