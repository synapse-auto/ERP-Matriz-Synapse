#!/usr/bin/env bash
# E209 — prova de equivalência na bancada: executa cada cenário com a SQL antiga e com a nova,
# sob a mesma RLS e a mesma massa, e compara a saída byte a byte (todas as colunas, na ordem).
#
# Uso: comparar.sh <container> <cenarios-antes> <cenarios-depois>
set -euo pipefail

CONTAINER="$1"
ANTES="$2"
DEPOIS="$3"
PSQL=(docker exec -i "$CONTAINER" psql -U postgres -d crm -X -At -v ON_ERROR_STOP=1)

diferentes=0
for arquivo in "$ANTES"/*.sql; do
  nome="$(basename "$arquivo")"
  saida_antes="$("${PSQL[@]}" < "$arquivo")"
  saida_depois="$("${PSQL[@]}" < "$DEPOIS/$nome")"
  if [ "$saida_antes" = "$saida_depois" ]; then
    echo "IGUAL      ${nome%.sql}"
  else
    diferentes=$((diferentes + 1))
    echo "DIFERENTE  ${nome%.sql}: $(echo "$saida_antes" | wc -l) -> $(echo "$saida_depois" | wc -l) linhas;" \
      "última linha: $(echo "$saida_antes" | tail -1 | cut -c1-40) -> $(echo "$saida_depois" | tail -1 | cut -c1-40)"
  fi
done
echo "cenarios diferentes: $diferentes"
