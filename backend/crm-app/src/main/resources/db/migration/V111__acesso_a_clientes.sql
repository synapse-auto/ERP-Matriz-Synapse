-- Negacao estrutural adicional, composta com (nunca substitui) carteira/participacao/Agenda.
-- O contexto e resolvido pelo catalogo/cache de Gestao e publicado uma vez por transacao.
CREATE FUNCTION app_pode_acessar_clientes()
RETURNS BOOLEAN LANGUAGE sql STABLE AS $$
    SELECT COALESCE(app_e_servico(), FALSE)
        OR COALESCE(current_setting('app.acesso_clientes', TRUE) = 'true', FALSE);
$$;

-- SELECT escalar vira InitPlan: nao consulta permissoes por linha/card.
-- Tabelas sem RLS preservam o acesso anterior para quem continua autorizado.
DO $$
DECLARE tabela TEXT; ja_protegida BOOLEAN;
BEGIN
    FOREACH tabela IN ARRAY ARRAY[
        'lead', 'atendimento', 'mensagem', 'lembrete', 'mensagem_programada',
        'solicitacao_resumo_ia', 'atendimento_informacao_chatbot', 'evento_timeline',
        'mensagem_reacao', 'mensagem_reacao_cliente', 'lead_tag', 'atendimento_leitura',
        'atendimento_participante', 'pedido_entrada_atendimento', 'mensagem_id_externo',
        'mensagem_referencia', 'lead_atendente_vinculado', 'avaliacao',
        'finalizacao_em_massa', 'finalizacao_em_massa_item', 'finalizacao_em_massa_aviso',
        'campanha_template', 'campanha_template_destinatario', 'campanha_template_dia',
        'campanha_envio_dia', 'contato_optout', 'cancelamento_agendamento'
    ] LOOP
        SELECT relrowsecurity INTO STRICT ja_protegida FROM pg_class
          WHERE oid = to_regclass(format('public.%I', tabela));
        IF NOT ja_protegida THEN
            EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', tabela);
            EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', tabela);
            EXECUTE format('CREATE POLICY acesso_clientes_base ON %I FOR ALL USING (TRUE) WITH CHECK (TRUE)', tabela);
        END IF;
        EXECUTE format('CREATE POLICY acesso_clientes ON %I AS RESTRICTIVE FOR ALL '
            'USING ((SELECT app_pode_acessar_clientes())) '
            'WITH CHECK ((SELECT app_pode_acessar_clientes()))', tabela);
    END LOOP;
END $$;

-- Helpers legados SECURITY DEFINER tambem precisam compor a negacao; RLS sozinha nao os alcanca.
CREATE OR REPLACE FUNCTION app_lead_do_atendimento(p_atendimento UUID)
RETURNS UUID LANGUAGE sql SECURITY DEFINER SET search_path = public AS $$
    SELECT lead_id FROM atendimento WHERE id = p_atendimento AND app_pode_acessar_clientes();
$$;
CREATE OR REPLACE FUNCTION app_dono_do_atendimento(p_atendimento UUID)
RETURNS UUID LANGUAGE sql SECURITY DEFINER SET search_path = public AS $$
    SELECT atendente_id FROM atendimento WHERE id = p_atendimento AND app_pode_acessar_clientes();
$$;
CREATE OR REPLACE FUNCTION app_atendimento_aberto_do_lead(p_lead UUID)
RETURNS UUID LANGUAGE sql SECURITY DEFINER SET search_path = public AS $$
    SELECT id FROM atendimento WHERE lead_id = p_lead AND status <> 'FINALIZADO'
      AND app_pode_acessar_clientes() ORDER BY iniciado_em DESC LIMIT 1;
$$;
CREATE OR REPLACE FUNCTION app_buscar_lead_para_entrada(p_termo TEXT, p_usuario UUID)
RETURNS TABLE(id UUID, nome TEXT, empresa TEXT, responsavel_id UUID, responsavel_nome TEXT)
LANGUAGE sql SECURITY DEFINER SET search_path = public AS $$
    SELECT l.id, l.nome::text, l.empresa::text, l.atendente_responsavel_id, u.nome::text
      FROM lead l JOIN usuario u ON u.id = l.atendente_responsavel_id
      JOIN atendimento a ON a.lead_id = l.id AND a.status <> 'FINALIZADO'
     WHERE app_pode_acessar_clientes()
       AND l.atendente_responsavel_id IS NOT NULL AND l.atendente_responsavel_id <> p_usuario
       AND (l.nome ILIKE '%' || p_termo || '%' OR regexp_replace(COALESCE(l.telefone,''),'[^0-9]','','g')
            LIKE '%' || regexp_replace(p_termo,'[^0-9]','','g') || '%')
     ORDER BY l.nome LIMIT 10;
$$;
CREATE OR REPLACE FUNCTION app_registrar_pedido_entrada(p_atendimento UUID, p_solicitante UUID)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE v_id UUID;
BEGIN
    IF NOT app_pode_acessar_clientes() THEN RETURN NULL; END IF;
    IF NOT EXISTS (SELECT 1 FROM atendimento WHERE id = p_atendimento
                   AND status <> 'FINALIZADO' AND atendente_id IS NOT NULL
                   AND atendente_id <> p_solicitante) THEN RETURN NULL; END IF;
    INSERT INTO pedido_entrada_atendimento (atendimento_id, solicitante_id)
    VALUES (p_atendimento, p_solicitante) ON CONFLICT DO NOTHING RETURNING id INTO v_id;
    IF v_id IS NULL THEN
        SELECT id INTO v_id FROM pedido_entrada_atendimento WHERE atendimento_id = p_atendimento
          AND solicitante_id = p_solicitante AND status = 'PENDENTE';
    END IF;
    RETURN v_id;
END $$;
