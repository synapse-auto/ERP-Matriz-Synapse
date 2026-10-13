-- Executar somente no banco da instancia explicitamente escolhida pelo operador (hoje: Disbrel).
-- Nao e migration Flyway e nenhum job a chama. Ver docs/71 (D3) e a V104 (chaves e tabela do vinculo).
--
-- O que faz: liga/desliga, por variavel, as chaves de distribuicao da Disbrel em configuracao_automacao.
-- NADA e alterado se a variavel nao for informada: sem -v, o script so MOSTRA o estado atual (ROLLBACK).
--
--   -v sequencial=true|false      ia.distribuicao.sequencial          (true = gira por quem recebeu ha mais tempo; Disbrel: true)
--   -v vinculado=true|false       ia.distribuicao.atendente_vinculado (true = o lead volta ao atendente vinculado se elegivel)
--   -v por_botoes=true|false      ia.distribuicao.por_botoes          (so guarda a escolha; quem age e a Automacao)
--   -v preferencia=true|false     ia.distribuicao.perguntar_preferencia (so guarda a escolha; quem age e a Automacao)
--   -v confirmar=APLICAR          sem isto o script termina em ROLLBACK (simulacao)
--
-- Os setores sao outra capacidade (feature flag `setores`, habilitar-setores.sql) e o filtro por setor so vale se ela estiver
-- ligada. Para cada chave o script mostra o valor antes e depois.
--
-- Uso (simulacao):   psql "$SYNAPSE_DB_URL" -v vinculado=true -f docker/provisionamento/instancias/disbrel/distribuicao-disbrel.sql
-- Uso (aplicacao):   psql "$SYNAPSE_DB_URL" -v vinculado=true -v sequencial=true -v confirmar=APLICAR -f .../distribuicao-disbrel.sql
-- Desfazer: rode de novo com o valor anterior (impresso na coluna "antes"), por exemplo -v vinculado=false.
-- Requer psql 13 ou superior.
\set ON_ERROR_STOP on
\pset pager off

\if :{?confirmar}
\else
  \set confirmar ''
\endif
SELECT :'confirmar' NOT IN ('', 'APLICAR') AS confirmar_invalido \gset
\if :confirmar_invalido
  \echo 'confirmar so aceita APLICAR (ou ausente, para simulacao). Nada foi feito.'
  \quit
\endif

BEGIN;

CREATE TEMP TABLE pedido (chave TEXT PRIMARY KEY, novo TEXT) ON COMMIT DROP;

\if :{?sequencial}
  INSERT INTO pedido VALUES ('ia.distribuicao.sequencial', :'sequencial');
\endif
\if :{?vinculado}
  INSERT INTO pedido VALUES ('ia.distribuicao.atendente_vinculado', :'vinculado');
\endif
\if :{?por_botoes}
  INSERT INTO pedido VALUES ('ia.distribuicao.por_botoes', :'por_botoes');
\endif
\if :{?preferencia}
  INSERT INTO pedido VALUES ('ia.distribuicao.perguntar_preferencia', :'preferencia');
\endif

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pedido WHERE novo NOT IN ('true', 'false')) THEN
        RAISE EXCEPTION 'os valores aceitos sao true ou false; nada aplicado';
    END IF;
    IF EXISTS (SELECT 1 FROM pedido p WHERE NOT EXISTS (SELECT 1 FROM configuracao_automacao c WHERE c.chave = p.chave)) THEN
        RAISE EXCEPTION 'chave ausente em configuracao_automacao: implante a versao com a V104 antes; nada aplicado';
    END IF;
END $$;

CREATE TEMP TABLE antes ON COMMIT DROP AS
SELECT chave, valor FROM configuracao_automacao WHERE chave LIKE 'ia.distribuicao.%';

UPDATE configuracao_automacao c
   SET valor = p.novo, atualizado_em = now()
  FROM pedido p
 WHERE c.chave = p.chave AND c.valor <> p.novo;

\echo ''
\echo '--- CHAVES DE DISTRIBUICAO (antes x depois) -------------------------------'
SELECT c.chave, a.valor AS antes, c.valor AS depois
  FROM configuracao_automacao c
  JOIN antes a ON a.chave = c.chave
 WHERE c.chave LIKE 'ia.distribuicao.%'
 ORDER BY c.chave;

SELECT :'confirmar' = 'APLICAR' AS confirmado \gset
\if :confirmado
  COMMIT;
  \echo 'APLICADO.'
\else
  ROLLBACK;
  \echo 'SIMULACAO: nada foi gravado. Para aplicar, repita com -v confirmar=APLICAR.'
\endif
