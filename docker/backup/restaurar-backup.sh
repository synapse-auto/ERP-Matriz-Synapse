#!/usr/bin/env bash
# Restaura um dump em banco novo; nunca sobrescreve SYNAPSE_DB_NAME.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../operacoes/resolver-postgres.sh
source "${SCRIPT_DIR}/../operacoes/resolver-postgres.sh"

exigir_variavel() {
  local nome="$1"
  if [[ -z "${!nome:-}" ]]; then
    printf 'ERRO: a variavel %s e obrigatoria.\n' "$nome" >&2
    exit 2
  fi
}

for variavel in \
  SYNAPSE_DB_NAME SYNAPSE_DB_USER BACKUP_S3_ENDPOINT BACKUP_S3_BUCKET \
  BACKUP_S3_REGION AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY BACKUP_RCLONE_IMAGE \
  BACKUP_OBJECT_KEY RESTAURAR_DB_NOME; do
  exigir_variavel "$variavel"
done

if [[ "$RESTAURAR_DB_NOME" == "$SYNAPSE_DB_NAME" ]]; then
  printf 'ERRO: RESTAURAR_DB_NOME nao pode sobrescrever o banco em producao/homologacao.\n' >&2
  exit 2
fi
if [[ ! "$RESTAURAR_DB_NOME" =~ ^[a-z_][a-z0-9_]{0,62}$ ]]; then
  printf 'ERRO: RESTAURAR_DB_NOME deve ser identificador PostgreSQL seguro.\n' >&2
  exit 2
fi
if [[ ! "$BACKUP_OBJECT_KEY" =~ ^[a-zA-Z0-9][a-zA-Z0-9/_-]*\.dump$ ]] || [[ "$BACKUP_OBJECT_KEY" == *..* ]]; then
  printf 'ERRO: BACKUP_OBJECT_KEY deve apontar para um .dump sob um prefixo seguro.\n' >&2
  exit 2
fi
if [[ "$BACKUP_RCLONE_IMAGE" != *:* || "$BACKUP_RCLONE_IMAGE" == *:latest ]]; then
  printf 'ERRO: BACKUP_RCLONE_IMAGE deve usar uma tag imutavel, nunca latest.\n' >&2
  exit 2
fi

export RCLONE_CONFIG=/dev/null
export RCLONE_CONFIG_SYNAPSEREMOTE_TYPE=s3
export RCLONE_CONFIG_SYNAPSEREMOTE_PROVIDER=Other
export RCLONE_CONFIG_SYNAPSEREMOTE_ENV_AUTH=true
export RCLONE_CONFIG_SYNAPSEREMOTE_ENDPOINT="$BACKUP_S3_ENDPOINT"
export RCLONE_CONFIG_SYNAPSEREMOTE_REGION="$BACKUP_S3_REGION"

TEMP_DIR="$(mktemp -d)"
cleanup() { rm -rf -- "$TEMP_DIR"; }
trap cleanup EXIT

POSTGRES_CONTAINER="$(resolver_postgres_container)"
ARQUIVO="$(basename "$BACKUP_OBJECT_KEY")"
CAMINHO_LOCAL="${TEMP_DIR}/${ARQUIVO}"
ORIGEM="synapseremote:${BACKUP_S3_BUCKET}/${BACKUP_OBJECT_KEY}"

rclone() {
  docker run --rm \
    -v "${TEMP_DIR}:/backup" \
    -e RCLONE_CONFIG \
    -e RCLONE_CONFIG_SYNAPSEREMOTE_TYPE \
    -e RCLONE_CONFIG_SYNAPSEREMOTE_PROVIDER \
    -e RCLONE_CONFIG_SYNAPSEREMOTE_ENV_AUTH \
    -e RCLONE_CONFIG_SYNAPSEREMOTE_ENDPOINT \
    -e RCLONE_CONFIG_SYNAPSEREMOTE_REGION \
    -e AWS_ACCESS_KEY_ID \
    -e AWS_SECRET_ACCESS_KEY \
    "$BACKUP_RCLONE_IMAGE" "$@"
}

if docker exec "$POSTGRES_CONTAINER" psql -U "$SYNAPSE_DB_USER" -d postgres -tAc \
  "SELECT 1 FROM pg_database WHERE datname = '$RESTAURAR_DB_NOME'" | grep -qx '1'; then
  printf 'ERRO: o banco de destino %s ja existe; a restauracao so aceita banco novo.\n' \
    "$RESTAURAR_DB_NOME" >&2
  exit 1
fi

printf '%s baixando %s.\n' "$(date -u +%FT%TZ)" "$ORIGEM"
if ! rclone copyto "$ORIGEM" "/backup/${ARQUIVO}"; then
  printf 'ERRO: download do backup falhou; restauracao nao iniciou.\n' >&2
  exit 1
fi
if [[ ! -s "$CAMINHO_LOCAL" ]]; then
  printf 'ERRO: download vazio; restauracao cancelada.\n' >&2
  exit 1
fi

docker exec -i "$POSTGRES_CONTAINER" psql -U "$SYNAPSE_DB_USER" -d postgres \
  -v ON_ERROR_STOP=1 --set=app_user="$SYNAPSE_DB_USER" <<'SQL'
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'synapse_app') THEN
        CREATE ROLE synapse_app NOLOGIN;
    END IF;
END
$$;
SELECT format('GRANT synapse_app TO %I', :'app_user') \gexec
SQL

docker exec "$POSTGRES_CONTAINER" createdb -U "$SYNAPSE_DB_USER" "$RESTAURAR_DB_NOME"
if ! docker exec -i "$POSTGRES_CONTAINER" \
  pg_restore -U "$SYNAPSE_DB_USER" -d "$RESTAURAR_DB_NOME" \
    --no-owner --exit-on-error --verbose <"$CAMINHO_LOCAL"; then
  printf 'ERRO: pg_restore falhou; o banco parcial %s foi preservado para diagnostico.\n' \
    "$RESTAURAR_DB_NOME" >&2
  exit 1
fi

docker exec -i "$POSTGRES_CONTAINER" psql -U "$SYNAPSE_DB_USER" -d "$RESTAURAR_DB_NOME" \
  -v ON_ERROR_STOP=1 <<'SQL'
GRANT USAGE ON SCHEMA public TO synapse_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO synapse_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO synapse_app;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO synapse_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO synapse_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO synapse_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT EXECUTE ON FUNCTIONS TO synapse_app;
SELECT garantir_particoes_mensagem(3);
DO $$
BEGIN
    IF mensagens_na_particao_default() <> 0 THEN
        RAISE EXCEPTION 'mensagem_default contem linhas apos restauracao; drene antes de subir a aplicacao';
    END IF;
END
$$;
SQL

printf '%s restauracao concluida em %s. Confirme a aplicacao contra este banco antes de descarta-lo.\n' \
  "$(date -u +%FT%TZ)" "$RESTAURAR_DB_NOME"
