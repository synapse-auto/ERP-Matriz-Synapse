import { describe, expect, it } from "vitest";
import { temPaginaAnteriorDoCrm } from "./retorno";

describe("retorno com fallback conservador", () => {
  it("reconhece histórico conhecido no CRM", () => {
    expect(temPaginaAnteriorDoCrm(2, "https://crm.test/login", "https://crm.test")).toBe(true);
  });
  it.each([[1,"https://crm.test/login"],[2,""],[3,"https://externo.test"],[2,"não é uma URL"]])("entrada direta/sem histórico válido usa início", (tamanho, referrer) => {
    expect(temPaginaAnteriorDoCrm(tamanho as number, referrer as string, "https://crm.test")).toBe(false);
  });
});
