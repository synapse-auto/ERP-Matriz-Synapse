-- =========================================================
-- Modulo crm-automacao-config: fonte da verdade dos parametros.
-- O CRM configura a automacao; nao a executa (RN-CRM-07).
-- =========================================================

CREATE TABLE configuracao_automacao (
    chave             VARCHAR(100) PRIMARY KEY,
    valor             TEXT NOT NULL,
    unidade           VARCHAR(30),
    tipo              VARCHAR(20) NOT NULL,
    valor_min         NUMERIC,
    valor_max         NUMERIC,
    descricao         TEXT,
    atualizado_por_id UUID REFERENCES usuario(id),
    atualizado_em     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_configuracao_automacao_tipo
        CHECK (tipo IN ('INT', 'DECIMAL', 'BOOLEAN', 'TEXT')),
    CONSTRAINT ck_configuracao_automacao_faixa
        CHECK (valor_min IS NULL OR valor_max IS NULL OR valor_min <= valor_max)
);
COMMENT ON COLUMN configuracao_automacao.valor_min IS
    'Faixa validada no painel: parametro de tempo com valor absurdo derruba a operacao em silencio.';

CREATE TABLE regra_follow_up (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome          VARCHAR(100) NOT NULL,
    tempo_minutos INT NOT NULL CHECK (tempo_minutos > 0),
    texto         TEXT NOT NULL,
    ativo         BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE regra_fidelizacao (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dias_sem_contato INT NOT NULL CHECK (dias_sem_contato > 0),
    mensagem         TEXT NOT NULL,
    ativo            BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE mensagem_festiva (
    id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    data  DATE NOT NULL,
    texto TEXT NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE configuracao_resumo_ia (
    id                   SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    ativo                BOOLEAN NOT NULL DEFAULT TRUE,
    gatilho              gatilho_resumo NOT NULL DEFAULT 'AO_FINALIZAR',
    quantidade_mensagens INT
);
COMMENT ON TABLE configuracao_resumo_ia IS 'Singleton: uma linha, garantida pelo CHECK (id = 1).';

CREATE TABLE status_automacao_telemetria (
    id                      SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    mensagens_enviadas      BIGINT NOT NULL DEFAULT 0,
    clientes_transferidos   BIGINT NOT NULL DEFAULT 0,
    conexao_automacao_ativa BOOLEAN NOT NULL DEFAULT FALSE,
    crm_online              BOOLEAN NOT NULL DEFAULT TRUE,
    atualizado_em           TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE status_automacao_telemetria IS 'Singleton: snapshot mais recente da telemetria.';
