-- Cache sob demanda: uma linha por lead ativo no recurso, nunca varredura da carteira.
-- Referencia gerada identifica exclusivamente fotos produzidas pelo coletor Z-API.
CREATE TABLE lead_foto_captura (
    lead_id UUID PRIMARY KEY REFERENCES lead(id) ON DELETE CASCADE,
    instancia TEXT NOT NULL,
    credencial_id UUID NOT NULL REFERENCES canal_credencial(id),
    telefone_hash CHAR(64) NOT NULL,
    token UUID NOT NULL,
    proxima_consulta TIMESTAMPTZ NOT NULL,
    referencia_gerada TEXT,
    resultado TEXT CHECK (resultado IN ('FOTO', 'AUSENTE', 'ERRO', 'PRESERVADA'))
);
ALTER TABLE lead_foto_captura ENABLE ROW LEVEL SECURITY;
ALTER TABLE lead_foto_captura FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_lead_foto_captura ON lead_foto_captura
    USING (app_e_servico()) WITH CHECK (app_e_servico());
GRANT SELECT, INSERT, UPDATE, DELETE ON lead_foto_captura TO synapse_app;
