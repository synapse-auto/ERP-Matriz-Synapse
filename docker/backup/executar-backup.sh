#!/usr/bin/env bash
# Backup horario do banco da instancia. Executar em um Dokploy Server Job.
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
  SYNAPSE_DB_NAME SYNAPSE_DB_USER BACKUP_INSTANCIA_CODIGO \
  BACKUP_S3_ENDPOINT BACKUP_S3_BUCKET BACKUP_S3_REGION \
  AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY BACKUP_RCLONE_IMAGE; do
  exigir_variavel "$variavel"
done

if [[ ! "$BACKUP_INSTANCIA_CODIGO" =~ ^[a-z0-9][a-z0-9-]{1,62}$ ]]; then
  printf 'ERRO: BACKUP_INSTANCIA_CODIGO deve usar apenas letras minusculas, numeros e hifen.\n' >&2
  exit 2
fi
if [[ ! "$BACKUP_S3_BUCKET" =~ ^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$ ]]; then
  printf 'ERRO: BACKUP_S3_BUCKET nao tem formato de bucket DNS valido.\n' >&2
  exit 2
fi
if [[ ! "$BACKUP_S3_ENDPOINT" =~ ^https?:// ]]; then
  printf 'ERRO: BACKUP_S3_ENDPOINT deve iniciar com http:// ou https://.\n' >&2
  exit 2
fi
if [[ "$BACKUP_RCLONE_IMAGE" != *:* || "$BACKUP_RCLONE_IMAGE" == *:latest ]]; then
  printf 'ERRO: BACKUP_RCLONE_IMAGE deve usar uma tag imutavel, nunca latest.\n' >&2
  exit 2
fi

RETENCAO_DIAS="${BACKUP_RETENCAO_DIAS:-7}"
PREFIXO="${BACKUP_S3_PREFIX:-synapse-crm}"
LOCK_FILE="${BACKUP_LOCK_FILE:-/tmp/synapse-crm-backup.lock}"

if [[ ! "$RETENCAO_DIAS" =~ ^[1-9][0-9]*$ ]] || (( RETENCAO_DIAS < 7 )); then
  printf 'ERRO: BACKUP_RETENCAO_DIAS deve ser inteiro de no minimo 7.\n' >&2
  exit 2
fi
if [[ ! "$PREFIXO" =~ ^[a-zA-Z0-9][a-zA-Z0-9/_-]*$ ]] || [[ "$PREFIXO" == *..* ]]; then
  printf 'ERRO: BACKUP_S3_PREFIX contem caracteres nao permitidos.\n' >&2
  exit 2
fi
if ! command -v flock >/dev/null 2>&1; then
  printf 'ERRO: flock e obrigatorio para evitar dois backups simultaneos.\n' >&2
  exit 2
fi

exec 9>"$LOCK_FILE"
if ! flock -n 9; then
  printf 'ERRO: ja existe um backup em execucao; nada foi iniciado em paralelo.\n' >&2
  exit 1
fi

export RCLONE_CONFIG=/dev/null
export RCLONE_CONFIG_SYNAPSEREMOTE_TYPE=s3
export RCLONE_CONFIG_SYNAPSEREMOTE_PROVIDER=Other
export RCLONE_CONFIG_SYNAPSEREMOTE_ENV_AUTH=true
export RCLONE_CONFIG_SYNAPSEREMOTE_ENDPOINT="$BACKUP_S3_ENDPOINT"
export RCLONE_CONFIG_SYNAPSEREMOTE_REGION="$BACKUP_S3_REGION"

TEMP_DIR="$(mktemp -d)"
cleanup() {
  rm -rf -- "$TEMP_DIR"
}
trap cleanup EXIT

POSTGRES_CONTAINER="$(resolver_postgres_container)"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
ARQUIVO="${BACKUP_INSTANCIA_CODIGO}-${TIMESTAMP}.dump"
CAMINHO_LOCAL="${TEMP_DIR}/${ARQUIVO}"
DESTINO_BASE="synapseremote:${BACKUP_S3_BUCKET}/${PREFIXO}/${BACKUP_INSTANCIA_CODIGO}"
DESTINO="${DESTINO_BASE}/${ARQUIVO}"

rclone() {
  docker run --rm \
    -v "${TEMP_DIR}:/backup:ro" \
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

printf '%s iniciando pg_dump de %s no container %s.\n' "$(date -u +%FT%TZ)" "$SYNAPSE_DB_NAME" "$POSTGRES_CONTAINER"
if ! docker exec "$POSTGRES_CONTAINER" \
  pg_dump -U "$SYNAPSE_DB_USER" -d "$SYNAPSE_DB_NAME" \
    --format=custom --compress=9 --no-owner >"$CAMINHO_LOCAL"; then
  printf 'ERRO: pg_dump falhou; nenhum upload foi considerado valido.\n' >&2
  exit 1
fi

TAMANHO_LOCAL="$(wc -c <"$CAMINHO_LOCAL" | tr -d '[:space:]')"
if [[ ! "$TAMANHO_LOCAL" =~ ^[1-9][0-9]*$ ]]; then
  printf 'ERRO: dump vazio ou truncado (%s bytes); upload cancelado.\n' "$TAMANHO_LOCAL" >&2
  exit 1
fi
printf '%s dump concluido: %s bytes (%s).\n' "$(date -u +%FT%TZ)" "$TAMANHO_LOCAL" "$ARQUIVO"

if ! rclone mkdir "synapseremote:${BACKUP_S3_BUCKET}"; then
  printf 'ERRO: nao foi possivel confirmar/criar o bucket de backup.\n' >&2
  exit 1
fi
if ! rclone copyto "/backup/${ARQUIVO}" "$DESTINO"; then
  printf 'ERRO: upload do backup falhou; trate este backup como inexistente.\n' >&2
  exit 1
fi

LISTAGEM_REMOTA="$(rclone lsf --files-only --format ps "$DESTINO" || true)"
TAMANHO_REMOTO="$(printf '%s\n' "$LISTAGEM_REMOTA" | awk -F ';' 'NF == 2 { print $2 }')"
if [[ "$TAMANHO_REMOTO" != "$TAMANHO_LOCAL" ]]; then
  printf 'ERRO: upload nao foi confirmado: local=%s bytes, remoto=%s bytes.\n' \
    "$TAMANHO_LOCAL" "${TAMANHO_REMOTO:-ausente}" >&2
  exit 1
fi
printf '%s upload confirmado: %s bytes em %s.\n' \
  "$(date -u +%FT%TZ)" "$TAMANHO_REMOTO" "$DESTINO"

if ! rclone delete --min-age "${RETENCAO_DIAS}d" "$DESTINO_BASE"; then
  printf 'ERRO: o upload foi confirmado, mas o expurgo de backups com mais de %s dias falhou.\n' \
    "$RETENCAO_DIAS" >&2
  exit 1
fi
printf '%s retencao aplicada: objetos com mais de %s dias foram expurgados.\n' \
  "$(date -u +%FT%TZ)" "$RETENCAO_DIAS"
