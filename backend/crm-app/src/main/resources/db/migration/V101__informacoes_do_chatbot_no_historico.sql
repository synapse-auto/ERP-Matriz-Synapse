-- Card interno com as informacoes que o chatbot coletou antes de transferir o atendimento a um humano.
--
-- NAO e mensagem: vive fora de `mensagem` de proposito. Mensagem alimenta nao lida, previa da lista,
-- inatividade, recencia da Agenda, metricas de envio e o contexto do EV-05; um registro interno ali
-- distorceria todas essas leituras. Aqui o registro e um snapshot imutavel: o resumo da ficha do
-- lead (lead.resumo_ia) continua sendo sobrescrito por outro contrato e nunca reescreve esta linha.
CREATE TABLE atendimento_informacao_chatbot (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    atendimento_id     UUID NOT NULL REFERENCES atendimento(id) ON DELETE CASCADE,
    -- Chave da ocorrencia (Idempotency-Key do n8n). A reserva em comando_automacao_idempotencia ja
    -- garante o replay; o UNIQUE e a segunda barreira, no proprio dado, contra card duplicado.
    chave_idempotencia VARCHAR(255) NOT NULL,
    conteudo           TEXT NOT NULL,
    origem             VARCHAR(20) NOT NULL DEFAULT 'AUTOMACAO',
    registrado_em      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_atendimento_informacao_chatbot_chave UNIQUE (chave_idempotencia),
    CONSTRAINT ck_atendimento_informacao_chatbot_origem CHECK (origem IN ('AUTOMACAO')),
    -- Teto absoluto do banco. O limite operacional (menor, configuravel) e aplicado pela aplicacao.
    CONSTRAINT ck_atendimento_informacao_chatbot_conteudo
        CHECK (char_length(btrim(conteudo)) BETWEEN 1 AND 20000)
);

-- Leitura do historico: os cards de UM atendimento em ordem cronologica, sem varrer a tabela.
CREATE INDEX idx_atendimento_informacao_chatbot_atendimento
    ON atendimento_informacao_chatbot (atendimento_id, registrado_em DESC, id DESC);

-- A V13 concede SELECT/INSERT/UPDATE/DELETE por DEFAULT PRIVILEGES a toda tabela nova. O snapshot e
-- imutavel, entao UPDATE/DELETE saem explicitamente: a imutabilidade nao pode depender so de nunca
-- alguem escrever uma politica de UPDATE/DELETE aqui (o ON DELETE CASCADE do atendimento segue valendo,
-- porque a acao referencial roda com os privilegios do dono da tabela).
GRANT SELECT, INSERT ON atendimento_informacao_chatbot TO synapse_app;
REVOKE UPDATE, DELETE ON atendimento_informacao_chatbot FROM synapse_app;

ALTER TABLE atendimento_informacao_chatbot ENABLE ROW LEVEL SECURITY;
ALTER TABLE atendimento_informacao_chatbot FORCE ROW LEVEL SECURITY;

-- Leitura: quem alcanca o atendimento alcanca os cards. A subconsulta passa pela RLS de
-- `atendimento`, entao a regra de visibilidade continua em UM lugar so (RN-CRM-01) e esta politica
-- nao a duplica nem diverge dela quando ela mudar.
CREATE POLICY rls_atendimento_informacao_chatbot_leitura ON atendimento_informacao_chatbot
    FOR SELECT USING (
        EXISTS (
            SELECT 1
              FROM atendimento a
             WHERE a.id = atendimento_informacao_chatbot.atendimento_id
        )
    );

-- Escrita: so o contexto de servico (contrato interno do n8n). Nenhuma politica de UPDATE/DELETE:
-- o snapshot historico e imutavel, e usuario algum o reescreve ou apaga pela aplicacao.
CREATE POLICY rls_atendimento_informacao_chatbot_insercao ON atendimento_informacao_chatbot
    FOR INSERT WITH CHECK (app_e_servico());

COMMENT ON TABLE atendimento_informacao_chatbot IS
    'Snapshot imutavel das informacoes coletadas pelo chatbot na transferencia a um humano; card interno do historico, nunca enviado ao WhatsApp.';
COMMENT ON COLUMN atendimento_informacao_chatbot.origem IS
    'Quem produziu o conteudo. Hoje so AUTOMACAO; o card nunca e atribuido a uma pessoa ou ao cliente.';

-- Flag desligada por padrao em TODA instancia. Habilitar e operacao pontual por instancia
-- (docker/provisionamento/habilitar-informacoes-do-chatbot.sql), nunca por migration.
INSERT INTO feature_flag (chave, habilitado, descricao) VALUES
    ('informacoes_chatbot_historico', FALSE,
     'Card interno no historico do atendimento com as informacoes coletadas pelo chatbot na transferencia.')
ON CONFLICT (chave) DO NOTHING;
