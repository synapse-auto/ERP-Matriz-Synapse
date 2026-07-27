-- =========================================================
-- Infraestrutura transversal: auditoria, feature flags e outbox.
-- =========================================================

CREATE TABLE audit_log (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ator_id       UUID REFERENCES usuario(id),
    ator_tipo     origem_evento NOT NULL,
    acao          VARCHAR(80) NOT NULL,
    entidade_tipo VARCHAR(60) NOT NULL,
    entidade_id   UUID,
    lead_id       UUID,
    dados_antes   JSONB,
    dados_depois  JSONB,
    ip            INET,
    criado_em     TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE audit_log IS
    'Transversal, para manutencao e diagnostico. Distinta de evento_timeline, que e a visao do atendente por lead.';
COMMENT ON COLUMN audit_log.lead_id IS 'Desnormalizado para filtrar rapido por cliente.';

CREATE TABLE feature_flag (
    chave      VARCHAR(80) PRIMARY KEY,
    habilitado BOOLEAN NOT NULL DEFAULT FALSE,
    descricao  TEXT
);
COMMENT ON TABLE feature_flag IS 'Base PAI: habilitar modulos por filho sem deploy.';

CREATE TABLE outbox_evento (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo         VARCHAR(100) NOT NULL,
    payload      JSONB NOT NULL,
    criado_em    TIMESTAMPTZ NOT NULL DEFAULT now(),
    publicado_em TIMESTAMPTZ,
    tentativas   SMALLINT NOT NULL DEFAULT 0
);
COMMENT ON TABLE outbox_evento IS
    'Transactional Outbox: o evento e gravado na mesma transacao da mudanca de estado e publicado depois.';
