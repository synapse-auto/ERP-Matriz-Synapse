-- =========================================================
-- Disbrel: DIAGNOSTICO do funil (somente leitura).
--
-- Mostra, sem gravar nada, como o funil da instancia esta hoje em relacao ao desenho da Disbrel
-- (Novo Contato, Aguardando Medidas, Negociacao, Fechado = GANHO, Pos-venda): quais etapas existem,
-- quantos leads ha em cada uma, o que falta criar, o que diverge e o que ficaria "fora do desenho".
-- Rode ANTES de funil-disbrel.sql e guarde a saida.
--
-- Uso:
--   psql "$SYNAPSE_DB_URL" -f docker/provisionamento/instancias/disbrel/funil-disbrel-diagnostico.sql
--
-- Precisa de um papel que enxergue todos os leads (o mesmo dos demais scripts de provisionamento):
-- lead tem FORCE ROW LEVEL SECURITY e a contagem sob RLS seria parcial. Sem isso o script recusa.
-- Requer psql 13 ou superior.
-- =========================================================
\set ON_ERROR_STOP on
\pset pager off
\pset border 2

BEGIN;

SELECT set_config('app.papel', 'SERVICO', TRUE);

DO $$
BEGIN
    IF NOT app_enxerga_todos_os_leads() THEN
        RAISE EXCEPTION 'contexto de servico nao aplicado: a contagem de leads por etapa seria parcial';
    END IF;
END $$;

CREATE TEMP TABLE desenho (ordem SMALLINT, nome TEXT, resultado TEXT) ON COMMIT DROP;
INSERT INTO desenho VALUES
    (1, 'Novo Contato',       'EM_ANDAMENTO'),
    (2, 'Aguardando Medidas', 'EM_ANDAMENTO'),
    (3, 'Negociação',         'EM_ANDAMENTO'),
    (4, 'Fechado',            'GANHO'),
    (5, 'Pós-venda',          'EM_ANDAMENTO');

-- Comparacao por nome sem caixa, sem acento e com espacos colapsados: "Negociacao" casa com "Negociação".
CREATE TEMP VIEW etapa_normalizada AS
SELECT e.*,
       lower(btrim(regexp_replace(
           translate(e.nome, 'ÁÀÂÃÄáàâãäÉÈÊËéèêëÍÌÎÏíìîïÓÒÔÕÖóòôõöÚÙÛÜúùûüÇç',
                             'AAAAAaaaaaEEEEeeeeIIIIiiiiOOOOOoooooUUUUuuuuCc'),
           '\s+', ' ', 'g'))) AS chave
  FROM etapa_atendimento e;

CREATE TEMP VIEW desenho_normalizado AS
SELECT d.*,
       lower(btrim(regexp_replace(
           translate(d.nome, 'ÁÀÂÃÄáàâãäÉÈÊËéèêëÍÌÎÏíìîïÓÒÔÕÖóòôõöÚÙÛÜúùûüÇç',
                             'AAAAAaaaaaEEEEeeeeIIIIiiiiOOOOOoooooUUUUuuuuCc'),
           '\s+', ' ', 'g'))) AS chave
  FROM desenho d;

-- A partir daqui a transacao nao aceita nenhuma escrita (os temporarios ja foram criados).
SET LOCAL transaction_read_only = on;

\echo ''
\echo '--- 1. ETAPAS HOJE, COM LEADS ---------------------------------------------'
\echo ''

SELECT e.ordem, e.nome, e.resultado, e.cor_visual,
       count(l.id) AS leads,
       CASE WHEN d.chave IS NULL THEN 'fora do desenho' ELSE 'no desenho' END AS situacao
  FROM etapa_normalizada e
  LEFT JOIN lead l ON l.etapa_atendimento_id = e.id
  LEFT JOIN desenho_normalizado d ON d.chave = e.chave
 GROUP BY e.id, e.ordem, e.nome, e.resultado, e.cor_visual, d.chave
 ORDER BY e.ordem;

SELECT count(*) AS leads_sem_etapa FROM lead WHERE etapa_atendimento_id IS NULL;

\echo ''
\echo '--- 2. DESENHO DA DISBREL x INSTANCIA -------------------------------------'
\echo ''

SELECT d.ordem AS ordem_desejada, d.nome AS etapa_desejada, d.resultado AS resultado_desejado,
       CASE
           WHEN e.id IS NULL THEN 'AUSENTE: o script cria'
           WHEN e.resultado::text <> d.resultado THEN 'DIVERGE: resultado hoje ' || e.resultado || ' (o script recusa)'
           WHEN e.ordem <> d.ordem THEN 'EXISTE: o script ajusta a posicao (hoje ' || e.ordem || ')'
           ELSE 'OK'
       END AS situacao
  FROM desenho_normalizado d
  LEFT JOIN etapa_normalizada e ON e.chave = d.chave
 ORDER BY d.ordem;

\echo ''
\echo '--- 3. CONFLITOS QUE EXIGEM DECISAO ----------------------------------------'
\echo ''

-- GANHO e unico (idx_etapa_unica_ganha): se outra etapa ja e GANHO, "Fechado" so entra depois de um remapeamento.
SELECT e.nome AS etapa_que_ja_e_ganho, count(l.id) AS leads
  FROM etapa_normalizada e
  LEFT JOIN lead l ON l.etapa_atendimento_id = e.id
 WHERE e.resultado = 'GANHO'
   AND e.chave NOT IN (SELECT chave FROM desenho_normalizado WHERE resultado = 'GANHO')
 GROUP BY e.id, e.nome;

SELECT chave AS nome_normalizado_duplicado, count(*) AS etapas
  FROM etapa_normalizada
 GROUP BY chave
HAVING count(*) > 1;

SELECT max(ordem) AS maior_ordem_hoje,
       CASE WHEN max(ordem) >= 1000 THEN 'ATENCAO: o script recusa ordem >= 1000' ELSE 'ok' END AS veredito
  FROM etapa_atendimento;

\echo ''
\echo 'Diagnostico concluido; nada foi gravado.'

ROLLBACK;
