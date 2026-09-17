#!/usr/bin/env bash
set -euo pipefail

arquivo_json="$(mktemp)"
trap 'rm -f "$arquivo_json"' EXIT

docker compose -f docker/dokploy-stack.yml config --format json >"$arquivo_json"

python3 - "$arquivo_json" <<'PY'
import json
import re
import sys


def segundos(valor: str) -> int:
    texto = str(valor)
    partes = re.findall(r"(\d+)(ms|s|m|h)", texto)
    if not partes or "".join(f"{quantidade}{unidade}" for quantidade, unidade in partes) != texto:
        raise AssertionError(f"duracao invalida: {valor!r}")
    multiplicadores = {"ms": 0.001, "s": 1, "m": 60, "h": 3600}
    resultado = int(sum(int(quantidade) * multiplicadores[unidade] for quantidade, unidade in partes))
    if resultado < 1:
        raise AssertionError(f"duracao precisa ser positiva: {valor!r}")
    return resultado


def texto_healthcheck(test) -> str:
    if isinstance(test, list):
        return " ".join(str(item) for item in test)
    return str(test)


def monitor_cobre_inicio(start_periodo: int, monitor: int) -> bool:
    return monitor > start_periodo


with open(sys.argv[1], encoding="utf-8") as arquivo:
    compose = json.load(arquivo)

services = compose.get("services", {})
for nome, monitor_minimo in {"backend": 180, "frontend": 90}.items():
    service = services.get(nome)
    assert service is not None, f"servico ausente: {nome}"
    healthcheck = service.get("healthcheck", {})
    start_periodo = segundos(healthcheck.get("start_period"))
    comando = texto_healthcheck(healthcheck.get("test"))
    assert "/health/liveness" in comando, f"healthcheck de container mudou para readiness: {nome}"

    deploy = service.get("deploy", {})
    assert deploy.get("replicas") == 1, f"{nome} precisa continuar com uma replica por padrao"
    update = deploy.get("update_config", {})
    rollback = deploy.get("rollback_config", {})
    assert update.get("order") == "start-first", f"update sem start-first: {nome}"
    assert rollback.get("order") == "start-first", f"rollback sem start-first: {nome}"
    assert update.get("failure_action") == "rollback", f"update sem rollback: {nome}"
    update_monitor = segundos(update.get("monitor"))
    rollback_monitor = segundos(rollback.get("monitor"))
    assert update_monitor >= monitor_minimo, f"monitor de update curto: {nome}"
    assert rollback_monitor >= monitor_minimo, f"monitor de rollback curto: {nome}"
    assert monitor_cobre_inicio(start_periodo, update_monitor), (
        f"update monitor nao cobre start_period: {nome}"
    )
    assert monitor_cobre_inicio(start_periodo, rollback_monitor), (
        f"rollback monitor nao cobre start_period: {nome}"
    )

    labels = deploy.get("labels", service.get("labels", []))
    labels_text = " ".join(str(label) for label in labels)
    assert "/health/readiness" in labels_text, f"Traefik nao usa readiness: {nome}"

# Protege contra a regressao exata do incidente: monitorar menos tempo que a inicializacao
# permite que o Swarm marque o task como falho antes do readiness real responder.
assert not monitor_cobre_inicio(90, 60)
assert monitor_cobre_inicio(90, 180)
assert not monitor_cobre_inicio(15, 15)
assert monitor_cobre_inicio(15, 90)
print("politica de rollout valida: start-first, rollback, readiness no Traefik e monitores suficientes")
PY
