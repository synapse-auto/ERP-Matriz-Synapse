-- Convite pendente concede somente LEITURA, e somente enquanto vigente (docs/51).
--
-- A V78 colocou o convite pendente dentro das politicas FOR ALL de atendimento e lead. Dois
-- efeitos que nao eram a intencao declarada ("leitura operacional para decidir"):
--   1. o convidado ainda nao aceito conseguia ENVIAR e, pela RN-CRM-06, herdar lead e atendimento;
--   2. o convite continuava valendo depois de expirar, porque a validade so era calculada na
--      aplicacao e a linha permanece 'PENDENTE' no banco.
--
-- Correcao: o convite sai das politicas FOR ALL e volta numa politica FOR SELECT propria, limitada
-- a validade configurada. Politicas permissivas se somam por comando; UPDATE, DELETE e
-- SELECT ... FOR UPDATE (o lock do envio) so enxergam as politicas FOR ALL, entao o convidado le o
-- cartao e o historico para decidir, mas nao escreve nem trava a linha.

CREATE OR REPLACE FUNCTION app_validade_pedido_entrada()
RETURNS INTERVAL LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public AS $$
    SELECT make_interval(mins => COALESCE(
        (SELECT valor::int FROM configuracao_automacao
          WHERE chave = 'atendimento.pedido-entrada-expiracao-minutos'),
        30));
$$;

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

DROP POLICY IF EXISTS rls_atendimento_convite_leitura ON atendimento;
CREATE POLICY rls_atendimento_convite_leitura ON atendimento FOR SELECT USING (
    app_papel() = 'ATENDENTE'
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
    OR (app_papel() = 'ATENDENTE' AND (
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
    app_papel() = 'ATENDENTE'
    AND EXISTS (SELECT 1 FROM atendimento a JOIN pedido_entrada_atendimento convite
                ON convite.atendimento_id = a.id
                WHERE a.lead_id = lead.id
                  AND convite.solicitante_id = app_usuario_id()
                  AND convite.tipo = 'CONVITE'
                  AND convite.status = 'PENDENTE'
                  AND convite.solicitado_em > now() - app_validade_pedido_entrada())
);
