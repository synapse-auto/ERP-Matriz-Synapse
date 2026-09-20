-- Convites sao pedidos de entrada criados por um participante para outro atendente.
ALTER TABLE pedido_entrada_atendimento
    ADD COLUMN tipo VARCHAR(20) NOT NULL DEFAULT 'SOLICITACAO';

ALTER TABLE pedido_entrada_atendimento
    ADD CONSTRAINT ck_pedido_entrada_tipo
    CHECK (tipo IN ('SOLICITACAO', 'CONVITE'));

-- A restricao parcial fecha a corrida entre dois cliques/transacoes simultaneas:
-- so pode existir um convite pendente para o mesmo atendimento e destinatario.
CREATE UNIQUE INDEX ux_pedido_convite_pendente
    ON pedido_entrada_atendimento (atendimento_id, solicitante_id)
    WHERE tipo = 'CONVITE' AND status = 'PENDENTE';

-- Um convite pendente concede somente a leitura operacional necessaria para o destinatario
-- encontrar o cartao em Pendentes e decidir; nao concede acesso a outro atendimento.
DROP POLICY IF EXISTS rls_atendimento ON atendimento;
CREATE POLICY rls_atendimento ON atendimento FOR ALL USING (
    app_e_agenda()
    OR app_enxerga_todos_os_leads()
    OR (app_papel() = 'ATENDENTE' AND (
        atendente_id = app_usuario_id() OR status = 'EM_IA' OR status = 'FINALIZADO'
        OR EXISTS (SELECT 1 FROM atendimento_participante p
                   WHERE p.atendimento_id = atendimento.id
                     AND p.usuario_id = app_usuario_id() AND p.saiu_em IS NULL)
        OR EXISTS (SELECT 1 FROM pedido_entrada_atendimento convite
                   WHERE convite.atendimento_id = atendimento.id
                     AND convite.solicitante_id = app_usuario_id()
                     AND convite.tipo = 'CONVITE'
                     AND convite.status = 'PENDENTE')
    ))
) WITH CHECK (TRUE);

DROP POLICY IF EXISTS rls_lead ON lead;
CREATE POLICY rls_lead ON lead FOR ALL USING (
    app_e_agenda()
    OR app_enxerga_todos_os_leads()
    OR (app_papel() = 'ATENDENTE' AND (
        atendente_responsavel_id = app_usuario_id() OR status_basico = 'IA'
        OR status_basico = 'FINALIZADO'
        OR EXISTS (SELECT 1 FROM atendimento a JOIN atendimento_participante p
                   ON p.atendimento_id = a.id
                   WHERE a.lead_id = lead.id AND p.usuario_id = app_usuario_id()
                     AND p.saiu_em IS NULL)
        OR EXISTS (SELECT 1 FROM atendimento a JOIN pedido_entrada_atendimento convite
                   ON convite.atendimento_id = a.id
                   WHERE a.lead_id = lead.id
                     AND convite.solicitante_id = app_usuario_id()
                     AND convite.tipo = 'CONVITE'
                     AND convite.status = 'PENDENTE')
    ))
) WITH CHECK (TRUE);

COMMENT ON COLUMN pedido_entrada_atendimento.tipo IS
    'SOLICITACAO quando o usuario pede entrada; CONVITE quando um participante convida o destinatario.';
