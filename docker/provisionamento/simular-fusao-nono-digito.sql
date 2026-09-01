-- =========================================================
-- E111 — simulacao da V50 (nono digito). SOMENTE LEITURA.
--
-- Mostra exatamente o que a migration V50 faria com os dados desta instancia, sem alterar nada.
-- Roda ANTES do deploy; o resultado e a base da autorizacao para faze-lo.
--
-- Como e somente-leitura, mesmo criando funcao: tudo acontece dentro de uma transacao encerrada por
-- ROLLBACK, e no Postgres DDL e transacional. As funcoes criadas aqui desaparecem junto com a
-- transacao — se a V50 ja tiver sido aplicada, o CREATE OR REPLACE tambem e desfeito e as definicoes
-- em producao ficam como estavam.
--
-- A saida e o UNICO registro do que a migration descarta: o nome do lead perdedor, e todo campo que
-- ele tinha preenchido e o sobrevivente tambem. Guarde a saida antes de autorizar o deploy.
--
-- Uso (o DDI vem do mesmo TELEFONE_DDI_PADRAO que a aplicacao usa; 55 e o default):
--
--   psql "$SYNAPSE_DB_URL" -v ddi=55 -f docker/provisionamento/simular-fusao-nono-digito.sql
--
-- =========================================================
\set ON_ERROR_STOP on
\pset pager off
\pset border 2

\if :{?ddi}
\else
  \set ddi 55
\endif

BEGIN;

-- lead, atendimento, lembrete e mensagem_programada tem FORCE ROW LEVEL SECURITY (V12). Sem
-- contexto a politica nega tudo e esta simulacao mostraria "nenhum par" num banco cheio de pares.
-- SERVICO e o mesmo papel que a V50 assume.
SELECT set_config('app.papel', 'SERVICO', TRUE);

DO $$
BEGIN
    IF NOT app_enxerga_todos_os_leads() THEN
        RAISE EXCEPTION 'contexto de servico nao aplicado: esta simulacao enxergaria zero leads';
    END IF;
END $$;

-- >>> regra-do-nono-digito >>>
-- Definicao unica da regra em SQL. O bloco entre estes marcadores e copiado literalmente em
-- docker/provisionamento/simular-fusao-nono-digito.sql, e RegraDoNonoDigitoCompartilhadaTest
-- reprova o build se os dois textos divergirem em um caractere.
--
-- A contraparte Java e TelefoneCanonico; quem prova que as duas concordam e
-- TelefoneNonoDigitoIT.Paridade, que roda a mesma tabela de casos nas duas implementacoes.

-- Somente digitos, mais o DDI da instancia quando o numero veio local. Espelha as duas primeiras
-- etapas de TelefoneCanonico.normalizar. NULL significa "sem telefone ou curto demais para ser um":
-- onde o Java levanta TelefoneInvalidoException, aqui devolve NULL.
CREATE OR REPLACE FUNCTION app_telefone_com_ddi(entrada TEXT, ddi_padrao TEXT)
RETURNS TEXT LANGUAGE sql IMMUTABLE AS $$
    SELECT CASE
               WHEN digitos IS NULL THEN NULL
               WHEN length(digitos) < 10 THEN NULL
               WHEN length(digitos) IN (10, 11) THEN ddi_padrao || digitos
               ELSE digitos
           END
      FROM (SELECT regexp_replace(entrada, '[^0-9]', '', 'g') AS digitos) AS limpo;
$$;

-- Assinante de oito digitos comecando em 6, 7, 8 ou 9 e celular que perdeu o nono digito: ganha o 9.
-- Comecando em 2, 3, 4 ou 5 e fixo e fica como esta. Nove digitos ja e canonico. Qualquer outro
-- tamanho, e qualquer DDI que nao seja o do Brasil, passa intacto.
--
-- A inferencia e segura porque no Brasil fixo nunca comeca em 6, 7, 8 ou 9, e desde 2016 todo
-- celular tem nove digitos comecando em 9. A regra e do pais, nao do cliente: ela olha o DDI do
-- proprio numero, entao um contato portugues na base de um filho brasileiro nao e tocado.
CREATE OR REPLACE FUNCTION app_telefone_canonico(entrada TEXT, ddi_padrao TEXT)
RETURNS TEXT LANGUAGE sql IMMUTABLE AS $$
    SELECT CASE
               WHEN com_ddi IS NULL THEN NULL
               WHEN length(com_ddi) = 12
                    AND left(com_ddi, 2) = '55'
                    AND substr(com_ddi, 5, 1) BETWEEN '6' AND '9'
                   THEN substr(com_ddi, 1, 4) || '9' || substr(com_ddi, 5)
               ELSE com_ddi
           END
      FROM (SELECT app_telefone_com_ddi(entrada, ddi_padrao) AS com_ddi) AS base;
$$;

-- Assinante de oito digitos comecando em 0 ou 1 nao existe no plano de numeracao brasileiro. A
-- regra nao diz o que fazer com ele e esta migration nao inventa: quem chama isto aborta.
CREATE OR REPLACE FUNCTION app_telefone_fora_da_regra(com_ddi TEXT)
RETURNS BOOLEAN LANGUAGE sql IMMUTABLE AS $$
    SELECT com_ddi IS NOT NULL
       AND length(com_ddi) = 12
       AND left(com_ddi, 2) = '55'
       AND substr(com_ddi, 5, 1) < '2';
$$;
-- <<< regra-do-nono-digito <<<

\echo ''
\echo '==========================================================================='
\echo ' E111 - simulacao da fusao por nono digito (nenhuma alteracao e gravada)'
\echo '==========================================================================='

SELECT :'ddi' AS ddi_padrao_usado,
       count(*) FILTER (WHERE telefone IS NOT NULL) AS leads_com_telefone,
       count(*) AS leads_no_total
  FROM lead;

-- ---------------------------------------------------------------------------
-- Base da simulacao: o mesmo recorte que a V50 usa.
-- ---------------------------------------------------------------------------
CREATE TEMP TABLE sim_lead AS
SELECT l.id,
       l.nome,
       l.telefone,
       l.criado_em,
       l.atendente_responsavel_id,
       u.nome AS dono,
       l.email,
       l.empresa,
       l.cpf,
       l.localizacao,
       l.codigo,
       l.notas,
       l.resumo_ia,
       l.foto_referencia,
       l.dados_customizados::text AS dados_customizados,
       l.num_atendimentos AS contador_atendimentos,
       l.num_mensagens AS contador_mensagens,
       l.ultima_interacao_em,
       app_telefone_com_ddi(l.telefone, :'ddi') AS com_ddi,
       app_telefone_canonico(l.telefone, :'ddi') AS canonico,
       (SELECT count(*) FROM atendimento a WHERE a.lead_id = l.id) AS atendimentos,
       (SELECT count(*)
          FROM mensagem m
          JOIN atendimento a ON a.id = m.atendimento_id
         WHERE a.lead_id = l.id) AS mensagens
  FROM lead l
  LEFT JOIN usuario u ON u.id = l.atendente_responsavel_id
 WHERE l.telefone IS NOT NULL;

CREATE TEMP TABLE sim_ordenado AS
SELECT s.*,
       row_number() OVER (
           PARTITION BY canonico
           ORDER BY mensagens DESC, atendimentos DESC, criado_em ASC, id ASC) AS posicao
  FROM sim_lead s
 WHERE canonico IN (SELECT canonico FROM sim_lead GROUP BY canonico HAVING count(*) = 2);

CREATE TEMP TABLE sim_par AS
SELECT s.canonico,
       s.id AS id_s, s.nome AS nome_s, s.telefone AS tel_s, s.dono AS dono_s,
       s.atendimentos AS at_s, s.mensagens AS msg_s, s.criado_em AS criado_s,
       s.contador_atendimentos AS cont_at_s, s.contador_mensagens AS cont_msg_s,
       s.email AS email_s, s.empresa AS empresa_s, s.cpf AS cpf_s,
       s.localizacao AS local_s, s.codigo AS codigo_s, s.notas AS notas_s,
       s.resumo_ia AS resumo_s, s.foto_referencia AS foto_s,
       s.dados_customizados AS custom_s, s.ultima_interacao_em AS interacao_s,
       p.id AS id_p, p.nome AS nome_p, p.telefone AS tel_p, p.dono AS dono_p,
       p.atendimentos AS at_p, p.mensagens AS msg_p, p.criado_em AS criado_p,
       p.contador_atendimentos AS cont_at_p, p.contador_mensagens AS cont_msg_p,
       p.email AS email_p, p.empresa AS empresa_p, p.cpf AS cpf_p,
       p.localizacao AS local_p, p.codigo AS codigo_p, p.notas AS notas_p,
       p.resumo_ia AS resumo_p, p.foto_referencia AS foto_p,
       p.dados_customizados AS custom_p, p.ultima_interacao_em AS interacao_p
  FROM sim_ordenado s
  JOIN sim_ordenado p ON p.canonico = s.canonico AND p.posicao = 2
 WHERE s.posicao = 1;

\echo ''
\echo '--- 1. CASOS QUE FARIAM A MIGRATION ABORTAR ------------------------------'
\echo '    Qualquer linha abaixo derruba o deploy. Vazio = a V50 roda ate o fim.'
\echo ''
\echo '1a. FK apontando para lead que a V50 nao preve'

SELECT conrelid::regclass AS tabela, conname AS nome_da_constraint
  FROM pg_constraint
 WHERE confrelid = 'lead'::regclass
   AND contype = 'f'
   AND conrelid::regclass::text <> ALL (ARRAY[
        'atendimento', 'campanha_mensagem_metrica', 'evento_timeline',
        'lead_tag', 'lembrete', 'mensagem_programada'])
 ORDER BY 1;

\echo ''
\echo '1b. FK que a V50 espera e que sumiu do schema'

SELECT esperada AS tabela_ausente
  FROM unnest(ARRAY[
        'atendimento', 'campanha_mensagem_metrica', 'evento_timeline',
        'lead_tag', 'lembrete', 'mensagem_programada']) AS esperada
 WHERE NOT EXISTS (
           SELECT 1 FROM pg_constraint
            WHERE confrelid = 'lead'::regclass
              AND contype = 'f'
              AND conrelid::regclass::text = esperada)
 ORDER BY 1;

\echo ''
\echo '1c. Telefone fora dos formatos do Bloco 1'

SELECT id, nome, telefone,
       CASE WHEN telefone !~ '^[0-9]+$' THEN 'nao e somente digitos'
            WHEN com_ddi IS NULL THEN 'curto demais para ser telefone'
            ELSE 'assinante de 8 digitos comecando em 0 ou 1' END AS motivo
  FROM sim_lead
 WHERE telefone !~ '^[0-9]+$'
    OR com_ddi IS NULL
    OR app_telefone_fora_da_regra(com_ddi)
 ORDER BY id;

\echo ''
\echo '1d. Mais de dois leads no mesmo telefone canonico'

SELECT canonico,
       count(*) AS quantos,
       string_agg(format('%s | %s | %s', id, nome, telefone), '; ' ORDER BY id) AS leads
  FROM sim_lead
 GROUP BY canonico
HAVING count(*) > 2
 ORDER BY canonico;

\echo ''
\echo '1e. Metrica de campanha existente nos dois lados do par'

SELECT par.canonico, par.id_s AS sobrevivente, par.id_p AS perdedor,
       count(*) AS metricas_em_conflito
  FROM sim_par par
  JOIN campanha_mensagem_metrica perdida ON perdida.lead_id = par.id_p
  JOIN campanha_mensagem_metrica mantida
    ON mantida.lead_id = par.id_s
   AND mantida.campanha_mensagem_id = perdida.campanha_mensagem_id
 GROUP BY 1, 2, 3
 ORDER BY 1;

\echo ''
\echo '1f. Colisao que sobraria DEPOIS da fusao (a V50 aborta antes de normalizar)'

SELECT canonico,
       count(*) AS quantos,
       string_agg(format('%s | %s | %s', id, nome, telefone), '; ' ORDER BY id) AS leads
  FROM sim_lead
 WHERE id NOT IN (SELECT id_p FROM sim_par)
 GROUP BY canonico
HAVING count(*) > 1
 ORDER BY canonico;

\echo ''
\echo '--- 2. PARES QUE SERIAM FUNDIDOS -----------------------------------------'
\echo '    Sobrevive quem tem a conversa: mais mensagens, empate mais atendimentos,'
\echo '    empate o mais antigo. O dono e o do sobrevivente. O nome NAO e fundido.'
\echo ''

SELECT canonico,
       format('%s | %s | dono %s | %s at | %s msg',
              id_s, nome_s, coalesce(dono_s, '(sem dono)'), at_s, msg_s) AS sobrevivente,
       format('%s | %s | dono %s | %s at | %s msg',
              id_p, nome_p, coalesce(dono_p, '(sem dono)'), at_p, msg_p) AS perdedor_apagado,
       tel_s AS telefone_do_sobrevivente,
       tel_p AS telefone_do_perdedor,
       CASE WHEN msg_s <> msg_p THEN 'mais mensagens'
            WHEN at_s <> at_p THEN 'mais atendimentos'
            WHEN criado_s <> criado_p THEN 'mais antigo'
            ELSE 'empate total; desempate pelo id' END AS criterio
  FROM sim_par
 ORDER BY canonico;

\echo ''
\echo '2b. Contadores desnormalizados x contagem real (deriva existente, se houver)'

SELECT canonico,
       id_s AS sobrevivente,
       cont_at_s AS contador_at_s, at_s AS real_at_s,
       cont_msg_s AS contador_msg_s, msg_s AS real_msg_s,
       cont_at_p AS contador_at_p, at_p AS real_at_p,
       cont_msg_p AS contador_msg_p, msg_p AS real_msg_p,
       cont_at_s + cont_at_p AS at_apos_fusao,
       cont_msg_s + cont_msg_p AS msg_apos_fusao
  FROM sim_par
 ORDER BY canonico;

\echo ''
\echo '--- 3. CAMPOS: O QUE SERIA PREENCHIDO E O QUE SERIA DESCARTADO -----------'
\echo '    Esta e a unica vez que os valores descartados aparecem. Guarde a saida.'
\echo ''

SELECT par.canonico,
       campo.nome_do_campo,
       campo.no_sobrevivente,
       campo.no_perdedor,
       CASE
           WHEN NOT campo.fundido
               THEN 'NAO FUNDIDO: o valor do perdedor some com ele'
           WHEN nullif(campo.no_sobrevivente, '') IS NULL
               THEN 'PREENCHE com o valor do perdedor'
           ELSE 'DESCARTA o valor do perdedor'
       END AS decisao
  FROM sim_par par
  CROSS JOIN LATERAL (VALUES
        ('email',              TRUE,  par.email_s,   par.email_p),
        ('empresa',            TRUE,  par.empresa_s, par.empresa_p),
        ('cpf',                TRUE,  par.cpf_s,     par.cpf_p),
        ('localizacao',        TRUE,  par.local_s,   par.local_p),
        ('codigo',             TRUE,  par.codigo_s,  par.codigo_p),
        ('nome',               FALSE, par.nome_s,    par.nome_p),
        ('notas',              FALSE, par.notas_s,   par.notas_p),
        ('resumo_ia',          FALSE, par.resumo_s,  par.resumo_p),
        ('foto_referencia',    FALSE, par.foto_s,    par.foto_p),
        ('dados_customizados', FALSE, par.custom_s,  par.custom_p)
      ) AS campo(nome_do_campo, fundido, no_sobrevivente, no_perdedor)
 WHERE nullif(campo.no_perdedor, '') IS NOT NULL
   AND campo.no_sobrevivente IS DISTINCT FROM campo.no_perdedor
 ORDER BY par.canonico, campo.nome_do_campo;

\echo ''
\echo '3b. Janela de 24h: ultima_interacao_em depois da fusao (GREATEST dos dois)'

SELECT canonico, id_s AS sobrevivente, interacao_s, interacao_p,
       GREATEST(interacao_s, interacao_p) AS apos_fusao
  FROM sim_par
 WHERE interacao_p IS NOT NULL
 ORDER BY canonico;

\echo ''
\echo '--- 4. LINHAS QUE MUDAM DE DONO NA FUSAO ---------------------------------'
\echo ''

SELECT par.canonico, par.id_p AS perdedor, origem.tabela, origem.linhas
  FROM sim_par par
  CROSS JOIN LATERAL (VALUES
        ('atendimento',
         (SELECT count(*) FROM atendimento t WHERE t.lead_id = par.id_p)),
        ('mensagem (via atendimento)',
         (SELECT count(*) FROM mensagem m JOIN atendimento a ON a.id = m.atendimento_id
           WHERE a.lead_id = par.id_p)),
        ('evento_timeline',
         (SELECT count(*) FROM evento_timeline t WHERE t.lead_id = par.id_p)),
        ('lembrete',
         (SELECT count(*) FROM lembrete t WHERE t.lead_id = par.id_p)),
        ('mensagem_programada',
         (SELECT count(*) FROM mensagem_programada t WHERE t.lead_id = par.id_p)),
        ('lead_tag',
         (SELECT count(*) FROM lead_tag t WHERE t.lead_id = par.id_p)),
        ('campanha_mensagem_metrica',
         (SELECT count(*) FROM campanha_mensagem_metrica t WHERE t.lead_id = par.id_p)),
        ('audit_log (sem FK, coluna de filtro)',
         (SELECT count(*) FROM audit_log t WHERE t.lead_id = par.id_p))
      ) AS origem(tabela, linhas)
 WHERE origem.linhas > 0
 ORDER BY par.canonico, origem.tabela;

\echo ''
\echo '--- 4b. ATENDIMENTOS: QUAL FICA ABERTO E QUAIS SERAO FINALIZADOS ---------'
\echo '    Depois de mover os atendimentos do perdedor para o sobrevivente, so um'
\echo '    nao-finalizado permanece aberto. Fica o de mais mensagens; empate, o'
\echo '    mais antigo; empate, o menor id. Os demais viram FINALIZADO (nada e'
\echo '    apagado). Esta e a lista que a gestao aprova antes do deploy.'
\echo ''

CREATE TEMP TABLE sim_atendimento_aberto_do_par AS
SELECT par.canonico,
       par.nome_s AS cliente,
       a.id AS atendimento_id,
       coalesce(u.nome, '(sem dono)') AS atendente,
       a.iniciado_em,
       (SELECT count(*) FROM mensagem m WHERE m.atendimento_id = a.id) AS mensagens,
       row_number() OVER (
           PARTITION BY par.canonico
           ORDER BY (SELECT count(*) FROM mensagem m WHERE m.atendimento_id = a.id) DESC,
                    a.iniciado_em ASC,
                    a.id ASC) AS posicao
  FROM sim_par par
  JOIN atendimento a ON a.lead_id IN (par.id_s, par.id_p)
                    AND a.status <> 'FINALIZADO'
  LEFT JOIN usuario u ON u.id = a.atendente_id;

\echo 'Ficam abertos (um por par, quando ha atendimento aberto):'
\echo ''

SELECT canonico,
       cliente,
       atendente,
       mensagens,
       iniciado_em,
       atendimento_id
  FROM sim_atendimento_aberto_do_par
 WHERE posicao = 1
 ORDER BY canonico;

\echo ''
\echo 'Serao finalizados (historico permanece no cliente; a conversa some da aba'
\echo 'Todos de quem so participava, porque a participacao e encerrada):'
\echo ''

SELECT canonico,
       cliente,
       atendente,
       mensagens,
       iniciado_em,
       atendimento_id
  FROM sim_atendimento_aberto_do_par
 WHERE posicao > 1
 ORDER BY canonico, mensagens ASC, iniciado_em ASC;

\echo ''
\echo '--- 5. LEADS NORMALIZADOS SEM FUSAO --------------------------------------'
\echo ''

SELECT count(*) AS leads_que_ganham_o_nono_digito_sem_fusao
  FROM sim_lead
 WHERE id NOT IN (SELECT id_s FROM sim_par UNION ALL SELECT id_p FROM sim_par)
   AND telefone IS DISTINCT FROM canonico;

SELECT count(*) AS leads_com_telefone_ja_canonico
  FROM sim_lead
 WHERE telefone = canonico;

\echo ''
\echo '--- 6. RESUMO ------------------------------------------------------------'
\echo ''

SELECT (SELECT count(*) FROM sim_par) AS pares_a_fundir,
       (SELECT count(*) FROM sim_par) AS leads_a_apagar,
       (SELECT count(*) FROM sim_lead WHERE telefone IS DISTINCT FROM canonico)
           AS telefones_a_normalizar,
       (SELECT count(*) FROM sim_atendimento_aberto_do_par WHERE posicao > 1)
           AS atendimentos_a_finalizar,
       (SELECT count(*) FROM sim_lead
         WHERE telefone !~ '^[0-9]+$' OR com_ddi IS NULL OR app_telefone_fora_da_regra(com_ddi))
           AS casos_que_abortam;

\echo ''
\echo 'Nada foi gravado: a transacao abaixo e desfeita.'
ROLLBACK;
