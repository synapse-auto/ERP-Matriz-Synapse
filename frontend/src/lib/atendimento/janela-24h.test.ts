import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import { describe, expect, it } from "vitest";

import { estadoDaJanelaTextoLivre, janelaTextoLivreAberta } from "./janela-24h";

describe("janelaTextoLivreAberta", () => {
  const agora = new Date("2026-08-03T12:00:00Z");

  it("sem última mensagem do lead, considera fechada", () => {
    expect(janelaTextoLivreAberta(null, agora)).toBe(false);
  });

  it("23h59 depois da última mensagem do lead, ainda aberta", () => {
    const ultima = new Date(agora.getTime() - (23 * 60 + 59) * 60 * 1000).toISOString();
    expect(janelaTextoLivreAberta(ultima, agora)).toBe(true);
  });

  it("24h01 depois, já fechada", () => {
    const ultima = new Date(agora.getTime() - (24 * 60 + 1) * 60 * 1000).toISOString();
    expect(janelaTextoLivreAberta(ultima, agora)).toBe(false);
  });

  it("exatamente 24h, fechada (limite exclusivo)", () => {
    const ultima = new Date(agora.getTime() - 24 * 60 * 60 * 1000).toISOString();
    expect(janelaTextoLivreAberta(ultima, agora)).toBe(false);
  });
});

describe("estadoDaJanelaTextoLivre", () => {
  const agora = new Date("2026-08-03T12:00:00Z");

  it("lead que nunca escreveu é inexistente, não fechada", () => {
    expect(estadoDaJanelaTextoLivre(null, agora)).toBe("inexistente");
  });

  it("mensagem recente é aberta", () => {
    const ultima = new Date(agora.getTime() - 60 * 60 * 1000).toISOString();
    expect(estadoDaJanelaTextoLivre(ultima, agora)).toBe("aberta");
  });

  it("mensagem antiga é fechada", () => {
    const ultima = new Date(agora.getTime() - 25 * 60 * 60 * 1000).toISOString();
    expect(estadoDaJanelaTextoLivre(ultima, agora)).toBe("fechada");
  });

  it("devolve só um dos três estados", () => {
    const ultima = new Date(agora.getTime() - 60 * 60 * 1000).toISOString();
    expect(["aberta", "fechada", "inexistente"]).toContain(
      estadoDaJanelaTextoLivre(ultima, agora),
    );
  });
});

describe("trava: estado, não horário", () => {
  const fontes = [
    "src/lib/atendimento/janela-24h.ts",
    "src/components/atendimentos/composer.tsx",
    "src/components/atendimentos/lista-templates-whatsapp.tsx",
    "src/components/templates-whatsapp/pagina-templates-whatsapp.tsx",
  ].map((arquivo) => ({
    arquivo,
    conteudo: readFileSync(resolve(process.cwd(), arquivo), "utf8"),
  }));

  it("não calcula nem renderiza horário de fechamento ou contagem regressiva", () => {
    const proibidos = [
      /fechaEm/,
      /horarioDeFechamento/,
      /minutosRestantes/,
      /horasRestantes/,
      /contagemRegressiva/,
      /toLocaleTimeString/,
      /toLocaleDateString/,
      /DateTimeFormat/,
      /fecha em/i,
      /fecha amanh/i,
      /faltam \d/i,
    ];
    for (const { arquivo, conteudo } of fontes) {
      for (const padrao of proibidos) {
        expect(conteudo, `${arquivo} casou ${padrao}`).not.toMatch(padrao);
      }
    }
  });

  it("não define cor só dentro de um tema", () => {
    for (const { arquivo, conteudo } of fontes) {
      expect(conteudo, arquivo).not.toMatch(/\bdark:/);
    }
  });
});
