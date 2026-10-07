#!/usr/bin/env python3
"""Collect only whitelisted technical fields; never write raw Docker logs."""

import datetime as dt
import json
import os
import re
import subprocess
from pathlib import Path


SERVICE = "fmnaprod-uzapi-36acu4_backend"
START = dt.datetime(2026, 10, 7, 15, 35, tzinfo=dt.timezone.utc)
END = dt.datetime(2026, 10, 7, 16, 0, tzinfo=dt.timezone.utc)
IMAGE = re.compile(r"[A-Za-z0-9._/:\-]+(?:@sha256:[a-f0-9]{64})?")
TECHNICAL_ID = r"[A-Za-z0-9._+=:/\-]{1,512}"
TIMESTAMP = re.compile(r"^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d+))?(Z|[+-]\d{2}:\d{2})\s")


def identifier(line, pattern):
    match = re.search(pattern, line)
    if not match:
        return None
    value = match.group(1)
    if not re.fullmatch(TECHNICAL_ID, value) or "://" in value:
        return "FORMATO_NAO_EXPORTADO"
    return value


def sanitize(line):
    stamp = TIMESTAMP.match(line)
    if not stamp:
        return None
    fraction = (stamp.group(2) or "").ljust(6, "0")[:6]
    moment = dt.datetime.fromisoformat(stamp.group(1) + "." + fraction + stamp.group(3).replace("Z", "+00:00"))
    if not START <= moment < END:
        return None
    if "[MIDIA_NAO_RECEBIDA]" in line:
        marker = "MIDIA_NAO_RECEBIDA"
        entry = identifier(line, r"\bentrada=([^\s;]+)")
    elif "Midia do evento " in line:
        marker = "MIDIA_RETENTATIVA"
        entry = identifier(line, r"Midia do evento ([^\s;]+) ainda indisponivel")
    else:
        return None
    record = {"horario_utc": moment.isoformat(), "evento": marker, "entrada_id": entry}
    media_id = identifier(line, r"\bmidiaId=([^\s;]+)")
    if media_id is not None:
        record["midia_id"] = media_id
    stage = re.search(r"\betapa=(resolvedor|download|storage)\b", line)
    record["etapa"] = stage.group(1) if stage else "NAO_INFORMADA"
    status = re.search(r"respondeu HTTP ([1-5][0-9]{2})\b", line)
    if status:
        record["http_status"] = int(status.group(1))
    message_type = re.search(r"\btipo=(IMAGEM|DOCUMENTO|AUDIO|VIDEO)\b", line)
    if message_type:
        record["tipo"] = message_type.group(1)
    attempt = re.search(r"\btentativa=([0-9]{1,9})\b", line)
    if attempt:
        record["tentativa"] = int(attempt.group(1))
    for text, code in (
        ("resolvedor respondeu sem url utilizavel", "RESOLVEDOR_SEM_URL"),
        ("download respondeu sem bytes", "DOWNLOAD_SEM_BYTES"),
    ):
        if text in line:
            record["motivo"] = code
    return record


def main():
    # This command reads only the image field, never Config.Env or service secrets.
    image_result = subprocess.run(
        ["docker", "service", "inspect", SERVICE, "--format", "{{.Spec.TaskTemplate.ContainerSpec.Image}}"],
        capture_output=True, text=True, check=False,
    )
    if image_result.returncode:
        raise SystemExit("Falha ao consultar a imagem do servico; nenhum stderr bruto foi exportado.")
    image = image_result.stdout.strip()
    if not IMAGE.fullmatch(image):
        raise SystemExit("Formato de referencia de imagem inesperado; nenhum valor foi exportado.")
    reference, separator, digest = image.partition("@")
    last = reference.rsplit("/", 1)[-1]
    tag = last.rsplit(":", 1)[1] if ":" in last else None
    report = {
        "servico": SERVICE,
        "coletado_em_utc": dt.datetime.now(dt.timezone.utc).isoformat(),
        "janela_utc_inicio_inclusivo": START.isoformat(),
        "janela_utc_fim_exclusivo": END.isoformat(),
        "imagem_configurada": image,
        "tag": tag,
        "digest_configurado": digest if separator else None,
        "observacao_digest": "Digest da especificacao do servico; ausente nao foi inferido de uma tag mutavel.",
        "logs": [],
    }
    # Docker service logs has --since, but no --until. Bound each timestamp here.
    # Stderr is also streamed through the whitelist; no raw text is printed or saved.
    process = subprocess.Popen(
        ["docker", "service", "logs", "--raw", "--timestamps", "--since", START.isoformat(), SERVICE],
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, errors="replace",
    )
    with process.stdout:
        for line in process.stdout:
            record = sanitize(line)
            if record is not None:
                report["logs"].append(record)
    status = process.wait()
    report["logs_comando_exit_code"] = status
    report["logs"].sort(key=lambda entry: entry["horario_utc"])
    report["quantidade_logs"] = len(report["logs"])
    report["observacao_logs"] = (
        "Ausencia de linhas nao prova ausencia de tentativa: verificar retencao, tarefas antigas e logging driver. "
        "Falha no comando pode indicar coleta incompleta; detalhes brutos foram omitidos."
    )
    output = Path.cwd() / ("diagnostico-fmnaprod-midias-20261007-1535-1600-"
                           + dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%S%fZ") + ".json")
    # Exclusive creation and private permissions: never overwrite an existing file.
    descriptor = os.open(output, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8") as destination:
        json.dump(report, destination, ensure_ascii=True, indent=2)
        destination.write("\n")
    print("Diagnostico sanitizado salvo em: " + str(output))
    print("Linhas tecnicas na janela: " + str(report["quantidade_logs"]))
    print("Codigo de saida da coleta de logs: " + str(status))
    if not separator:
        print("Digest ausente na especificacao; nao foi inferido nem consultado no registry.")


if __name__ == "__main__":
    main()
