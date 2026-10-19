-- Projecao generica recebida por API; nunca concede acesso SQL a integracao.
CREATE TABLE cancelamento_agendamento (
    id UUID PRIMARY KEY,
    origem VARCHAR(80) NOT NULL,
    evento_externo_id VARCHAR(200) NOT NULL,
    agendamento_externo_id VARCHAR(200) NOT NULL,
    chave_idempotencia VARCHAR(200) NOT NULL,
    impressao VARCHAR(64) NOT NULL,
    lead_id UUID REFERENCES lead(id),
    atendimento_id UUID REFERENCES atendimento(id),
    agendado_para TIMESTAMPTZ NOT NULL,
    cancelado_em TIMESTAMPTZ,
    recebido_em TIMESTAMPTZ NOT NULL,
    contato_nome VARCHAR(200),
    profissional_nome VARCHAR(200),
    tipo_agendamento VARCHAR(200),
    descricao VARCHAR(2000),
    CONSTRAINT cancelamento_par_vinculo CHECK ((lead_id IS NULL) = (atendimento_id IS NULL)),
    CONSTRAINT cancelamento_evento_unico UNIQUE (origem, evento_externo_id),
    CONSTRAINT cancelamento_chave_unica UNIQUE (origem, chave_idempotencia)
);
CREATE INDEX cancelamento_recebido_idx ON cancelamento_agendamento (recebido_em DESC, id DESC);
CREATE INDEX cancelamento_agendado_idx ON cancelamento_agendamento (agendado_para DESC, id DESC);
CREATE INDEX cancelamento_ocorrencia_idx ON cancelamento_agendamento (cancelado_em DESC, id DESC)
    WHERE cancelado_em IS NOT NULL;
CREATE INDEX cancelamento_vinculo_idx ON cancelamento_agendamento (lead_id, atendimento_id);

ALTER TABLE cancelamento_agendamento ENABLE ROW LEVEL SECURITY;
ALTER TABLE cancelamento_agendamento FORCE ROW LEVEL SECURITY;
-- Read model protegido antes da paginacao. EXISTS usa as politicas vigentes do lead E atendimento,
-- inclusive participacao; nao reproduz nem amplia o recorte da carteira no codigo de relatorios.
CREATE POLICY cancelamento_leitura ON cancelamento_agendamento FOR SELECT USING (
    app_e_servico() OR (
        app_papel() IN ('GESTOR','SUBGESTOR','ADMINISTRADOR','ATENDENTE','OPERADOR') AND (
            (lead_id IS NULL AND app_enxerga_todos_os_leads()) OR (
                EXISTS (SELECT 1 FROM lead l WHERE l.id=cancelamento_agendamento.lead_id)
                AND EXISTS (SELECT 1 FROM atendimento a WHERE a.id=cancelamento_agendamento.atendimento_id
                    AND a.lead_id=cancelamento_agendamento.lead_id)
            )
        )
    )
);
CREATE POLICY cancelamento_escrita ON cancelamento_agendamento FOR INSERT WITH CHECK (app_e_servico());
-- Sem UPDATE/DELETE para a role da aplicacao: replay e imutavel, nao atualiza o historico.
REVOKE UPDATE, DELETE ON cancelamento_agendamento FROM synapse_app;
