#!/usr/bin/env bash
set -euo pipefail

# Diagnostico SOMENTE LEITURA: mensagens repetidas e mensagens da Automacao ausentes no CRM.
#
# Uso no servidor da instancia:  bash docker/operacoes/diagnostico-automacao-duplicidade.sh [DIAS]
#
# Nao reenvia, nao apaga, nao altera nada: toda sessao do psql abre com
# default_transaction_read_only=on e o script so executa SELECT. Nao imprime telefone;
# textos aparecem truncados (60 caracteres) para a equipe reconhecer a mensagem.
# Contexto e leitura do resultado: docs/49-diagnostico-automacao-duplicidade.md.

DIAS="${1:-14}"
[[ "${DIAS}" =~ ^[0-9]+$ ]] || { echo "DIAS deve ser inteiro" >&2; exit 1; }

DIRETORIO="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=resolver-postgres.sh
source "${DIRETORIO}/resolver-postgres.sh"

unico_container() {
  local padrao="$1" ids
  ids="$(docker ps --filter status=running --format '{{.ID}} {{.Names}}' | awk -v p="${padrao}" '$2 ~ p {print $1}')"
  if [[ "$(wc -w <<<"${ids}")" -ne 1 ]]; then
    echo "ERRO: esperado um container ${padrao}, encontrei: ${ids:-nenhum}" >&2
    return 1
  fi
  echo "${ids}"
}

PG="$(resolver_postgres_container)"
BACKEND="$(unico_container '_backend\.1\.')"
N8N="$(unico_container '_n8n\.1\.')"
N8N_DB="$(docker exec "${N8N}" printenv DB_POSTGRESDB_DATABASE)"

leitura() {  # leitura <banco> <sql>
  docker exec -i -e PGOPTIONS='-c default_transaction_read_only=on -c statement_timeout=180s' \
    "${PG}" psql -U synapse -d "$1" -v ON_ERROR_STOP=1 -P pager=off -c "$2"
}

secao() { printf '\n==================== %s ====================\n' "$1"; }

secao "A. Repasse CRM -> n8n por dia (tipo automacao.webhook.repassar)"
leitura synapse_crm "
SELECT date_trunc('day', criado_em)::date AS dia,
       count(*) AS eventos,
       count(*) FILTER (WHERE publicado_em IS NOT NULL AND tentativas > 0) AS publicados_apos_falha,
       count(*) FILTER (WHERE esgotado_em IS NOT NULL) AS esgotados,
       count(*) FILTER (WHERE publicado_em IS NULL AND esgotado_em IS NULL) AS pendentes,
       max(tentativas) AS max_tentativas
  FROM outbox_evento
 WHERE tipo = 'automacao.webhook.repassar' AND criado_em > now() - interval '${DIAS} days'
 GROUP BY 1 ORDER BY 1;"

secao "A2. Repasses publicados apos falha (candidatos a entrega dupla) - 50 mais recentes"
leitura synapse_crm "
SELECT o.id AS outbox_id, o.criado_em, o.publicado_em, o.tentativas,
       round(extract(epoch FROM o.publicado_em - o.criado_em))::int AS segundos_ate_publicar,
       substring(o.payload::text FROM 'wamid\.[A-Za-z0-9=_\-]+') AS wamid_entrada,
       left(md5(o.payload::text), 12) AS assinatura_payload
  FROM outbox_evento o
 WHERE o.tipo = 'automacao.webhook.repassar' AND o.criado_em > now() - interval '${DIAS} days'
   AND o.publicado_em IS NOT NULL AND o.tentativas > 0
 ORDER BY o.criado_em DESC LIMIT 50;"

secao "B. Motivo das falhas de repasse no log do backend (ultimos ${DIAS} dias)"
docker logs --since "$((DIAS * 24))h" "${BACKEND}" 2>&1 \
  | grep 'Repasse do webhook para a Automacao falhou' \
  | sed -E 's/.*tentara novamente: //; s#https?://[^ "]+#<url>#g' \
  | cut -c1-140 | sort | uniq -c | sort -rn | head -15 || true
printf 'Circuit breaker do repasse aberto (ocorrencias): %s\n' \
  "$(docker logs --since "$((DIAS * 24))h" "${BACKEND}" 2>&1 | grep -c 'Circuit breaker do repasse para a Automacao esta aberto' || true)"

secao "C. Execucoes do n8n por workflow e dia"
leitura "${N8N_DB}" "
SELECT w.name AS workflow, date_trunc('day', e.\"startedAt\")::date AS dia, e.mode,
       count(*) AS execucoes,
       count(*) FILTER (WHERE e.status = 'success') AS sucesso,
       count(*) FILTER (WHERE e.status IN ('error', 'crashed')) AS erro,
       count(*) FILTER (WHERE e.\"retryOf\" IS NOT NULL) AS retries_n8n
  FROM execution_entity e JOIN workflow_entity w ON w.id = e.\"workflowId\"
 WHERE e.\"startedAt\" > now() - interval '${DIAS} days'
 GROUP BY 1, 2, 3 ORDER BY 2 DESC, 4 DESC;"

secao "C2. Mesmo wamid de entrada em mais de uma execucao de webhook do n8n"
leitura "${N8N_DB}" "
WITH ids AS (
  SELECT e.id, e.\"workflowId\", e.\"startedAt\", e.status,
         substring(d.data FROM 'wamid\.[A-Za-z0-9=_\-]+') AS wamid_entrada
    FROM execution_entity e JOIN execution_data d ON d.\"executionId\" = e.id
   WHERE e.\"startedAt\" > now() - interval '${DIAS} days' AND e.mode = 'webhook')
SELECT w.name AS workflow, i.wamid_entrada, count(*) AS execucoes,
       min(i.\"startedAt\") AS primeira, max(i.\"startedAt\") AS ultima,
       string_agg(i.id::text || ':' || i.status, ', ' ORDER BY i.\"startedAt\") AS execucoes_ids
  FROM ids i JOIN workflow_entity w ON w.id = i.\"workflowId\"
 WHERE i.wamid_entrada IS NOT NULL
 GROUP BY 1, 2 HAVING count(*) > 1
 ORDER BY 3 DESC, 4 DESC LIMIT 50;"

secao "D. Mesma mensagem da IA/Sistema repetida no mesmo atendimento em ate 10 minutos"
leitura synapse_crm "
WITH saida AS (
  SELECT m.id, m.atendimento_id, a.lead_id, m.remetente_tipo, m.enviado_em,
         md5(m.conteudo) AS assinatura,
         left(regexp_replace(m.conteudo, '\s+', ' ', 'g'), 60) AS trecho,
         lag(m.enviado_em) OVER (PARTITION BY m.atendimento_id, md5(m.conteudo) ORDER BY m.enviado_em) AS anterior
    FROM mensagem m JOIN atendimento a ON a.id = m.atendimento_id
   WHERE m.remetente_tipo IN ('IA', 'SISTEMA') AND m.conteudo IS NOT NULL
     AND m.enviado_em > now() - interval '${DIAS} days')
SELECT s.lead_id, s.atendimento_id, s.remetente_tipo, s.trecho,
       count(*) + 1 AS ocorrencias, max(s.enviado_em) AS ultima,
       (SELECT string_agg(x.wamid, ', ')
          FROM saida t JOIN mensagem_id_externo x ON x.mensagem_id = t.id
         WHERE t.atendimento_id = s.atendimento_id AND t.assinatura = s.assinatura) AS wamids_saida
  FROM saida s
 WHERE s.anterior IS NOT NULL AND s.enviado_em - s.anterior < interval '10 minutes'
 GROUP BY s.lead_id, s.atendimento_id, s.remetente_tipo, s.trecho, s.assinatura
 ORDER BY max(s.enviado_em) DESC LIMIT 50;"

secao "D2. Alcance: repeticoes em ate 10 minutos por dia"
leitura synapse_crm "
WITH saida AS (
  SELECT m.atendimento_id, m.enviado_em,
         lag(m.enviado_em) OVER (PARTITION BY m.atendimento_id, md5(m.conteudo) ORDER BY m.enviado_em) AS anterior
    FROM mensagem m
   WHERE m.remetente_tipo IN ('IA', 'SISTEMA') AND m.conteudo IS NOT NULL
     AND m.enviado_em > now() - interval '${DIAS} days')
SELECT date_trunc('day', s.enviado_em)::date AS dia, count(*) AS repeticoes,
       count(DISTINCT a.lead_id) AS leads_afetados
  FROM saida s JOIN atendimento a ON a.id = s.atendimento_id
 WHERE s.anterior IS NOT NULL AND s.enviado_em - s.anterior < interval '10 minutes'
 GROUP BY 1 ORDER BY 1;"

secao "E. Atendimentos ativos agora (pagina padrao de /internal/v1/atendimentos/em-andamento = 20)"
leitura synapse_crm "
SELECT status, count(*) FROM atendimento WHERE status IN ('EM_IA', 'EM_ATENDIMENTO') GROUP BY 1;"

secao "E2. Mensagens registradas pela Automacao (POST mensagens-enviadas) por dia"
leitura synapse_crm "
SELECT date_trunc('day', criado_em)::date AS dia, count(*) AS registradas
  FROM mensagem_automacao_idempotencia
 WHERE criado_em > now() - interval '${DIAS} days' GROUP BY 1 ORDER BY 1;"

printf '\nFim. Nada foi alterado.\n'
