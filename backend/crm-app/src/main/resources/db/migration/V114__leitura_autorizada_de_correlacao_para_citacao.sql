-- Somente leitura do alias de mensagem ja visivel; nenhuma escrita ou alias historico novo.
-- RLS de mensagem, atendimento e lead compoe carteira, participacao e acesso a clientes.
CREATE POLICY rls_correlacao_citacao_visivel ON mensagem_correlacao_canal
    FOR SELECT USING (
        namespace = 'WHATSAPP' AND NOT conflitante
        AND EXISTS (
            SELECT 1 FROM mensagem m
            JOIN atendimento a ON a.id = m.atendimento_id
            JOIN lead l ON l.id = a.lead_id
            WHERE m.id = mensagem_correlacao_canal.mensagem_id
              AND m.enviado_em = mensagem_correlacao_canal.mensagem_enviada_em
              AND a.id = mensagem_correlacao_canal.atendimento_id
        )
    );
-- A politica anterior continua sendo a unica que autoriza INSERT/UPDATE/DELETE (SERVICO).
