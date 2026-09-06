-- Customizacao opcional da marca, sem migrar os arquivos de deploy atuais.
-- NULL em tema/logo significa "usar o fallback do classpath".
CREATE TABLE marca_da_instancia (
    id                      INTEGER PRIMARY KEY DEFAULT 1,
    tema                    JSONB DEFAULT NULL,
    logo_referencia_storage VARCHAR(200) DEFAULT NULL,
    atualizado_por_id       UUID REFERENCES usuario(id) DEFAULT NULL,
    atualizado_em           TIMESTAMPTZ DEFAULT NULL,
    CONSTRAINT ck_marca_da_instancia_singleton CHECK (id = 1)
);

INSERT INTO marca_da_instancia (id) VALUES (1)
ON CONFLICT (id) DO NOTHING;
