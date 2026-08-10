import { describe, expect, it } from "vitest";

import { CRITERIO_SEM_FILTRO } from "./api";
import { FILTROS_RAPIDOS_VAZIOS, type FiltroAtivo } from "./types";
import { criterioDosFiltrosAtivos, SEM_RESPONSAVEL } from "./use-agenda";

const TAGS = [
  { id: "tag-urgente", nome: "Urgente", cor: "var(--primary)", icone: null },
  { id: "tag-retorno", nome: "Retorno", cor: "var(--secondary)", icone: null },
];

describe("critério da barra da Agenda", () => {
  it("mantém o critério neutro quando não há filtros", () => {
    expect(criterioDosFiltrosAtivos(FILTROS_RAPIDOS_VAZIOS, [], TAGS)).toEqual(
      CRITERIO_SEM_FILTRO,
    );
  });

  it("compõe a busca livre com OU e traduz nome de tag para o id aceito pelo backend", () => {
    expect(
      criterioDosFiltrosAtivos(
        { ...FILTROS_RAPIDOS_VAZIOS, busca: "urge" },
        [],
        TAGS,
      ),
    ).toEqual({
      tipo: "COMPOSTO",
      conector: "OR",
      criterios: [
        { tipo: "SIMPLES", campo: "nome", operador: "CONTEM", valor: "urge" },
        { tipo: "SIMPLES", campo: "telefone", operador: "CONTEM", valor: "urge" },
        { tipo: "SIMPLES", campo: "cpf", operador: "CONTEM", valor: "urge" },
        { tipo: "SIMPLES", campo: "tag", operador: "EM", valores: ["tag-urgente"] },
      ],
    });
  });

  it("liga grupos rápidos e avançados por E e preserva sem responsável", () => {
    const avancado: FiltroAtivo = {
      id: "filtro-1",
      campo: {
        apelido: "semRetornoDias",
        rotulo: "Dias sem retorno",
        tipo: "NUMERO",
        operadores: ["MAIOR_QUE"],
        opcoes: [],
      },
      operador: "MAIOR_QUE",
      valor: "30",
      rotuloValor: "Dias sem retorno maior que 30",
    };

    const criterio = criterioDosFiltrosAtivos(
      {
        busca: "",
        etapas: ["etapa-1", "etapa-2"],
        atendentes: ["usuario-1", SEM_RESPONSAVEL],
        cidades: ["Taguatinga · DF"],
        tags: ["tag-urgente"],
      },
      [avancado],
      TAGS,
    );

    expect(criterio).toMatchObject({ tipo: "COMPOSTO", conector: "AND" });
    if (criterio.tipo !== "COMPOSTO") throw new Error("critério deveria ser composto");
    expect(criterio.criterios).toContainEqual({
      tipo: "SIMPLES",
      campo: "etapa",
      operador: "EM",
      valores: ["etapa-1", "etapa-2"],
    });
    expect(criterio.criterios).toContainEqual({
      tipo: "COMPOSTO",
      conector: "OR",
      criterios: [
        {
          tipo: "SIMPLES",
          campo: "atendenteResponsavel",
          operador: "EM",
          valores: ["usuario-1"],
        },
        { tipo: "SIMPLES", campo: "atendenteResponsavel", operador: "VAZIO" },
      ],
    });
    expect(criterio.criterios).toContainEqual({
      tipo: "SIMPLES",
      campo: "semRetornoDias",
      operador: "MAIOR_QUE",
      valor: "30",
    });
  });
});
