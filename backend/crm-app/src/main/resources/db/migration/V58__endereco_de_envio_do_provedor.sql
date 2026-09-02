-- O telefone canonico identifica o cliente no CRM; o provedor pode usar outro identificador
-- (por exemplo, um wa_id sem o nono digito). A coluna e preenchida pelo webhook de entrada.
ALTER TABLE lead ADD COLUMN telefone_provedor VARCHAR(30);

COMMENT ON COLUMN lead.telefone_provedor IS
    'Identificador do destinatario no provedor para envio. Nao e segundo telefone, nem campo de busca ou exibicao.';
