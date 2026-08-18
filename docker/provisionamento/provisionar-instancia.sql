\set ON_ERROR_STOP on

\if :{?admin_nome}
\else
  \echo 'ERRO: parametro admin_nome ausente.'
  DO $$ BEGIN RAISE EXCEPTION 'provisionamento interrompido'; END $$;
\endif
\if :{?admin_email}
\else
  \echo 'ERRO: parametro admin_email ausente.'
  DO $$ BEGIN RAISE EXCEPTION 'provisionamento interrompido'; END $$;
\endif
\if :{?admin_senha_hash}
\else
  \echo 'ERRO: parametro admin_senha_hash ausente.'
  DO $$ BEGIN RAISE EXCEPTION 'provisionamento interrompido'; END $$;
\endif
\if :{?etapas_json}
\else
  \echo 'ERRO: parametro etapas_json ausente.'
  DO $$ BEGIN RAISE EXCEPTION 'provisionamento interrompido'; END $$;
\endif
\if :{?tags_json}
\else
  \echo 'ERRO: parametro tags_json ausente.'
  DO $$ BEGIN RAISE EXCEPTION 'provisionamento interrompido'; END $$;
\endif
\if :{?automacao_json}
\else
  \echo 'ERRO: parametro automacao_json ausente.'
  DO $$ BEGIN RAISE EXCEPTION 'provisionamento interrompido'; END $$;
\endif
\if :{?whatsapp_numero}
\else
  \echo 'ERRO: parametro whatsapp_numero ausente.'
  DO $$ BEGIN RAISE EXCEPTION 'provisionamento interrompido'; END $$;
\endif
\if :{?whatsapp_provedor}
\else
  \echo 'ERRO: parametro whatsapp_provedor ausente.'
  DO $$ BEGIN RAISE EXCEPTION 'provisionamento interrompido'; END $$;
\endif

-- Phone Number ID nao e o numero telefonico exibido: e o identificador numerico da Meta.
-- Recusar em vez de normalizar impede um valor errado de virar vazio e deixar o webhook
-- silenciosamente fechado depois do provisionamento.
SELECT :'whatsapp_numero' ~ '^[0-9]+$' AS whatsapp_phone_number_id_valido
\gset

\if :whatsapp_phone_number_id_valido
\else
  \echo 'ERRO: whatsapp_numero deve ser o Phone Number ID numerico da Meta.'
  DO $$ BEGIN RAISE EXCEPTION 'provisionamento interrompido'; END $$;
\endif

SELECT :'whatsapp_numero' AS whatsapp_phone_number_id
\gset

BEGIN;

-- Um e-mail e o identificador estavel do administrador. Reexecutar atualiza
-- dados e hash, sem criar outro admin nem duplicar usuario.
--
-- E31b: senha_alterada_em (E29) so volta a NULL quando o hash gravado REALMENTE
-- muda. Reexecutar o provisionamento para reconciliar canal/etapas/flags e comum
-- (a operacao faz isso), e nao pode forcar o administrador a trocar de senha a
-- toa; mas um SYNAPSE_ADMIN_SENHA_HASH novo significa que alguem redefiniu a
-- senha por fora do produto, e o dono nao a escolheu — o sistema tem que tratar
-- como provisoria de novo, senao o bloqueio de primeiro acesso da E29 nunca
-- dispara para quem entra pela porta do provisionamento. IS DISTINCT FROM trata
-- NULL corretamente (nunca e o caso aqui, mas evita a armadilha de NULL = NULL
-- ser desconhecido em vez de verdadeiro).
INSERT INTO usuario (id, nome, email, senha_hash, papel, ativo)
VALUES (gen_random_uuid(), :'admin_nome', :'admin_email', :'admin_senha_hash', 'ADMINISTRADOR', TRUE)
ON CONFLICT (email) DO UPDATE
    SET nome = EXCLUDED.nome,
        senha_hash = EXCLUDED.senha_hash,
        papel = EXCLUDED.papel,
        ativo = TRUE,
        senha_alterada_em = CASE
            WHEN usuario.senha_hash IS DISTINCT FROM EXCLUDED.senha_hash THEN NULL
            ELSE usuario.senha_alterada_em
        END;

-- O numero configurado no deploy e o Phone Number ID da Meta. A credencial
-- persiste apenas a referencia ao secret do ambiente; o token nunca entra no
-- SQL nem no banco. Reexecutar reconcilia o canal e mantem uma unica
-- credencial ativa, preservando as antigas como historico quando o numero muda.
INSERT INTO canal (id, nome, tipo, ativo)
VALUES (gen_random_uuid(), 'WhatsApp Principal', :'whatsapp_provedor', TRUE)
ON CONFLICT (nome) DO UPDATE
    SET tipo = EXCLUDED.tipo,
        ativo = TRUE
RETURNING id AS canal_whatsapp_id
\gset

UPDATE canal_credencial
   SET ativo = FALSE,
       vigente_ate = COALESCE(vigente_ate, now())
 WHERE canal_id = :'canal_whatsapp_id'::uuid
   AND ativo;

UPDATE canal_credencial
   SET numero = :'whatsapp_phone_number_id',
       token_ref = 'env://WHATSAPP_TOKEN',
       ativo = TRUE,
       vigente_ate = NULL
 WHERE id = (
       SELECT id
         FROM canal_credencial
        WHERE canal_id = :'canal_whatsapp_id'::uuid
          AND identificador_externo = :'whatsapp_phone_number_id'
        ORDER BY vigente_desde DESC, id
        LIMIT 1
 );

INSERT INTO canal_credencial
    (id, canal_id, numero, identificador_externo, token_ref, ativo)
SELECT gen_random_uuid(), :'canal_whatsapp_id'::uuid, :'whatsapp_phone_number_id',
       :'whatsapp_phone_number_id', 'env://WHATSAPP_TOKEN', TRUE
 WHERE NOT EXISTS (
       SELECT 1
         FROM canal_credencial
        WHERE canal_id = :'canal_whatsapp_id'::uuid
          AND identificador_externo = :'whatsapp_phone_number_id'
          AND ativo
 );

-- JSON mantem quantidade de etapas livre por filho sem transformar este PAI
-- em uma lista fixa de parametros. Nao removemos etapas antigas: podem estar
-- referenciadas por leads historicos.
-- Limpar primeiro evita conflito transitorio quando uma instancia troca qual
-- etapa e GANHO; a transacao inteira continua atomica para os demais leitores.
UPDATE etapa_atendimento SET resultado = 'EM_ANDAMENTO' WHERE resultado = 'GANHO';

WITH etapas AS (
    SELECT nome, ordem, cor_visual, resultado
      FROM jsonb_to_recordset(:'etapas_json'::jsonb)
           AS entrada(nome TEXT, ordem SMALLINT, cor_visual TEXT, resultado TEXT)
)
INSERT INTO etapa_atendimento (id, nome, ordem, cor_visual, resultado)
SELECT gen_random_uuid(), nome, ordem, cor_visual,
       COALESCE(resultado, 'EM_ANDAMENTO')::resultado_etapa
  FROM etapas
ON CONFLICT (ordem) DO UPDATE
    SET nome = EXCLUDED.nome,
        cor_visual = EXCLUDED.cor_visual,
        resultado = EXCLUDED.resultado;

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

SELECT count(*) = 4 AS provisionamento_midia_completa
  FROM _provisionamento_configuracao
 WHERE chave IN (
     'anexo.tamanho_maximo_imagem_mb',
     'anexo.tamanho_maximo_audio_mb',
     'anexo.tamanho_maximo_documento_mb',
     'gravacao_audio.duracao_maxima_segundos'
 )
\gset

\if :provisionamento_midia_completa
\else
  \echo 'ERRO: automacao_json precisa conter os tres limites de midia e a duracao maxima de gravacao.'
  DO $$ BEGIN RAISE EXCEPTION 'provisionamento interrompido'; END $$;
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

-- A Visao Geral voltou ao escopo da primeira entrega na E20. Provisionar uma
-- instancia precisa habilitar a rota no menu; regras de autorizacao continuam
-- restringindo seu conteudo a gestor e subgestor.
INSERT INTO feature_flag (chave, habilitado, descricao)
VALUES ('dashboard', TRUE, 'Dashboard de indicadores.')
ON CONFLICT (chave) DO UPDATE
    SET habilitado = TRUE,
        descricao = EXCLUDED.descricao;

COMMIT;
\echo 'Provisionamento concluido: administrador, canal, etapas, tags, Dashboard e configuracao da Automacao reconciliados.'
