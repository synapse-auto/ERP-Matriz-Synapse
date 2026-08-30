import { describe, expect, it } from "vitest";

import {
  analisarVariaveisDoCorpo,
  interpolarCatalogo,
  rotulosDasVariaveis,
} from "./variaveis-do-template";

describe("variaveis do template", () => {
  it("lista todos os indices presentes, sem repetir o mesmo numero", () => {
    const analise = analisarVariaveisDoCorpo("Ola {{1}}, {{2}}, {{3}} e {{4}}. {{1}} de novo.");

    expect(analise.erro).toBeNull();
    expect(rotulosDasVariaveis(analise.indices)).toBe("{{1}}, {{2}}, {{3}}, {{4}}");
  });

  it("aponta o indice ausente quando a sequencia pula", () => {
    const analise = analisarVariaveisDoCorpo("Ola {{1}} e {{3}}");

    expect(analise.erro).toEqual({ tipo: "ausente", indice: 2 });
  });

  it("rejeita indice menor que 1", () => {
    const analise = analisarVariaveisDoCorpo("Ola {{0}}");

    expect(analise.erro).toEqual({ tipo: "invalido", indice: 0 });
  });

  it("interpola a lista no texto de ajuda", () => {
    expect(
      interpolarCatalogo("Variáveis sequenciais: {lista}", {
        lista: "{{1}}, {{2}}, {{3}}, {{4}}",
      }),
    ).toBe("Variáveis sequenciais: {{1}}, {{2}}, {{3}}, {{4}}");
  });
});
