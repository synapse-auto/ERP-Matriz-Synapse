-- Codigo numerico interno do cliente, editavel pelo atendente na ficha.
-- Nao e campo customizado: precisa aparecer no card da lista de atendimentos,
-- e dados_customizados nao entra em projecao de listagem.
ALTER TABLE lead ADD COLUMN codigo VARCHAR(20);

ALTER TABLE lead ADD CONSTRAINT lead_codigo_somente_digitos
    CHECK (codigo IS NULL OR codigo ~ '^[0-9]+$');

COMMENT ON COLUMN lead.codigo IS
    'Identificador numerico interno do cliente (somente digitos). Editavel pelo atendente.';
