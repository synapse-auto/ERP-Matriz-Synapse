import { describe, expect, it } from "vitest";

import { janelaTextoLivreAberta } from "./janela-24h";

describe("janelaTextoLivreAberta", () => {
  const agora = new Date("2026-08-03T12:00:00Z");

  it("sem última mensagem do lead, considera fechada", () => {
    expect(janelaTextoLivreAberta(null, agora)).toBe(false);
  });

  it("23h59 depois da última mensagem do lead, ainda aberta", () => {
    const ultima = new Date(agora.getTime() - (23 * 60 + 59) * 60 * 1000).toISOString();
    expect(janelaTextoLivreAberta(ultima, agora)).toBe(true);
  });

  it("24h01 depois, já fechada", () => {
    const ultima = new Date(agora.getTime() - (24 * 60 + 1) * 60 * 1000).toISOString();
    expect(janelaTextoLivreAberta(ultima, agora)).toBe(false);
  });

  it("exatamente 24h, fechada (limite exclusivo)", () => {
    const ultima = new Date(agora.getTime() - 24 * 60 * 60 * 1000).toISOString();
    expect(janelaTextoLivreAberta(ultima, agora)).toBe(false);
  });
});
