-- Somente leitura. Executar no banco correto com acesso autorizado.
-- No psql: informar limites com offset e intervalo curto antes de executar o arquivo.
-- \set inicio '2026-10-20T00:00:00-03:00'
-- \set fim '2026-10-21T00:00:00-03:00'
-- Nao exporta nomes, CPF, telefone, descricao, token ou payload.
BEGIN READ ONLY;
SET LOCAL statement_timeout = '5s';
SET LOCAL TIME ZONE 'UTC';

SELECT id, origem, evento_externo_id, agendamento_externo_id,
       agendado_para, cancelado_em, recebido_em,
       profissional_nome IS NOT NULL AND btrim(profissional_nome) <> '' AS profissional_informado,
       contato_nome IS NOT NULL AS contato_informado,
       tipo_agendamento IS NOT NULL AS tipo_informado,
       CASE WHEN lower(btrim(tipo_agendamento)) IN ('consulta','retorno','exame')
            THEN 'TIPO_NAO_ESPECIALIDADE' ELSE 'NAO_CLASSIFICADO' END AS semantica_tipo,
       lead_id IS NOT NULL AND atendimento_id IS NOT NULL AS vinculo_completo
FROM cancelamento_agendamento
WHERE recebido_em >= :'inicio'::timestamptz AND recebido_em < :'fim'::timestamptz
ORDER BY recebido_em DESC, id DESC
LIMIT 100;

ROLLBACK;
-- Tabela tem FORCE RLS: sem contexto autorizado o resultado pode ser vazio.
-- Nao desligar RLS nem alterar politicas. Ausencia de linhas nao prova ausencia do evento.
-- Compartilhar IDs somente por canal privado; reduzir o intervalo se houver 100 linhas.
