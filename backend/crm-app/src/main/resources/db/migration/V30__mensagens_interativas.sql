-- Interações da Meta são traduzidas para o modelo do CRM; o JSON do provedor não atravessa o ACL.
ALTER TYPE tipo_mensagem ADD VALUE IF NOT EXISTS 'BOTOES';
ALTER TYPE tipo_mensagem ADD VALUE IF NOT EXISTS 'LISTA';

ALTER TABLE mensagem
    ADD COLUMN opcoes JSONB;

COMMENT ON COLUMN mensagem.opcoes IS
    'Opções normalizadas de BOTOES/LISTA: [{"id":"...","titulo":"...","descricao":"..."}]';
