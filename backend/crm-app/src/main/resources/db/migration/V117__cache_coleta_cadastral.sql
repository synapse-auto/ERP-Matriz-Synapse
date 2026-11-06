-- Uma lease por lead, sem dados pessoais; sem varredura da carteira.
CREATE TABLE lead_coleta_cadastral (
    lead_id UUID PRIMARY KEY REFERENCES lead(id) ON DELETE CASCADE,
    identidade_hash CHAR(64) NOT NULL,
    token UUID NOT NULL,
    estado TEXT NOT NULL,
    proxima_consulta TIMESTAMPTZ NOT NULL,
    repeticao_manual TIMESTAMPTZ NOT NULL
);
ALTER TABLE lead_coleta_cadastral ENABLE ROW LEVEL SECURITY;
ALTER TABLE lead_coleta_cadastral FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_lead_coleta_cadastral ON lead_coleta_cadastral
    USING (app_e_servico()) WITH CHECK (app_e_servico());
GRANT SELECT, INSERT, UPDATE, DELETE ON lead_coleta_cadastral TO synapse_app;
