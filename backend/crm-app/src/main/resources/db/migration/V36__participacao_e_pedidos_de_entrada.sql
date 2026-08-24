-- E43: participacao explicita em atendimento. A participacao nao altera o dono comercial.
CREATE TABLE atendimento_participante (
    atendimento_id UUID NOT NULL REFERENCES atendimento(id) ON DELETE CASCADE,
    usuario_id UUID NOT NULL REFERENCES usuario(id),
    entrou_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    saiu_em TIMESTAMPTZ,
    PRIMARY KEY (atendimento_id, usuario_id, entrou_em)
);

CREATE UNIQUE INDEX uq_atendimento_participante_ativo
    ON atendimento_participante (atendimento_id, usuario_id)
    WHERE saiu_em IS NULL;

CREATE TABLE pedido_entrada_atendimento (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    atendimento_id UUID NOT NULL REFERENCES atendimento(id) ON DELETE CASCADE,
    solicitante_id UUID NOT NULL REFERENCES usuario(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE'
        CHECK (status IN ('PENDENTE', 'APROVADO', 'RECUSADO', 'EXPIRADO')),
    solicitado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    respondido_em TIMESTAMPTZ,
    respondido_por_id UUID REFERENCES usuario(id)
);

CREATE UNIQUE INDEX uq_pedido_entrada_pendente
    ON pedido_entrada_atendimento (atendimento_id, solicitante_id)
    WHERE status = 'PENDENTE';
CREATE INDEX idx_pedido_entrada_atendimento ON pedido_entrada_atendimento (atendimento_id, status);

-- O limite e configuravel por instancia. A ausencia da linha usa o default do caso de uso.
INSERT INTO configuracao_automacao (chave, valor, unidade, tipo, valor_min, valor_max, descricao)
VALUES ('atendimento.pedido-entrada-expiracao-minutos', '30', 'minutos', 'INT', 1, 1440,
        'Tempo de validade de um pedido de entrada em atendimento')
ON CONFLICT (chave) DO NOTHING;

-- Pedido e uma operacao estreita: nao abre leitura do atendimento ao solicitante.
CREATE OR REPLACE FUNCTION app_registrar_pedido_entrada(p_atendimento UUID, p_solicitante UUID)
RETURNS UUID LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE v_id UUID;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM atendimento WHERE id = p_atendimento
                   AND status <> 'FINALIZADO' AND atendente_id IS NOT NULL
                   AND atendente_id <> p_solicitante) THEN
        RETURN NULL;
    END IF;
    INSERT INTO pedido_entrada_atendimento (atendimento_id, solicitante_id)
    VALUES (p_atendimento, p_solicitante)
    ON CONFLICT DO NOTHING
    RETURNING id INTO v_id;
    IF v_id IS NULL THEN
        SELECT id INTO v_id FROM pedido_entrada_atendimento
         WHERE atendimento_id = p_atendimento AND solicitante_id = p_solicitante
           AND status = 'PENDENTE';
    END IF;
    RETURN v_id;
END $$;

CREATE OR REPLACE FUNCTION app_lead_do_atendimento(p_atendimento UUID)
RETURNS UUID LANGUAGE sql SECURITY DEFINER SET search_path = public AS $$
    SELECT lead_id FROM atendimento WHERE id = p_atendimento;
$$;
CREATE OR REPLACE FUNCTION app_dono_do_atendimento(p_atendimento UUID)
RETURNS UUID LANGUAGE sql SECURITY DEFINER SET search_path = public AS $$
    SELECT atendente_id FROM atendimento WHERE id = p_atendimento;
$$;
CREATE OR REPLACE FUNCTION app_atendimento_aberto_do_lead(p_lead UUID)
RETURNS UUID LANGUAGE sql SECURITY DEFINER SET search_path = public AS $$
    SELECT id FROM atendimento WHERE lead_id=p_lead AND status <> 'FINALIZADO' ORDER BY iniciado_em DESC LIMIT 1;
$$;

CREATE OR REPLACE FUNCTION app_buscar_lead_para_entrada(p_termo TEXT, p_usuario UUID)
RETURNS TABLE(id UUID, nome TEXT, empresa TEXT, responsavel_id UUID, responsavel_nome TEXT)
LANGUAGE sql SECURITY DEFINER SET search_path = public AS $$
    SELECT l.id, l.nome::text, l.empresa::text, l.atendente_responsavel_id, u.nome::text
      FROM lead l JOIN usuario u ON u.id=l.atendente_responsavel_id
      JOIN atendimento a ON a.lead_id=l.id AND a.status <> 'FINALIZADO'
     WHERE l.atendente_responsavel_id IS NOT NULL AND l.atendente_responsavel_id <> p_usuario
       AND (l.nome ILIKE '%' || p_termo || '%' OR regexp_replace(COALESCE(l.telefone,''),'[^0-9]','','g') LIKE '%' || regexp_replace(p_termo,'[^0-9]','','g') || '%')
     ORDER BY l.nome LIMIT 10;
$$;

-- Participantes ativos sao a unica excecao a RN-CRM-01: convite explicito, sem lista geral.
DROP POLICY IF EXISTS rls_atendimento ON atendimento;
CREATE POLICY rls_atendimento ON atendimento FOR ALL USING (
    app_enxerga_todos_os_leads()
    OR (app_papel() = 'ATENDENTE' AND (
        atendente_id = app_usuario_id() OR status = 'EM_IA'
        OR EXISTS (SELECT 1 FROM atendimento_participante p
                   WHERE p.atendimento_id = atendimento.id
                     AND p.usuario_id = app_usuario_id() AND p.saiu_em IS NULL)
    ))
) WITH CHECK (TRUE);

DROP POLICY IF EXISTS rls_lead ON lead;
CREATE POLICY rls_lead ON lead FOR ALL USING (
    app_enxerga_todos_os_leads()
    OR (app_papel() = 'ATENDENTE' AND (
        atendente_responsavel_id = app_usuario_id() OR status_basico = 'IA'
        OR EXISTS (SELECT 1 FROM atendimento a JOIN atendimento_participante p
                   ON p.atendimento_id = a.id
                   WHERE a.lead_id = lead.id AND p.usuario_id = app_usuario_id()
                     AND p.saiu_em IS NULL)
    ))
) WITH CHECK (TRUE);
