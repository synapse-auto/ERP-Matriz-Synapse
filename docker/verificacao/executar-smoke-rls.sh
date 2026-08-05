#!/usr/bin/env bash
set -euo pipefail

: "${SYNAPSE_DB_NAME:?Defina SYNAPSE_DB_NAME (nao e segredo) no ambiente antes de executar.}"
: "${SYNAPSE_DB_USER:?Defina SYNAPSE_DB_USER (nao e segredo) no ambiente antes de executar.}"

diretorio_script="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../operacoes/resolver-postgres.sh
source "${diretorio_script}/../operacoes/resolver-postgres.sh"

container="$(resolver_postgres_container)"

docker exec -i "${container}" \
  psql --set=ON_ERROR_STOP=1 --username "${SYNAPSE_DB_USER}" --dbname "${SYNAPSE_DB_NAME}" \
  < "${diretorio_script}/smoke-rls.sql"
