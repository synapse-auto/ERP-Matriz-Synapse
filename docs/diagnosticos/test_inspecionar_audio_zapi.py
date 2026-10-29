import runpy
import struct
import unittest
from pathlib import Path
from unittest.mock import patch


COLETOR = runpy.run_path(str(Path(__file__).with_name("inspecionar-cabecalho-audio-zapi.py")))


def pagina(payload, flags, granule, seq):
    dados = bytearray(b"OggS" + bytes([0, flags]) + struct.pack("<qIII", granule, 7, seq, 0)
                      + bytes([1, len(payload)]) + payload)
    crc = 0
    for valor in dados:
        crc ^= valor << 24
        for _ in range(8):
            crc = ((crc << 1) ^ (0x04c11db7 if crc & 0x80000000 else 0)) & 0xffffffff
    dados[22:26] = struct.pack("<I", crc)
    return dados


def audio(taxa=48000, tags=None, granule=48000):
    head = b"OpusHead" + bytes([1, 1]) + struct.pack("<HIhB", 312, taxa, 0, 0)
    tags = tags if tags is not None else b"OpusTags" + bytes(8)
    return pagina(head, 2, 0, 0) + pagina(tags, 0, 0, 1) + pagina(b"\xf8\xff\xfe", 0, granule, 2)


class ColetorAudioTest(unittest.TestCase):
    def test_perfil_sem_eos_integro(self):
        r = COLETOR["estrutura"](audio())
        self.assertTrue(r["crc_paginas_validos"])
        self.assertTrue(r["opus_head_no_perfil"])
        self.assertTrue(r["opus_tags_validas"])
        self.assertTrue(r["flags_perfil_sem_eos"])
        self.assertFalse(r["ultima_pagina_eos"])

    def test_taxa_original_informativa_sem_expor_bytes(self):
        r = COLETOR["estrutura"](audio(taxa=16000))
        self.assertTrue(r["crc_paginas_validos"])
        self.assertTrue(r["opus_head_no_perfil"])
        self.assertEqual(COLETOR["cabecalho"](audio(taxa=16000))["taxa_original_hz"], 16000)

    def test_tags_malformadas_e_granule_invalido(self):
        self.assertFalse(COLETOR["estrutura"](audio(tags=b"OpusTags" + bytes([255]) * 8))["opus_tags_validas"])
        self.assertFalse(COLETOR["estrutura"](audio(granule=-1))["granule_sem_regressao"])

    def test_crc_e_truncamento(self):
        dados = audio()
        dados[-1] ^= 1
        self.assertFalse(COLETOR["estrutura"](dados)["crc_paginas_validos"])
        self.assertEqual(COLETOR["estrutura"](audio()[:-1])["estrutura_paginas"], "CORPO_TRUNCADO")

    def test_id_invalido_recusado_antes_de_docker(self):
        with patch("sys.argv", ["coletor", "--entrada", "id-invalido"]), patch("subprocess.run") as processo:
            with self.assertRaisesRegex(ValueError, "ENTRADA_INVALIDA"):
                COLETOR["main"]()
            processo.assert_not_called()


if __name__ == "__main__":
    unittest.main()
