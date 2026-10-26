-- Metadados aditivos: nenhuma FK historica de atendimento/mensagem e reescrita.
CREATE TABLE canal_transicao (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    origem_credencial_id UUID NOT NULL REFERENCES canal_credencial(id),
    destino_credencial_id UUID NOT NULL REFERENCES canal_credencial(id),
    provedor_origem TEXT NOT NULL,
    provedor_destino TEXT NOT NULL,
    identificador_origem TEXT NOT NULL,
    identificador_destino TEXT NOT NULL,
    estado TEXT NOT NULL CHECK (estado IN ('PREPARADA','ATIVA','RECUPERACAO')),
    preparada_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    ativada_em TIMESTAMPTZ,
    CHECK (origem_credencial_id <> destino_credencial_id)
);
CREATE UNIQUE INDEX idx_canal_transicao_unica ON canal_transicao ((true));

CREATE TABLE atendimento_rota_canal (
    atendimento_id UUID PRIMARY KEY REFERENCES atendimento(id),
    transicao_id UUID NOT NULL REFERENCES canal_transicao(id),
    credencial_historica_id UUID REFERENCES canal_credencial(id)
);
CREATE TABLE canal_transicao_programada (
    programada_id UUID PRIMARY KEY REFERENCES mensagem_programada(id),
    transicao_id UUID NOT NULL REFERENCES canal_transicao(id),
    liberada_em TIMESTAMPTZ
);
-- As migrations iniciais concedem escrita por DEFAULT PRIVILEGES. GRANT SELECT sozinho
-- nao retira esses poderes e deixaria o usuario da aplicacao reescrever o corte/retencao.
REVOKE ALL ON canal_transicao, atendimento_rota_canal, canal_transicao_programada FROM synapse_app;
GRANT SELECT ON canal_transicao, atendimento_rota_canal, canal_transicao_programada TO synapse_app;
-- Somente metadados tecnicos, sem telefone/conteudo/token. A rota exige atendimento visivel.
ALTER TABLE atendimento_rota_canal ENABLE ROW LEVEL SECURITY;
ALTER TABLE atendimento_rota_canal FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_atendimento_rota_canal ON atendimento_rota_canal
    FOR SELECT USING (EXISTS (SELECT 1 FROM atendimento a WHERE a.id=atendimento_id));
COMMENT ON TABLE canal_transicao IS 'Controle operacional auditado de transicao; escrita apenas pelo operador de manutencao. PREPARADA/RECUPERACAO suspendem novas saidas.';
COMMENT ON TABLE atendimento_rota_canal IS 'Somente novas intencoes de envio usam a rota; canal_credencial_id historico e outboxes anteriores ficam intactos.';
COMMENT ON TABLE canal_transicao_programada IS 'Agenda anterior retida para revisao explicita, sem cancelamento ou mudanca de conteudo/autoria.';
