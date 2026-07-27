-- =========================================================
-- Modulo crm-core: lead, tags, lembretes, mensagens programadas,
-- timeline, preferencias e banco de arquivos.
-- =========================================================

CREATE TABLE lead (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome                     VARCHAR(150) NOT NULL,
    foto_url                 TEXT,
    telefone                 VARCHAR(30),
    email                    VARCHAR(200),
    cpf                      VARCHAR(14),
    empresa                  VARCHAR(150),
    localizacao              VARCHAR(200),
    canal_origem_id          UUID REFERENCES canal(id),
    status_basico            status_basico_lead NOT NULL DEFAULT 'IA',
    etapa_atendimento_id     UUID REFERENCES etapa_atendimento(id),
    atendente_responsavel_id UUID REFERENCES usuario(id),
    notas                    TEXT,
    resumo_ia                TEXT,
    num_atendimentos         INT NOT NULL DEFAULT 0,
    num_mensagens            INT NOT NULL DEFAULT 0,
    criado_em                TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON COLUMN lead.num_atendimentos IS
    'Contador denormalizado (RF-CRM-71): incrementado na mesma transacao que cria o atendimento.';
COMMENT ON COLUMN lead.num_mensagens IS
    'Contador denormalizado (RF-CRM-71): evita COUNT(*) ao abrir a ficha do lead.';

CREATE TABLE tag (
    id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome  VARCHAR(60) NOT NULL UNIQUE,
    cor   VARCHAR(20) NOT NULL,
    icone VARCHAR(60)
);

CREATE TABLE lead_tag (
    lead_id UUID NOT NULL REFERENCES lead(id) ON DELETE CASCADE,
    tag_id  UUID NOT NULL REFERENCES tag(id) ON DELETE CASCADE,
    PRIMARY KEY (lead_id, tag_id)
);

CREATE TABLE lembrete (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lead_id           UUID REFERENCES lead(id) ON DELETE CASCADE,
    atendente_id      UUID NOT NULL REFERENCES usuario(id),
    texto             TEXT NOT NULL,
    data_hora         TIMESTAMPTZ NOT NULL,
    origem_automatica BOOLEAN NOT NULL DEFAULT FALSE,
    status            status_lembrete NOT NULL DEFAULT 'PENDENTE'
);

CREATE TABLE mensagem_programada (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lead_id      UUID NOT NULL REFERENCES lead(id) ON DELETE CASCADE,
    atendente_id UUID NOT NULL REFERENCES usuario(id),
    conteudo     TEXT NOT NULL,
    data_envio   TIMESTAMPTZ NOT NULL,
    status       status_msg_prog NOT NULL DEFAULT 'AGENDADA'
);

CREATE TABLE mensagem_rapida (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    atendente_id  UUID NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    palavra_chave VARCHAR(60) NOT NULL,
    conteudo      TEXT NOT NULL,
    tipo_midia    VARCHAR(30)
);

CREATE TABLE evento_timeline (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lead_id        UUID NOT NULL REFERENCES lead(id) ON DELETE CASCADE,
    atendimento_id UUID,
    tipo           VARCHAR(60) NOT NULL,
    descricao      TEXT NOT NULL,
    origem         origem_evento NOT NULL,
    criado_em      TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE evento_timeline IS
    'Append-only (RNF-CRM-10): apenas INSERT. A FK de atendimento_id entra na V5.';

CREATE TABLE preferencia_usuario (
    usuario_id UUID NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    chave      VARCHAR(80) NOT NULL,
    valor      TEXT NOT NULL,
    PRIMARY KEY (usuario_id, chave)
);

CREATE TABLE arquivo_banco (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome                VARCHAR(200) NOT NULL,
    tipo                VARCHAR(50) NOT NULL,
    url                 TEXT NOT NULL,
    tamanho_bytes       BIGINT,
    descricao_metadados TEXT,
    enviado_por_id      UUID REFERENCES usuario(id),
    criado_em           TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON COLUMN arquivo_banco.url IS 'Referencia ao objeto no S3/MinIO, nunca o binario.';

-- ---------------------------------------------------------
-- Constraint de unicidade (regra de negocio, nao performance) — ver V3.
-- ---------------------------------------------------------

-- A palavra-chave e o atalho que o atendente digita no chat. Duas iguais para
-- o mesmo atendente tornariam a expansao ambigua.
CREATE UNIQUE INDEX idx_msg_rapida_atendente_chave
    ON mensagem_rapida (atendente_id, palavra_chave);
