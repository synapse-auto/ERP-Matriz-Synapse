ALTER TABLE atendimento
    ADD COLUMN lido_ate TIMESTAMPTZ;

COMMENT ON COLUMN atendimento.lido_ate IS
    'Ultimo instante que o responsavel comercial abriu a conversa. Leitura por gestor nao altera.';
