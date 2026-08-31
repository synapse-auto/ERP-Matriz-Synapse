#!/usr/bin/env bash
set -euo pipefail

modo="SIMULAR"
arquivo=""
backup=""

while (($#)); do
  case "$1" in
    --arquivo)
      arquivo="${2:-}"
      shift 2
      ;;
    --simular)
      modo="SIMULAR"
      shift
      ;;
    --aplicar)
      modo="APLICAR"
      shift
      ;;
    --backup)
      backup="${2:-}"
      shift 2
      ;;
    *)
      printf 'Opcao desconhecida: %s\n' "$1" >&2
      exit 2
      ;;
  esac
done

: "${SYNAPSE_DB_NAME:?Defina SYNAPSE_DB_NAME no ambiente antes de executar.}"
: "${SYNAPSE_DB_USER:?Defina SYNAPSE_DB_USER no ambiente antes de executar.}"
: "${TELEFONE_DDI_PADRAO:?Defina TELEFONE_DDI_PADRAO no ambiente antes de executar.}"

if [[ -z "$arquivo" || ! -f "$arquivo" ]]; then
  printf 'Informe um CSV existente com --arquivo /caminho/fora/do/git/leads.csv\n' >&2
  exit 2
fi

if [[ "$modo" == "APLICAR" ]]; then
  if [[ -z "$backup" ]]; then
    printf 'O modo --aplicar exige --backup /caminho/seguro/antes-importacao.dump\n' >&2
    exit 2
  fi
  if [[ -e "$backup" ]]; then
    printf 'O arquivo de backup ja existe; escolha um caminho novo: %s\n' "$backup" >&2
    exit 2
  fi
  if [[ ! -d "$(dirname -- "$backup")" ]]; then
    printf 'A pasta do backup nao existe: %s\n' "$(dirname -- "$backup")" >&2
    exit 2
  fi
fi

diretorio_script="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
raiz_repositorio="$(cd -- "${diretorio_script}/../.." && pwd)"
# shellcheck source=../operacoes/resolver-postgres.sh
source "${diretorio_script}/../operacoes/resolver-postgres.sh"

temporario="$(mktemp -d)"
script_psql="${temporario}/importacao.psql"
limpar() {
  local alvo
  alvo="$(cd -- "$temporario" && pwd)"
  if [[ "$alvo" == "$(cd -- "$(dirname -- "$temporario")" && pwd)"/* ]]; then
    rm -rf -- "$alvo"
  fi
}
trap limpar EXIT

"${raiz_repositorio}/backend/mvnw" -q -f "${raiz_repositorio}/backend/pom.xml" \
  -pl crm-core -am -DskipTests package

java -cp "${raiz_repositorio}/backend/crm-core/target/classes" \
  com.synapse.crm.core.infrastructure.operacao.GerarScriptImportacaoLeadsCsv \
  "$arquivo" "$TELEFONE_DDI_PADRAO" "$modo" > "$script_psql"

container="$(resolver_postgres_container)"

if [[ "$modo" == "APLICAR" ]]; then
  docker exec "$container" pg_dump \
    --username "$SYNAPSE_DB_USER" \
    --dbname "$SYNAPSE_DB_NAME" \
    --format=custom > "$backup"
  if [[ ! -s "$backup" ]]; then
    printf 'Backup vazio; importacao cancelada.\n' >&2
    exit 3
  fi
  printf 'Backup criado antes da importacao: %s\n' "$backup"
else
  printf 'SIMULACAO: a transacao sera revertida e nenhuma linha permanecera gravada.\n'
fi

docker exec -i "$container" psql \
  --set=ON_ERROR_STOP=1 \
  --username "$SYNAPSE_DB_USER" \
  --dbname "$SYNAPSE_DB_NAME" < "$script_psql"
