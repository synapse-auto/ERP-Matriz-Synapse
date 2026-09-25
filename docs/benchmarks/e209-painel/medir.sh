#!/usr/bin/env bash
# E209 — executa cada cenário extraído por ExtrairSql.java N vezes sob RLS real
# (SET ROLE synapse_app + app.usuario_id/app.papel) e imprime, por execução:
# tempo médio/máximo, blocos lidos, linhas devolvidas e os contadores de tabela
# que apareceram no incidente (idx_scan em atendimento/mensagem, seq_scan em usuario).
#
# Somente para a bancada local: usa pg_stat_statements e pg_stat_statements_reset, que não
# estão habilitados nas instâncias de produção.
#
# Uso: medir.sh <container> <diretorio-de-cenarios> [repeticoes]
set -euo pipefail

CONTAINER="$1"
CENARIOS="$2"
REPETICOES="${3:-5}"
PSQL=(docker exec -i "$CONTAINER" psql -U postgres -d crm -X -q -v ON_ERROR_STOP=1)

"${PSQL[@]}" -c "CREATE EXTENSION IF NOT EXISTS pg_stat_statements" >/dev/null

contadores() {
  "${PSQL[@]}" -At -F' ' -c "
    SELECT coalesce(sum(idx_scan) FILTER (WHERE relname = 'atendimento'), 0),
           coalesce(sum(idx_scan) FILTER (WHERE relname LIKE 'mensagem%'), 0),
           coalesce(sum(seq_scan) FILTER (WHERE relname = 'usuario'), 0)
      FROM pg_stat_user_tables"
}

printf 'cenario\tms_medio\tms_max\tblocos_por_exec\tlinhas\tidx_atendimento\tidx_mensagem\tseq_usuario\n'
for arquivo in "$CENARIOS"/*.sql; do
  nome="$(basename "$arquivo" .sql)"
  "${PSQL[@]}" -c "SELECT pg_stat_statements_reset()" >/dev/null
  read -r a0 m0 u0 < <(contadores)
  for _ in $(seq 1 "$REPETICOES"); do
    "${PSQL[@]}" < "$arquivo" >/dev/null
  done
  sleep 1
  read -r a1 m1 u1 < <(contadores)
  "${PSQL[@]}" -At -F$'\t' -c "
    SELECT '$nome', round(mean_exec_time::numeric, 1), round(max_exec_time::numeric, 1),
           (shared_blks_hit + shared_blks_read) / calls, rows / calls,
           ($a1 - $a0) / $REPETICOES, ($m1 - $m0) / $REPETICOES, ($u1 - $u0) / $REPETICOES
      FROM pg_stat_statements
     WHERE query NOT ILIKE '%set_config%' AND query NOT ILIKE 'SET ROLE%'
       AND query NOT ILIKE '%pg_stat%' AND calls = $REPETICOES
     ORDER BY total_exec_time DESC LIMIT 1"
done
