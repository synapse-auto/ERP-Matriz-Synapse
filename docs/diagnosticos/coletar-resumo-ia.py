"""Coleta somente leitura, stdout sanitizado; nunca imprime env/log/payload bruto."""
import argparse
import datetime
import json
import re
import subprocess
import uuid


def executar(argumentos):
    return subprocess.run(argumentos, capture_output=True, text=True, check=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--servico", required=True)
    parser.add_argument("--solicitacao", required=True, type=uuid.UUID)
    parser.add_argument("--desde", required=True)
    parser.add_argument("--ate", required=True)
    args = parser.parse_args()
    inicio = datetime.datetime.fromisoformat(args.desde.replace("Z", "+00:00"))
    fim = datetime.datetime.fromisoformat(args.ate.replace("Z", "+00:00"))
    if inicio.tzinfo is None or fim.tzinfo is None or inicio >= fim:
        parser.error("janela exige fuso explícito e início anterior ao fim")
    if not re.fullmatch(r"[A-Za-z0-9_.-]+", args.servico):
        parser.error("nome de serviço inválido")
    imagem = executar(["docker", "service", "inspect", args.servico, "--format",
                       "{{.Spec.TaskTemplate.ContainerSpec.Image}}"])
    logs = executar(["docker", "service", "logs", args.servico, "--timestamps",
                     "--since", args.desde, "--until", args.ate])
    eventos = []
    for linha in (logs.stdout + "\n" + logs.stderr).splitlines():
        if "solicitacaoId=" + str(args.solicitacao) not in linha:
            continue
        if not re.search(r"\[RESUMO_IA_(HTTP|ENTREGA)\]", linha):
            continue
        evento = {"solicitacaoId": str(args.solicitacao)}
        horario = re.search(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z", linha)
        if horario:
            evento["horario_utc"] = horario.group(0)
        for campo in ("atendimentoId", "leadId"):
            valor = re.search(r"\b" + campo + r"=([0-9a-fA-F-]{36})\b", linha)
            if valor:
                try:
                    evento[campo] = str(uuid.UUID(valor.group(1)))
                except ValueError:
                    pass
        for campo, padrao in (("etapa", r"webhook|outbox"), ("http", r"[1-5][0-9]{2}"),
                              ("resultado", r"ACEITO|TENTAR_NOVAMENTE|RECUSADO|AUTENTICACAO_RECUSADA|RESULTADO_INCERTO|INCERTO")):
            valor = re.search(r"\b" + campo + r"=(" + padrao + r")\b", linha)
            if valor:
                evento[campo] = int(valor.group(1)) if campo == "http" else valor.group(1)
        eventos.append(evento)
    # Apenas formato público de referência de imagem. Nunca imprimir saída de erro do docker.
    referencia = imagem.stdout.strip()
    if not re.fullmatch(r"[a-zA-Z0-9./_:@-]+", referencia):
        referencia = None
    print(json.dumps({"servico": args.servico, "solicitacaoId": str(args.solicitacao),
                      "imagem_configurada": referencia, "inspect_exit_code": imagem.returncode,
                      "logs_exit_code": logs.returncode, "eventos": eventos,
                      "limite": "ausência de linhas não prova ausência de tentativa; não inclui logs antigos sem correlação"},
                     ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
