import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import { describe, expect, it } from "vitest";

import { TextosSchema } from "./schema";

describe("TextosSchema", () => {
  it("aceita o catálogo servido pelo backend e mantém os textos da automação no grupo correto", () => {
    const caminho = resolve(
      process.cwd(),
      "../backend/crm-app/src/main/resources/textos.json",
    );
    const catalogo = JSON.parse(readFileSync(caminho, "utf8")) as unknown;

    const textos = TextosSchema.parse(catalogo);

    expect(textos.automacao.abas.followUp).toBe("Follow-up");
    expect(textos.automacao.recursosIa.resumo).toBe("Resumo automático por IA");
    expect(textos.automacao.regras.novo).toBe("Nova regra");
    expect(textos.automacao.disponibilidade.contagem).toContain("{disponiveis}");
    expect(textos.automacao.regras.visualizacaoWhatsapp).toBe("VISUALIZAÇÃO NO WHATSAPP");
  });
});
