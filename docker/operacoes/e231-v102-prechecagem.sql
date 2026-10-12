-- E231 / V102 (#295) - pre-checagem SOMENTE LEITURA, para rodar nos tres bancos ANTES do deploy.
--
-- A V102 troca duas CHECK de `atendimento` (ADD CONSTRAINT sem NOT VALID: o Postgres valida TODAS as linhas sob
-- ACCESS EXCLUSIVE no momento da migration). Se alguma linha violar, o `ALTER` falha e o backend novo nao sobe
-- (o Swarm volta ao anterior). Este script responde, sem alterar nada, se isso pode acontecer e se ha transacao longa
-- que faria a migration esperar na fila de locks.
--
-- Uso (psql 13+, no banco de cada stack; o usuario so precisa de SELECT):
--   psql -X -d <banco> -v ON_ERROR_STOP=1 -f docker/operacoes/e231-v102-prechecagem.sql
-- Leia a ULTIMA tabela (veredito). Nada aqui escreve: BEGIN READ ONLY ... ROLLBACK.
\set ON_ERROR_STOP on
\pset pager off
BEGIN READ ONLY;
SET LOCAL statement_timeout = '30s';

\echo '== 1. As CHECK atuais (V97) existem e estao validadas? (convalidated deve ser t nas duas)'
SELECT conname, convalidated
  FROM pg_constraint
 WHERE conrelid = 'atendimento'::regclass
   AND conname IN ('ck_atendimento_origem_resultado_venda', 'ck_atendimento_estado_resultado_venda')
 ORDER BY conname;

\echo '== 2. Linhas que VIOLARIAM as CHECK da V102 (esperado: 0 e 0). Predicado copiado da V102; violar = resultar FALSE'
SELECT count(*) FILTER (WHERE (origem_resultado_venda IS NULL
                               OR origem_resultado_venda IN ('MANUAL', 'FINALIZACAO', 'AUTOMACAO')) IS FALSE)
           AS viola_origem,
       count(*) FILTER (WHERE ((resultado_venda IS NULL
                                AND valor_venda IS NULL
                                AND venda_registrada_por_id IS NULL
                                AND venda_registrada_em IS NULL
                                AND origem_resultado_venda IS NULL)
                            OR (resultado_venda IS NOT NULL
                                AND venda_registrada_em IS NOT NULL
                                AND origem_resultado_venda IS NOT NULL
                                AND ((origem_resultado_venda = 'AUTOMACAO' AND venda_registrada_por_id IS NULL)
                                  OR (origem_resultado_venda IN ('MANUAL', 'FINALIZACAO')
                                      AND venda_registrada_por_id IS NOT NULL)))) IS FALSE)
           AS viola_estado,
       count(*) AS linhas_em_atendimento
  FROM atendimento;

\echo '== 3. Transacoes abertas ha mais de 30 s neste banco (a migration entra na fila de locks atras delas)'
SELECT pid, state, now() - xact_start AS aberta_ha, left(query, 80) AS ultima_consulta
  FROM pg_stat_activity
 WHERE datname = current_database()
   AND pid <> pg_backend_pid()
   AND xact_start < now() - interval '30 seconds'
 ORDER BY xact_start;

\echo '== 4. Linhas com origem AUTOMACAO (so existem depois da #295 em uso; relevante para ROLLBACK de imagem, ver docs/72)'
SELECT count(*) AS linhas_origem_automacao FROM atendimento WHERE origem_resultado_venda = 'AUTOMACAO';

\echo '== VEREDITO (LIBERAR = nenhuma linha viola e as CHECK atuais estao validadas; a tabela 3 e olhada a olho)'
SELECT CASE
         WHEN (SELECT count(*) FROM atendimento
                WHERE (origem_resultado_venda IS NULL
                       OR origem_resultado_venda IN ('MANUAL', 'FINALIZACAO', 'AUTOMACAO')) IS FALSE
                   OR ((resultado_venda IS NULL AND valor_venda IS NULL AND venda_registrada_por_id IS NULL
                        AND venda_registrada_em IS NULL AND origem_resultado_venda IS NULL)
                    OR (resultado_venda IS NOT NULL AND venda_registrada_em IS NOT NULL
                        AND origem_resultado_venda IS NOT NULL
                        AND ((origem_resultado_venda = 'AUTOMACAO' AND venda_registrada_por_id IS NULL)
                          OR (origem_resultado_venda IN ('MANUAL', 'FINALIZACAO')
                              AND venda_registrada_por_id IS NOT NULL)))) IS FALSE) > 0
           THEN 'BLOQUEAR: ha linha que violaria a V102'
         WHEN (SELECT count(*) FROM pg_constraint
                WHERE conrelid = 'atendimento'::regclass
                  AND conname IN ('ck_atendimento_origem_resultado_venda', 'ck_atendimento_estado_resultado_venda')
                  AND convalidated) <> 2
           THEN 'ATENCAO: nenhuma linha viola, mas a CHECK atual esta ausente ou nao validada (a V97 nao e garantia)'
         ELSE 'LIBERAR'
       END AS veredito;
ROLLBACK;
