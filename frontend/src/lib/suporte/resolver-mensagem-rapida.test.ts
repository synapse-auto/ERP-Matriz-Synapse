import { describe, expect, it } from "vitest";

import { resolverMensagemRapida } from "./resolver-mensagem-rapida";

describe("resolverMensagemRapida", () => {
  it("resolve somente variáveis documentadas com dados do lead", () => {
    expect(resolverMensagemRapida("Olá, {nome} da {empresa}!", { nome: "Ana", empresa: "Vidros" }))
      .toEqual({ texto: "Olá, Ana da Vidros!", pendentes: [] });
  });

  it("mantém variável ausente ou não autorizada identificada", () => {
    expect(resolverMensagemRapida("CPF {cpf} de {nome}", { nome: "Ana" }))
      .toEqual({ texto: "CPF {cpf} de Ana", pendentes: ["cpf"] });
  });
});
