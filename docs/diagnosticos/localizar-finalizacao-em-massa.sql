-- Estrutural/matriz_hml: executar SOMENTE no banco confirmado pelo operador.
-- Cada bloco é independente, read-only, limitado e encerra com ROLLBACK.
-- Preencher parâmetros e executar um bloco por vez. Não presumir qual sábado.
-- Saída apenas técnica; compartilhar IDs por canal privado. Sem payload/conteúdo/telefone/email.

-- 1. Localizar candidatos ao usuário. O nome é entrada do filtro, NÃO é retornado.
BEGIN READ ONLY;
SET LOCAL statement_timeout = '5s';
SET LOCAL TIME ZONE 'UTC';
WITH p AS (SELECT 'Andrílene'::text AS nome_parcial)
SELECT u.id AS usuario_id, u.papel, u.ativo, u.status_presenca
  FROM usuario u CROSS JOIN p
 WHERE length(btrim(p.nome_parcial)) >= 3
   AND translate(lower(u.nome), 'áàâãäéèêëíìîïóòôõöúùûüç', 'aaaaaeeeeiiiiooooouuuuc')
       LIKE '%' || translate(lower(p.nome_parcial), 'áàâãäéèêëíìîïóòôõöúùûüç', 'aaaaaeeeeiiiiooooouuuuc') || '%'
 ORDER BY u.id LIMIT 20;
ROLLBACK;

-- 2. Substituir UUID e horários LOCAIS, limite final EXCLUSIVO; ampliar o período somente se necessário.
-- Uma operação pode ter sido criada antes da execução: considera sobreposição do ciclo da operação.
BEGIN READ ONLY;
SET LOCAL statement_timeout = '5s';
SET LOCAL TIME ZONE 'UTC';
WITH p AS (
  SELECT '<UUID-USUARIO>'::uuid AS usuario_id,
         '<INICIO-LOCAL-AAAA-MM-DD HH:MI:SS>'::timestamp AT TIME ZONE 'America/Sao_Paulo' AS inicio,
         '<FIM-LOCAL-EXCLUSIVO-AAAA-MM-DD HH:MI:SS>'::timestamp AT TIME ZONE 'America/Sao_Paulo' AS fim
)
SELECT o.id AS operacao_id, o.solicitante_id,
       o.criada_em, o.iniciada_em, o.concluida_em, o.lease_ate,
       o.periodo_inicio, o.periodo_fim, o.fuso, o.status,
       o.encontrados, o.finalizados, o.ignorados, o.falhas
  FROM finalizacao_em_massa o CROSS JOIN p
 WHERE p.fim > p.inicio
   AND (o.solicitante_id=p.usuario_id OR p.usuario_id=ANY(o.atendente_ids))
   AND o.criada_em < p.fim
   AND (o.concluida_em >= p.inicio OR o.status IN ('PENDENTE','EM_ANDAMENTO'))
 ORDER BY o.criada_em DESC,o.id DESC LIMIT 100;
ROLLBACK;

-- 3. Escolher uma operação do bloco 2; itens do usuário, inclusive sucessos, não só abertos.
-- Páginas de 50 por chave: primeira cursor NULL, seguinte último atendimento_id retornado.
BEGIN READ ONLY;
SET LOCAL statement_timeout = '5s';
SET LOCAL TIME ZONE 'UTC';
WITH p AS (
  SELECT '<UUID-OPERACAO>'::uuid AS operacao_id,
         '<UUID-USUARIO>'::uuid AS usuario_id,
         NULL::uuid AS depois_de
)
SELECT i.operacao_id, i.atendimento_id, a.lead_id,
       i.atendente_id AS responsavel_congelado, i.status AS resultado_item, i.motivo, i.processado_em,
       a.status AS estado_ciclo, a.iniciado_em, a.finalizado_em,
       a.atendente_id AS responsavel_historico,
       l.status_basico AS estado_lead, l.atendente_responsavel_id AS responsavel_atual,
       a.em_negociacao, a.resultado_venda
  FROM finalizacao_em_massa_item i CROSS JOIN p
  JOIN atendimento a ON a.id=i.atendimento_id
  JOIN lead l ON l.id=a.lead_id
 WHERE i.operacao_id=p.operacao_id AND i.atendente_id=p.usuario_id
   AND (p.depois_de IS NULL OR i.atendimento_id > p.depois_de)
 ORDER BY i.atendimento_id LIMIT 50;
ROLLBACK;

-- 4. Só para 1–3 IDs de ciclos do bloco 3. Descobrir reabertura sem ler mensagens.
BEGIN READ ONLY;
SET LOCAL statement_timeout = '5s';
SET LOCAL TIME ZONE 'UTC';
WITH p AS (SELECT ARRAY['<UUID-CICLO-1>'::uuid] AS ciclos)
SELECT antigo.id AS ciclo_do_lote, antigo.lead_id, antigo.status AS estado_ciclo_do_lote,
       antigo.iniciado_em, antigo.finalizado_em,
       atual.id AS ciclo_aberto_atual, atual.status AS estado_atual, atual.iniciado_em AS abertura_atual
  FROM atendimento antigo CROSS JOIN p
  LEFT JOIN LATERAL (
    SELECT id,status,iniciado_em FROM atendimento
     WHERE lead_id=antigo.lead_id AND status <> 'FINALIZADO'
     ORDER BY iniciado_em DESC,id DESC LIMIT 1
  ) atual ON TRUE
 WHERE cardinality(p.ciclos) BETWEEN 1 AND 3 AND antigo.id=ANY(p.ciclos)
 ORDER BY antigo.id;
ROLLBACK;

-- Se LIMIT foi alcançado, paginar/reduzir janela; não inferir ausência nem executar SELECT *.
-- Se um bloco der erro/timeout, executar ROLLBACK antes de prosseguir. Não aumentar timeout cegamente.
-- Não rodar UPDATE, DELETE, reiniciar job, limpar lease ou reenviar aviso nesta coleta.
