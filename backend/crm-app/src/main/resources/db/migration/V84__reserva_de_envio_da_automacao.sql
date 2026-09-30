-- Reserva persistente ANTES de a Automacao enviar ao provedor (docs/50).
--
-- O n8n reserva uma chave estavel (derivada do evento de entrada, ex.: X-Synapse-Evento-Id + passo)
-- antes de chamar o provedor. A PK torna a reserva atomica: com duas entregas simultaneas do mesmo
-- evento, so uma insere; a outra recebe "ja reservado" e nao envia. mensagem_automacao_idempotencia
-- nao serve para isso: ela deduplica pelo wamid de SAIDA, que so existe depois do envio e e diferente
-- a cada envio.
--
-- estado:
--   RESERVADO  o envio foi autorizado; o resultado ainda nao voltou ao CRM. Uma reserva velha neste
--              estado e a janela ambigua (o provedor pode ter recebido): concilia-se, nunca se reenvia
--              as cegas.
--   ENVIADO    POST /mensagens-enviadas registrou a saida com esta chave; wamid_saida preenchido.

CREATE TABLE envio_automacao_reserva (
    chave           VARCHAR(200) PRIMARY KEY,
    atendimento_id  UUID NOT NULL REFERENCES atendimento (id),
    estado          VARCHAR(10) NOT NULL DEFAULT 'RESERVADO',
    wamid_saida     TEXT,
    reservado_em    TIMESTAMPTZ NOT NULL DEFAULT now(),
    enviado_em      TIMESTAMPTZ,
    CONSTRAINT ck_envio_automacao_reserva_estado CHECK (estado IN ('RESERVADO', 'ENVIADO')),
    CONSTRAINT ck_envio_automacao_reserva_enviado
        CHECK ((estado = 'ENVIADO') = (wamid_saida IS NOT NULL AND enviado_em IS NOT NULL))
);

COMMENT ON TABLE envio_automacao_reserva IS
    'Reserva atomica de um envio da Automacao antes de chamar o provedor; deduplica reentregas do mesmo evento.';

-- FK: consultas por atendimento e a checagem da FK ao apagar atendimento usam este indice.
CREATE INDEX idx_envio_automacao_reserva_atendimento ON envio_automacao_reserva (atendimento_id);

-- Conciliacao: so as reservas sem resultado importam, e devem ser poucas.
CREATE INDEX idx_envio_automacao_reserva_pendente
    ON envio_automacao_reserva (reservado_em)
    WHERE estado = 'RESERVADO';

GRANT SELECT, INSERT, UPDATE ON envio_automacao_reserva TO synapse_app;
