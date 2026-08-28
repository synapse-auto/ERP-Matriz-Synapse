-- Uma avaliacao por atendimento: a coleta (CRM ou Automacao) nao pode duplicar a nota.
-- O indice em criado_em cobre o recorte diario da Visao Geral.
CREATE UNIQUE INDEX uq_avaliacao_atendimento ON avaliacao (atendimento_id);
CREATE INDEX idx_avaliacao_criado_em ON avaliacao (criado_em);
