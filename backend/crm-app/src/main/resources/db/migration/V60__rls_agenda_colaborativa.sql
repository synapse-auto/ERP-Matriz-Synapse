-- =========================================================
-- V60: leitura colaborativa da Agenda, sem elevar o papel do usuario.
--
-- A Agenda e uma visao operacional da instancia: qualquer papel autenticado
-- do CRM pode localizar um contato e abrir a conversa existente. O marcador
-- app.contexto_agenda e publicado com SET LOCAL somente pelos endpoints da
-- Agenda; sem ele, a RN-CRM-01 da V59 continua intacta.
-- =========================================================

CREATE OR REPLACE FUNCTION app_e_agenda()
RETURNS BOOLEAN LANGUAGE sql STABLE AS $$
    SELECT app_papel() IN ('ATENDENTE', 'SUBGESTOR', 'GESTOR', 'ADMINISTRADOR')
       AND current_setting('app.contexto_agenda', TRUE) = 'true';
$$;

COMMENT ON FUNCTION app_e_agenda() IS
    'Leitura colaborativa da Agenda em transacao autenticada; nao e contexto de servico.';

DROP POLICY IF EXISTS rls_atendimento ON atendimento;
CREATE POLICY rls_atendimento ON atendimento FOR ALL USING (
    app_e_agenda()
    OR app_enxerga_todos_os_leads()
    OR (app_papel() = 'ATENDENTE' AND (
        atendente_id = app_usuario_id() OR status = 'EM_IA' OR status = 'FINALIZADO'
        OR EXISTS (SELECT 1 FROM atendimento_participante p
                   WHERE p.atendimento_id = atendimento.id
                     AND p.usuario_id = app_usuario_id() AND p.saiu_em IS NULL)
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
    ))
) WITH CHECK (TRUE);
