import { describe, expect, it } from "vitest";

import { areaDeFeedbackVisivel, itemDeMenuVisivel } from "./visibilidade-do-menu";

describe("visibilidade do menu e das áreas de feedback", () => {
  it("esconde Dashboard, Equipe e Automação do atendente, mesmo com a flag ligada", () => {
    const flags = ["dashboard"];
    expect(itemDeMenuVisivel("dashboard", "ATENDENTE", flags, "dashboard")).toBe(false);
    expect(itemDeMenuVisivel("equipe", "ATENDENTE", flags)).toBe(false);
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
    expect(areaDeFeedbackVisivel("EQUIPE", "SUBGESTOR", [])).toBe(false);
  });
});
