import { describe, expect, it } from "vitest";

import { recebeAtendimento } from "./papel";

describe("recebeAtendimento", () => {
  it("atendente e subgestor recebem; gestor e administrador nao", () => {
    expect(recebeAtendimento("ATENDENTE")).toBe(true);
    expect(recebeAtendimento("SUBGESTOR")).toBe(true);
    expect(recebeAtendimento("GESTOR")).toBe(false);
    expect(recebeAtendimento("ADMINISTRADOR")).toBe(false);
    expect(recebeAtendimento(null)).toBe(false);
  });
});
