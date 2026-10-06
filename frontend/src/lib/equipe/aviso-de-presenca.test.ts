import { describe, expect, it } from "vitest";

import { ehAvisoDePresencaAlterada } from "./aviso-de-presenca";

const valido = { tipo: "PRESENCA_ALTERADA", eventoId: "e-1", dados: { status: "ONLINE" } };

describe("ehAvisoDePresencaAlterada", () => {
  it("reconhece os três estados", () => {
    for (const status of ["ONLINE", "AUSENTE", "OFFLINE"]) {
      expect(ehAvisoDePresencaAlterada({ ...valido, dados: { status } })).toBe(true);
    }
  });

  it("recusa estado desconhecido, tipo diferente, sem dados ou valor que não é objeto", () => {
    expect(ehAvisoDePresencaAlterada({ ...valido, dados: { status: "VOANDO" } })).toBe(false);
    expect(ehAvisoDePresencaAlterada({ ...valido, dados: {} })).toBe(false);
    expect(ehAvisoDePresencaAlterada({ ...valido, dados: null })).toBe(false);
    expect(ehAvisoDePresencaAlterada({ ...valido, tipo: "ACESSO_ALTERADO" })).toBe(false);
    expect(ehAvisoDePresencaAlterada(null)).toBe(false);
    expect(ehAvisoDePresencaAlterada("PRESENCA_ALTERADA")).toBe(false);
  });
});
