-- =========================================================
-- V103: setores (D2 da Disbrel, docs/71). Capacidade DESLIGADA em toda instancia.
--
-- Setor e dado cadastravel (Vendas, Suporte e Diretoria sao linhas, nao codigo). Esta migration nao cria nenhum
-- setor e nao altera nenhuma consulta existente: com a feature flag `setores` desligada, o sistema se comporta
-- exatamente como antes. Ligar a flag e operacao pontual por instancia
-- (docker/provisionamento/instancias/disbrel/habilitar-setores.sql), nunca por migration.
--
-- Um usuario pertence a NO MAXIMO um setor por ora. A regra mora num indice unico separado
-- (ux_usuario_setor_usuario) e nao na chave primaria: relaxar para "varios setores" e um DROP INDEX, sem reescrever
-- a tabela. Setor nao da acesso a lead nenhum: RN-CRM-01 e as politicas RLS nao leem estas tabelas.
-- =========================================================

CREATE TABLE setor (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nome              VARCHAR(80) NOT NULL,
    recebe_da_triagem BOOLEAN NOT NULL DEFAULT TRUE,
    ativo             BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em         TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_setor_nome CHECK (btrim(nome) <> '')
);

COMMENT ON COLUMN setor.recebe_da_triagem IS
    'FALSE para setores que nunca recebem lead da triagem da IA (ex.: Diretoria); so a distribuicao le este campo.';

-- Nome unico sem diferenciar caixa nem espacos nas pontas.
CREATE UNIQUE INDEX ux_setor_nome ON setor (lower(btrim(nome)));

CREATE TABLE usuario_setor (
    usuario_id       UUID NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    setor_id         UUID NOT NULL REFERENCES setor(id) ON DELETE RESTRICT,
    atribuido_em     TIMESTAMPTZ NOT NULL DEFAULT now(),
    atribuido_por_id UUID REFERENCES usuario(id) ON DELETE SET NULL,
    CONSTRAINT pk_usuario_setor PRIMARY KEY (usuario_id, setor_id)
);

-- "Um usuario, um setor": relaxavel com DROP INDEX ux_usuario_setor_usuario.
CREATE UNIQUE INDEX ux_usuario_setor_usuario ON usuario_setor (usuario_id);
-- Consulta por setor (contagem na tela e, depois, o filtro do rodizio).
CREATE INDEX idx_usuario_setor_setor ON usuario_setor (setor_id);

-- Mesmas tabelas sem RLS de `usuario` e `disponibilidade_atendente_ia`: nao guardam dado comercial de lead.
GRANT SELECT, INSERT, UPDATE ON setor TO synapse_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON usuario_setor TO synapse_app;

INSERT INTO feature_flag (chave, habilitado, descricao) VALUES
    ('setores', FALSE,
     'Setores (Vendas, Suporte, Diretoria...) e a atribuicao de usuarios a um setor na gestao de equipe.')
ON CONFLICT (chave) DO NOTHING;
