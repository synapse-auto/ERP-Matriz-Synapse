import { describe, expect, it } from "vitest";

import type { CampoCustomizado } from "@/lib/lead/types";

import { primeiroCampoObrigatorioAusente } from "./painel-lateral-lead";

const campoObrigatorio: CampoCustomizado = {
  chave: "codigo_obra",
  rotulo: "Código da obra",
  tipo: "TEXTO",
  opcoes: [],
  obrigatorio: true,
  filtravel: false,
  ordem: 1,
};

describe("campos customizados da ficha", () => {
  it("exige um campo obrigatório ausente antes de salvar", () => {
    expect(primeiroCampoObrigatorioAusente([campoObrigatorio], {})).toBe(campoObrigatorio);
    expect(primeiroCampoObrigatorioAusente([campoObrigatorio], { codigo_obra: "   " })).toBe(
      campoObrigatorio,
    );
  });

  it("aceita o campo obrigatório preenchido", () => {
    expect(primeiroCampoObrigatorioAusente([campoObrigatorio], { codigo_obra: "OBRA-12" })).toBeUndefined();
  });
});
