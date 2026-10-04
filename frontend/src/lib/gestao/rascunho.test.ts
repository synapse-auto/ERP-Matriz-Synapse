import { describe, expect, it } from "vitest";

import {
  aplicarNivel,
  alternarAcao,
  chavesAlteradas,
  contagem,
  restaurarAcao,
  simular,
  tocaSensivel,
  violacaoDaDelegacao,
  type EstadoDaEdicao,
} from "./rascunho";
import type { Catalogo, Rascunho } from "./types";

const catalogo: Catalogo = {
  modulos: [
    {
      id: "mensagens_rapidas",
      nivelMinimoPermitido: "SEM_ACESSO",
      flag: null,
      nivelMaximoPorPapel: { ATENDENTE: "EDITAR", OPERADOR: "EDITAR", SUBGESTOR: "EDITAR", GESTOR: "EDITAR", ADMINISTRADOR: "EDITAR" },
      nivelPadraoPorPapel: { ATENDENTE: "EDITAR", OPERADOR: "EDITAR", SUBGESTOR: "EDITAR", GESTOR: "EDITAR", ADMINISTRADOR: "EDITAR" },
    },
    {
      id: "tags",
      nivelMinimoPermitido: "VER",
      flag: null,
      nivelMaximoPorPapel: { ATENDENTE: "EDITAR", OPERADOR: "EDITAR", SUBGESTOR: "GERENCIAR", GESTOR: "GERENCIAR", ADMINISTRADOR: "GERENCIAR" },
      nivelPadraoPorPapel: { ATENDENTE: "EDITAR", OPERADOR: "EDITAR", SUBGESTOR: "GERENCIAR", GESTOR: "GERENCIAR", ADMINISTRADOR: "GERENCIAR" },
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

  describe("alçada do SUBGESTOR delegado (espelho de PoliticaDeConcessao)", () => {
    // Ação não delegável que depende de criar: o nível e a dependência também a desligam.
    const comLote: Catalogo = {
      ...catalogo,
      capacidades: [
        ...catalogo.capacidades,
        { id: "mensagens_rapidas.lote", modulo: "mensagens_rapidas", nivelMinimo: "EDITAR", tipo: "ACAO", sensivel: true, teto: ["ATENDENTE", "SUBGESTOR", "GESTOR", "ADMINISTRADOR"], delegavel: false, dependencias: ["mensagens_rapidas.criar"], alcancePorPapel: {} },
      ],
    };
    const perfil: Rascunho = { ...perfilAtendente, acoes: { ...perfilAtendente.acoes, "mensagens_rapidas.lote": true } };
    const edicao = (r: Rascunho): EstadoDaEdicao => ({ acoes: r.acoes, simulacao: simular(comLote, "ATENDENTE", r) });
    const julgar = (antes: Rascunho, depois: Rascunho, tem: string[]) =>
      violacaoDaDelegacao(comLote, (id) => tem.includes(id), edicao(antes), edicao(depois));

    it("nível que só desliga o delegável passa; que desliga o não delegável, não", () => {
      const tagsEmVer = aplicarNivel(perfil, comLote, "ATENDENTE", "tags", "VER");
      expect(julgar(perfil, tagsEmVer, [])).toBeNull();
      const mensagensEmVer = aplicarNivel(perfil, comLote, "ATENDENTE", "mensagens_rapidas", "VER");
      expect(julgar(perfil, mensagensEmVer, [])).toEqual({ capacidade: "mensagens_rapidas.lote", motivo: "FORA_DO_CONJUNTO_DELEGAVEL" });
    });

    it("subir o nível com o preset liga ações: só passa se o ator as tem", () => {
      const soVer: Rascunho = { niveis: { ...perfilAtendente.niveis, mensagens_rapidas: "VER" }, acoes: { ...perfilAtendente.acoes, "mensagens_rapidas.criar": false } };
      const editar = aplicarNivel(soVer, catalogo, "ATENDENTE", "mensagens_rapidas", "EDITAR");
      const julgarSemLote = (tem: string[]) => violacaoDaDelegacao(catalogo, (id) => tem.includes(id),
        { acoes: soVer.acoes, simulacao: simular(catalogo, "ATENDENTE", soVer) },
        { acoes: editar.acoes, simulacao: simular(catalogo, "ATENDENTE", editar) });
      expect(julgarSemLote([])).toEqual({ capacidade: "mensagens_rapidas.criar", motivo: "ACIMA_DA_PROPRIA_PERMISSAO" });
      expect(julgarSemLote(["mensagens_rapidas.criar"])).toBeNull();
    });

    it("dependência também liga e desliga: a ação afetada é a que responde", () => {
      // desligar criar derruba o lote (não delegável) pela dependência
      expect(julgar(perfil, alternarAcao(perfil, "mensagens_rapidas.criar", false), [])).toEqual({
        capacidade: "mensagens_rapidas.lote",
        motivo: "FORA_DO_CONJUNTO_DELEGAVEL",
      });
      // religar "usar" reativa criar, que estava ligado mas bloqueado — e o ator não tem criar
      const semUsar = { niveis: perfilAtendente.niveis, acoes: { ...perfilAtendente.acoes, "mensagens_rapidas.usar": false } };
      const deVolta = alternarAcao(semUsar, "mensagens_rapidas.usar", true);
      const julgarSemLote = (tem: string[]) => violacaoDaDelegacao(catalogo, (id) => tem.includes(id),
        { acoes: semUsar.acoes, simulacao: simular(catalogo, "ATENDENTE", semUsar) },
        { acoes: deVolta.acoes, simulacao: simular(catalogo, "ATENDENTE", deVolta) });
      expect(julgarSemLote(["mensagens_rapidas.usar"])).toEqual({ capacidade: "mensagens_rapidas.criar", motivo: "ACIMA_DA_PROPRIA_PERMISSAO" });
      expect(julgarSemLote(["mensagens_rapidas.usar", "mensagens_rapidas.criar"])).toBeNull();
    });
  });

  it("diff e sensibilidade das alterações pendentes", () => {
    const alterado = aplicarNivel(perfilAtendente, catalogo, "SUBGESTOR", "tags", "GERENCIAR");
    const chaves = chavesAlteradas(perfilAtendente, alterado);
    expect(chaves).toContain("modulo:tags");
    expect(tocaSensivel(catalogo, chaves)).toBe(true);
    expect(tocaSensivel(catalogo, ["tags.aplicar"])).toBe(false);
  });
});
