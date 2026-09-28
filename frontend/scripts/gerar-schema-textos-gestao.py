#!/usr/bin/env python3
"""Gera src/lib/config/schema-gestao.ts a partir da secao "gestao" de textos.json (docs/47).

Fonte unica: textos.json. O schema gerado da default a cada campo para tolerar o rollout
independente de frontend e backend. Uso: python3 scripts/gerar-schema-textos-gestao.py
"""
import collections
import json
import pathlib

RAIZ = pathlib.Path(__file__).resolve().parents[2]
TEXTOS = RAIZ / "backend/crm-app/src/main/resources/textos.json"
SAIDA = RAIZ / "frontend/src/lib/config/schema-gestao.ts"


def zod(valor, ind=2):
    pad = " " * ind
    if isinstance(valor, str):
        return f"z.string().default({json.dumps(valor, ensure_ascii=False)})"
    campos = ",\n".join(f"{pad}  {json.dumps(k)}: {zod(v, ind + 2)}" for k, v in valor.items())
    return f"z.object({{\n{campos},\n{pad}}}).default({{}})"


def main():
    # Encoding e fim de linha explicitos: o padrao do Windows (cp1252, CRLF) corrompe o catalogo.
    gestao = json.loads(TEXTOS.read_text(encoding="utf-8"), object_pairs_hook=collections.OrderedDict)["gestao"]
    fixos = collections.OrderedDict((k, v) for k, v in gestao.items() if k not in ("capacidades", "modulos"))
    corpo = zod(fixos)[: -len(".default({})")]
    modulos = json.dumps(gestao["modulos"], ensure_ascii=False, indent=2)
    capacidades = json.dumps(gestao["capacidades"], ensure_ascii=False, indent=2)
    SAIDA.write_text(f'''/*
 * GERADO a partir da secao "gestao" de textos.json (docs/47). Nao editar a mao: altere
 * textos.json e regenere com scripts/gerar-schema-textos-gestao.py.
 *
 * Todos os campos tem default: frontend e backend tem rollout independente, e um backend
 * anterior a Gestao serve textos.json sem esta secao (mesma regra do resto do TextosSchema).
 */
import {{ z }} from "zod";

const MODULOS_PADRAO: Record<string, {{ rotulo: string; descricao: string }}> = {modulos};

const CAPACIDADES_PADRAO: Record<string, string> = {capacidades};

export const GestaoTextosSchema = {corpo}.extend({{
  modulos: z.record(z.string(), z.object({{ rotulo: z.string(), descricao: z.string() }})).default(MODULOS_PADRAO),
  capacidades: z.record(z.string(), z.string()).default(CAPACIDADES_PADRAO),
}}).default({{}});
''', encoding="utf-8", newline="\n")


if __name__ == "__main__":
    main()
