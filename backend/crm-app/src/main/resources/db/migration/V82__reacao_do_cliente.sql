-- E214: reacao do cliente (WhatsApp) a uma mensagem da conversa.
--
-- Separada de mensagem_reacao de proposito: aquela e uma reacao por USUARIO do CRM
-- (usuario_id NOT NULL REFERENCES usuario). O cliente nao e usuario; inventar um usuario_id para
-- ele misturaria autoria e quebraria o "reagi" de quem esta logado.
--
-- Semantica (docs/44, Fase 2 item 2):
--   * uma linha por mensagem: numa conversa individual ha um so cliente, e o WhatsApp guarda uma
--     reacao por pessoa por mensagem — reagir de novo SUBSTITUI o emoji;
--   * remocao (emoji vazio no provedor) grava emoji NULL em vez de apagar a linha, para que um
--     evento atrasado mais antigo nao ressuscite a reacao removida;
--   * reagido_em e o horario do provedor: so um evento igual ou mais novo altera a linha;
--   * o alvo e resolvido pelo id externo (mensagem_id_externo) e so vale se a mensagem for da
--     conversa do lead que reagiu. Alvo desconhecido nao grava nada aqui.

CREATE TABLE mensagem_reacao_cliente (
    mensagem_id          UUID NOT NULL,
    mensagem_enviada_em  TIMESTAMPTZ NOT NULL,
    emoji                VARCHAR(32),
    reagido_em           TIMESTAMPTZ NOT NULL,
    id_externo_evento    TEXT NOT NULL,
    atualizado_em        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (mensagem_id, mensagem_enviada_em),
    FOREIGN KEY (mensagem_id, mensagem_enviada_em)
        REFERENCES mensagem (id, enviado_em) ON DELETE CASCADE
);

COMMENT ON TABLE mensagem_reacao_cliente IS
    'Reacao atual do cliente a uma mensagem de atendimento. emoji NULL = removida pelo cliente.';
COMMENT ON COLUMN mensagem_reacao_cliente.reagido_em IS
    'Horario do evento no provedor; ordena eventos fora de ordem.';

-- A PK (mensagem_id, mensagem_enviada_em) ja serve a leitura em lote por pagina do historico.

GRANT SELECT, INSERT, UPDATE, DELETE ON mensagem_reacao_cliente TO synapse_app;
