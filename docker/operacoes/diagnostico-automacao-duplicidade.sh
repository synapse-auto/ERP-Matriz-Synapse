#!/usr/bin/env bash
set -euo pipefail

# Diagnostico SOMENTE LEITURA: mensagens repetidas e mensagens da Automacao ausentes no CRM.
#
# Uso no servidor:
#   bash docker/operacoes/diagnostico-automacao-duplicidade.sh --stack <nome-da-stack> [--dias 14] [--sim]
#
# A VPS hospeda mais de uma stack. Nada e escolhido por padrao generico: Postgres, backend e
# n8n sao resolvidos pelos labels do Swarm da stack informada (com.docker.stack.namespace e
# com.docker.swarm.service.name = <stack>_<servico>). Os bancos vem do ambiente dos proprios
# containers. O script para se algum alvo for ambiguo, ausente ou nao pertencer a mesma instancia,
# e mostra os alvos antes da primeira consulta (confirmacao interativa, ou --sim).
#
# Nao reenvia, nao apaga, nao altera nada: toda sessao do psql abre com
# default_transaction_read_only=on e so executa SELECT. Nao imprime token, senha, telefone nem
# mensagem integral: e-mails e numeros de 8+ digitos (mesmo formatados) sao mascarados e so
# depois os textos sao truncados em 60 caracteres. Leitura do resultado: docs/49-diagnostico-automacao-duplicidade.md.

uso() {
  cat >&2 <<'USO'
Uso: diagnostico-automacao-duplicidade.sh --stack <nome> [--dias N] [--sim]
  --stack  nome da stack Swarm da instancia (obrigatorio; ex.: o nome da aplicacao no Dokploy)
  --dias   janela em dias (padrao 14)
  --sim    nao pedir confirmacao depois de exibir os alvos
USO
}

falhar() { printf 'ERRO: %s\n' "$*" >&2; exit 1; }

STACK=""
DIAS="14"
SEM_CONFIRMACAO="nao"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --stack) [[ $# -ge 2 ]] || { uso; falhar "--stack exige um valor"; }; STACK="$2"; shift 2 ;;
    --dias) [[ $# -ge 2 ]] || { uso; falhar "--dias exige um valor"; }; DIAS="$2"; shift 2 ;;
    --sim) SEM_CONFIRMACAO="sim"; shift ;;
    -h|--help) uso; exit 0 ;;
    *) uso; falhar "argumento desconhecido: $1" ;;
  esac
done

stacks_em_execucao() {
  docker ps --filter status=running --format '{{.Label "com.docker.stack.namespace"}}' \
    | sed '/^$/d' | sort -u | sed 's/^/  /'
}

if [[ -z "${STACK}" ]]; then
  uso
  printf 'Stacks em execucao neste servidor:\n%s\n' "$(stacks_em_execucao)" >&2
  falhar "informe a instancia com --stack; nada e escolhido automaticamente"
fi
[[ "${STACK}" =~ ^[A-Za-z0-9][A-Za-z0-9_.-]*$ ]] || falhar "nome de stack invalido: ${STACK}"
[[ "${DIAS}" =~ ^[1-9][0-9]{0,2}$ ]] || falhar "--dias deve ser inteiro entre 1 e 999"

# Exatamente um container em execucao do servico <stack>_<servico>, com o label da stack.
resolver_servico() {
  local servico="$1" ids quantidade
  ids="$(docker ps --filter status=running \
    --filter "label=com.docker.stack.namespace=${STACK}" \
    --filter "label=com.docker.swarm.service.name=${STACK}_${servico}" \
    --format '{{.ID}}')"
  quantidade="$(wc -w <<<"${ids}")"
  if [[ "${quantidade}" -ne 1 ]]; then
    printf 'Servico %s_%s: %s container(s) em execucao: %s\n' "${STACK}" "${servico}" "${quantidade}" "${ids:-nenhum}" >&2
    [[ "${quantidade}" -eq 0 ]] && printf 'Stacks em execucao:\n%s\n' "$(stacks_em_execucao)" >&2
    falhar "alvo ${servico} ausente ou ambiguo na stack ${STACK}"
  fi
  printf '%s\n' "${ids}"
}

# Le UMA variavel de ambiente do container. Nunca le nem imprime variaveis de segredo.
ambiente() {
  local container="$1" variavel="$2" valor
  valor="$(docker exec "${container}" printenv "${variavel}" 2>/dev/null)" \
    || falhar "variavel ${variavel} ausente no container ${container}"
  [[ -n "${valor}" ]] || falhar "variavel ${variavel} vazia no container ${container}"
  printf '%s\n' "${valor}"
}

nome_do_container() { docker inspect --format '{{.Name}}' "$1" | sed 's#^/##'; }

PG="$(resolver_servico postgres)"
BACKEND="$(resolver_servico backend)"
N8N="$(resolver_servico n8n)"

PG_USUARIO="$(ambiente "${PG}" POSTGRES_USER)"
PG_BANCO="$(ambiente "${PG}" POSTGRES_DB)"

# Backend: jdbc:postgresql://<host>:<porta>/<banco>[?...] tem que apontar para o Postgres desta stack.
URL_CRM="$(ambiente "${BACKEND}" SYNAPSE_DB_URL)"
[[ "${URL_CRM}" =~ ^jdbc:postgresql://([^:/?]+)(:[0-9]+)?/([^?/]+) ]] \
  || falhar "SYNAPSE_DB_URL do backend em formato inesperado"
HOST_CRM="${BASH_REMATCH[1]}"
CRM_DB="${BASH_REMATCH[3]}"
USUARIO_CRM="$(ambiente "${BACKEND}" SYNAPSE_DB_USER)"

HOST_N8N="$(ambiente "${N8N}" DB_POSTGRESDB_HOST)"
N8N_DB="$(ambiente "${N8N}" DB_POSTGRESDB_DATABASE)"

host_da_stack() {  # nomes pelos quais o servico postgres desta stack e alcancado na rede dela
  [[ "$1" == "postgres" || "$1" == "${STACK}_postgres" || "$1" == "tasks.${STACK}_postgres" ]]
}
host_da_stack "${HOST_CRM}" || falhar "backend usa o banco em '${HOST_CRM}', nao o Postgres da stack ${STACK}"
host_da_stack "${HOST_N8N}" || falhar "n8n usa o banco em '${HOST_N8N}', nao o Postgres da stack ${STACK}"
[[ "${CRM_DB}" == "${PG_BANCO}" ]] \
  || falhar "backend usa o banco '${CRM_DB}', mas o Postgres da stack foi criado com '${PG_BANCO}'"
[[ "${USUARIO_CRM}" == "${PG_USUARIO}" ]] \
  || falhar "backend conecta como '${USUARIO_CRM}', diferente do usuario do Postgres da stack"
[[ "${N8N_DB}" != "${CRM_DB}" ]] || falhar "banco do n8n igual ao do CRM (${CRM_DB}); configuracao inesperada"

leitura() {  # leitura <banco> <sql> [opcoes do psql]  — sessao somente leitura, com limite de tempo
  docker exec -i -e PGOPTIONS='-c default_transaction_read_only=on -c statement_timeout=180s' \
    "${PG}" psql -X -U "${PG_USUARIO}" -d "$1" -v ON_ERROR_STOP=1 -P pager=off "${@:3}" -c "$2"
}

for banco in "${CRM_DB}" "${N8N_DB}"; do
  [[ "${banco}" =~ ^[A-Za-z0-9_-]+$ ]] || falhar "nome de banco inesperado: ${banco}"
  existe="$(leitura postgres "SELECT count(*) FROM pg_database WHERE datname = '${banco}'" -tA)" \
    || falhar "nao foi possivel consultar o Postgres da stack ${STACK} (veja o erro acima)"
  [[ "${existe}" == "1" ]] || falhar "banco '${banco}' nao existe no Postgres da stack ${STACK}"
done

cat <<ALVOS

Alvos resolvidos para a stack ${STACK}:
  postgres  ${PG}  $(nome_do_container "${PG}")
  backend   ${BACKEND}  $(nome_do_container "${BACKEND}")
  n8n       ${N8N}  $(nome_do_container "${N8N}")
  banco CRM ${CRM_DB} (host ${HOST_CRM}, usuario ${USUARIO_CRM})
  banco n8n ${N8N_DB} (host ${HOST_N8N})
  janela    ${DIAS} dia(s); sessoes somente leitura
ALVOS

if [[ "${SEM_CONFIRMACAO}" != "sim" ]]; then
  [[ -t 0 ]] || falhar "sem terminal para confirmar os alvos; confira-os acima e rode de novo com --sim"
  read -r -p "Os alvos acima sao da instancia certa? [s/N] " resposta
  [[ "${resposta}" =~ ^[sS]$ ]] || falhar "cancelado; nenhuma consulta foi executada"
fi

secao() { printf '\n==================== %s ====================\n' "$1"; }
# Mascara ANTES de truncar (cortar primeiro deixaria e-mail/telefone parcial sem mascara):
# e-mails e numeros com 8+ digitos, mesmo formatados ("(61) 99999-0000", "+55 61 ...").
MASCARA_SQL="left(regexp_replace(regexp_replace(regexp_replace(m.conteudo, '\s+', ' ', 'g'), '[^\s@]+@[^\s@]+', '<email>', 'g'), '\+?\(?\d[\d\s().-]{6,}\d', '<num>', 'g'), 60)"

secao "A. Repasse CRM -> n8n por dia (tipo automacao.webhook.repassar)"
leitura "${CRM_DB}" "
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
leitura "${CRM_DB}" "
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
  | sed -E 's/.*tentara novamente: //; s#https?://[^ "]+#<url>#g; s/[^[:space:]@]+@[^[:space:]@]+/<email>/g; s/\+?\(?[0-9][0-9 ().-]{6,}[0-9]/<num>/g' \
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
leitura "${CRM_DB}" "
WITH saida AS (
  SELECT m.id, m.atendimento_id, a.lead_id, m.remetente_tipo, m.enviado_em,
         md5(m.conteudo) AS assinatura,
         ${MASCARA_SQL} AS trecho,
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
leitura "${CRM_DB}" "
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
leitura "${CRM_DB}" "
SELECT status, count(*) FROM atendimento WHERE status IN ('EM_IA', 'EM_ATENDIMENTO') GROUP BY 1;"

secao "E2. Mensagens registradas pela Automacao (POST mensagens-enviadas) por dia"
leitura "${CRM_DB}" "
SELECT date_trunc('day', criado_em)::date AS dia, count(*) AS registradas
  FROM mensagem_automacao_idempotencia
 WHERE criado_em > now() - interval '${DIAS} days' GROUP BY 1 ORDER BY 1;"

printf '\nFim. Nada foi alterado.\n'
