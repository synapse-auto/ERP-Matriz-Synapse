#!/usr/bin/env python3
"""Regressao para as labels dos webhooks no Compose Path unico do Dokploy."""

from __future__ import annotations

import json
import os
import re
import subprocess
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
STACK = ROOT / "docker" / "dokploy-stack.yml"
OVERRIDES = (
    ROOT / "docker" / "dokploy-z-api-transicao.override.yml",
    ROOT / "docker" / "dokploy-z-api-homologacao.override.yml",
)
PREFIX = "ci-webhook"
DOMAIN = "crm.example.test"


def renderizar(override: Path | None = None) -> dict:
    texto = STACK.read_text(encoding="utf-8")
    ambiente = os.environ.copy()
    for nome, operador in re.findall(r"\$\{([A-Z0-9_]+)([^}]*)}", texto):
        ambiente.pop(nome, None)
        if operador.startswith(":?"):
            ambiente[nome] = "valor-sintetico-ci"
    ambiente.update(
        SYNAPSE_DOMINIO=DOMAIN,
        TRAEFIK_ROUTER_PREFIX=PREFIX,
        SYNAPSE_TIMEZONE="UTC",
    )

    with tempfile.TemporaryDirectory(prefix="synapse-dokploy-ci-") as temporario:
        env_vazio = Path(temporario) / "empty.env"
        env_vazio.write_text("", encoding="utf-8")
        comando = ["docker", "compose", "--env-file", str(env_vazio), "-f", str(STACK)]
        if override is not None:
            comando.extend(["-f", str(override)])
        comando.extend(["config", "--format", "json"])
        resultado = subprocess.run(
            comando,
            cwd=ROOT,
            env=ambiente,
            capture_output=True,
            text=True,
            check=False,
            timeout=45,
        )
    if resultado.returncode != 0:
        raise SystemExit("docker compose config falhou para a stack sintetica")
    return json.loads(resultado.stdout)


def labels_backend(composto: dict) -> dict:
    labels = composto.get("services", {}).get("backend", {}).get("deploy", {}).get("labels")
    if not isinstance(labels, dict):
        raise SystemExit("labels deploy.labels do backend ausentes ou em formato inesperado")
    return labels


base = renderizar()
labels = labels_backend(base)
labels_combinadas = {override.name: labels_backend(renderizar(override)) for override in OVERRIDES}

falhas: list[str] = []


def exigir(chave: str, esperado: str) -> None:
    if labels.get(chave) != esperado:
        falhas.append(chave)


router = f"traefik.http.routers.{PREFIX}-transicao."
middleware = f"{PREFIX}-webhook-sem-origin"
exigir(
    router + "rule",
    f"Host(`{DOMAIN}`) && (PathPrefix(`/webhook/z-api`) || PathPrefix(`/webhook/canal`))",
)
exigir(router + "entrypoints", "websecure")
exigir(router + "tls", "true")
exigir(router + "tls.certresolver", "letsencrypt")
exigir(router + "priority", "150")
exigir(router + "service", f"{PREFIX}-backend")
exigir(router + "middlewares", middleware)
exigir(router + "observability.accessLogs", "false")
exigir(router + "observability.tracing", "false")
exigir(
    f"traefik.http.middlewares.{middleware}.headers.customrequestheaders.Origin",
    "",
)

backend_router = f"traefik.http.routers.{PREFIX}-backend."
if labels.get(backend_router + "middlewares"):
    falhas.append("middleware nao pode ser aplicado ao router geral da API")
for nome, labels_override in labels_combinadas.items():
    if labels_override != labels:
        falhas.append(f"{nome} alterou labels em relacao ao Compose Path")

if falhas:
    raise SystemExit("regressao de roteamento webhook Dokploy: " + "; ".join(falhas))

print("OK: Compose Path sozinho e overrides legados mantem router seguro dos webhooks")
