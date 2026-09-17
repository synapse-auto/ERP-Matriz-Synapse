-- Solicitações assíncronas do resumo por IA. A solicitação é o ciclo, não o conteúdo:
-- o CRM guarda somente IDs, estado e erro sanitizado; o histórico segue restrito ao contrato de contexto.
CREATE TABLE solicitacao_resumo_ia (
    solicitacao_id UUID PRIMARY KEY,
    lead_id UUID NOT NULL REFERENCES lead(id) ON DELETE CASCADE,
    atendimento_id UUID NOT NULL REFERENCES atendimento(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL,
    solicitado_em TIMESTAMPTZ NOT NULL,
    atualizado_em TIMESTAMPTZ NOT NULL,
    erro_codigo VARCHAR(80),
    erro_mensagem VARCHAR(500),
    CONSTRAINT ck_solicitacao_resumo_ia_status
        CHECK (status IN ('PENDENTE', 'PROCESSANDO', 'CONCLUIDO', 'FALHOU')),
    CONSTRAINT ck_solicitacao_resumo_ia_erro
        CHECK (status = 'FALHOU' OR (erro_codigo IS NULL AND erro_mensagem IS NULL))
);

CREATE INDEX idx_solicitacao_resumo_ia_atendimento
    ON solicitacao_resumo_ia (atendimento_id, solicitado_em DESC);

CREATE INDEX idx_solicitacao_resumo_ia_lead
    ON solicitacao_resumo_ia (lead_id, solicitado_em DESC);

ALTER TABLE solicitacao_resumo_ia ENABLE ROW LEVEL SECURITY;
ALTER TABLE solicitacao_resumo_ia FORCE ROW LEVEL SECURITY;
CREATE POLICY rls_solicitacao_resumo_ia ON solicitacao_resumo_ia
    FOR ALL USING (
        app_e_agenda()
        OR app_enxerga_todos_os_leads()
        OR (
            app_papel() = 'ATENDENTE'
            AND EXISTS (
                SELECT 1
                  FROM atendimento a
                 WHERE a.id = solicitacao_resumo_ia.atendimento_id
                   AND (
                       a.atendente_id = app_usuario_id()
                       OR a.status IN ('EM_IA', 'FINALIZADO')
                       OR EXISTS (
                           SELECT 1
                             FROM atendimento_participante p
                            WHERE p.atendimento_id = a.id
                              AND p.usuario_id = app_usuario_id()
                              AND p.saiu_em IS NULL
                       )
                   )
            )
        )
    ) WITH CHECK (
        app_e_agenda()
        OR app_enxerga_todos_os_leads()
        OR (
            app_papel() = 'ATENDENTE'
            AND EXISTS (
                SELECT 1
                  FROM atendimento a
                 WHERE a.id = solicitacao_resumo_ia.atendimento_id
                   AND (
                       a.atendente_id = app_usuario_id()
                       OR a.status IN ('EM_IA', 'FINALIZADO')
                       OR EXISTS (
                           SELECT 1
                             FROM atendimento_participante p
                            WHERE p.atendimento_id = a.id
                              AND p.usuario_id = app_usuario_id()
                              AND p.saiu_em IS NULL
                       )
                   )
            )
        )
    );

COMMENT ON TABLE solicitacao_resumo_ia IS
    'Ciclo idempotente de geração assíncrona do resumo por IA; não guarda histórico ou conteúdo de mensagens.';
