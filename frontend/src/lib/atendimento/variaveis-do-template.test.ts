import { describe, expect, it } from "vitest";

import {
  analisarVariaveisDoCorpo,
  interpolarCatalogo,
  interpolarCorpoDoTemplate,
  interpolarPreviaDoTemplate,
  parametrosDoTemplatePreenchidos,
  rotulosDasVariaveis,
  trechoDaVariavel,
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

describe("prévia e contexto das variáveis", () => {
  it("substitui o marcador preenchido e mantém o vazio", () => {
    expect(
      interpolarCorpoDoTemplate("Olá {{1}}, o pedido {{2}} ficou pronto.", ["Maria", ""]),
    ).toBe("Olá Maria, o pedido {{2}} ficou pronto.");
  });

  it("na prévia troca o marcador vazio por um rótulo legível, sem alterar o interpolador cru", () => {
    expect(
      interpolarPreviaDoTemplate(
        "Olá {{1}}, o pedido {{2}} ficou pronto.",
        ["Maria", ""],
        "[variável {indice}]",
      ),
    ).toBe("Olá Maria, o pedido [variável 2] ficou pronto.");
    expect(
      interpolarCorpoDoTemplate("Olá {{1}}, o pedido {{2}} ficou pronto.", ["Maria", ""]),
    ).toBe("Olá Maria, o pedido {{2}} ficou pronto.");
  });

  it("mostra o trecho do corpo em que a variável cai", () => {
    expect(trechoDaVariavel("Olá {{1}}, o orçamento ficou pronto.", 1)).toContain("{{1}}");
    expect(trechoDaVariavel("Olá {{1}}, o orçamento ficou pronto.", 1)).toContain("Olá");
  });

  it("considera preenchido só quando nenhum valor está em branco", () => {
    expect(parametrosDoTemplatePreenchidos(["Maria", "42"])).toBe(true);
    expect(parametrosDoTemplatePreenchidos(["Maria", "  "])).toBe(false);
    expect(parametrosDoTemplatePreenchidos([])).toBe(true);
  });
});
