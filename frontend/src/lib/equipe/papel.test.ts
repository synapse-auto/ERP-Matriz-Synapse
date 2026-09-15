import { describe, expect, it } from "vitest";

import { recebeAtendimento, visivelNaEquipe } from "./papel";

describe("recebeAtendimento", () => {
  it("atendente e subgestor recebem; gestor e administrador nao", () => {
    expect(recebeAtendimento("ATENDENTE")).toBe(true);
    expect(recebeAtendimento("SUBGESTOR")).toBe(true);
    expect(recebeAtendimento("GESTOR")).toBe(false);
    expect(recebeAtendimento("ADMINISTRADOR")).toBe(false);
    expect(recebeAtendimento(null)).toBe(false);
  });
});

describe("visivelNaEquipe", () => {
  it("mostra os papéis da equipe, mas oculta administrador e valor ausente", () => {
    expect(visivelNaEquipe("ATENDENTE")).toBe(true);
    expect(visivelNaEquipe("SUBGESTOR")).toBe(true);
    expect(visivelNaEquipe("GESTOR")).toBe(true);
    expect(visivelNaEquipe("ADMINISTRADOR")).toBe(false);
    expect(visivelNaEquipe(null)).toBe(false);
    expect(visivelNaEquipe(undefined)).toBe(false);
  });
});
