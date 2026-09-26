import { describe, expect, it } from "vitest";

import {
  aplicarNivel,
  alternarAcao,
  chavesAlteradas,
  contagem,
  restaurarAcao,
  simular,
  tocaSensivel,
} from "./rascunho";
import type { Catalogo, Rascunho } from "./types";

const catalogo: Catalogo = {
  modulos: [
    {
      id: "mensagens_rapidas",
      nivelMinimoPermitido: "SEM_ACESSO",
      flag: null,
      nivelMaximoPorPapel: { ATENDENTE: "EDITAR", SUBGESTOR: "EDITAR", GESTOR: "EDITAR", ADMINISTRADOR: "EDITAR" },
      nivelPadraoPorPapel: { ATENDENTE: "EDITAR", SUBGESTOR: "EDITAR", GESTOR: "EDITAR", ADMINISTRADOR: "EDITAR" },
    },
    {
      id: "tags",
      nivelMinimoPermitido: "VER",
      flag: null,
      nivelMaximoPorPapel: { ATENDENTE: "EDITAR", SUBGESTOR: "GERENCIAR", GESTOR: "GERENCIAR", ADMINISTRADOR: "GERENCIAR" },
      nivelPadraoPorPapel: { ATENDENTE: "EDITAR", SUBGESTOR: "GERENCIAR", GESTOR: "GERENCIAR", ADMINISTRADOR: "GERENCIAR" },
    },
  ],
  capacidades: [
    { id: "mensagens_rapidas.usar", modulo: "mensagens_rapidas", nivelMinimo: "VER", tipo: "ACAO", sensivel: false, teto: ["ATENDENTE", "SUBGESTOR", "GESTOR", "ADMINISTRADOR"], delegavel: true, dependencias: [], alcancePorPapel: {} },
    { id: "mensagens_rapidas.criar", modulo: "mensagens_rapidas", nivelMinimo: "EDITAR", tipo: "ACAO", sensivel: false, teto: ["ATENDENTE", "SUBGESTOR", "GESTOR", "ADMINISTRADOR"], delegavel: true, dependencias: ["mensagens_rapidas.usar"], alcancePorPapel: {} },
    { id: "tags.aplicar", modulo: "tags", nivelMinimo: "EDITAR", tipo: "ACAO", sensivel: false, teto: ["ATENDENTE", "SUBGESTOR", "GESTOR", "ADMINISTRADOR"], delegavel: true, dependencias: [], alcancePorPapel: {} },
    { id: "tags.editar_excluir", modulo: "tags", nivelMinimo: "GERENCIAR", tipo: "ACAO", sensivel: true, teto: ["SUBGESTOR", "GESTOR", "ADMINISTRADOR"], delegavel: false, dependencias: [], alcancePorPapel: {} },
  ],
};

const perfilAtendente: Rascunho = {
  niveis: { mensagens_rapidas: "EDITAR", tags: "EDITAR" },
  acoes: { "mensagens_rapidas.usar": true, "mensagens_rapidas.criar": true, "tags.aplicar": true },
};

describe("rascunho de permissões", () => {
  it("preset de nível liga até o nível e desliga acima; nunca mostra ligado o que está bloqueado", () => {
    const soVer = aplicarNivel(perfilAtendente, catalogo, "ATENDENTE", "mensagens_rapidas", "VER");
    expect(soVer.acoes["mensagens_rapidas.criar"]).toBe(false);
    const sim = simular(catalogo, "ATENDENTE", soVer);
    expect(sim.estados["mensagens_rapidas.usar"].permitido).toBe(true);
    expect(sim.estados["mensagens_rapidas.criar"]).toMatchObject({ permitido: false });
  });

  it("modo exceção grava só a diferença; igual ao perfil volta a herdar", () => {
    const excecao = alternarAcao({ niveis: {}, acoes: {} }, "tags.aplicar", false, perfilAtendente);
    expect(excecao.acoes).toEqual({ "tags.aplicar": false });
    const deVolta = alternarAcao(excecao, "tags.aplicar", true, perfilAtendente);
    expect(deVolta.acoes).toEqual({});
    const nivel = aplicarNivel({ niveis: {}, acoes: {} }, catalogo, "ATENDENTE", "mensagens_rapidas", "VER", perfilAtendente);
    expect(nivel).toEqual({ niveis: { mensagens_rapidas: "VER" }, acoes: { "mensagens_rapidas.criar": false } });
    expect(restaurarAcao(nivel, "mensagens_rapidas.criar").acoes).toEqual({});
  });

  it("dependência negada e teto do papel aparecem com o motivo certo", () => {
    const semUsar = alternarAcao(perfilAtendente, "mensagens_rapidas.usar", false);
    const sim = simular(catalogo, "ATENDENTE", semUsar);
    expect(sim.estados["mensagens_rapidas.criar"].motivo).toBe("DEPENDENCIA");
    expect(sim.estados["tags.editar_excluir"].motivo).toBe("TETO_DO_PAPEL");
    expect(contagem(catalogo, "ATENDENTE", sim)).toEqual({ permitidas: 1, total: 3 });
  });

  it("GESTOR é fixo: tudo do teto permitido", () => {
    const sim = simular(catalogo, "GESTOR", { niveis: {}, acoes: {} });
    expect(Object.values(sim.estados).every((e) => e.permitido && e.origem === "FIXO")).toBe(true);
  });

  it("diff e sensibilidade das alterações pendentes", () => {
    const alterado = aplicarNivel(perfilAtendente, catalogo, "SUBGESTOR", "tags", "GERENCIAR");
    const chaves = chavesAlteradas(perfilAtendente, alterado);
    expect(chaves).toContain("modulo:tags");
    expect(tocaSensivel(catalogo, chaves)).toBe(true);
    expect(tocaSensivel(catalogo, ["tags.aplicar"])).toBe(false);
  });
});
