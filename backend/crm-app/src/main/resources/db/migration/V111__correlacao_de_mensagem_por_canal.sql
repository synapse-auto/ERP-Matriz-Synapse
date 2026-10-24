-- Estrutura aditiva: IDs brutos preservados; sem reescrever mensagem_id_externo/historico.
-- Um status antecipado cria a identidade sem vinculo. O aceite preenche o vinculo na mesma
-- linha; UPSERT serializa somente concorrentes da mesma identidade, sem varredura de outbox.
CREATE TABLE mensagem_correlacao_canal (
    provedor TEXT NOT NULL,
    instancia TEXT NOT NULL,
    namespace TEXT NOT NULL CHECK (namespace IN ('WHATSAPP', 'PROCESSAMENTO')),
    id_externo TEXT NOT NULL,
    mensagem_id UUID,
    mensagem_enviada_em TIMESTAMPTZ,
    atendimento_id UUID REFERENCES atendimento(id),
    status_observado status_entrega,
    ocorrido_em TIMESTAMPTZ,
    PRIMARY KEY (provedor, instancia, namespace, id_externo),
    FOREIGN KEY (mensagem_id, mensagem_enviada_em) REFERENCES mensagem(id, enviado_em) ON DELETE CASCADE,
    CHECK ((mensagem_id IS NULL AND mensagem_enviada_em IS NULL AND atendimento_id IS NULL)
        OR (mensagem_id IS NOT NULL AND mensagem_enviada_em IS NOT NULL AND atendimento_id IS NOT NULL))
);

CREATE INDEX idx_mensagem_correlacao_canal_mensagem
    ON mensagem_correlacao_canal(mensagem_id, mensagem_enviada_em) WHERE mensagem_id IS NOT NULL;

ALTER TABLE mensagem_correlacao_canal ENABLE ROW LEVEL SECURITY;
ALTER TABLE mensagem_correlacao_canal FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_mensagem_correlacao_canal ON mensagem_correlacao_canal
    USING (app_e_servico()) WITH CHECK (app_e_servico());
GRANT SELECT, INSERT, UPDATE, DELETE ON mensagem_correlacao_canal TO synapse_app;
COMMENT ON TABLE mensagem_correlacao_canal IS
    'Aliases brutos isolados por provedor/instancia/namespace e ultimo status comprovado; escrita exclusiva de servico.';
