#!/usr/bin/env bash
set -euo pipefail

variaveis_obrigatorias=(
  SYNAPSE_DB_NAME
  SYNAPSE_DB_USER
  SYNAPSE_ADMIN_NOME
  SYNAPSE_ADMIN_EMAIL
  SYNAPSE_ADMIN_BCRYPT_HASH
  SYNAPSE_ETAPAS_JSON
  SYNAPSE_TAGS_JSON
  SYNAPSE_AUTOMACAO_JSON
)

for variavel in "${variaveis_obrigatorias[@]}"; do
  : "${!variavel:?Defina ${variavel} no ambiente antes de executar.}"
done

# Um BCrypt valido nao contem espaco nem aspas simples. Validar antes de envia-lo
# pelo stdin impede erro confuso do psql e garante que ele jamais vira argumento
# de linha de comando ou aparece em `ps`.
if [[ ! "${SYNAPSE_ADMIN_BCRYPT_HASH}" =~ ^\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}$ ]]; then
  printf 'ERRO: SYNAPSE_ADMIN_BCRYPT_HASH nao parece um hash BCrypt valido.\n' >&2
  exit 2
fi

diretorio_script="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../operacoes/resolver-postgres.sh
source "${diretorio_script}/../operacoes/resolver-postgres.sh"

container="$(resolver_postgres_container)"

# O hash chega a psql pelo stdin, em vez de `docker exec -e` ou `psql -v`.
# Assim ele nao aparece no historico do shell nem na lista de processos do host.
{
  printf "\\set admin_senha_hash %s\n" "${SYNAPSE_ADMIN_BCRYPT_HASH}"
  cat "${diretorio_script}/provisionar-instancia.sql"
} | docker exec -i "${container}" \
  psql --set=ON_ERROR_STOP=1 --username "${SYNAPSE_DB_USER}" --dbname "${SYNAPSE_DB_NAME}" \
    --set=admin_nome="${SYNAPSE_ADMIN_NOME}" \
    --set=admin_email="${SYNAPSE_ADMIN_EMAIL}" \
    --set=etapas_json="${SYNAPSE_ETAPAS_JSON}" \
    --set=tags_json="${SYNAPSE_TAGS_JSON}" \
    --set=automacao_json="${SYNAPSE_AUTOMACAO_JSON}"
