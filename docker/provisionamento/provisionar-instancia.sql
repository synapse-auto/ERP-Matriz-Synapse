\set ON_ERROR_STOP on

\if :{?admin_nome}
\else
  \echo 'ERRO: parametro admin_nome ausente.'
  \quit
\endif
\if :{?admin_email}
\else
  \echo 'ERRO: parametro admin_email ausente.'
  \quit
\endif
\if :{?admin_senha_hash}
\else
  \echo 'ERRO: parametro admin_senha_hash ausente.'
  \quit
\endif
\if :{?etapas_json}
\else
  \echo 'ERRO: parametro etapas_json ausente.'
  \quit
\endif
\if :{?tags_json}
\else
  \echo 'ERRO: parametro tags_json ausente.'
  \quit
\endif
\if :{?automacao_json}
\else
  \echo 'ERRO: parametro automacao_json ausente.'
  \quit
\endif

BEGIN;

-- Um e-mail e o identificador estavel do administrador. Reexecutar atualiza
-- dados e hash, sem criar outro admin nem duplicar usuario.
INSERT INTO usuario (id, nome, email, senha_hash, papel, ativo)
VALUES (gen_random_uuid(), :'admin_nome', :'admin_email', :'admin_senha_hash', 'ADMINISTRADOR', TRUE)
ON CONFLICT (email) DO UPDATE
    SET nome = EXCLUDED.nome,
        senha_hash = EXCLUDED.senha_hash,
        papel = EXCLUDED.papel,
        ativo = TRUE;

-- JSON mantem quantidade de etapas livre por filho sem transformar este PAI
-- em uma lista fixa de parametros. Nao removemos etapas antigas: podem estar
-- referenciadas por leads historicos.
WITH etapas AS (
    SELECT nome, ordem, cor_visual
      FROM jsonb_to_recordset(:'etapas_json'::jsonb)
           AS entrada(nome TEXT, ordem SMALLINT, cor_visual TEXT)
)
INSERT INTO etapa_atendimento (id, nome, ordem, cor_visual)
SELECT gen_random_uuid(), nome, ordem, cor_visual
  FROM etapas
ON CONFLICT (ordem) DO UPDATE
    SET nome = EXCLUDED.nome,
        cor_visual = EXCLUDED.cor_visual;

WITH tags AS (
    SELECT nome, cor, icone
      FROM jsonb_to_recordset(:'tags_json'::jsonb)
           AS entrada(nome TEXT, cor TEXT, icone TEXT)
)
INSERT INTO tag (id, nome, cor, icone)
SELECT gen_random_uuid(), nome, cor, icone
  FROM tags
ON CONFLICT (nome) DO UPDATE
    SET cor = EXCLUDED.cor,
        icone = EXCLUDED.icone;

-- A tabela temporaria faz a validacao acontecer antes de tocar a configuracao
-- real. Valores numericos fora da faixa e tipos incoerentes interrompem a
-- transacao em vez de criar uma Automacao silenciosamente invalida.
CREATE TEMP TABLE _provisionamento_configuracao (
    chave TEXT NOT NULL,
    valor TEXT NOT NULL,
    unidade TEXT,
    tipo TEXT NOT NULL,
    valor_min NUMERIC,
    valor_max NUMERIC,
    descricao TEXT,
    CONSTRAINT ck_provisionamento_tipo CHECK (tipo IN ('INT', 'DECIMAL', 'BOOLEAN', 'TEXT')),
    CONSTRAINT ck_provisionamento_faixa CHECK (
        valor_min IS NULL OR valor_max IS NULL OR valor_min <= valor_max
    ),
    CONSTRAINT ck_provisionamento_valor CHECK (
        CASE
            WHEN tipo = 'INT' THEN valor ~ '^-?[0-9]+$'
            WHEN tipo = 'DECIMAL' THEN valor ~ '^-?[0-9]+([.][0-9]+)?$'
            WHEN tipo = 'BOOLEAN' THEN lower(valor) IN ('true', 'false')
            ELSE TRUE
        END
    ),
    CONSTRAINT ck_provisionamento_valor_na_faixa CHECK (
        CASE
            WHEN tipo IN ('INT', 'DECIMAL') THEN
                valor::NUMERIC >= COALESCE(valor_min, valor::NUMERIC)
                AND valor::NUMERIC <= COALESCE(valor_max, valor::NUMERIC)
            ELSE TRUE
        END
    ),
    CONSTRAINT ck_provisionamento_sem_faixa_textual CHECK (
        tipo NOT IN ('BOOLEAN', 'TEXT') OR (valor_min IS NULL AND valor_max IS NULL)
    )
) ON COMMIT DROP;

INSERT INTO _provisionamento_configuracao
    (chave, valor, unidade, tipo, valor_min, valor_max, descricao)
SELECT chave, valor, unidade, tipo, valor_min, valor_max, descricao
  FROM jsonb_to_recordset(:'automacao_json'::jsonb)
       AS entrada(
           chave TEXT,
           valor TEXT,
           unidade TEXT,
           tipo TEXT,
           valor_min NUMERIC,
           valor_max NUMERIC,
           descricao TEXT
       );

SELECT count(*) = 3 AS provisionamento_midia_completa
  FROM _provisionamento_configuracao
 WHERE chave IN (
     'anexo.tamanho_maximo_imagem_mb',
     'anexo.tamanho_maximo_audio_mb',
     'anexo.tamanho_maximo_documento_mb'
 )
\gset

\if :provisionamento_midia_completa
\else
  \echo 'ERRO: automacao_json precisa conter os tres limites de midia da Meta.'
  \quit
\endif

INSERT INTO configuracao_automacao
    (chave, valor, unidade, tipo, valor_min, valor_max, descricao)
SELECT chave, valor, unidade, tipo, valor_min, valor_max, descricao
  FROM _provisionamento_configuracao
ON CONFLICT (chave) DO UPDATE
    SET valor = EXCLUDED.valor,
        unidade = EXCLUDED.unidade,
        tipo = EXCLUDED.tipo,
        valor_min = EXCLUDED.valor_min,
        valor_max = EXCLUDED.valor_max,
        descricao = EXCLUDED.descricao,
        atualizado_em = now();

COMMIT;
\echo 'Provisionamento concluido: administrador, etapas, tags e configuracao da Automacao reconciliados.'
