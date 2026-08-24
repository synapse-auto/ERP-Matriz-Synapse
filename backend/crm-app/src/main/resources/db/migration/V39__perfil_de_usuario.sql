-- E49: dados de exibição do próprio perfil.
-- Telefone e cargo não participam de autenticação, roteamento ou comissão;
-- são apenas informações visíveis para a equipe e para o dono da conta.
ALTER TABLE usuario
    ADD COLUMN telefone VARCHAR(30),
    ADD COLUMN cargo VARCHAR(120);

COMMENT ON COLUMN usuario.telefone IS 'Telefone de exibição do integrante; não é usado para identificar leads.';
COMMENT ON COLUMN usuario.cargo IS 'Cargo de exibição do integrante; não altera o papel de autorização.';
