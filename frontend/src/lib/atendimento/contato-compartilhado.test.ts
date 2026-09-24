import { describe, expect, it } from "vitest";

import {
  contatosDaMensagem,
  numeroDiscavel,
  textoCopiavelDosContatos,
} from "./contato-compartilhado";

describe("contatosDaMensagem", () => {
  it("lê nome, números, waId e tipo, preservando a ordem", () => {
    const contatos = contatosDaMensagem(JSON.stringify({
      contatos: [
        { nome: "A", telefones: [{ numero: "1", tipo: "WORK" }, { numero: "2", waId: "552" }] },
        { nome: "B", telefones: [] },
      ],
    }));

    expect(contatos).toEqual([
      { nome: "A", telefones: [{ numero: "1", tipo: "WORK", waId: undefined }, { numero: "2", waId: "552", tipo: undefined }] },
      { nome: "B", telefones: [] },
    ]);
  });

  it("ignora entradas sem nome nem telefone e JSON ilegível", () => {
    expect(contatosDaMensagem(JSON.stringify({ contatos: [{}, { telefones: [{ numero: " " }] }] }))).toEqual([]);
    expect(contatosDaMensagem("{quebrado")).toEqual([]);
    expect(contatosDaMensagem(null)).toEqual([]);
    expect(contatosDaMensagem(JSON.stringify({ latitude: 1 }))).toEqual([]);
  });
});

describe("numeroDiscavel", () => {
  it.each([
    ["+55 61 98888-0000", "+5561988880000"],
    ["(61) 3000-0000", "6130000000"],
    ["5561988880000", "5561988880000"],
  ])("aceita %s", (numero, esperado) => {
    expect(numeroDiscavel(numero)).toBe(esperado);
  });

  it.each(["123", "ramal 12", "", "+55 61 9 8888 0000 0000 00", "61+3000-0000"])(
    "recusa %s",
    (numero) => {
      expect(numeroDiscavel(numero)).toBeNull();
    },
  );
});

describe("textoCopiavelDosContatos", () => {
  it("monta uma linha por contato com nome e números", () => {
    expect(textoCopiavelDosContatos([
      { nome: "A", telefones: [{ numero: "1" }, { numero: "2" }] },
      { telefones: [{ numero: "3" }] },
    ])).toBe("A 1 2\n3");
    expect(textoCopiavelDosContatos([])).toBeNull();
  });
});
