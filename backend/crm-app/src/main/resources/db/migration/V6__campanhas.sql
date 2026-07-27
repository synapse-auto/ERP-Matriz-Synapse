-- =========================================================
-- Modulo crm-campanhas: filtro modular, campanhas e metricas.
-- =========================================================

CREATE TABLE filtro_modular (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome          VARCHAR(120) NOT NULL,
    contexto      contexto_filtro NOT NULL,
    criterios     JSONB NOT NULL,
    criado_por_id UUID REFERENCES usuario(id),
    criado_em     TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON COLUMN filtro_modular.criterios IS
    'Arvore AND/OR de criterios (Composite + Interpreter). JSONB para acrescentar criterio sem migration.';

CREATE TABLE campanha (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome                 VARCHAR(150) NOT NULL,
    filtro_publico_id    UUID REFERENCES filtro_modular(id),
    data_inicio          TIMESTAMPTZ,
    data_fim             TIMESTAMPTZ,
    intervalo_envio_dias SMALLINT NOT NULL CHECK (intervalo_envio_dias BETWEEN 1 AND 7),
    status               status_campanha NOT NULL DEFAULT 'RASCUNHO',
    criado_por_id        UUID REFERENCES usuario(id)
);

CREATE TABLE campanha_mensagem (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campanha_id UUID NOT NULL REFERENCES campanha(id) ON DELETE CASCADE,
    ordem       SMALLINT NOT NULL,
    conteudo    TEXT NOT NULL,
    tipo_midia  VARCHAR(30)
);

CREATE TABLE campanha_mensagem_metrica (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campanha_mensagem_id UUID NOT NULL REFERENCES campanha_mensagem(id) ON DELETE CASCADE,
    lead_id              UUID NOT NULL REFERENCES lead(id),
    enviada              BOOLEAN NOT NULL DEFAULT FALSE,
    visualizou           BOOLEAN NOT NULL DEFAULT FALSE,
    respondeu            BOOLEAN NOT NULL DEFAULT FALSE,
    entrou_atendimento   BOOLEAN NOT NULL DEFAULT FALSE,
    num_mensagens_lead   INT NOT NULL DEFAULT 0,
    fechado              BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (campanha_mensagem_id, lead_id)
);
