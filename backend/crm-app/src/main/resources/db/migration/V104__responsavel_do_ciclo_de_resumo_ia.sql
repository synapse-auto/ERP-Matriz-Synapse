-- Snapshot técnico para recusar resultado de geração solicitada antes de uma transferência.
-- V103 ocupada por setores em origin/main; numeração reconferida antes do push.
-- Não inferir o dono histórico de solicitações antigas a partir do dono atual.
ALTER TABLE solicitacao_resumo_ia ADD COLUMN atendente_id_no_pedido UUID;
COMMENT ON COLUMN solicitacao_resumo_ia.atendente_id_no_pedido IS
    'Responsável capturado sob lock na criação; NULL legado sem snapshot verificável.';
