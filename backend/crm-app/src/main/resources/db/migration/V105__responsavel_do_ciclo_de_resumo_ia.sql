-- Snapshot técnico para recusar resultado de geração solicitada antes de uma transferência.
-- V103 ocupada por setores em origin/main; numeração reconferida antes do push.
-- Não inferir o dono histórico de solicitações antigas a partir do dono atual.
ALTER TABLE solicitacao_resumo_ia ADD COLUMN atendente_id_no_pedido UUID;
ALTER TABLE solicitacao_resumo_ia ADD COLUMN versao_responsabilidade_no_pedido BIGINT;
ALTER TABLE atendimento ADD COLUMN versao_responsabilidade BIGINT NOT NULL DEFAULT 0;

-- Metadado técnico: detecta inclusive transferência A -> B -> A, sem mudar regras de dono.
-- Sem consulta adicional: avança somente no UPDATE que já altera o responsável/estado.
CREATE FUNCTION app_avancar_versao_responsabilidade() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.atendente_id IS DISTINCT FROM NEW.atendente_id OR OLD.status IS DISTINCT FROM NEW.status THEN
        NEW.versao_responsabilidade := OLD.versao_responsabilidade + 1;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_versao_responsabilidade_atendimento
BEFORE UPDATE OF atendente_id, status ON atendimento
FOR EACH ROW EXECUTE FUNCTION app_avancar_versao_responsabilidade();
COMMENT ON COLUMN solicitacao_resumo_ia.atendente_id_no_pedido IS
    'Responsável capturado sob lock na criação; NULL legado sem snapshot verificável.';
