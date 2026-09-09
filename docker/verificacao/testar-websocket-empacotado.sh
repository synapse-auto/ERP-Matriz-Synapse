#!/usr/bin/env sh
set -eu

RAIZ_REPOSITORIO=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
COMPOSE="$RAIZ_REPOSITORIO/docker/verificacao/websocket-empacotado.yml"
DIRETORIO_TEMPORARIO=''

limpar() {
  docker compose -f "$COMPOSE" down --volumes --remove-orphans >/dev/null 2>&1 || true
  if [ -n "$DIRETORIO_TEMPORARIO" ]; then
    rm -rf "$DIRETORIO_TEMPORARIO"
  fi
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

# E172: a imagem empacotada precisa provar que autentica e grava em um MinIO real.
# A IT do EnviarMidiaUseCase cobre tambem XLSX com o mimetype ja normalizado; aqui
# PDF e PNG atravessam ainda o endpoint, o Tika, a imagem de runtime e a rede Docker.
docker compose -f "$COMPOSE" exec --no-TTY postgres psql \
  --username synapse_ws --dbname synapse_ws --set ON_ERROR_STOP=1 <<'SQL'
INSERT INTO lead
    (id, nome, telefone, atendente_responsavel_id, status_basico,
     ultima_interacao_em, ultima_mensagem_do_lead_em)
VALUES
    ('e1720000-0000-4000-8000-000000000001', 'Smoke de mídia empacotada',
     '5561977700172', '11000000-0000-4000-8000-000000000004',
     'EM_ATENDIMENTO'::status_basico_lead, now(), now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO atendimento
    (id, lead_id, canal_id, canal_credencial_id, atendente_id, status, iniciado_em)
VALUES
    ('e1720000-0000-4000-8000-000000000002',
     'e1720000-0000-4000-8000-000000000001',
     'ca000000-0000-4000-8000-000000000001',
     'cc000000-0000-4000-8000-000000000001',
     '11000000-0000-4000-8000-000000000004',
     'EM_ATENDIMENTO'::status_atendimento, now())
ON CONFLICT (id) DO NOTHING;
SQL

DIRETORIO_TEMPORARIO=$(mktemp -d)
printf '%%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\n%%%%EOF\n' \
  > "$DIRETORIO_TEMPORARIO/documento.pdf"
printf '\211PNG\r\n\032\n\000' > "$DIRETORIO_TEMPORARIO/imagem.png"

testar_upload_midia() {
  arquivo=$1
  gravacao_do_composer=${2:-false}
  resposta=$(curl --fail-with-body --silent --show-error \
    --header 'Host: crm.ws.test' \
    --header "Authorization: Bearer $access_token" \
    --form "arquivo=@$arquivo" \
    --form "gravacaoDoComposer=$gravacao_do_composer" \
    http://127.0.0.1:18080/api/v1/atendimentos/e1720000-0000-4000-8000-000000000002/mensagens/midia)
  printf '%s' "$resposta" | grep --quiet '"statusEntrega":"PENDENTE"' || {
    echo "upload empacotado nao foi persistido: $resposta" >&2
    exit 1
  }
}

testar_upload_midia "$DIRETORIO_TEMPORARIO/documento.pdf"
testar_upload_midia "$DIRETORIO_TEMPORARIO/imagem.png"

# Gera uma gravacao AAC dentro da propria imagem empacotada. Assim o smoke
# tambem prova que o FFmpeg converte o audio do composer para AAC/ADTS antes
# de persistir no mesmo MinIO real usado pelos anexos comuns.
docker run --rm --entrypoint ffmpeg synapse-backend-websocket-test:local \
  -hide_banner -loglevel error -f lavfi \
  -i sine=frequency=1000:duration=0.2 -c:a aac \
  -movflags frag_keyframe+empty_moov -f mp4 pipe:1 \
  > "$DIRETORIO_TEMPORARIO/gravacao.m4a"
testar_upload_midia "$DIRETORIO_TEMPORARIO/gravacao.m4a" true

total_midias=$(docker compose -f "$COMPOSE" exec --no-TTY postgres psql \
  --username synapse_ws --dbname synapse_ws --tuples-only --no-align \
  --command "SELECT count(*) FROM mensagem WHERE atendimento_id = 'e1720000-0000-4000-8000-000000000002' AND midia_url IS NOT NULL")
[ "$total_midias" = "3" ] || {
  echo "smoke esperava 3 mídias persistidas, encontrou $total_midias" >&2
  exit 1
}
total_audio_aac=$(docker compose -f "$COMPOSE" exec --no-TTY postgres psql \
  --username synapse_ws --dbname synapse_ws --tuples-only --no-align \
  --command "SELECT count(*) FROM mensagem WHERE atendimento_id = 'e1720000-0000-4000-8000-000000000002' AND tipo = 'AUDIO' AND midia_metadados ->> 'mimetype' = 'audio/aac'")
[ "$total_audio_aac" = "1" ] || {
  echo "smoke nao encontrou a gravacao convertida para AAC/ADTS" >&2
  exit 1
}
echo 'uploads empacotados confirmados contra MinIO real: PDF=200, PNG=200, audio AAC/ADTS=200, mensagens=3'

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
