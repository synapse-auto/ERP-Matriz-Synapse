-- A avaliacao nasce somente nas novas finalizacoes individuais elegiveis.
-- Nenhum backfill: atendimentos ja encerrados nao geram pesquisa retroativa.
-- O UUID da reserva cerca resultados tardios; os publishers existentes nao o utilizam.
ALTER TABLE outbox_evento ADD COLUMN avaliacao_reserva_id UUID;
COMMENT ON COLUMN outbox_evento.avaliacao_reserva_id IS
    'Token da tentativa de automacao.avaliacao.iniciar; resultado de lease antigo nao pode confirmar o novo.';

-- Somente a fila nova participa deste indice; mensagens e repasse cru ficam intactos.
CREATE INDEX idx_outbox_avaliacao_pendente
    ON outbox_evento (proxima_tentativa_em, id)
    WHERE tipo = 'automacao.avaliacao.iniciar'
      AND publicado_em IS NULL AND esgotado_em IS NULL;
