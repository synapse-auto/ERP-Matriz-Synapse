import { describe, expect, it } from "vitest";

import { areaDeFeedbackVisivel, itemDeMenuVisivel } from "./visibilidade-do-menu";

describe("visibilidade do menu e das áreas de feedback", () => {
  it("esconde Dashboard, Gestão e Automação do atendente, mesmo com a flag ligada", () => {
    const flags = ["dashboard"];
    expect(itemDeMenuVisivel("dashboard", "ATENDENTE", flags, "dashboard")).toBe(false);
    expect(itemDeMenuVisivel("gestao", "ATENDENTE", flags)).toBe(false);
    expect(itemDeMenuVisivel("automacao", "ATENDENTE", flags)).toBe(false);
    expect(areaDeFeedbackVisivel("DASHBOARD", "ATENDENTE", flags)).toBe(false);
    expect(areaDeFeedbackVisivel("EQUIPE", "ATENDENTE", flags)).toBe(false);
    expect(areaDeFeedbackVisivel("AUTOMACAO", "ATENDENTE", flags)).toBe(false);
    expect(areaDeFeedbackVisivel("ATENDIMENTOS", "ATENDENTE", flags)).toBe(true);
    expect(areaDeFeedbackVisivel("CONFIGURACOES", "ATENDENTE", flags)).toBe(true);
    expect(areaDeFeedbackVisivel("GERAL", "ATENDENTE", flags)).toBe(true);
  });

  it("mostra Dashboard para gestor somente quando a feature está habilitada", () => {
    expect(areaDeFeedbackVisivel("DASHBOARD", "GESTOR", ["dashboard"])).toBe(true);
    expect(areaDeFeedbackVisivel("DASHBOARD", "GESTOR", [])).toBe(false);
    expect(areaDeFeedbackVisivel("EQUIPE", "GESTOR", [])).toBe(true);
    // Gestão (docs/47): SUBGESTOR acessa Gestão; o que ele administra depende de delegação.
    expect(areaDeFeedbackVisivel("EQUIPE", "SUBGESTOR", [])).toBe(true);
  });

  it("requer somente a flag para o chat interno e não cria guarda de papel", () => {
    expect(itemDeMenuVisivel("chatInterno", "ATENDENTE", ["chat_interno"], "chat_interno")).toBe(true);
    expect(itemDeMenuVisivel("chatInterno", "GESTOR", ["chat_interno"], "chat_interno")).toBe(true);
    expect(itemDeMenuVisivel("chatInterno", "ATENDENTE", [], "chat_interno")).toBe(false);
  });

  it("esconde templates quando o provedor não oferece gerenciamento", () => {
    expect(itemDeMenuVisivel("templatesWhatsApp", "GESTOR", [], undefined, false)).toBe(false);
    expect(itemDeMenuVisivel("templatesWhatsApp", "GESTOR", [])).toBe(true);
  });

  it("com permissões efetivas, o menu some quando a leitura foi revogada e Gestão segue o backend", () => {
    const efetivas = (ids: Record<string, boolean>, acessaGestao = true) => ({
      usuarioId: "u", papel: "SUBGESTOR" as const, revisao: 1, acessaGestao, editaPerfis: false, editaExcecoes: false,
      capacidades: Object.fromEntries(Object.entries(ids).map(([id, permitido]) => [id, { permitido, motivo: "PERMITIDO" as const, alcance: null }])),
    });
    expect(itemDeMenuVisivel("gestao", "SUBGESTOR", [], undefined, true, efetivas({}, false))).toBe(false);
    expect(itemDeMenuVisivel("gestao", "SUBGESTOR", [], undefined, true, efetivas({}, true))).toBe(true);
    expect(itemDeMenuVisivel("gestao", "GESTOR", [], undefined, true, efetivas({}, true))).toBe(true);
    expect(itemDeMenuVisivel("automacao", "SUBGESTOR", [], undefined, true, efetivas({ "automacao.ver": false }))).toBe(false);
    expect(itemDeMenuVisivel("mensagensRapidas", "ATENDENTE", [], undefined, true, efetivas({ "mensagens_rapidas.usar": false }))).toBe(false);
    expect(itemDeMenuVisivel("mensagensRapidas", "ATENDENTE", [], undefined, true, efetivas({ "mensagens_rapidas.usar": true }))).toBe(true);
    expect(itemDeMenuVisivel("atendimentos", "ATENDENTE", [], undefined, true, efetivas({}))).toBe(true);
  });

  it("com permissões ainda desconhecidas, esconde todo item condicionado em vez de cair na regra do papel", () => {
    expect(itemDeMenuVisivel("templatesWhatsApp", "GESTOR", [], undefined, true, null)).toBe(false);
    expect(itemDeMenuVisivel("dashboard", "GESTOR", ["dashboard"], "dashboard", true, null)).toBe(false);
    expect(itemDeMenuVisivel("gestao", "ADMINISTRADOR", [], undefined, true, null)).toBe(false);
    expect(itemDeMenuVisivel("atendimentos", "ATENDENTE", [], undefined, true, null)).toBe(true);
    expect(itemDeMenuVisivel("tags", "ATENDENTE", [], undefined, true, null)).toBe(true);
  });
});
