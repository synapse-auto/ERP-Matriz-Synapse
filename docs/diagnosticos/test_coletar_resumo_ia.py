"""Provas negativas da coleta; docker substituído, sem acesso a serviço/ambiente real."""
import contextlib
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("coleta", Path(__file__).with_name("coletar-resumo-ia.py"))
coleta = importlib.util.module_from_spec(spec)
spec.loader.exec_module(coleta)

PEDIDO = "11111111-1111-4111-8111-111111111111"
OUTRO = "22222222-2222-4222-8222-222222222222"
ARGS = ["coleta", "--servico", "backend-teste", "--solicitacao", PEDIDO,
        "--desde", "2026-10-13T00:00:00Z", "--ate", "2026-10-13T01:00:00Z"]


class ColetaResumoIaTest(unittest.TestCase):
    def test_somente_ids_etapa_http_e_resultado_sem_token_url_ou_conteudo(self):
        linha = ("2026-10-13T00:01:00.123Z [RESUMO_IA_HTTP] solicitacaoId=" + PEDIDO
                 + " atendimentoId=" + OUTRO + " leadId=" + OUTRO
                 + " etapa=webhook http=403 token=SEGREDO_INDEVIDO url=https://privado/?token=SEGREDO_INDEVIDO"
                 + " resumo=CONTEUDO_PRIVADO payload=HISTORICO_PRIVADO")
        logs = linha + "\n" + linha.replace(PEDIDO, OUTRO) + "\nlinha bruta privada\n"
        respostas = [subprocess.CompletedProcess([], 0, "ghcr.io/exemplo/backend:sha@sha256:abc\n", ""),
                     subprocess.CompletedProcess([], 0, logs, "stderr privado sem marcador")]
        saida = io.StringIO()
        with patch("sys.argv", ARGS), patch.object(coleta, "executar", side_effect=respostas) as comando:
            with contextlib.redirect_stdout(saida):
                coleta.main()
        resultado = json.loads(saida.getvalue())
        self.assertEqual(len(resultado["eventos"]), 1)
        self.assertEqual(resultado["eventos"][0]["http"], 403)
        self.assertEqual(resultado["eventos"][0]["atendimentoId"], OUTRO)
        for segredo in ("SEGREDO_INDEVIDO", "https://privado", "CONTEUDO_PRIVADO", "HISTORICO_PRIVADO", "linha bruta", "stderr privado"):
            self.assertNotIn(segredo, saida.getvalue())
        self.assertEqual([chamada.args[0][2] for chamada in comando.call_args_list], ["inspect", "logs"])

    def test_falha_do_docker_expoe_codigo_mas_nao_erro_bruto(self):
        respostas = [subprocess.CompletedProcess([], 1, "", "credencial privada"),
                     subprocess.CompletedProcess([], 1, "", "payload e telefone privados")]
        saida = io.StringIO()
        with patch("sys.argv", ARGS), patch.object(coleta, "executar", side_effect=respostas):
            with contextlib.redirect_stdout(saida):
                coleta.main()
        resultado = json.loads(saida.getvalue())
        self.assertEqual(resultado["inspect_exit_code"], 1)
        self.assertEqual(resultado["logs_exit_code"], 1)
        self.assertIsNone(resultado["imagem_configurada"])
        self.assertEqual(resultado["eventos"], [])
        self.assertNotIn("credencial privada", saida.getvalue())
        self.assertNotIn("telefone privados", saida.getvalue())

    def test_servico_invalido_e_janela_invertida_nao_executam_docker(self):
        for argumentos in (ARGS[:2] + ["backend;comando"] + ARGS[3:],
                           ARGS[:-1] + ["2026-10-12T00:00:00Z"]):
            with patch("sys.argv", argumentos), patch.object(coleta, "executar") as comando:
                with contextlib.redirect_stderr(io.StringIO()):
                    with self.assertRaises(SystemExit):
                        coleta.main()
                comando.assert_not_called()


if __name__ == "__main__":
    unittest.main()
