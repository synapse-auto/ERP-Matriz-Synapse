#!/usr/bin/env python3
"""Inspecao manual de um prefixo de audio. Nao salva arquivo nem reprocessa evento."""

import argparse
import http.client
import ipaddress
import json
import re
import socket
import ssl
import subprocess
import time
from urllib.parse import urlsplit


HOST = "f004.backblazeb2.com"  # Host comprovado nestas entradas; nao e allowlist do produto.
LIMITE = 16 * 1024 * 1024


def executar(comando, entrada=None):
    resultado = subprocess.run(comando, input=entrada, capture_output=True, text=True, timeout=20)
    if resultado.returncode:
        raise ValueError("COLETA_LOCAL_FALHOU")
    return resultado.stdout.strip()


def cabecalho(prefixo):
    resultado = {"ogg": prefixo[:4] == b"OggS", "opus_head": False}
    if len(prefixo) < 27 or not resultado["ogg"]:
        return resultado
    inicio = 27 + prefixo[26]
    if len(prefixo) < inicio + 19 or prefixo[inicio:inicio + 8] != b"OpusHead":
        return resultado
    resultado.update(opus_head=True, versao=prefixo[inicio + 8], canais=prefixo[inicio + 9],
                     taxa_original_hz=int.from_bytes(prefixo[inicio + 12:inicio + 16], "little"),
                     mapping_family=prefixo[inicio + 18])
    return resultado


def tags_validas(dados, inicio, tamanho):
    fim = inicio + tamanho
    if tamanho < 16 or dados[inicio:inicio + 8] != b"OpusTags":
        return False
    vendor = int.from_bytes(dados[inicio + 8:inicio + 12], "little")
    if vendor > tamanho - 16:
        return False
    cursor = inicio + 12 + vendor
    comentarios = int.from_bytes(dados[cursor:cursor + 4], "little")
    cursor += 4
    if comentarios > (fim - cursor) // 4:
        return False
    for _ in range(comentarios):
        if fim - cursor < 4:
            return False
        comprimento = int.from_bytes(dados[cursor:cursor + 4], "little")
        cursor += 4
        if comprimento > fim - cursor:
            return False
        cursor += comprimento
    return True


def estrutura(dados):
    posicao = paginas = flags = granule = 0
    crc_validos = sequencia_contigua = stream_unico = sem_continuacoes = pacotes_encerrados = True
    serial = None
    pacotes = 0
    tags = head = False
    granule_anterior = 0
    granules_validos = flags_validas = flags_entrada_validas = tamanhos_validos = True
    while posicao < len(dados):
        if len(dados) - posicao < 27 or dados[posicao:posicao + 4] != b"OggS":
            return {"estrutura_paginas": "CABECALHO_TRUNCADO_OU_INVALIDO", "paginas": paginas}
        if dados[posicao + 4] != 0:
            return {"estrutura_paginas": "VERSAO_OGG_INVALIDA", "paginas": paginas}
        fim_tabela = posicao + 27 + dados[posicao + 26]
        if fim_tabela > len(dados):
            return {"estrutura_paginas": "TABELA_TRUNCADA", "paginas": paginas}
        fim_pagina = fim_tabela + sum(dados[posicao + 27:fim_tabela])
        if fim_pagina > len(dados):
            return {"estrutura_paginas": "CORPO_TRUNCADO", "paginas": paginas}
        flags = dados[posicao + 5]
        flags_validas &= flags == (2 if paginas == 0 else 0)
        flags_entrada_validas &= (flags == 2 if paginas == 0
                                  else flags == 0 or (flags == 4 and fim_pagina == len(dados)))
        atual = int.from_bytes(dados[posicao + 14:posicao + 18], "little")
        if serial is None:
            serial = atual
        stream_unico &= atual == serial
        sequencia_contigua &= int.from_bytes(dados[posicao + 18:posicao + 22], "little") == paginas
        sem_continuacoes &= not bool(flags & 1)
        pacotes_encerrados &= fim_tabela > posicao + 27 and dados[fim_tabela - 1] < 255
        inicio_pacote = fim_tabela
        tamanho_pacote = 0
        for segmento in dados[posicao + 27:fim_tabela]:
            tamanho_pacote += segmento
            if segmento == 255:
                continue
            tamanhos_validos &= tamanho_pacote > 0
            if pacotes == 0:
                h = dados[inicio_pacote:inicio_pacote + tamanho_pacote]
                head = (tamanho_pacote == 19 and h[:8] == b"OpusHead" and h[8:10] == bytes([1, 1])
                        and h[18] == 0)
            if pacotes == 1:
                tags = tags_validas(dados, inicio_pacote, tamanho_pacote)
            pacotes += 1
            inicio_pacote += tamanho_pacote
            tamanho_pacote = 0
        crc = 0
        for i in range(posicao, fim_pagina):
            crc ^= (0 if posicao + 22 <= i < posicao + 26 else dados[i]) << 24
            for _ in range(8):
                crc = ((crc << 1) ^ (0x04c11db7 if crc & 0x80000000 else 0)) & 0xffffffff
        crc_validos &= crc == int.from_bytes(dados[posicao + 22:posicao + 26], "little")
        granule = int.from_bytes(dados[posicao + 6:posicao + 14], "little", signed=True)
        granules_validos &= granule >= granule_anterior
        granule_anterior = granule
        paginas += 1
        posicao = fim_pagina
    return {"estrutura_paginas": "COMPLETAS", "paginas": paginas,
            "ultima_pagina_eos": bool(flags & 4), "granule_final": granule,
            "granule_final_positivo": granule > 0, "crc_paginas_validos": crc_validos,
            "sequencia_contigua": sequencia_contigua, "stream_unico": stream_unico,
            "sem_continuacoes": sem_continuacoes, "pacotes_encerrados": pacotes_encerrados,
            "flags_perfil_sem_eos": flags_validas, "granule_sem_regressao": granules_validos,
            "flags_perfil_entrada": flags_entrada_validas,
            "opus_head_no_perfil": head, "opus_tags_validas": tags,
            "pacotes_nao_vazios": tamanhos_validos, "minimo_paginas_e_pacotes": paginas >= 3 and pacotes >= 3}


def main():
    class ArgumentosSeguros(argparse.ArgumentParser):
        def error(self, message):
            raise ValueError("ARGUMENTO_INVALIDO")

    parser = ArgumentosSeguros(description="Inspecao limitada de audio; nao persiste nem reproduz.")
    parser.add_argument("--entrada", required=True)
    parser.add_argument("--completo", action="store_true")
    argumentos = parser.parse_args()
    entrada_id = argumentos.entrada
    if not re.fullmatch(r"canal:[0-9a-f]{64}", entrada_id):
        raise ValueError("ENTRADA_INVALIDA")
    completo = argumentos.completo
    containers = executar(["docker", "ps", "--filter", "name=fmnaprod-uzapi-36acu4_postgres.1",
                           "--format", "{{.ID}}"] ).splitlines()
    if len(containers) != 1:
        raise ValueError("CONTAINER_POSTGRES_AMBIGUO")
    sql = ("BEGIN READ ONLY; SET LOCAL statement_timeout='5s'; "
           "SELECT payload::jsonb #>> '{audio,audioUrl}' FROM webhook_entrada "
           "WHERE provedor='z-api' AND id_externo='" + entrada_id + "'; ROLLBACK;")
    url = executar(["docker", "exec", "-i", containers[0], "sh", "-c",
                    'exec psql -X -qAt -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d fmna_db'], sql)
    partes = urlsplit(url)
    if (partes.scheme != "https" or partes.hostname != HOST or partes.port not in (None, 443)
            or partes.username is not None or partes.password is not None or partes.fragment
            or any(c.isspace() or ord(c) < 32 for c in url)):
        raise ValueError("REFERENCIA_RECUSADA")
    ips = list(dict.fromkeys(item[4][0] for item in socket.getaddrinfo(HOST, 443, type=socket.SOCK_STREAM)))
    if not ips or any(not ipaddress.ip_address(ip).is_global
                      or ipaddress.ip_address(ip).is_multicast for ip in ips):
        raise ValueError("DNS_RECUSADO")

    class ConexaoFixada(http.client.HTTPSConnection):
        def connect(self):
            transporte = socket.create_connection((ips[0], 443), timeout=15)
            try:
                self.sock = self._context.wrap_socket(transporte, server_hostname=HOST)
            except BaseException:
                transporte.close()
                raise

    # Sem proxy, redirect, retry, token ou Client-Token. TLS valida o hostname original.
    conexao = ConexaoFixada(HOST, timeout=15, context=ssl.create_default_context())
    try:
        destino = partes.path or "/"
        if partes.query:
            destino += "?" + partes.query
        headers = {"Accept-Encoding": "identity"}
        if not completo:
            headers["Range"] = "bytes=0-4095"
        conexao.request("GET", destino, headers=headers)
        resposta = conexao.getresponse()
        mime = resposta.getheader("Content-Type", "").split(";", 1)[0].strip().lower()
        permitidos = {"audio/ogg", "audio/opus", "application/ogg", "application/octet-stream"}
        resultado = {"entrada": entrada_id, "hostname": HOST, "http_atual": resposta.status,
                     "mime_http": mime if mime in permitidos else "OUTRO_OU_AUSENTE"}
        if completo and resposta.status == 200:
            inicio = time.monotonic()
            dados = bytearray()
            while len(dados) <= LIMITE and time.monotonic() - inicio < 30:
                parte = resposta.read(min(65536, LIMITE + 1 - len(dados)))
                if not parte:
                    break
                dados.extend(parte)
            if len(dados) > LIMITE or time.monotonic() - inicio >= 30:
                resultado["leitura"] = "LIMITE_DE_BYTES_OU_TEMPO"
            else:
                resultado.update(bytes_lidos=len(dados), **cabecalho(dados), **estrutura(dados))
        elif not completo and resposta.status in (200, 206):
            resultado.update(cabecalho(resposta.read(4096)))
        print(json.dumps(resultado))
    finally:
        conexao.close()


if __name__ == "__main__":
    try:
        main()
    except Exception:
        # Nunca imprimir excecao/traceback: bibliotecas podem incluir URL assinada.
        print(json.dumps({"etapa": "inspecao_manual", "resultado": "FALHA_NA_COLETA_SEM_DETALHES_SENSIVEIS"}))
        raise SystemExit(1)
