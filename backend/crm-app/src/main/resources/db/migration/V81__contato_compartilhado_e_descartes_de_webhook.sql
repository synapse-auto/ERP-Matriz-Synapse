-- Contato compartilhado pelo cliente vira mensagem estruturada, como LOCALIZACAO (V61):
-- nome e telefones ficam em mensagem.midia_metadados. ADD VALUE roda dentro da transacao da
-- migration porque o valor novo nao e usado nesta mesma transacao (Postgres 12+).
ALTER TYPE tipo_mensagem ADD VALUE 'CONTATO';

-- Antes desta migration, um item de mensagem que o tradutor nao entendia sumia com um WARN e a
-- linha terminava "processada" como se tudo tivesse virado mensagem. Agora a linha diz quantos
-- itens de cliente ficaram de fora e por que. DEFAULT constante: ADD COLUMN sem reescrever a tabela.
ALTER TABLE webhook_entrada
    ADD COLUMN itens_descartados SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN descartes JSONB;

COMMENT ON COLUMN webhook_entrada.itens_descartados IS
    'Itens de mensagem do cliente que nao viraram mensagem no historico. Zero quando todos foram '
    'traduzidos. Status de entrega e Status/Story ignorados por decisao nao contam.';
COMMENT ON COLUMN webhook_entrada.descartes IS
    'Lista [{tipo, motivo}] com tipo normalizado para vocabulario fechado e motivo do descarte. '
    'Nunca contem telefone, nome, conteudo ou payload.';

-- A consulta operacional e "quais POSTs perderam item, e quando". O indice parcial so carrega as
-- linhas com descarte, que devem ser raras; a varredura de construcao acontece uma vez, no deploy.
CREATE INDEX idx_webhook_entrada_com_descarte
    ON webhook_entrada (processado_em)
 WHERE itens_descartados > 0;
