ALTER TABLE evento_timeline
    ADD COLUMN ator_id UUID REFERENCES usuario(id) ON DELETE SET NULL,
    ADD COLUMN dados JSONB NOT NULL DEFAULT '{}'::jsonb;

COMMENT ON COLUMN evento_timeline.ator_id IS
    'Usuario que causou o evento; nulo para sistema, automacao e linhas legadas.';
COMMENT ON COLUMN evento_timeline.dados IS
    'Parametros estruturados usados para renderizar a descricao com os nomes atuais.';
