-- =========================================================
-- E121: a janela de 24h so conta mensagem do cliente.
--
-- lead.ultima_interacao_em avanca em toda mensagem que sai (EnviarMensagem,
-- ResponderAutomacao, RegistrarMensagemEnviadaDaAutomacao). A Agenda usa esse
-- campo em semRetornoDias — "ha quanto tempo esse lead nao tem movimento" —
-- e essa semantica permanece. A Meta, porem, so abre/renova a janela de 24h
-- quando o cliente escreve. Usar ultima_interacao_em no envio deixava a janela
-- aberta enquanto a equipe falava: o CRM aceitava texto livre, a Meta recusava,
-- e o cliente nao recebia.
--
-- Fonte nova, pergunta unica: quando foi a ultima mensagem do cliente?
-- Coluna desnormalizada no lead (mesmo padrao de contadores e ultima_interacao_em).
-- NULL = o lead nunca escreveu = janela inexistente/fechada (7.144 importados).
-- =========================================================

ALTER TABLE lead ADD COLUMN ultima_mensagem_do_lead_em TIMESTAMPTZ;

COMMENT ON COLUMN lead.ultima_mensagem_do_lead_em IS
    'Instante da ultima mensagem com remetente LEAD. Base da janela de 24h da Meta. '
    'NULL = o lead nunca escreveu; janela fechada. Nao confundir com ultima_interacao_em, '
    'que avanca tambem em saida e alimenta o filtro semRetornoDias.';

-- ---------------------------------------------------------
-- Contexto de servico.
--
-- lead, atendimento e mensagem tem FORCE ROW LEVEL SECURITY desde a V12. FORCE
-- alcanca ate o dono da tabela — que e justamente o usuario que roda as migrations.
-- Sem contexto, app_papel() e NULL, a politica nega tudo e o backfill vira um
-- no-op silencioso: nenhum UPDATE, nenhum erro, nenhum aviso. Hoje a instancia
-- escapa porque o usuario do Flyway e superusuario do container, e superusuario
-- ignora RLS; num Postgres gerenciado, onde o usuario da aplicacao e apenas dono
-- da tabela, nao escaparia.
--
-- SERVICO e o papel que app_e_servico() ja reconhece para jobs e consumidores de
-- fila. set_config com o terceiro argumento TRUE e local a transacao da migration,
-- como o SET LOCAL do AplicadorDeContextoRls: nao vaza para a proxima conexao do
-- pool. Mesmo padrao documentado na V50.
-- ---------------------------------------------------------
SELECT set_config('app.papel', 'SERVICO', TRUE);

DO $$
BEGIN
    IF NOT app_enxerga_todos_os_leads() THEN
        RAISE EXCEPTION
            'contexto de servico nao aplicado: esta migration enxergaria zero leads e backfillaria nada';
    END IF;
END $$;

-- Backfill: MAX(enviado_em) das mensagens do cliente por lead. Idempotente —
-- rodar de novo escreve o mesmo instante. Lead sem mensagem LEAD permanece NULL.
UPDATE lead l
   SET ultima_mensagem_do_lead_em = origem.ultima
  FROM (
        SELECT a.lead_id AS lead_id, max(m.enviado_em) AS ultima
          FROM mensagem m
          JOIN atendimento a ON a.id = m.atendimento_id
         WHERE m.remetente_tipo = 'LEAD'
         GROUP BY a.lead_id
       ) origem
 WHERE l.id = origem.lead_id;
