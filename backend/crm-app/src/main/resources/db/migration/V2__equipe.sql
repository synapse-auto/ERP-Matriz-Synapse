-- =========================================================
-- Modulo crm-equipe: usuarios, papeis, presenca e horarios.
-- Vem primeiro porque quase toda tabela do modelo referencia usuario.
-- =========================================================

CREATE TABLE usuario (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome            VARCHAR(150) NOT NULL,
    email           VARCHAR(200) NOT NULL UNIQUE,
    senha_hash      VARCHAR(255) NOT NULL,
    papel           papel_usuario NOT NULL,
    status_presenca status_presenca NOT NULL DEFAULT 'OFFLINE',
    ativo           BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- atendimento_id fica sem FK aqui e ganha a constraint na V5: a tabela
-- atendimento so passa a existir naquela migration.
CREATE TABLE avaliacao (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    atendimento_id UUID NOT NULL,
    atendente_id   UUID NOT NULL REFERENCES usuario(id),
    nota           SMALLINT NOT NULL CHECK (nota BETWEEN 1 AND 5),
    comentario     TEXT,
    criado_em      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE rotina_disponibilidade (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dia_semana dia_semana NOT NULL,
    nome       VARCHAR(100) NOT NULL,
    tipo       tipo_rotina NOT NULL,
    ativo      BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE rotina_disponibilidade_atendente (
    rotina_id    UUID NOT NULL REFERENCES rotina_disponibilidade(id) ON DELETE CASCADE,
    atendente_id UUID NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    PRIMARY KEY (rotina_id, atendente_id)
);

CREATE TABLE disponibilidade_atendente_ia (
    atendente_id       UUID PRIMARY KEY REFERENCES usuario(id) ON DELETE CASCADE,
    disponivel_para_ia BOOLEAN NOT NULL DEFAULT FALSE,
    atualizado_em      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE horario_trabalho (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aplicavel_a VARCHAR(20) NOT NULL,  -- 'IA' ou um valor de papel_usuario
    dia_semana  dia_semana NOT NULL,
    inicio      TIME NOT NULL,
    fim         TIME NOT NULL,
    CONSTRAINT ck_horario_trabalho_intervalo CHECK (fim > inicio)
);
