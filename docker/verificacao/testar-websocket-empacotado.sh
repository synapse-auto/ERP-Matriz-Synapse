#!/usr/bin/env sh
set -eu

RAIZ_REPOSITORIO=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
COMPOSE="$RAIZ_REPOSITORIO/docker/verificacao/websocket-empacotado.yml"

limpar() {
  docker compose -f "$COMPOSE" down --volumes --remove-orphans >/dev/null 2>&1 || true
}
trap limpar EXIT INT TERM

limpar
docker build --file "$RAIZ_REPOSITORIO/backend/Dockerfile" \
  --tag synapse-backend-websocket-test:local "$RAIZ_REPOSITORIO"
if ! docker compose -f "$COMPOSE" up --detach --wait; then
  docker compose -f "$COMPOSE" logs backend traefik
  exit 1
fi

tentativa=0
codigo=000
while [ "$tentativa" -lt 60 ]; do
  codigo=$(curl --silent --output /dev/null --write-out '%{http_code}' \
    --header 'Host: crm.ws.test' http://127.0.0.1:18080/health/liveness || true)
  [ "$codigo" = "200" ] && break
  tentativa=$((tentativa + 1))
  sleep 2
done
[ "$codigo" = "200" ] || {
  docker compose -f "$COMPOSE" logs backend traefik
  echo "backend empacotado nao ficou acessivel pelo Traefik" >&2
  exit 1
}

testar_boot_por_papel() {
  email=$1
  senha=$2
  papel=$3

  resposta_login=$(curl --fail --silent --show-error \
    --header 'Host: crm.ws.test' \
    --header 'Content-Type: application/json' \
    --data "{\"email\":\"$email\",\"senha\":\"$senha\"}" \
    http://127.0.0.1:18080/api/v1/auth/login)
  access_token=$(printf '%s' "$resposta_login" \
    | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
  [ -n "$access_token" ] || {
    echo "login nao devolveu accessToken para $papel" >&2
    exit 1
  }

  resposta_features=$(curl --fail --silent --show-error \
    --header 'Host: crm.ws.test' \
    --header "Authorization: Bearer $access_token" \
    http://127.0.0.1:18080/api/v1/config/features)
  printf '%s' "$resposta_features" | grep --quiet '^\[' || {
    echo "features nao devolveu lista para $papel: $resposta_features" >&2
    exit 1
  }

  resposta_me=$(curl --fail --silent --show-error \
    --header 'Host: crm.ws.test' \
    --header "Authorization: Bearer $access_token" \
    http://127.0.0.1:18080/api/v1/me)
  printf '%s' "$resposta_me" | grep --quiet '"nome":' || {
    echo "/me nao devolveu nome para $papel: $resposta_me" >&2
    exit 1
  }
  printf '%s' "$resposta_me" | grep --quiet "\"papel\":\"$papel\"" || {
    echo "/me devolveu papel incorreto para $papel: $resposta_me" >&2
    exit 1
  }
  printf '%s' "$resposta_me" | grep --quiet '"presenca":' || {
    echo "/me nao devolveu presenca para $papel: $resposta_me" >&2
    exit 1
  }

  echo "fumaca de boot confirmada para $papel: features=200, me=200"
}

testar_boot_por_papel 'admin@dev.local' 'admin123' 'ADMINISTRADOR'
testar_boot_por_papel 'gestor@dev.local' 'gestor123' 'GESTOR'
testar_boot_por_papel 'subgestor@dev.local' 'subgestor123' 'SUBGESTOR'
testar_boot_por_papel 'ana@dev.local' 'atendente123' 'ATENDENTE'

docker run --rm --interactive \
  --network synapse-ws-proxy \
  --env ACCESS_TOKEN="$access_token" \
  python:3.12-alpine python - <<'PY'
import base64
import os
import socket
from urllib.parse import quote

token = quote(os.environ["ACCESS_TOKEN"], safe="")
chave = base64.b64encode(os.urandom(16)).decode("ascii")
requisicao = (
    f"GET /ws?access_token={token} HTTP/1.1\r\n"
    "Host: crm.ws.test\r\n"
    "Origin: http://crm.ws.test\r\n"
    "Upgrade: websocket\r\n"
    "Connection: Upgrade\r\n"
    f"Sec-WebSocket-Key: {chave}\r\n"
    "Sec-WebSocket-Version: 13\r\n\r\n"
)

with socket.create_connection(("traefik", 80), timeout=10) as conexao:
    conexao.sendall(requisicao.encode("ascii"))
    resposta = conexao.recv(4096).decode("latin-1")

linha_status = resposta.split("\r\n", 1)[0]
if " 101 " not in linha_status:
    raise SystemExit(f"handshake WebSocket falhou: {linha_status}")
print(f"handshake WebSocket empacotado confirmado: {linha_status}")
PY
