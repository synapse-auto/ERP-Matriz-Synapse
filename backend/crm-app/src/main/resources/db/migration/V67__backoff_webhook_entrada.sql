-- Retentativas de webhook precisam sobreviver ao restart do backend e ao proximo tick do scheduler.
-- Antes desta migration a fila era varrida a cada segundo, fazendo cinco tentativas quase
-- imediatas quando o provedor ainda nao tinha disponibilizado uma midia recebida.
ALTER TABLE webhook_entrada
    ADD COLUMN proxima_tentativa_em TIMESTAMPTZ NOT NULL DEFAULT now();

COMMENT ON COLUMN webhook_entrada.proxima_tentativa_em IS
    'Instante minimo da proxima tentativa; o backoff fica duravel no banco e nao consome tentativa '
    'quando o circuito do provedor esta aberto.';

DROP INDEX idx_webhook_a_processar;

CREATE INDEX idx_webhook_a_processar
    ON webhook_entrada (proxima_tentativa_em, recebido_em)
 WHERE processado_em IS NULL AND esgotado_em IS NULL;
