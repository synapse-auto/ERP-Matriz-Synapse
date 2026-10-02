import { describe, expect, it } from "vitest";

import { textosReais } from "@/test/textos-reais";

import { motivoDaPausaLegivel } from "./motivo-de-pausa";

const textos = textosReais.campanhas.detalhe.alertaPausa;

describe("motivoDaPausaLegivel", () => {
  it("traduz a taxa de falha com o percentual", () => {
    expect(motivoDaPausaLegivel("TAXA_DE_FALHA:100", textos)).toBe("A taxa de falha chegou a 100%, acima do limiar configurado.");
  });

  it("traduz o código de erro da Meta", () => {
    expect(motivoDaPausaLegivel("ERRO_DA_META:131048", textos)).toContain("131048");
  });

  it("traduz a causa sem detalhe", () => {
    expect(motivoDaPausaLegivel("CANAL_SEM_CAMPANHA", textos)).toBe(textos.causas.CANAL_SEM_CAMPANHA);
  });

  it("não esconde uma causa nova: mostra o código bruto no texto de reserva", () => {
    expect(motivoDaPausaLegivel("NOVA_CAUSA:x", textos)).toBe("Código do motivo: NOVA_CAUSA:x");
  });

  it("sem motivo registrado, devolve o traço", () => {
    expect(motivoDaPausaLegivel(null, textos)).toBe("-");
  });
});
