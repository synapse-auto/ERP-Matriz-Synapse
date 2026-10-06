-- Encaminhar mensagem ou midia do Chat Interno para um cliente (docs/61).
--
-- Cada linha liga UMA mensagem interna a UMA mensagem externa criada pelo fluxo oficial de envio
-- (EnviarMensagemUseCase: outbox, idempotencia, janela de 24h, RN-CRM-01/06). E o unico elo entre os
-- dois mundos: o Chat Interno nao tem lead nem atendimento (V8, V54), e o destino e escolhido por quem
-- encaminha, validado pelo backend contra o alcance dele.
--
--   chave_idempotencia   Idempotency-Key do clique; UNIQUE: repetir devolve o mesmo encaminhamento
--   usuario_id           quem encaminhou (nunca vem do payload)
--   transferiu_o_lead    a RN-CRM-06 assumiu o lead (so quando nao havia responsavel)
--   convite_criado       quem encaminhou, sem ser responsavel nem participante, recebeu convite
--
-- Mensagem externa por FK composta (a tabela e particionada por enviado_em, como na V45). O status de
-- entrega NAO e copiado para ca: a tela le mensagem.status_entrega, que o worker e o webhook ja mantem.
--
-- Nenhuma linha de mensagem e tocada, e a tabela e nova: nada a migrar. Sem RLS: o acesso passa sempre
-- pelo backend, filtrado por usuario_id; os privilegios da role da aplicacao vem do DEFAULT PRIVILEGES (V13).

CREATE TABLE chat_interno_encaminhamento_cliente (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chave_idempotencia          VARCHAR(255) NOT NULL,
    usuario_id                  UUID NOT NULL REFERENCES usuario (id),
    conversa_id                 UUID NOT NULL REFERENCES chat_interno_conversa (id) ON DELETE CASCADE,
    mensagem_interna_id         UUID NOT NULL REFERENCES chat_interno_mensagem (id) ON DELETE CASCADE,
    atendimento_id              UUID NOT NULL REFERENCES atendimento (id),
    lead_id                     UUID NOT NULL REFERENCES lead (id),
    mensagem_externa_id         UUID NOT NULL,
    mensagem_externa_enviada_em TIMESTAMPTZ NOT NULL,
    tipo                        VARCHAR(16) NOT NULL,
    transferiu_o_lead           BOOLEAN NOT NULL,
    convite_criado              BOOLEAN NOT NULL,
    criado_em                   TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (mensagem_externa_id, mensagem_externa_enviada_em)
        REFERENCES mensagem (id, enviado_em) ON DELETE CASCADE,
    CONSTRAINT uq_chat_interno_encaminhamento_cliente_chave UNIQUE (chave_idempotencia),
    CONSTRAINT ck_chat_interno_encaminhamento_cliente_tipo
        CHECK (tipo IN ('TEXTO', 'IMAGEM', 'AUDIO', 'VIDEO', 'DOCUMENTO'))
);

-- Leitura da tela: "o que eu ja encaminhei desta mensagem", mais recente primeiro.
CREATE INDEX idx_chat_interno_encaminhamento_cliente_mensagem
    ON chat_interno_encaminhamento_cliente (mensagem_interna_id, usuario_id, criado_em DESC);

-- FKs sem indice: apagar um atendimento, lead ou mensagem externa varreria a tabela inteira.
CREATE INDEX idx_chat_interno_encaminhamento_cliente_externa
    ON chat_interno_encaminhamento_cliente (mensagem_externa_id, mensagem_externa_enviada_em);
CREATE INDEX idx_chat_interno_encaminhamento_cliente_atendimento
    ON chat_interno_encaminhamento_cliente (atendimento_id);

COMMENT ON TABLE chat_interno_encaminhamento_cliente IS
    'Elo entre uma mensagem do Chat Interno e a mensagem externa que o fluxo oficial de envio criou para o cliente.';
COMMENT ON COLUMN chat_interno_encaminhamento_cliente.chave_idempotencia IS
    'Idempotency-Key do clique do usuario; repetir devolve o mesmo encaminhamento, sem nova mensagem nem convite.';
COMMENT ON COLUMN chat_interno_encaminhamento_cliente.transferiu_o_lead IS
    'RN-CRM-06: true so quando o lead nao tinha responsavel e quem encaminhou o assumiu.';
COMMENT ON COLUMN chat_interno_encaminhamento_cliente.convite_criado IS
    'true quando quem encaminhou nao era responsavel nem participante e recebeu convite (docs/51).';
