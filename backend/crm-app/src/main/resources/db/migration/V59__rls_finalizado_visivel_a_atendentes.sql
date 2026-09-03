-- E145: atendimento encerrado volta ao balcao — qualquer atendente enxerga e pode reativar.
--
-- Decisao de produto (nao descuido): FINALIZADO entra no mesmo escape que IA/EM_IA ja usa.
-- Consequencia aceita e grande: todo atendente passa a ver leads finalizados de todos os
-- colegas — na agenda, na busca, na visao Finalizados — incluindo o historico completo das
-- conversas anteriores. E intencional: permite assumir um cliente que voltou sabendo o que
-- ja foi tratado.
--
-- O que NAO muda: atendimento EM_ATENDIMENTO de colega continua invisivel. RN-CRM-01 segue
-- valendo para o que esta aberto.
--
-- Participantes ativos continuam a unica excecao a RN-CRM-01 por convite explicito.

DROP POLICY IF EXISTS rls_atendimento ON atendimento;
CREATE POLICY rls_atendimento ON atendimento FOR ALL USING (
    app_enxerga_todos_os_leads()
    OR (app_papel() = 'ATENDENTE' AND (
        atendente_id = app_usuario_id() OR status = 'EM_IA' OR status = 'FINALIZADO'
        OR EXISTS (SELECT 1 FROM atendimento_participante p
                   WHERE p.atendimento_id = atendimento.id
                     AND p.usuario_id = app_usuario_id() AND p.saiu_em IS NULL)
    ))
) WITH CHECK (TRUE);

DROP POLICY IF EXISTS rls_lead ON lead;
CREATE POLICY rls_lead ON lead FOR ALL USING (
    app_enxerga_todos_os_leads()
    OR (app_papel() = 'ATENDENTE' AND (
        atendente_responsavel_id = app_usuario_id() OR status_basico = 'IA' OR status_basico = 'FINALIZADO'
        OR EXISTS (SELECT 1 FROM atendimento a JOIN atendimento_participante p
                   ON p.atendimento_id = a.id
                   WHERE a.lead_id = lead.id AND p.usuario_id = app_usuario_id()
                     AND p.saiu_em IS NULL)
    ))
) WITH CHECK (TRUE);
