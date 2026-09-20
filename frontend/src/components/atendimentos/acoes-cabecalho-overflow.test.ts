import { describe, expect, it } from "vitest";

import { distribuirAcoesPorLargura } from "./acoes-cabecalho-overflow";

const acoes = [
  { id: "finalizar", prioridade: 100 },
  { id: "transferir", prioridade: 90 },
  { id: "convidar", prioridade: 80 },
  { id: "buscar", prioridade: 20 },
];

const larguras = {
  finalizar: 96,
  transferir: 104,
  convidar: 112,
  buscar: 128,
};

describe("distribuirAcoesPorLargura", () => {
  it("mantém todas as ações no header quando há espaço", () => {
    expect(distribuirAcoesPorLargura(acoes, larguras, 480)).toEqual({
      visiveis: ["finalizar", "transferir", "convidar", "buscar"],
      excedentes: [],
    });
  });

  it("preserva as ações de maior prioridade e manda as excedentes ao menu", () => {
    expect(distribuirAcoesPorLargura(acoes, larguras, 250)).toEqual({
      visiveis: ["finalizar", "transferir"],
      excedentes: ["convidar", "buscar"],
    });
  });

  it("recalcula a distribuição quando o espaço muda", () => {
    const estreito = distribuirAcoesPorLargura(acoes, larguras, 170);
    const largo = distribuirAcoesPorLargura(acoes, larguras, 480);

    expect(estreito.excedentes).toEqual(["transferir", "convidar", "buscar"]);
    expect(largo.excedentes).toEqual([]);
  });

  it("não exibe ações que já foram removidas por autorização", () => {
    expect(
      distribuirAcoesPorLargura(
        acoes.filter((acao) => acao.id !== "convidar"),
        larguras,
        250,
      ),
    ).toEqual({
      visiveis: ["finalizar", "transferir"],
      excedentes: ["buscar"],
    });
  });
});
