-- Operador configuravel: mesmo recorte estrutural do Atendente, sem visao global.
-- O enum entrou na V92 em transacao separada; nenhuma migration aplicada e alterada.
ALTER TABLE permissao_perfil DROP CONSTRAINT permissao_perfil_papel_check;
ALTER TABLE permissao_perfil ADD CONSTRAINT permissao_perfil_papel_check
    CHECK (papel IN ('SUBGESTOR', 'ATENDENTE', 'OPERADOR'));
INSERT INTO permissao_perfil (papel) VALUES ('OPERADOR');

-- Leitura de campanhas e contadores agregados, sem escrita nem acesso a destinatarios/opt-outs.
-- A concessao efetiva + feature flag continuam obrigatorias nos casos de uso HTTP.
CREATE POLICY rls_campanha_operador_leitura ON campanha_template
    FOR SELECT USING (app_papel() = 'OPERADOR');
CREATE POLICY rls_campanha_dia_operador_leitura ON campanha_template_dia
    FOR SELECT USING (app_papel() = 'OPERADOR');
CREATE POLICY rls_campanha_envio_dia_operador_leitura ON campanha_envio_dia
    FOR SELECT USING (app_papel() = 'OPERADOR');

DROP POLICY IF EXISTS rls_atendimento ON atendimento;
CREATE POLICY rls_atendimento ON atendimento FOR ALL USING (
    app_e_agenda()
    OR app_enxerga_todos_os_leads()
    OR (app_papel() IN ('ATENDENTE', 'OPERADOR') AND (
        atendente_id = app_usuario_id() OR status = 'EM_IA' OR status = 'FINALIZADO'
        OR EXISTS (SELECT 1 FROM atendimento_participante p
                   WHERE p.atendimento_id = atendimento.id
                     AND p.usuario_id = app_usuario_id() AND p.saiu_em IS NULL)
    ))
) WITH CHECK (TRUE);

DROP POLICY IF EXISTS rls_atendimento_convite_leitura ON atendimento;
CREATE POLICY rls_atendimento_convite_leitura ON atendimento FOR SELECT USING (
    app_papel() IN ('ATENDENTE', 'OPERADOR')
    AND EXISTS (SELECT 1 FROM pedido_entrada_atendimento convite
                WHERE convite.atendimento_id = atendimento.id
                  AND convite.solicitante_id = app_usuario_id()
                  AND convite.tipo = 'CONVITE'
                  AND convite.status = 'PENDENTE'
                  AND convite.solicitado_em > now() - app_validade_pedido_entrada())
);

DROP POLICY IF EXISTS rls_lead ON lead;
CREATE POLICY rls_lead ON lead FOR ALL USING (
    app_e_agenda()
    OR app_enxerga_todos_os_leads()
    OR (app_papel() IN ('ATENDENTE', 'OPERADOR') AND (
        atendente_responsavel_id = app_usuario_id() OR status_basico = 'IA'
        OR status_basico = 'FINALIZADO'
        OR EXISTS (SELECT 1 FROM atendimento a JOIN atendimento_participante p
                   ON p.atendimento_id = a.id
                   WHERE a.lead_id = lead.id AND p.usuario_id = app_usuario_id()
                     AND p.saiu_em IS NULL)
    ))
) WITH CHECK (TRUE);

DROP POLICY IF EXISTS rls_lead_convite_leitura ON lead;
CREATE POLICY rls_lead_convite_leitura ON lead FOR SELECT USING (
    app_papel() IN ('ATENDENTE', 'OPERADOR')
    AND EXISTS (SELECT 1 FROM atendimento a JOIN pedido_entrada_atendimento convite
                ON convite.atendimento_id = a.id
                WHERE a.lead_id = lead.id
                  AND convite.solicitante_id = app_usuario_id()
                  AND convite.tipo = 'CONVITE'
                  AND convite.status = 'PENDENTE'
                  AND convite.solicitado_em > now() - app_validade_pedido_entrada())
);

DROP POLICY rls_solicitacao_resumo_ia ON solicitacao_resumo_ia;
CREATE POLICY rls_solicitacao_resumo_ia ON solicitacao_resumo_ia
    FOR ALL USING (
        app_e_agenda()
        OR app_enxerga_todos_os_leads()
        OR (
            app_papel() IN ('ATENDENTE', 'OPERADOR')
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
            app_papel() IN ('ATENDENTE', 'OPERADOR')
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
